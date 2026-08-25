package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConversationPolicyTest {
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
    fun `fresh backend history contains only completed turn pairs`() {
        val messages = listOf(
            user(1, "first question"),
            assistant(2, "first answer"),
            notice(3, "model notice"),
            user(4, "failed question"),
            assistant(5, "partial", ChatMessageStatus.FAILED),
            user(6, "current question"),
            assistant(7, "streaming", ChatMessageStatus.GENERATING),
            user(8, "last completed question"),
            assistant(9, "last completed answer"),
        )

        val history = ChatConversationPolicy.historyForFreshBackend(messages)

        assertEquals(
            listOf("first question", "first answer", "last completed question", "last completed answer"),
            history.map { message -> message.textParts.single() },
        )
        assertEquals(
            listOf(
                GenerationRole.USER,
                GenerationRole.ASSISTANT,
                GenerationRole.USER,
                GenerationRole.ASSISTANT,
            ),
            history.map { message -> message.role },
        )
    }

    @Test
    fun `fresh backend history retains a bounded recent suffix`() {
        val messages = buildList {
            repeat(ChatConversationPolicy.MAXIMUM_RETAINED_TURNS + 6) { turn ->
                add(user(turn * 2 + 1, "question-$turn"))
                add(assistant(turn * 2 + 2, "answer-$turn"))
            }
        }

        val history = ChatConversationPolicy.historyForFreshBackend(messages)

        assertEquals(ChatConversationPolicy.MAXIMUM_RETAINED_TURNS * 2, history.size)
        assertEquals("question-6", history.first().textParts.single())
        assertEquals("answer-29", history.last().textParts.single())
    }

    @Test
    fun `oversized newest turn prevents a non-contiguous older history`() {
        val oversized = "x".repeat(ChatConversationPolicy.MAXIMUM_RETAINED_HISTORY_BYTES + 1)
        val history = ChatConversationPolicy.historyForFreshBackend(
            listOf(
                user(1, "older"),
                assistant(2, "answer"),
                user(3, "newest"),
                assistant(4, oversized),
            ),
        )

        assertTrue(history.isEmpty())
    }

    @Test
    fun `a completed empty response is not replayed as model context`() {
        val history = ChatConversationPolicy.historyForFreshBackend(
            listOf(
                user(1, "question"),
                assistant(2, ""),
            ),
        )

        assertTrue(history.isEmpty())
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

    @Test
    fun `backend rotation starts at the configured turn boundary`() {
        assertFalse(
            ChatConversationPolicy.shouldRotateBackend(
                ChatConversationPolicy.MAXIMUM_BACKEND_TURNS - 1,
            ),
        )
        assertTrue(
            ChatConversationPolicy.shouldRotateBackend(
                ChatConversationPolicy.MAXIMUM_BACKEND_TURNS,
            ),
        )
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

    private fun notice(id: Int, text: String) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.NOTICE,
        text = text,
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
