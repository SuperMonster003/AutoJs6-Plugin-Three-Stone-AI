package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiExecutionProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryCheckpointerTest {
    private val directExecutor = Executor(Runnable::run)
    private val estimator = ContextTokenEstimator(
        tokensPerUtf8Byte = 1.0,
        messageRoleOverheadTokens = 0,
    )

    @Test
    fun `successful checkpoint uses an isolated one-shot session and closes it`() {
        val backend = FakeBackend(success(validResponse()))
        var outcome: SummaryCheckpointOutcome? = null

        SummaryCheckpointer(backend, directExecutor).generate(request()) { result ->
            outcome = result
        }

        val success = outcome as SummaryCheckpointOutcome.Success
        assertEquals(1, success.attempts)
        assertEquals(2L, success.validatedState.coveredThroughMessageId)
        assertEquals(1, backend.createdSessions)
        assertEquals(1, backend.closedSessions)
        assertEquals(0, backend.cancelledSessions)
        assertEquals(target.targetId, backend.sessionRequests.single().targetId)
        assertEquals(
            SummaryPromptProtocol.RESPONSE_SCHEMA,
            backend.generationRequests.single().responseJsonSchema,
        )
    }

    @Test
    fun `malformed JSON and network failures receive exactly one retry`() {
        val malformedBackend = FakeBackend(success("{}"), success(validResponse()))
        var malformedOutcome: SummaryCheckpointOutcome? = null
        SummaryCheckpointer(malformedBackend, directExecutor).generate(request()) { result ->
            malformedOutcome = result
        }

        val networkBackend = FakeBackend(
            failure(IOException("offline")),
            failure(IOException("still offline")),
        )
        var networkOutcome: SummaryCheckpointOutcome? = null
        SummaryCheckpointer(networkBackend, directExecutor).generate(request()) { result ->
            networkOutcome = result
        }

        assertTrue(malformedOutcome is SummaryCheckpointOutcome.Success)
        assertEquals(2, (malformedOutcome as SummaryCheckpointOutcome.Success).attempts)
        assertEquals(2, malformedBackend.createdSessions)
        assertTrue(networkOutcome is SummaryCheckpointOutcome.Failure)
        assertEquals(2, (networkOutcome as SummaryCheckpointOutcome.Failure).attempts)
        assertEquals(2, networkBackend.createdSessions)
        assertEquals(2, networkBackend.closedSessions)
    }

    @Test
    fun `oversized input fails before opening a hidden backend session`() {
        val backend = FakeBackend(success(validResponse()))
        var outcome: SummaryCheckpointOutcome? = null

        SummaryCheckpointer(backend, directExecutor).generate(
            request(maximumInputTokens = 1L),
        ) { result -> outcome = result }

        val failure = outcome as SummaryCheckpointOutcome.Failure
        assertTrue(failure.error is SummaryCheckpointInputLimitException)
        assertEquals(0, failure.attempts)
        assertEquals(0, backend.createdSessions)
    }

    @Test
    fun `oversized streamed output is cancelled and retried once`() {
        val backend = FakeBackend(
            success("x".repeat(20)),
            success("x".repeat(20)),
        )
        var outcome: SummaryCheckpointOutcome? = null

        SummaryCheckpointer(
            backend = backend,
            executor = directExecutor,
            maximumResponseBytes = 10,
            cancellationExecutor = directExecutor,
        ).generate(request()) { result -> outcome = result }

        val failure = outcome as SummaryCheckpointOutcome.Failure
        assertTrue(failure.error is SummaryCheckpointResponseLimitException)
        assertEquals(2, failure.attempts)
        assertEquals(2, backend.cancelledSessions)
    }

    @Test
    fun `cancelling a blocking stream does not wait behind the summary executor`() {
        val backend = BlockingBackend()
        val worker = Executors.newSingleThreadExecutor()
        val callbackDelivered = AtomicBoolean(false)
        try {
            val task = SummaryCheckpointer(backend, worker).generate(request()) {
                callbackDelivered.set(true)
            }
            assertTrue(backend.started.await(2, TimeUnit.SECONDS))

            task.cancel()

            assertTrue(backend.cancelled.await(2, TimeUnit.SECONDS))
            assertTrue(backend.finished.await(2, TimeUnit.SECONDS))
            assertTrue(!callbackDelivered.get())
        } finally {
            worker.shutdownNow()
        }
    }

    private fun request(maximumInputTokens: Long = 10_000L): SummaryCheckpointRequest {
        val transcript = listOf(
            user(1, "Always answer concisely."),
            assistant(2, "Understood."),
            user(3, "recent"),
            assistant(4, "reply"),
        )
        val plan = checkNotNull(
            SummaryCheckpointPlanner.plan(
                transcript = transcript,
                state = ConversationContextState.EMPTY,
                retainedRawMessageIds = listOf(3L, 4L),
                estimator = estimator,
                maximumSourceTokens = 1_000L,
            ),
        )
        return SummaryCheckpointRequest(
            targetId = target.targetId,
            executionProfileId = null,
            structuredJson = true,
            plan = plan,
            state = ConversationContextState.EMPTY,
            transcript = transcript,
            estimator = estimator,
            maximumInputTokens = maximumInputTokens,
        )
    }

    private fun validResponse(): String = """
        {
          "summary":"The user wants concise answers.",
          "workingMemory":[{
            "key":"preference.concise",
            "kind":"PREFERENCE",
            "text":"Keep answers concise.",
            "sourceMessageIds":[1],
            "status":"CONFIRMED"
          }]
        }
    """.trimIndent()

    private fun success(response: String): (GenerationListener) -> Unit = { listener ->
        listener.onTextDelta(response)
        listener.onCompleted(GenerationStatistics(10, 10, 20))
    }

    private fun failure(error: Throwable): (GenerationListener) -> Unit = { listener ->
        listener.onFailed(error, null)
    }

    private fun user(id: Int, text: String) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.USER,
        text = text,
    )

    private fun assistant(id: Int, text: String) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.ASSISTANT,
        text = text,
        target = responseTarget,
    )

    private class FakeBackend(
        vararg behaviors: (GenerationListener) -> Unit,
    ) : AiBackend {
        private val behaviors = ArrayDeque(behaviors.toList())
        var createdSessions = 0
        var closedSessions = 0
        var cancelledSessions = 0
        val sessionRequests = mutableListOf<AiBackendSessionRequest>()
        val generationRequests = mutableListOf<GenerationRequest>()

        override val backendId = "fake"

        override fun ownsTarget(targetId: String): Boolean = targetId == target.targetId

        override fun catalog() = AiTargetCatalog("fake-generation", target.targetId, listOf(target))

        override fun createSession(request: AiBackendSessionRequest): AiBackendSession {
            require(request.targetId == target.targetId)
            val behavior = behaviors.removeFirst()
            createdSessions++
            sessionRequests += request
            return object : AiBackendSession {
                override val target = Companion.target

                override fun stream(request: GenerationRequest, listener: GenerationListener) {
                    generationRequests += request
                    behavior(listener)
                }

                override fun cancel() {
                    cancelledSessions++
                }

                override fun close() {
                    closedSessions++
                }
            }
        }
    }

    private class BlockingBackend : AiBackend {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val finished = CountDownLatch(1)

        override val backendId = "blocking"

        override fun ownsTarget(targetId: String): Boolean = targetId == target.targetId

        override fun catalog() = AiTargetCatalog("blocking-generation", target.targetId, listOf(target))

        override fun createSession(request: AiBackendSessionRequest): AiBackendSession =
            object : AiBackendSession {
                override val target = Companion.target

                override fun stream(request: GenerationRequest, listener: GenerationListener) {
                    started.countDown()
                    try {
                        cancelled.await(5, TimeUnit.SECONDS)
                    } finally {
                        finished.countDown()
                    }
                }

                override fun cancel() {
                    cancelled.countDown()
                }

                override fun close() = Unit
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
        val target = AiTarget(
            targetId = "local:test-model",
            backendId = "fake",
            providerId = "autojs6.three-stone-ai",
            profileId = null,
            modelId = "test-model",
            displayName = "Test model",
            locality = AiTargetLocality.LOCAL,
            credentialMode = AiTargetCredentialMode.NONE,
            declaredHttpsOrigins = emptyList(),
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
            limits = AiTargetLimits(null, null),
            executionProfiles = emptyList<AiExecutionProfile>(),
        )
    }
}
