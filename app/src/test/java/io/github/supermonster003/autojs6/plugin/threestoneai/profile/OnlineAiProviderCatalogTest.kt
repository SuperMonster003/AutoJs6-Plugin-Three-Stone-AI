package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiProviderCatalogTest {
    @Test
    fun builtInTemplatesMatchTheHostCatalogWithoutPinningModels() {
        val templates = OnlineAiProviderCatalog.templates

        assertEquals(
            listOf("openai", "anthropic", "gemini", "deepseek", "openrouter", "openai-compatible"),
            templates.map { template -> template.provider.providerId },
        )
        assertEquals(
            listOf(
                "https://api.openai.com/v1",
                "https://api.anthropic.com/v1",
                "https://generativelanguage.googleapis.com/v1beta",
                "https://api.deepseek.com",
                "https://openrouter.ai/api/v1",
            ),
            templates.mapNotNull { template -> template.defaultBaseUrl },
        )
        assertNull(templates.last().defaultBaseUrl)
        assertTrue(templates.none { template -> template.displayName.isBlank() })
    }

    @Test
    fun templatesCreateNormalizedProfilesAndAllowExplicitEndpointOverrides() {
        val openAi = OnlineAiProviderCatalog.templateFor(OnlineAiProvider.OPENAI).createProfile(
            displayName = " Work ",
            modelId = " model-a ",
        )
        val proxied = OnlineAiProviderCatalog.templateFor(OnlineAiProvider.ANTHROPIC).createProfile(
            displayName = "Claude Proxy",
            modelId = "claude-a",
            baseUrl = "https://proxy.example.com/anthropic/",
        )

        assertEquals("Work", openAi.displayName)
        assertEquals("https://api.openai.com/v1", openAi.baseUrl)
        assertEquals("model-a", openAi.modelId)
        assertEquals("https://proxy.example.com/anthropic", proxied.baseUrl)
    }

    @Test
    fun customTemplateRequiresAnExplicitHttpsBaseUrl() {
        val custom = OnlineAiProviderCatalog.templateFor(OnlineAiProvider.OPENAI_COMPATIBLE)

        assertThrows(IllegalArgumentException::class.java) {
            custom.createProfile("Custom", "model-a")
        }
        assertThrows(IllegalArgumentException::class.java) {
            custom.createProfile("Custom", "model-a", "http://api.example.com/v1")
        }
    }

    @Test
    fun everyProviderHasExactlyOneProtocolAndTemplate() {
        OnlineAiProvider.entries.forEach { provider ->
            assertEquals(provider, OnlineAiProvider.fromProviderId(provider.providerId))
            assertEquals(provider, OnlineAiProviderCatalog.fromProviderId(provider.providerId).provider)
        }
        assertEquals(
            setOf(
                OnlineAiProtocol.OPENAI_COMPATIBLE,
                OnlineAiProtocol.ANTHROPIC_MESSAGES,
                OnlineAiProtocol.GEMINI_GENERATE_CONTENT,
            ),
            OnlineAiProvider.entries.map { provider -> provider.protocol }.toSet(),
        )
    }

    @Test
    fun everyProviderIdRoundTripsThroughTheStrictProfileDocument() {
        val profiles = OnlineAiProvider.entries.mapIndexed { index, provider ->
            val template = OnlineAiProviderCatalog.templateFor(provider)
            OnlineAiProfile(
                profileId = "00000000-0000-4000-8000-${(index + 1).toString().padStart(12, '0')}",
                displayName = template.displayName,
                provider = provider,
                baseUrl = template.defaultBaseUrl ?: "https://custom.example.com/v1",
                modelId = "model-${index + 1}",
            )
        }
        val document = OnlineAiProfileDocument(revision = 7L, profiles = profiles)

        val encoded = OnlineAiProfileCodec.encode(document)
        val decoded = OnlineAiProfileCodec.decode(encoded)
        val encodedText = encoded.toString(Charsets.UTF_8)

        assertEquals(document, decoded)
        OnlineAiProvider.entries.forEach { provider ->
            assertTrue(encodedText.contains("\"providerId\":\"${provider.providerId}\""))
        }
        assertFalse(encodedText.contains("credential", ignoreCase = true))
        assertFalse(encodedText.contains("apiKey", ignoreCase = true))
    }
}
