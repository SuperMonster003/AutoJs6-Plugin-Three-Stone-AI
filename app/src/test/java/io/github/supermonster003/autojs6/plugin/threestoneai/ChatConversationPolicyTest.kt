package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatConversationPolicyTest {
    @Test
    fun `cumulative input is calculated through the selected message`() {
        val messages = listOf(
            user(1, "one"),
            assistant(2, "first", usage = ChatMessageUsage(10L, 2L, 3L)),
            user(3, "two"),
            assistant(4, "second", usage = ChatMessageUsage(25L, 4L, 5L)),
            user(5, "three"),
            assistant(6, "third", usage = ChatMessageUsage(40L, 6L, 7L)),
        )

        assertEquals(10L, ChatConversationPolicy.cumulativeInputTokensThrough(messages, 2L))
        assertEquals(35L, ChatConversationPolicy.cumulativeInputTokensThrough(messages, 4L))
        assertEquals(75L, ChatConversationPolicy.cumulativeInputTokensThrough(messages, 6L))
        assertEquals(0L, ChatConversationPolicy.cumulativeInputTokensThrough(messages, 99L))
    }

    @Test
    fun `only assistant messages carry an actual target snapshot`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessage(1, ChatMessageRole.ASSISTANT, "missing target")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessage(2, ChatMessageRole.USER, "unexpected target", target = responseTarget)
        }
    }

    @Test
    fun `restoring changes only in-flight assistant messages to stopped`() {
        val completeUsage = ChatMessageUsage(4, 5, 6)
        val messages = listOf(
            user(1, "question"),
            assistant(2, "answer", usage = completeUsage),
            assistant(3, "partial", ChatMessageStatus.GENERATING),
            assistant(4, "failed", ChatMessageStatus.FAILED),
        )

        val restored = ChatConversationPolicy.restore(messages)

        assertEquals(ChatMessageStatus.COMPLETE, restored[0].status)
        assertEquals(ChatMessageStatus.COMPLETE, restored[1].status)
        assertEquals(completeUsage, restored[1].usage)
        assertEquals(ChatMessageStatus.STOPPED, restored[2].status)
        assertNull(restored[2].usage)
        assertEquals(ChatMessageStatus.FAILED, restored[3].status)
    }

    private fun user(id: Int, text: String) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.USER,
        text = text,
    )

    private fun assistant(
        id: Int,
        text: String,
        status: ChatMessageStatus = ChatMessageStatus.COMPLETE,
        usage: ChatMessageUsage? = null,
    ) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.ASSISTANT,
        text = text,
        status = status,
        usage = usage,
        target = responseTarget,
    )

    private companion object {
        val responseTarget = ConversationTargetSnapshot(
            targetId = "local:test-model",
            providerId = "autojs6.three-stone-ai",
            modelId = "test-model",
            displayName = "Test model",
            locality = AiTargetLocality.LOCAL,
        )
    }
}
