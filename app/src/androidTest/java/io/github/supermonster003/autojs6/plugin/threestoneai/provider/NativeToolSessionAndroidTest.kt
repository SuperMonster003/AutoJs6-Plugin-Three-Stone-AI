package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.ParcelFileDescriptor
import android.os.Process
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.*
import org.autojs.plugin.ai.common.api.*
import org.autojs.plugin.ai.provider.api.*
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the real session through AIDL proxies with deterministic model and identity fixtures. */
class NativeToolSessionAndroidTest {
    @Test
    fun streamingAndNonStreamingContinuationKeepOneSessionAndCumulativeUsage() {
        for (streaming in listOf(true, false)) Fixture(streaming = streaming).use { fixture ->
            fixture.start()
            fixture.awaitTools()
            assertEquals(1, fixture.started.size)
            assertEquals(2, fixture.started.single().effectiveMaximumToolRounds)
            assertEquals(if (streaming) listOf("Before.") else emptyList<String>(), fixture.chunks.toList())
            assertEquals(5L, fixture.usage.single().totalTokens)
            assertEquals(0, fixture.completions.size)
            fixture.submit()
            fixture.awaitTerminal()
            assertTrue(fixture.failures.isEmpty())
            val result = fixture.completions.single()
            assertEquals("Before.After.", String(requireNotNull(result.output.inlineBytes), Charsets.UTF_8))
            assertEquals(11L, result.usage!!.inputTokens)
            assertEquals(3L, result.usage!!.outputTokens)
            assertEquals(listOf(5L, 14L), fixture.usage.map { it.totalTokens })
            assertEquals(1, fixture.backend.resumes.get())
            assertTrue(fixture.backendClosed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun exactDescriptorPayloadIsReadBeforeContinuing() = Fixture().use { fixture ->
        fixture.start()
        fixture.awaitTools()
        val pipe = ParcelFileDescriptor.createReliablePipe()
        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write("result".toByteArray()) }
        val result = AiToolResult("call-a", AiPayloadReference(AiProviderMimeType.PLAIN, 6, descriptorIndex = 0, charset = "utf-8"))
        try {
            fixture.remote.submitToolResults(AiProviderCodec.encodeToolResultBatch(AiToolResultBatch(listOf(result))), arrayOf(pipe[0]))
            // Reliable-pipe copies share the status socket. Keep the sending read endpoint
            // until completion so its close cannot consume the receiving endpoint's status.
            fixture.awaitTerminal()
        } finally { pipe[0].close() }
        assertTrue(fixture.failures.isEmpty())
        assertEquals("result", fixture.backend.received.single().output)
    }

    @Test
    fun mismatchedAndRepeatedResultsCannotStartAnotherGeneration() = Fixture().use { fixture ->
        fixture.start()
        fixture.awaitTools()
        fixture.submit("wrong-id")
        fixture.awaitTerminal()
        assertEquals(AiErrorCode.PROTOCOL_VIOLATION, fixture.failures.single().code)
        fixture.submit()
        assertEquals(0, fixture.backend.resumes.get())
        assertTrue(fixture.completions.isEmpty())
    }

    @Test
    fun cancellationWhileWaitingEmitsOneTerminalAndClosesBackend() = Fixture().use { fixture ->
        fixture.start()
        fixture.awaitTools()
        fixture.remote.cancel()
        fixture.awaitTerminal()
        fixture.submit()
        assertEquals(1, fixture.cancelled.get())
        assertTrue(fixture.completions.isEmpty())
        assertTrue(fixture.backendClosed.await(5, TimeUnit.SECONDS))
        assertEquals(0, fixture.backend.resumes.get())
    }

    @Test
    fun waitingForToolResultsDoesNotResetOriginalDeadline() = Fixture(timeoutMillis = 1_000).use { fixture ->
        fixture.start()
        fixture.awaitTools()
        fixture.awaitTerminal()
        assertEquals(AiErrorCode.TIMEOUT, fixture.failures.single().code)
        assertTrue(fixture.completions.isEmpty())
        assertTrue(fixture.backendClosed.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun cancellationClosesAnOwnedResultPipeWithoutWaitingForItsWriter() = Fixture().use { fixture ->
        fixture.start()
        fixture.awaitTools()
        val pipe = ParcelFileDescriptor.createReliablePipe()
        try {
            val result = AiToolResult("call-a", AiPayloadReference(AiProviderMimeType.PLAIN, 8, descriptorIndex = 0, charset = "utf-8"))
            fixture.remote.submitToolResults(AiProviderCodec.encodeToolResultBatch(AiToolResultBatch(listOf(result))), arrayOf(pipe[0]))
            pipe[0].close()
            fixture.remote.cancel()
            fixture.awaitTerminal()
            assertTrue(fixture.backendClosed.await(5, TimeUnit.SECONDS))
            assertEquals(1, fixture.cancelled.get())
            assertEquals(0, fixture.backend.resumes.get())
        } finally { pipe.forEach { runCatching { it.close() } } }
    }

    @Test
    fun toolCallbackWaitsForEarlierCreditedText() = Fixture(initialCredits = 0).use { fixture ->
        fixture.start()
        assertTrue(fixture.modelPaused.await(5, TimeUnit.SECONDS))
        assertFalse(fixture.toolsReady.await(150, TimeUnit.MILLISECONDS))
        fixture.remote.grantCredits(2)
        fixture.awaitTools()
        assertEquals(listOf("Before."), fixture.chunks.toList())
        fixture.submit()
        fixture.awaitTerminal()
        assertEquals(1, fixture.completions.size)
    }

    @Test
    fun nativeToolsWithPersistentKvSessionAreRejectedBeforeBackendCreation() = Fixture(persistent = true).use { fixture ->
        fixture.start()
        fixture.awaitTerminal()
        assertEquals(AiErrorCode.UNSUPPORTED_CAPABILITY, fixture.failures.single().code)
        assertEquals(0, fixture.backend.opens.get())
    }

    @Test
    fun missingRequestedUsageFailsBeforeToolExecution() = Fixture(missingUsage = true).use { fixture ->
        fixture.start()
        fixture.awaitTerminal()
        assertEquals(AiErrorCode.PROVIDER_FAILED, fixture.failures.single().code)
        assertEquals(1L, fixture.toolsReady.count)
        assertEquals(0, fixture.backend.resumes.get())
    }

    @Test
    fun outputOrCompletionWhileWaitingIsAProtocolViolation() {
        for (event in listOf("text", "completed")) Fixture(invalidAfterPause = event).use { fixture ->
            fixture.start()
            fixture.awaitTerminal()
            assertEquals(AiErrorCode.PROTOCOL_VIOLATION, fixture.failures.single().code)
            assertTrue(fixture.completions.isEmpty())
            assertEquals(0, fixture.backend.resumes.get())
        }
    }

    @Test
    fun wrongIdsAreRejectedBeforeReadingAStalledPipe() = Fixture().use { fixture ->
        fixture.start()
        fixture.awaitTools()
        val pipe = ParcelFileDescriptor.createReliablePipe()
        try {
            val result = AiToolResult("wrong-id", AiPayloadReference(AiProviderMimeType.PLAIN, 8, descriptorIndex = 0, charset = "utf-8"))
            fixture.remote.submitToolResults(AiProviderCodec.encodeToolResultBatch(AiToolResultBatch(listOf(result))), arrayOf(pipe[0]))
            pipe[0].close()
            fixture.awaitTerminal()
            assertEquals(AiErrorCode.PROTOCOL_VIOLATION, fixture.failures.single().code)
            assertEquals(0, fixture.backend.resumes.get())
        } finally { pipe.forEach { runCatching { it.close() } } }
    }

    private class Fixture(
        streaming: Boolean = true,
        timeoutMillis: Long = 10_000,
        private val initialCredits: Int = 8,
        persistent: Boolean = false,
        missingUsage: Boolean = false,
        invalidAfterPause: String? = null,
    ) : Closeable {
        private val worker = Executors.newFixedThreadPool(3)
        private val timer = Executors.newSingleThreadScheduledExecutor()
        private val lane = SerialCallbackLane()
        val started = CopyOnWriteArrayList<AiSessionStarted>()
        val chunks = CopyOnWriteArrayList<String>()
        val completions = CopyOnWriteArrayList<AiCompletionResult>()
        val failures = CopyOnWriteArrayList<AiError>()
        val usage = CopyOnWriteArrayList<AiUsage>()
        val cancelled = AtomicInteger()
        val toolsReady = CountDownLatch(1)
        val modelPaused = CountDownLatch(1)
        private val terminal = CountDownLatch(1)
        val backendClosed = CountDownLatch(1)
        val backend = FakeBackend(backendClosed, modelPaused, missingUsage, invalidAfterPause)
        private val callback = object : IAiCallback.Stub() {
            override fun onStarted(metadata: ByteArray) { started += AiProviderCodec.decodeSessionStarted(metadata) }
            override fun onChunk(chunk: ByteArray) { chunks += AiProviderCodec.decodeTextChunk(chunk).textDelta.orEmpty() }
            override fun onToolCalls(calls: ByteArray, descriptors: Array<out ParcelFileDescriptor>) {
                assertEquals(listOf("call-a"), AiProviderCodec.decodeToolCallBatch(calls).calls.map { it.callId })
                assertTrue(descriptors.isEmpty())
                toolsReady.countDown()
            }
            override fun onUsage(value: ByteArray) { usage += AiCommonCodec.decodeUsage(value) }
            override fun onCompleted(value: ByteArray, descriptors: Array<out ParcelFileDescriptor>) {
                completions += AiProviderCodec.decodeCompletionResult(value)
                descriptors.forEach { it.close() }
                terminal.countDown()
            }
            override fun onFailed(error: ByteArray) { failures += AiCommonCodec.decodeError(error); terminal.countDown() }
            override fun onCancelled() { cancelled.incrementAndGet(); terminal.countDown() }
        }
        private val request = AiProviderRequest(
            requestId = "native-tools-test", protocolVersion = AiProviderProtocol.HOST_PROTOCOL_RANGE.maximum,
            providerId = ThreeStoneAiPlugin.PROVIDER_ID, targetId = backend.target.targetId,
            messages = listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(payload("Inspect"))))),
            tools = listOf(AiToolDefinition("observe", "Inspect", payload("{\"type\":\"object\"}", AiProviderMimeType.JSON))),
            options = AiGenerationOptions(stream = streaming, includeReasoning = false, structuredJson = false,
                reportUsage = true, maximumOutputBytes = 65536, maximumToolRounds = 2, timeoutMillis = timeoutMillis,
                responseMimeType = AiProviderMimeType.PLAIN, persistentSession = persistent,
                requiredCapabilityIds = if (persistent) listOf(AiProviderCapabilityId.PERSISTENT_SESSION) else emptyList()),
        )
        private val session = RemoteThreeStoneAiSession(Process.myUid(), AiProviderCodec.encodeTextRequest(request),
            OwnedParcelFileDescriptors.duplicateBeforeAsync(emptyArray()),
            IAiCallback.Stub.asInterface(proxy(callback.asBinder())),
            SessionOwnerVerifier { expected -> check(expected == Binder.getCallingUid()) },
            backend, worker, timer, lane) {}
        val remote: IAiSession = IAiSession.Stub.asInterface(proxy(session.asBinder()))
        fun start() {
            session.start()
            if (initialCredits > 0) remote.grantCredits(initialCredits)
        }
        fun awaitTools() { assertTrue("No tool callback; failures=$failures", toolsReady.await(5, TimeUnit.SECONDS)) }
        fun awaitTerminal() { assertTrue("No terminal callback", terminal.await(5, TimeUnit.SECONDS)) }
        fun submit(id: String = "call-a") = remote.submitToolResults(
            AiProviderCodec.encodeToolResultBatch(AiToolResultBatch(listOf(AiToolResult(id, payload("ok"))))), emptyArray())
        override fun close() {
            session.serviceDestroyed()
            lane.close()
            worker.shutdownNow()
            timer.shutdownNow()
        }
    }

    private class FakeBackend(
        private val closed: CountDownLatch,
        private val paused: CountDownLatch,
        private val missingUsage: Boolean,
        private val invalidAfterPause: String?,
    ) : AiBackend, AiBackendSession {
        override val backendId = "native-tool-test"
        override val target = AiTarget("profile:test", backendId, "openai-compatible", "test", "model", "Fixture",
            AiTargetLocality.REMOTE, AiTargetCredentialMode.PLUGIN_MANAGED, listOf("https://example.com"), true, true,
            AiTargetCapabilities(true, true, true, true, false, true), AiTargetLimits(262144, 65536), emptyList())
        val opens = AtomicInteger()
        val resumes = AtomicInteger()
        var received = emptyList<GenerationToolResult>()
        private lateinit var listener: GenerationListener
        override fun ownsTarget(targetId: String) = targetId == target.targetId
        override fun catalog() = AiTargetCatalog("test", target.targetId, listOf(target))
        override fun createSession(request: AiBackendSessionRequest): AiBackendSession { opens.incrementAndGet(); return this }
        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            this.listener = listener
            check(request.tools.single().name == "observe")
            listener.onTextDelta("Before.")
            listener.onToolCalls(listOf(GenerationToolCall("call-a", "observe", "{}")),
                if (missingUsage) null else GenerationStatistics(3, 2, 7))
            paused.countDown()
            if (invalidAfterPause == "text") listener.onTextDelta("Invalid")
            if (invalidAfterPause == "completed") listener.onCompleted(GenerationStatistics(3, 2, 7))
        }
        override fun submitToolResults(results: List<GenerationToolResult>) {
            received = results
            resumes.incrementAndGet()
            listener.onTextDelta("After.")
            listener.onCompleted(GenerationStatistics(8, 1, 11))
        }
        override fun cancel() = Unit
        override fun close() { closed.countDown() }
    }

    private companion object {
        fun payload(value: String, mime: String = AiProviderMimeType.PLAIN): AiPayloadReference = value.toByteArray().let {
            AiPayloadReference(mime, it.size.toLong(), inlineBytes = it, charset = "utf-8")
        }
        fun proxy(binder: IBinder): IBinder = object : IBinder by binder {
            override fun queryLocalInterface(descriptor: String): IInterface? = null
        }
    }
}
