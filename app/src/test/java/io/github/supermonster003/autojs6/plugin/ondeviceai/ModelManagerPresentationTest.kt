package io.github.supermonster003.autojs6.plugin.ondeviceai

import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ImportedModel
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelCatalogDocument
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelCatalogEntry
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelDeletionState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelHealthCheckState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelHealthStatus
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelImportFailureReason
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelImportProgress
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelImportPolicy
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelImportState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelManagerSnapshot
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelManagerState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelRenameState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelSelectionState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelStorageCleanupState
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
        assertFalse(ready.catalogMutationBusy)
        assertTrue(ready.cleanupEnabled)
        assertFalse(ready.cleanupInProgress)
    }

    @Test
    fun readyCatalogExposesSelectedRowsExactSizesAndEnabledSelection() {
        val entryA = entry("11", "A", 8L, ModelHealthStatus.AVAILABLE)
        val entryB = entry("22", "B", 13L, ModelHealthStatus.INCOMPATIBLE)
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
        assertEquals(
            listOf(ModelHealthStatus.AVAILABLE, ModelHealthStatus.INCOMPATIBLE),
            view.rows.map { it.healthStatus },
        )
        assertTrue(view.rows.none { it.healthCheckInProgress })
        assertTrue(view.rows.all { it.healthCheckEnabled })
        assertTrue(view.rows.all { it.selectionEnabled })
        assertTrue(view.rows.all { it.renameEnabled })
        assertTrue(view.rows.all { it.deletionEnabled })
        assertEquals(21L, view.totalSizeBytes)
        assertFalse(view.catalogMutationBusy)
        assertTrue(view.cleanupEnabled)
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
        assertTrue(view.rows.none { it.healthCheckEnabled })
        assertTrue(view.rows.none { it.selectionEnabled })
        assertTrue(view.rows.none { it.renameEnabled })
        assertTrue(view.rows.none { it.deletionEnabled })
        assertTrue(view.catalogMutationBusy)
        assertFalse(view.cleanupEnabled)

        val importing = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Running(8L, snapshot.selectedModel),
                snapshot = snapshot,
                selection = ModelSelectionState.Idle,
            ),
        )
        assertTrue(importing.rows.none { it.healthCheckEnabled })
        assertTrue(importing.rows.none { it.selectionEnabled })
        assertTrue(importing.rows.none { it.renameEnabled })
        assertTrue(importing.rows.none { it.deletionEnabled })
        assertFalse(importing.catalogMutationBusy)
        assertFalse(importing.cleanupEnabled)
    }

    @Test
    fun pendingDeletionKeepsSelectionStableAndDisablesEveryCatalogMutation() {
        val entryA = entry("11", "A", 8L, ModelHealthStatus.AVAILABLE)
        val entryB = entry("22", "B", 13L, ModelHealthStatus.INCOMPATIBLE)
        val snapshot = snapshot(entryA.modelId, listOf(entryA, entryB))

        val view = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Ready(snapshot.selectedModel),
                snapshot = snapshot,
                selection = ModelSelectionState.Idle,
                deletion = ModelDeletionState.Deleting(9L, entryB.modelId),
            ),
        )

        assertEquals(listOf(true, false), view.rows.map { it.selected })
        assertTrue(view.rows.none { it.healthCheckEnabled })
        assertTrue(view.rows.none { it.selectionEnabled })
        assertTrue(view.rows.none { it.renameEnabled })
        assertTrue(view.rows.none { it.deletionEnabled })
        assertTrue(view.catalogMutationBusy)
        assertFalse(view.cleanupEnabled)
    }

    @Test
    fun storageCleanupDisablesAllManagerMutationsWithoutChangingSelection() {
        val entryA = entry("11", "A", 8L)
        val entryB = entry("22", "B", 13L)
        val snapshot = snapshot(entryA.modelId, listOf(entryA, entryB))

        val view = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Ready(snapshot.selectedModel),
                snapshot = snapshot,
                selection = ModelSelectionState.Idle,
                storageCleanup = ModelStorageCleanupState.Cleaning(10L),
            ),
        )

        assertEquals(listOf(true, false), view.rows.map { it.selected })
        assertTrue(view.rows.none { it.healthCheckEnabled })
        assertTrue(view.rows.none { it.selectionEnabled })
        assertTrue(view.rows.none { it.renameEnabled })
        assertTrue(view.rows.none { it.deletionEnabled })
        assertTrue(view.catalogMutationBusy)
        assertFalse(view.cleanupEnabled)
        assertTrue(view.cleanupInProgress)
    }

    @Test
    fun pendingRenameKeepsCommittedNameVisibleAndDisablesEveryMutation() {
        val entryA = entry("11", "A", 8L)
        val entryB = entry("22", "B", 13L)
        val snapshot = snapshot(entryA.modelId, listOf(entryA, entryB))

        val view = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Ready(snapshot.selectedModel),
                snapshot = snapshot,
                selection = ModelSelectionState.Idle,
                rename = ModelRenameState.Renaming(11L, entryA.modelId, "Renamed"),
            ),
        )

        assertEquals(listOf("A", "B"), view.rows.map { it.displayName })
        assertTrue(view.rows.none { it.healthCheckEnabled })
        assertTrue(view.rows.none { it.selectionEnabled })
        assertTrue(view.rows.none { it.renameEnabled })
        assertTrue(view.rows.none { it.deletionEnabled })
        assertTrue(view.catalogMutationBusy)
        assertFalse(view.cleanupEnabled)
    }

    @Test
    fun pendingHealthCheckMarksOnlyItsModelAndDisablesEveryCatalogMutation() {
        val entryA = entry("11", "A", 8L, ModelHealthStatus.AVAILABLE)
        val entryB = entry("22", "B", 13L)
        val snapshot = snapshot(entryA.modelId, listOf(entryA, entryB))

        val view = ModelManagerPresentation.managerView(
            ModelManagerState(
                importState = ModelImportState.Ready(snapshot.selectedModel),
                snapshot = snapshot,
                selection = ModelSelectionState.Idle,
                healthCheck = ModelHealthCheckState.Checking(12L, entryB.modelId),
            ),
        )

        assertEquals(listOf(false, true), view.rows.map { it.healthCheckInProgress })
        assertEquals(
            listOf(ModelHealthStatus.AVAILABLE, ModelHealthStatus.NOT_CHECKED),
            view.rows.map { it.healthStatus },
        )
        assertTrue(view.rows.none { it.healthCheckEnabled })
        assertTrue(view.rows.none { it.selectionEnabled })
        assertTrue(view.rows.none { it.renameEnabled })
        assertTrue(view.rows.none { it.deletionEnabled })
        assertTrue(view.catalogMutationBusy)
        assertFalse(view.cleanupEnabled)
    }

    private fun snapshot(
        selectedModelId: String?,
        entries: List<ModelCatalogEntry>,
    ): ModelManagerSnapshot = ModelManagerSnapshot.from(
        ModelCatalogDocument(1L, selectedModelId, entries),
        ::importedModel,
    )

    private fun entry(
        byte: String,
        displayName: String,
        sizeBytes: Long,
        healthStatus: ModelHealthStatus = ModelHealthStatus.NOT_CHECKED,
    ): ModelCatalogEntry {
        val digest = byte.repeat(32)
        return ModelCatalogEntry(
            modelId = ModelImportPolicy.stableModelId(digest),
            displayName = displayName,
            fileName = "model-$digest.litertlm",
            sizeBytes = sizeBytes,
            sha256 = digest,
            importedAtMillis = 1L,
            healthStatus = healthStatus,
        )
    }

    private fun importedModel(entry: ModelCatalogEntry) = ImportedModel(
        modelId = entry.modelId,
        displayName = entry.displayName,
        file = File("models", entry.fileName),
        sizeBytes = entry.sizeBytes,
        sha256 = entry.sha256,
        importedAtMillis = entry.importedAtMillis,
        healthStatus = entry.healthStatus,
    )
}
