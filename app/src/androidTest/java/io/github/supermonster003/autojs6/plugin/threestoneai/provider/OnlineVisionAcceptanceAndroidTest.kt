package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiApplication
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.*
import org.autojs.plugin.ai.common.api.*
import org.autojs.plugin.ai.provider.api.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Explicit opt-in only: never runs a paid network request during normal connected tests. */
class OnlineVisionAcceptanceAndroidTest {
    @Test
    fun listConfiguredTargetsWithoutCredentials() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("listVisionTargets") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ThreeStoneAiApplication
        val rows = app.onlineBackend.catalog().targets.map { "${it.targetId} | ${it.displayName} | ${it.modelId} | configured=${it.configured}" }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("visionTargets", rows.joinToString("\n")) })
    }

    @Test
    fun selectedOnlineModelReadsSyntheticImageThroughProviderSession() {
        val args = InstrumentationRegistry.getArguments()
        val targetId = args.getString("realVisionTarget")
        assumeTrue("Explicit target required for real online acceptance", targetId != null)
        val expectedModel = requireNotNull(args.getString("realVisionModel"))
        val mode = requireNotNull(args.getString("realVisionMode"))
        val maximumTokens = (args.getString("realVisionMaxTokens") ?: "1024").toLong().also { require(it in 512..4096) }
        require(mode in listOf("initial", "tool"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ThreeStoneAiApplication
        val selected = app.onlineBackend.catalog().requireTarget(requireNotNull(targetId))
        require(selected.modelId == expectedModel && selected.configured && selected.available)
        val registry = app.onlineProfileRegistry
        val stored = registry.snapshot().profiles.single { it.profile.profileId == selected.profileId }.profile
        // The explicit test target enables images only for this in-memory probe. Do not change
        // saved profiles, metered-network consent, credentials, or the user's default model.
        val profile = stored.copy(modelId = expectedModel, visionModelIds = (stored.visionModelIds + expectedModel).distinct())
        val answer = (100000 + SecureRandom().nextInt(900000)).toString()
        val bitmap = Bitmap.createBitmap(512, 192, Bitmap.Config.ARGB_8888)
        val bytes = try {
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawText(answer, 32f, 132f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK; textSize = 96f; typeface = Typeface.MONOSPACE
                })
            }
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        } finally { bitmap.recycle() }
        val image = AiContentPart(AiPayloadReference("image/jpeg", bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) },
            descriptorIndex = 0), AiImageMetadata(512, 192))
        fun descriptor(): ParcelFileDescriptor {
            val file = File.createTempFile("vision-probe-", ".jpg", context.cacheDir)
            try { file.writeBytes(bytes); return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) } finally { file.delete() }
        }
        fun text(value: String, mime: String = "text/plain") = value.toByteArray().let { AiPayloadReference(mime, it.size.toLong(), inlineBytes = it, charset = "utf-8") }
        val execution = OnlineAiHttpExecution.create(AndroidOnlineAiNetworkAccess(context) { registry.settings().allowMeteredNetwork })
        val backend = object : AiBackend {
            override val backendId = "online-vision-acceptance"
            val target = selected.copy(capabilities = execution.capabilities(profile))
            override fun ownsTarget(targetId: String) = targetId == target.targetId
            override fun catalog() = AiTargetCatalog("vision-probe", target.targetId, listOf(target))
            override fun createSession(request: AiBackendSessionRequest): AiBackendSession =
                execution.createSession(target, profile, registry.bindCredential(stored))
        }
        val native = mode == "tool"
        val prompt = if (native) "Call observe_image exactly once. Then reply with only the six digits visible in its returned image. Do not guess before observing."
            else "Read the six digits in the attached image. Reply with only those six digits."
        val request = AiProviderRequest("real-vision-$mode", AiProviderProtocol.PROTOCOL_V2_1, ThreeStoneAiPlugin.PROVIDER_ID, selected.targetId,
            listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(text(prompt))) + if (native) emptyList() else listOf(image))),
            tools = if (native) listOf(AiToolDefinition("observe_image", "Return an image containing six digits", text("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}", "application/json"))) else emptyList(),
            options = AiGenerationOptions(stream = false, includeReasoning = false, structuredJson = false, reportUsage = true,
                maximumOutputBytes = 8192, maximumOutputTokens = maximumTokens, maximumToolRounds = if (native) 1 else 0,
                timeoutMillis = 120_000, responseMimeType = "text/plain", requiredCapabilityIds = listOf(AiProviderCapabilityId.VISION)))
        val worker = Executors.newFixedThreadPool(3)
        val timer = Executors.newSingleThreadScheduledExecutor()
        val lane = SerialCallbackLane()
        val terminal = CountDownLatch(1)
        val calls = AtomicInteger()
        var failure: Int? = null
        var result: AiCompletionResult? = null
        var session: RemoteThreeStoneAiSession? = null
        val callback = object : IAiCallback.Stub() {
            override fun onStarted(metadata: ByteArray) = Unit
            override fun onChunk(metadata: ByteArray) = Unit
            override fun onUsage(metadata: ByteArray) = Unit
            override fun onToolCalls(metadata: ByteArray, descriptors: Array<out ParcelFileDescriptor>) {
                descriptors.forEach { it.close() }
                val call = AiProviderCodec.decodeToolCallBatch(metadata).calls.single()
                require(call.name == "observe_image"); check(calls.incrementAndGet() == 1)
                requireNotNull(session).submitToolResults(AiProviderCodec.encodeToolResultBatch(AiToolResultBatch(listOf(
                    AiToolResult(call.callId, text("Image captured"), imageParts = listOf(image))))), arrayOf(descriptor()))
            }
            override fun onCompleted(metadata: ByteArray, descriptors: Array<out ParcelFileDescriptor>) {
                descriptors.forEach { it.close() }; result = AiProviderCodec.decodeCompletionResult(metadata); terminal.countDown()
            }
            override fun onFailed(metadata: ByteArray) { failure = AiCommonCodec.decodeError(metadata).code; terminal.countDown() }
            override fun onCancelled() { failure = AiErrorCode.CANCELLED; terminal.countDown() }
        }
        val start = android.os.SystemClock.elapsedRealtime()
        try {
            session = RemoteThreeStoneAiSession(Process.myUid(), AiProviderCodec.encodeTextRequest(request),
                OwnedParcelFileDescriptors.duplicateBeforeAsync(if (native) emptyArray() else arrayOf(descriptor())), callback,
                SessionOwnerVerifier { check(it == Binder.getCallingUid()) }, backend, worker, timer, lane) {}
            requireNotNull(session).apply { start(); grantCredits(8) }
            assertTrue("Online vision deadline expired", terminal.await(125, TimeUnit.SECONDS))
            assertNull("Provider error code: $failure", failure)
            val completed = requireNotNull(result)
            val output = AiValidation.decodeUtf8(requireNotNull(completed.output.inlineBytes)).trim()
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("visionMode", mode); putString("visionModel", expectedModel)
                putLong("visionElapsedMs", android.os.SystemClock.elapsedRealtime() - start)
                putLong("visionInputTokens", completed.usage?.inputTokens ?: -1)
                putLong("visionOutputTokens", completed.usage?.outputTokens ?: -1)
                putInt("visionOutputCharacters", output.length); putInt("visionToolCalls", calls.get())
                putLong("visionMaximumTokens", maximumTokens)
                putBoolean("visionMatched", answer == output)
            })
            assertEquals("Image digits were not read correctly", answer, output)
            assertEquals(if (native) 1 else 0, calls.get())
        } finally {
            session?.serviceDestroyed()
            bytes.fill(0); worker.shutdownNow(); timer.shutdownNow(); lane.close(); execution.close()
        }
    }
}
