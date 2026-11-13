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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
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
 * The model is written to a hash-named partial file in private storage, verified against its
 * pinned size and SHA-256 and then handed to [ModelImportCoordinator], which publishes it like an
 * import. A paused, failed or interrupted download keeps its partial file and resumes with an HTTP
 * Range request; after a process death the next cold start shows it as a paused download.
 */
internal class ModelDownloadCoordinator private constructor(
    context: Context,
    private val client: ModelDownloadClient = HttpsModelDownloadClient,
) {
    fun interface Observer {
        fun onStateChanged(state: ModelDownloadState)
    }

    private val applicationContext = context.applicationContext
    private val directory = File(applicationContext.filesDir, PRIVATE_MODEL_DIRECTORY_NAME)
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
            val control = ModelDownloadOperationControl { progress ->
                if (stateMachine.updateProgress(operationId, progress)) publishState()
            }
            ActiveDownload(operationId, model, partFile(model), control).also { activeDownload = it }
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
        var discarded: File? = null
        val active = synchronized(lifecycleLock) {
            val state = stateMachine.snapshot()
            val current = activeDownload?.takeIf { it.operationId == operationId }
            if (current == null) {
                if (state is ModelDownloadState.Cancelled && state.operationId == operationId) return true
                val model = state.cardModel ?: return false
                if (!stateMachine.discard(operationId)) return false
                discarded = partFile(model)
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
        discarded?.let { file ->
            try {
                executor.execute { deletePartialFile(file) }
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
        val part = active.partFile
        val control = active.control
        control.ensureActive()
        prepareDirectory()
        discardPartialFilesExcept(part)
        val existing = trimPartialFile(part, model)
        if (!ModelDownloadPolicy.hasStorageFor(directory.usableSpace, model.expectedSizeBytes - existing)) {
            throw ModelDownloadFailureException(
                ModelDownloadFailureReason.INSUFFICIENT_STORAGE,
                "Private storage cannot hold the rest of the model",
            )
        }
        alignProgress(active, existing)

        var digest = MessageDigest.getInstance("SHA-256")
        if (existing > 0L) {
            enterStage(active, ModelDownloadStage.CHECKING_PARTIAL)
            val input = try {
                FileInputStream(part)
            } catch (error: IOException) {
                throw destinationFailure(error)
            }
            control.registerCancellationResource(input)
            digest = input.use {
                ModelDownloadTransfer.seedPartial(
                    input = it,
                    length = existing,
                    ensureActive = control::ensureActive,
                )
            }
        }
        val verified = if (existing == model.expectedSizeBytes) {
            ModelDownloadTransfer.verifyDigest(digest, existing, model)
        } else {
            enterStage(active, ModelDownloadStage.CONNECTING)
            val opened = client.open(model, control, existing)
            val restart = existing > 0L && opened.startOffset == 0L
            if (restart) {
                digest = MessageDigest.getInstance("SHA-256")
                if (stateMachine.restartProgress(active.operationId)) publishState()
            }
            val copied = if (restart) 0L else existing
            val output = try {
                FileOutputStream(part, copied > 0L)
            } catch (error: IOException) {
                throw destinationFailure(error)
            }
            control.registerCancellationResource(output)
            enterStage(active, ModelDownloadStage.DOWNLOADING)
            opened.input.use { input ->
                output.use { destination ->
                    ModelDownloadTransfer.resumeAndVerify(
                        input = input,
                        output = destination,
                        model = model,
                        alreadyCopied = copied,
                        digest = digest,
                        ensureActive = control::ensureActive,
                        progressListener = control::reportProgress,
                    ).also {
                        try {
                            destination.flush()
                            destination.fd.sync()
                        } catch (error: IOException) {
                            throw destinationFailure(error)
                        }
                    }
                }
            }
        }
        check(verified.byteCount == model.expectedSizeBytes && verified.sha256 == model.expectedSha256)
        return handOffToImport(active)
    }

    private fun handOffToImport(active: ActiveDownload): Boolean {
        val importCoordinator = ModelImportCoordinator.get(applicationContext)
        enterStage(active, ModelDownloadStage.WAITING_FOR_IMPORT)
        val handOff = VerifiedDownloadImport(
            partFile = active.partFile,
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
            if (deletePart) deletePartialFile(active.partFile)
            activeDownload = null
        }
        publishState()
    }

    /** The verified prefix a resume can start from; a stale or foreign file counts as nothing. */
    private fun keptPartialBytes(active: ActiveDownload): Long =
        runCatching {
            ModelDownloadPolicy.resumableLength(
                partLength = if (active.partFile.isFile) active.partFile.length() else 0L,
                expectedSizeBytes = active.model.expectedSizeBytes,
            )
        }.getOrDefault(0L)

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

    /** Truncates a partial file to its resumable length and returns that length. */
    private fun trimPartialFile(part: File, model: RecommendedModel): Long {
        if (!part.exists()) return 0L
        if (!part.isFile) {
            throw ModelDownloadFailureException(
                ModelDownloadFailureReason.DESTINATION_UNAVAILABLE,
                "Partial model download is not a regular file",
            )
        }
        val length = part.length()
        val resumable = ModelDownloadPolicy.resumableLength(length, model.expectedSizeBytes)
        if (resumable == 0L) {
            deletePartialFile(part)
            if (part.exists()) {
                throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.DESTINATION_UNAVAILABLE,
                    "Stale partial model download cannot be removed",
                )
            }
        } else if (resumable != length) {
            try {
                RandomAccessFile(part, "rw").use { it.setLength(resumable) }
            } catch (error: IOException) {
                throw destinationFailure(error)
            }
        }
        return resumable
    }

    private fun discardPartialFilesExcept(keep: File) {
        partialFiles().filter { it.name != keep.name }.forEach(::deletePartialFile)
    }

    private fun partialFiles(): List<File> =
        directory.listFiles()
            ?.filter { file -> file.isFile && ModelDownloadPolicy.partFileSha256(file.name) != null }
            .orEmpty()

    private fun deletePartialFile(file: File) {
        if (ModelDownloadPolicy.partFileSha256(file.name) == null) return
        if (!file.delete() && file.exists()) {
            Log.w(TAG, "Partial model download could not be deleted")
        }
    }

    private fun partFile(model: RecommendedModel): File =
        File(directory, ModelDownloadPolicy.partFileName(model.expectedSha256))

    /**
     * Cold start: keeps the newest partial file of a recommended model that is not imported yet
     * as a paused download and removes every other partial file.
     */
    private fun restorePartialDownload() {
        runCatching {
            val candidates = partialFiles()
            if (candidates.isEmpty()) return
            val importedSha256 = runCatching {
                ModelRepository(applicationContext).catalogSnapshot().entries.mapTo(hashSetOf()) { it.sha256 }
            }.getOrDefault(emptySet())
            val restored = candidates
                .mapNotNull { file ->
                    val sha256 = ModelDownloadPolicy.partFileSha256(file.name) ?: return@mapNotNull null
                    val model = RecommendedModelCatalog.models.firstOrNull { it.expectedSha256 == sha256 }
                        ?: return@mapNotNull null
                    if (sha256 in importedSha256) return@mapNotNull null
                    val bytes = ModelDownloadPolicy.resumableLength(file.length(), model.expectedSizeBytes)
                    if (bytes == 0L) null else Triple(file, model, bytes)
                }
                .maxByOrNull { (file) -> file.lastModified() }
            val accepted = synchronized(lifecycleLock) {
                if (activeDownload != null) {
                    false
                } else if (restored == null) {
                    true
                } else {
                    stateMachine.restorePaused(restored.second, restored.third)
                }
            }
            // A download started meanwhile cleans the other partial files itself.
            if (!accepted) return
            candidates.filter { it != restored?.first }.forEach(::deletePartialFile)
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
        val debuggable =
            applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) Log.e(TAG, message, error) else Log.e(TAG, message)
    }

    private inner class ActiveDownload(
        val operationId: Long,
        val model: RecommendedModel,
        val partFile: File,
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
         * [client], as a cold start would (a partial file left behind is restored as paused).
         * Pass [HttpsModelDownloadClient] to put the production client back.
         */
        @VisibleForTesting
        internal fun replaceForTesting(context: Context, client: ModelDownloadClient): ModelDownloadCoordinator =
            synchronized(this) {
                ModelDownloadCoordinator(context.applicationContext, client).also { instance = it }
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
