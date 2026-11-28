package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmEngineFactory
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLocalBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLocalCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.ResourceLease
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.ReusableResourceCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The chat and its summary checkpoints share the process's single on-device engine (F2,
 * 3-stone-voice-1c, D222 / D223), and an idle chat gives it back to the app's other process (D227). The fake sessions borrow a real [ReusableResourceCache] the way
 * `LiteRtLocalSession` does: in [AiBackendSession.stream], until [AiBackendSession.close].
 */
class LocalEngineHandoffTest {
    private val executors = mutableListOf<ExecutorService>()
    private val engineScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor().also(executors::add)
    private val engines = ReusableResourceCache<String, FakeEngine>(engineScheduler, TimeUnit.DAYS.toMillis(1L))
    private val target = localTarget()
    private val scheduler = ManualScheduler()
    private val logger = RecordingLogger()
    private val backend = LeasingBackend(target, engines)
    private val settings = ChatUiSettings(maximumOutputTokens = 64, maximumOutputTokensDraft = 64)
    /** The context after a turn that reaches the hard watermark, so the next turn rebuilds its session. */
    private val rotatingContextTokens = ContextBudgetCalculator.calculate(
        targetLimits = target.limits,
        applicationInputTokenBudget = settings.contextTokenBudget,
        maximumOutputTokens = settings.maximumOutputTokens,
    ).hardWatermarkTokens

    @After
    fun tearDown() {
        backend.sessions.forEach(LeasingSession::close)
        engines.close()
        executors.forEach(ExecutorService::shutdownNow)
    }

    @Test
    fun `a rebuilt local turn waits for the replaced session to return the engine (F2)`() {
        val coordinator = coordinator(summaryExecutor = Executor { command -> command.run() })
        val screen = Screen("c1", shortTranscript())
        coordinator.attach(screen)

        coordinator.start(request("c1", shortTranscript()))
        val first = backend.awaitSession(0)
        first.awaitLease()
        first.listener.onCompleted(GenerationStatistics(40L, 8L, 30L, contextTokensAfterTurn = rotatingContextTokens))
        scheduler.runAll()
        assertEquals(1, screen.completions.size)

        // The turn rebuilds its session; the replaced one closes on a slow cancel thread.
        coordinator.start(request("c1", shortTranscript()))
        val second = backend.awaitSession(1)
        assertTrue("the rebuilt turn got the engine", second.awaitLease())
        assertTrue(first.closed.get())
        second.listener.onTextDelta("9")
        second.listener.onCompleted(GenerationStatistics(40L, 1L, 30L))
        scheduler.runAll()

        assertEquals(emptyList<String>(), screen.failures)
        assertEquals(2, screen.completions.size)
    }

    @Test
    fun `a local summary takes the idle engine and yields to the next turn (D223)`() {
        val coordinator = coordinator(summaryExecutor = singleThread())
        val transcript = longTranscript()
        val screen = Screen("c1", transcript)
        coordinator.attach(screen)

        coordinator.start(request("c1", transcript))
        val chat = backend.awaitSession(0)
        chat.awaitLease()
        chat.listener.onCompleted(GenerationStatistics(40L, 8L, 30L, contextTokensAfterTurn = rotatingContextTokens))
        // ManualScheduler ignores delays: this also runs the idle wait that follows the completion.
        scheduler.runAll()

        val summary = backend.awaitSession(1)
        assertTrue("the summary got the engine once the idle chat session returned it", summary.awaitLease())
        assertTrue(chat.closed.get())
        assertTrue(logger.messages.any { message -> message.startsWith("Context summary start") })

        coordinator.start(request("c1", transcript))
        val next = backend.awaitSession(2)
        assertTrue("the next turn got the engine from the yielding summary", next.awaitLease())
        assertTrue(summary.cancelled.get())
        assertTrue(logger.messages.any { message -> message.startsWith("Context summary yields") })
        next.listener.onCompleted(GenerationStatistics(40L, 8L, 30L, contextTokensAfterTurn = rotatingContextTokens))
        scheduler.runAll()

        // The yielded summary was not recorded as failed: the same plan is tried again.
        val retried = backend.awaitSession(3)
        assertTrue(retried.awaitLease())
        assertEquals(emptyList<String>(), screen.failures)
        assertEquals(2, screen.completions.size)
        assertTrue(logger.messages.none { message -> message.startsWith("Context summary failed") })
    }

    @Test
    fun `an idle local session gives the engine back to another process, a reply in progress keeps it (D227)`() {
        val coordinator = coordinator(summaryExecutor = Executor { command -> command.run() })
        val screen = Screen("c1", shortTranscript())
        coordinator.attach(screen)

        coordinator.start(request("c1", shortTranscript()))
        val first = backend.awaitSession(0)
        first.awaitLease()
        first.listener.onCompleted(GenerationStatistics(40L, 8L, 30L, contextTokensAfterTurn = 50L))
        scheduler.runAll()
        assertEquals(1, screen.completions.size)

        // What ThreeStoneAiApplication does when the script provider is about to load a model.
        assertTrue(coordinator.releaseIdleLocalEngine())
        engines.evict()
        assertTrue("the engine was closed after the idle session returned it", engines.awaitEmpty(3_000L))
        assertTrue(first.closed.get())
        assertEquals(1, coordinator.engineHandoffCountsForTest.releasedForAnotherProcess.get())

        // The next turn loads the engine again in a new session.
        coordinator.start(request("c1", shortTranscript()))
        val second = backend.awaitSession(1)
        assertTrue(second.awaitLease())
        assertFalse("a reply in progress keeps its engine", coordinator.releaseIdleLocalEngine())
        assertFalse(second.closed.get())
        assertFalse(second.cancelled.get())
        second.listener.onTextDelta("9")
        second.listener.onCompleted(GenerationStatistics(40L, 1L, 30L, contextTokensAfterTurn = 60L))
        scheduler.runAll()

        assertEquals(emptyList<String>(), screen.failures)
        assertEquals(2, screen.completions.size)
        assertEquals(1, coordinator.engineHandoffCountsForTest.releasedForAnotherProcess.get())
    }

    private fun coordinator(summaryExecutor: Executor) = ChatGenerationCoordinator(
        backend = { backend },
        store = EmptyStore(),
        calibrationStore = ContextTokenCalibrationStore(MemoryCalibration()),
        scheduler = scheduler,
        failureText = { _, error -> "failure: ${error.message}" },
        logger = logger,
        backendExecutor = singleThread(),
        // Like the app: a new thread per close, here slowed down so the next borrower arrives first.
        cancelExecutor = Executor { command ->
            Thread {
                Thread.sleep(CLOSE_DELAY_MILLIS)
                command.run()
            }.apply { isDaemon = true }.start()
        },
        summaryExecutor = summaryExecutor,
    )

    private fun singleThread(): ExecutorService = Executors.newSingleThreadExecutor().also(executors::add)

    private fun request(conversationId: String, transcript: List<ChatMessage>) = ChatTurnRequest(
        conversationId = conversationId,
        assistantMessageId = transcript.last().id,
        target = target,
        executionProfileId = null,
        prompt = transcript[transcript.lastIndex - 1].text,
        transcript = transcript,
        contextState = ConversationContextState.EMPTY,
        settings = settings,
    )

    private fun shortTranscript() = listOf(
        ChatMessage(1L, ChatMessageRole.USER, "8 + 1"),
        ChatMessage(2L, ChatMessageRole.ASSISTANT, "", ChatMessageStatus.GENERATING, target = ConversationTargetSnapshot.from(target)),
    )

    /** Thirty complete turns, far over the compaction target, so a summary plan exists. */
    private fun longTranscript(): List<ChatMessage> {
        val snapshot = ConversationTargetSnapshot.from(target)
        val messages = ArrayList<ChatMessage>()
        var id = 1L
        repeat(30) { turn ->
            messages += ChatMessage(id++, ChatMessageRole.USER, "Question $turn: " + "tell me more. ".repeat(40))
            messages += ChatMessage(id++, ChatMessageRole.ASSISTANT, "Answer $turn: " + "here is more. ".repeat(40), target = snapshot)
        }
        messages += ChatMessage(id++, ChatMessageRole.USER, "One more question?")
        messages += ChatMessage(id, ChatMessageRole.ASSISTANT, "", ChatMessageStatus.GENERATING, target = snapshot)
        return messages
    }

    private class FakeEngine : AutoCloseable {
        override fun close() = Unit
    }

    private class LeasingBackend(
        private val target: AiTarget,
        private val engines: ReusableResourceCache<String, FakeEngine>,
    ) : AiBackend {
        val sessions = CopyOnWriteArrayList<LeasingSession>()

        override val backendId: String = LiteRtLocalBackend.BACKEND_ID

        override fun ownsTarget(targetId: String): Boolean = targetId == target.targetId

        override fun catalog(): AiTargetCatalog = throw UnsupportedOperationException()

        override fun createSession(request: AiBackendSessionRequest): AiBackendSession =
            LeasingSession(target, engines).also(sessions::add)

        fun awaitSession(index: Int): LeasingSession {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L)
            while (sessions.size <= index && System.nanoTime() < deadline) Thread.sleep(5L)
            return sessions[index]
        }
    }

    private class LeasingSession(
        override val target: AiTarget,
        private val engines: ReusableResourceCache<String, FakeEngine>,
    ) : AiBackendSession {
        private val leased = CountDownLatch(1)
        private val lock = Any()
        private var lease: ResourceLease<FakeEngine>? = null
        val cancelled = AtomicBoolean(false)
        val closed = AtomicBoolean(false)

        @Volatile
        lateinit var listener: GenerationListener

        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            this.listener = listener
            try {
                val acquired = engines.acquire("model", shouldStop = { cancelled.get() || closed.get() }) { FakeEngine() }
                val keep = synchronized(lock) {
                    if (closed.get()) false else true.also { lease = acquired }
                }
                if (!keep) acquired.close() else leased.countDown()
            } catch (error: Throwable) {
                if (!cancelled.get() && !closed.get()) listener.onFailed(error, null)
            }
        }

        override fun streamNext(request: GenerationRequest, listener: GenerationListener) {
            this.listener = listener
        }

        override fun cancel() {
            cancelled.set(true)
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            synchronized(lock) { lease.also { lease = null } }?.close()
        }

        fun awaitLease(): Boolean = leased.await(3L, TimeUnit.SECONDS)
    }

    private inner class Screen(
        private val conversationId: String,
        private val messages: List<ChatMessage>,
    ) : ChatGenerationCoordinator.Observer {
        val completions = CopyOnWriteArrayList<ChatGenerationCompletion>()
        val failures = CopyOnWriteArrayList<String>()

        override fun onDelta(generation: ChatGenerationSnapshot, delta: String) = Unit

        override fun onCompleted(generation: ChatGenerationSnapshot, completion: ChatGenerationCompletion) {
            completions += completion
        }

        override fun onFailed(generation: ChatGenerationSnapshot, failureText: String) {
            failures += failureText
        }

        override fun onStateChanged(active: ChatGenerationSnapshot?) = Unit

        override fun conversationSnapshot(conversationId: String): ChatConversationSnapshot? =
            if (conversationId == this.conversationId) {
                ChatConversationSnapshot(
                    messages.map { message -> message.copy(status = ChatMessageStatus.COMPLETE) },
                    ConversationContextState.EMPTY,
                )
            } else {
                null
            }

        override fun onContextStateChanged(conversationId: String, state: ConversationContextState) = Unit
    }

    private class ManualScheduler : ChatGenerationScheduler {
        private val queue = ArrayList<() -> Unit>()

        override fun post(delayMillis: Long, action: () -> Unit) {
            synchronized(queue) { queue += action }
        }

        fun runAll() {
            while (true) {
                val next = synchronized(queue) { if (queue.isEmpty()) null else queue.removeAt(0) } ?: return
                next()
            }
        }
    }

    private class EmptyStore : ChatGenerationStore {
        override fun find(conversationId: String): StoredConversation? = null

        override fun update(conversation: StoredConversation): Boolean = false

        override fun recall(
            conversationId: String,
            query: String,
            excludedMessageIds: Set<Long>,
        ): List<ConversationRecallChunk> = emptyList()
    }

    private class MemoryCalibration : ContextTokenCalibrationPersistence {
        private val values = HashMap<String, ContextTokenCalibration>()

        override fun load(targetId: String): ContextTokenCalibration? = values[targetId]

        override fun save(targetId: String, calibration: ContextTokenCalibration) {
            values[targetId] = calibration
        }
    }

    private class RecordingLogger : ChatGenerationLogger {
        val messages = CopyOnWriteArrayList<String>()

        override fun debug(message: String) {
            messages += message
        }

        override fun warn(message: String, error: Throwable?) {
            messages += message
        }

        override fun error(message: String, error: Throwable?) {
            messages += message
        }
    }

    private companion object {
        const val CLOSE_DELAY_MILLIS = 150L

        fun localTarget() = AiTarget(
            targetId = AiTargetIds.local("gemma"),
            backendId = LiteRtLocalBackend.BACKEND_ID,
            providerId = ThreeStoneAiPlugin.PROVIDER_ID,
            profileId = null,
            modelId = "gemma",
            displayName = "Gemma",
            locality = AiTargetLocality.LOCAL,
            credentialMode = AiTargetCredentialMode.NONE,
            declaredHttpsOrigins = emptyList(),
            configured = true,
            available = true,
            capabilities = LiteRtLocalCatalog.capabilities,
            limits = AiTargetLimits(
                maximumContextBytes = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES,
                maximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
                maximumContextTokens = LiteRtLmEngineFactory.MAXIMUM_CONTEXT_TOKENS,
            ),
            executionProfiles = emptyList(),
        )
    }
}
