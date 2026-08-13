package io.github.supermonster003.autojs6.plugin.ai.text.model

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal data class ModelCatalogEntry(
    val modelId: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val importedAtMillis: Long,
)

internal data class ModelCatalogDocument(
    val revision: Long,
    val selectedModelId: String?,
    val entries: List<ModelCatalogEntry>,
)

internal data class ModelCatalogUpdate(
    val document: ModelCatalogDocument,
    val model: ModelCatalogEntry,
    val changed: Boolean,
)

internal object ModelCatalogPolicy {
    const val SCHEMA = 2
    const val MAXIMUM_ENTRIES = 100
    private val SHA_256 = Regex("^[0-9a-f]{64}$")
    private val MODEL_ID = Regex("^litertlm\\.([0-9a-f]{32})$")
    private val FILE_NAME = Regex("^model-([0-9a-f]{64})\\.litertlm$")

    fun empty(): ModelCatalogDocument = ModelCatalogDocument(
        revision = 1L,
        selectedModelId = null,
        entries = emptyList(),
    )

    fun fromLegacy(entry: ModelCatalogEntry): ModelCatalogDocument = normalize(
        ModelCatalogDocument(
            revision = 1L,
            selectedModelId = entry.modelId,
            entries = listOf(entry),
        ),
    )

    fun normalize(document: ModelCatalogDocument): ModelCatalogDocument {
        require(document.revision >= 1L) { "Model catalog revision is invalid" }
        require(document.entries.size <= MAXIMUM_ENTRIES) { "Model catalog contains too many entries" }
        val sorted = document.entries.sortedBy(ModelCatalogEntry::modelId)
        sorted.forEach(::requireValidEntry)
        require(sorted.map(ModelCatalogEntry::modelId).distinct().size == sorted.size) {
            "Model catalog contains duplicate model IDs"
        }
        require(sorted.map(ModelCatalogEntry::sha256).distinct().size == sorted.size) {
            "Model catalog contains duplicate model digests"
        }
        require(sorted.map(ModelCatalogEntry::fileName).distinct().size == sorted.size) {
            "Model catalog contains duplicate model files"
        }
        document.selectedModelId?.let { selected ->
            require(sorted.any { it.modelId == selected }) { "Selected model is not present in the catalog" }
        }
        return document.copy(entries = sorted)
    }

    fun integrateImport(
        document: ModelCatalogDocument,
        candidate: ModelCatalogEntry,
        select: Boolean = true,
    ): ModelCatalogUpdate {
        val current = normalize(document)
        requireValidEntry(candidate)
        val digestMatch = current.entries.singleOrNull { it.sha256 == candidate.sha256 }
        val chosen = digestMatch ?: candidate
        if (digestMatch == null) {
            require(current.entries.none { it.modelId == candidate.modelId }) {
                "Model digest prefix collides with an existing model"
            }
            require(current.entries.size < MAXIMUM_ENTRIES) { "Model catalog is full" }
        } else {
            require(
                digestMatch.modelId == candidate.modelId &&
                    digestMatch.fileName == candidate.fileName &&
                    digestMatch.sizeBytes == candidate.sizeBytes,
            ) { "Existing model catalog entry conflicts with imported content" }
        }
        val selected = if (select) chosen.modelId else current.selectedModelId
        val entries = if (digestMatch == null) current.entries + candidate else current.entries
        val proposed = normalize(current.copy(selectedModelId = selected, entries = entries))
        val contentChanged = proposed.selectedModelId != current.selectedModelId || proposed.entries != current.entries
        val updated = if (contentChanged) proposed.copy(revision = Math.addExact(current.revision, 1L)) else current
        return ModelCatalogUpdate(updated, chosen, contentChanged)
    }

    /** Changes only when fields exposed by model listing change. */
    fun listingGeneration(document: ModelCatalogDocument): String {
        val normalized = normalize(document)
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                normalized.entries.forEach { entry ->
                    output.writeLengthPrefixed(entry.modelId)
                    output.writeLengthPrefixed(entry.displayName)
                }
            }
            buffer.toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "litertlm-catalog-v2-${digest.toHex().take(32)}"
    }

    fun requireValidEntry(entry: ModelCatalogEntry) {
        require(MODEL_ID.matches(entry.modelId)) { "Model catalog ID is invalid" }
        require(SHA_256.matches(entry.sha256)) { "Model catalog digest is invalid" }
        require(entry.modelId == ModelImportPolicy.stableModelId(entry.sha256)) {
            "Model catalog ID does not match its digest"
        }
        require(FILE_NAME.matches(entry.fileName) && entry.fileName == "model-${entry.sha256}.litertlm") {
            "Model catalog file does not match its digest"
        }
        val displayNameBytes = runCatching {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(entry.displayName))
                .remaining()
        }.getOrElse { throw IllegalArgumentException("Model catalog display name is invalid", it) }
        require(displayNameBytes in 1..256) {
            "Model catalog display name is invalid"
        }
        require(entry.sizeBytes in 1L..ModelImportPolicy.MAXIMUM_MODEL_BYTES) {
            "Model catalog size is invalid"
        }
        require(entry.importedAtMillis >= 0L) { "Model catalog import time is invalid" }
    }

    private fun DataOutputStream.writeLengthPrefixed(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
