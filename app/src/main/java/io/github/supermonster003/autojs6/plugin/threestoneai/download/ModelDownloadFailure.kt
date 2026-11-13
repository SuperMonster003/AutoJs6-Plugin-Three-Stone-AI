package io.github.supermonster003.autojs6.plugin.threestoneai.download

internal enum class ModelDownloadFailureReason {
    NETWORK_UNAVAILABLE,
    HTTP_ERROR,
    INVALID_CONTENT,
    INTEGRITY_MISMATCH,
    DESTINATION_UNAVAILABLE,
    INSUFFICIENT_STORAGE,
    INTERRUPTED,
    IMPORT_FAILED,
    UNKNOWN,
}

internal class ModelDownloadFailureException(
    val reason: ModelDownloadFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
