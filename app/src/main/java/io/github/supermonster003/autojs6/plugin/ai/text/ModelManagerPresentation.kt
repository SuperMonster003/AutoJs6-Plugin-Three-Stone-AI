package io.github.supermonster003.autojs6.plugin.ai.text

import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportState

internal object ModelManagerPresentation {
    fun <T> visibleModel(state: ModelImportState<T>): T? = when (state) {
        ModelImportState.Preparing,
        ModelImportState.Unavailable,
        -> null
        is ModelImportState.Ready -> state.current
        is ModelImportState.Running -> state.previous
        is ModelImportState.Succeeded -> state.model
        is ModelImportState.Failed -> state.current
    }
}
