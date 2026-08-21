package io.github.supermonster003.autojs6.plugin.ondeviceai

import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelDeletionState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelHealthCheckState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelHealthStatus
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelImportState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelManagerState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelRenameState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelSelectionState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelStorageCleanupState

internal data class ModelManagerRow(
    val modelId: String,
    val displayName: String,
    val sizeBytes: Long,
    val selected: Boolean,
    val healthStatus: ModelHealthStatus,
    val healthCheckInProgress: Boolean,
    val healthCheckEnabled: Boolean,
    val selectionEnabled: Boolean,
    val renameEnabled: Boolean,
    val deletionEnabled: Boolean,
)

internal enum class ModelCatalogAvailability {
    LOADING,
    UNAVAILABLE,
    READY,
}

internal data class ModelManagerViewState(
    val rows: List<ModelManagerRow>,
    val totalSizeBytes: Long,
    val availability: ModelCatalogAvailability,
    val catalogMutationBusy: Boolean,
    val cleanupEnabled: Boolean,
    val cleanupInProgress: Boolean,
)

internal object ModelManagerPresentation {
    fun <T> visibleModel(state: ModelImportState<T>): T? = when (state) {
        ModelImportState.Preparing,
        ModelImportState.Unavailable,
        -> null
        is ModelImportState.Ready -> state.current
        is ModelImportState.Running -> state.previous
        is ModelImportState.Cancelling -> state.previous
        is ModelImportState.Succeeded -> state.model
        is ModelImportState.Cancelled -> state.current
        is ModelImportState.Failed -> state.current
    }

    fun managerView(state: ModelManagerState): ModelManagerViewState {
        val snapshot = state.snapshot
        val importInFlight = state.importState === ModelImportState.Preparing ||
            state.importState is ModelImportState.Running ||
            state.importState is ModelImportState.Cancelling
        val importAllowsMutation = when (val importState = state.importState) {
            ModelImportState.Unavailable -> false
            is ModelImportState.Failed -> importState.retryAllowed
            else -> true
        }
        val selectionBusy = state.selection is ModelSelectionState.Selecting
        val deletionBusy = state.deletion is ModelDeletionState.Deleting
        val renameBusy = state.rename is ModelRenameState.Renaming
        val cleanupBusy = state.storageCleanup is ModelStorageCleanupState.Cleaning
        val healthCheckBusy = state.healthCheck is ModelHealthCheckState.Checking
        val catalogMutationBusy = selectionBusy || deletionBusy || renameBusy || cleanupBusy || healthCheckBusy
        val mutationEnabled = snapshot != null && importAllowsMutation && !catalogMutationBusy && !importInFlight
        val selectedModelId = (state.selection as? ModelSelectionState.Selecting)?.modelId
            ?: snapshot?.selectedModelId
        val checkedModelId = (state.healthCheck as? ModelHealthCheckState.Checking)?.modelId
        return ModelManagerViewState(
            rows = snapshot?.models.orEmpty().map { model ->
                ModelManagerRow(
                    modelId = model.modelId,
                    displayName = model.displayName,
                    sizeBytes = model.sizeBytes,
                    selected = model.modelId == selectedModelId,
                    healthStatus = model.healthStatus,
                    healthCheckInProgress = model.modelId == checkedModelId,
                    healthCheckEnabled = mutationEnabled,
                    selectionEnabled = mutationEnabled,
                    renameEnabled = mutationEnabled,
                    deletionEnabled = mutationEnabled,
                )
            },
            totalSizeBytes = snapshot?.totalSizeBytes ?: 0L,
            availability = when {
                snapshot != null -> ModelCatalogAvailability.READY
                state.importState === ModelImportState.Preparing -> ModelCatalogAvailability.LOADING
                else -> ModelCatalogAvailability.UNAVAILABLE
            },
            catalogMutationBusy = catalogMutationBusy,
            cleanupEnabled = mutationEnabled,
            cleanupInProgress = cleanupBusy,
        )
    }
}
