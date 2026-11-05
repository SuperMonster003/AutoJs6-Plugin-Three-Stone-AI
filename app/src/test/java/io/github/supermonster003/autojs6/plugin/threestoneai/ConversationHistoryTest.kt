package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryTest {
    @Test
    fun `codec round trips messages usage and target snapshot`() {
        val messages = listOf(
            ChatMessage(1, ChatMessageRole.USER, "First question"),
            ChatMessage(
                2,
                ChatMessageRole.ASSISTANT,
                "**Answer**",
                usage = ChatMessageUsage(12, 34, 567),
                target = assistantTarget,
                truncated = true,
            ),
        )
        val contextState = contextState(messages)
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
            messages = messages,
            contextState = contextState,
        )

        val restored = ConversationHistoryCodec.decode(ConversationHistoryCodec.encode(listOf(original)))

        assertEquals(listOf(original), restored)
        assertEquals(AiTargetLocality.REMOTE, restored.single().target?.locality)
        assertEquals(assistantTarget, restored.single().messages.last().target)
        assertTrue(restored.single().messages.last().truncated)
        assertEquals(contextState, restored.single().contextState)
    }

    @Test
    fun `codec rejects every unpublished older version instead of retaining compatibility`() {
        listOf(1, 2, 3).forEach { version ->
            val encoded = ConversationHistoryCodec.encode(emptyList())
            ByteBuffer.wrap(encoded).putInt(Int.SIZE_BYTES, version)

            assertThrows(IllegalArgumentException::class.java) {
                ConversationHistoryCodec.decode(encoded)
            }
        }
    }

    @Test
    fun `codec reads the published version 4 layout without the truncation flag`() {
        val restored = ConversationHistoryCodec.decode(publishedLayout(4)).single()

        assertEquals("conversation-1", restored.id)
        assertNull(restored.target)
        assertEquals(listOf("First question", "**Answer**"), restored.messages.map { message -> message.text })
        assertEquals(ChatMessageUsage(12, 34, 567), restored.messages.last().usage)
        assertEquals(assistantTarget, restored.messages.last().target)
        assertTrue(restored.messages.none { message -> message.truncated })
        assertEquals(ConversationContextState(), restored.contextState)
        assertNull(restored.effortOverride)
        assertNull(restored.fastOverride)
    }

    @Test
    fun `codec reads the published version 5 layout without property tables`() {
        val restored = ConversationHistoryCodec.decode(publishedLayout(5)).single()

        assertEquals(listOf(false, true), restored.messages.map { message -> message.truncated })
        assertEquals(assistantTarget, restored.messages.last().target)
        assertEquals(ConversationContextState(), restored.contextState)
        assertNull(restored.effortOverride)
        assertNull(restored.fastOverride)
    }

    @Test
    fun `codec round trips every conversation effort and fast override`() {
        val overrides = GenerationEffort.entries.map { effort -> effort to (effort.ordinal % 2 == 0) } +
            listOf<Pair<GenerationEffort?, Boolean?>>(null to null, GenerationEffort.HIGH to null, null to false)
        overrides.forEach { (effort, fast) ->
            val original = conversation("override", updatedAt = 10L).copy(effortOverride = effort, fastOverride = fast)

            val restored = ConversationHistoryCodec.decode(ConversationHistoryCodec.encode(listOf(original))).single()

            assertEquals(original, restored)
            assertEquals(effort, restored.effortOverride)
            assertEquals(fast, restored.fastOverride)
        }
    }

    @Test
    fun `codec skips unknown property keys and unparseable known values`() {
        val bytes = publishedLayout(
            version = 6,
            messageProperties = listOf("thinking" to ByteArray(64 * 1_024) { 7 }),
            conversationProperties = listOf(
                "effort" to "ultra".toByteArray(),
                "fast" to byteArrayOf(2),
                "future.key_2" to byteArrayOf(1, 2, 3),
            ),
        )

        val restored = ConversationHistoryCodec.decode(bytes).single()

        assertEquals(listOf("First question", "**Answer**"), restored.messages.map { message -> message.text })
        assertNull(restored.effortOverride)
        assertNull(restored.fastOverride)
    }

    @Test
    fun `codec round trips assistant reasoning text duration and cut mark (P3)`() {
        val longText = "\u601d".repeat(ChatMessageReasoning.MAXIMUM_BYTES / 3 + 10)
        listOf(
            ChatMessageReasoning("plan the answer", 4_200L),
            ChatMessageReasoning("still thinking", null),
            ChatMessageReasoning("cut earlier", 0L, truncated = true),
            ChatMessageReasoning.bounded(longText, 61_000L),
        ).forEach { reasoning ->
            val original = conversation("reasoning", updatedAt = 10L).copy(
                messages = listOf(
                    ChatMessage(1L, ChatMessageRole.USER, "question"),
                    assistant(2L, "answer").copy(reasoning = reasoning),
                ),
            )

            val restored = ConversationHistoryCodec.decode(ConversationHistoryCodec.encode(listOf(original))).single()

            assertEquals(original, restored)
            assertEquals(reasoning, restored.messages.last().reasoning)
            assertNull(restored.messages.first().reasoning)
        }
        val cut = ChatMessageReasoning.bounded(longText, 61_000L)
        assertTrue(cut.truncated)
        assertEquals(ChatMessageReasoning.MAXIMUM_BYTES / 3 * 3, cut.text.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `codec reads reasoning keys only on assistant replies and skips bad values`() {
        val millis = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(1_500L).array()
        val restored = ConversationHistoryCodec.decode(
            publishedLayout(
                version = 6,
                messageProperties = listOf(
                    "reasoning" to "weigh options".toByteArray(),
                    "reasoning.cut" to byteArrayOf(1),
                    "reasoning.ms" to millis,
                ),
            ),
        ).single()

        assertNull(restored.messages.first().reasoning)
        assertEquals(ChatMessageReasoning("weigh options", 1_500L, truncated = true), restored.messages.last().reasoning)

        val broken = ConversationHistoryCodec.decode(
            publishedLayout(
                version = 6,
                messageProperties = listOf("reasoning" to "x".toByteArray(), "reasoning.ms" to byteArrayOf(1, 2)),
            ),
        ).single()
        assertEquals(ChatMessageReasoning("x", null), broken.messages.last().reasoning)
        val orphan = ConversationHistoryCodec.decode(
            publishedLayout(version = 6, messageProperties = listOf("reasoning.ms" to millis)),
        ).single()
        assertNull(orphan.messages.last().reasoning)
    }

    @Test
    fun `codec reads known keys next to unknown ones`() {
        val bytes = publishedLayout(
            version = 6,
            conversationProperties = listOf(
                "effort" to "xhigh".toByteArray(),
                "fast" to byteArrayOf(1),
                "later" to byteArrayOf(),
            ),
        )

        val restored = ConversationHistoryCodec.decode(bytes).single()

        assertEquals(GenerationEffort.XHIGH, restored.effortOverride)
        assertEquals(true, restored.fastOverride)
    }

    @Test
    fun `codec rejects malformed property tables`() {
        listOf(
            listOf("fast" to byteArrayOf(1), "fast" to byteArrayOf(0)),
            listOf("Effort" to "high".toByteArray()),
            listOf("1key" to byteArrayOf()),
            listOf("k".repeat(33) to byteArrayOf()),
            listOf("big" to ByteArray(64 * 1_024 + 1)),
            List(65) { index -> "key$index" to byteArrayOf() },
        ).forEach { properties ->
            assertThrows(IllegalArgumentException::class.java) {
                ConversationHistoryCodec.decode(publishedLayout(version = 6, conversationProperties = properties))
            }
            assertThrows(IllegalArgumentException::class.java) {
                ConversationHistoryCodec.decode(publishedLayout(version = 6, messageProperties = properties))
            }
        }
    }

    /**
     * Byte for byte what each published format writes: a conversation without a target snapshot, one
     * user and one truncated assistant message with usage and target, and an empty context state.
     * Version 4 (1.4.x) has no truncation flag; version 6 adds raw property tables.
     */
    private fun publishedLayout(
        version: Int,
        messageProperties: List<Pair<String, ByteArray>> = emptyList(),
        conversationProperties: List<Pair<String, ByteArray>> = emptyList(),
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(0x33534143)
            output.writeInt(version)
            output.writeInt(1)
            output.writeLegacyText("conversation-1")
            output.writeLegacyText("First question")
            output.writeLong(100L)
            output.writeLong(200L)
            output.writeBoolean(false)
            output.writeInt(2)
            output.writeLong(1L)
            output.writeLegacyText("USER")
            output.writeLegacyText("COMPLETE")
            output.writeLegacyText("First question")
            output.writeBoolean(false)
            if (version >= 5) output.writeBoolean(false)
            output.writeBoolean(false)
            if (version >= 6) output.writeRawProperties(messageProperties)
            output.writeLong(2L)
            output.writeLegacyText("ASSISTANT")
            output.writeLegacyText("COMPLETE")
            output.writeLegacyText("**Answer**")
            output.writeBoolean(true)
            output.writeLong(12L)
            output.writeLong(34L)
            output.writeLong(567L)
            if (version >= 5) output.writeBoolean(true)
            output.writeBoolean(true)
            output.writeLegacyText(assistantTarget.targetId)
            output.writeLegacyText(assistantTarget.providerId)
            output.writeLegacyText(assistantTarget.modelId)
            output.writeLegacyText(assistantTarget.displayName)
            output.writeLegacyText(assistantTarget.locality.name)
            if (version >= 6) output.writeRawProperties(messageProperties)
            output.writeInt(1)
            output.writeBoolean(false)
            output.writeInt(0)
            output.writeInt(0)
            if (version >= 6) output.writeRawProperties(conversationProperties)
        }
    }.toByteArray()

    private fun DataOutputStream.writeRawProperties(properties: List<Pair<String, ByteArray>>) {
        writeInt(properties.size)
        properties.forEach { (key, value) ->
            writeLegacyText(key)
            writeInt(value.size)
            write(value)
        }
    }

    private fun DataOutputStream.writeLegacyText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
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
    fun `uncheckpointed oversized conversations retain source truth beyond the soft item cap`() {
        val messages = (1..ConversationHistoryPolicy.MAXIMUM_MESSAGES_PER_CONVERSATION + 2).map { id ->
            ChatMessage(id.toLong(), ChatMessageRole.USER, "message-$id")
        }
        val normalized = ConversationHistoryPolicy.normalized(
            listOf(conversation("large", updatedAt = 20).copy(messages = messages)),
        ).single()

        assertEquals(messages.size, normalized.messages.size)
        assertEquals(1L, normalized.messages.first().id)
        assertEquals(messages.last(), normalized.messages.last())
    }

    @Test
    fun `oversized conversations prune only a checkpoint-covered prefix`() {
        val messages = (1..ConversationHistoryPolicy.MAXIMUM_MESSAGES_PER_CONVERSATION + 2).map { id ->
            ChatMessage(id.toLong(), ChatMessageRole.USER, "message-$id")
        }
        val covered = ConversationContextState(
            coveredThroughMessageId = 2L,
            summarySegments = listOf(
                SummarySegment(
                    firstMessageId = 1L,
                    lastMessageId = 2L,
                    sourceMessageIds = listOf(1L, 2L),
                    sourceHash = ConversationContextPolicy.sourceHash(messages.take(2)),
                    summary = "The first two messages were checkpointed.",
                ),
            ),
        )

        val normalized = ConversationHistoryPolicy.normalized(
            listOf(
                conversation("large", updatedAt = 20).copy(
                    messages = messages,
                    contextState = covered,
                ),
            ),
        ).single()

        assertEquals(ConversationHistoryPolicy.MAXIMUM_MESSAGES_PER_CONVERSATION, normalized.messages.size)
        assertEquals(3L, normalized.messages.first().id)
        assertEquals(covered, normalized.contextState)
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
    fun `delete impact removes the selected sent message and every later message`() {
        val messages = listOf(
            ChatMessage(1, ChatMessageRole.USER, "keep"),
            assistant(2, "keep answer"),
            ChatMessage(3, ChatMessageRole.USER, "delete"),
            assistant(4, "old branch"),
            ChatMessage(5, ChatMessageRole.USER, "later"),
        )

        val impact = ConversationDeletionPolicy.impact(messages, 3)

        assertEquals(MessageDeletionImpact(messageIndex = 2, laterMessageCount = 2), impact)
        assertEquals(messages.take(2), ConversationDeletionPolicy.prefixBefore(messages, 3))
        assertNull(ConversationDeletionPolicy.impact(messages, 2))
    }

    @Test
    fun `plain text export includes titles roles and every nonblank message`() {
        val transcript = ConversationTranscriptFormatter.format(
            conversations = listOf(
                conversation("one", updatedAt = 20).copy(
                    title = "First chat",
                    messages = listOf(
                        ChatMessage(1, ChatMessageRole.USER, "Question"),
                        assistant(2, "Answer"),
                        ChatMessage(3, ChatMessageRole.NOTICE, "Model changed"),
                        ChatMessage(4, ChatMessageRole.USER, "   "),
                    ),
                ),
            ),
            userLabel = "You",
            assistantLabel = "Assistant",
            noticeLabel = "Notice",
        )

        assertEquals(
            "First chat\n\nYou:\nQuestion\n\nAssistant:\nAnswer\n\nNotice:\nModel changed",
            transcript,
        )
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

    private fun contextState(messages: List<ChatMessage>): ConversationContextState =
        ConversationContextState(
            coveredThroughMessageId = 2L,
            summarySegments = listOf(
                SummarySegment(
                    firstMessageId = 1L,
                    lastMessageId = 2L,
                    sourceMessageIds = listOf(1L, 2L),
                    sourceHash = ConversationContextPolicy.sourceHash(messages),
                    summary = "The user asked a first question and received an answer.",
                ),
            ),
            workingMemory = listOf(
                MemoryItem(
                    key = "goal.first-question",
                    kind = MemoryItemKind.GOAL,
                    text = "Answer the first question.",
                    sourceMessageIds = listOf(1L),
                    status = MemoryItemStatus.CONFIRMED,
                ),
            ),
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
