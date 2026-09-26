package io.github.supermonster003.autojs6.plugin.threestoneai.profile

internal data class OnlineAiModelPresetGroup(
    val title: String,
    val modelIds: List<String>,
)

/** Display groups only: model IDs, saved profiles, and catalog defaults are never rewritten. */
internal object OnlineAiModelCatalogGroups {
    // OpenRouter namespaces verified against its public Models API on 2026-09-26.
    // AI21's historical namespace is retained for catalogs that still include Jamba.
    private val titles = linkedMapOf(
        "openai" to "OpenAI / GPT",
        "anthropic" to "Anthropic / Claude",
        "google" to "Google / Gemini",
        "deepseek" to "DeepSeek",
        "x-ai" to "xAI / Grok",
        "meta" to "Meta",
        "mistralai" to "Mistral AI",
        "qwen" to "Alibaba / Qwen",
        "moonshotai" to "Moonshot AI / Kimi",
        "z-ai" to "Z.ai / GLM",
        "minimax" to "MiniMax",
        "bytedance-seed" to "ByteDance / Seed",
        "tencent" to "Tencent / Hunyuan",
        "baidu" to "Baidu / ERNIE",
        "cohere" to "Cohere / Command",
        "amazon" to "Amazon / Nova",
        "microsoft" to "Microsoft / Phi",
        "nvidia" to "NVIDIA / Nemotron",
        "ai21" to "AI21 Labs / Jamba",
        "ibm-granite" to "IBM / Granite",
        "perplexity" to "Perplexity / Sonar",
    )
    private val openAiReasoningPrefix = Regex("^o[0-9]+(?:-|$)")

    /** Known brands have a fixed order; unknown namespaces retain their first-seen order. */
    fun group(
        provider: OnlineAiProvider,
        modelIds: List<String>,
        otherTitle: String,
    ): List<OnlineAiModelPresetGroup> {
        val groups = linkedMapOf<String?, MutableList<String>>()
        modelIds.distinct().forEach { modelId ->
            val namespace = when (provider) {
                OnlineAiProvider.OPENAI -> "openai"
                OnlineAiProvider.ANTHROPIC -> "anthropic"
                OnlineAiProvider.GEMINI -> "google"
                OnlineAiProvider.DEEPSEEK -> "deepseek"
                OnlineAiProvider.OPENROUTER -> routerNamespace(modelId)
                OnlineAiProvider.OPENAI_COMPATIBLE -> nativeNamespace(modelId)
            }
            groups.getOrPut(namespace) { mutableListOf() }.add(modelId)
        }
        return buildList {
            titles.forEach { (namespace, title) ->
                groups[namespace]?.let { add(OnlineAiModelPresetGroup(title, it.toList())) }
            }
            groups.forEach { (namespace, models) ->
                if (namespace != null && namespace !in titles) {
                    add(OnlineAiModelPresetGroup(namespace, models.toList()))
                }
            }
            groups[null]?.let { add(OnlineAiModelPresetGroup(otherTitle, it.toList())) }
        }
    }

    private fun routerNamespace(modelId: String): String? {
        val namespace = modelId.substringBefore('/').takeIf { '/' in modelId && it.isNotEmpty() }
        // Muse and Llama belong to one vendor, but retain their original routing IDs.
        return if (namespace == "meta-llama") "meta" else namespace
    }

    private fun nativeNamespace(modelId: String): String? {
        if ('/' in modelId) return null
        return when {
            modelId.startsWith("gpt-") || openAiReasoningPrefix.containsMatchIn(modelId) -> "openai"
            modelId.startsWith("claude-") -> "anthropic"
            modelId.startsWith("gemini-") -> "google"
            modelId.startsWith("deepseek-") -> "deepseek"
            else -> null
        }
    }
}
