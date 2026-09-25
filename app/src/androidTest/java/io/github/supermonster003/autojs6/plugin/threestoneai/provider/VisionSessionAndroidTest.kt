package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.*
import org.autojs.plugin.ai.common.api.*
import org.autojs.plugin.ai.provider.api.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real Android codecs, AIDL proxy marshalling, fd ownership and worker cancellation. No network. */
class VisionSessionAndroidTest {
    @Test
    fun pngAndJpegArriveAsImagesAndV20StillAcceptsText() {
        for (mime in listOf("image/png", "image/jpeg")) {
            val bytes = image(mime)
            Fixture(listOf(part(bytes, mime)), arrayOf(file(bytes))).use { f ->
                f.start(); f.awaitTerminal()
                assertTrue(f.errors.toString(), f.errors.isEmpty())
                assertEquals(1, f.backend.initial.single().images.size)
                assertEquals(mime, f.backend.initial.single().images.single().mimeType)
                assertEquals(listOf("Inspect"), f.backend.initial.single().textParts)
            }
        }
        Fixture(version = AiProviderProtocol.PROTOCOL_V2, requestVision = false).use { f ->
            f.start(); f.awaitTerminal(); assertTrue(f.errors.isEmpty()); assertEquals(1, f.backend.opens.get())
        }
    }

    @Test
    fun nativeToolImagesUseTheSameSessionWithMixedTextAndImageDescriptors() {
        for (stream in listOf(true, false)) Fixture(tools = true, stream = stream).use { f ->
            f.start(); f.awaitTools()
            val bytes = image("image/png")
            val text = "captured".toByteArray()
            val result = AiToolResult("call-a", AiPayloadReference("text/plain", text.size.toLong(), descriptorIndex = 0, charset = "utf-8"),
                imageParts = listOf(part(bytes, index = 1)))
            f.remote.submitToolResults(AiProviderCodec.encodeToolResultBatch(AiToolResultBatch(listOf(result))), arrayOf(file(text), file(bytes)))
            f.awaitTerminal()
            assertTrue(f.errors.toString(), f.errors.isEmpty())
            assertEquals(1, f.backend.opens.get()); assertEquals(1, f.backend.resumes.get())
            assertEquals("captured", f.backend.results.single().output)
            assertEquals(1, f.backend.results.single().images.size)
            assertEquals(11L, f.completions.single().usage?.inputTokens)
        }
    }

    @Test
    fun malformedImageHashDimensionsMimeEncodingAndLengthFailBeforeBackend() {
        val bytes = image("image/png")
        val invalid = listOf(
            part(bytes, hash = "00".repeat(32)) to bytes,
            part(bytes, width = 31) to bytes,
            part(bytes, mime = "image/jpeg") to bytes,
            part(byteArrayOf(1, 2, 3)) to byteArrayOf(1, 2, 3),
            part(bytes) to bytes.dropLast(1).toByteArray(),
            part(bytes) to (bytes + byteArrayOf(0)),
        )
        for ((declared, actual) in invalid) Fixture(listOf(declared), arrayOf(file(actual))).use { f ->
            f.start(); f.awaitTerminal()
            assertTrue(f.errors.isNotEmpty()); assertEquals(0, f.backend.opens.get())
        }
    }

    @Test
    fun unsupportedTargetsAndPersistentVisionRejectUnwrittenPipesWithoutWaiting() {
        for (unsupported in listOf(true, false)) {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            try {
                Fixture(listOf(part(byteArrayOf(1))), arrayOf(pipe[0]), targetVision = !unsupported, persistent = !unsupported).use { f ->
                    f.start(); f.awaitTerminal()
                    assertTrue(f.errors.isNotEmpty()); assertEquals(0, f.backend.opens.get())
                }
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
    }

    @Test
    fun cancellationAndTimeoutReleaseWorkersWaitingForInitialOrToolImageBytes() {
        for (tool in listOf(false, true)) for (cancel in listOf(false, true)) {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            try {
                Fixture(if (tool) emptyList() else listOf(part(byteArrayOf(1))), if (tool) emptyArray() else arrayOf(pipe[0]),
                    tools = tool, timeout = if (cancel) 10_000 else 1_000).use { f ->
                    f.start()
                    if (tool) {
                        f.awaitTools()
                        f.remote.submitToolResults(AiProviderCodec.encodeToolResultBatch(AiToolResultBatch(listOf(
                            AiToolResult("call-a", text("captured"), imageParts = listOf(part(byteArrayOf(1))))))), arrayOf(pipe[0]))
                    }
                    if (cancel) { Thread.sleep(100); f.remote.cancel() }
                    f.awaitTerminal()
                    if (cancel) assertEquals(1, f.cancelled.get()) else assertEquals(AiErrorCode.TIMEOUT, f.errors.single().code)
                    assertEquals(0, f.backend.resumes.get())
                    f.assertWorkersFinish()
                }
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
    }

    @Test
    fun undeclaredVisionAndMismatchedResultIdsRejectImagesBeforeReading() {
        for (optIn in listOf(false, true)) Fixture(tools = true, requestVision = optIn).use { f ->
            f.start(); f.awaitTools()
            val pipe = ParcelFileDescriptor.createReliablePipe()
            try {
                val result = AiToolResult(if (optIn) "wrong" else "call-a", text("captured"), imageParts = listOf(part(byteArrayOf(1))))
                f.remote.submitToolResults(AiProviderCodec.encodeToolResultBatch(AiToolResultBatch(listOf(result))), arrayOf(pipe[0]))
                f.awaitTerminal(); assertEquals(AiErrorCode.PROTOCOL_VIOLATION, f.errors.single().code)
                assertEquals(0, f.backend.resumes.get()); f.assertWorkersFinish()
            } finally { pipe.forEach { runCatching { it.close() } } }
        }
    }

    @Test
    fun regularFileOffsetsAndDeclaredSizeArePreserved() {
        val bytes = image("image/png")
        val fd = file(byteArrayOf(0, 1, 2) + bytes)
        android.system.Os.lseek(fd.fileDescriptor, 3, android.system.OsConstants.SEEK_SET)
        Fixture(listOf(part(bytes)), arrayOf(fd)).use { f ->
            f.start(); f.awaitTerminal(); assertTrue(f.errors.toString(), f.errors.isEmpty())
        }
    }

    @Test
    fun reliablePipeProducerFailureIsNotLostWhenOwnershipIsTransferred() {
        val bytes = image("image/png")
        val pipe = ParcelFileDescriptor.createReliablePipe()
        try {
            android.system.Os.write(pipe[1].fileDescriptor, bytes, 0, bytes.size)
            pipe[1].closeWithError("synthetic producer failure")
            Fixture(listOf(part(bytes)), arrayOf(pipe[0])).use { f ->
                f.start(); f.awaitTerminal()
                assertTrue(f.errors.isNotEmpty()); assertEquals(0, f.backend.opens.get())
            }
        } finally { pipe.forEach { runCatching { it.close() } } }
    }

    @Test
    fun reliableProducerFailureSurvivesToolResultAidlMarshalling() = Fixture(tools = true).use { f ->
        f.start(); f.awaitTools()
        val pipe = ParcelFileDescriptor.createReliablePipe()
        try {
            val bytes = image("image/png")
            android.system.Os.write(pipe[1].fileDescriptor, bytes, 0, bytes.size)
            pipe[1].closeWithError("synthetic failure after image bytes")
            val result = AiToolResult("call-a", text("captured"), imageParts = listOf(part(bytes)))
            f.remote.submitToolResults(AiProviderCodec.encodeToolResultBatch(AiToolResultBatch(listOf(result))), arrayOf(pipe[0]))
            f.awaitTerminal()
            assertEquals(AiErrorCode.PROTOCOL_VIOLATION, f.errors.single().code)
            assertEquals(0, f.backend.resumes.get())
        } finally { pipe.forEach { runCatching { it.close() } } }
    }

    private class Fixture(
        images: List<AiContentPart> = emptyList(), incoming: Array<ParcelFileDescriptor> = emptyArray(),
        tools: Boolean = false, stream: Boolean = true, requestVision: Boolean = true, targetVision: Boolean = true,
        persistent: Boolean = false, timeout: Long = 10_000, version: AiProtocolVersion = AiProviderProtocol.PROTOCOL_V2_1,
    ) : Closeable {
        private val worker = Executors.newFixedThreadPool(3)
        private val timer = Executors.newSingleThreadScheduledExecutor()
        private val lane = SerialCallbackLane()
        val errors = CopyOnWriteArrayList<AiError>()
        val completions = CopyOnWriteArrayList<AiCompletionResult>()
        val cancelled = AtomicInteger()
        private val terminal = CountDownLatch(1)
        private val toolReady = CountDownLatch(1)
        val backend = Backend(targetVision)
        private val callback = object : IAiCallback.Stub() {
            override fun onStarted(metadata: ByteArray) = Unit
            override fun onChunk(metadata: ByteArray) = Unit
            override fun onUsage(metadata: ByteArray) = Unit
            override fun onToolCalls(metadata: ByteArray, descriptors: Array<out ParcelFileDescriptor>) { toolReady.countDown() }
            override fun onCompleted(metadata: ByteArray, descriptors: Array<out ParcelFileDescriptor>) {
                completions += AiProviderCodec.decodeCompletionResult(metadata); descriptors.forEach { it.close() }; terminal.countDown()
            }
            override fun onFailed(metadata: ByteArray) { errors += AiCommonCodec.decodeError(metadata); terminal.countDown() }
            override fun onCancelled() { cancelled.incrementAndGet(); terminal.countDown() }
        }
        private val request = AiProviderRequest("vision-test", version, ThreeStoneAiPlugin.PROVIDER_ID, backend.target.targetId,
            listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(text("Inspect"))) + images)),
            tools = if (tools) listOf(AiToolDefinition("observe", "Inspect", text("{\"type\":\"object\"}", "application/json"))) else emptyList(),
            options = AiGenerationOptions(stream = stream, includeReasoning = false, structuredJson = false, reportUsage = true,
                maximumOutputBytes = 65536, maximumToolRounds = if (tools) 2 else 0, timeoutMillis = timeout, responseMimeType = "text/plain",
                requiredCapabilityIds = if (requestVision) listOf(AiProviderCapabilityId.VISION) else emptyList(), persistentSession = persistent))
        private val session = RemoteThreeStoneAiSession(Process.myUid(), AiProviderCodec.encodeTextRequest(request),
            OwnedParcelFileDescriptors.duplicateBeforeAsync(incoming), IAiCallback.Stub.asInterface(proxy(callback.asBinder())),
            SessionOwnerVerifier { check(it == Binder.getCallingUid()) }, backend, worker, timer, lane) {}
        val remote: IAiSession = IAiSession.Stub.asInterface(proxy(session.asBinder()))
        fun start() { session.start(); remote.grantCredits(8) }
        fun awaitTerminal() { assertTrue("No terminal callback", terminal.await(5, TimeUnit.SECONDS)) }
        fun awaitTools() { assertTrue("No tools: $errors", toolReady.await(5, TimeUnit.SECONDS)) }
        fun assertWorkersFinish() { worker.shutdown(); assertTrue("Descriptor worker is still blocked", worker.awaitTermination(2, TimeUnit.SECONDS)) }
        override fun close() { session.serviceDestroyed(); lane.close(); worker.shutdownNow(); timer.shutdownNow() }
    }

    private class Backend(vision: Boolean) : AiBackend, AiBackendSession {
        override val backendId = "vision-test"
        override val target = AiTarget("profile:test", backendId, "openai-compatible", "test", "model", "Test",
            AiTargetLocality.REMOTE, AiTargetCredentialMode.PLUGIN_MANAGED, listOf("https://example.com"), true, true,
            AiTargetCapabilities(true, true, true, true, false, true, vision), AiTargetLimits(262144, 65536), emptyList())
        val opens = AtomicInteger(); val resumes = AtomicInteger()
        var initial = emptyList<GenerationMessage>(); var results = emptyList<GenerationToolResult>()
        private lateinit var listener: GenerationListener
        override fun ownsTarget(targetId: String) = targetId == target.targetId
        override fun catalog() = AiTargetCatalog("test", target.targetId, listOf(target))
        override fun createSession(request: AiBackendSessionRequest): AiBackendSession { opens.incrementAndGet(); return this }
        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            this.listener = listener; initial = request.history + request.prompt
            if (request.tools.isNotEmpty()) listener.onToolCalls(listOf(GenerationToolCall("call-a", "observe", "{}")), GenerationStatistics(3, 2, 1))
            else { listener.onTextDelta("seen"); listener.onCompleted(GenerationStatistics(3, 2, 1)) }
        }
        override fun submitToolResults(results: List<GenerationToolResult>) {
            this.results = results; resumes.incrementAndGet(); listener.onTextDelta("seen"); listener.onCompleted(GenerationStatistics(8, 1, 1))
        }
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private companion object {
        fun image(mime: String): ByteArray {
            val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
            return try { ByteArrayOutputStream().also { bitmap.compress(if (mime == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray() }
            finally { bitmap.recycle() }
        }
        fun part(bytes: ByteArray, mime: String = "image/png", index: Int = 0, width: Int = 32,
            hash: String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }) =
            AiContentPart(AiPayloadReference(mime, bytes.size.toLong(), sha256 = hash, descriptorIndex = index), AiImageMetadata(width, 24))
        fun file(bytes: ByteArray): ParcelFileDescriptor {
            val file = File.createTempFile("vision-test-", ".bin", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
            try { file.writeBytes(bytes); return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) } finally { file.delete() }
        }
        fun text(value: String, mime: String = "text/plain") = value.toByteArray().let { AiPayloadReference(mime, it.size.toLong(), inlineBytes = it, charset = "utf-8") }
        fun proxy(binder: IBinder): IBinder = object : IBinder by binder { override fun queryLocalInterface(descriptor: String): IInterface? = null }
    }
}
