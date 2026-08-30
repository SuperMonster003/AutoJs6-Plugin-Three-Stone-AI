package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import java.util.Collections

/**
 * Convenience values only. Providers may add, rename, gate, or remove model IDs independently;
 * users can always enter an exact custom ID alongside these presets.
 */
internal object OnlineAiModelPresetCatalog {
    private val openAi = listOf(
        "gpt-5.6-sol",
        "gpt-5.6",
        "gpt-5.4",
        "gpt-4.1",
        "o4-mini",
    )

    private val anthropic = listOf(
        "claude-opus-4-8",
        "claude-opus-5",
        "claude-fable-5",
        "claude-sonnet-4-6",
        "claude-haiku-4-5",
    )

    private val gemini = listOf(
        "gemini-3.1-pro-preview",
        "gemini-3-flash-preview",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
    )

    private val deepSeek = listOf(
        "deepseek-chat",
        "deepseek-reasoner",
    )

    private val openRouter = listOf(
        "openai/gpt-5.6-sol",
        "anthropic/claude-opus-5",
        "google/gemini-3.1-pro-preview",
        "deepseek/deepseek-chat",
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
