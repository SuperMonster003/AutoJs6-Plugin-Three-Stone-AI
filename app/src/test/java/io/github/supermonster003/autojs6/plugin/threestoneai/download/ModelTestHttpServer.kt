package io.github.supermonster003.autojs6.plugin.threestoneai.download

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A loopback HTTP/1.1 server for the download tests: each path answers from a script (the last
 * answer repeats), files honour `Range: bytes=N-`, and every exchange is recorded.
 */
internal class ModelTestHttpServer : Closeable {
    data class Exchange(
        val path: String,
        val range: String?,
        val ifRange: String?,
        val status: Int,
        val sentBytes: Long,
    )

    sealed interface Answer {
        /** The file; [cutAfter] body bytes end the connection early, [chunkDelayMillis] slows it down. */
        class Serve(
            val bytes: ByteArray,
            val cutAfter: Long? = null,
            val etag: String? = ETAG,
            val chunkDelayMillis: Long = 0L,
        ) : Answer

        data class Status(val code: Int) : Answer

        data class Redirect(val location: String, val code: Int = 302) : Answer
    }

    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val scripts = HashMap<String, ArrayDeque<Answer>>()
    val exchanges = CopyOnWriteArrayList<Exchange>()
    val port: Int get() = server.localPort

    init {
        thread(isDaemon = true, name = "model-test-http") {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (error: Exception) {
                    break
                }
                thread(isDaemon = true) { socket.use(::handle) }
            }
        }
    }

    fun url(path: String): String = "http://127.0.0.1:$port$path"

    fun script(path: String, vararg answers: Answer) = synchronized(scripts) {
        scripts[path] = ArrayDeque(answers.toList())
    }

    fun paths(): List<String> = exchanges.map { it.path }

    /** An exchange is recorded once its body is written, which can be after the client read it. */
    fun awaitExchanges(count: Int): List<Exchange> {
        val deadline = System.currentTimeMillis() + 5_000L
        while (exchanges.size < count && System.currentTimeMillis() < deadline) Thread.sleep(10L)
        return exchanges.toList()
    }

    private fun next(path: String): Answer = synchronized(scripts) {
        val queue = scripts[path] ?: return Answer.Status(404)
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
        val range = headers["range"]
        val ifRange = headers["if-range"]
        val output = socket.getOutputStream()
        when (val answer = next(path)) {
            is Answer.Status -> {
                head(output, answer.code, "Content-Length: 0")
                exchanges += Exchange(path, range, ifRange, answer.code, 0L)
            }
            is Answer.Redirect -> {
                head(output, answer.code, "Location: ${answer.location}", "Content-Length: 0")
                exchanges += Exchange(path, range, ifRange, answer.code, 0L)
            }
            is Answer.Serve -> serve(output, path, range, ifRange, answer)
        }
        output.flush()
    }

    private fun serve(output: OutputStream, path: String, range: String?, ifRange: String?, answer: Answer.Serve) {
        val bytes = answer.bytes
        val start = range?.let { Regex("bytes=(\\d+)-").find(it)?.groupValues?.get(1)?.toLong() }
        val etagLine = answer.etag?.let { "ETag: $it" }
        val status: Int
        val from: Long
        if (start != null && start < bytes.size) {
            status = 206
            from = start
            head(
                output,
                status,
                "Content-Range: bytes $start-${bytes.size - 1}/${bytes.size}",
                "Content-Length: ${bytes.size - start}",
                etagLine,
            )
        } else if (start != null) {
            head(output, 416, "Content-Range: bytes */${bytes.size}", "Content-Length: 0")
            exchanges += Exchange(path, range, ifRange, 416, 0L)
            return
        } else {
            status = 200
            from = 0L
            head(output, status, "Content-Length: ${bytes.size}", etagLine)
        }
        val limit = answer.cutAfter?.let { minOf(from + it, bytes.size.toLong()) } ?: bytes.size.toLong()
        var position = from
        try {
            while (position < limit) {
                val count = minOf(CHUNK_BYTES.toLong(), limit - position).toInt()
                output.write(bytes, position.toInt(), count)
                output.flush()
                position += count
                if (answer.chunkDelayMillis > 0L) Thread.sleep(answer.chunkDelayMillis)
            }
        } catch (error: Exception) {
            // The client went away (a pause or a cancellation).
        } finally {
            exchanges += Exchange(path, range, ifRange, status, position - from)
        }
    }

    private fun head(output: OutputStream, status: Int, vararg lines: String?) {
        val text = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
            lines.filterNotNull().forEach { append(it).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        output.write(text.toByteArray(Charsets.US_ASCII))
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        206 -> "Partial Content"
        302 -> "Found"
        404 -> "Not Found"
        416 -> "Range Not Satisfiable"
        503 -> "Service Unavailable"
        else -> "Status"
    }

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) return if (line.isEmpty()) null else line.toString()
            if (value == '\n'.code) return line.toString().trimEnd('\r')
            line.append(value.toChar())
        }
    }

    override fun close() {
        server.close()
    }

    companion object {
        const val ETAG = "\"v1\""
        private const val CHUNK_BYTES = 16 * 1024
    }
}
