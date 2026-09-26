package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import java.util.Collections

/** Public catalog metadata never changes a saved profile or grants a model any capability. */
internal class OnlineAiModelCatalog internal constructor(
    val revision: Long,
    val updatedAtEpochMillis: Long,
    providers: Map<String, OnlineAiModelCatalogProvider>,
) {
    internal val providers: Map<String, OnlineAiModelCatalogProvider> = Collections.unmodifiableMap(
        providers.mapValues { (_, value) ->
            OnlineAiModelCatalogProvider(
                value.defaultModelId,
                Collections.unmodifiableList(ArrayList(value.models)),
            )
        },
    )

    fun forProvider(provider: OnlineAiProvider): List<String> = when (provider) {
        OnlineAiProvider.OPENAI_COMPATIBLE -> Collections.unmodifiableList(
            DIRECT_PROVIDER_IDS.flatMap { providers.getValue(it).models }.distinct(),
        )
        else -> providers.getValue(provider.providerId).models
    }

    fun defaultForProvider(provider: OnlineAiProvider): String = providers.getValue(
        if (provider == OnlineAiProvider.OPENAI_COMPATIBLE) "openai" else provider.providerId,
    ).defaultModelId

    internal companion object {
        val DIRECT_PROVIDER_IDS = listOf("openai", "anthropic", "gemini", "deepseek")
        val PROVIDER_IDS = DIRECT_PROVIDER_IDS + "openrouter"
    }
}

internal data class OnlineAiModelCatalogProvider(
    val defaultModelId: String,
    val models: List<String>,
)

internal data class OnlineAiModelCatalogSnapshot(
    val catalog: OnlineAiModelCatalog,
    val isCached: Boolean,
    val lastCheckedAtMillis: Long,
)

internal enum class OnlineAiModelCatalogRefreshResult {
    UPDATED,
    UNCHANGED,
    SKIPPED_FRESH,
    SKIPPED_BACKOFF,
}
