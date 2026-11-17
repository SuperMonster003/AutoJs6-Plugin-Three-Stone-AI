package io.github.supermonster003.autojs6.plugin.threestoneai.download

import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportPolicy
import org.autojs.plugin.downloadkit.DownloadFailure
import org.autojs.plugin.downloadkit.DownloadFailureKind
import org.autojs.plugin.downloadkit.DownloadFetcher
import org.autojs.plugin.downloadkit.DownloadListener
import org.autojs.plugin.downloadkit.DownloadOutcome
import org.autojs.plugin.downloadkit.DownloadRequest
import org.autojs.plugin.downloadkit.DownloadRetention
import org.autojs.plugin.downloadkit.DownloadStore
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** What a fetch ended with, in the terms of the download state machine. */
internal sealed interface ModelFetchResult {
    /** The file in the download store, verified against the pinned size and SHA-256. */
    data class Ready(val outcome: DownloadOutcome.Ready) : ModelFetchResult

    data class Failed(val reason: ModelDownloadFailureReason, val message: String) : ModelFetchResult

    data object Cancelled : ModelFetchResult
}

/** Events of a running fetch; they arrive on the thread that called [ModelDownloadStorage.fetch]. */
internal interface ModelFetchEvents {
    /** A complete file on disk is being hashed. */
    fun onVerifying() {}

    /** Bytes of the model on disk, at most its pinned size; a smaller value than before means it started over. */
    fun onProgress(downloadedBytes: Long) {}

    /** An automatic retry or the next mirror is about to connect. */
    fun onReconnecting() {}
}

/**
 * The transfer and the staging of model downloads (plugin-center P13.3, D22, T20, T32): the shared
 * download library of the official plugins keeps the unfinished download in its store under
 * [downloadDirectory] and resumes it (Range and If-Range, automatic retries, mirrors). The store is
 * only staging: a verified model leaves it through [handOff] under the name the import expects in
 * [modelDirectory]. Files of the older downloader (`.download-<sha256>.part` in [modelDirectory],
 * also left there when an import is cancelled) are moved into the store by [adoptLegacyFile] and
 * continue from their last byte (Q51 A). As before, nothing is deleted because of its age: only one
 * model download is kept ([retainOnly]), and the caller discards it on cancellation or when its
 * content is wrong. Every method except [storedBytes] runs on the single download thread of the
 * caller.
 */
internal class ModelDownloadStorage(
    val modelDirectory: File,
    val downloadDirectory: File,
    private val transport: ModelDownloadTransport,
    retryDelaysMillis: LongArray = DownloadFetcher.DEFAULT_RETRY_DELAYS_MILLIS,
    private val log: (String) -> Unit = {},
) {
    val store = DownloadStore(downloadDirectory, MODEL_FILE_NAME, log = log, logName = "Stone AI model store")

    /** Receives every connection of the running fetch, so a pause or cancellation can close it. */
    @Volatile
    private var connectionSink: ((Closeable) -> Unit)? = null

    private val fetcher = DownloadFetcher(
        store = store,
        retryDelaysMillis = retryDelaysMillis,
        open = { url -> PolicyRedirectConnection(url, transport, ::registerConnection) },
        log = log,
        logName = "Stone AI model",
        connectTimeoutMillis = ModelDownloadPolicy.CONNECT_TIMEOUT_MILLIS,
        readTimeoutMillis = ModelDownloadPolicy.READ_TIMEOUT_MILLIS,
    )

    fun request(model: RecommendedModel): DownloadRequest = DownloadRequest(
        urls = transport.urls(model),
        expectedSha256 = model.expectedSha256,
        expectedSizeBytes = model.expectedSizeBytes,
        extras = mapOf(EXTRA_MODEL_ID to model.id),
    )

    /** The partial file of the older downloader, and the name a verified model is handed off under. */
    fun legacyFile(sha256: String): File = File(modelDirectory, ModelDownloadPolicy.partFileName(sha256))

    fun legacyFiles(): List<File> =
        modelDirectory.listFiles()
            ?.filter { file -> file.isFile && ModelDownloadPolicy.partFileSha256(file.name) != null }
            .orEmpty()

    /** The unfinished download of [model] in the store. */
    fun partFile(model: RecommendedModel): File = store.partFile(model.expectedSha256)

    /** The verified length a resume of [model] starts from, wherever its bytes are; zero when nothing is usable. */
    fun storedBytes(model: RecommendedModel): Long {
        val key = model.expectedSha256
        val expected = model.expectedSizeBytes
        val finished = store.file(key).lengthOrZero().takeIf { it == expected } ?: 0L
        return listOf(legacyFile(key).lengthOrZero(), store.partFile(key).lengthOrZero(), finished)
            .maxOf { ModelDownloadPolicy.resumableLength(it, expected) }
    }

    /**
     * Q51 A: moves the older-format file of [model] into the store so the next fetch continues it
     * (a complete one is only hashed again). Null when there is none; a file the store cannot use,
     * or one shorter than what the store already has, is deleted.
     */
    fun adoptLegacyFile(model: RecommendedModel): DownloadStore.PartialImport? {
        val legacy = legacyFile(model.expectedSha256)
        if (!legacy.isFile) return null
        val length = legacy.length()
        val result = if (ModelDownloadPolicy.resumableLength(length, model.expectedSizeBytes) == 0L) {
            DownloadStore.PartialImport.REJECTED
        } else {
            store.importPartial(request(model), legacy)
        }
        when (result) {
            DownloadStore.PartialImport.IMPORTED ->
                log("Stone AI model ${model.id}: adopted $length B of the older downloader.")
            DownloadStore.PartialImport.KEPT_EXISTING,
            DownloadStore.PartialImport.REJECTED,
            -> {
                log("Stone AI model ${model.id}: dropped $length B of the older downloader ($result).")
                legacy.deleteQuietly()
            }
            DownloadStore.PartialImport.BUSY -> Unit
        }
        return result
    }

    /** Deletes every download except the one of [keepSha256] (none when null): one model download at a time. */
    fun retainOnly(keepSha256: String?) {
        legacyFiles()
            .filter { ModelDownloadPolicy.partFileSha256(it.name) != keepSha256 }
            .forEach { it.deleteQuietly() }
        store.cleanup(NO_AGE_LIMITS) { entry, _ -> if (entry.key != keepSha256) RULE_ONE_DOWNLOAD else null }
    }

    /**
     * Before a transfer of [model]: adopts its older-format file, drops every other download and
     * checks what is left. Returns the bytes the transfer starts from.
     */
    @Throws(ModelDownloadFailureException::class)
    fun prepare(model: RecommendedModel): Long {
        val key = model.expectedSha256
        adoptLegacyFile(model)
        retainOnly(key)
        val part = store.partFile(key)
        if (part.isFile && !store.file(key).isFile) {
            if (ModelDownloadPolicy.resumableLength(part.length(), model.expectedSizeBytes) == 0L) {
                store.discardPart(key)
            } else if (hasLiteRtHeader(part) == false) {
                store.remove(key)
                throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.INVALID_CONTENT,
                    "Partial model download is not a LiteRT-LM model",
                )
            }
        }
        return storedBytes(model)
    }

    /**
     * Cold start (D126): adopts the older-format files of [catalog] models that are not imported
     * and returns the newest download worth resuming with its length. Deletes nothing else; the
     * caller then calls [retainOnly] with the model it shows (or null).
     */
    fun findResumable(
        catalog: List<RecommendedModel>,
        importedSha256: Set<String>,
    ): Pair<RecommendedModel, Long>? {
        legacyFiles().forEach { file ->
            val sha256 = ModelDownloadPolicy.partFileSha256(file.name) ?: return@forEach
            if (sha256 in importedSha256) return@forEach
            catalog.firstOrNull { it.expectedSha256 == sha256 }?.let(::adoptLegacyFile)
        }
        return store.entries()
            .mapNotNull { entry ->
                if (entry.meta == null || entry.key in importedSha256) return@mapNotNull null
                val model = catalog.firstOrNull { it.expectedSha256 == entry.key } ?: return@mapNotNull null
                val bytes = storedBytes(model)
                if (bytes == 0L) null else Triple(model, bytes, maxOf(entry.fileModified, entry.partModified))
            }
            .maxByOrNull { (_, _, modified) -> modified }
            ?.let { (model, bytes) -> model to bytes }
    }

    /**
     * Fetches [model] into the store. [cancel] (set by a pause or cancellation, together with the
     * connections registered on [control]) ends it; the call returns only once the library's
     * transfer has stopped writing, so a new transfer never shares the partial file with it.
     */
    fun fetch(
        model: RecommendedModel,
        control: ModelDownloadOperationControl,
        cancel: AtomicBoolean,
        events: ModelFetchEvents,
    ): ModelFetchResult {
        val request = request(model)
        val key = request.key
        var invalidContent = false
        var headerChecked = false
        var previousBytes = -1L
        val listener = object : DownloadListener {
            override suspend fun onVerifying(checkedBytes: Long, totalBytes: Long) = events.onVerifying()

            override suspend fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                if (downloadedBytes < previousBytes) headerChecked = false
                previousBytes = downloadedBytes
                // The LiteRT-LM header fails fast, as before, instead of after the whole file.
                if (!headerChecked && downloadedBytes >= ModelImportPolicy.LITERTLM_MAGIC_BYTES) {
                    when (hasLiteRtHeader(store.partFile(key))) {
                        false -> {
                            invalidContent = true
                            cancel.set(true)
                            return
                        }
                        true -> headerChecked = true
                        null -> Unit
                    }
                }
                events.onProgress(downloadedBytes.coerceIn(0L, model.expectedSizeBytes))
            }

            override suspend fun onRetry(retry: Int, delayMillis: Long, failure: DownloadFailure) {
                log("Stone AI model ${model.id}: retry $retry in $delayMillis ms after ${failure.kind}.")
                events.onReconnecting()
            }

            override suspend fun onMirrorChanged(mirrorIndex: Int, url: String, failure: DownloadFailure) {
                log("Stone AI model ${model.id}: mirror ${mirrorIndex + 1} takes over after ${failure.kind}.")
                events.onReconnecting()
            }
        }
        connectionSink = { connection -> control.registerCancellationResource(connection) }
        val outcome = try {
            fetcher.fetchBlocking(request, cancel, listener)
        } finally {
            connectionSink = null
            awaitStopped(key)
        }
        return when (outcome) {
            is DownloadOutcome.Ready -> ModelFetchResult.Ready(outcome)
            is DownloadOutcome.Failed -> ModelFetchResult.Failed(
                reasonOf(outcome.failure),
                "Model download failed: ${outcome.failure.kind} ${outcome.failure.message}",
            )
            DownloadOutcome.Cancelled -> if (invalidContent) {
                ModelFetchResult.Failed(
                    ModelDownloadFailureReason.INVALID_CONTENT,
                    "Downloaded content is not a LiteRT-LM model",
                )
            } else {
                ModelFetchResult.Cancelled
            }
        }
    }

    /**
     * Moves the verified file of [ready] to the hand-off name in [modelDirectory] (the import
     * renames it from there), flushes it to disk and removes the store entry.
     */
    @Throws(IOException::class)
    fun handOff(model: RecommendedModel, ready: DownloadOutcome.Ready): File {
        val target = legacyFile(model.expectedSha256)
        store.usingKey(ready.key) {
            if (target.exists() && !target.delete()) throw IOException("Previous hand-off file cannot be replaced")
            if (!ready.file.renameTo(target)) {
                ready.file.copyTo(target, overwrite = true)
                ready.file.delete()
            }
            FileOutputStream(target, true).use { it.fd.sync() }
        }
        store.remove(ready.key)
        return target
    }

    /** Deletes every byte of the download of [model]. */
    fun discard(model: RecommendedModel) {
        store.remove(model.expectedSha256)
        legacyFile(model.expectedSha256).deleteQuietly()
    }

    private fun registerConnection(connection: Closeable) {
        connectionSink?.invoke(connection)
    }

    private fun awaitStopped(key: String) {
        var interrupted = Thread.interrupted()
        try {
            val deadline = System.nanoTime() + STOP_TIMEOUT_MILLIS * 1_000_000L
            while (store.isInUse(key) && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(STOP_POLL_MILLIS)
                } catch (error: InterruptedException) {
                    interrupted = true
                }
            }
            if (store.isInUse(key)) log("Stone AI model ${key.take(12)}: the transfer has not stopped yet.")
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun File.lengthOrZero(): Long = if (isFile) length() else 0L

    private fun File.deleteQuietly() {
        if (!delete() && exists()) log("Stone AI model download file cannot be deleted: $name")
    }

    companion object {
        /** Under filesDir, next to the model directory (same file system, so hand-offs are renames). */
        const val DOWNLOAD_DIRECTORY_NAME = "model-downloads"
        const val MODEL_FILE_NAME = "model.litertlm"
        const val EXTRA_MODEL_ID = "modelId"
        private const val RULE_ONE_DOWNLOAD = "ONE_DOWNLOAD"
        private const val STOP_POLL_MILLIS = 50L

        /** Above the read timeout: a closed connection normally ends the transfer at once. */
        private const val STOP_TIMEOUT_MILLIS = ModelDownloadPolicy.READ_TIMEOUT_MILLIS + 10_000L

        /** No age limits: an unfinished model of several GB is never deleted because it waited. */
        private val NO_AGE_LIMITS = DownloadRetention.Limits(
            maxIdleMillis = Long.MAX_VALUE,
            partMaxAgeMillis = Long.MAX_VALUE,
        )

        /** True or false once [file] holds the eight header bytes, null before. */
        fun hasLiteRtHeader(file: File): Boolean? {
            val header = ByteArray(ModelImportPolicy.LITERTLM_MAGIC_BYTES)
            val count = try {
                FileInputStream(file).use { input ->
                    var read = 0
                    while (read < header.size) {
                        val n = input.read(header, read, header.size - read)
                        if (n < 0) break
                        read += n
                    }
                    read
                }
            } catch (error: IOException) {
                return null
            }
            if (count < header.size) return null
            return try {
                ModelImportPolicy.requireLiteRtLmHeader(header)
                true
            } catch (error: ModelImportFailureException) {
                false
            }
        }

        /** Failure kinds of the library as the reasons the download card shows. */
        fun reasonOf(failure: DownloadFailure): ModelDownloadFailureReason = when (failure.kind) {
            DownloadFailureKind.HTTP_STATUS,
            DownloadFailureKind.SECURITY,
            -> ModelDownloadFailureReason.HTTP_ERROR
            DownloadFailureKind.NETWORK -> if (NO_SPACE.containsMatchIn(failure.message)) {
                ModelDownloadFailureReason.INSUFFICIENT_STORAGE
            } else {
                ModelDownloadFailureReason.NETWORK_UNAVAILABLE
            }
            // A short file is a cut transfer that a retry continues; a longer one is another file.
            DownloadFailureKind.SIZE_MISMATCH -> {
                val actual = failure.actualSize
                val expected = failure.expectedSize
                if (actual != null && expected != null && actual < expected) {
                    ModelDownloadFailureReason.NETWORK_UNAVAILABLE
                } else {
                    ModelDownloadFailureReason.INTEGRITY_MISMATCH
                }
            }
            DownloadFailureKind.CHECKSUM_MISMATCH -> ModelDownloadFailureReason.INTEGRITY_MISMATCH
            DownloadFailureKind.OTHER -> ModelDownloadFailureReason.UNKNOWN
        }

        private val NO_SPACE = Regex("ENOSPC|No space left", RegexOption.IGNORE_CASE)
    }
}
