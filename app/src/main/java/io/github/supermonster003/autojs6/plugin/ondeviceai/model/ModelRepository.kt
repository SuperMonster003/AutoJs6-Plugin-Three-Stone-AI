package io.github.supermonster003.autojs6.plugin.ondeviceai.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

internal data class ImportedModel(
    val modelId: String,
    val displayName: String,
    val file: File,
    val sizeBytes: Long,
    val sha256: String,
    val importedAtMillis: Long,
    val healthStatus: ModelHealthStatus = ModelHealthStatus.NOT_CHECKED,
) {
    val listingGeneration: String
        get() = "litertlm-${sha256.take(32)}"
}

internal data class ModelDeletionResult(
    val deletedModel: ImportedModel,
    val snapshot: ModelManagerSnapshot,
)

internal data class ModelStorageCleanupResult(
    val removedFileCount: Int,
    val releasedBytes: Long,
    val snapshot: ModelManagerSnapshot,
)

/** A defensive, read-only view of every managed model and the atomically selected model ID. */
internal class ModelManagerSnapshot private constructor(
    models: List<ImportedModel>,
    val selectedModelId: String?,
    val totalSizeBytes: Long,
) {
    val models: List<ImportedModel> = Collections.unmodifiableList(ArrayList(models))
    val selectedModel: ImportedModel? = selectedModelId?.let { selected ->
        this.models.single { it.modelId == selected }
    }

    companion object {
        fun from(document: ModelCatalogDocument, convert: (ModelCatalogEntry) -> ImportedModel): ModelManagerSnapshot {
            val normalized = ModelCatalogPolicy.normalize(document)
            val models = normalized.entries.map(convert)
            val totalSizeBytes = models.fold(0L) { total, model -> Math.addExact(total, model.sizeBytes) }
            return ModelManagerSnapshot(models, normalized.selectedModelId, totalSizeBytes)
        }
    }
}

internal class ModelRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val directory = File(applicationContext.filesDir, DIRECTORY_NAME)
    private val catalogFile = File(directory, CATALOG_FILE_NAME)
    private val legacyMetadataFile = File(directory, LEGACY_METADATA_FILE_NAME)
    private val pendingMarkerFile = File(directory, PENDING_MARKER_FILE_NAME)

    @Synchronized
    fun current(): ImportedModel? = runCatching {
        val catalog = readCatalogWithLegacyFallback()
        catalog.selectedModelId?.let { selected ->
            catalog.entries.single { it.modelId == selected }.toImportedModel()
        }
    }.getOrNull()

    /** A coherent, immutable provider listing snapshot. A present corrupt catalog fails closed. */
    @Synchronized
    fun catalogSnapshot(): ModelCatalogDocument = readCatalogWithLegacyFallback()

    @Synchronized
    fun findByModelId(modelId: String): ImportedModel? = runCatching {
        readCatalogWithLegacyFallback().entries.singleOrNull { it.modelId == modelId }?.toImportedModel()
    }.getOrNull()

    /** Returns one coherent manager view; a present corrupt catalog fails closed. */
    @Synchronized
    fun managerSnapshot(): ModelManagerSnapshot = readAuthoritativeManagerCatalog().toManagerSnapshot()

    /** Atomically switches only the catalog pointer. No model generation is copied or removed. */
    @Synchronized
    fun selectExisting(modelId: String): ModelManagerSnapshot {
        val update = ModelCatalogPolicy.select(readAuthoritativeManagerCatalog(), modelId)
        if (update.changed) publishCatalogExactly(update.document)
        return update.document.toManagerSnapshot()
    }

    /** Atomically changes only the catalog display name of an existing model. */
    @Synchronized
    fun renameModel(modelId: String, displayName: String): ModelManagerSnapshot {
        val update = ModelCatalogPolicy.rename(
            document = readAuthoritativeManagerCatalog(),
            modelId = modelId,
            displayName = displayName,
        )
        if (update.changed) publishCatalogExactly(update.document)
        return update.document.toManagerSnapshot()
    }

    /** Atomically persists the terminal result of one LiteRT-LM Engine initialization probe. */
    @Synchronized
    fun recordHealthStatus(modelId: String, status: ModelHealthStatus): ModelManagerSnapshot {
        val update = ModelCatalogPolicy.recordHealthStatus(
            document = readAuthoritativeManagerCatalog(),
            modelId = modelId,
            status = status,
        )
        if (update.changed) publishCatalogExactly(update.document)
        return update.document.toManagerSnapshot()
    }

    /**
     * Removes one unselected immutable generation and durably releases its private-storage file.
     * The catalog is published first so a crash can leave only an unreferenced file, never a
     * catalog entry whose file was already removed. Such an orphan remains safe for a later bounded
     * storage-recovery pass.
     */
    @Synchronized
    fun deleteUnselected(modelId: String): ModelDeletionResult {
        val original = readAuthoritativeManagerCatalog()
        val deletion = ModelCatalogPolicy.deleteUnselected(original, modelId)
        requireCatalogFile(deletion.model)
        val target = File(directory, deletion.model.fileName)

        publishCatalogExactly(deletion.document)
        val deletionFailure = try {
            require(target.delete() || !target.exists()) { "Model file cannot be deleted" }
            syncDirectory()
            null
        } catch (error: Throwable) {
            error
        }

        if (deletionFailure != null) {
            if (!target.exists()) {
                try {
                    syncDirectory()
                } catch (retryFailure: Throwable) {
                    retryFailure.addSuppressed(deletionFailure)
                    throw retryFailure
                }
            } else {
                try {
                    requireCatalogFile(deletion.model)
                    publishCatalogExactly(
                        original.copy(revision = Math.addExact(deletion.document.revision, 1L)),
                    )
                } catch (rollbackFailure: Throwable) {
                    deletionFailure.addSuppressed(rollbackFailure)
                }
                throw deletionFailure
            }
        }

        check(!target.exists()) { "Deleted model file is still present" }
        return ModelDeletionResult(
            deletedModel = deletion.model.toImportedModel(),
            snapshot = deletion.document.toManagerSnapshot(),
        )
    }

    /** Deletes only hash-named model files that the authoritative catalog does not reference. */
    @Synchronized
    fun cleanUnreferencedModelFiles(): ModelStorageCleanupResult {
        val catalog = readAuthoritativeManagerCatalog()
        val directoryEntries = directory.listFiles()
            ?: throw IllegalStateException("Private model storage cannot be listed")
        require(directoryEntries.size <= MAXIMUM_MODEL_DIRECTORY_ENTRIES) {
            "Private model storage contains too many entries for bounded cleanup"
        }
        val entriesByName = directoryEntries.associateBy(File::getName)
        val candidates = ModelCatalogPolicy.unreferencedModelFileNames(
            document = catalog,
            fileNames = entriesByName.keys,
        ).map { name -> checkNotNull(entriesByName[name]) }
        candidates.forEach(::requireSafeRegularFile)
        val releasedBytes = candidates.fold(0L) { total, candidate ->
            Math.addExact(total, candidate.length())
        }

        candidates.forEach { candidate ->
            requireSafeRegularFile(candidate)
            require(candidate.delete() || !candidate.exists()) {
                "Unreferenced model file cannot be deleted"
            }
        }
        if (candidates.isNotEmpty()) syncDirectory()
        check(candidates.none(File::exists)) { "Cleaned model file is still present" }
        return ModelStorageCleanupResult(
            removedFileCount = candidates.size,
            releasedBytes = releasedBytes,
            snapshot = catalog.toManagerSnapshot(),
        )
    }

    /**
     * Called exactly once by the process-local model-manager coordinator before it accepts imports.
     * The provider process never invokes recovery, so an active provider cannot clean old generations.
     */
    @Synchronized
    fun recoverInterruptedImportFromManagerColdStart() {
        ensureDirectory()
        val entries = directory.listFiles().orEmpty()
        require(entries.size <= MAXIMUM_MODEL_DIRECTORY_ENTRIES) {
            "Private model storage contains too many entries for bounded recovery"
        }
        var directoryChanged = false
        entries.filter { RECOVERABLE_TEMP_FILE.matches(it.name) }.forEach { temporary ->
            requireSafeRegularFile(temporary)
            require(temporary.delete() || !temporary.exists()) { "Stale model transaction file cannot be removed" }
            directoryChanged = true
        }

        if (pendingMarkerFile.exists()) {
            requireSafeRegularFile(pendingMarkerFile)
            val marker = runCatching { readPendingMarker() }.getOrNull()
            if (marker == null) {
                require(pendingMarkerFile.delete() || !pendingMarkerFile.exists()) {
                    "Invalid model transaction marker cannot be removed"
                }
            } else {
                resolvePendingTransaction(marker)
            }
            directoryChanged = true
        }
        if (!catalogFile.exists()) {
            val initial = if (legacyMetadataFile.exists()) {
                ModelCatalogPolicy.fromLegacy(readLegacyCurrentEntry())
            } else {
                ModelCatalogPolicy.empty()
            }
            publishCatalogExactly(initial)
            directoryChanged = true
        } else {
            // A present catalog is authoritative. Validate it rather than falling back to legacy.
            readCatalog()
        }
        if (directoryChanged) syncDirectory()
    }

    /**
     * Repeats bounded recovery before the coordinator releases its active-import ownership. The
     * unconditional sync also retries a marker deletion whose first directory sync was ambiguous.
     */
    @Synchronized
    fun recoverInterruptedImportAfterWorker() {
        recoverInterruptedImportFromManagerColdStart()
        syncDirectory()
    }

    @Synchronized
    fun importFrom(
        uri: Uri,
        operation: ModelImportOperationControl = ModelImportOperationControl(),
    ): ImportedModel {
        operation.reportProgress(ModelImportProgress.initial())
        if (uri.scheme != "content") {
            throw ModelImportFailureException(
                ModelImportFailureReason.SOURCE_UNAVAILABLE,
                "Only a Storage Access Framework content URI is accepted",
            )
        }
        ensureDirectory()
        readAuthoritativeManagerCatalog()
        operation.ensureActive()
        val document = querySourceDocument(uri)
        operation.ensureActive()
        ModelImportPolicy.requireImportableName(document.displayName)
        val storagePreflight = ModelImportPolicy.storagePreflight(directory.usableSpace)
        val maximumBytes = storagePreflight.maximumAdditionalModelBytes
        if (!storagePreflight.canOpenPicker) {
            throw ModelImportFailureException(
                ModelImportFailureReason.INSUFFICIENT_STORAGE,
                "There is not enough free storage for a model",
            )
        }
        ModelImportPolicy.requireDeclaredSizeWithinBudget(document.declaredSize, maximumBytes)
        operation.reportProgress(
            ModelImportProgress(
                stage = ModelImportStage.VALIDATING,
                processedBytes = 0L,
                totalBytes = document.declaredSize,
            ),
        )

        val transactionId = UUID.randomUUID().toString()
        val temporary = File(directory, ".incoming-$transactionId.tmp")
        val pendingTemporary = File(directory, ".pending-$transactionId.tmp")
        var pendingTransaction: PendingModelTransaction? = null
        try {
            val limitFailureReason = if (maximumBytes < ModelImportPolicy.MAXIMUM_MODEL_BYTES) {
                ModelImportFailureReason.INSUFFICIENT_STORAGE
            } else {
                ModelImportFailureReason.MODEL_TOO_LARGE
            }
            val input = openSourceInput(uri)
            operation.registerCancellationResource(input)
            val copied = try {
                input.use {
                    FileOutputStream(temporary).use { output ->
                        ModelImportCopier.copy(
                            input = input,
                            output = output,
                            maximumBytes = maximumBytes,
                            limitFailureReason = limitFailureReason,
                            progressListener = { processedBytes ->
                                operation.reportProgress(
                                    ModelImportProgress(
                                        stage = ModelImportStage.COPYING,
                                        processedBytes = processedBytes,
                                        totalBytes = document.declaredSize?.takeIf {
                                            it > 0L && processedBytes <= it
                                        },
                                    ),
                                )
                            },
                        ).also {
                            operation.ensureActive()
                            output.fd.sync()
                            operation.ensureActive()
                        }
                    }
                }
            } finally {
                operation.unregisterCancellationResource(input)
            }
            val sha256 = copied.sha256
            val destination = File(directory, "model-$sha256.litertlm")
            val destinationExistedBeforeImport = destination.exists()
            if (!PendingModelTransactionPolicy.shouldCreateMarker(destinationExistedBeforeImport)) {
                require(
                    destination.isFile &&
                        destination.length() == copied.byteCount &&
                        digestExistingFile(destination, operation) == sha256
                ) {
                    "A conflicting private model file already exists"
                }
                operation.whileActive {
                    require(temporary.delete() || !temporary.exists()) {
                        "Duplicate model transaction file cannot be removed"
                    }
                }
            } else {
                pendingTransaction = PendingModelTransactionPolicy.create(transactionId, sha256)
                operation.whileActive {
                    publishPendingMarker(pendingTransaction, pendingTemporary)
                }
                operation.whileActive {
                    Os.rename(temporary.absolutePath, destination.absolutePath)
                    syncDirectory()
                }
            }

            val candidate = ModelCatalogEntry(
                modelId = ModelImportPolicy.stableModelId(sha256),
                displayName = ModelImportPolicy.safeDisplayName(document.displayName),
                fileName = destination.name,
                sizeBytes = copied.byteCount,
                sha256 = sha256,
                importedAtMillis = System.currentTimeMillis(),
            )
            operation.reportProgress(
                ModelImportProgress(
                    stage = ModelImportStage.PUBLISHING,
                    processedBytes = copied.byteCount,
                    totalBytes = copied.byteCount,
                ),
            )
            operation.beginCommit()
            val update = ModelCatalogPolicy.integrateImport(
                document = readCatalog(),
                candidate = candidate,
                select = true,
            )
            publishCommittedImport(update, pendingTransaction)
            // The Binder provider runs in a separate process. A previous model may already have
            // been selected from metadata but not opened by LiteRT-LM yet, so hash-named model
            // generations are intentionally retained instead of being deleted during import.
            return update.model.toImportedModel()
        } catch (error: Throwable) {
            pendingTransaction?.let { marker -> runCatching { rollbackOwnedPendingTransaction(marker) } }
            throw error
        } finally {
            if (temporary.exists()) temporary.delete()
            if (pendingTemporary.exists()) pendingTemporary.delete()
        }
    }

    private fun readCatalogWithLegacyFallback(): ModelCatalogDocument = when {
        catalogFile.exists() -> readCatalog()
        legacyMetadataFile.exists() -> ModelCatalogPolicy.fromLegacy(readLegacyCurrentEntry())
        else -> ModelCatalogPolicy.empty()
    }

    private fun readAuthoritativeManagerCatalog(): ModelCatalogDocument {
        require(!pendingMarkerFile.exists()) { "Interrupted model import recovery is required" }
        return readCatalog()
    }

    private fun readCatalog(): ModelCatalogDocument {
        requireSafeRegularFile(catalogFile)
        require(catalogFile.length() in 1L..ModelCatalogCodec.MAXIMUM_CATALOG_BYTES.toLong()) {
            "Model catalog size is invalid"
        }
        return ModelCatalogCodec.decode(catalogFile.readBytes()).also(::requireCatalogFiles)
    }

    private fun readLegacyCurrentEntry(): ModelCatalogEntry {
        requireSafeRegularFile(legacyMetadataFile)
        require(legacyMetadataFile.length() in 1L..ModelCatalogCodec.MAXIMUM_LEGACY_BYTES.toLong()) {
            "Legacy model metadata size is invalid"
        }
        return ModelCatalogCodec.decodeLegacyCurrent(legacyMetadataFile.readBytes()).also(::requireCatalogFile)
    }

    private fun requireCatalogFiles(document: ModelCatalogDocument) {
        document.entries.forEach(::requireCatalogFile)
    }

    private fun requireCatalogFile(entry: ModelCatalogEntry) {
        val modelFile = File(directory, entry.fileName)
        requireSafeRegularFile(modelFile)
        require(modelFile.length() == entry.sizeBytes) { "Catalog model file size changed" }
    }

    private fun ModelCatalogEntry.toImportedModel(): ImportedModel = ImportedModel(
        modelId = modelId,
        displayName = displayName,
        file = File(directory, fileName),
        sizeBytes = sizeBytes,
        sha256 = sha256,
        importedAtMillis = importedAtMillis,
        healthStatus = healthStatus,
    )

    private fun ModelCatalogDocument.toManagerSnapshot(): ModelManagerSnapshot =
        ModelManagerSnapshot.from(this) { entry -> entry.toImportedModel() }

    private fun publishCatalogExactly(document: ModelCatalogDocument) {
        val normalized = ModelCatalogPolicy.normalize(document)
        val bytes = ModelCatalogCodec.encode(normalized)
        val temporary = File(directory, ".catalog-${UUID.randomUUID()}.tmp")
        val publicationFailure = try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Os.rename(temporary.absolutePath, catalogFile.absolutePath)
            syncDirectory()
            null
        } catch (error: Throwable) {
            error
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        if (publicationFailure == null) return
        if (runCatching { readCatalog() == normalized }.getOrDefault(false).not()) {
            throw publicationFailure
        }
        try {
            syncDirectory()
        } catch (retryFailure: Throwable) {
            retryFailure.addSuppressed(publicationFailure)
            throw retryFailure
        }
    }

    /** Verifies and re-syncs catalog publication when the first directory sync is ambiguous. */
    private fun publishCommittedImport(
        update: ModelCatalogUpdate,
        marker: PendingModelTransaction?,
    ) {
        if (update.changed) publishCatalogExactly(update.document)
        marker?.let { runCatching { completePendingTransaction(it) } }
    }

    private fun publishPendingMarker(marker: PendingModelTransaction, temporary: File) {
        require(!pendingMarkerFile.exists()) { "A model transaction is already pending" }
        require(!temporary.exists()) { "Model transaction temporary file already exists" }
        FileOutputStream(temporary).use { output ->
            output.write(PendingModelTransactionPolicy.encode(marker))
            output.fd.sync()
        }
        Os.rename(temporary.absolutePath, pendingMarkerFile.absolutePath)
        syncDirectory()
    }

    private fun completePendingTransaction(marker: PendingModelTransaction) {
        require(readPendingMarker() == marker) { "Model transaction marker changed unexpectedly" }
        require(readCatalog().entries.any { it.fileName == marker.destinationFileName }) {
            "Model transaction catalog was not published"
        }
        require(pendingMarkerFile.delete() || !pendingMarkerFile.exists()) {
            "Published model transaction marker cannot be removed"
        }
        syncDirectory()
    }

    private fun rollbackOwnedPendingTransaction(marker: PendingModelTransaction) {
        if (!pendingMarkerFile.isFile) return
        val persisted = runCatching { readPendingMarker() }.getOrNull() ?: return
        if (persisted != marker) return
        resolvePendingTransaction(marker)
        syncDirectory()
    }

    private fun resolvePendingTransaction(marker: PendingModelTransaction) {
        val destination = File(directory, marker.destinationFileName)
        requireSafeDirectChild(destination)
        val decision = PendingModelTransactionPolicy.decideRecovery(
            marker = marker,
            publishedFileNames = readCatalogWithLegacyFallback().entries.mapTo(linkedSetOf()) { it.fileName },
            destinationExists = destination.exists(),
        )
        if (decision == PendingModelRecoveryDecision.DELETE_UNPUBLISHED_DESTINATION) {
            requireSafeRegularFile(destination)
            require(destination.delete() || !destination.exists()) {
                "Unpublished model transaction destination cannot be removed"
            }
        }
        require(pendingMarkerFile.delete() || !pendingMarkerFile.exists()) {
            "Model transaction marker cannot be removed"
        }
    }

    private fun readPendingMarker(): PendingModelTransaction {
        requireSafeRegularFile(pendingMarkerFile)
        require(pendingMarkerFile.length() in 1L..PendingModelTransactionPolicy.MAXIMUM_MARKER_BYTES.toLong()) {
            "Model transaction marker size is invalid"
        }
        return PendingModelTransactionPolicy.decode(pendingMarkerFile.readBytes())
    }

    private fun querySourceDocument(uri: Uri): DocumentInfo = try {
        queryDocument(uri)
    } catch (error: Exception) {
        throw ModelImportFailureException(
            ModelImportFailureReason.SOURCE_UNAVAILABLE,
            "The selected model metadata cannot be read",
            error,
        )
    }

    private fun digestExistingFile(file: File, operation: ModelImportOperationControl): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                operation.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun openSourceInput(uri: Uri): InputStream = try {
        applicationContext.contentResolver.openInputStream(uri)
            ?: throw ModelImportFailureException(
                ModelImportFailureReason.SOURCE_UNAVAILABLE,
                "The selected model cannot be opened",
            )
    } catch (error: ModelImportFailureException) {
        throw error
    } catch (error: Exception) {
        throw ModelImportFailureException(
            ModelImportFailureReason.SOURCE_UNAVAILABLE,
            "The selected model cannot be opened",
            error,
        )
    }

    private fun queryDocument(uri: Uri): DocumentInfo {
        var name: String? = null
        var size: Long? = null
        applicationContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return DocumentInfo(name ?: "model.litertlm", size?.takeIf { it >= 0L })
    }

    private fun ensureDirectory() {
        require((directory.isDirectory || directory.mkdirs()) && directory.canonicalFile.parentFile == applicationContext.filesDir.canonicalFile) {
            "Private model storage is unavailable"
        }
    }

    private fun requireSafeRegularFile(file: File) {
        require(file.isFile) { "Private model transaction path is not a regular file" }
        requireSafeDirectChild(file)
    }

    private fun requireSafeDirectChild(file: File) {
        require(file.absoluteFile.parentFile == directory.absoluteFile) {
            "Private model transaction path escaped its directory"
        }
        require(file.canonicalFile.parentFile == directory.canonicalFile) {
            "Private model transaction path escaped through a link"
        }
    }

    private fun syncDirectory() {
        val descriptor = Os.open(
            directory.absolutePath,
            OsConstants.O_RDONLY,
            0,
        )
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private data class DocumentInfo(val displayName: String, val declaredSize: Long?)
    private companion object {
        const val DIRECTORY_NAME = "models"
        const val CATALOG_FILE_NAME = "catalog.json"
        const val LEGACY_METADATA_FILE_NAME = "current.json"
        const val PENDING_MARKER_FILE_NAME = ".pending-model.json"
        const val MAXIMUM_MODEL_DIRECTORY_ENTRIES = 16_384
        private const val TRANSACTION_ID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
        val RECOVERABLE_TEMP_FILE = Regex("^\\.(?:incoming|current|catalog|pending)-$TRANSACTION_ID_PATTERN\\.tmp$")
    }
}
