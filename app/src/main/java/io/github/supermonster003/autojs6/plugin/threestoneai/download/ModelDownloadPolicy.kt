package io.github.supermonster003.autojs6.plugin.threestoneai.download

import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportPolicy
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

    /** Name of the partial download kept in private model storage until it is verified (D99). */
    fun partFileName(sha256: String): String {
        require(SHA_256.matches(sha256)) { "Model download digest is invalid" }
        return ".download-$sha256.part"
    }

    /** Returns the pinned digest named by a partial download file, or null for any other name. */
    fun partFileSha256(fileName: String): String? = PART_FILE.matchEntire(fileName)?.groupValues?.get(1)

    /**
     * Length of an existing partial file that a resumed transfer can build on. Anything longer than
     * the pinned size, or too short to hold the LiteRT-LM header, restarts from the first byte.
     */
    fun resumableLength(partLength: Long, expectedSizeBytes: Long): Long = when {
        partLength > expectedSizeBytes -> 0L
        partLength < ModelImportPolicy.LITERTLM_MAGIC_BYTES -> 0L
        else -> partLength
    }

    /** Free space needed before the remaining bytes are fetched, keeping the import reserve free. */
    fun hasStorageFor(usableBytes: Long, remainingBytes: Long): Boolean =
        usableBytes.coerceAtLeast(0L) - ModelImportPolicy.RESERVED_FREE_BYTES >= remainingBytes

    /** Failures that leave a partial file worth resuming; content and digest failures start over. */
    fun keepsPartialFile(reason: ModelDownloadFailureReason): Boolean = when (reason) {
        ModelDownloadFailureReason.INVALID_CONTENT,
        ModelDownloadFailureReason.INTEGRITY_MISMATCH,
        -> false
        ModelDownloadFailureReason.NETWORK_UNAVAILABLE,
        ModelDownloadFailureReason.HTTP_ERROR,
        ModelDownloadFailureReason.DESTINATION_UNAVAILABLE,
        ModelDownloadFailureReason.INSUFFICIENT_STORAGE,
        ModelDownloadFailureReason.INTERRUPTED,
        ModelDownloadFailureReason.IMPORT_FAILED,
        ModelDownloadFailureReason.UNKNOWN,
        -> true
    }

    /**
     * D107: warns before a download when the device memory is below the model's stated minimum.
     * The kernel reserves part of the installed memory, so a phone sold with N GB reports roughly
     * 90 percent of it; the 85 percent tolerance keeps such a phone from being flagged.
     */
    fun isBelowMemoryRequirement(deviceTotalBytes: Long, minimumMemoryGb: Int): Boolean {
        if (deviceTotalBytes <= 0L || minimumMemoryGb <= 0) return false
        val required = minimumMemoryGb.toLong() * BYTES_PER_GIB / 100L * MEMORY_TOLERANCE_PERCENT
        return deviceTotalBytes < required
    }

    private const val PRIMARY_HOST = "huggingface.co"
    private const val BYTES_PER_GIB = 1024L * 1024L * 1024L
    private const val MEMORY_TOLERANCE_PERCENT = 85L
    private val PART_FILE = Regex("^\\.download-([0-9a-f]{64})\\.part$")
}
