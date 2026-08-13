package io.github.supermonster003.autojs6.plugin.ai.text

import io.github.supermonster003.autojs6.plugin.ai.text.model.ImportedModel
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelCatalogDocument
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelCatalogEntry
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportFailureReason
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportProgress
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportPolicy
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportState
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelManagerSnapshot
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelManagerState
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelManagerPresentationTest {
    @Test
    fun exposesTheModelThatCanStillBeCopiedForEveryState() {
        assertNull(ModelManagerPresentation.visibleModel(ModelImportState.Preparing))
        assertNull(ModelManagerPresentation.visibleModel(ModelImportState.Unavailable))
        assertNull(ModelManagerPresentation.visibleModel(ModelImportState.Ready<String>(null)))
        assertEquals("ready", ModelManagerPresentation.visibleModel(ModelImportState.Ready("ready")))
        assertEquals(
            "previous",
            ModelManagerPresentation.visibleModel(ModelImportState.Running(1L, "previous")),
        )
        assertEquals(
            "previous-while-cancelling",
            ModelManagerPresentation.visibleModel(
                ModelImportState.Cancelling(
                    operationId = 2L,
                    previous = "previous-while-cancelling",
                    progress = ModelImportProgress.initial(),
                ),
            ),
        )
        assertEquals(
            "imported",
            ModelManagerPresentation.visibleModel(ModelImportState.Succeeded(3L, "imported")),
        )
        assertEquals(
            "current-after-cancel",
            ModelManagerPresentation.visibleModel(
                ModelImportState.Cancelled(4L, "current-after-cancel"),
            ),
        )
        assertEquals(
            "current",
            ModelManagerPresentation.visibleModel(
                ModelImportState.Failed(5L, "current", ModelImportFailureReason.INVALID_FORMAT),
            ),
        )
    }

    @Test
    fun runningWithoutPreviousModelDoesNotExposeACopyAction() {
        assertNull(ModelManagerPresentation.visibleModel(ModelImportState.Running<String>(1L, null)))
    }

    @Test
    fun catalogAvailabilityDistinguishesLoadingUnavailableAndReadyEmpty() {
        val loading = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Preparing,
                snapshot = null,
                selection = ModelSelectionState.Idle,
            ),
        )
        assertEquals(ModelCatalogAvailability.LOADING, loading.availability)
        assertTrue(loading.rows.isEmpty())

        val unavailable = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Unavailable,
                snapshot = null,
                selection = ModelSelectionState.Idle,
            ),
        )
        assertEquals(ModelCatalogAvailability.UNAVAILABLE, unavailable.availability)
        assertTrue(unavailable.rows.isEmpty())

        val ready = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Ready(null),
                snapshot = snapshot(selectedModelId = null, entries = emptyList()),
                selection = ModelSelectionState.Idle,
            ),
        )
        assertEquals(ModelCatalogAvailability.READY, ready.availability)
        assertTrue(ready.rows.isEmpty())
        assertEquals(0L, ready.totalSizeBytes)
        assertFalse(ready.selectionBusy)
    }

    @Test
    fun readyCatalogExposesSelectedRowsExactSizesAndEnabledSelection() {
        val entryA = entry("11", "A", 8L)
        val entryB = entry("22", "B", 13L)
        val snapshot = snapshot(entryA.modelId, listOf(entryB, entryA))

        val view = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Ready(snapshot.selectedModel),
                snapshot = snapshot,
                selection = ModelSelectionState.Idle,
            ),
        )

        assertEquals(ModelCatalogAvailability.READY, view.availability)
        assertEquals(listOf(entryA.modelId, entryB.modelId), view.rows.map { it.modelId })
        assertEquals(listOf("A", "B"), view.rows.map { it.displayName })
        assertEquals(listOf(8L, 13L), view.rows.map { it.sizeBytes })
        assertEquals(listOf(true, false), view.rows.map { it.selected })
        assertTrue(view.rows.all { it.selectionEnabled })
        assertEquals(21L, view.totalSizeBytes)
        assertFalse(view.selectionBusy)
    }

    @Test
    fun pendingSelectionAndImportOwnershipDisableEveryRow() {
        val entryA = entry("11", "A", 8L)
        val entryB = entry("22", "B", 13L)
        val snapshot = snapshot(entryA.modelId, listOf(entryA, entryB))

        val view = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Ready(snapshot.selectedModel),
                snapshot = snapshot,
                selection = ModelSelectionState.Selecting(7L, entryB.modelId),
            ),
        )

        assertEquals(listOf(false, true), view.rows.map { it.selected })
        assertTrue(view.rows.none { it.selectionEnabled })
        assertTrue(view.selectionBusy)

        val importing = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Running(8L, snapshot.selectedModel),
                snapshot = snapshot,
                selection = ModelSelectionState.Idle,
            ),
        )
        assertTrue(importing.rows.none { it.selectionEnabled })
        assertFalse(importing.selectionBusy)
    }

    private fun snapshot(
        selectedModelId: String?,
        entries: List<ModelCatalogEntry>,
    ): ModelManagerSnapshot = ModelManagerSnapshot.from(
        ModelCatalogDocument(1L, selectedModelId, entries),
        ::importedModel,
    )

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
