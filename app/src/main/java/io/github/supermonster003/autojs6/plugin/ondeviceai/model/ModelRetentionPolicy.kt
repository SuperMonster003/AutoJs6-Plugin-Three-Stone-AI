package io.github.supermonster003.autojs6.plugin.ondeviceai.model

/**
 * Model generations are immutable and retained because a provider in another process can have
 * selected an older generation immediately before the current metadata pointer is replaced.
 */
internal object ModelRetentionPolicy {
    const val DELETE_SUPERSEDED_GENERATIONS_DURING_IMPORT = false
}
