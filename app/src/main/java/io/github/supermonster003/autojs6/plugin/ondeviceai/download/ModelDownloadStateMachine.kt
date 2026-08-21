package io.github.supermonster003.autojs6.plugin.ondeviceai.download

internal sealed interface ModelDownloadState<out D> {
    data object Idle : ModelDownloadState<Nothing>

    data class Running<D>(
        val operationId: Long,
        val model: RecommendedModel,
        val destination: D,
        val grantedFlags: Int,
        val progress: ModelDownloadProgress,
    ) : ModelDownloadState<D>

    data class Cancelling<D>(
        val operationId: Long,
        val model: RecommendedModel,
        val destination: D,
        val grantedFlags: Int,
        val progress: ModelDownloadProgress,
    ) : ModelDownloadState<D>

    data class Succeeded<D>(
        val operationId: Long,
        val model: RecommendedModel,
        val destination: D,
        val grantedFlags: Int,
    ) : ModelDownloadState<D>

    data class Cancelled(
        val operationId: Long,
        val model: RecommendedModel,
        val cleanup: ModelDownloadCleanupResult,
    ) : ModelDownloadState<Nothing>

    data class Failed(
        val operationId: Long,
        val model: RecommendedModel,
        val reason: ModelDownloadFailureReason,
        val cleanup: ModelDownloadCleanupResult,
    ) : ModelDownloadState<Nothing>
}

internal class ModelDownloadStateMachine<D> {
    private var nextOperationId = 1L
    private var state: ModelDownloadState<D> = ModelDownloadState.Idle

    @Synchronized
    fun snapshot(): ModelDownloadState<D> = state

    @Synchronized
    fun begin(model: RecommendedModel, destination: D, grantedFlags: Int): Long? {
        if (state is ModelDownloadState.Running || state is ModelDownloadState.Cancelling) {
            return null
        }
        check(nextOperationId > 0L) { "Model download operation IDs are exhausted" }
        val operationId = nextOperationId
        nextOperationId = if (operationId == Long.MAX_VALUE) 0L else operationId + 1L
        state = ModelDownloadState.Running(
            operationId = operationId,
            model = model,
            destination = destination,
            grantedFlags = grantedFlags,
            progress = ModelDownloadProgress(0L, model.expectedSizeBytes),
        )
        return operationId
    }

    @Synchronized
    fun updateProgress(operationId: Long, progress: ModelDownloadProgress): Boolean {
        val running = state as? ModelDownloadState.Running ?: return false
        if (running.operationId != operationId || running.progress == progress) return false
        require(progress.totalBytes == running.model.expectedSizeBytes) {
            "Model download progress total changed"
        }
        require(progress.processedBytes >= running.progress.processedBytes) {
            "Model download progress moved backwards"
        }
        state = running.copy(progress = progress)
        return true
    }

    @Synchronized
    fun beginCancellation(operationId: Long): Boolean {
        val running = state as? ModelDownloadState.Running ?: return false
        if (running.operationId != operationId) return false
        state = ModelDownloadState.Cancelling(
            operationId = running.operationId,
            model = running.model,
            destination = running.destination,
            grantedFlags = running.grantedFlags,
            progress = running.progress,
        )
        return true
    }

    @Synchronized
    fun succeed(operationId: Long): Boolean {
        val running = state as? ModelDownloadState.Running ?: return false
        if (running.operationId != operationId) return false
        state = ModelDownloadState.Succeeded(
            operationId = running.operationId,
            model = running.model,
            destination = running.destination,
            grantedFlags = running.grantedFlags,
        )
        return true
    }

    @Synchronized
    fun finishCancellation(
        operationId: Long,
        cleanup: ModelDownloadCleanupResult,
    ): Boolean {
        val cancelling = state as? ModelDownloadState.Cancelling ?: return false
        if (cancelling.operationId != operationId) return false
        state = ModelDownloadState.Cancelled(
            operationId = operationId,
            model = cancelling.model,
            cleanup = cleanup,
        )
        return true
    }

    @Synchronized
    fun fail(
        operationId: Long,
        reason: ModelDownloadFailureReason,
        cleanup: ModelDownloadCleanupResult,
    ): Boolean {
        val active = state
        val model = when (active) {
            is ModelDownloadState.Running -> active.takeIf { it.operationId == operationId }?.model
            is ModelDownloadState.Cancelling -> active.takeIf { it.operationId == operationId }?.model
            else -> null
        } ?: return false
        state = ModelDownloadState.Failed(operationId, model, reason, cleanup)
        return true
    }
}
