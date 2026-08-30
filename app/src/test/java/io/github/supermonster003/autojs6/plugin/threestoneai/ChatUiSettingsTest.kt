package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatUiSettingsTest {
    @Test
    fun `generation defaults are unlimited and leave sampling to model`() {
        val settings = ChatUiSettings()

        assertNull(settings.maximumOutputTokens)
        assertNull(settings.samplingOptions())
        assertEquals(EnterKeyBehavior.SEND, settings.enterKeyBehavior)
        assertEquals(ContextPolicy.DEFAULT_INPUT_TOKEN_BUDGET, settings.contextTokenBudget)
    }

    @Test
    fun `custom generation controls map exactly to backend options`() {
        val settings = ChatUiSettings(
            maximumOutputTokens = 4_096,
            maximumOutputTokensDraft = 4_096,
            contextTokenBudget = 32_768,
            useModelSamplingDefaults = false,
            temperature = 0.65,
            topK = 24,
            topP = 0.88,
        )

        assertEquals(4_096, settings.maximumOutputTokens)
        assertEquals(32_768, settings.contextTokenBudget)
        val sampling = requireNotNull(settings.samplingOptions())
        assertEquals(0.65, sampling.temperature, 0.0)
        assertEquals(24, sampling.topK)
        assertEquals(0.88, sampling.topP, 0.0)
    }

    @Test
    fun `context token budget must remain positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatUiSettings(contextTokenBudget = 0)
        }
    }
}
