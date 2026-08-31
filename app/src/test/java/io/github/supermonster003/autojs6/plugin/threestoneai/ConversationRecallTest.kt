package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRecallTest {
    private val exactEstimator = ContextTokenEstimator(
        tokensPerUtf8Byte = 1.0,
        messageRoleOverheadTokens = 0,
    )

    @Test
    fun `complete history is indexed as stable three-turn chunks with a two-turn tail`() {
        val conversation = conversation(
            turns = (1..8).map { turn ->
                "问题 $turn ORCHID-$turn" to "回答 $turn"
            },
        )

        val chunks = ConversationRecallPolicy.chunks(conversation)

        assertEquals(listOf(3, 3, 2), chunks.map { chunk -> chunk.turns.size })
        assertEquals(listOf(1L, 7L, 13L), chunks.map { chunk -> chunk.firstMessageId })
        assertEquals(listOf(6L, 12L, 16L), chunks.map { chunk -> chunk.lastMessageId })
        assertEquals(
            listOf(
                GenerationRole.USER,
                GenerationRole.ASSISTANT,
                GenerationRole.USER,
                GenerationRole.ASSISTANT,
                GenerationRole.USER,
                GenerationRole.ASSISTANT,
            ),
            chunks.first().generationMessages().map(GenerationMessage::role),
        )
        assertTrue(chunks.first().searchTerms.contains("问题"))
        assertTrue(chunks.first().searchTerms.contains("orchid-1"))
    }

    @Test
    fun `one incomplete tail turn is not indexed until another turn completes`() {
        val single = ConversationRecallPolicy.chunks(
            conversation(listOf("only" to "answer")),
        )
        val two = ConversationRecallPolicy.chunks(
            conversation(listOf("one" to "answer", "two" to "answer")),
        )

        assertTrue(single.isEmpty())
        assertEquals(1, two.size)
        assertEquals(2, two.single().turns.size)
    }

    @Test
    fun `query tokenizer supports CJK bigrams identifiers and camel case`() {
        val terms = ConversationRecallPolicy.queryTerms(
            "请回忆之前的预算设置与 contextTokenBudget / ORCHID-731",
        )

        assertTrue(terms.contains("预算"))
        assertTrue(terms.contains("设置"))
        assertTrue(terms.contains("contexttokenbudget"))
        assertTrue(terms.contains("context"))
        assertTrue(terms.contains("token"))
        assertTrue(terms.contains("budget"))
        assertTrue(terms.contains("orchid-731"))
        assertFalse(terms.contains("之前"))
        assertTrue(
            ConversationRecallPolicy.ftsMatchExpression(listOf("预算", "orchid-731")) ==
                "\"预算\" OR \"orchid-731\"",
        )
    }

    @Test
    fun `ranking favors keyword coverage then uses message time as a recency signal`() {
        val olderExact = ConversationRecallPolicy.chunks(
            conversation(
                turns = listOf(
                    "ORCHID-731 input budget" to "16K tokens",
                    "unrelated" to "answer",
                ),
                id = "older",
                firstMessageId = 1L,
            ),
        ).single()
        val newerPartial = ConversationRecallPolicy.chunks(
            conversation(
                turns = listOf(
                    "input budget" to "32K tokens",
                    "newer note" to "answer",
                ),
                id = "newer",
                firstMessageId = 101L,
            ),
        ).single().copy(conversationId = "older")

        val exactQuery = ConversationRecallPolicy.queryTerms("ORCHID-731 input budget")
        val exactRanking = ConversationRecallPolicy.rank(
            listOf(newerPartial, olderExact),
            exactQuery,
        )
        val sharedQuery = ConversationRecallPolicy.queryTerms("input budget")
        val sharedRanking = ConversationRecallPolicy.rank(
            listOf(olderExact, newerPartial),
            sharedQuery,
        )

        assertEquals(olderExact.firstMessageId, exactRanking.first().chunk.firstMessageId)
        assertEquals(newerPartial.firstMessageId, sharedRanking.first().chunk.firstMessageId)
        assertTrue(exactRanking.first().matchedTerms > exactRanking.last().matchedTerms)
    }

    @Test
    fun `sticky merge is stable without a new hit and replaces an edited source range`() {
        val chunks = ConversationRecallPolicy.chunks(
            conversation((1..8).map { turn -> "question-$turn" to "answer-$turn" }),
        )
        val sticky = chunks.take(2)

        assertTrue(
            ConversationRecallPolicy.sameChunks(
                sticky,
                ConversationRecallPolicy.mergeSticky(sticky, emptyList()),
            ),
        )
        val edited = sticky.first().copy(sourceHash = "a".repeat(64))
        val merged = ConversationRecallPolicy.mergeSticky(sticky, listOf(edited))
        assertEquals(2, merged.size)
        assertEquals(edited.identity, merged.first().identity)
        assertFalse(merged.any { chunk -> chunk.identity == sticky.first().identity })
    }

    @Test
    fun `payload codec round trips newlines emoji and delimiter-looking text`() {
        val turns = listOf(
            ConversationRecallTurn(1L, 2L, "12\nline 😀", "TSR1\n0\nanswer"),
            ConversationRecallTurn(3L, 4L, "second", "尾声"),
        )

        val encoded = ConversationRecallPayloadCodec.encode(turns)

        assertEquals(turns, ConversationRecallPayloadCodec.decode(encoded))
        assertThrows(IllegalArgumentException::class.java) {
            ConversationRecallPayloadCodec.decode(encoded.dropLast(1))
        }
    }

    @Test
    fun `compiler places recall before the recent raw suffix without duplicating source turns`() {
        val transcript = messages(
            (1..5).map { turn -> "question-$turn" to "answer-$turn" },
        )
        val recalled = ConversationRecallPolicy.chunks(
            storedConversation("recall", transcript.take(6)),
        ).single()

        val compiled = ChatConversationPolicy.compileContext(
            transcript = transcript,
            prompt = GenerationMessage(GenerationRole.USER, listOf("current")),
            policy = ContextCompilationPolicy(
                estimator = exactEstimator,
                maximumInputTokens = 100_000L,
                minimumRecentTurns = 2,
                recalledHistory = listOf(recalled),
            ),
        )

        assertEquals(1, compiled.recalledChunksIncluded)
        assertEquals(listOf(7L, 8L, 9L, 10L), compiled.coveredMessageIds)
        assertEquals(
            listOf(
                "question-1", "answer-1", "question-2", "answer-2", "question-3",
                "answer-3", "question-4", "answer-4", "question-5", "answer-5",
            ),
            compiled.messages.map { message -> message.textParts.single() },
        )
        assertTrue(compiled.layerTokens.recalledHistoryTokens > 0L)
        assertEquals(
            exactEstimator.estimateMessages(compiled.messages).estimatedTokens,
            compiled.layerTokens.estimatedTotalTokens,
        )
        assertNotEquals(
            ConversationContextPolicy.fingerprint(ConversationContextState.EMPTY),
            compiled.contextFingerprint,
        )
    }

    @Test
    fun `recall obeys its layer cap and emergency derived-context omission`() {
        val transcript = messages(
            (1..5).map { turn -> "question-$turn" to "answer-$turn" },
        )
        val recalled = ConversationRecallPolicy.chunks(
            storedConversation("recall", transcript.take(6)),
        ).single()
        val prompt = GenerationMessage(GenerationRole.USER, listOf("current"))

        val capped = ChatConversationPolicy.compileContext(
            transcript,
            prompt,
            ContextCompilationPolicy(
                estimator = exactEstimator,
                maximumInputTokens = 100_000L,
                recalledHistory = listOf(recalled),
                maximumRecalledHistoryTokens = 1L,
            ),
        )
        val emergency = ChatConversationPolicy.compileContext(
            transcript,
            prompt,
            ContextCompilationPolicy(
                estimator = exactEstimator,
                maximumInputTokens = 100_000L,
                recalledHistory = listOf(recalled),
                includeDerivedContext = false,
            ),
        )

        assertEquals(0, capped.recalledChunksIncluded)
        assertEquals(0L, capped.layerTokens.recalledHistoryTokens)
        assertEquals(0, emergency.recalledChunksIncluded)
        assertEquals(0L, emergency.layerTokens.recalledHistoryTokens)
    }

    private fun conversation(
        turns: List<Pair<String, String>>,
        id: String = "conversation",
        firstMessageId: Long = 1L,
    ): StoredConversation = storedConversation(
        id = id,
        messages = messages(turns, firstMessageId),
    )

    private fun storedConversation(id: String, messages: List<ChatMessage>) = StoredConversation(
        id = id,
        title = "Recall test",
        createdAtMillis = 1L,
        updatedAtMillis = 2L,
        target = responseTarget,
        messages = messages,
    )

    private fun messages(
        turns: List<Pair<String, String>>,
        firstMessageId: Long = 1L,
    ): List<ChatMessage> = buildList {
        var id = firstMessageId
        turns.forEach { (question, answer) ->
            add(ChatMessage(id++, ChatMessageRole.USER, question))
            add(
                ChatMessage(
                    id = id++,
                    role = ChatMessageRole.ASSISTANT,
                    text = answer,
                    target = responseTarget,
                ),
            )
        }
    }

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
