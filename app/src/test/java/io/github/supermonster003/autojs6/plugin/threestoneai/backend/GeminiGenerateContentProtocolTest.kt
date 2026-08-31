package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiGenerateContentProtocolTest {
    @Test
    fun requestMapsNativeEndpointHeadersContentsControlsAndStructuredOutput() {
        val turn = request(
            maximumOutputTokens = 456,
            samplingOptions = GenerationSamplingOptions(
                temperature = 0.3,
                topK = 23,
                topP = 0.7,
            ),
            responseJsonSchema = """{"type":"object","properties":{"answer":{"type":"string"}}}""",
        )

        GeminiGenerateContentProtocolAdapter.prepare(
            profile = profile(modelId = "models/gemini-test"),
            messages = listOf(
                GenerationMessage(GenerationRole.SYSTEM, listOf("rule ", "one")),
                GenerationMessage(GenerationRole.USER, listOf("earlier question")),
                GenerationMessage(GenerationRole.ASSISTANT, listOf("earlier answer")),
                turn.prompt,
            ),
            turn = turn,
            credential = "gemini-secret".toByteArray(),
        ).use { prepared ->
            val outgoing = prepared.request
            val json = outgoing.bodyJson()

            assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-test:streamGenerateContent?alt=sse",
                outgoing.url.toString(),
            )
            assertEquals("gemini-secret", outgoing.header("x-goog-api-key"))
            assertNull(outgoing.header("Authorization"))

            val systemParts = json.getAsJsonObject("systemInstruction").getAsJsonArray("parts")
            assertEquals("rule one", systemParts.single().asJsonObject.get("text").asString)
            val contents = json.getAsJsonArray("contents")
            assertEquals(listOf("user", "model", "user"), contents.map { element ->
                element.asJsonObject.get("role").asString
            })
            assertEquals(
                "hello",
                contents.last().asJsonObject.getAsJsonArray("parts")
                    .single().asJsonObject.get("text").asString,
            )

            val config = json.getAsJsonObject("generationConfig")
            assertEquals(456, config.get("maxOutputTokens").asInt)
            assertEquals(0.3, config.get("temperature").asDouble, 0.0)
            assertEquals(23, config.get("topK").asInt)
            assertEquals(0.7, config.get("topP").asDouble, 0.0)
            assertEquals("application/json", config.get("responseMimeType").asString)
            assertEquals("object", config.getAsJsonObject("responseSchema").get("type").asString)
            assertFalse(json.toString().contains("gemini-secret"))
        }
    }

    @Test
    fun endpointReplacesExistingGenerateActionAndRejectsAmbiguousModelIds() {
        GeminiGenerateContentProtocolAdapter.prepare(
            profile = profile(
                baseUrl = "https://example.com/v1beta/models/old:generateContent",
                modelId = "new-model",
            ),
            messages = listOf(request().prompt),
            turn = request(),
            credential = "key".toByteArray(),
        ).use { prepared ->
            assertEquals(
                "https://example.com/v1beta/models/new-model:streamGenerateContent?alt=sse",
                prepared.request.url.toString(),
            )
        }

        listOf("models/parent/model", "model:action").forEach { modelId ->
            assertThrows(IllegalArgumentException::class.java) {
                GeminiGenerateContentProtocolAdapter.prepare(
                    profile = profile(modelId = modelId),
                    messages = listOf(request().prompt),
                    turn = request(),
                    credential = "key".toByteArray(),
                )
            }
        }
    }

    @Test
    fun streamParserIgnoresThoughtPartsAndAcceptsProviderSpecificUsageTotals() {
        val first = GeminiGenerateContentProtocolAdapter.parseEvent(
            event(
                """
                {
                  "candidates":[{"content":{"parts":[
                    {"text":"private thought","thought":true},
                    {"text":"Hello"}
                  ]}}],
                  "usageMetadata":{
                    "promptTokenCount":4,
                    "candidatesTokenCount":1,
                    "thoughtsTokenCount":2,
                    "totalTokenCount":7
                  }
                }
                """.trimIndent(),
            ),
        )
        val final = GeminiGenerateContentProtocolAdapter.parseEvent(
            event(
                """
                {
                  "candidates":[{
                    "content":{"parts":[{"text":" world"}]},
                    "finishReason":"STOP"
                  }],
                  "usageMetadata":{
                    "promptTokenCount":4,
                    "candidatesTokenCount":2,
                    "thoughtsTokenCount":2,
                    "totalTokenCount":8
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals("Hello", first.text)
        assertTrue(first.contentSeen)
        assertFalse(first.done)
        assertEquals(OnlineAiUsageUpdate(4L, 1L, 7L), first.usage)
        assertEquals(" world", final.text)
        assertTrue(final.contentSeen)
        assertTrue(final.done)
        assertEquals(OnlineAiUsageUpdate(4L, 2L, 8L), final.usage)
    }

    @Test
    fun usageParsesGeminiCachedContentTokens() {
        val update = GeminiGenerateContentProtocolAdapter.parseEvent(
            event(
                """
                {
                  "candidates":[{"content":{"parts":[{"text":"OK"}]}}],
                  "usageMetadata":{
                    "promptTokenCount":1000,
                    "cachedContentTokenCount":800,
                    "candidatesTokenCount":2,
                    "totalTokenCount":1002
                  }
                }
                """.trimIndent(),
            ),
        ).usage

        assertEquals(
            OnlineAiUsageUpdate(
                inputTokens = 1000L,
                outputTokens = 2L,
                totalTokens = 1002L,
                cachedInputTokens = 800L,
                cacheEligibleInputTokens = 1000L,
            ),
            update,
        )
    }

    @Test
    fun jsonFallbackRequiresTerminalTextAndReturnsUsage() {
        val response = GeminiGenerateContentProtocolAdapter.parseJson(
            """
            {
              "candidates":[{
                "content":{"parts":[{"text":"complete"}]},
                "finishReason":"MAX_TOKENS"
              }],
              "usageMetadata":{
                "promptTokenCount":3,
                "candidatesTokenCount":2,
                "totalTokenCount":5
              }
            }
            """.trimIndent().toResponseBody("application/json".toMediaType()),
        )

        assertEquals("complete", response.text)
        assertEquals(OnlineAiUsageUpdate(3L, 2L, 5L), response.usage)

        assertFailure(OnlineAiFailureReason.INVALID_RESPONSE) {
            GeminiGenerateContentProtocolAdapter.parseJson(
                """{"candidates":[{"content":{"parts":[{"text":"partial"}]}}]}"""
                    .toResponseBody("application/json".toMediaType()),
            )
        }
    }

    @Test
    fun doneSentinelSafetyBlocksAndErrorObjectsFailClosed() {
        assertFailure(OnlineAiFailureReason.INVALID_RESPONSE) {
            GeminiGenerateContentProtocolAdapter.parseEvent(
                OnlineAiSseEvent(event = null, data = "[DONE]", isDone = true),
            )
        }
        assertFailure(OnlineAiFailureReason.PROVIDER_ERROR) {
            GeminiGenerateContentProtocolAdapter.parseEvent(
                event(
                    """{"candidates":[{"finishReason":"SAFETY"}]}""",
                ),
            )
        }
        assertFailure(OnlineAiFailureReason.PROVIDER_ERROR) {
            GeminiGenerateContentProtocolAdapter.parseEvent(
                event("""{"promptFeedback":{"blockReason":"SAFETY"}}"""),
            )
        }
        assertFailure(OnlineAiFailureReason.PROVIDER_ERROR) {
            GeminiGenerateContentProtocolAdapter.parseEvent(
                event("""{"error":{"message":"secret"}}"""),
            )
        }
    }

    private fun profile(
        baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        modelId: String = "gemini-test",
    ) = OnlineAiProfile(
        profileId = "22222222-2222-4222-8222-222222222222",
        displayName = "Gemini",
        provider = OnlineAiProvider.GEMINI,
        baseUrl = baseUrl,
        modelId = modelId,
    )

    private fun request(
        maximumOutputTokens: Int? = null,
        samplingOptions: GenerationSamplingOptions? = null,
        responseJsonSchema: String? = null,
    ) = GenerationRequest(
        history = emptyList(),
        prompt = GenerationMessage(GenerationRole.USER, listOf("hello")),
        maximumOutputTokens = maximumOutputTokens,
        samplingOptions = samplingOptions,
        reportUsage = true,
        responseJsonSchema = responseJsonSchema,
    )

    private fun event(data: String) = OnlineAiSseEvent(
        event = null,
        data = data,
        isDone = false,
    )

    private fun okhttp3.Request.bodyJson(): JsonObject {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return JsonParser.parseString(buffer.readUtf8()).asJsonObject
    }

    private fun assertFailure(reason: OnlineAiFailureReason, action: () -> Unit) {
        val failure = assertThrows(OnlineAiFailureException::class.java) { action() }
        assertEquals(reason, failure.reason)
        assertFalse(failure.toString().contains("secret"))
    }
}
