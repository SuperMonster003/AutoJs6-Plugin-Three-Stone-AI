package io.github.supermonster003.autojs6.plugin.ai.text.model

internal sealed interface ModelImportState<out T> {
    data object Preparing : ModelImportState<Nothing>
    data object Unavailable : ModelImportState<Nothing>
    data class Ready<T>(val current: T?) : ModelImportState<T>
    data class Running<T>(val operationId: Long, val previous: T?) : ModelImportState<T>
    data class Succeeded<T>(val operationId: Long, val model: T) : ModelImportState<T>
    data class Failed<T>(
        val operationId: Long,
        val current: T?,
        val reason: ModelImportFailureReason,
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
            state is ModelImportState.Running<*>
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
    fun succeed(operationId: Long, model: T): Boolean {
        if (!isRunning(operationId)) return false
        current = model
        state = ModelImportState.Succeeded(operationId, model)
        return true
    }

    @Synchronized
    fun fail(
        operationId: Long,
        visibleCurrent: T?,
        reason: ModelImportFailureReason,
    ): Boolean {
        if (!isRunning(operationId)) return false
        current = visibleCurrent
        state = ModelImportState.Failed(operationId, visibleCurrent, reason)
        return true
    }

    private fun isRunning(operationId: Long): Boolean =
        (state as? ModelImportState.Running<*>)?.operationId == operationId
}
