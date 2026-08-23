package io.github.supermonster003.autojs6.plugin.ondeviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatUiSettingsTest {
    @Test
    fun `generation defaults are unlimited and leave sampling to model`() {
        val settings = ChatUiSettings()

        assertNull(settings.maximumOutputTokens)
        assertNull(settings.samplingOptions())
        assertEquals(EnterKeyBehavior.SEND, settings.enterKeyBehavior)
    }

    @Test
    fun `custom generation controls map exactly to backend options`() {
        val settings = ChatUiSettings(
            maximumOutputTokens = 4_096,
            maximumOutputTokensDraft = 4_096,
            useModelSamplingDefaults = false,
            temperature = 0.65,
            topK = 24,
            topP = 0.88,
        )

        assertEquals(4_096, settings.maximumOutputTokens)
        val sampling = requireNotNull(settings.samplingOptions())
        assertEquals(0.65, sampling.temperature, 0.0)
        assertEquals(24, sampling.topK)
        assertEquals(0.88, sampling.topP, 0.0)
    }
}
