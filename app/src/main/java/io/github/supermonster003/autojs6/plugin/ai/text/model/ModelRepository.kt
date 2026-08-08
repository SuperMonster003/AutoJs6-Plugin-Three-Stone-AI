package io.github.supermonster003.autojs6.plugin.ai.text.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

internal data class ImportedModel(
    val modelId: String,
    val displayName: String,
    val file: File,
    val sizeBytes: Long,
    val sha256: String,
    val importedAtMillis: Long,
) {
    val listingGeneration: String
        get() = "litertlm-${sha256.take(32)}"
}

internal class ModelRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val directory = File(applicationContext.filesDir, DIRECTORY_NAME)
    private val metadataFile = File(directory, METADATA_FILE_NAME)
    private val pendingMarkerFile = File(directory, PENDING_MARKER_FILE_NAME)

    @Synchronized
    fun current(): ImportedModel? = runCatching { readCurrent() }.getOrNull()

    @Synchronized
    fun findByModelId(modelId: String): ImportedModel? {
        current()?.takeIf { it.modelId == modelId }?.let { return it }
        val match = MODEL_ID.matchEntire(modelId) ?: return null
        val digestPrefix = match.groupValues[1]
        val candidates = directory.listFiles { file ->
            file.isFile && FILE_NAME.matches(file.name) && file.name.startsWith("model-$digestPrefix")
        }.orEmpty()
        if (candidates.size != 1) return null
        val file = candidates.single()
        val digest = FILE_NAME.matchEntire(file.name)?.groupValues?.get(1) ?: return null
        val size = file.length()
        if (size !in 1L..ModelImportPolicy.MAXIMUM_MODEL_BYTES) return null
        return ImportedModel(
            modelId = ModelImportPolicy.stableModelId(digest),
            displayName = file.name,
            file = file,
            sizeBytes = size,
            sha256 = digest,
            importedAtMillis = file.lastModified(),
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
        require(entries.size <= MAXIMUM_RECOVERY_DIRECTORY_ENTRIES) {
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
        if (directoryChanged) syncDirectory()
    }

    @Synchronized
    fun importFrom(uri: Uri): ImportedModel {
        require(uri.scheme == "content") { "Only a Storage Access Framework content URI is accepted" }
        ensureDirectory()
        require(!pendingMarkerFile.exists()) { "Interrupted model import recovery is required" }
        val document = queryDocument(uri)
        ModelImportPolicy.requireImportableName(document.displayName)
        val maximumBytes = ModelImportPolicy.maximumCopyBytes(directory.usableSpace)
        require(maximumBytes > 0L) { "There is not enough free storage for a model" }
        document.declaredSize?.let { size ->
            require(size in 1L..maximumBytes) { "The selected model exceeds the import limit" }
        }

        val transactionId = UUID.randomUUID().toString()
        val temporary = File(directory, ".incoming-$transactionId.tmp")
        val pendingTemporary = File(directory, ".pending-$transactionId.tmp")
        var pendingTransaction: PendingModelTransaction? = null
        try {
            val copied = applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                copyBounded(input, temporary, maximumBytes)
            } ?: throw IllegalArgumentException("The selected model cannot be opened")
            val sha256 = copied.sha256
            val destination = File(directory, "model-$sha256.litertlm")
            val destinationExistedBeforeImport = destination.exists()
            if (!PendingModelTransactionPolicy.shouldCreateMarker(destinationExistedBeforeImport)) {
                require(destination.isFile && destination.length() == copied.byteCount) {
                    "A conflicting private model file already exists"
                }
                temporary.delete()
            } else {
                pendingTransaction = PendingModelTransactionPolicy.create(transactionId, sha256)
                publishPendingMarker(pendingTransaction, pendingTemporary)
                Os.rename(temporary.absolutePath, destination.absolutePath)
                syncDirectory()
            }

            val imported = ImportedModel(
                modelId = ModelImportPolicy.stableModelId(sha256),
                displayName = ModelImportPolicy.safeDisplayName(document.displayName),
                file = destination,
                sizeBytes = copied.byteCount,
                sha256 = sha256,
                importedAtMillis = System.currentTimeMillis(),
            )
            publishMetadata(imported)
            pendingTransaction?.let(::completePendingTransaction)
            // The Binder provider runs in a separate process. A previous model may already have
            // been selected from metadata but not opened by LiteRT-LM yet, so hash-named model
            // generations are intentionally retained instead of being deleted during import.
            return imported
        } catch (error: Throwable) {
            pendingTransaction?.let { marker -> runCatching { rollbackOwnedPendingTransaction(marker) } }
            throw error
        } finally {
            if (temporary.exists()) temporary.delete()
            if (pendingTemporary.exists()) pendingTemporary.delete()
        }
    }

    private fun readCurrent(): ImportedModel? {
        if (!metadataFile.isFile || metadataFile.length() !in 1L..MAXIMUM_METADATA_BYTES) return null
        val json = JSONObject(metadataFile.readText(Charsets.UTF_8))
        if (json.getInt("schema") != METADATA_SCHEMA) return null
        val sha256 = json.getString("sha256")
        val expectedModelId = ModelImportPolicy.stableModelId(sha256)
        if (json.getString("modelId") != expectedModelId) return null
        val expectedFileName = "model-$sha256.litertlm"
        if (json.getString("fileName") != expectedFileName) return null
        val modelFile = File(directory, expectedFileName)
        if (modelFile.canonicalFile.parentFile != directory.canonicalFile) return null
        val size = json.getLong("sizeBytes")
        if (!modelFile.isFile || size !in 1L..ModelImportPolicy.MAXIMUM_MODEL_BYTES || modelFile.length() != size) {
            return null
        }
        val displayName = ModelImportPolicy.safeDisplayName(json.getString("displayName"))
        return ImportedModel(
            modelId = expectedModelId,
            displayName = displayName,
            file = modelFile,
            sizeBytes = size,
            sha256 = sha256,
            importedAtMillis = json.getLong("importedAtMillis"),
        )
    }

    private fun publishMetadata(model: ImportedModel) {
        val json = JSONObject()
            .put("schema", METADATA_SCHEMA)
            .put("modelId", model.modelId)
            .put("displayName", model.displayName)
            .put("fileName", model.file.name)
            .put("sizeBytes", model.sizeBytes)
            .put("sha256", model.sha256)
            .put("importedAtMillis", model.importedAtMillis)
        val temporary = File(directory, ".current-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(json.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Os.rename(temporary.absolutePath, metadataFile.absolutePath)
            syncDirectory()
        } finally {
            if (temporary.exists()) temporary.delete()
        }
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
        require(readCurrent()?.file?.name == marker.destinationFileName) {
            "Model transaction metadata was not published"
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
            currentFileName = readCurrent()?.file?.name,
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

    private fun copyBounded(input: InputStream, destination: File, maximumBytes: Long): CopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(4)
        var headerCount = 0
        var copied = 0L
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        FileOutputStream(destination).use { output ->
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Model import was interrupted")
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                require(copied <= maximumBytes - count) { "The selected model exceeds the import limit" }
                if (headerCount < header.size) {
                    val headerBytes = minOf(count, header.size - headerCount)
                    buffer.copyInto(header, headerCount, 0, headerBytes)
                    headerCount += headerBytes
                }
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                copied += count
            }
            require(copied > 0L) { "The selected model is empty" }
            ModelImportPolicy.requireZipHeader(header.copyOf(headerCount))
            output.fd.sync()
        }
        return CopyResult(
            byteCount = copied,
            sha256 = digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xFF)
            },
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
    private data class CopyResult(val byteCount: Long, val sha256: String)

    private companion object {
        const val DIRECTORY_NAME = "models"
        const val METADATA_FILE_NAME = "current.json"
        const val PENDING_MARKER_FILE_NAME = ".pending-model.json"
        const val METADATA_SCHEMA = 1
        const val MAXIMUM_METADATA_BYTES = 64L * 1024L
        const val COPY_BUFFER_BYTES = 1024 * 1024
        const val MAXIMUM_RECOVERY_DIRECTORY_ENTRIES = 16_384
        private const val TRANSACTION_ID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
        val MODEL_ID = Regex("^litertlm\\.([0-9a-f]{32})$")
        val FILE_NAME = Regex("^model-([0-9a-f]{64})\\.litertlm$")
        val RECOVERABLE_TEMP_FILE = Regex("^\\.(?:incoming|current|pending)-$TRANSACTION_ID_PATTERN\\.tmp$")
    }
}
