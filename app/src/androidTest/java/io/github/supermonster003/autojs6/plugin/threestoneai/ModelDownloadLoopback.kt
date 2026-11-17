package io.github.supermonster003.autojs6.plugin.threestoneai

import android.util.Log
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadTransport
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModel
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A loopback HTTP/1.1 server in the test process for the model download tests (plugin-center
 * P13.3). Each path answers from a script whose last answer repeats. A served model is "LITERTLM",
 * zeros and an optional different last byte, generated on the fly, so a catalog model of 1.6 GB
 * needs no storage; `Range: bytes=N-` is honoured. Every request is recorded when it arrives.
 */
internal class LoopbackModelServer : Closeable {
    data class Request(val path: String, val range: String?, val ifRange: String?)

    sealed interface Answer {
        class Serve(
            val size: Long,
            val lastByte: Byte = 0,
            val cutAfter: Long? = null,
            val chunkDelayMillis: Long = CHUNK_DELAY_MILLIS,
        ) : Answer

        /** Closes the connection without an answer, like a network that drops. */
        data object Refuse : Answer
    }

    private val server = ServerSocket(0, 50, InetAddress.getByName(HOST))
    private val scripts = HashMap<String, ArrayDeque<Answer>>()
    private val sockets = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<Request>()

    init {
        thread(isDaemon = true, name = "model-loopback-http") {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (error: IOException) {
                    break
                }
                sockets += socket
                thread(isDaemon = true) {
                    try {
                        socket.use(::handle)
                    } catch (error: IOException) {
                        // The client closed the connection (a pause or a cancellation).
                    } finally {
                        sockets -= socket
                    }
                }
            }
        }
    }

    fun url(path: String): String = "http://$HOST:${server.localPort}$path"

    fun script(path: String, vararg answers: Answer) = synchronized(scripts) {
        scripts[path] = ArrayDeque(answers.toList())
    }

    fun ranges(path: String): List<String?> = requests.filter { it.path == path }.map { it.range }

    private fun next(path: String): Answer? = synchronized(scripts) {
        val queue = scripts[path] ?: return null
        if (queue.size > 1) queue.removeFirst() else queue.first()
    }

    private fun handle(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
        val request = Request(path, headers["range"], headers["if-range"])
        requests += request
        Log.i(TAG, "request $path range=${request.range} if-range=${request.ifRange}")
        val output = socket.getOutputStream()
        when (val answer = next(path)) {
            null -> head(output, 404, "Content-Length: 0")
            Answer.Refuse -> return
            is Answer.Serve -> serve(output, request, answer)
        }
        output.flush()
    }

    private fun serve(output: OutputStream, request: Request, answer: Answer.Serve) {
        val size = answer.size
        val start = request.range?.let { Regex("bytes=(\\d+)-").find(it)?.groupValues?.get(1)?.toLong() }
        val from = if (start != null && start < size) {
            head(output, 206, "Content-Range: bytes $start-${size - 1}/$size", "Content-Length: ${size - start}", "ETag: $ETAG")
            start
        } else {
            head(output, 200, "Content-Length: $size", "ETag: $ETAG")
            0L
        }
        val limit = answer.cutAfter?.let { minOf(from + it, size) } ?: size
        val buffer = ByteArray(CHUNK_BYTES)
        var position = from
        while (position < limit) {
            val count = minOf(buffer.size.toLong(), limit - position).toInt()
            for (index in 0 until count) buffer[index] = byteAt(position + index, size, answer.lastByte)
            output.write(buffer, 0, count)
            output.flush()
            position += count
            if (answer.chunkDelayMillis > 0L) Thread.sleep(answer.chunkDelayMillis)
        }
        Log.i(TAG, "sent ${request.path} ${position - from} B from $from")
    }

    private fun head(output: OutputStream, status: Int, vararg lines: String) {
        val text = buildString {
            append("HTTP/1.1 ").append(status).append(if (status == 206) " Partial Content" else " OK").append("\r\n")
            lines.forEach { append(it).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        output.write(text.toByteArray(StandardCharsets.US_ASCII))
    }

    override fun close() {
        server.close()
        sockets.forEach { runCatching { it.close() } }
    }

    /** Plain HTTP to this server only; the production transport stays HTTPS on Hugging Face. */
    inner class Transport : ModelDownloadTransport {
        override fun urls(model: RecommendedModel): List<String> = listOf(url("/${model.id}"))

        override fun requireAllowed(uri: URI) {
            require(uri.scheme == "http" && uri.host == HOST && uri.port == server.localPort) {
                "Only the loopback test server is approved"
            }
        }

        override fun openHop(url: URL): HttpURLConnection = LoopbackHttpConnection(url)
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val ETAG = "\"loopback-1\""
        const val CHUNK_BYTES = 64 * 1024
        const val CHUNK_DELAY_MILLIS = 15L
        private const val TAG = "ModelLoopback"
        private val MAGIC = "LITERTLM".toByteArray(StandardCharsets.US_ASCII)

        fun byteAt(position: Long, size: Long, lastByte: Byte): Byte = when {
            position < MAGIC.size -> MAGIC[position.toInt()]
            position == size - 1 -> lastByte
            else -> 0
        }

        /** SHA-256 of what [Answer.Serve] sends for [size] and [lastByte]. */
        fun sha256Of(size: Long, lastByte: Byte): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(CHUNK_BYTES)
            var position = 0L
            while (position < size) {
                val count = minOf(buffer.size.toLong(), size - position).toInt()
                for (index in 0 until count) buffer[index] = byteAt(position + index, size, lastByte)
                digest.update(buffer, 0, count)
                position += count
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }

        internal fun readLine(input: InputStream): String? {
            val line = StringBuilder()
            while (true) {
                val value = input.read()
                if (value < 0) return if (line.isEmpty()) null else line.toString()
                if (value == '\n'.code) return line.toString().trimEnd('\r')
                line.append(value.toChar())
            }
        }
    }
}

/**
 * A minimal HTTP/1.1 client over a plain socket for the loopback server. The app's network
 * security policy (no cleartext traffic) applies to the platform's HTTP stack, not to sockets, so
 * the download library talks real HTTP to the test server without any change to the app.
 */
internal class LoopbackHttpConnection(url: URL) : HttpURLConnection(url) {
    private var socket: Socket? = null
    private var status = -1
    private var message: String? = null
    private val headers = LinkedHashMap<String, String>()
    private var body: InputStream? = null

    @Synchronized
    override fun connect() {
        if (connected) return
        val properties = requestProperties
        val connection = Socket()
        socket = connection
        connection.connect(InetSocketAddress(url.host, url.port), connectTimeout)
        connection.soTimeout = readTimeout
        val request = buildString {
            append("GET ").append(url.file.ifEmpty { "/" }).append(" HTTP/1.1\r\n")
            append("Host: ").append(url.host).append(':').append(url.port).append("\r\n")
            properties.forEach { (name, values) ->
                if (name != null) append(name).append(": ").append(values.joinToString(",")).append("\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        connection.getOutputStream().apply {
            write(request.toByteArray(StandardCharsets.US_ASCII))
            flush()
        }
        val input = BufferedInputStream(connection.getInputStream())
        val statusLine = LoopbackModelServer.readLine(input) ?: throw EOFException("Connection closed without a response")
        val parts = statusLine.split(' ', limit = 3)
        status = parts.getOrNull(1)?.toIntOrNull() ?: throw IOException("Invalid status line")
        message = parts.getOrNull(2)
        while (true) {
            val line = LoopbackModelServer.readLine(input) ?: throw EOFException("Connection closed in the headers")
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toLongOrNull()
        body = if (length != null) Bounded(input, length) else input
        connected = true
    }

    override fun getResponseCode(): Int {
        connect()
        return status
    }

    override fun getResponseMessage(): String? {
        connect()
        return message
    }

    override fun getHeaderField(name: String?): String? {
        connect()
        return name?.let { headers[it.lowercase()] }
    }

    override fun getHeaderFields(): Map<String?, List<String>> {
        connect()
        return headers.mapValues { listOf(it.value) }
    }

    override fun getContentLengthLong(): Long = getHeaderField("Content-Length")?.toLongOrNull() ?: -1L

    override fun getInputStream(): InputStream {
        connect()
        if (status >= 400) throw IOException("HTTP $status")
        return checkNotNull(body)
    }

    override fun disconnect() {
        runCatching { socket?.close() }
    }

    override fun usingProxy(): Boolean = false

    private class Bounded(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0L) return -1
            return super.read().also { if (it >= 0) remaining -= 1 }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0L) return -1
            val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count > 0) remaining -= count
            return count
        }
    }
}
