package io.github.supermonster003.autojs6.plugin.ai.text.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ModelManagerSnapshotTest {
    @Test
    fun exposesSortedRowsSelectedModelAndExactTotalThroughDefensiveList() {
        val entryA = entry("11", "A", 8L)
        val entryB = entry("22", "B", 13L)
        val sourceEntries = mutableListOf(entryB, entryA)
        val snapshot = ModelManagerSnapshot.from(
            ModelCatalogDocument(4L, entryB.modelId, sourceEntries),
            ::importedModel,
        )

        assertEquals(listOf(entryA.modelId, entryB.modelId), snapshot.models.map { it.modelId })
        assertEquals(entryB.modelId, snapshot.selectedModelId)
        assertEquals(entryB.modelId, snapshot.selectedModel?.modelId)
        assertEquals(21L, snapshot.totalSizeBytes)
        assertEquals(listOf(8L, 13L), snapshot.models.map { it.sizeBytes })

        sourceEntries.clear()
        assertEquals(2, snapshot.models.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.models as MutableList<ImportedModel>).clear()
        }
        assertEquals(2, snapshot.models.size)
    }

    private fun entry(byte: String, displayName: String, sizeBytes: Long): ModelCatalogEntry {
        val digest = byte.repeat(32)
        return ModelCatalogEntry(
            modelId = ModelImportPolicy.stableModelId(digest),
            displayName = displayName,
            fileName = "model-$digest.litertlm",
            sizeBytes = sizeBytes,
            sha256 = digest,
            importedAtMillis = 1L,
        )
    }

    private fun importedModel(entry: ModelCatalogEntry) = ImportedModel(
        modelId = entry.modelId,
        displayName = entry.displayName,
        file = File("models", entry.fileName),
        sizeBytes = entry.sizeBytes,
        sha256 = entry.sha256,
        importedAtMillis = entry.importedAtMillis,
    )
}
