package io.github.supermonster003.autojs6.plugin.threestoneai.download

/** What a running download is doing; only the first three stages can be paused (D99). */
internal enum class ModelDownloadStage {
    CHECKING_PARTIAL,
    CONNECTING,
    DOWNLOADING,
    WAITING_FOR_IMPORT,
    IMPORTING,
    ;

    val pausable: Boolean
        get() = this == CHECKING_PARTIAL || this == CONNECTING || this == DOWNLOADING

    /** After the hand-off the import owns the file and its own panel offers cancellation. */
    val cancellable: Boolean
        get() = this != IMPORTING
}

internal sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState

    data class Running(
        val operationId: Long,
        val model: RecommendedModel,
        val stage: ModelDownloadStage,
        val progress: ModelDownloadProgress,
    ) : ModelDownloadState

    data class Pausing(
        val operationId: Long,
        val model: RecommendedModel,
        val progress: ModelDownloadProgress,
    ) : ModelDownloadState

    data class Cancelling(
        val operationId: Long,
        val model: RecommendedModel,
        val progress: ModelDownloadProgress,
    ) : ModelDownloadState

    /** A partial file is kept in private storage and the next start resumes from it. */
    data class Paused(
        val operationId: Long,
        val model: RecommendedModel,
        val progress: ModelDownloadProgress,
    ) : ModelDownloadState

    data class Succeeded(
        val operationId: Long,
        val model: RecommendedModel,
    ) : ModelDownloadState

    data class Cancelled(
        val operationId: Long,
        val model: RecommendedModel,
    ) : ModelDownloadState

    /** [downloadedBytes] is what a retry resumes from; zero means the retry starts over. */
    data class Failed(
        val operationId: Long,
        val model: RecommendedModel,
        val reason: ModelDownloadFailureReason,
        val downloadedBytes: Long,
    ) : ModelDownloadState
}

/** The model a download card is about, or null when there is nothing to show. */
internal val ModelDownloadState.cardModel: RecommendedModel?
    get() = when (this) {
        ModelDownloadState.Idle,
        is ModelDownloadState.Succeeded,
        is ModelDownloadState.Cancelled,
        -> null
        is ModelDownloadState.Running -> model.takeIf { stage != ModelDownloadStage.IMPORTING }
        is ModelDownloadState.Pausing -> model
        is ModelDownloadState.Cancelling -> model
        is ModelDownloadState.Paused -> model
        is ModelDownloadState.Failed -> model
    }

internal class ModelDownloadStateMachine {
    private var nextOperationId = 1L
    private var state: ModelDownloadState = ModelDownloadState.Idle

    @Synchronized
    fun snapshot(): ModelDownloadState = state

    /** Starts or resumes; [initialBytes] is the verified-length prefix already on disk. */
    @Synchronized
    fun begin(model: RecommendedModel, initialBytes: Long): Long? {
        if (isActive(state)) return null
        val operationId = allocateOperationId()
        state = ModelDownloadState.Running(
            operationId = operationId,
            model = model,
            stage = if (initialBytes > 0L) {
                ModelDownloadStage.CHECKING_PARTIAL
            } else {
                ModelDownloadStage.CONNECTING
            },
            progress = ModelDownloadProgress(initialBytes, model.expectedSizeBytes),
        )
        return operationId
    }

    /** Cold start: a partial file left by an earlier process becomes a paused download. */
    @Synchronized
    fun restorePaused(model: RecommendedModel, downloadedBytes: Long): Boolean {
        if (state != ModelDownloadState.Idle) return false
        state = ModelDownloadState.Paused(
            operationId = allocateOperationId(),
            model = model,
            progress = ModelDownloadProgress(downloadedBytes, model.expectedSizeBytes),
        )
        return true
    }

    @Synchronized
    fun updateStage(operationId: Long, stage: ModelDownloadStage): Boolean {
        val running = state as? ModelDownloadState.Running ?: return false
        if (running.operationId != operationId || running.stage == stage) return false
        state = running.copy(stage = stage)
        return true
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

    /** The only way back to zero: the partial file was discarded and the transfer starts over. */
    @Synchronized
    fun restartProgress(operationId: Long): Boolean {
        val running = state as? ModelDownloadState.Running ?: return false
        if (running.operationId != operationId) return false
        state = running.copy(progress = ModelDownloadProgress(0L, running.model.expectedSizeBytes))
        return true
    }

    @Synchronized
    fun beginPause(operationId: Long): Boolean {
        val running = state as? ModelDownloadState.Running ?: return false
        if (running.operationId != operationId || !running.stage.pausable) return false
        state = ModelDownloadState.Pausing(running.operationId, running.model, running.progress)
        return true
    }

    @Synchronized
    fun finishPause(operationId: Long, downloadedBytes: Long): Boolean {
        val pausing = state as? ModelDownloadState.Pausing ?: return false
        if (pausing.operationId != operationId) return false
        state = ModelDownloadState.Paused(
            operationId = operationId,
            model = pausing.model,
            progress = ModelDownloadProgress(downloadedBytes, pausing.model.expectedSizeBytes),
        )
        return true
    }

    @Synchronized
    fun beginCancellation(operationId: Long): Boolean {
        val active = state
        state = when (active) {
            is ModelDownloadState.Running -> {
                if (active.operationId != operationId || !active.stage.cancellable) return false
                ModelDownloadState.Cancelling(active.operationId, active.model, active.progress)
            }
            is ModelDownloadState.Pausing -> {
                if (active.operationId != operationId) return false
                ModelDownloadState.Cancelling(active.operationId, active.model, active.progress)
            }
            else -> return false
        }
        return true
    }

    @Synchronized
    fun finishCancellation(operationId: Long): Boolean {
        val cancelling = state as? ModelDownloadState.Cancelling ?: return false
        if (cancelling.operationId != operationId) return false
        state = ModelDownloadState.Cancelled(operationId, cancelling.model)
        return true
    }

    /** Discards a paused or failed download that has no worker; the caller deletes the file. */
    @Synchronized
    fun discard(operationId: Long): Boolean {
        val model = when (val inactive = state) {
            is ModelDownloadState.Paused -> inactive.takeIf { it.operationId == operationId }?.model
            is ModelDownloadState.Failed -> inactive.takeIf { it.operationId == operationId }?.model
            else -> null
        } ?: return false
        state = ModelDownloadState.Cancelled(operationId, model)
        return true
    }

    /**
     * The import that took over the verified file was cancelled. A file that is still complete
     * becomes a paused download (continuing re-verifies and imports it again); otherwise the
     * download ends as cancelled.
     */
    @Synchronized
    fun finishHandOffCancellation(operationId: Long, keptBytes: Long): Boolean {
        val running = state as? ModelDownloadState.Running ?: return false
        if (running.operationId != operationId || running.stage != ModelDownloadStage.IMPORTING) return false
        require(keptBytes in 0L..running.model.expectedSizeBytes) { "Kept length is invalid" }
        state = if (keptBytes > 0L) {
            ModelDownloadState.Paused(
                operationId = operationId,
                model = running.model,
                progress = ModelDownloadProgress(keptBytes, running.model.expectedSizeBytes),
            )
        } else {
            ModelDownloadState.Cancelled(operationId, running.model)
        }
        return true
    }

    @Synchronized
    fun succeed(operationId: Long): Boolean {
        val running = state as? ModelDownloadState.Running ?: return false
        if (running.operationId != operationId) return false
        state = ModelDownloadState.Succeeded(operationId, running.model)
        return true
    }

    @Synchronized
    fun fail(
        operationId: Long,
        reason: ModelDownloadFailureReason,
        downloadedBytes: Long,
    ): Boolean {
        val model = when (val active = state) {
            is ModelDownloadState.Running -> active.takeIf { it.operationId == operationId }?.model
            is ModelDownloadState.Pausing -> active.takeIf { it.operationId == operationId }?.model
            is ModelDownloadState.Cancelling -> active.takeIf { it.operationId == operationId }?.model
            else -> null
        } ?: return false
        require(downloadedBytes in 0L..model.expectedSizeBytes) { "Resumable length is invalid" }
        state = ModelDownloadState.Failed(operationId, model, reason, downloadedBytes)
        return true
    }

    private fun allocateOperationId(): Long {
        check(nextOperationId > 0L) { "Model download operation IDs are exhausted" }
        val operationId = nextOperationId
        nextOperationId = if (operationId == Long.MAX_VALUE) 0L else operationId + 1L
        return operationId
    }

    private fun isActive(value: ModelDownloadState): Boolean =
        value is ModelDownloadState.Running ||
            value is ModelDownloadState.Pausing ||
            value is ModelDownloadState.Cancelling
}
