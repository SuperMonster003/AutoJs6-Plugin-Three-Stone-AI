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
        assertEquals(ChatBubbleStyle.BACKGROUND, settings.userBubbleStyle)
        assertEquals(ChatBubbleStyle.NONE, settings.assistantBubbleStyle)
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
    fun `speech defaults read only on request and prefer macaw`() {
        val settings = ChatUiSettings()

        // Roadmap D10 and D12: automatic reading is off, 3-Macaw Speech is preferred.
        assertEquals(false, settings.autoReadReplies)
        assertEquals(true, settings.preferMacawSpeech)
        assertEquals(
            ChatUiSettings(autoReadReplies = true, preferMacawSpeech = false),
            settings.copy(autoReadReplies = true, preferMacawSpeech = false),
        )
    }

    @Test
    fun `reasoning toggles default on and drive the thinking card (P3)`() {
        val defaults = ChatUiSettings()
        assertEquals(true, defaults.showReasoningWhileGenerating)
        assertEquals(true, defaults.collapseReasoningWhenDone)

        val generating = ChatMessageStatus.GENERATING
        assertEquals(true, ChatReasoningCardPolicy.expanded(generating, defaults, null))
        assertEquals(false, ChatReasoningCardPolicy.expanded(ChatMessageStatus.COMPLETE, defaults, null))
        assertEquals(false, ChatReasoningCardPolicy.expanded(ChatMessageStatus.STOPPED, defaults, null))
        assertEquals(false, ChatReasoningCardPolicy.expanded(generating, defaults, false))
        assertEquals(true, ChatReasoningCardPolicy.expanded(ChatMessageStatus.COMPLETE, defaults, true))

        val hidden = defaults.copy(showReasoningWhileGenerating = false, collapseReasoningWhenDone = false)
        assertEquals(false, ChatReasoningCardPolicy.expanded(generating, hidden, null))
        assertEquals(true, ChatReasoningCardPolicy.expanded(ChatMessageStatus.FAILED, hidden, null))

        assertEquals(1L, ChatReasoningCardPolicy.seconds(0L))
        assertEquals(1L, ChatReasoningCardPolicy.seconds(1_499L))
        assertEquals(2L, ChatReasoningCardPolicy.seconds(1_500L))
        assertEquals(61L, ChatReasoningCardPolicy.seconds(61_000L))
    }

    @Test
    fun `context token budget must remain positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatUiSettings(contextTokenBudget = 0)
        }
    }
}
