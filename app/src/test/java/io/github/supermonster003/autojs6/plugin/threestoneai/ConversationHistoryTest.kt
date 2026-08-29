package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryTest {
    @Test
    fun `codec round trips messages usage and target snapshot`() {
        val original = StoredConversation(
            id = "conversation-1",
            title = "First question",
            createdAtMillis = 100L,
            updatedAtMillis = 200L,
            target = ConversationTargetSnapshot(
                targetId = "profile:00000000-0000-0000-0000-000000000001",
                providerId = "openai-compatible",
                modelId = "provider-model-id",
                displayName = "PoloAPI",
                locality = AiTargetLocality.REMOTE,
            ),
            messages = listOf(
                ChatMessage(1, ChatMessageRole.USER, "First question"),
                ChatMessage(
                    2,
                    ChatMessageRole.ASSISTANT,
                    "**Answer**",
                    usage = ChatMessageUsage(12, 34, 567),
                    target = assistantTarget,
                ),
            ),
        )

        val restored = ConversationHistoryCodec.decode(ConversationHistoryCodec.encode(listOf(original)))

        assertEquals(listOf(original), restored)
        assertEquals(AiTargetLocality.REMOTE, restored.single().target?.locality)
        assertEquals(assistantTarget, restored.single().messages.last().target)
    }

    @Test
    fun `codec rejects every unpublished older version instead of retaining compatibility`() {
        listOf(1, 2).forEach { version ->
            val encoded = ConversationHistoryCodec.encode(emptyList())
            ByteBuffer.wrap(encoded).putInt(Int.SIZE_BYTES, version)

            assertThrows(IllegalArgumentException::class.java) {
                ConversationHistoryCodec.decode(encoded)
            }
        }
    }

    @Test
    fun `history is ordered newest first and deduplicated`() {
        val olderVersion = conversation("same", updatedAt = 20)
        val newerVersion = conversation("same", updatedAt = 30)
        val another = conversation("another", updatedAt = 25)

        val normalized = ConversationHistoryPolicy.normalized(
            listOf(olderVersion, another, newerVersion),
        )

        assertEquals(listOf(newerVersion, another), normalized)
    }

    @Test
    fun `history has no conversation count limit within its storage budget`() {
        val conversations = (1..75).map { index ->
            conversation("conversation-$index", updatedAt = 100L + index)
        }

        val normalized = ConversationHistoryPolicy.normalized(conversations)

        assertEquals(75, normalized.size)
        assertEquals("conversation-75", normalized.first().id)
        assertEquals("conversation-1", normalized.last().id)
    }

    @Test
    fun `oversized conversations retain a recent bounded suffix instead of disappearing`() {
        val messages = (1..ConversationHistoryPolicy.MAXIMUM_MESSAGES_PER_CONVERSATION + 2).map { id ->
            ChatMessage(id.toLong(), ChatMessageRole.USER, "message-$id")
        }
        val normalized = ConversationHistoryPolicy.normalized(
            listOf(conversation("large", updatedAt = 20).copy(messages = messages)),
        ).single()

        assertEquals(ConversationHistoryPolicy.MAXIMUM_MESSAGES_PER_CONVERSATION, normalized.messages.size)
        assertEquals(3L, normalized.messages.first().id)
        assertEquals(messages.last(), normalized.messages.last())
    }

    @Test
    fun `title uses a compact bounded first user prompt`() {
        val prompt = "  A first\n\nquestion with   spacing  " + "x".repeat(100)
        val title = ConversationHistoryPolicy.titleFor(
            listOf(ChatMessage(1, ChatMessageRole.USER, prompt)),
            "fallback",
        )

        assertTrue(title.startsWith("A first question with spacing"))
        assertTrue(title.endsWith("..."))
        assertTrue(title.length <= 64)
    }

    @Test
    fun `search finds every visible markdown occurrence with rendered offsets`() {
        val messages = listOf(
            ChatMessage(1, ChatMessageRole.USER, "answer here"),
            assistant(2, "**Answer** and another answer"),
        )

        val matches = ConversationSearchPolicy.find(messages, "answer")

        assertEquals(3, matches.size)
        assertEquals(listOf(1L, 2L, 2L), matches.map(ConversationSearchMatch::messageId))
        val rendered = ConversationSearchPolicy.documentFor(messages[1]).text
        assertEquals("Answer", rendered.substring(matches[1].start, matches[1].end))
        assertEquals("answer", rendered.substring(matches[2].start, matches[2].end))
    }

    @Test
    fun `edit impact removes the selected user node and every later message`() {
        val messages = listOf(
            ChatMessage(1, ChatMessageRole.USER, "keep"),
            assistant(2, "keep answer"),
            ChatMessage(3, ChatMessageRole.USER, "edit"),
            assistant(4, "old branch"),
            ChatMessage(5, ChatMessageRole.USER, "later"),
        )

        val impact = ConversationEditPolicy.impact(messages, 3)

        assertEquals(MessageEditImpact(messageIndex = 2, laterMessageCount = 2), impact)
        assertEquals(messages.take(2), ConversationEditPolicy.prefixBefore(messages, 3))
        assertNull(ConversationEditPolicy.impact(messages, 2))
    }

    @Test
    fun `regeneration locates the paired user and reports later branch size`() {
        val messages = listOf(
            ChatMessage(1, ChatMessageRole.USER, "first"),
            assistant(2, "answer"),
            ChatMessage(3, ChatMessageRole.NOTICE, "model unchanged"),
            ChatMessage(4, ChatMessageRole.USER, "second"),
            ChatMessage(5, ChatMessageRole.NOTICE, "notice"),
            assistant(6, "second answer"),
            ChatMessage(7, ChatMessageRole.USER, "later"),
        )

        assertEquals(
            MessageRegenerationImpact(
                userMessageId = 4,
                laterMessageCount = 1,
                responseTarget = assistantTarget,
            ),
            ConversationRegenerationPolicy.impact(messages, 6),
        )
        assertNull(ConversationRegenerationPolicy.impact(messages, 4))
    }

    private fun conversation(id: String, updatedAt: Long) = StoredConversation(
        id = id,
        title = id,
        createdAtMillis = 10,
        updatedAtMillis = updatedAt,
        target = null,
        messages = emptyList(),
    )

    private fun assistant(id: Long, text: String) = ChatMessage(
        id = id,
        role = ChatMessageRole.ASSISTANT,
        text = text,
        target = assistantTarget,
    )

    private companion object {
        val assistantTarget = ConversationTargetSnapshot(
            targetId = "local:actual-response-model",
            providerId = "autojs6.three-stone-ai",
            modelId = "actual-response-model",
            displayName = "Actual response model",
            locality = AiTargetLocality.LOCAL,
        )
    }
}
