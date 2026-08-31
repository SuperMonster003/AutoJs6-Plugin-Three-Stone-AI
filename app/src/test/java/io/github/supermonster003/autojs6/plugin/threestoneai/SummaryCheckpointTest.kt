package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryCheckpointTest {
    private val exactEstimator = ContextTokenEstimator(
        tokensPerUtf8Byte = 1.0,
        messageRoleOverheadTokens = 0,
    )

    @Test
    fun `planner summarizes only the complete turns outside the retained suffix`() {
        val transcript = transcriptWithInterruptedTurn()

        val plan = SummaryCheckpointPlanner.plan(
            transcript = transcript,
            state = ConversationContextState.EMPTY,
            retainedRawMessageIds = listOf(8L, 9L),
            estimator = exactEstimator,
            maximumSourceTokens = 1_000L,
        )

        checkNotNull(plan)
        assertEquals(listOf(1L, 2L, 6L, 7L), plan.sourceMessageIds)
        assertEquals((1L..7L).toList(), plan.rangeMessages.map(ChatMessage::id))
        assertTrue(plan.sourceMessages.none { message -> message.status != ChatMessageStatus.COMPLETE })
        assertTrue(plan.sourceMessages.none { message -> message.role == ChatMessageRole.NOTICE })
    }

    @Test
    fun `planner chunks at complete-turn boundaries and refuses an oversized first turn`() {
        val transcript = listOf(
            user(1, "12345"),
            assistant(2, "67890"),
            user(3, "abc"),
            assistant(4, "def"),
            user(5, "new"),
            assistant(6, "reply"),
        )

        val oneTurn = SummaryCheckpointPlanner.plan(
            transcript,
            ConversationContextState.EMPTY,
            retainedRawMessageIds = listOf(5L, 6L),
            estimator = exactEstimator,
            maximumSourceTokens = 10L,
        )
        val none = SummaryCheckpointPlanner.plan(
            transcript,
            ConversationContextState.EMPTY,
            retainedRawMessageIds = listOf(5L, 6L),
            estimator = exactEstimator,
            maximumSourceTokens = 9L,
        )
        val oneTurnInputTokens = SummaryPromptProtocol.inputEstimate(
            checkNotNull(oneTurn),
            ConversationContextState.EMPTY,
            exactEstimator,
        ).estimatedTokens
        val inputBounded = SummaryCheckpointPlanner.planWithinInputBudget(
            transcript = transcript,
            state = ConversationContextState.EMPTY,
            retainedRawMessageIds = listOf(5L, 6L),
            estimator = exactEstimator,
            maximumSourceTokens = 1_000L,
            maximumInputTokens = oneTurnInputTokens,
        )

        assertEquals(listOf(1L, 2L), oneTurn?.sourceMessageIds)
        assertEquals(listOf(1L, 2L), inputBounded?.sourceMessageIds)
        assertNull(none)
    }

    @Test
    fun `planner keeps checkpoint ranges continuous across ignored transcript records`() {
        val transcript = transcriptWithInterruptedTurn()
        val firstState = ConversationContextState(
            coveredThroughMessageId = 2L,
            summarySegments = listOf(segment(transcript.take(2), "First turn")),
        )

        val plan = SummaryCheckpointPlanner.plan(
            transcript = transcript,
            state = firstState,
            retainedRawMessageIds = listOf(8L, 9L),
            estimator = exactEstimator,
            maximumSourceTokens = 1_000L,
        )

        checkNotNull(plan)
        assertEquals(listOf(6L, 7L), plan.sourceMessageIds)
        assertEquals((3L..7L).toList(), plan.rangeMessages.map(ChatMessage::id))
        assertEquals(3L, plan.firstMessageId)
        assertEquals(7L, plan.lastMessageId)
    }

    @Test
    fun `checkpoint trigger starts at the soft watermark`() {
        val budget = ContextBudgetCalculator.calculate(
            targetLimits = AiTargetLimits(
                maximumContextBytes = null,
                maximumOutputBytes = null,
            ),
            applicationInputTokenBudget = 100,
            maximumOutputTokens = 1,
        )

        assertEquals(65L, budget.softWatermarkTokens)
        assertTrue(
            !SummaryCheckpointTriggerPolicy.shouldSchedule(
                ContextAccounting(64L, ContextAccountingSource.FULL_ESTIMATE),
                budget,
            ),
        )
        assertTrue(
            SummaryCheckpointTriggerPolicy.shouldSchedule(
                ContextAccounting(65L, ContextAccountingSource.BACKEND_EXACT),
                budget,
            ),
        )
        assertTrue(!SummaryCheckpointTriggerPolicy.shouldSchedule(null, budget))
    }

    @Test
    fun `strict JSON codec accepts the schema and rejects wrappers unknown keys and decimal IDs`() {
        val valid = """
            {
              "summary":"A compact checkpoint.",
              "workingMemory":[{
                "key":"preference.concise",
                "kind":"PREFERENCE",
                "text":"Keep answers concise.",
                "sourceMessageIds":[1],
                "status":"CONFIRMED"
              }]
            }
        """.trimIndent()

        val decoded = SummaryCheckpointJsonCodec.decode(valid)

        assertEquals("A compact checkpoint.", decoded.summary)
        assertEquals("preference.concise", decoded.workingMemory.single().key)
        assertThrows(IllegalArgumentException::class.java) {
            SummaryCheckpointJsonCodec.decode("```json\n$valid\n```")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SummaryCheckpointJsonCodec.decode(valid.replace("\"summary\":", "\"extra\":1,\"summary\":"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SummaryCheckpointJsonCodec.decode(valid.replace("[1]", "[1.0]"))
        }
    }

    @Test
    fun `prompt only JSON mode still carries the complete response contract`() {
        val transcript = listOf(
            user(1, "remember this"),
            assistant(2, "understood"),
            user(3, "recent"),
            assistant(4, "reply"),
        )
        val plan = checkNotNull(
            SummaryCheckpointPlanner.plan(
                transcript = transcript,
                state = ConversationContextState.EMPTY,
                retainedRawMessageIds = listOf(3L, 4L),
                estimator = exactEstimator,
                maximumSourceTokens = 1_000L,
            ),
        )

        val request = SummaryPromptProtocol.generationRequest(
            plan = plan,
            state = ConversationContextState.EMPTY,
            structuredJson = false,
        )
        val systemInstruction = request.history.single().textParts.single()

        assertNull(request.responseJsonSchema)
        assertTrue(systemInstruction.contains("exactly two properties"))
        assertTrue(systemInstruction.contains("\"summary\""))
        assertTrue(systemInstruction.contains("\"workingMemory\""))
        assertTrue(systemInstruction.contains("sourceMessageIds"))
    }

    @Test
    fun `validator accepts user-confirmed memory and rejects assistant-only confirmation`() {
        val transcript = listOf(
            user(1, "Always answer concisely."),
            assistant(2, "I will keep answers concise."),
            user(3, "new"),
            assistant(4, "reply"),
        )
        val plan = checkNotNull(
            SummaryCheckpointPlanner.plan(
                transcript,
                ConversationContextState.EMPTY,
                retainedRawMessageIds = listOf(3L, 4L),
                estimator = exactEstimator,
                maximumSourceTokens = 1_000L,
            ),
        )
        val accepted = SummaryCheckpointValidator.apply(
            state = ConversationContextState.EMPTY,
            plan = plan,
            draft = SummaryCheckpointDraft(
                summary = "The user requires concise answers.",
                workingMemory = listOf(
                    memory(
                        key = "preference.concise",
                        sourceIds = listOf(1L),
                        status = MemoryItemStatus.CONFIRMED,
                    ),
                    memory(
                        key = "fact.assistant-promise",
                        sourceIds = listOf(2L),
                        status = MemoryItemStatus.PROPOSED,
                    ),
                ),
            ),
            transcript = transcript,
        )

        assertEquals(2L, accepted.coveredThroughMessageId)
        assertEquals(2, accepted.workingMemory.size)
        assertThrows(IllegalArgumentException::class.java) {
            SummaryCheckpointValidator.apply(
                ConversationContextState.EMPTY,
                plan,
                SummaryCheckpointDraft(
                    summary = "Invalid confirmation.",
                    workingMemory = listOf(
                        memory(
                            key = "decision.assistant-only",
                            sourceIds = listOf(2L),
                            status = MemoryItemStatus.CONFIRMED,
                        ),
                    ),
                ),
                transcript,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SummaryCheckpointValidator.apply(
                ConversationContextState.EMPTY,
                plan,
                SummaryCheckpointDraft(
                    summary = "x".repeat(ConversationContextPolicy.MAXIMUM_SUMMARY_BYTES + 1),
                    workingMemory = emptyList(),
                ),
                transcript,
            )
        }
    }

    @Test
    fun `validator enforces provenance and proposed to confirmed state transitions`() {
        val transcript = listOf(
            user(1, "Could we use blue?"),
            assistant(2, "I suggest blue."),
            user(3, "Yes, blue is confirmed."),
            assistant(4, "Confirmed."),
            user(5, "recent"),
            assistant(6, "reply"),
        )
        val initialSegment = SummarySegment(
            firstMessageId = 1L,
            lastMessageId = 2L,
            sourceMessageIds = listOf(1L, 2L),
            sourceHash = ConversationContextPolicy.sourceHash(transcript.take(2)),
            summary = "Blue was suggested but not yet confirmed.",
        )
        val proposed = memory(
            key = "decision.color",
            sourceIds = listOf(2L),
            status = MemoryItemStatus.PROPOSED,
        )
        val state = ConversationContextState(
            coveredThroughMessageId = 2L,
            summarySegments = listOf(initialSegment),
            workingMemory = listOf(proposed),
        )
        val plan = checkNotNull(
            SummaryCheckpointPlanner.plan(
                transcript,
                state,
                retainedRawMessageIds = listOf(5L, 6L),
                estimator = exactEstimator,
                maximumSourceTokens = 1_000L,
            ),
        )
        val confirmed = proposed.copy(
            text = "Use blue.",
            sourceMessageIds = listOf(2L, 3L),
            status = MemoryItemStatus.CONFIRMED,
        )

        val updated = SummaryCheckpointValidator.apply(
            state,
            plan,
            SummaryCheckpointDraft("The user confirmed blue.", listOf(confirmed)),
            transcript,
        )

        assertEquals(MemoryItemStatus.CONFIRMED, updated.workingMemory.single().status)
        assertEquals(4L, updated.coveredThroughMessageId)
        assertThrows(IllegalArgumentException::class.java) {
            SummaryCheckpointValidator.apply(
                state,
                plan,
                SummaryCheckpointDraft(
                    "Bad provenance.",
                    listOf(confirmed.copy(sourceMessageIds = listOf(2L, 5L))),
                ),
                transcript,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SummaryCheckpointValidator.apply(
                state,
                plan,
                SummaryCheckpointDraft("Missing existing memory.", emptyList()),
                transcript,
            )
        }
        val rejectedState = state.copy(
            workingMemory = listOf(proposed.copy(status = MemoryItemStatus.REJECTED)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            SummaryCheckpointValidator.apply(
                rejectedState,
                plan.copy(previousCoveredThroughMessageId = rejectedState.coveredThroughMessageId),
                SummaryCheckpointDraft(
                    "Illegal rejected-to-confirmed transition.",
                    listOf(
                        proposed.copy(
                            sourceMessageIds = listOf(2L, 3L),
                            status = MemoryItemStatus.CONFIRMED,
                        ),
                    ),
                ),
                transcript,
            )
        }
    }

    @Test
    fun `invalidation removes the affected checkpoint tail and dependent memory`() {
        val transcript = listOf(
            user(1, "one"),
            assistant(2, "answer"),
            user(3, "two"),
            assistant(4, "answer"),
        )
        val first = segment(transcript.take(2), "First")
        val second = segment(transcript.drop(2), "Second")
        val state = ConversationContextState(
            coveredThroughMessageId = 4L,
            summarySegments = listOf(first, second),
            workingMemory = listOf(
                memory("fact.first", listOf(1L), MemoryItemStatus.CONFIRMED),
                memory("fact.second", listOf(3L), MemoryItemStatus.CONFIRMED),
            ),
        )

        val invalidated = ConversationContextPolicy.invalidateFrom(state, 3L)

        assertEquals(listOf(first), invalidated.summarySegments)
        assertEquals(listOf("fact.first"), invalidated.workingMemory.map(MemoryItem::key))
        assertEquals(2L, invalidated.coveredThroughMessageId)
        assertEquals(ConversationContextState.EMPTY, ConversationContextPolicy.invalidateFrom(state, 1L))
    }

    @Test
    fun `context fingerprint is deterministic and changes with derived content`() {
        val messages = listOf(user(1, "one"), assistant(2, "answer"))
        val base = ConversationContextState(
            coveredThroughMessageId = 2L,
            summarySegments = listOf(segment(messages, "First summary")),
        )

        assertEquals(
            ConversationContextPolicy.fingerprint(base),
            ConversationContextPolicy.fingerprint(base.copy()),
        )
        assertNotEquals(
            ConversationContextPolicy.fingerprint(base),
            ConversationContextPolicy.fingerprint(
                base.copy(
                    summarySegments = listOf(segment(messages, "Changed summary")),
                ),
            ),
        )
    }

    private fun transcriptWithInterruptedTurn(): List<ChatMessage> = listOf(
        user(1, "old"),
        assistant(2, "old answer"),
        user(3, "failed"),
        assistant(4, "partial", ChatMessageStatus.FAILED),
        ChatMessage(5, ChatMessageRole.NOTICE, "Target changed"),
        user(6, "middle"),
        assistant(7, "middle answer"),
        user(8, "recent"),
        assistant(9, "recent answer"),
    )

    private fun segment(messages: List<ChatMessage>, summary: String) = SummarySegment(
        firstMessageId = messages.first().id,
        lastMessageId = messages.last().id,
        sourceMessageIds = messages.map(ChatMessage::id),
        sourceHash = ConversationContextPolicy.sourceHash(messages),
        summary = summary,
    )

    private fun memory(
        key: String,
        sourceIds: List<Long>,
        status: MemoryItemStatus,
    ) = MemoryItem(
        key = key,
        kind = MemoryItemKind.DECISION,
        text = key,
        sourceMessageIds = sourceIds,
        status = status,
    )

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
