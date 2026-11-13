package io.github.supermonster003.autojs6.plugin.threestoneai.download

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * [startOffset] is where [input] begins in the pinned file: the requested offset when the server
 * honoured the range request, or zero when it sent the whole file and the caller must start over.
 */
internal data class OpenModelDownload(
    val input: InputStream,
    val declaredLength: Long,
    val startOffset: Long = 0L,
)

internal fun interface ModelDownloadClient {
    @Throws(ModelDownloadFailureException::class, InterruptedException::class)
    fun open(
        model: RecommendedModel,
        control: ModelDownloadOperationControl,
        offset: Long,
    ): OpenModelDownload
}

internal object HttpsModelDownloadClient : ModelDownloadClient {
    override fun open(
        model: RecommendedModel,
        control: ModelDownloadOperationControl,
        offset: Long,
    ): OpenModelDownload {
        ModelDownloadPolicy.requireValidCatalogEntry(model)
        require(offset in 0L until model.expectedSizeBytes) { "Model download offset is invalid" }
        var uri = URI(model.downloadUrl)
        repeat(ModelDownloadPolicy.MAXIMUM_REDIRECTS + 1) { redirectCount ->
            control.ensureActive()
            try {
                ModelDownloadPolicy.requireAllowedRedirectTarget(uri)
            } catch (error: IllegalArgumentException) {
                throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.HTTP_ERROR,
                    "Model download redirect target is not approved",
                    error,
                )
            }
            val connection = try {
                uri.toURL().openConnection() as? HttpsURLConnection
                    ?: throw IOException("Model URL did not create an HTTPS connection")
            } catch (error: IOException) {
                throw networkFailure(error)
            }
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = ModelDownloadPolicy.CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = ModelDownloadPolicy.READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", "AutoJs6-Three-Stone-AI/1")
            // Sent on every hop: the redirect target is the host that actually serves the bytes.
            if (offset > 0L) connection.setRequestProperty("Range", "bytes=$offset-")
            control.registerCancellationResource(Closeable(connection::disconnect))

            val status = try {
                connection.responseCode
            } catch (error: IOException) {
                throw networkFailure(error)
            }
            if (status in REDIRECT_STATUS_CODES) {
                if (redirectCount >= ModelDownloadPolicy.MAXIMUM_REDIRECTS) {
                    throw ModelDownloadFailureException(
                        ModelDownloadFailureReason.HTTP_ERROR,
                        "Model download exceeded the redirect limit",
                    )
                }
                val location = connection.getHeaderField("Location")
                    ?: throw ModelDownloadFailureException(
                        ModelDownloadFailureReason.HTTP_ERROR,
                        "Model download redirect omitted its destination",
                    )
                connection.disconnect()
                uri = try {
                    uri.resolve(location)
                } catch (error: IllegalArgumentException) {
                    throw ModelDownloadFailureException(
                        ModelDownloadFailureReason.HTTP_ERROR,
                        "Model download redirect is invalid",
                        error,
                    )
                }
                return@repeat
            }
            val startOffset = when {
                status == HttpURLConnection.HTTP_PARTIAL && offset > 0L -> {
                    requireMatchingRange(
                        connection.getHeaderField("Content-Range"),
                        offset,
                        model.expectedSizeBytes,
                    )
                    offset
                }
                status == HttpURLConnection.HTTP_OK -> 0L
                else -> {
                    runCatching { connection.errorStream?.close() }
                    throw ModelDownloadFailureException(
                        ModelDownloadFailureReason.HTTP_ERROR,
                        "Model download returned HTTP $status",
                    )
                }
            }

            val declaredLength = connection.contentLengthLong
            if (declaredLength != model.expectedSizeBytes - startOffset) {
                throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.INTEGRITY_MISMATCH,
                    "Model download length does not match its pinned size",
                )
            }
            val input = try {
                BufferedInputStream(connection.inputStream, NETWORK_BUFFER_BYTES)
            } catch (error: IOException) {
                throw networkFailure(error)
            }
            control.registerCancellationResource(input)
            return OpenModelDownload(input, declaredLength, startOffset)
        }
        throw ModelDownloadFailureException(
            ModelDownloadFailureReason.HTTP_ERROR,
            "Model download redirect loop did not terminate",
        )
    }

    /** Accepts only "bytes <offset>-<size - 1>/<size>" so the appended bytes line up exactly. */
    internal fun requireMatchingRange(header: String?, offset: Long, totalBytes: Long) {
        val match = header?.trim()?.let(CONTENT_RANGE::matchEntire)
        val start = match?.groupValues?.get(1)?.toLongOrNull()
        val end = match?.groupValues?.get(2)?.toLongOrNull()
        val total = match?.groupValues?.get(3)?.toLongOrNull()
        if (start != offset || end != totalBytes - 1L || total != totalBytes) {
            throw ModelDownloadFailureException(
                ModelDownloadFailureReason.INTEGRITY_MISMATCH,
                "Model download range does not match the partial file",
            )
        }
    }

    private fun networkFailure(error: IOException) = ModelDownloadFailureException(
        ModelDownloadFailureReason.NETWORK_UNAVAILABLE,
        "Model download connection failed",
        error,
    )

    private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    private val CONTENT_RANGE = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)", RegexOption.IGNORE_CASE)
    private const val NETWORK_BUFFER_BYTES = 1024 * 1024
}
