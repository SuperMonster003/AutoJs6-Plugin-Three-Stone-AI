package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.ContextTokenEstimator
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiExecutionProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentSessionContextTest {
    private val exactEstimator = ContextTokenEstimator(
        tokensPerUtf8Byte = 1.0,
        messageRoleOverheadTokens = 0,
    )

    @Test
    fun retainsInitialSystemAndHistoryOnlyAfterSuccessfulCompletion() {
        val context = context()
        val request = request(
            history = listOf(
                message(GenerationRole.SYSTEM, "system"),
                message(GenerationRole.USER, "old-user"),
                message(GenerationRole.ASSISTANT, "old-answer"),
            ),
            prompt = "current-user",
        )

        val abandoned = context.prepareFirst(request, target())
        assertFalse(context.diagnostics().initialized)
        context.abandon(abandoned)
        assertFalse(context.diagnostics().initialized)

        val prepared = context.prepareFirst(request, target())
        val commit = context.complete(prepared, "current-answer", statistics(42L))

        assertEquals(PersistentBackendTurnMode.INITIAL, prepared.mode)
        assertEquals(request, prepared.generationRequest)
        assertTrue(commit.diagnostics.initialized)
        assertEquals(1, commit.diagnostics.systemMessageCount)
        assertEquals(0, commit.diagnostics.anchoredMessageCount)
        assertEquals(2, commit.diagnostics.completedTurnCount)
        assertEquals(42L, commit.diagnostics.accounting.tokens)
        assertEquals(1, commit.diagnostics.completedTurnsOnBackend)
        assertEquals(1L, commit.diagnostics.backendEpoch)
    }

    @Test
    fun failedOrCancelledContinuationDoesNotEnterTheTranscript() {
        val context = initializedContext()
        val before = context.diagnostics()

        val failed = context.prepareNext(request(prompt = "failed"), target())
        context.abandon(failed)
        assertEquals(before, context.diagnostics())

        val cancelled = context.prepareNext(request(prompt = "cancelled"), target())
        context.abandon(cancelled)
        assertEquals(before, context.diagnostics())
    }

    @Test
    fun nonMonotonicExactUsageCannotLowerTheCurrentBackendHighWater() {
        val context = context(applicationInputTokenBudget = 1_000)
        val first = context.prepareFirst(request(prompt = "first"), target())
        context.complete(first, "answer", statistics(100L))
        val dipped = context.prepareNext(request(prompt = "dip"), target())
        context.complete(dipped, "answer", statistics(60L))

        val diagnostics = context.diagnostics()
        assertEquals(60L, diagnostics.accounting.tokens)
        assertEquals(100L, diagnostics.accountingHighWater.tokens)

        val next = context.prepareNext(request(prompt = "after-dip"), target())
        assertEquals(PersistentBackendTurnMode.CONTINUE, next.mode)
        assertEquals(100L, next.accountingBefore.tokens)
        assertTrue(next.estimatedInputTokens > 100L)
        context.abandon(next)
    }

    @Test
    fun memoryGuardEvictsOldestWholeTurnsAndForcesTheNextRebuild() {
        val context = context(
            applicationInputTokenBudget = 10_000,
            maximumTranscriptBytes = 100L,
            transcriptCompactionTargetBytes = 45L,
        )
        completeFirst(context, prompt = "u0".padEnd(20, 'u'), answer = "a0".padEnd(20, 'a'))
        repeat(3) { index ->
            completeNext(
                context,
                prompt = "u${index + 1}".padEnd(20, 'u'),
                answer = "a${index + 1}".padEnd(20, 'a'),
                contextTokens = 1L,
            )
        }

        val diagnostics = context.diagnostics()
        assertTrue(diagnostics.transcriptBytes <= 100L)
        assertEquals(2, diagnostics.completedTurnCount)
        assertTrue(diagnostics.rebuildRequired)

        val rebuilding = context.prepareNext(request(prompt = "after-guard"), target())
        assertEquals(PersistentBackendTurnMode.REBUILD, rebuilding.mode)
        assertEquals(2, rebuilding.retainedTurnCount)
        assertTrue(rebuilding.generationRequest.history.none { generationMessage ->
            generationMessage.textParts.any { text -> text.startsWith("u0") }
        })
        context.abandon(rebuilding)
    }

    @Test
    fun irregularInitialHistoryRemainsAnchoredAcrossARebuild() {
        val context = context(
            maximumTranscriptBytes = 45L,
            transcriptCompactionTargetBytes = 20L,
        )
        val first = context.prepareFirst(
            request(
                history = listOf(
                    message(GenerationRole.SYSTEM, "system"),
                    message(GenerationRole.ASSISTANT, "orphan-assistant"),
                ),
                prompt = "first",
            ),
            target(),
        )
        context.complete(first, "answer", statistics(33L))
        val continued = context.prepareNext(request(prompt = "middle"), target())
        context.complete(continued, "answer2", statistics(46L))

        val rebuild = context.prepareNext(request(prompt = "second"), target())

        assertEquals(PersistentBackendTurnMode.REBUILD, rebuild.mode)
        assertEquals(GenerationRole.SYSTEM, rebuild.generationRequest.history[0].role)
        assertEquals("orphan-assistant", rebuild.generationRequest.history[1].textParts.single())
        context.abandon(rebuild)
    }

    @Test
    fun rebuildCalibratesAnUnderestimateAgainstExactContextAccounting() {
        val underestimated = ContextTokenEstimator(
            tokensPerUtf8Byte = 0.1,
            messageRoleOverheadTokens = 0,
        )
        val context = context(
            estimator = underestimated,
            applicationInputTokenBudget = 400,
        )
        val prompt = "u".repeat(20)
        val answer = "a".repeat(20)
        val first = context.prepareFirst(request(prompt = prompt), target())
        context.complete(first, answer, statistics(50L))
        repeat(6) { index ->
            val continued = context.prepareNext(request(prompt = prompt), target())
            assertEquals(PersistentBackendTurnMode.CONTINUE, continued.mode)
            context.complete(continued, answer, statistics((index + 2L) * 50L))
        }

        val rebuild = context.prepareNext(request(prompt = prompt), target())

        assertEquals(PersistentBackendTurnMode.REBUILD, rebuild.mode)
        assertTrue(rebuild.evictedTurnCount > 0)
        assertTrue(rebuild.retainedTurnCount < 7)
        assertTrue(rebuild.estimatedInputTokens <= rebuild.budget.compactionTargetTokens)
        assertEquals(rebuild.retainedTurnCount * 2, rebuild.generationRequest.history.size)
        context.abandon(rebuild)
    }

    @Test
    fun fortyEightTurnsKeepHostEventsStableWhileUnderlyingSessionsRebuild() {
        val context = context(applicationInputTokenBudget = 200)
        val hostEvents = ArrayList<String>()
        val backendModes = ArrayList<PersistentBackendTurnMode>()
        val backendSessions = ArrayList<FakePersistentBackendSession>()
        var activeBackend: FakePersistentBackendSession? = null

        repeat(48) { turn ->
            val prompt = "user-$turn"
            val prepared = if (turn == 0) {
                context.prepareFirst(
                    request(
                        history = listOf(message(GenerationRole.SYSTEM, "stable-system")),
                        prompt = prompt,
                    ),
                    target(),
                )
            } else {
                context.prepareNext(request(prompt = prompt), target())
            }
            backendModes += prepared.mode
            when (prepared.mode) {
                PersistentBackendTurnMode.INITIAL,
                PersistentBackendTurnMode.REBUILD,
                -> {
                    activeBackend?.close()
                    activeBackend = FakePersistentBackendSession(target(), exactEstimator)
                        .also(backendSessions::add)
                }
                PersistentBackendTurnMode.CONTINUE -> {
                    assertTrue(prepared.generationRequest.history.isEmpty())
                }
            }
            assertTrue(prepared.estimatedInputTokens <= prepared.budget.absoluteProtectionTokens)

            hostEvents += "started:stable-session"
            val output = StringBuilder()
            checkNotNull(activeBackend).streamPreparedPersistentTurn(
                prepared,
                object : GenerationListener {
                    override fun onTextDelta(text: String) {
                        output.append(text)
                        hostEvents += "delta:$text"
                    }

                    override fun onCompleted(statistics: GenerationStatistics?) {
                        hostEvents += "usage:${checkNotNull(statistics).contextTokensAfterTurn}"
                        context.complete(prepared, output.toString(), statistics)
                        hostEvents += "completed"
                    }

                    override fun onFailed(
                        error: Throwable,
                        statistics: GenerationStatistics?,
                    ) = throw AssertionError(error)
                },
            )
        }
        activeBackend?.close()

        assertTrue(backendSessions.size > 1)
        assertTrue(backendSessions.all { session -> session.closed })
        assertTrue(backendModes.contains(PersistentBackendTurnMode.REBUILD))
        assertTrue(backendModes.contains(PersistentBackendTurnMode.CONTINUE))
        assertEquals(48 * 4, hostEvents.size)
        hostEvents.chunked(4).forEach { events ->
            assertEquals("started:stable-session", events[0])
            assertTrue(events[1].startsWith("delta:"))
            assertTrue(events[2].startsWith("usage:"))
            assertEquals("completed", events[3])
        }
        assertTrue(context.diagnostics().accounting.tokens < 200L)
    }

    @Test
    fun irreduciblePromptFailsClosedWithAnExistingProtocolErrorCode() {
        val context = context(applicationInputTokenBudget = 100)
        completeFirst(context, prompt = "small", answer = "answer")

        assertThrows(PersistentSessionContextExhaustedException::class.java) {
            context.prepareNext(request(prompt = "x".repeat(200)), target())
        }
        assertEquals(
            AiErrorCode.INVALID_REQUEST,
            PersistentSessionFailurePolicy.contextExhaustedErrorCode,
        )
    }

    private fun initializedContext(): PersistentSessionContext = context().also { value ->
        completeFirst(value, prompt = "first", answer = "answer")
    }

    private fun completeFirst(
        context: PersistentSessionContext,
        prompt: String,
        answer: String,
    ) {
        val prepared = context.prepareFirst(request(prompt = prompt), target())
        context.complete(prepared, answer, statistics(20L))
    }

    private fun completeNext(
        context: PersistentSessionContext,
        prompt: String,
        answer: String,
        contextTokens: Long,
    ) {
        val prepared = context.prepareNext(request(prompt = prompt), target())
        context.complete(prepared, answer, statistics(contextTokens))
    }

    private fun context(
        estimator: ContextTokenEstimator = exactEstimator,
        applicationInputTokenBudget: Int = 16_384,
        maximumTranscriptBytes: Long = PersistentSessionContextLimits.MAXIMUM_TRANSCRIPT_BYTES,
        transcriptCompactionTargetBytes: Long =
            maximumTranscriptBytes * PersistentSessionContextLimits.COMPACTION_TARGET_PERCENT / 100L,
    ) = PersistentSessionContext(
        estimator = estimator,
        applicationInputTokenBudget = applicationInputTokenBudget,
        maximumTranscriptBytes = maximumTranscriptBytes,
        transcriptCompactionTargetBytes = transcriptCompactionTargetBytes,
    )

    private fun request(
        history: List<GenerationMessage> = emptyList(),
        prompt: String,
    ) = GenerationRequest(
        history = history,
        prompt = message(GenerationRole.USER, prompt),
        maximumOutputTokens = null,
        samplingOptions = null,
        reportUsage = true,
    )

    private fun message(role: GenerationRole, text: String) =
        GenerationMessage(role, listOf(text))

    private fun statistics(contextTokens: Long) = GenerationStatistics(
        inputTokens = contextTokens,
        outputTokens = 0L,
        durationMillis = 1L,
        contextTokensAfterTurn = contextTokens,
    )

    private fun target() = AiTarget(
        targetId = "profile:test-profile",
        backendId = "test-backend",
        providerId = ThreeStoneAiPlugin.PROVIDER_ID,
        profileId = "test-profile",
        modelId = "test-model",
        displayName = "Test target",
        locality = AiTargetLocality.REMOTE,
        credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf("https://example.com"),
        configured = true,
        available = true,
        capabilities = AiTargetCapabilities(
            streaming = true,
            persistentSession = true,
            structuredJson = true,
            usage = true,
            reasoning = false,
            tools = false,
        ),
        limits = AiTargetLimits(
            maximumContextBytes = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES,
            maximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
        ),
        executionProfiles = listOf(AiExecutionProfile("cpu", available = true)),
    )

    private class FakePersistentBackendSession(
        override val target: AiTarget,
        private val estimator: ContextTokenEstimator,
    ) : AiBackendSession {
        private var messages = emptyList<GenerationMessage>()
        private var initialized = false
        var closed = false
            private set

        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            check(!closed)
            messages = request.history
            initialized = true
            generate(request, listener)
        }

        override fun streamNext(request: GenerationRequest, listener: GenerationListener) {
            check(initialized && !closed)
            check(request.history.isEmpty())
            generate(request, listener)
        }

        private fun generate(request: GenerationRequest, listener: GenerationListener) {
            val answer = "answer:${request.prompt.textParts.joinToString("")}"
            messages = messages + request.prompt + GenerationMessage(
                GenerationRole.ASSISTANT,
                listOf(answer),
            )
            val contextTokens = estimator.estimateMessages(messages).estimatedTokens
            listener.onTextDelta(answer)
            listener.onCompleted(
                GenerationStatistics(
                    inputTokens = contextTokens,
                    outputTokens = 0L,
                    durationMillis = 1L,
                    contextTokensAfterTurn = contextTokens,
                ),
            )
        }

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }
    }
}
