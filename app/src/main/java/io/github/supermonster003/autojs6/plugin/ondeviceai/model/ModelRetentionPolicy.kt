package io.github.supermonster003.autojs6.plugin.ondeviceai.model

/**
 * Model generations are immutable and never deleted automatically during import because a provider
 * in another process can have selected an older generation immediately before catalog publication.
 * Explicit manager cleanup may later unlink only generations no longer referenced by the catalog.
 */
internal object ModelRetentionPolicy {
    const val DELETE_SUPERSEDED_GENERATIONS_DURING_IMPORT = false
}
