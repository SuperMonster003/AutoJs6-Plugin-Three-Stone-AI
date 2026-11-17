package io.github.supermonster003.autojs6.plugin.threestoneai.download

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Where a recommended model is fetched from and how each HTTP hop is opened (plugin-center P13.3).
 * The shared download library follows redirects by itself, so every connection it opens is a
 * [PolicyRedirectConnection] that walks the redirects with these rules instead.
 */
internal interface ModelDownloadTransport {
    /** Mirrors of [model], tried in order. The catalog pins one Hugging Face URL per model. */
    fun urls(model: RecommendedModel): List<String>

    /** Checks the first URL and every redirect target; throws [IllegalArgumentException] when refused. */
    fun requireAllowed(uri: URI)

    /** Opens one hop; the caller turns automatic redirects off and adds the request headers. */
    @Throws(IOException::class)
    fun openHop(url: URL): HttpURLConnection
}

internal object HttpsModelDownloadTransport : ModelDownloadTransport {
    override fun urls(model: RecommendedModel): List<String> = listOf(model.downloadUrl)

    override fun requireAllowed(uri: URI) = ModelDownloadPolicy.requireAllowedRedirectTarget(uri)

    override fun openHop(url: URL): HttpURLConnection =
        url.openConnection() as? HttpsURLConnection
            ?: throw IOException("Model URL did not create an HTTPS connection")
}

/**
 * The connection the download library uses for a model URL. [connect] opens the hops itself with
 * automatic redirects off: every hop must pass [ModelDownloadTransport.requireAllowed] (HTTPS on
 * Hugging Face hosts in production) and receives the request headers the library set (Range,
 * If-Range), since the redirect target is the host that serves the bytes. A refused target fails
 * with [SecurityException], which the library does not retry. When the redirect limit is reached or
 * a redirect has no usable Location, that redirect response is what the library sees, so it fails
 * as an HTTP status that is not retried either. Every hop is handed to [register], which closes it
 * when the download is paused or cancelled.
 */
internal class PolicyRedirectConnection(
    url: URL,
    private val transport: ModelDownloadTransport,
    private val register: (Closeable) -> Unit,
    private val maximumRedirects: Int = ModelDownloadPolicy.MAXIMUM_REDIRECTS,
) : HttpURLConnection(url) {
    @Volatile
    private var delegate: HttpURLConnection? = null

    @Synchronized
    override fun connect() {
        if (delegate != null) return
        val headers = requestProperties.mapValues { (_, values) -> values.joinToString(",") }
        var uri = try {
            url.toURI()
        } catch (error: URISyntaxException) {
            throw IOException("Model download URL is invalid", error)
        }
        var redirects = 0
        while (true) {
            try {
                transport.requireAllowed(uri)
            } catch (error: IllegalArgumentException) {
                throw SecurityException("Model download host is not approved: ${uri.host}", error)
            }
            val hop = transport.openHop(uri.toURL())
            hop.instanceFollowRedirects = false
            hop.requestMethod = "GET"
            hop.connectTimeout = connectTimeout
            hop.readTimeout = readTimeout
            hop.useCaches = false
            hop.setRequestProperty("Accept", "application/octet-stream")
            hop.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (name, value) -> hop.setRequestProperty(name, value) }
            try {
                register(Closeable(hop::disconnect))
            } catch (error: InterruptedException) {
                hop.disconnect()
                throw InterruptedIOException("Model download was cancelled")
            }
            val status = hop.responseCode
            val next = if (status in REDIRECT_STATUS_CODES && redirects < maximumRedirects) {
                hop.getHeaderField("Location")?.let { location ->
                    try {
                        uri.resolve(location.trim())
                    } catch (error: IllegalArgumentException) {
                        null
                    }
                }
            } else {
                null
            }
            if (next == null) {
                delegate = hop
                connected = true
                return
            }
            hop.disconnect()
            uri = next
            redirects += 1
        }
    }

    private fun connected(): HttpURLConnection {
        connect()
        return checkNotNull(delegate)
    }

    override fun getResponseCode(): Int = connected().responseCode

    override fun getResponseMessage(): String? = connected().responseMessage

    override fun getHeaderField(name: String?): String? = connected().getHeaderField(name)

    override fun getHeaderField(n: Int): String? = connected().getHeaderField(n)

    override fun getHeaderFieldKey(n: Int): String? = connected().getHeaderFieldKey(n)

    override fun getHeaderFields(): Map<String?, List<String>> = connected().headerFields

    override fun getContentLength(): Int = connected().contentLength

    override fun getContentLengthLong(): Long = connected().contentLengthLong

    override fun getContentType(): String? = connected().contentType

    override fun getInputStream(): InputStream = connected().inputStream

    override fun getErrorStream(): InputStream? = delegate?.errorStream

    /** The URL of the final hop, so failure details name the host that served the bytes. */
    override fun getURL(): URL = delegate?.url ?: super.getURL()

    override fun disconnect() {
        delegate?.disconnect()
    }

    override fun usingProxy(): Boolean = delegate?.usingProxy() ?: false

    companion object {
        const val USER_AGENT = "AutoJs6-Three-Stone-AI/1"
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}
