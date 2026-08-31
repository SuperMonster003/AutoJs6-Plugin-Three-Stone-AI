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

class ContextCompilationTest {
    private val exactEstimator = ContextTokenEstimator(
        tokensPerUtf8Byte = 1.0,
        messageRoleOverheadTokens = 0,
    )

    @Test
    fun `mixed turns are packed from the tail as intact chronological pairs`() {
        val context = ChatConversationPolicy.compileContext(
            transcript = listOf(
                user(1, "older-long"),
                assistant(2, "older-answer"),
                user(3, "middle"),
                assistant(4, "reply"),
                user(5, "new"),
                assistant(6, "ok"),
            ),
            prompt = prompt("p"),
            policy = policy(maximumInputTokens = 19L, minimumRecentTurns = 0),
        )

        assertEquals(listOf(3L, 4L, 5L, 6L), context.coveredMessageIds)
        assertEquals(listOf("middle", "reply", "new", "ok"), context.texts())
        assertEquals(17L, context.estimatedInputTokens)
        assertTrue(context.requiresSessionRebuild)
        assertFalse(context.exceedsInputBudget)
    }

    @Test
    fun `minimum recent turn floor is retained even for an extremely small budget`() {
        val context = ChatConversationPolicy.compileContext(
            transcript = listOf(
                user(1, "old"),
                assistant(2, "old-answer"),
                user(3, "recent-a"),
                assistant(4, "reply-a"),
                user(5, "recent-b"),
                assistant(6, "reply-b"),
            ),
            prompt = prompt("current"),
            policy = policy(maximumInputTokens = 1L),
        )

        assertEquals(listOf(3L, 4L, 5L, 6L), context.coveredMessageIds)
        assertEquals(37L, context.estimatedInputTokens)
        assertTrue(context.exceedsInputBudget)
        assertTrue(context.requiresSessionRebuild)
    }

    @Test
    fun `an oversized current prompt is preserved without partial history messages`() {
        val prompt = prompt("x".repeat(100))

        val context = ChatConversationPolicy.compileContext(
            transcript = listOf(
                user(1, "one"),
                assistant(2, "a"),
                user(3, "two"),
                assistant(4, "b"),
            ),
            prompt = prompt,
            policy = policy(maximumInputTokens = 10L),
        )

        assertEquals(listOf(1L, 2L, 3L, 4L), context.coveredMessageIds)
        assertEquals(108L, context.estimatedInputTokens)
        assertTrue(context.exceedsInputBudget)
    }

    @Test
    fun `failed stopped generating blank and unpaired turns never enter context`() {
        val context = ChatConversationPolicy.compileContext(
            transcript = listOf(
                user(1, "complete"),
                assistant(2, "answer"),
                user(3, "failed"),
                assistant(4, "partial", ChatMessageStatus.FAILED),
                user(5, "stopped"),
                assistant(6, "partial", ChatMessageStatus.STOPPED),
                user(7, "generating"),
                assistant(8, "partial", ChatMessageStatus.GENERATING),
                user(9, "blank"),
                assistant(10, ""),
                user(11, "pending"),
            ),
            prompt = prompt("current"),
            policy = policy(maximumInputTokens = 1_000L),
        )

        assertEquals(listOf(1L, 2L), context.coveredMessageIds)
        assertEquals(listOf("complete", "answer"), context.texts())
        assertFalse(context.requiresSessionRebuild)
    }

    @Test
    fun `transport guard stops at an oversized newest turn without backfilling older turns`() {
        val context = ChatConversationPolicy.compileContext(
            transcript = listOf(
                user(1, "older"),
                assistant(2, "answer"),
                user(3, "newest"),
                assistant(4, "x".repeat(30)),
            ),
            prompt = prompt("p"),
            policy = policy(maximumInputTokens = 1_000L, maximumHistoryBytes = 20L),
        )

        assertTrue(context.messages.isEmpty())
        assertTrue(context.coveredMessageIds.isEmpty())
        assertTrue(context.requiresSessionRebuild)
    }

    @Test
    fun `all fitting turns preserve source order without requesting compaction`() {
        val context = ChatConversationPolicy.compileContext(
            transcript = listOf(
                notice(1, "ignored"),
                user(2, "one"),
                assistant(3, "a"),
                user(4, "two"),
                assistant(5, "b"),
            ),
            prompt = prompt("p"),
            policy = policy(maximumInputTokens = 1_000L),
        )

        assertEquals(listOf(2L, 3L, 4L, 5L), context.coveredMessageIds)
        assertEquals(listOf("one", "a", "two", "b"), context.texts())
        assertFalse(context.requiresSessionRebuild)
    }

    @Test
    fun `derived memory and summaries precede only the raw turns after checkpoint coverage`() {
        val transcript = listOf(
            user(1, "covered"),
            assistant(2, "covered-answer"),
            user(3, "recent-a"),
            assistant(4, "reply-a"),
            user(5, "recent-b"),
            assistant(6, "reply-b"),
        )
        val contextState = contextState(transcript.take(2))

        val context = ChatConversationPolicy.compileContext(
            transcript = transcript,
            prompt = prompt("current"),
            policy = policy(
                maximumInputTokens = 10_000L,
                contextState = contextState,
            ),
        )

        assertEquals(
            listOf(
                GenerationRole.SYSTEM,
                GenerationRole.SYSTEM,
                GenerationRole.USER,
                GenerationRole.ASSISTANT,
                GenerationRole.USER,
                GenerationRole.ASSISTANT,
            ),
            context.messages.map(GenerationMessage::role),
        )
        assertEquals(listOf(3L, 4L, 5L, 6L), context.coveredMessageIds)
        assertEquals(1, context.workingMemoryItemsIncluded)
        assertEquals(1, context.summarySegmentsIncluded)
        assertEquals(ConversationContextPolicy.fingerprint(contextState), context.contextFingerprint)
        assertTrue(context.requiresSessionRebuild)
    }

    @Test
    fun `working memory cap keeps confirmed items before proposed items`() {
        val transcript = listOf(
            user(1, "covered"),
            assistant(2, "covered-answer"),
            user(3, "recent-a"),
            assistant(4, "reply-a"),
            user(5, "recent-b"),
            assistant(6, "reply-b"),
        )
        val confirmed = MemoryItem(
            key = "constraint.confirmed",
            kind = MemoryItemKind.CONSTRAINT,
            text = "Confirmed constraint.",
            sourceMessageIds = listOf(1L),
            status = MemoryItemStatus.CONFIRMED,
        )
        val proposed = MemoryItem(
            key = "fact.proposed",
            kind = MemoryItemKind.FACT,
            text = "Proposed fact.",
            sourceMessageIds = listOf(2L),
            status = MemoryItemStatus.PROPOSED,
        )
        val state = contextState(transcript.take(2)).copy(
            workingMemory = listOf(proposed, confirmed),
        )
        val confirmedOnlyLimit = exactEstimator.estimateMessage(
            ConversationContextFormatter.workingMemoryMessage(listOf(confirmed)),
        ).estimatedTokens

        val context = ChatConversationPolicy.compileContext(
            transcript = transcript,
            prompt = prompt("current"),
            policy = policy(
                maximumInputTokens = 100_000L,
                contextState = state,
                maximumWorkingMemoryTokens = confirmedOnlyLimit,
            ),
        )

        val memoryJson = context.messages.first().textParts.single()
        assertEquals(1, context.workingMemoryItemsIncluded)
        assertTrue(memoryJson.contains(confirmed.key))
        assertFalse(memoryJson.contains(proposed.key))
    }

    @Test
    fun `summary cap keeps the newest checkpoint segment`() {
        val transcript = listOf(
            user(1, "old"),
            assistant(2, "old-answer"),
            user(3, "middle"),
            assistant(4, "middle-answer"),
            user(5, "recent-a"),
            assistant(6, "reply-a"),
            user(7, "recent-b"),
            assistant(8, "reply-b"),
        )
        val older = summarySegment(transcript.subList(0, 2), "OLDER-CHECKPOINT")
        val newer = summarySegment(transcript.subList(2, 4), "NEWER-CHECKPOINT")
        val state = ConversationContextState(
            coveredThroughMessageId = 4L,
            summarySegments = listOf(older, newer),
        )
        val newestOnlyLimit = exactEstimator.estimateMessage(
            ConversationContextFormatter.summaryMessage(listOf(newer)),
        ).estimatedTokens

        val context = ChatConversationPolicy.compileContext(
            transcript = transcript,
            prompt = prompt("current"),
            policy = policy(
                maximumInputTokens = 100_000L,
                contextState = state,
                maximumSummaryTokens = newestOnlyLimit,
            ),
        )

        val summaryJson = context.messages.first().textParts.single()
        assertEquals(1, context.summarySegmentsIncluded)
        assertTrue(summaryJson.contains("NEWER-CHECKPOINT"))
        assertFalse(summaryJson.contains("OLDER-CHECKPOINT"))
    }

    @Test
    fun `absolute protection drops derived layers while preserving the recent raw floor`() {
        val transcript = listOf(
            user(1, "covered"),
            assistant(2, "covered-answer"),
            user(3, "recent-a"),
            assistant(4, "reply-a"),
            user(5, "recent-b"),
            assistant(6, "reply-b"),
        )

        val context = ChatConversationPolicy.compileContext(
            transcript = transcript,
            prompt = prompt("current"),
            policy = policy(
                maximumInputTokens = 10_000L,
                contextState = contextState(transcript.take(2)),
                includeDerivedContext = false,
            ),
        )

        assertEquals(
            listOf(
                GenerationRole.USER,
                GenerationRole.ASSISTANT,
                GenerationRole.USER,
                GenerationRole.ASSISTANT,
            ),
            context.messages.map(GenerationMessage::role),
        )
        assertEquals(0, context.workingMemoryItemsIncluded)
        assertEquals(0, context.summarySegmentsIncluded)
        assertEquals(listOf(3L, 4L, 5L, 6L), context.coveredMessageIds)
        assertNotEquals(
            ConversationContextPolicy.fingerprint(contextState(transcript.take(2))),
            context.contextFingerprint,
        )
    }

    @Test
    fun `compiler validates prompt and policy boundaries`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatConversationPolicy.compileContext(
                emptyList(),
                GenerationMessage(GenerationRole.ASSISTANT, listOf("not-user")),
                policy(10L),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy(maximumInputTokens = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy(maximumInputTokens = 10L, maximumHistoryBytes = 0L)
        }
    }

    private fun policy(
        maximumInputTokens: Long,
        minimumRecentTurns: Int = ContextPolicy.MINIMUM_RECENT_TURNS,
        maximumHistoryBytes: Long = ChatConversationPolicy.MAXIMUM_RETAINED_HISTORY_BYTES.toLong(),
        contextState: ConversationContextState = ConversationContextState.EMPTY,
        includeDerivedContext: Boolean = true,
        maximumWorkingMemoryTokens: Long = ContextPolicy.WORKING_MEMORY_MAXIMUM_TOKENS.toLong(),
        maximumSummaryTokens: Long = ContextPolicy.SUMMARY_LAYER_MAXIMUM_TOKENS.toLong(),
    ) = ContextCompilationPolicy(
        estimator = exactEstimator,
        maximumInputTokens = maximumInputTokens,
        minimumRecentTurns = minimumRecentTurns,
        maximumHistoryBytes = maximumHistoryBytes,
        contextState = contextState,
        includeDerivedContext = includeDerivedContext,
        maximumWorkingMemoryTokens = maximumWorkingMemoryTokens,
        maximumSummaryTokens = maximumSummaryTokens,
    )

    private fun summarySegment(source: List<ChatMessage>, summary: String) = SummarySegment(
        firstMessageId = source.first().id,
        lastMessageId = source.last().id,
        sourceMessageIds = source.map(ChatMessage::id),
        sourceHash = ConversationContextPolicy.sourceHash(source),
        summary = summary,
    )

    private fun contextState(source: List<ChatMessage>) = ConversationContextState(
        coveredThroughMessageId = source.last().id,
        summarySegments = listOf(
            SummarySegment(
                firstMessageId = source.first().id,
                lastMessageId = source.last().id,
                sourceMessageIds = source.map(ChatMessage::id),
                sourceHash = ConversationContextPolicy.sourceHash(source),
                summary = "Earlier checkpoint.",
            ),
        ),
        workingMemory = listOf(
            MemoryItem(
                key = "preference.concise",
                kind = MemoryItemKind.PREFERENCE,
                text = "Keep answers concise.",
                sourceMessageIds = listOf(source.first().id),
                status = MemoryItemStatus.CONFIRMED,
            ),
        ),
    )

    private fun prompt(text: String) = GenerationMessage(GenerationRole.USER, listOf(text))

    private fun user(id: Int, text: String) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.USER,
        text = text,
    )

    private fun assistant(
        id: Int,
        text: String,
        status: ChatMessageStatus = ChatMessageStatus.COMPLETE,
    ) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.ASSISTANT,
        text = text,
        status = status,
        target = responseTarget,
    )

    private fun notice(id: Int, text: String) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.NOTICE,
        text = text,
    )

    private fun CompiledContext.texts() = messages.map { message -> message.textParts.single() }

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
