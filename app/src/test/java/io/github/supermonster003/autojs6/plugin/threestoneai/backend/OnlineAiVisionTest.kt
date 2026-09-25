package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.*
import io.github.supermonster003.autojs6.plugin.threestoneai.provider.TargetPager
import okhttp3.Call
import okio.Buffer
import org.autojs.plugin.ai.provider.api.*
import org.junit.Assert.*
import org.junit.Test

class OnlineAiVisionTest {
    @Test
    fun imageCapabilitiesAreExactModelOptInsAndOldProfilesStayTextOnly() {
        val source = profile(OnlineAiProvider.OPENAI_COMPATIBLE).copy(modelIds = listOf("model", "text-model"))
        val encoded = OnlineAiProfileCodec.encode(OnlineAiProfileDocument(1, listOf(source))).toString(Charsets.UTF_8)
        val schema3 = encoded.replace("\"schema\":4", "\"schema\":3").replace(",\"visionModelIds\":[\"model\"]", "")
        val migrated = OnlineAiProfileCodec.decode(schema3.toByteArray()).profiles.single()
        assertTrue(migrated.visionModelIds.isEmpty())
        val execution = OnlineAiHttpExecution(Call.Factory { error("Must not call HTTP") })
        assertTrue(execution.capabilities(source).vision)
        assertFalse(execution.capabilities(source.copy(modelId = "text-model")).vision)
        assertFalse(execution.capabilities(migrated).vision)
        assertEquals(source, OnlineAiProfileCodec.decode(encoded.toByteArray()).profiles.single())
        for (ids in listOf(listOf("absent"), listOf("model", "model"))) {
            assertThrows(IllegalArgumentException::class.java) { OnlineAiProfilePolicy.normalizeProfile(source.copy(visionModelIds = ids)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfileCodec.decode(encoded.replace("\"schema\":4", "\"schema\":3").toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfileCodec.decode(encoded.replace(",\"visionModelIds\":[\"model\"]", "").toByteArray())
        }
    }

    @Test
    fun initialImagesHaveProtocolNativeShapeAndTextOnlyRequestsKeepTheirShape() {
        for (provider in providers) {
            val message = GenerationMessage(GenerationRole.USER, listOf("inspect"), images = listOf(image()))
            val json = prepare(provider, listOf(message))
            val parts = when (provider) {
                OnlineAiProvider.GEMINI -> json.getAsJsonArray("contents")[0].asJsonObject.getAsJsonArray("parts")
                else -> json.getAsJsonArray("messages")[0].asJsonObject.getAsJsonArray("content")
            }
            assertEquals("inspect", parts[0].asJsonObject.get("text").asString)
            val imagePart = parts[1].asJsonObject
            when (provider) {
                OnlineAiProvider.OPENAI_COMPATIBLE -> assertEquals("data:image/png;base64,AQID", imagePart.getAsJsonObject("image_url").get("url").asString)
                OnlineAiProvider.ANTHROPIC -> assertEquals("AQID", imagePart.getAsJsonObject("source").get("data").asString)
                else -> assertEquals("AQID", imagePart.getAsJsonObject("inlineData").get("data").asString)
            }
            val text = prepare(provider, listOf(message.copy(images = emptyList())))
            if (provider != OnlineAiProvider.GEMINI) assertTrue(text.getAsJsonArray("messages")[0].asJsonObject.get("content").isJsonPrimitive)
            assertThrows(IllegalArgumentException::class.java) { prepare(provider, listOf(message), enabled = false) }
            assertThrows(IllegalArgumentException::class.java) {
                prepare(provider, listOf(message.copy(role = GenerationRole.SYSTEM), message.copy(images = emptyList())))
            }
        }
    }

    @Test
    fun parallelToolImagesFollowCallOrderWithoutPuttingBase64InRetainedText() {
        val calls = listOf(GenerationToolCall("first", "observe", "{}"), GenerationToolCall("second", "observe", "{}"))
        for (provider in providers) {
            val prompt = OnlineAiTools.resultsMessage(provider.protocol, calls,
                listOf(GenerationToolResult("second", "failed", true, listOf(image())), GenerationToolResult("first", "ok")))
            val native = requireNotNull(prompt.nativeToolMessage)
            assertFalse(native.json.contains("AQID"))
            assertEquals(1, native.imageResults.single().resultIndex)
            val json = prepare(provider, listOf(prompt))
            when (provider) {
                OnlineAiProvider.OPENAI_COMPATIBLE -> {
                    val messages = json.getAsJsonArray("messages")
                    assertEquals(listOf("tool", "tool", "user"), messages.map { it.asJsonObject.get("role").asString })
                    assertEquals("second", messages[1].asJsonObject.get("tool_call_id").asString)
                    assertTrue(messages[1].asJsonObject.get("content").isJsonPrimitive)
                    assertTrue(messages[2].asJsonObject.getAsJsonArray("content")[0].asJsonObject.get("text").asString.contains("second"))
                }
                OnlineAiProvider.ANTHROPIC -> {
                    val results = json.getAsJsonArray("messages")[0].asJsonObject.getAsJsonArray("content")
                    assertTrue(results[0].asJsonObject.get("content").isJsonPrimitive)
                    assertTrue(results[1].asJsonObject.get("is_error").asBoolean)
                    assertEquals("image", results[1].asJsonObject.getAsJsonArray("content")[1].asJsonObject.get("type").asString)
                }
                else -> {
                    val parts = json.getAsJsonArray("contents")[0].asJsonObject.getAsJsonArray("parts")
                    assertTrue(parts[0].asJsonObject.has("functionResponse"))
                    assertTrue(parts[1].asJsonObject.has("functionResponse"))
                    assertEquals("failed", parts[1].asJsonObject.getAsJsonObject("functionResponse").getAsJsonObject("response").get("error").asString)
                    assertTrue(parts[3].asJsonObject.has("inlineData"))
                }
            }
        }
    }

    @Test
    fun imageBytesDoNotConsumeTextQuotaAndHistoryQuotasStillApply() {
        val large = GenerationImage(ByteArray(700_000) { 7 }, "image/jpeg", AiImageMetadata(1280, 720))
        val prompt = OnlineAiTools.resultsMessage(OnlineAiProtocol.OPENAI_COMPATIBLE,
            listOf(GenerationToolCall("a", "observe", "{}")), listOf(GenerationToolResult("a", "ok", images = listOf(large))))
        assertTrue(OnlineAiRequestSupport.requireConversation(listOf(prompt)) < 256)
        assertTrue(prepare(OnlineAiProvider.OPENAI_COMPATIBLE, listOf(prompt)).toString().length > 700_000)
        assertThrows(IllegalArgumentException::class.java) {
            prepare(OnlineAiProvider.OPENAI_COMPATIBLE, List(17) { prompt })
        }
        assertThrows(IllegalArgumentException::class.java) {
            prepare(OnlineAiProvider.OPENAI_COMPATIBLE, listOf(GenerationMessage(GenerationRole.USER,
                listOf("x".repeat(262145)), images = listOf(image()))))
        }
        val bytes = byteArrayOf(1, 2, 3)
        val immutable = GenerationImage(bytes, "image/png", AiImageMetadata(32, 24))
        bytes.fill(0)
        assertEquals("AQID", immutable.base64())
        assertFalse(immutable.toString().contains("AQID"))
    }

    @Test
    fun targetPagesStripVisionForV20OnEveryPage() {
        val target = AiTarget("profile:test", "online", "openai-compatible", "test", "model", "Test",
            AiTargetLocality.REMOTE, AiTargetCredentialMode.PLUGIN_MANAGED, listOf("https://example.com"), true, true,
            AiTargetCapabilities(true, true, true, true, false, true, vision = true), AiTargetLimits(262144, 65536), emptyList())
        val pager = TargetPager({ AiTargetCatalog("generation", target.targetId, listOf(target, target.copy(targetId = "profile:other", profileId = "other"))) })
        for (version in listOf(AiProviderProtocol.PROTOCOL_V2, AiProviderProtocol.PROTOCOL_V2_1)) {
            val first = pager.page(AiTargetListRequest(version, 1))
            val second = pager.page(AiTargetListRequest(version, 1, first.nextPageToken))
            for (page in listOf(first, second)) assertEquals(version == AiProviderProtocol.PROTOCOL_V2_1,
                AiProviderCapabilityId.VISION in page.targets.single().capabilityIds)
        }
    }

    private fun prepare(provider: OnlineAiProvider, messages: List<GenerationMessage>, enabled: Boolean = true): JsonObject {
        val profile = profile(provider).let { if (enabled) it else it.copy(visionModelIds = emptyList()) }
        val turn = GenerationRequest(messages.dropLast(1), messages.last(), 100, null, true)
        return OnlineAiProtocolAdapters.forProfile(profile).prepare(profile, messages, turn, "test-only".toByteArray()).use { prepared ->
            val body = checkNotNull(prepared.request.body)
            val result = Buffer().also(body::writeTo).readUtf8()
            JsonParser.parseString(result).asJsonObject
        }
    }

    private fun profile(provider: OnlineAiProvider) = OnlineAiProfile("11111111-1111-4111-8111-111111111111", "Vision", provider,
        "https://example.com/v1", "model", visionModelIds = listOf("model"))
    private fun image() = GenerationImage(byteArrayOf(1, 2, 3), "image/png", AiImageMetadata(32, 24))
    private val providers = listOf(OnlineAiProvider.OPENAI_COMPATIBLE, OnlineAiProvider.ANTHROPIC, OnlineAiProvider.GEMINI)
}
