package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiModelCatalogGroupsTest {
    @Test
    fun openRouterUsesExactOfficialNamespacesAndFixedBrandOrder() {
        val expected = listOf(
            "OpenAI / GPT" to "openai/gpt-6-sol",
            "Anthropic / Claude" to "anthropic/claude-fable-5.1",
            "Google / Gemini" to "google/gemini-3.8-flash",
            "DeepSeek" to "deepseek/deepseek-v4.1-flash",
            "xAI / Grok" to "x-ai/grok-4.7",
            "Meta" to "meta-llama/llama-4-maverick",
            "Mistral AI" to "mistralai/mistral-medium-3-5",
            "Alibaba / Qwen" to "qwen/qwen3.8-max-prime",
            "Moonshot AI / Kimi" to "moonshotai/kimi-k3",
            "Z.ai / GLM" to "z-ai/glm-5.3-prime",
            "MiniMax" to "minimax/minimax-m3",
            "ByteDance / Seed" to "bytedance-seed/seed-2-1-turbo",
            "Tencent / Hunyuan" to "tencent/hy4-preview",
            "Baidu / ERNIE" to "baidu/ernie-4.5-vl-424b-a47b",
            "Cohere / Command" to "cohere/command-a-plus",
            "Amazon / Nova" to "amazon/nova-2-lite-v1",
            "Microsoft / Phi" to "microsoft/phi-4",
            "NVIDIA / Nemotron" to "nvidia/nemotron-3.5-lightning",
            "AI21 Labs / Jamba" to "ai21/jamba-large-1.7",
            "IBM / Granite" to "ibm-granite/granite-4.2-8b",
            "Perplexity / Sonar" to "perplexity/sonar-pro-search",
        )

        assertEquals(
            expected.map { (title, model) -> OnlineAiModelPresetGroup(title, listOf(model)) },
            OnlineAiModelCatalogGroups.group(
                OnlineAiProvider.OPENROUTER,
                expected.reversed().map { it.second },
                "Other models",
            ),
        )
    }

    @Test
    fun keepsUnknownNamespacesAndExactIdsWithoutInferringTheirBrand() {
        val models = listOf(
            "new-vendor/custom-model:free",
            "meta/muse-spark-1.3",
            "bytedance/ui-tars-1.5-7b",
            "openai-community/gpt-custom",
            "~openai/gpt-custom",
            "no-namespace",
            "new-vendor/second/model",
            "openai/gpt-6-sol",
        )

        assertEquals(
            listOf(
                OnlineAiModelPresetGroup("OpenAI / GPT", listOf("openai/gpt-6-sol")),
                OnlineAiModelPresetGroup("Meta", listOf("meta/muse-spark-1.3")),
                OnlineAiModelPresetGroup("new-vendor", listOf("new-vendor/custom-model:free", "new-vendor/second/model")),
                OnlineAiModelPresetGroup("bytedance", listOf("bytedance/ui-tars-1.5-7b")),
                OnlineAiModelPresetGroup("openai-community", listOf("openai-community/gpt-custom")),
                OnlineAiModelPresetGroup("~openai", listOf("~openai/gpt-custom")),
                OnlineAiModelPresetGroup("Autres modèles", listOf("no-namespace")),
            ),
            OnlineAiModelCatalogGroups.group(OnlineAiProvider.OPENROUTER, models, "Autres modèles"),
        )
    }

    @Test
    fun metaNamespacesShareOneVendorGroupWithoutChangingRoutingIdsOrOrder() {
        val ids = listOf(
            "meta/muse-spark-1.3",
            "meta-llama/llama-4-maverick",
            "meta/muse-spark-1.2",
            "meta-llama/llama-4-scout:free",
        )
        assertEquals(
            listOf(OnlineAiModelPresetGroup("Meta", ids)),
            OnlineAiModelCatalogGroups.group(OnlineAiProvider.OPENROUTER, ids, "Other"),
        )
    }

    @Test
    fun compatibleCatalogUsesNativePrefixesAndLocalizedFallbackWithoutStrippingNamespaces() {
        val models = listOf(
            "deepseek-flash", "other-model", "gemini-3.8-flash", "claude-fable-5-1",
            "o4-mini", "gpt-6-sol", "o1", "o10-mini", "other/gpt-6-sol", "openrouter/auto",
            "ocean-model", "o4mini", "gpt-custom", "Claude-custom", "gpt-6-sol",
        )

        assertEquals(
            listOf(
                OnlineAiModelPresetGroup("OpenAI / GPT", listOf("o4-mini", "gpt-6-sol", "o1", "o10-mini", "gpt-custom")),
                OnlineAiModelPresetGroup("Anthropic / Claude", listOf("claude-fable-5-1")),
                OnlineAiModelPresetGroup("Google / Gemini", listOf("gemini-3.8-flash")),
                OnlineAiModelPresetGroup("DeepSeek", listOf("deepseek-flash")),
                OnlineAiModelPresetGroup("Autres modèles", listOf(
                    "other-model", "other/gpt-6-sol", "openrouter/auto", "ocean-model", "o4mini", "Claude-custom",
                )),
            ),
            OnlineAiModelCatalogGroups.group(OnlineAiProvider.OPENAI_COMPATIBLE, models, "Autres modèles"),
        )
    }

    @Test
    fun directProvidersKeepTheirOrderAndDoNotReclassifyUnrecognizedModelNames() {
        val titles = mapOf(
            OnlineAiProvider.OPENAI to "OpenAI / GPT",
            OnlineAiProvider.ANTHROPIC to "Anthropic / Claude",
            OnlineAiProvider.GEMINI to "Google / Gemini",
            OnlineAiProvider.DEEPSEEK to "DeepSeek",
        )
        titles.forEach { (provider, title) ->
            assertEquals(
                listOf(OnlineAiModelPresetGroup(title, listOf("second", "first", "custom/model:variant"))),
                OnlineAiModelCatalogGroups.group(provider, listOf("second", "first", "second", "custom/model:variant"), "Other"),
            )
        }
    }

    @Test
    fun deduplicatesInFirstSeenOrderWithoutMutatingOrRetainingTheInput() {
        val input = mutableListOf("qwen/second:free", "openai/first", "qwen/first", "qwen/second:free", "openai/first")
        val original = input.toList()
        val groups = OnlineAiModelCatalogGroups.group(OnlineAiProvider.OPENROUTER, input, "Other")

        assertEquals(original, input)
        assertEquals(listOf("qwen/second:free", "qwen/first"), groups[1].modelIds)
        input.clear()
        assertEquals(listOf("openai/first", "qwen/second:free", "qwen/first"), groups.flatMap { it.modelIds })
    }

    @Test
    fun emptyCatalogHasNoEmptyHeadingsForAnyProvider() {
        OnlineAiProvider.entries.forEach { provider ->
            assertTrue(OnlineAiModelCatalogGroups.group(provider, emptyList(), "Other").isEmpty())
        }
    }
}
