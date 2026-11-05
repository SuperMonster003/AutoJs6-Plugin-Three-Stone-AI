package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiTransportLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** State machine of the process-wide chat generation coordinator (roadmap D77, P1.4). */
class ChatGenerationCoordinatorTest {
    private val target = remoteTarget()
    private val targetSnapshot = ConversationTargetSnapshot.from(target)
    private val scheduler = ManualScheduler()
    private val store = MemoryStore()
    private val logger = RecordingLogger()
    private val backend = FakeBackend(target)
    private val executors = mutableListOf<ExecutorService>()

    @After
    fun tearDown() {
        executors.forEach(ExecutorService::shutdownNow)
    }

    @Test
    fun `streamed text reaches the screen in batches and the completion follows`() {
        val coordinator = coordinator()
        val screen = Screen("c1")
        coordinator.attach(screen)

        val generationId = coordinator.start(request("c1"))
        val session = backend.sessions.single()
        session.listener.onTextDelta("Hel")
        session.listener.onTextDelta("lo")
        scheduler.runAll()

        assertEquals(listOf("Hello"), screen.deltas)
        assertEquals(generationId, screen.lastState?.generationId)
        assertTrue(coordinator.isGenerating)

        session.listener.onCompleted(GenerationStatistics(12L, 3L, 40L))
        scheduler.runAll()

        val completion = screen.completions.single()
        assertEquals("Hello", completion.text)
        assertEquals(ChatMessageUsage(12L, 3L, 40L), completion.usage)
        assertFalse(completion.truncated)
        assertFalse(coordinator.isGenerating)
        assertNull(screen.lastState)
        assertEquals(0, session.cancelCount.get())
        assertTrue(logger.messages.any { message -> message.startsWith("Context complete") })
    }

    @Test
    fun `reasoning is buffered timed and survives a recreated screen (P3)`() {
        val clock = AtomicLong(1_000L)
        val coordinator = coordinator(clock = clock::get)
        store.put(conversation("c1"))
        val before = Screen("c1")
        coordinator.attach(before)
        coordinator.start(request("c1"))
        val session = backend.sessions.single()
        assertTrue(session.request.includeReasoning)

        session.listener.onReasoningDelta("Plan")
        session.listener.onReasoningDelta(" it")
        scheduler.runAll()
        assertEquals(listOf(ChatMessageReasoning("Plan it", null)), before.reasoning)
        assertTrue(before.deltas.isEmpty())

        coordinator.detach(before)
        clock.set(3_400L)
        session.listener.onTextDelta("Hi")
        scheduler.runAll()
        val after = Screen("c1")
        val attachment = coordinator.attach(after)
        assertEquals("Hi", attachment.active?.text)
        assertEquals(ChatMessageReasoning("Plan it", 2_400L), attachment.active?.reasoning)

        session.listener.onTextDelta("!")
        session.listener.onCompleted(GenerationStatistics(1L, 2L, 3L))
        scheduler.runAll()
        assertTrue(after.reasoning.isEmpty())
        assertEquals(listOf("!"), after.deltas)
        val completion = after.completions.single()
        assertEquals("Hi!", completion.text)
        assertEquals(ChatMessageReasoning("Plan it", 2_400L), completion.reasoning)
    }

    @Test
    fun `reasoning of a reply finishing without a screen is stored and capped (P3)`() {
        val clock = AtomicLong(0L)
        val coordinator = coordinator(clock = clock::get)
        store.put(conversation("c1"))
        val screen = Screen("c1")
        coordinator.attach(screen)
        coordinator.start(request("c1"))
        val session = backend.sessions.single()
        coordinator.detach(screen)

        session.listener.onReasoningDelta("a".repeat(ChatMessageReasoning.MAXIMUM_BYTES - 1))
        session.listener.onReasoningDelta("bcd")
        clock.set(900L)
        session.listener.onCompleted(GenerationStatistics(1L, 2L, 3L))
        scheduler.runAll()

        val stored = requireNotNull(store.reply("c1").reasoning)
        assertEquals("", store.reply("c1").text)
        assertEquals(ChatMessageReasoning.MAXIMUM_BYTES, stored.text.length)
        assertTrue(stored.text.endsWith("ab"))
        assertTrue(stored.truncated)
        assertEquals(900L, stored.durationMillis)
    }

    @Test
    fun `a stopped reply keeps the reasoning it had (P3)`() {
        val clock = AtomicLong(0L)
        val coordinator = coordinator(clock = clock::get)
        coordinator.attach(Screen("c1"))
        coordinator.start(request("c1"))
        val session = backend.sessions.single()
        session.listener.onReasoningDelta("half")
        clock.set(250L)

        val stopped = requireNotNull(coordinator.stop())

        assertEquals(ChatMessageReasoning("half", 250L), stopped.reasoning)
        assertEquals("", stopped.text)
    }

    @Test
    fun `a screen recreated during streaming continues the same reply`() {
        val coordinator = coordinator()
        val before = Screen("c1")
        coordinator.attach(before)
        coordinator.start(request("c1"))
        val session = backend.sessions.single()
        session.listener.onTextDelta("Hel")
        scheduler.runAll()

        // Rotation, night mode, language or font size: onStop detaches, the new instance attaches.
        coordinator.detach(before)
        session.listener.onTextDelta("lo wor")
        scheduler.runAll()
        val after = Screen("c1")
        val attachment = coordinator.attach(after)

        assertEquals(listOf("Hel"), before.deltas)
        assertEquals("Hello wor", attachment.active?.text)
        assertTrue(attachment.changedConversationIds.isEmpty())

        session.listener.onTextDelta("ld")
        session.listener.onCompleted(GenerationStatistics(20L, 5L, 60L))
        scheduler.runAll()

        assertEquals(listOf("ld"), after.deltas)
        assertEquals("Hello world", after.completions.single().text)
        assertTrue(before.completions.isEmpty())
        assertEquals(0, session.cancelCount.get())
        assertEquals(1, backend.sessions.size)
    }

    @Test
    fun `a reply finishing with no screen attached is stored and reported on attach`() {
        val coordinator = coordinator()
        store.put(conversation("c1"))
        val screen = Screen("c1")
        coordinator.attach(screen)
        coordinator.start(request("c1"))
        val session = backend.sessions.single()
        coordinator.detach(screen)

        session.listener.onTextDelta("Done")
        session.listener.onCompleted(GenerationStatistics(12L, 3L, 40L), true)
        scheduler.runAll()

        val stored = store.reply("c1")
        assertEquals(ChatMessageStatus.COMPLETE, stored.status)
        assertEquals("Done", stored.text)
        assertEquals(ChatMessageUsage(12L, 3L, 40L), stored.usage)
        assertTrue(stored.truncated)
        assertTrue(screen.completions.isEmpty())

        val attachment = Screen("c1").let(coordinator::attach)
        assertNull(attachment.active)
        assertEquals(setOf("c1"), attachment.changedConversationIds)
        assertTrue(coordinator.attach(Screen("c1")).changedConversationIds.isEmpty())
    }

    @Test
    fun `stop hard-cancels a blocked backend on its own thread within one second`() {
        val backendExecutor = Executors.newSingleThreadExecutor().also(executors::add)
        val coordinator = coordinator(backendExecutor = backendExecutor, cancelExecutor = null)
        backend.blockStreams = true
        val screen = Screen("c1")
        coordinator.attach(screen)
        coordinator.start(request("c1"))
        val session = backend.awaitSession()
        assertTrue(session.streamEntered.await(2L, TimeUnit.SECONDS))
        session.listener.onTextDelta("partial")
        scheduler.runAll()

        val requestedAt = System.nanoTime()
        val stopped = coordinator.stop()
        val stopMillis = (System.nanoTime() - requestedAt) / NANOS_PER_MILLI

        assertEquals("partial", stopped?.text)
        assertTrue("stop() blocked for ${stopMillis}ms", stopMillis < 1_000L)
        assertTrue(session.cancelled.await(1L, TimeUnit.SECONDS))
        val cancelMillis = (session.cancelledAtNanos.get() - requestedAt) / NANOS_PER_MILLI
        assertTrue("backend cancelled after ${cancelMillis}ms", cancelMillis in 0L..1_000L)
        assertEquals("three-stone-ai-chat-cancel", session.cancelThreadName)
        assertTrue(session.streamReturned.await(1L, TimeUnit.SECONDS))
        assertTrue(logger.messages.contains("Generation cancel requested reason=stopped target=${target.targetId}"))
        assertTrue(
            logger.await { message ->
                message.startsWith("Generation cancel complete reason=stopped ") &&
                    message.endsWith("success=true fake=cancelled")
            },
        )
        assertFalse(coordinator.isGenerating)
    }

    @Test
    fun `callbacks arriving after stop are ignored`() {
        val coordinator = coordinator()
        val screen = Screen("c1")
        coordinator.attach(screen)
        coordinator.start(request("c1"))
        val session = backend.sessions.single()
        session.listener.onTextDelta("kept")
        scheduler.runAll()

        assertEquals("kept", coordinator.stop()?.text)
        session.listener.onTextDelta(" late")
        session.listener.onCompleted(GenerationStatistics(1L, 1L, 1L))
        session.listener.onFailed(IllegalStateException("late"), null)
        scheduler.runAll()

        assertEquals(listOf("kept"), screen.deltas)
        assertTrue(screen.completions.isEmpty())
        assertTrue(screen.failures.isEmpty())
        assertEquals(1, session.cancelCount.get())
        assertEquals(1, session.closeCount.get())
        assertNull(coordinator.stop())
        assertFalse(coordinator.closeBackend())
    }

    @Test
    fun `stopping with no screen attached stores the stopped text`() {
        val coordinator = coordinator()
        store.put(conversation("c1"))
        val screen = Screen("c1")
        coordinator.attach(screen)
        coordinator.start(request("c1"))
        val session = backend.sessions.single()
        session.listener.onTextDelta("Partial")
        scheduler.runAll()
        coordinator.detach(screen)

        val stopped = coordinator.stop()

        assertEquals("Partial", stopped?.text)
        val stored = store.reply("c1")
        assertEquals(ChatMessageStatus.STOPPED, stored.status)
        assertEquals("Partial", stored.text)
        assertNull(stored.usage)
        assertEquals(1, session.cancelCount.get())
        assertEquals(setOf("c1"), coordinator.attach(screen).changedConversationIds)
    }

    @Test
    fun `a new turn stops the background reply of another conversation`() {
        val coordinator = coordinator()
        store.put(conversation("c1"))
        val screen = Screen("c2")
        coordinator.attach(screen)
        coordinator.start(request("c1"))
        val first = backend.sessions.single()
        first.listener.onTextDelta("background")
        scheduler.runAll()

        coordinator.start(request("c2"))

        assertEquals(1, first.cancelCount.get())
        assertEquals(2, backend.sessions.size)
        val stored = store.reply("c1")
        assertEquals(ChatMessageStatus.STOPPED, stored.status)
        assertEquals("background", stored.text)
        assertNull(coordinator.activeFor("c1"))
        assertNotNull(coordinator.activeFor("c2"))
        assertTrue(logger.messages.any { message -> message.startsWith("Generation cancel requested reason=superseded") })
    }

    @Test
    fun `a failure is shown on the screen and the backend is cancelled`() {
        val coordinator = coordinator()
        val screen = Screen("c1")
        coordinator.attach(screen)
        coordinator.start(request("c1"))
        val session = backend.sessions.single()
        session.listener.onTextDelta("par")
        session.listener.onFailed(IllegalStateException("boom"), null)
        scheduler.runAll()

        val (generation, failure) = screen.failures.single()
        assertEquals("par", generation.text)
        assertEquals("failure: boom", failure)
        assertEquals(1, session.cancelCount.get())
        assertFalse(coordinator.isGenerating)
    }

    @Test
    fun `a failure with no screen attached is stored with the failure text`() {
        val coordinator = coordinator()
        store.put(conversation("c1"))
        val screen = Screen("c1")
        coordinator.attach(screen)
        coordinator.start(request("c1"))
        val session = backend.sessions.single()
        coordinator.detach(screen)
        session.listener.onTextDelta("par")
        session.listener.onFailed(IllegalStateException("boom"), null)
        scheduler.runAll()

        val stored = store.reply("c1")
        assertEquals(ChatMessageStatus.FAILED, stored.status)
        assertEquals(ChatGenerationText.withFailure("par", "failure: boom"), stored.text)
        assertTrue(screen.failures.isEmpty())
        assertEquals(setOf("c1"), coordinator.attach(screen).changedConversationIds)
    }

    @Test
    fun `activeFor reports only the conversation being generated`() {
        val coordinator = coordinator()
        coordinator.start(request("c1"))
        backend.sessions.single().listener.onTextDelta("abc")

        assertEquals("abc", coordinator.activeFor("c1")?.text)
        assertEquals(2L, coordinator.activeFor("c1")?.assistantMessageId)
        assertNull(coordinator.activeFor("c2"))
    }

    private fun coordinator(
        backendExecutor: ExecutorService = DirectExecutorService(),
        cancelExecutor: Executor? = Executor { command -> command.run() },
        clock: () -> Long = { System.nanoTime() / 1_000_000L },
    ): ChatGenerationCoordinator {
        val calibration = ContextTokenCalibrationStore(MemoryCalibration())
        val failureText: (ConversationTargetSnapshot, Throwable) -> String = { _, error ->
            "failure: ${error.message}"
        }
        return if (cancelExecutor == null) {
            ChatGenerationCoordinator(
                backend = { backend },
                store = store,
                calibrationStore = calibration,
                scheduler = scheduler,
                failureText = failureText,
                logger = logger,
                backendExecutor = backendExecutor,
                summaryExecutor = Executor { command -> command.run() },
                clock = clock,
            )
        } else {
            ChatGenerationCoordinator(
                backend = { backend },
                store = store,
                calibrationStore = calibration,
                scheduler = scheduler,
                failureText = failureText,
                logger = logger,
                backendExecutor = backendExecutor,
                cancelExecutor = cancelExecutor,
                summaryExecutor = Executor { command -> command.run() },
                clock = clock,
            )
        }
    }

    private fun conversation(id: String) = StoredConversation(
        id = id,
        title = "Title $id",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
        target = targetSnapshot,
        messages = transcript(),
    )

    private fun transcript() = listOf(
        ChatMessage(1L, ChatMessageRole.USER, "hello"),
        ChatMessage(2L, ChatMessageRole.ASSISTANT, "", ChatMessageStatus.GENERATING, target = targetSnapshot),
    )

    private fun request(conversationId: String) = ChatTurnRequest(
        conversationId = conversationId,
        assistantMessageId = 2L,
        target = target,
        executionProfileId = null,
        prompt = "hello",
        transcript = transcript(),
        contextState = ConversationContextState.EMPTY,
        settings = ChatUiSettings(),
    )

    private inner class Screen(private val conversationId: String) : ChatGenerationCoordinator.Observer {
        val deltas = mutableListOf<String>()
        val reasoning = mutableListOf<ChatMessageReasoning?>()
        val completions = mutableListOf<ChatGenerationCompletion>()
        val failures = mutableListOf<Pair<ChatGenerationSnapshot, String>>()
        var lastState: ChatGenerationSnapshot? = null

        override fun onDelta(generation: ChatGenerationSnapshot, delta: String) {
            deltas += delta
        }

        override fun onReasoning(generation: ChatGenerationSnapshot) {
            reasoning += generation.reasoning
        }

        override fun onCompleted(generation: ChatGenerationSnapshot, completion: ChatGenerationCompletion) {
            completions += completion
        }

        override fun onFailed(generation: ChatGenerationSnapshot, failureText: String) {
            failures += generation to failureText
        }

        override fun onStateChanged(active: ChatGenerationSnapshot?) {
            lastState = active
        }

        override fun conversationSnapshot(conversationId: String): ChatConversationSnapshot? =
            if (conversationId == this.conversationId) {
                ChatConversationSnapshot(transcript(), ConversationContextState.EMPTY)
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

    private class MemoryStore : ChatGenerationStore {
        private val conversations = LinkedHashMap<String, StoredConversation>()

        fun put(conversation: StoredConversation) {
            conversations[conversation.id] = conversation
        }

        fun reply(conversationId: String): ChatMessage =
            checkNotNull(conversations[conversationId]).messages.single { message -> message.id == 2L }

        override fun find(conversationId: String): StoredConversation? = conversations[conversationId]

        override fun update(conversation: StoredConversation): Boolean {
            if (conversation.id !in conversations) return false
            conversations[conversation.id] = conversation
            return true
        }

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

        fun await(predicate: (String) -> Boolean): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L)
            while (System.nanoTime() < deadline) {
                if (messages.any(predicate)) return true
                Thread.sleep(5L)
            }
            return messages.any(predicate)
        }
    }

    private class FakeBackend(private val target: AiTarget) : AiBackend {
        val sessions = CopyOnWriteArrayList<FakeSession>()
        private val created = CountDownLatch(1)

        @Volatile
        var blockStreams = false

        override val backendId: String = OnlineAiBackend.BACKEND_ID

        override fun ownsTarget(targetId: String): Boolean = targetId == target.targetId

        override fun catalog(): AiTargetCatalog = throw UnsupportedOperationException()

        override fun createSession(request: AiBackendSessionRequest): AiBackendSession =
            FakeSession(target, blockStreams).also { session ->
                sessions += session
                created.countDown()
            }

        fun awaitSession(): FakeSession {
            check(created.await(2L, TimeUnit.SECONDS))
            return sessions.first()
        }
    }

    private class FakeSession(
        override val target: AiTarget,
        private val blockStreams: Boolean,
    ) : AiBackendSession {
        val streamEntered = CountDownLatch(1)
        val streamReturned = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val cancelledAtNanos = AtomicLong(0L)
        val cancelCount = AtomicInteger(0)
        val closeCount = AtomicInteger(0)

        @Volatile
        var cancelThreadName: String? = null

        lateinit var listener: GenerationListener
        lateinit var request: GenerationRequest

        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            this.request = request
            this.listener = listener
            streamEntered.countDown()
            if (blockStreams) {
                // Like OkHttp's Call.execute(): returns only once the call is cancelled.
                cancelled.await(5L, TimeUnit.SECONDS)
            }
            streamReturned.countDown()
        }

        override fun streamNext(request: GenerationRequest, listener: GenerationListener) = stream(request, listener)

        override fun cancel() {
            if (cancelCount.incrementAndGet() == 1) {
                cancelThreadName = Thread.currentThread().name
                cancelledAtNanos.set(System.nanoTime())
                cancelled.countDown()
            }
        }

        override fun cancellationDiagnostics(): String = "fake=cancelled"

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class DirectExecutorService : AbstractExecutorService() {
        @Volatile
        private var shutdown = false

        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val PROFILE_ID = "profile-one"

        fun remoteTarget() = AiTarget(
            targetId = AiTargetIds.profile(PROFILE_ID),
            backendId = OnlineAiBackend.BACKEND_ID,
            providerId = OnlineAiProvider.OPENAI_COMPATIBLE.providerId,
            profileId = PROFILE_ID,
            modelId = "remote-model",
            displayName = "Remote",
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
                OnlineAiTransportLimits.MAXIMUM_CONTEXT_BYTES,
                OnlineAiTransportLimits.MAXIMUM_OUTPUT_BYTES,
            ),
            executionProfiles = emptyList(),
        )
    }
}
