package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import java.util.Collections

/** Non-secret preset metadata. Model IDs deliberately remain user profile data. */
internal data class OnlineAiProviderTemplate(
    val provider: OnlineAiProvider,
    val displayName: String,
    val defaultBaseUrl: String?,
    val structuredJson: Boolean,
) {
    init {
        require(displayName.isNotBlank())
        require(
            (provider == OnlineAiProvider.OPENAI_COMPATIBLE) == (defaultBaseUrl == null),
        ) { "Only the custom OpenAI-compatible template may omit a base URL" }
        defaultBaseUrl?.let { baseUrl ->
            require(OnlineAiProfileUrls.normalize(baseUrl) == baseUrl) {
                "Online AI provider template base URL must be normalized"
            }
        }
    }

    fun createProfile(
        displayName: String,
        modelId: String,
        baseUrl: String? = null,
    ): OnlineAiProfile = OnlineAiProfile.create(
        displayName = displayName,
        provider = provider,
        baseUrl = baseUrl ?: requireNotNull(defaultBaseUrl) {
            "A custom OpenAI-compatible profile requires a base URL"
        },
        modelId = modelId,
    )
}

/** Built-in providers kept in the same stable order as the AutoJs6 host catalog. */
internal object OnlineAiProviderCatalog {
    val templates: List<OnlineAiProviderTemplate> = Collections.unmodifiableList(
        listOf(
            OnlineAiProviderTemplate(
                provider = OnlineAiProvider.OPENAI,
                displayName = "OpenAI",
                defaultBaseUrl = "https://api.openai.com/v1",
                structuredJson = true,
            ),
            OnlineAiProviderTemplate(
                provider = OnlineAiProvider.ANTHROPIC,
                displayName = "Anthropic",
                defaultBaseUrl = "https://api.anthropic.com/v1",
                structuredJson = true,
            ),
            OnlineAiProviderTemplate(
                provider = OnlineAiProvider.GEMINI,
                displayName = "Gemini",
                defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
                structuredJson = true,
            ),
            OnlineAiProviderTemplate(
                provider = OnlineAiProvider.DEEPSEEK,
                displayName = "DeepSeek",
                defaultBaseUrl = "https://api.deepseek.com",
                structuredJson = false,
            ),
            OnlineAiProviderTemplate(
                provider = OnlineAiProvider.OPENROUTER,
                displayName = "OpenRouter",
                defaultBaseUrl = "https://openrouter.ai/api/v1",
                structuredJson = true,
            ),
            OnlineAiProviderTemplate(
                provider = OnlineAiProvider.OPENAI_COMPATIBLE,
                displayName = "OpenAI Compatible",
                defaultBaseUrl = null,
                structuredJson = true,
            ),
        ),
    )

    init {
        require(templates.map { template -> template.provider }.toSet() == OnlineAiProvider.entries.toSet())
        require(templates.map { template -> template.provider.providerId }.distinct().size == templates.size)
    }

    fun templateFor(provider: OnlineAiProvider): OnlineAiProviderTemplate =
        templates.single { template -> template.provider == provider }

    fun fromProviderId(providerId: String): OnlineAiProviderTemplate =
        templateFor(OnlineAiProvider.fromProviderId(providerId))
}
