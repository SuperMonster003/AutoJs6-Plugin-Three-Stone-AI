package io.github.supermonster003.autojs6.plugin.threestoneai.model

internal enum class ModelImportFailureReason {
    INVALID_FORMAT,
    MODEL_TOO_LARGE,
    INSUFFICIENT_STORAGE,
    SOURCE_UNAVAILABLE,
    INTERRUPTED,
    UNKNOWN,
}

internal class ModelImportFailureException(
    val reason: ModelImportFailureReason,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal fun Throwable.toModelImportFailureReason(): ModelImportFailureReason = when (this) {
    is ModelImportFailureException -> reason
    is InterruptedException -> ModelImportFailureReason.INTERRUPTED
    else -> ModelImportFailureReason.UNKNOWN
}
