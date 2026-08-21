package io.github.supermonster003.autojs6.plugin.ondeviceai.download

import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelImportPolicy
import java.net.URI

internal object ModelDownloadPolicy {
    const val MAXIMUM_REDIRECTS = 8
    const val CONNECT_TIMEOUT_MILLIS = 30_000
    const val READ_TIMEOUT_MILLIS = 60_000
    private val SHA_256 = Regex("^[0-9a-f]{64}$")
    private val IMMUTABLE_RESOLVE_PATH = Regex(".*/resolve/[0-9a-f]{40}/[^/]+\\.litertlm$")

    fun requireValidCatalogEntry(model: RecommendedModel) {
        require(model.id.matches(Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$"))) {
            "Recommended model ID is invalid"
        }
        require(model.displayName.isNotBlank()) { "Recommended model name is empty" }
        require(
            model.fileName.endsWith(".litertlm", ignoreCase = true) &&
                '/' !in model.fileName && '\\' !in model.fileName,
        ) { "Recommended model file name is invalid" }
        require(model.expectedSizeBytes in ModelImportPolicy.LITERTLM_MAGIC_BYTES.toLong()..
            ModelImportPolicy.MAXIMUM_MODEL_BYTES) {
            "Recommended model size is outside the import policy"
        }
        require(SHA_256.matches(model.expectedSha256)) {
            "Recommended model SHA-256 is invalid"
        }
        require(model.license.isNotBlank()) { "Recommended model license is empty" }

        val source = URI(model.sourceUrl)
        requireAllowedHttpsUri(source)
        require(source.host.equals(PRIMARY_HOST, ignoreCase = true)) {
            "Recommended model source must use the approved host"
        }

        val download = URI(model.downloadUrl)
        requireAllowedHttpsUri(download)
        require(download.host.equals(PRIMARY_HOST, ignoreCase = true)) {
            "Recommended model download must start on the approved host"
        }
        require(IMMUTABLE_RESOLVE_PATH.matches(download.path)) {
            "Recommended model download must pin an immutable revision"
        }
    }

    fun requireAllowedRedirectTarget(uri: URI) {
        requireAllowedHttpsUri(uri)
        val host = checkNotNull(uri.host).lowercase()
        require(
            host == PRIMARY_HOST ||
                host.endsWith(".huggingface.co") ||
                host == "hf.co" ||
                host.endsWith(".hf.co"),
        ) { "Model download redirect host is not approved" }
    }

    private fun requireAllowedHttpsUri(uri: URI) {
        require(uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true)) {
            "Model downloads require HTTPS"
        }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "Model download URL authority is invalid"
        }
        require(uri.port == -1 || uri.port == 443) { "Model download URL port is invalid" }
    }

    private const val PRIMARY_HOST = "huggingface.co"
}
