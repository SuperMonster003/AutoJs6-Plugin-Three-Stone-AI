package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import java.util.Collections

/**
 * Convenience values only. Providers may add, rename, gate, or remove model IDs independently;
 * users can always enter an exact custom ID alongside these presets.
 * Reviewed 2026-09-26; sources and protocol limits: docs/dev/online-model-presets.md.
 */
internal object OnlineAiModelPresetCatalog {
    private val openAi = listOf(
        // The first preset is selected for new profiles. Keep the Chat Completions default;
        // GPT-6 tool calling needs protocol/parameter support beyond this catalog refresh.
        "gpt-5.6-sol",
        "gpt-6-astra",
        "gpt-6-sol",
        "gpt-6-luna",
        "gpt-5.6-terra",
        "gpt-5.6-luna",
        "gpt-5.5",
        "gpt-5.4",
        "gpt-5.4-mini",
        "gpt-5.4-nano",
        "gpt-4.1",
    )

    private val anthropic = listOf(
        "claude-opus-5-5",
        "claude-fable-5-1",
        "claude-sonnet-5",
        "claude-haiku-4-5",
        "claude-opus-5",
        "claude-fable-5",
        "claude-opus-4-8",
        "claude-sonnet-4-6",
    )

    private val gemini = listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-pro-preview",
        "gemini-3-flash-preview",
    )

    private val deepSeek = listOf(
        "deepseek-flash",
        "deepseek-v4-pro",
    )

    private val openRouter = listOf(
        "openai/gpt-5.6-sol",
        "openai/gpt-6-astra",
        "openai/gpt-6-sol",
        "openai/gpt-6-luna",
        // OpenRouter versions use dots; native Anthropic model IDs use hyphens.
        "anthropic/claude-opus-5.5",
        "anthropic/claude-fable-5.1",
        "anthropic/claude-sonnet-5",
        "anthropic/claude-haiku-4.5",
        "anthropic/claude-opus-5",
        "google/gemini-3.8-flash",
        "google/gemini-3.5-flash-lite",
        "google/gemini-3.1-pro-preview",
        "deepseek/deepseek-v4.1-flash",
        // The unsuffixed OpenRouter V4 Pro slug still identifies the older 0423 release.
        "deepseek/deepseek-v4-pro-0813",
    )

    fun forProvider(provider: OnlineAiProvider): List<String> = Collections.unmodifiableList(
        when (provider) {
            OnlineAiProvider.OPENAI -> openAi
            OnlineAiProvider.ANTHROPIC -> anthropic
            OnlineAiProvider.GEMINI -> gemini
            OnlineAiProvider.DEEPSEEK -> deepSeek
            OnlineAiProvider.OPENROUTER -> openRouter
            OnlineAiProvider.OPENAI_COMPATIBLE ->
                openAi + anthropic + gemini + deepSeek
        }.distinct(),
    )
}
