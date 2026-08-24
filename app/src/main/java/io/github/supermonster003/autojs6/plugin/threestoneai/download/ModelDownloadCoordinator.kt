package io.github.supermonster003.autojs6.plugin.threestoneai.download

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import java.io.IOException
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
 * Owns the single process-local user-initiated model download independently of Activity lifetime.
 * A process death can stop the transfer; pinned validation prevents a partial file from being
 * presented as successful, and every in-process failure makes a best-effort destination cleanup.
 */
internal class ModelDownloadCoordinator private constructor(
    context: Context,
    private val client: ModelDownloadClient = HttpsModelDownloadClient,
) {
    fun interface Observer {
        fun onStateChanged(state: ModelDownloadState<Uri>)
    }

    private val applicationContext = context.applicationContext
    private val contentResolver = applicationContext.contentResolver
    private val stateMachine = ModelDownloadStateMachine<Uri>()
    private val observers = CopyOnWriteArraySet<Observer>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleLock = Any()
    private val publishScheduled = AtomicBoolean(false)
    private var activeDownload: ActiveDownload? = null
    val processSessionToken: String = UUID.randomUUID().toString()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litertlm-model-download").apply { isDaemon = true }
    }
    private val cancellationExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1),
        { runnable -> Thread(runnable, "litertlm-model-download-cancel").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    fun attach(observer: Observer): ModelDownloadState<Uri> {
        observers += observer
        return stateMachine.snapshot()
    }

    fun detach(observer: Observer) {
        observers -= observer
    }

    fun state(): ModelDownloadState<Uri> = stateMachine.snapshot()

    fun beginDownload(
        model: RecommendedModel,
        destination: Uri,
        grantedFlags: Int,
    ): Boolean {
        val active = synchronized(lifecycleLock) {
            if (activeDownload != null) return false
            val operationId = stateMachine.begin(model, destination, grantedFlags) ?: return false
            val control = ModelDownloadOperationControl { progress ->
                if (stateMachine.updateProgress(operationId, progress)) publishState()
            }
            ActiveDownload(operationId, model, destination, control).also {
                activeDownload = it
            }
        }
        publishState()
        try {
            executor.execute { executeDownload(active) }
        } catch (error: RejectedExecutionException) {
            finishRejectedDownload(active, error)
        }
        return true
    }

    /** Cancels only the exact operation rendered by the caller. */
    fun cancelDownload(operationId: Long): Boolean {
        val active = synchronized(lifecycleLock) {
            val current = activeDownload?.takeIf { it.operationId == operationId }
                ?: return stateMachine.snapshot().let { state ->
                    state is ModelDownloadState.Cancelling && state.operationId == operationId ||
                        state is ModelDownloadState.Cancelled && state.operationId == operationId
                }
            val accepted = current.control.requestCancellation {
                check(stateMachine.beginCancellation(operationId)) {
                    "Accepted cancellation must match the running model download"
                }
            }
            if (!accepted) return true
            current
        }
        active.interruptWorker()
        active.closeResourcesAsync()
        publishState()
        return true
    }

    private fun executeDownload(active: ActiveDownload) {
        val worker = Thread.currentThread()
        active.attachWorker(worker)
        var transfer: ModelDownloadTransferResult? = null
        var failure: Throwable? = null
        try {
            active.control.ensureActive()
            val opened = client.open(active.model, active.control)
            if (opened.declaredLength != active.model.expectedSizeBytes) {
                throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.INTEGRITY_MISMATCH,
                    "Model download length does not match its pinned size",
                )
            }
            val descriptor = try {
                contentResolver.openFileDescriptor(active.destination, "rwt")
            } catch (error: Exception) {
                throw destinationFailure(error)
            } ?: throw ModelDownloadFailureException(
                ModelDownloadFailureReason.DESTINATION_UNAVAILABLE,
                "Model download destination could not be opened",
            )
            val output = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
            active.control.registerCancellationResource(output)
            opened.input.use { input ->
                output.use { destination ->
                    transfer = ModelDownloadTransfer.copyAndVerify(
                        input = input,
                        output = destination,
                        model = active.model,
                        ensureActive = active.control::ensureActive,
                        progressListener = active.control::reportProgress,
                    )
                    try {
                        destination.flush()
                        descriptor.fileDescriptor.sync()
                    } catch (error: IOException) {
                        throw destinationFailure(error)
                    }
                }
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            active.detachWorker(worker)
            active.control.closeCancellationResources()
            Thread.interrupted()
            finishDownload(active, transfer, failure)
        }
    }

    private fun finishDownload(
        active: ActiveDownload,
        transfer: ModelDownloadTransferResult?,
        failure: Throwable?,
    ) {
        val cancelled = active.control.isCancellationRequested()
        val completed = !cancelled && transfer != null && failure == null
        val cleanup = if (!completed) {
            cleanIncompleteDestination(active.destination)
        } else {
            ModelDownloadCleanupResult.NOT_NEEDED
        }
        val reason = failure?.toDownloadFailureReason()
            ?: if (!completed) ModelDownloadFailureReason.UNKNOWN else null
        if (!cancelled && reason != null) {
            logFailure(
                active,
                reason,
                failure ?: IllegalStateException("Model download ended without a result"),
            )
        }
        synchronized(lifecycleLock) {
            if (activeDownload !== active) return
            val changed = if (cancelled) {
                stateMachine.finishCancellation(active.operationId, cleanup)
            } else if (completed) {
                val verified = checkNotNull(transfer)
                check(verified.byteCount == active.model.expectedSizeBytes)
                check(verified.sha256 == active.model.expectedSha256)
                stateMachine.succeed(active.operationId)
            } else {
                stateMachine.fail(active.operationId, checkNotNull(reason), cleanup)
            }
            check(changed) { "Model download terminal state did not match its active operation" }
            activeDownload = null
        }
        publishState()
    }

    private fun finishRejectedDownload(active: ActiveDownload, error: RejectedExecutionException) {
        active.control.closeCancellationResources()
        val cleanup = cleanIncompleteDestination(active.destination)
        val cancelled = active.control.isCancellationRequested()
        if (!cancelled) logFailure(active, ModelDownloadFailureReason.UNKNOWN, error)
        synchronized(lifecycleLock) {
            if (activeDownload !== active) return
            val changed = if (cancelled) {
                stateMachine.finishCancellation(active.operationId, cleanup)
            } else {
                stateMachine.fail(
                    active.operationId,
                    ModelDownloadFailureReason.UNKNOWN,
                    cleanup,
                )
            }
            check(changed) { "Rejected model download did not match its active operation" }
            activeDownload = null
        }
        publishState()
    }

    private fun cleanIncompleteDestination(destination: Uri): ModelDownloadCleanupResult {
        val deleted = runCatching {
            DocumentsContract.deleteDocument(contentResolver, destination)
        }.getOrDefault(false) || runCatching {
            contentResolver.delete(destination, null, null) > 0
        }.getOrDefault(false)
        if (deleted) return ModelDownloadCleanupResult.DELETED

        return runCatching {
            contentResolver.openFileDescriptor(destination, "rwt")?.use { descriptor ->
                descriptor.fileDescriptor.sync()
            } ?: error("Incomplete model destination could not be reopened")
        }.fold(
            onSuccess = { ModelDownloadCleanupResult.TRUNCATED },
            onFailure = { ModelDownloadCleanupResult.FAILED },
        )
    }

    private fun publishState() {
        if (!publishScheduled.compareAndSet(false, true)) return
        val accepted = mainHandler.post {
            publishScheduled.set(false)
            val latest = stateMachine.snapshot()
            observers.forEach { observer -> runCatching { observer.onStateChanged(latest) } }
        }
        if (!accepted) publishScheduled.set(false)
    }

    private fun logFailure(
        active: ActiveDownload,
        reason: ModelDownloadFailureReason,
        error: Throwable,
    ) {
        val message =
            "Model download failure: operation=${active.operationId} reason=$reason " +
                "type=${error.javaClass.name}"
        val debuggable =
            applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) Log.e(TAG, message, error) else Log.e(TAG, message)
    }

    private inner class ActiveDownload(
        val operationId: Long,
        val model: RecommendedModel,
        val destination: Uri,
        val control: ModelDownloadOperationControl,
    ) {
        private val worker = AtomicReference<Thread?>()
        private val closeScheduled = AtomicBoolean(false)

        fun attachWorker(value: Thread) {
            check(worker.compareAndSet(null, value)) { "Model download worker was already attached" }
            if (control.isCancellationRequested()) value.interrupt()
        }

        fun detachWorker(value: Thread) {
            worker.compareAndSet(value, null)
        }

        fun interruptWorker() {
            worker.get()?.interrupt()
        }

        fun closeResourcesAsync() {
            if (!closeScheduled.compareAndSet(false, true)) return
            try {
                cancellationExecutor.execute(control::closeCancellationResources)
            } catch (error: RejectedExecutionException) {
                logFailure(this, ModelDownloadFailureReason.INTERRUPTED, error)
            }
        }
    }

    companion object {
        private const val TAG = "ModelDownloadCoordinator"

        @Volatile
        private var instance: ModelDownloadCoordinator? = null

        fun get(context: Context): ModelDownloadCoordinator = instance ?: synchronized(this) {
            instance ?: ModelDownloadCoordinator(context.applicationContext).also { instance = it }
        }
    }
}

private fun Throwable.toDownloadFailureReason(): ModelDownloadFailureReason = when (this) {
    is ModelDownloadFailureException -> reason
    is InterruptedException -> ModelDownloadFailureReason.INTERRUPTED
    else -> ModelDownloadFailureReason.UNKNOWN
}

private fun destinationFailure(error: Throwable) = ModelDownloadFailureException(
    ModelDownloadFailureReason.DESTINATION_UNAVAILABLE,
    "Model download destination failed",
    error,
)
