package io.github.supermonster003.autojs6.plugin.ondeviceai.download

internal enum class ModelDownloadFailureReason {
    NETWORK_UNAVAILABLE,
    HTTP_ERROR,
    INVALID_CONTENT,
    INTEGRITY_MISMATCH,
    DESTINATION_UNAVAILABLE,
    INTERRUPTED,
    UNKNOWN,
}

internal class ModelDownloadFailureException(
    val reason: ModelDownloadFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal enum class ModelDownloadCleanupResult {
    NOT_NEEDED,
    DELETED,
    TRUNCATED,
    FAILED,
}
