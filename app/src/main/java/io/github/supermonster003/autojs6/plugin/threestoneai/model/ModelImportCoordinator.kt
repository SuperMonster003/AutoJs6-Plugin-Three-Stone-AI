package io.github.supermonster003.autojs6.plugin.threestoneai.model

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmModelHealthChecker
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.ModelHealthChecker
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.liteRtLmCacheDirectory
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal sealed interface ModelSelectionState {
    data object Idle : ModelSelectionState
    data class Selecting(val operationId: Long, val modelId: String) : ModelSelectionState
    data class Succeeded(val operationId: Long, val modelId: String) : ModelSelectionState
    data class Failed(val operationId: Long, val modelId: String) : ModelSelectionState
}

internal sealed interface ModelDeletionState {
    data object Idle : ModelDeletionState
    data class Deleting(val operationId: Long, val modelId: String) : ModelDeletionState
    data class Succeeded(val operationId: Long, val modelId: String) : ModelDeletionState
    data class Failed(val operationId: Long, val modelId: String) : ModelDeletionState
}

internal sealed interface ModelRenameState {
    data object Idle : ModelRenameState
    data class Renaming(
        val operationId: Long,
        val modelId: String,
        val displayName: String,
    ) : ModelRenameState
    data class Succeeded(
        val operationId: Long,
        val modelId: String,
        val displayName: String,
    ) : ModelRenameState
    data class Failed(val operationId: Long, val modelId: String) : ModelRenameState
}

internal sealed interface ModelStorageCleanupState {
    data object Idle : ModelStorageCleanupState
    data class Cleaning(val operationId: Long) : ModelStorageCleanupState
    data class Succeeded(
        val operationId: Long,
        val removedFileCount: Int,
        val releasedBytes: Long,
    ) : ModelStorageCleanupState {
        init {
            require(removedFileCount >= 0) { "Removed model file count cannot be negative" }
            require(releasedBytes >= 0L) { "Released model storage cannot be negative" }
        }
    }
    data class Failed(val operationId: Long) : ModelStorageCleanupState
}

internal sealed interface ModelHealthCheckState {
    data object Idle : ModelHealthCheckState
    data class Checking(val operationId: Long, val modelId: String) : ModelHealthCheckState
    data class Succeeded(
        val operationId: Long,
        val modelId: String,
        val status: ModelHealthStatus,
    ) : ModelHealthCheckState {
        init {
            require(status.isChecked) { "A successful health operation must have a terminal status" }
        }
    }
    data class Failed(val operationId: Long, val modelId: String) : ModelHealthCheckState
}

internal data class ModelManagerState(
    val importState: ModelImportState<ImportedModel>,
    val snapshot: ModelManagerSnapshot?,
    val selection: ModelSelectionState,
    val deletion: ModelDeletionState = ModelDeletionState.Idle,
    val rename: ModelRenameState = ModelRenameState.Idle,
    val storageCleanup: ModelStorageCleanupState = ModelStorageCleanupState.Idle,
    val healthCheck: ModelHealthCheckState = ModelHealthCheckState.Idle,
)

/**
 * Serializes process-local model imports, selections, and deletions independently of any Activity
 * instance. A process death is handled by repository recovery when the manager next cold-starts.
 */
internal class ModelImportCoordinator private constructor(context: Context) {
    fun interface Observer {
        fun onStateChanged(state: ModelImportState<ImportedModel>)
    }

    fun interface ManagerObserver {
        fun onStateChanged(state: ModelManagerState)
    }

    private val applicationContext = context.applicationContext
    private val repository = ModelRepository(applicationContext)
    private val healthChecker: ModelHealthChecker = LiteRtLmModelHealthChecker(
        cacheDirectory = liteRtLmCacheDirectory(applicationContext.cacheDir),
    )
    private val stateMachine = ModelImportStateMachine<ImportedModel>()
    private val observers = CopyOnWriteArraySet<Observer>()
    private val managerObservers = CopyOnWriteArraySet<ManagerObserver>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleLock = Any()
    private val publishScheduled = AtomicBoolean(false)
    private var activeImport: ActiveImport? = null
    private var activeSelection: ActiveSelection? = null
    private var activeDeletion: ActiveDeletion? = null
    private var activeRename: ActiveRename? = null
    private var activeStorageCleanup: ActiveStorageCleanup? = null
    private var activeHealthCheck: ActiveHealthCheck? = null
    private var nextSelectionOperationId = 1L
    private var nextDeletionOperationId = 1L
    private var nextRenameOperationId = 1L
    private var nextStorageCleanupOperationId = 1L
    private var nextHealthCheckOperationId = 1L
    private var visibleManagerSnapshot: ModelManagerSnapshot? = null
    private var selectionState: ModelSelectionState = ModelSelectionState.Idle
    private var deletionState: ModelDeletionState = ModelDeletionState.Idle
    private var renameState: ModelRenameState = ModelRenameState.Idle
    private var storageCleanupState: ModelStorageCleanupState = ModelStorageCleanupState.Idle
    private var healthCheckState: ModelHealthCheckState = ModelHealthCheckState.Idle
    private var catalogMutationAvailable = false
    val processSessionToken: String = UUID.randomUUID().toString()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litertlm-model-manager").apply { isDaemon = true }
    }
    private val cancellationExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1),
        { runnable ->
            Thread(runnable, "litertlm-model-import-cancel").apply { isDaemon = true }
        },
        ThreadPoolExecutor.AbortPolicy(),
    )

    init {
        try {
            executor.execute {
                runCatching {
                    releaseStaleImportPermissions()
                    repository.recoverInterruptedImportFromManagerColdStart()
                    repository.managerSnapshot()
                }.fold(
                    onSuccess = { snapshot ->
                        synchronized(lifecycleLock) {
                            visibleManagerSnapshot = snapshot
                            stateMachine.finishPreparation(snapshot.selectedModel)
                            catalogMutationAvailable = true
                        }
                    },
                    onFailure = { error ->
                        logFailure(operationId = null, ModelImportFailureReason.UNKNOWN, error)
                        synchronized(lifecycleLock) {
                            visibleManagerSnapshot = null
                            catalogMutationAvailable = false
                            stateMachine.failPreparation()
                        }
                    },
                )
                publishState()
            }
        } catch (error: RejectedExecutionException) {
            logFailure(operationId = null, ModelImportFailureReason.UNKNOWN, error)
            synchronized(lifecycleLock) {
                visibleManagerSnapshot = null
                catalogMutationAvailable = false
                stateMachine.failPreparation()
            }
            publishState()
        }
    }

    fun attach(observer: Observer): ModelImportState<ImportedModel> {
        observers += observer
        return stateMachine.snapshot()
    }

    fun detach(observer: Observer) {
        observers -= observer
    }

    fun snapshot(): ModelImportState<ImportedModel> = stateMachine.snapshot()

    fun attachManager(observer: ManagerObserver): ModelManagerState {
        managerObservers += observer
        return managerState()
    }

    fun detachManager(observer: ManagerObserver) {
        managerObservers -= observer
    }

    fun managerState(): ModelManagerState = synchronized(lifecycleLock) {
        ModelManagerState(
            importState = stateMachine.snapshot(),
            snapshot = visibleManagerSnapshot,
            selection = selectionState,
            deletion = deletionState,
            rename = renameState,
            storageCleanup = storageCleanupState,
            healthCheck = healthCheckState,
        )
    }

    /**
     * Selects only a model in the last coherent manager snapshot. Selection and import reserve the
     * same process-local ownership gate and execute on the same serial worker.
     */
    fun beginSelection(modelId: String): Boolean {
        val active = synchronized(lifecycleLock) {
            if (!catalogMutationAvailable || hasActiveManagerOperationLocked()) return false
            val snapshot = visibleManagerSnapshot ?: return false
            if (snapshot.models.none { it.modelId == modelId }) return false
            if (snapshot.selectedModelId == modelId) return false
            check(nextSelectionOperationId > 0L) { "Model selection operation IDs are exhausted" }
            val operationId = nextSelectionOperationId
            nextSelectionOperationId = if (operationId == Long.MAX_VALUE) 0L else operationId + 1L
            ActiveSelection(operationId, modelId).also { operation ->
                activeSelection = operation
                selectionState = ModelSelectionState.Selecting(operationId, modelId)
                deletionState = ModelDeletionState.Idle
                renameState = ModelRenameState.Idle
                storageCleanupState = ModelStorageCleanupState.Idle
                healthCheckState = ModelHealthCheckState.Idle
            }
        }
        publishState()
        try {
            executor.execute { executeSelection(active) }
        } catch (error: RejectedExecutionException) {
            logSelectionFailure(active, error)
            synchronized(lifecycleLock) {
                if (activeSelection === active) {
                    activeSelection = null
                    selectionState = ModelSelectionState.Failed(active.operationId, active.modelId)
                }
            }
            publishState()
        }
        return true
    }

    /** Deletes only an unselected model from the last coherent manager snapshot. */
    fun beginDeletion(modelId: String): Boolean {
        val active = synchronized(lifecycleLock) {
            if (!catalogMutationAvailable || hasActiveManagerOperationLocked()) return false
            val snapshot = visibleManagerSnapshot ?: return false
            if (snapshot.models.none { it.modelId == modelId }) return false
            if (snapshot.selectedModelId == modelId) return false
            check(nextDeletionOperationId > 0L) { "Model deletion operation IDs are exhausted" }
            val operationId = nextDeletionOperationId
            nextDeletionOperationId = if (operationId == Long.MAX_VALUE) 0L else operationId + 1L
            ActiveDeletion(operationId, modelId).also { operation ->
                activeDeletion = operation
                selectionState = ModelSelectionState.Idle
                deletionState = ModelDeletionState.Deleting(operationId, modelId)
                renameState = ModelRenameState.Idle
                storageCleanupState = ModelStorageCleanupState.Idle
                healthCheckState = ModelHealthCheckState.Idle
            }
        }
        publishState()
        try {
            executor.execute { executeDeletion(active) }
        } catch (error: RejectedExecutionException) {
            logDeletionFailure(active, error)
            synchronized(lifecycleLock) {
                if (activeDeletion === active) {
                    activeDeletion = null
                    deletionState = ModelDeletionState.Failed(active.operationId, active.modelId)
                }
            }
            publishState()
        }
        return true
    }

    /** Renames only a model in the last coherent snapshot while retaining its stable model ID. */
    fun beginRename(modelId: String, displayName: String): Boolean {
        val normalizedName = runCatching {
            ModelDisplayNamePolicy.normalizeUserInput(displayName)
        }.getOrNull() ?: return false
        val active = synchronized(lifecycleLock) {
            if (!catalogMutationAvailable || hasActiveManagerOperationLocked()) return false
            val snapshot = visibleManagerSnapshot ?: return false
            if (snapshot.models.none { it.modelId == modelId }) return false
            check(nextRenameOperationId > 0L) { "Model rename operation IDs are exhausted" }
            val operationId = nextRenameOperationId
            nextRenameOperationId = if (operationId == Long.MAX_VALUE) 0L else operationId + 1L
            ActiveRename(operationId, modelId, normalizedName).also { operation ->
                activeRename = operation
                selectionState = ModelSelectionState.Idle
                deletionState = ModelDeletionState.Idle
                renameState = ModelRenameState.Renaming(operationId, modelId, normalizedName)
                storageCleanupState = ModelStorageCleanupState.Idle
                healthCheckState = ModelHealthCheckState.Idle
            }
        }
        publishState()
        try {
            executor.execute { executeRename(active) }
        } catch (error: RejectedExecutionException) {
            logRenameFailure(active, error)
            synchronized(lifecycleLock) {
                if (activeRename === active) {
                    activeRename = null
                    renameState = ModelRenameState.Failed(active.operationId, active.modelId)
                }
            }
            publishState()
        }
        return true
    }

    /** Reclaims only catalog-unreferenced hash model files on the serial manager worker. */
    fun beginStorageCleanup(): Boolean {
        val active = synchronized(lifecycleLock) {
            if (!catalogMutationAvailable || hasActiveManagerOperationLocked()) return false
            if (visibleManagerSnapshot == null) return false
            check(nextStorageCleanupOperationId > 0L) {
                "Model storage cleanup operation IDs are exhausted"
            }
            val operationId = nextStorageCleanupOperationId
            nextStorageCleanupOperationId = if (operationId == Long.MAX_VALUE) 0L else operationId + 1L
            ActiveStorageCleanup(operationId).also { operation ->
                activeStorageCleanup = operation
                selectionState = ModelSelectionState.Idle
                deletionState = ModelDeletionState.Idle
                renameState = ModelRenameState.Idle
                storageCleanupState = ModelStorageCleanupState.Cleaning(operationId)
                healthCheckState = ModelHealthCheckState.Idle
            }
        }
        publishState()
        try {
            executor.execute { executeStorageCleanup(active) }
        } catch (error: RejectedExecutionException) {
            logStorageCleanupFailure(active, error)
            synchronized(lifecycleLock) {
                if (activeStorageCleanup === active) {
                    activeStorageCleanup = null
                    storageCleanupState = ModelStorageCleanupState.Failed(active.operationId)
                }
            }
            publishState()
        }
        return true
    }

    /** Runs one explicit initialization probe without retaining an Engine in the manager process. */
    fun beginHealthCheck(modelId: String): Boolean {
        val active = synchronized(lifecycleLock) {
            if (!catalogMutationAvailable || hasActiveManagerOperationLocked()) return false
            val snapshot = visibleManagerSnapshot ?: return false
            val model = snapshot.models.singleOrNull { it.modelId == modelId } ?: return false
            check(nextHealthCheckOperationId > 0L) {
                "Model health-check operation IDs are exhausted"
            }
            val operationId = nextHealthCheckOperationId
            nextHealthCheckOperationId = if (operationId == Long.MAX_VALUE) 0L else operationId + 1L
            ActiveHealthCheck(operationId, model).also { operation ->
                activeHealthCheck = operation
                selectionState = ModelSelectionState.Idle
                deletionState = ModelDeletionState.Idle
                renameState = ModelRenameState.Idle
                storageCleanupState = ModelStorageCleanupState.Idle
                healthCheckState = ModelHealthCheckState.Checking(operationId, modelId)
            }
        }
        publishState()
        try {
            executor.execute { executeHealthCheck(active) }
        } catch (error: RejectedExecutionException) {
            logHealthCheckFailure(active, error)
            synchronized(lifecycleLock) {
                if (activeHealthCheck === active) {
                    activeHealthCheck = null
                    healthCheckState = ModelHealthCheckState.Failed(active.operationId, active.model.modelId)
                }
            }
            publishState()
        }
        return true
    }

    fun beginImport(uri: Uri, grantedFlags: Int, checkAfterImport: Boolean): Boolean {
        val active = synchronized(lifecycleLock) {
            if (!catalogMutationAvailable || hasActiveManagerOperationLocked()) return false
            val operationId = stateMachine.begin() ?: return false
            val running = stateMachine.snapshot() as ModelImportState.Running<ImportedModel>
            val persistedReadPermission = persistReadPermission(uri, grantedFlags)
            val control = ModelImportOperationControl { progress ->
                val changed = synchronized(lifecycleLock) {
                    stateMachine.updateProgress(operationId, progress)
                }
                if (changed) publishState()
            }
            val operation = ActiveImport(
                operationId = operationId,
                uri = uri,
                previous = running.previous,
                persistedReadPermission = persistedReadPermission,
                control = control,
                checkAfterImport = checkAfterImport,
            )
            activeImport = operation
            selectionState = ModelSelectionState.Idle
            deletionState = ModelDeletionState.Idle
            renameState = ModelRenameState.Idle
            storageCleanupState = ModelStorageCleanupState.Idle
            healthCheckState = ModelHealthCheckState.Idle
            operation
        }
        publishState()
        try {
            executor.execute {
                executeImport(active)
            }
        } catch (error: RejectedExecutionException) {
            val reason = ModelImportFailureReason.UNKNOWN
            logFailure(active.operationId, reason, error)
            val stateChanged = synchronized(lifecycleLock) {
                if (active.control.isCancellationRequested()) {
                    stateMachine.finishCancellation(active.operationId, active.previous)
                } else {
                    stateMachine.fail(active.operationId, active.previous, reason)
                }
            }
            active.finish()
            if (stateChanged) publishState()
            return true
        }
        return true
    }

    /** Cancels only the exact operation rendered by the caller; a stale UI cannot cancel its successor. */
    fun cancelImport(operationId: Long): Boolean {
        lateinit var active: ActiveImport
        val accepted = synchronized(lifecycleLock) {
            active = activeImport?.takeIf { it.operationId == operationId }
                ?: return stateMachine.isCancellationRequestedOrCompleted(operationId)
            val running = stateMachine.snapshot() as? ModelImportState.Running<ImportedModel>
            if (running?.operationId != operationId) {
                return stateMachine.isCancellationRequestedOrCompleted(operationId)
            }
            active.control.requestCancellation {
                check(stateMachine.beginCancellation(operationId)) {
                    "Accepted cancellation must have a matching running import"
                }
            }
        }
        if (!accepted) return stateMachine.isCancellationRequestedOrCompleted(operationId)
        active.interruptWorker()
        active.closeSourceAsync()
        publishState()
        return true
    }

    private fun executeImport(active: ActiveImport) {
        val worker = Thread.currentThread()
        active.attachWorker(worker)
        var imported: ImportedModel? = null
        var importFailure: Throwable? = null
        try {
            active.control.ensureActive()
            imported = repository.importFrom(active.uri, active.control)
            if (active.checkAfterImport) {
                val publishedModel = checkNotNull(imported)
                active.control.reportProgress(
                    ModelImportProgress(
                        stage = ModelImportStage.CHECKING_COMPATIBILITY,
                        processedBytes = publishedModel.sizeBytes,
                        totalBytes = publishedModel.sizeBytes,
                    ),
                )
                val healthStatus = probeModelHealth(
                    operationId = active.operationId,
                    model = publishedModel,
                )
                runCatching {
                    repository.recordHealthStatus(publishedModel.modelId, healthStatus)
                }.onSuccess { checkedSnapshot ->
                    imported = checkedSnapshot.models.single { it.modelId == publishedModel.modelId }
                }.onFailure { error ->
                    logHealthStatusPersistenceFailure(
                        operationId = active.operationId,
                        modelId = publishedModel.modelId,
                        error = error,
                    )
                }
            }
        } catch (error: Throwable) {
            importFailure = error
        } finally {
            active.detachWorker(worker)
            Thread.interrupted()
            val recoveryFailure = runCatching {
                repository.recoverInterruptedImportAfterWorker()
            }.exceptionOrNull()
            val snapshotResult = runCatching { repository.managerSnapshot() }
            val refreshedSnapshot = snapshotResult.getOrNull()
            val snapshotFailure = snapshotResult.exceptionOrNull() ?: if (
                recoveryFailure == null && imported != null &&
                refreshedSnapshot?.selectedModel?.modelId != imported.modelId
            ) {
                IllegalStateException("Imported model is not the authoritative catalog selection")
            } else {
                null
            }
            recoveryFailure?.let { error ->
                logFailure(active.operationId, ModelImportFailureReason.UNKNOWN, error)
            }
            snapshotFailure?.let { error ->
                logFailure(active.operationId, ModelImportFailureReason.UNKNOWN, error)
            }
            val stateChanged = synchronized(lifecycleLock) {
                when {
                    recoveryFailure != null || snapshotFailure != null -> {
                        catalogMutationAvailable = false
                        visibleManagerSnapshot = refreshedSnapshot.takeIf { snapshotFailure == null }
                        stateMachine.fail(
                            operationId = active.operationId,
                            visibleCurrent = visibleManagerSnapshot?.selectedModel,
                            reason = ModelImportFailureReason.UNKNOWN,
                            retryAllowed = false,
                        )
                    }
                    active.control.isCancellationRequested() -> {
                        val snapshot = checkNotNull(refreshedSnapshot)
                        visibleManagerSnapshot = snapshot
                        catalogMutationAvailable = true
                        stateMachine.finishCancellation(active.operationId, snapshot.selectedModel)
                    }
                    imported != null -> {
                        val snapshot = checkNotNull(refreshedSnapshot)
                        val selected = checkNotNull(snapshot.selectedModel)
                        visibleManagerSnapshot = snapshot
                        catalogMutationAvailable = true
                        stateMachine.succeed(active.operationId, selected)
                    }
                    else -> {
                        val error = checkNotNull(importFailure)
                        val reason = error.toModelImportFailureReason()
                        logFailure(active.operationId, reason, error)
                        val snapshot = checkNotNull(refreshedSnapshot)
                        visibleManagerSnapshot = snapshot
                        catalogMutationAvailable = true
                        stateMachine.fail(active.operationId, snapshot.selectedModel, reason)
                    }
                }
            }
            active.finish()
            if (stateChanged) publishState()
        }
    }

    private fun executeSelection(active: ActiveSelection) {
        val result = runCatching { repository.selectExisting(active.modelId) }
        result.exceptionOrNull()?.let { error -> logSelectionFailure(active, error) }
        val reread = if (result.isFailure) runCatching { repository.managerSnapshot() } else null
        reread?.exceptionOrNull()?.let { error -> logSelectionFailure(active, error) }
        synchronized(lifecycleLock) {
            if (activeSelection !== active) return
            result.fold(
                onSuccess = { snapshot ->
                    if (stateMachine.replaceCurrent(snapshot.selectedModel)) {
                        visibleManagerSnapshot = snapshot
                        catalogMutationAvailable = true
                        selectionState = ModelSelectionState.Succeeded(active.operationId, active.modelId)
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        stateMachine.makeUnavailable()
                        selectionState = ModelSelectionState.Failed(active.operationId, active.modelId)
                    }
                },
                onFailure = {
                    val refreshed = reread?.getOrNull()
                    if (refreshed != null && stateMachine.replaceCurrent(refreshed.selectedModel)) {
                        visibleManagerSnapshot = refreshed
                        catalogMutationAvailable = true
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        check(stateMachine.makeUnavailable()) {
                            "A selection refresh can fail closed only while no import is active"
                        }
                    }
                    selectionState = ModelSelectionState.Failed(active.operationId, active.modelId)
                },
            )
            activeSelection = null
        }
        publishState()
    }

    private fun executeDeletion(active: ActiveDeletion) {
        val result = runCatching {
            repository.deleteUnselected(active.modelId).also { deletion ->
                check(deletion.deletedModel.modelId == active.modelId) {
                    "Deleted model does not match the requested model"
                }
                check(deletion.snapshot.models.none { it.modelId == active.modelId }) {
                    "Deleted model is still present in the manager snapshot"
                }
            }.snapshot
        }
        result.exceptionOrNull()?.let { error -> logDeletionFailure(active, error) }
        val reread = if (result.isFailure) runCatching { repository.managerSnapshot() } else null
        reread?.exceptionOrNull()?.let { error -> logDeletionFailure(active, error) }
        synchronized(lifecycleLock) {
            if (activeDeletion !== active) return
            result.fold(
                onSuccess = { snapshot ->
                    if (stateMachine.replaceCurrent(snapshot.selectedModel)) {
                        visibleManagerSnapshot = snapshot
                        catalogMutationAvailable = true
                        deletionState = ModelDeletionState.Succeeded(active.operationId, active.modelId)
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        stateMachine.makeUnavailable()
                        deletionState = ModelDeletionState.Failed(active.operationId, active.modelId)
                    }
                },
                onFailure = {
                    val refreshed = reread?.getOrNull()
                    if (refreshed != null && stateMachine.replaceCurrent(refreshed.selectedModel)) {
                        visibleManagerSnapshot = refreshed
                        catalogMutationAvailable = true
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        check(stateMachine.makeUnavailable()) {
                            "A deletion refresh can fail closed only while no import is active"
                        }
                    }
                    deletionState = ModelDeletionState.Failed(active.operationId, active.modelId)
                },
            )
            activeDeletion = null
        }
        publishState()
    }

    private fun executeRename(active: ActiveRename) {
        val result = runCatching {
            repository.renameModel(active.modelId, active.displayName).also { snapshot ->
                check(snapshot.models.single { it.modelId == active.modelId }.displayName == active.displayName) {
                    "Renamed model display name is not authoritative"
                }
            }
        }
        result.exceptionOrNull()?.let { error -> logRenameFailure(active, error) }
        val reread = if (result.isFailure) runCatching { repository.managerSnapshot() } else null
        reread?.exceptionOrNull()?.let { error -> logRenameFailure(active, error) }
        synchronized(lifecycleLock) {
            if (activeRename !== active) return
            result.fold(
                onSuccess = { snapshot ->
                    if (stateMachine.replaceCurrent(snapshot.selectedModel)) {
                        visibleManagerSnapshot = snapshot
                        catalogMutationAvailable = true
                        renameState = ModelRenameState.Succeeded(
                            operationId = active.operationId,
                            modelId = active.modelId,
                            displayName = active.displayName,
                        )
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        stateMachine.makeUnavailable()
                        renameState = ModelRenameState.Failed(active.operationId, active.modelId)
                    }
                },
                onFailure = {
                    val refreshed = reread?.getOrNull()
                    if (refreshed != null && stateMachine.replaceCurrent(refreshed.selectedModel)) {
                        visibleManagerSnapshot = refreshed
                        catalogMutationAvailable = true
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        check(stateMachine.makeUnavailable()) {
                            "A rename refresh can fail closed only while no import is active"
                        }
                    }
                    renameState = ModelRenameState.Failed(active.operationId, active.modelId)
                },
            )
            activeRename = null
        }
        publishState()
    }

    private fun executeStorageCleanup(active: ActiveStorageCleanup) {
        val result = runCatching { repository.cleanUnreferencedModelFiles() }
        result.exceptionOrNull()?.let { error -> logStorageCleanupFailure(active, error) }
        val reread = if (result.isFailure) runCatching { repository.managerSnapshot() } else null
        reread?.exceptionOrNull()?.let { error -> logStorageCleanupFailure(active, error) }
        synchronized(lifecycleLock) {
            if (activeStorageCleanup !== active) return
            result.fold(
                onSuccess = { cleanup ->
                    if (stateMachine.replaceCurrent(cleanup.snapshot.selectedModel)) {
                        visibleManagerSnapshot = cleanup.snapshot
                        catalogMutationAvailable = true
                        storageCleanupState = ModelStorageCleanupState.Succeeded(
                            operationId = active.operationId,
                            removedFileCount = cleanup.removedFileCount,
                            releasedBytes = cleanup.releasedBytes,
                        )
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        stateMachine.makeUnavailable()
                        storageCleanupState = ModelStorageCleanupState.Failed(active.operationId)
                    }
                },
                onFailure = {
                    val refreshed = reread?.getOrNull()
                    if (refreshed != null && stateMachine.replaceCurrent(refreshed.selectedModel)) {
                        visibleManagerSnapshot = refreshed
                        catalogMutationAvailable = true
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        check(stateMachine.makeUnavailable()) {
                            "A storage cleanup refresh can fail closed only while no import is active"
                        }
                    }
                    storageCleanupState = ModelStorageCleanupState.Failed(active.operationId)
                },
            )
            activeStorageCleanup = null
        }
        publishState()
    }

    private fun executeHealthCheck(active: ActiveHealthCheck) {
        val status = probeModelHealth(active.operationId, active.model)
        val result = runCatching {
            repository.recordHealthStatus(active.model.modelId, status).also { snapshot ->
                check(snapshot.models.single { it.modelId == active.model.modelId }.healthStatus == status) {
                    "Model health status is not authoritative"
                }
            }
        }
        result.exceptionOrNull()?.let { error -> logHealthCheckFailure(active, error) }
        val reread = if (result.isFailure) runCatching { repository.managerSnapshot() } else null
        reread?.exceptionOrNull()?.let { error -> logHealthCheckFailure(active, error) }
        synchronized(lifecycleLock) {
            if (activeHealthCheck !== active) return
            result.fold(
                onSuccess = { snapshot ->
                    if (stateMachine.replaceCurrent(snapshot.selectedModel)) {
                        visibleManagerSnapshot = snapshot
                        catalogMutationAvailable = true
                        healthCheckState = ModelHealthCheckState.Succeeded(
                            operationId = active.operationId,
                            modelId = active.model.modelId,
                            status = status,
                        )
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        stateMachine.makeUnavailable()
                        healthCheckState = ModelHealthCheckState.Failed(
                            active.operationId,
                            active.model.modelId,
                        )
                    }
                },
                onFailure = {
                    val refreshed = reread?.getOrNull()
                    if (refreshed != null && stateMachine.replaceCurrent(refreshed.selectedModel)) {
                        visibleManagerSnapshot = refreshed
                        catalogMutationAvailable = true
                    } else {
                        visibleManagerSnapshot = null
                        catalogMutationAvailable = false
                        check(stateMachine.makeUnavailable()) {
                            "A health-check refresh can fail closed only while no import is active"
                        }
                    }
                    healthCheckState = ModelHealthCheckState.Failed(
                        active.operationId,
                        active.model.modelId,
                    )
                },
            )
            activeHealthCheck = null
        }
        publishState()
    }

    private fun probeModelHealth(operationId: Long, model: ImportedModel): ModelHealthStatus = try {
        healthChecker.requireInitializable(model)
        ModelHealthStatus.AVAILABLE
    } catch (error: Throwable) {
        logHealthProbeFailure(operationId, model.modelId, error)
        ModelHealthStatus.INCOMPATIBLE
    }

    /** Must be called only while [lifecycleLock] is held. */
    private fun hasActiveManagerOperationLocked(): Boolean =
        activeImport != null || activeSelection != null || activeDeletion != null ||
            activeRename != null || activeStorageCleanup != null || activeHealthCheck != null

    private fun finishActiveImport(active: ActiveImport) {
        synchronized(lifecycleLock) {
            if (activeImport === active) activeImport = null
        }
    }

    private fun persistReadPermission(uri: Uri, grantedFlags: Int): Boolean {
        if (grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return false
        return runCatching {
            applicationContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
    }

    private fun releaseReadPermission(uri: Uri) {
        runCatching {
            applicationContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun releaseStaleImportPermissions() {
        applicationContext.contentResolver.persistedUriPermissions.forEach { permission ->
            if (permission.isReadPermission) releaseReadPermission(permission.uri)
        }
    }

    /** Publishes one coherent manager snapshot from one main-thread post. */
    private fun publishState() {
        if (!publishScheduled.compareAndSet(false, true)) return
        val accepted = mainHandler.post {
            publishScheduled.set(false)
            val latest = managerState()
            managerObservers.forEach { observer -> runCatching { observer.onStateChanged(latest) } }
            observers.forEach { observer -> runCatching { observer.onStateChanged(latest.importState) } }
        }
        if (!accepted) publishScheduled.set(false)
    }

    private fun logFailure(
        operationId: Long?,
        reason: ModelImportFailureReason,
        error: Throwable,
    ) {
        val operation = operationId?.toString() ?: "preparation"
        val message = "Model import failure: operation=$operation reason=$reason type=${error.javaClass.name}"
        val debuggable = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            Log.e(TAG, message, error)
        } else {
            Log.e(TAG, message)
        }
    }

    private fun logSelectionFailure(active: ActiveSelection, error: Throwable) {
        val message =
            "Model selection failure: operation=${active.operationId} type=${error.javaClass.name}"
        val debuggable = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            Log.e(TAG, message, error)
        } else {
            Log.e(TAG, message)
        }
    }

    private fun logDeletionFailure(active: ActiveDeletion, error: Throwable) {
        val message =
            "Model deletion failure: operation=${active.operationId} type=${error.javaClass.name}"
        val debuggable = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            Log.e(TAG, message, error)
        } else {
            Log.e(TAG, message)
        }
    }

    private fun logRenameFailure(active: ActiveRename, error: Throwable) {
        val message =
            "Model rename failure: operation=${active.operationId} type=${error.javaClass.name}"
        val debuggable = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            Log.e(TAG, message, error)
        } else {
            Log.e(TAG, message)
        }
    }

    private fun logStorageCleanupFailure(active: ActiveStorageCleanup, error: Throwable) {
        val message =
            "Model storage cleanup failure: operation=${active.operationId} type=${error.javaClass.name}"
        val debuggable = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            Log.e(TAG, message, error)
        } else {
            Log.e(TAG, message)
        }
    }

    private fun logHealthProbeFailure(operationId: Long, modelId: String, error: Throwable) {
        logHealthFailure(
            "Model initialization probe reported incompatible: operation=$operationId " +
                "model=$modelId type=${error.javaClass.name}",
            error,
        )
    }

    private fun logHealthStatusPersistenceFailure(
        operationId: Long,
        modelId: String,
        error: Throwable,
    ) {
        logHealthFailure(
            "Model health status persistence failed: operation=$operationId " +
                "model=$modelId type=${error.javaClass.name}",
            error,
        )
    }

    private fun logHealthCheckFailure(active: ActiveHealthCheck, error: Throwable) {
        logHealthFailure(
            "Model health check failed: operation=${active.operationId} " +
                "model=${active.model.modelId} type=${error.javaClass.name}",
            error,
        )
    }

    private fun logHealthFailure(message: String, error: Throwable) {
        val debuggable = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            Log.w(TAG, message, error)
        } else {
            Log.w(TAG, message)
        }
    }

    private data class ActiveSelection(
        val operationId: Long,
        val modelId: String,
    )

    private data class ActiveDeletion(
        val operationId: Long,
        val modelId: String,
    )

    private data class ActiveRename(
        val operationId: Long,
        val modelId: String,
        val displayName: String,
    )

    private data class ActiveStorageCleanup(val operationId: Long)

    private data class ActiveHealthCheck(
        val operationId: Long,
        val model: ImportedModel,
    )

    private inner class ActiveImport(
        val operationId: Long,
        val uri: Uri,
        val previous: ImportedModel?,
        private val persistedReadPermission: Boolean,
        val control: ModelImportOperationControl,
        val checkAfterImport: Boolean,
    ) {
        private val worker = AtomicReference<Thread?>()
        private val finalized = AtomicBoolean(false)
        private val permissionReleased = AtomicBoolean(false)
        private val sourceCloseScheduled = AtomicBoolean(false)

        fun attachWorker(value: Thread) {
            check(worker.compareAndSet(null, value)) { "Model import worker was already attached" }
            if (control.isCancellationRequested()) value.interrupt()
        }

        fun detachWorker(value: Thread) {
            worker.compareAndSet(value, null)
        }

        fun interruptWorker() {
            worker.get()?.interrupt()
        }

        fun closeSourceAsync() {
            if (!sourceCloseScheduled.compareAndSet(false, true)) return
            try {
                cancellationExecutor.execute(control::closeCancellationResource)
            } catch (error: RejectedExecutionException) {
                logFailure(operationId, ModelImportFailureReason.INTERRUPTED, error)
            }
        }

        fun finish() {
            if (!finalized.compareAndSet(false, true)) return
            releaseReadPermissionOnce()
            finishActiveImport(this)
        }

        private fun releaseReadPermissionOnce() {
            if (!persistedReadPermission || !permissionReleased.compareAndSet(false, true)) return
            releaseReadPermission(uri)
        }
    }

    companion object {
        private const val TAG = "ModelImportCoordinator"

        @Volatile
        private var instance: ModelImportCoordinator? = null

        fun get(context: Context): ModelImportCoordinator = instance ?: synchronized(this) {
            instance ?: ModelImportCoordinator(context.applicationContext).also { instance = it }
        }
    }
}
