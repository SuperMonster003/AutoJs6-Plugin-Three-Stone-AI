package io.github.supermonster003.autojs6.plugin.ai.text.model

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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

internal data class ModelManagerState(
    val importState: ModelImportState<ImportedModel>,
    val snapshot: ModelManagerSnapshot?,
    val selection: ModelSelectionState,
)

/**
 * Owns the one process-local import independently of any Activity instance. A process death is
 * handled by the repository transaction marker the next time the model manager cold-starts.
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
    private val stateMachine = ModelImportStateMachine<ImportedModel>()
    private val observers = CopyOnWriteArraySet<Observer>()
    private val managerObservers = CopyOnWriteArraySet<ManagerObserver>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleLock = Any()
    private val publishScheduled = AtomicBoolean(false)
    private var activeImport: ActiveImport? = null
    private var activeSelection: ActiveSelection? = null
    private var nextSelectionOperationId = 1L
    private var visibleManagerSnapshot: ModelManagerSnapshot? = null
    private var selectionState: ModelSelectionState = ModelSelectionState.Idle
    private var catalogMutationAvailable = false
    val processSessionToken: String = UUID.randomUUID().toString()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litertlm-model-import").apply { isDaemon = true }
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
        )
    }

    /**
     * Selects only a model in the last coherent manager snapshot. Selection and import reserve the
     * same process-local ownership gate and execute on the same serial worker.
     */
    fun beginSelection(modelId: String): Boolean {
        val active = synchronized(lifecycleLock) {
            if (!catalogMutationAvailable || activeImport != null || activeSelection != null) return false
            val snapshot = visibleManagerSnapshot ?: return false
            if (snapshot.models.none { it.modelId == modelId }) return false
            if (snapshot.selectedModelId == modelId) return false
            check(nextSelectionOperationId > 0L) { "Model selection operation IDs are exhausted" }
            val operationId = nextSelectionOperationId
            nextSelectionOperationId = if (operationId == Long.MAX_VALUE) 0L else operationId + 1L
            ActiveSelection(operationId, modelId).also { operation ->
                activeSelection = operation
                selectionState = ModelSelectionState.Selecting(operationId, modelId)
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

    fun beginImport(uri: Uri, grantedFlags: Int): Boolean {
        val active = synchronized(lifecycleLock) {
            if (!catalogMutationAvailable || activeImport != null || activeSelection != null) return false
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
            )
            activeImport = operation
            selectionState = ModelSelectionState.Idle
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

    /** Publishes one coherent import/catalog/selection snapshot from one main-thread post. */
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

    private data class ActiveSelection(
        val operationId: Long,
        val modelId: String,
    )

    private inner class ActiveImport(
        val operationId: Long,
        val uri: Uri,
        val previous: ImportedModel?,
        private val persistedReadPermission: Boolean,
        val control: ModelImportOperationControl,
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
