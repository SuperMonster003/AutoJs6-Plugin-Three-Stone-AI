package io.github.supermonster003.autojs6.plugin.ai.text.model

internal enum class ModelImportStage {
    VALIDATING,
    COPYING,
    PUBLISHING,
}

internal data class ModelImportProgress(
    val stage: ModelImportStage,
    val processedBytes: Long,
    val totalBytes: Long?,
) {
    init {
        require(processedBytes >= 0L) { "Model import progress cannot be negative" }
        require(totalBytes == null || totalBytes > 0L) { "Model import total must be positive when known" }
        require(totalBytes == null || processedBytes <= totalBytes) {
            "Model import progress cannot exceed its known total"
        }
    }

    companion object {
        fun initial(): ModelImportProgress = ModelImportProgress(
            stage = ModelImportStage.VALIDATING,
            processedBytes = 0L,
            totalBytes = null,
        )
    }
}

internal sealed interface ModelImportState<out T> {
    data object Preparing : ModelImportState<Nothing>
    data object Unavailable : ModelImportState<Nothing>
    data class Ready<T>(val current: T?) : ModelImportState<T>
    data class Running<T>(
        val operationId: Long,
        val previous: T?,
        val progress: ModelImportProgress = ModelImportProgress.initial(),
    ) : ModelImportState<T>
    data class Cancelling<T>(
        val operationId: Long,
        val previous: T?,
        val progress: ModelImportProgress,
    ) : ModelImportState<T>
    data class Succeeded<T>(val operationId: Long, val model: T) : ModelImportState<T>
    data class Cancelled<T>(val operationId: Long, val current: T?) : ModelImportState<T>
    data class Failed<T>(
        val operationId: Long,
        val current: T?,
        val reason: ModelImportFailureReason,
        val retryAllowed: Boolean = true,
    ) : ModelImportState<T>
}

/** Process-local state only; durable crash recovery is owned by [ModelRepository]. */
internal class ModelImportStateMachine<T> {
    private var nextOperationId = 1L
    private var current: T? = null
    private var state: ModelImportState<T> = ModelImportState.Preparing

    @Synchronized
    fun snapshot(): ModelImportState<T> = state

    @Synchronized
    fun finishPreparation(value: T?) {
        check(state === ModelImportState.Preparing) { "Model import preparation already finished" }
        current = value
        state = ModelImportState.Ready(value)
    }

    @Synchronized
    fun failPreparation() {
        check(state === ModelImportState.Preparing) { "Model import preparation already finished" }
        state = ModelImportState.Unavailable
    }

    @Synchronized
    fun begin(): Long? {
        if (
            state === ModelImportState.Preparing || state === ModelImportState.Unavailable ||
            state is ModelImportState.Running<*> || state is ModelImportState.Cancelling<*> ||
            (state as? ModelImportState.Failed<*>)?.retryAllowed == false
        ) {
            return null
        }
        check(nextOperationId > 0L) { "Model import operation IDs are exhausted" }
        val operationId = nextOperationId
        nextOperationId = if (operationId == Long.MAX_VALUE) 0L else operationId + 1L
        state = ModelImportState.Running(operationId, current)
        return operationId
    }

    @Synchronized
    fun updateProgress(operationId: Long, progress: ModelImportProgress): Boolean {
        val running = state as? ModelImportState.Running<T> ?: return false
        if (running.operationId != operationId || running.progress == progress) return false
        require(progress.stage.ordinal >= running.progress.stage.ordinal) {
            "Model import stage cannot move backwards"
        }
        require(progress.processedBytes >= running.progress.processedBytes) {
            "Model import byte progress cannot move backwards"
        }
        state = running.copy(progress = progress)
        return true
    }

    @Synchronized
    fun succeed(operationId: Long, model: T): Boolean {
        if (!isRunning(operationId)) return false
        current = model
        state = ModelImportState.Succeeded(operationId, model)
        return true
    }

    @Synchronized
    fun beginCancellation(operationId: Long): Boolean {
        val running = state as? ModelImportState.Running<T> ?: return false
        if (running.operationId != operationId) return false
        state = ModelImportState.Cancelling(
            operationId = operationId,
            previous = running.previous,
            progress = running.progress,
        )
        return true
    }

    @Synchronized
    fun finishCancellation(operationId: Long, visibleCurrent: T?): Boolean {
        val cancelling = state as? ModelImportState.Cancelling<T> ?: return false
        if (cancelling.operationId != operationId) return false
        current = visibleCurrent
        state = ModelImportState.Cancelled(operationId, visibleCurrent)
        return true
    }

    @Synchronized
    fun isCancellationRequestedOrCompleted(operationId: Long): Boolean = when (val value = state) {
        is ModelImportState.Cancelling -> value.operationId == operationId
        is ModelImportState.Cancelled -> value.operationId == operationId
        else -> false
    }

    @Synchronized
    fun fail(
        operationId: Long,
        visibleCurrent: T?,
        reason: ModelImportFailureReason,
        retryAllowed: Boolean = true,
    ): Boolean {
        if (!isInFlight(operationId)) return false
        current = visibleCurrent
        state = ModelImportState.Failed(operationId, visibleCurrent, reason, retryAllowed)
        return true
    }

    private fun isRunning(operationId: Long): Boolean =
        (state as? ModelImportState.Running<*>)?.operationId == operationId

    private fun isInFlight(operationId: Long): Boolean = when (val value = state) {
        is ModelImportState.Running -> value.operationId == operationId
        is ModelImportState.Cancelling -> value.operationId == operationId
        else -> false
    }
}
