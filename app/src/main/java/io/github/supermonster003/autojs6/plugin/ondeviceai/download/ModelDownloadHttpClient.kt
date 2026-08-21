package io.github.supermonster003.autojs6.plugin.ondeviceai.download

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.net.ssl.HttpsURLConnection

internal data class OpenModelDownload(
    val input: InputStream,
    val declaredLength: Long,
)

internal fun interface ModelDownloadClient {
    @Throws(ModelDownloadFailureException::class, InterruptedException::class)
    fun open(model: RecommendedModel, control: ModelDownloadOperationControl): OpenModelDownload
}

internal object HttpsModelDownloadClient : ModelDownloadClient {
    override fun open(
        model: RecommendedModel,
        control: ModelDownloadOperationControl,
    ): OpenModelDownload {
        ModelDownloadPolicy.requireValidCatalogEntry(model)
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
            connection.setRequestProperty("User-Agent", "AutoJs6-On-Device-AI/1")
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
            if (status != HttpURLConnection.HTTP_OK) {
                runCatching { connection.errorStream?.close() }
                throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.HTTP_ERROR,
                    "Model download returned HTTP $status",
                )
            }

            val declaredLength = connection.contentLengthLong
            if (declaredLength != model.expectedSizeBytes) {
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
            return OpenModelDownload(input, declaredLength)
        }
        throw ModelDownloadFailureException(
            ModelDownloadFailureReason.HTTP_ERROR,
            "Model download redirect loop did not terminate",
        )
    }

    private fun networkFailure(error: IOException) = ModelDownloadFailureException(
        ModelDownloadFailureReason.NETWORK_UNAVAILABLE,
        "Model download connection failed",
        error,
    )

    private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    private const val NETWORK_BUFFER_BYTES = 1024 * 1024
}
