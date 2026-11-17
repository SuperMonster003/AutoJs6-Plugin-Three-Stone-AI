package io.github.supermonster003.autojs6.plugin.threestoneai.download

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.model.PRIVATE_MODEL_DIRECTORY_NAME
import io.github.supermonster003.autojs6.plugin.threestoneai.model.VerifiedDownloadImport
import io.github.supermonster003.autojs6.plugin.threestoneai.model.VerifiedDownloadImportOutcome
import io.github.supermonster003.autojs6.plugin.threestoneai.model.VerifiedDownloadImportStart
import org.autojs.plugin.downloadkit.DownloadFetcher
import java.io.Closeable
import java.io.File
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
 * Owns the single process-local model download independently of Activity lifetime (D99, D126).
 * The shared download library of the official plugins ([ModelDownloadStorage], plugin-center
 * P13.3) fetches the model into its private store, resumes it with HTTP Range requests, retries a
 * cut transfer by itself and verifies the pinned size and SHA-256; the verified file is then handed
 * to [ModelImportCoordinator], which publishes it like an import. A paused, failed or interrupted
 * download keeps its partial file; after a process death the next cold start shows it as a paused
 * download, including a partial file left by the downloader of an older version (Q51).
 */
internal class ModelDownloadCoordinator private constructor(
    context: Context,
    transport: ModelDownloadTransport = HttpsModelDownloadTransport,
    retryDelaysMillis: LongArray = DownloadFetcher.DEFAULT_RETRY_DELAYS_MILLIS,
) {
    fun interface Observer {
        fun onStateChanged(state: ModelDownloadState)
    }

    private val applicationContext = context.applicationContext
    private val directory = File(applicationContext.filesDir, PRIVATE_MODEL_DIRECTORY_NAME)
    private val debuggable =
        applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    internal val storage = ModelDownloadStorage(
        modelDirectory = directory,
        downloadDirectory = File(applicationContext.filesDir, ModelDownloadStorage.DOWNLOAD_DIRECTORY_NAME),
        transport = transport,
        retryDelaysMillis = retryDelaysMillis,
        log = { message -> if (debuggable) Log.i(TAG, message) },
    )
    private val stateMachine = ModelDownloadStateMachine()
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

    init {
        try {
            executor.execute(::restorePartialDownload)
        } catch (error: RejectedExecutionException) {
            logFailure(operationId = null, ModelDownloadFailureReason.UNKNOWN, error)
        }
    }

    fun attach(observer: Observer): ModelDownloadState {
        observers += observer
        return stateMachine.snapshot()
    }

    fun detach(observer: Observer) {
        observers -= observer
    }

    fun state(): ModelDownloadState = stateMachine.snapshot()

    /**
     * Starts [model], resuming its partial file when one exists. A paused or failed download of
     * another model is discarded, since only one partial file is kept.
     */
    fun beginDownload(model: RecommendedModel): Boolean {
        ModelDownloadPolicy.requireValidCatalogEntry(model)
        val active = synchronized(lifecycleLock) {
            if (activeDownload != null) return false
            val knownBytes = when (val previous = stateMachine.snapshot()) {
                is ModelDownloadState.Paused -> previous.progress.processedBytes.takeIf { previous.model == model }
                is ModelDownloadState.Failed -> previous.downloadedBytes.takeIf { previous.model == model }
                else -> null
            } ?: 0L
            val operationId = stateMachine.begin(model, knownBytes) ?: return false
            ActiveDownload(operationId, model, ModelDownloadOperationControl()).also { activeDownload = it }
        }
        publishState()
        try {
            executor.execute { executeDownload(active) }
        } catch (error: RejectedExecutionException) {
            finishWorker(active, error)
        }
        return true
    }

    /** Pauses only the exact operation rendered by the caller; the partial file is kept. */
    fun pauseDownload(operationId: Long): Boolean {
        val active = synchronized(lifecycleLock) {
            val current = activeDownload?.takeIf { it.operationId == operationId } ?: return false
            val running = stateMachine.snapshot() as? ModelDownloadState.Running ?: return false
            if (running.operationId != operationId || !running.stage.pausable) return false
            val accepted = current.control.requestCancellation {
                check(stateMachine.beginPause(operationId)) {
                    "Accepted pause must match the running model download"
                }
            }
            if (!accepted) return false
            current
        }
        active.interruptWorker()
        active.closeResourcesAsync()
        publishState()
        return true
    }

    /**
     * Cancels only the exact operation rendered by the caller and deletes its partial file. A
     * download already handed to the import is cancelled from the import panel instead.
     */
    fun cancelDownload(operationId: Long): Boolean {
        var discarded: RecommendedModel? = null
        val active = synchronized(lifecycleLock) {
            val state = stateMachine.snapshot()
            val current = activeDownload?.takeIf { it.operationId == operationId }
            if (current == null) {
                if (state is ModelDownloadState.Cancelled && state.operationId == operationId) return true
                val model = state.cardModel ?: return false
                if (!stateMachine.discard(operationId)) return false
                discarded = model
                null
            } else {
                when {
                    state is ModelDownloadState.Cancelling && state.operationId == operationId -> return true
                    state is ModelDownloadState.Pausing && state.operationId == operationId ->
                        check(stateMachine.beginCancellation(operationId))
                    state is ModelDownloadState.Running && state.operationId == operationId &&
                        state.stage.cancellable -> {
                        val accepted = current.control.requestCancellation {
                            check(stateMachine.beginCancellation(operationId)) {
                                "Accepted cancellation must match the running model download"
                            }
                        }
                        if (!accepted) return false
                    }
                    else -> return false
                }
                current
            }
        }
        if (active != null) {
            active.interruptWorker()
            active.closeResourcesAsync()
        }
        discarded?.let { model ->
            try {
                executor.execute { discardDownload(model) }
            } catch (error: RejectedExecutionException) {
                logFailure(operationId, ModelDownloadFailureReason.UNKNOWN, error)
            }
        }
        publishState()
        return true
    }

    private fun executeDownload(active: ActiveDownload) {
        val worker = Thread.currentThread()
        active.attachWorker(worker)
        var failure: Throwable? = null
        var handedOff = false
        try {
            handedOff = transfer(active)
        } catch (error: Throwable) {
            failure = error
        } finally {
            active.detachWorker(worker)
            active.control.closeCancellationResources()
            Thread.interrupted()
            if (!handedOff) finishWorker(active, failure)
        }
    }

    /** Returns true once the import owns the verified file and will report its outcome. */
    private fun transfer(active: ActiveDownload): Boolean {
        val model = active.model
        val control = active.control
        control.ensureActive()
        prepareDirectory()
        val existing = storage.prepare(model)
        if (!ModelDownloadPolicy.hasStorageFor(directory.usableSpace, model.expectedSizeBytes - existing)) {
            throw ModelDownloadFailureException(
                ModelDownloadFailureReason.INSUFFICIENT_STORAGE,
                "Private storage cannot hold the rest of the model",
            )
        }
        alignProgress(active, existing)
        enterStage(
            active,
            if (existing > 0L) ModelDownloadStage.CHECKING_PARTIAL else ModelDownloadStage.CONNECTING,
        )

        // A pause or cancellation sets the flag and closes the open connections (registered by
        // the storage); the library's transfer then stops and keeps the partial file.
        val cancel = AtomicBoolean(false)
        control.registerCancellationResource(Closeable { cancel.set(true) })
        val result = storage.fetch(model, control, cancel, FetchEvents(active))
        val ready = when (result) {
            is ModelFetchResult.Ready -> result.outcome
            is ModelFetchResult.Failed -> throw ModelDownloadFailureException(result.reason, result.message)
            ModelFetchResult.Cancelled -> throw InterruptedException("Model download was cancelled")
        }
        check(ready.sizeBytes == model.expectedSizeBytes && ready.sha256 == model.expectedSha256)
        control.ensureActive()
        val handOffFile = try {
            storage.handOff(model, ready)
        } catch (error: IOException) {
            throw destinationFailure(error)
        }
        return handOffToImport(active, handOffFile)
    }

    /** Turns the events of the library's transfer into stages and monotonic progress. */
    private inner class FetchEvents(private val active: ActiveDownload) : ModelFetchEvents {
        override fun onVerifying() = updateStageQuietly(active, ModelDownloadStage.CHECKING_PARTIAL)

        override fun onReconnecting() = updateStageQuietly(active, ModelDownloadStage.CONNECTING)

        override fun onProgress(downloadedBytes: Long) {
            val changed = synchronized(lifecycleLock) {
                if (active.control.isCancellationRequested()) return
                val running = stateMachine.snapshot() as? ModelDownloadState.Running ?: return
                if (running.operationId != active.operationId) return
                var changed = stateMachine.updateStage(active.operationId, ModelDownloadStage.DOWNLOADING)
                // The library started over (the server sent the whole file, or the resumed bytes
                // were refused): the progress goes back to zero only this way.
                if (downloadedBytes < running.progress.processedBytes) {
                    changed = stateMachine.restartProgress(active.operationId) || changed
                }
                if (downloadedBytes > 0L) {
                    changed = stateMachine.updateProgress(
                        active.operationId,
                        ModelDownloadProgress(downloadedBytes, active.model.expectedSizeBytes),
                    ) || changed
                }
                changed
            }
            if (changed) publishState()
        }
    }

    /** [enterStage] for the library's callbacks, which must not throw once a pause is accepted. */
    private fun updateStageQuietly(active: ActiveDownload, stage: ModelDownloadStage) {
        val changed = synchronized(lifecycleLock) {
            !active.control.isCancellationRequested() &&
                stateMachine.updateStage(active.operationId, stage)
        }
        if (changed) publishState()
    }

    private fun handOffToImport(active: ActiveDownload, handOffFile: File): Boolean {
        val importCoordinator = ModelImportCoordinator.get(applicationContext)
        enterStage(active, ModelDownloadStage.WAITING_FOR_IMPORT)
        val handOff = VerifiedDownloadImport(
            partFile = handOffFile,
            sha256 = active.model.expectedSha256,
            byteCount = active.model.expectedSizeBytes,
            displayName = active.model.fileName,
            onFinished = { outcome -> finishHandOff(active, outcome) },
        )
        while (true) {
            // Holding the lock keeps a concurrent cancellation from racing the hand-off; the
            // import reports its outcome from its own thread only after this block returns.
            val start = synchronized(lifecycleLock) {
                active.control.ensureActive()
                importCoordinator.beginVerifiedDownloadImport(handOff).also { start ->
                    if (start == VerifiedDownloadImportStart.STARTED) {
                        stateMachine.updateStage(active.operationId, ModelDownloadStage.IMPORTING)
                    }
                }
            }
            when (start) {
                VerifiedDownloadImportStart.STARTED -> {
                    publishState()
                    return true
                }
                VerifiedDownloadImportStart.UNAVAILABLE -> throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.IMPORT_FAILED,
                    "Model catalog cannot accept the verified download",
                )
                VerifiedDownloadImportStart.BUSY -> Thread.sleep(IMPORT_RETRY_DELAY_MILLIS)
            }
        }
    }

    private fun finishHandOff(active: ActiveDownload, outcome: VerifiedDownloadImportOutcome) {
        val keptBytes = keptPartialBytes(active)
        synchronized(lifecycleLock) {
            if (activeDownload !== active) return
            val changed = when (outcome) {
                is VerifiedDownloadImportOutcome.Imported -> stateMachine.succeed(active.operationId)
                // The verified file stays: the card offers to continue (which re-verifies and
                // imports again) or to cancel, so an accidental import cancellation loses nothing.
                VerifiedDownloadImportOutcome.Cancelled ->
                    stateMachine.finishHandOffCancellation(active.operationId, keptBytes)
                VerifiedDownloadImportOutcome.Failed -> stateMachine.fail(
                    active.operationId,
                    ModelDownloadFailureReason.IMPORT_FAILED,
                    keptBytes,
                )
            }
            check(changed) { "Model download import outcome did not match its active operation" }
            activeDownload = null
        }
        publishState()
    }

    private fun finishWorker(active: ActiveDownload, failure: Throwable?) {
        val reason = failure?.toDownloadFailureReason() ?: ModelDownloadFailureReason.UNKNOWN
        var deletePart = false
        synchronized(lifecycleLock) {
            if (activeDownload !== active) return
            val changed = when (val state = stateMachine.snapshot()) {
                is ModelDownloadState.Pausing ->
                    stateMachine.finishPause(active.operationId, keptPartialBytes(active))
                is ModelDownloadState.Cancelling -> {
                    deletePart = true
                    stateMachine.finishCancellation(active.operationId)
                }
                is ModelDownloadState.Running -> {
                    check(state.operationId == active.operationId)
                    val keep = ModelDownloadPolicy.keepsPartialFile(reason)
                    deletePart = !keep
                    logFailure(
                        active.operationId,
                        reason,
                        failure ?: IllegalStateException("Model download ended without a result"),
                    )
                    stateMachine.fail(
                        active.operationId,
                        reason,
                        if (keep) keptPartialBytes(active) else 0L,
                    )
                }
                else -> false
            }
            check(changed) { "Model download terminal state did not match its active operation" }
            if (deletePart) discardDownload(active.model)
            activeDownload = null
        }
        publishState()
    }

    /** The verified prefix a resume can start from; a stale or foreign file counts as nothing. */
    private fun keptPartialBytes(active: ActiveDownload): Long =
        runCatching { storage.storedBytes(active.model) }.getOrDefault(0L)

    private fun enterStage(active: ActiveDownload, stage: ModelDownloadStage) {
        val changed = synchronized(lifecycleLock) {
            // A pause or cancellation accepted for an earlier stage stops the worker here, so a
            // stage that cannot be paused is never entered after a pause request.
            active.control.ensureActive()
            stateMachine.updateStage(active.operationId, stage)
        }
        if (changed) publishState()
    }

    /** Makes the rendered byte count match the partial file actually found on disk. */
    private fun alignProgress(active: ActiveDownload, existing: Long) {
        val changed = synchronized(lifecycleLock) {
            val running = stateMachine.snapshot() as? ModelDownloadState.Running ?: return
            if (running.operationId != active.operationId || running.progress.processedBytes == existing) {
                return
            }
            stateMachine.restartProgress(active.operationId)
            if (existing > 0L) {
                stateMachine.updateProgress(
                    active.operationId,
                    ModelDownloadProgress(existing, active.model.expectedSizeBytes),
                )
            }
            true
        }
        if (changed) publishState()
    }

    private fun prepareDirectory() {
        val ready = runCatching {
            (directory.isDirectory || directory.mkdirs()) &&
                directory.canonicalFile.parentFile == applicationContext.filesDir.canonicalFile
        }.getOrDefault(false)
        if (!ready) {
            throw ModelDownloadFailureException(
                ModelDownloadFailureReason.DESTINATION_UNAVAILABLE,
                "Private model storage is unavailable",
            )
        }
    }

    /** Deletes the download of [model] wherever its bytes are; runs on the download thread. */
    private fun discardDownload(model: RecommendedModel) {
        runCatching { storage.discard(model) }.onFailure { error ->
            Log.w(TAG, "Partial model download could not be deleted: ${error.javaClass.name}")
        }
    }

    /**
     * Cold start: keeps the newest partial download of a recommended model that is not imported
     * yet as a paused download and removes every other one. A partial file the downloader of an
     * older version left in the model directory is moved into the download store first (Q51).
     */
    private fun restorePartialDownload() {
        runCatching {
            if (storage.legacyFiles().isEmpty() && storage.store.entries().isEmpty()) return
            val importedSha256 = runCatching {
                ModelRepository(applicationContext).catalogSnapshot().entries.mapTo(hashSetOf()) { it.sha256 }
            }.getOrDefault(emptySet())
            val restored = storage.findResumable(RecommendedModelCatalog.models, importedSha256)
            val accepted = synchronized(lifecycleLock) {
                if (activeDownload != null) {
                    false
                } else if (restored == null) {
                    true
                } else {
                    stateMachine.restorePaused(restored.first, restored.second)
                }
            }
            // A download started meanwhile cleans the other partial downloads itself.
            if (!accepted) return
            storage.retainOnly(restored?.first?.expectedSha256)
            if (restored != null) publishState()
        }.onFailure { error ->
            logFailure(operationId = null, ModelDownloadFailureReason.UNKNOWN, error)
        }
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
        operationId: Long?,
        reason: ModelDownloadFailureReason,
        error: Throwable,
    ) {
        val message =
            "Model download failure: operation=${operationId ?: "restore"} reason=$reason " +
                "type=${error.javaClass.name}"
        if (debuggable) Log.e(TAG, message, error) else Log.e(TAG, message)
    }

    private inner class ActiveDownload(
        val operationId: Long,
        val model: RecommendedModel,
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
                logFailure(operationId, ModelDownloadFailureReason.INTERRUPTED, error)
            }
        }
    }

    companion object {
        private const val TAG = "ModelDownloadCoordinator"
        private const val IMPORT_RETRY_DELAY_MILLIS = 1_000L

        @Volatile
        private var instance: ModelDownloadCoordinator? = null

        fun get(context: Context): ModelDownloadCoordinator = instance ?: synchronized(this) {
            instance ?: ModelDownloadCoordinator(context.applicationContext).also { instance = it }
        }

        /**
         * Device tests only: replaces the process instance with a new one that downloads through
         * [transport], as a cold start would (a partial file left behind is restored as paused).
         * Pass [HttpsModelDownloadTransport] to put the production transport back.
         */
        @VisibleForTesting
        internal fun replaceForTesting(
            context: Context,
            transport: ModelDownloadTransport,
            retryDelaysMillis: LongArray = DownloadFetcher.DEFAULT_RETRY_DELAYS_MILLIS,
        ): ModelDownloadCoordinator =
            synchronized(this) {
                ModelDownloadCoordinator(context.applicationContext, transport, retryDelaysMillis)
                    .also { instance = it }
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
