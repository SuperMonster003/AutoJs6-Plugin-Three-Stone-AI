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

/**
 * Owns the one process-local import independently of any Activity instance. A process death is
 * handled by the repository transaction marker the next time the model manager cold-starts.
 */
internal class ModelImportCoordinator private constructor(context: Context) {
    fun interface Observer {
        fun onStateChanged(state: ModelImportState<ImportedModel>)
    }

    private val applicationContext = context.applicationContext
    private val repository = ModelRepository(applicationContext)
    private val stateMachine = ModelImportStateMachine<ImportedModel>()
    private val observers = CopyOnWriteArraySet<Observer>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleLock = Any()
    private val publishScheduled = AtomicBoolean(false)
    private var activeImport: ActiveImport? = null
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
                    repository.current()
                }.fold(
                    onSuccess = stateMachine::finishPreparation,
                    onFailure = { error ->
                        logFailure(operationId = null, ModelImportFailureReason.UNKNOWN, error)
                        stateMachine.failPreparation()
                    },
                )
                publishLatest()
            }
        } catch (error: RejectedExecutionException) {
            logFailure(operationId = null, ModelImportFailureReason.UNKNOWN, error)
            stateMachine.failPreparation()
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

    fun beginImport(uri: Uri, grantedFlags: Int): Boolean {
        val active = synchronized(lifecycleLock) {
            if (activeImport != null) return false
            val operationId = stateMachine.begin() ?: return false
            val running = stateMachine.snapshot() as ModelImportState.Running<ImportedModel>
            val persistedReadPermission = persistReadPermission(uri, grantedFlags)
            val control = ModelImportOperationControl { progress ->
                if (stateMachine.updateProgress(operationId, progress)) publishLatest()
            }
            val operation = ActiveImport(
                operationId = operationId,
                uri = uri,
                previous = running.previous,
                persistedReadPermission = persistedReadPermission,
                control = control,
            )
            activeImport = operation
            operation
        }
        publishLatest()
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
            if (stateChanged) publishLatest()
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
        publishLatest()
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
            val visibleCurrent = repository.current() ?: active.previous
            val stateChanged = synchronized(lifecycleLock) {
                when {
                    recoveryFailure != null -> {
                        logFailure(active.operationId, ModelImportFailureReason.UNKNOWN, recoveryFailure)
                        stateMachine.fail(
                            operationId = active.operationId,
                            visibleCurrent = visibleCurrent,
                            reason = ModelImportFailureReason.UNKNOWN,
                            retryAllowed = false,
                        )
                    }
                    active.control.isCancellationRequested() -> {
                        stateMachine.finishCancellation(active.operationId, visibleCurrent)
                    }
                    imported != null -> stateMachine.succeed(active.operationId, imported)
                    else -> {
                        val error = checkNotNull(importFailure)
                        val reason = error.toModelImportFailureReason()
                        logFailure(active.operationId, reason, error)
                        stateMachine.fail(active.operationId, visibleCurrent, reason)
                    }
                }
            }
            active.finish()
            if (stateChanged) publishLatest()
        }
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

    private fun publishLatest() {
        if (!publishScheduled.compareAndSet(false, true)) return
        val accepted = mainHandler.post {
            publishScheduled.set(false)
            val latest = stateMachine.snapshot()
            observers.forEach { observer -> runCatching { observer.onStateChanged(latest) } }
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
