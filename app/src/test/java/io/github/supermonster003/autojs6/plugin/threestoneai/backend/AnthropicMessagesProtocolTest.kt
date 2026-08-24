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

class AnthropicMessagesProtocolTest {
    @Test
    fun requestMapsNativeEndpointHeadersMessagesControlsAndStructuredOutput() {
        val turn = request(
            maximumOutputTokens = 321,
            samplingOptions = GenerationSamplingOptions(
                temperature = 0.2,
                topK = 17,
                topP = 0.8,
            ),
            reportUsage = true,
            responseJsonSchema = """{"type":"object","required":["answer"]}""",
        )

        AnthropicMessagesProtocolAdapter.prepare(
            profile = profile(),
            messages = listOf(
                GenerationMessage(GenerationRole.SYSTEM, listOf("rule ", "one")),
                GenerationMessage(GenerationRole.USER, listOf("earlier question")),
                GenerationMessage(GenerationRole.ASSISTANT, listOf("earlier answer")),
                turn.prompt,
            ),
            turn = turn,
            credential = "anthropic-secret".toByteArray(),
        ).use { prepared ->
            val outgoing = prepared.request
            val json = outgoing.bodyJson()

            assertEquals("https://api.anthropic.com/v1/messages", outgoing.url.toString())
            assertEquals("anthropic-secret", outgoing.header("x-api-key"))
            assertEquals("2023-06-01", outgoing.header("anthropic-version"))
            assertNull(outgoing.header("Authorization"))
            assertEquals("claude-test", json.get("model").asString)
            assertTrue(json.get("stream").asBoolean)
            assertEquals(321, json.get("max_tokens").asInt)
            assertEquals(0.2, json.get("temperature").asDouble, 0.0)
            assertEquals(17, json.get("top_k").asInt)
            assertEquals(0.8, json.get("top_p").asDouble, 0.0)

            val system = json.getAsJsonArray("system")
            assertEquals(1, system.size())
            assertEquals("text", system[0].asJsonObject.get("type").asString)
            assertEquals("rule one", system[0].asJsonObject.get("text").asString)
            val messages = json.getAsJsonArray("messages")
            assertEquals(listOf("user", "assistant", "user"), messages.map { element ->
                element.asJsonObject.get("role").asString
            })
            assertEquals("hello", messages.last().asJsonObject.get("content").asString)

            val format = json.getAsJsonObject("output_config").getAsJsonObject("format")
            assertEquals("json_schema", format.get("type").asString)
            assertEquals("object", format.getAsJsonObject("schema").get("type").asString)
            assertFalse(json.toString().contains("anthropic-secret"))
        }
    }

    @Test
    fun requestSuppliesRequiredDefaultMaxTokensWithoutOptionalControls() {
        AnthropicMessagesProtocolAdapter.prepare(
            profile = profile("https://api.anthropic.com/v1/messages"),
            messages = listOf(request().prompt),
            turn = request(),
            credential = "key".toByteArray(),
        ).use { prepared ->
            val json = prepared.request.bodyJson()

            assertEquals("https://api.anthropic.com/v1/messages", prepared.request.url.toString())
            assertEquals(1024, json.get("max_tokens").asInt)
            listOf("temperature", "top_k", "top_p", "output_config").forEach { name ->
                assertFalse(json.has(name))
            }
        }
    }

    @Test
    fun streamEventsExposePartialUsageTextAndNativeTerminal() {
        val start = AnthropicMessagesProtocolAdapter.parseEvent(
            event(
                "message_start",
                """{"type":"message_start","message":{"usage":{"input_tokens":7}}}""",
            ),
        )
        val block = AnthropicMessagesProtocolAdapter.parseEvent(
            event(
                "content_block_start",
                """{"type":"content_block_start","content_block":{"type":"text","text":""}}""",
            ),
        )
        val text = AnthropicMessagesProtocolAdapter.parseEvent(
            event(
                "content_block_delta",
                """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hello"}}""",
            ),
        )
        val usage = AnthropicMessagesProtocolAdapter.parseEvent(
            event(
                "message_delta",
                """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}""",
            ),
        )
        val stop = AnthropicMessagesProtocolAdapter.parseEvent(
            event("message_stop", """{"type":"message_stop"}"""),
        )

        assertEquals(OnlineAiUsageUpdate(inputTokens = 7L), start.usage)
        assertTrue(block.contentSeen)
        assertEquals("Hello", text.text)
        assertTrue(text.contentSeen)
        assertEquals(OnlineAiUsageUpdate(outputTokens = 2L), usage.usage)
        assertTrue(stop.done)
    }

    @Test
    fun jsonFallbackCollectsOnlyTextBlocksAndUsage() {
        val response = AnthropicMessagesProtocolAdapter.parseJson(
            """
            {
              "type":"message",
              "content":[
                {"type":"text","text":"Hello"},
                {"type":"tool_use","id":"ignored"},
                {"type":"text","text":" world"}
              ],
              "stop_reason":"end_turn",
              "usage":{"input_tokens":4,"output_tokens":2}
            }
            """.trimIndent().toResponseBody("application/json".toMediaType()),
        )

        assertEquals("Hello world", response.text)
        assertEquals(OnlineAiUsageUpdate(inputTokens = 4L, outputTokens = 2L), response.usage)
    }

    @Test
    fun doneSentinelMismatchedEventsErrorsAndRefusalsFailClosed() {
        assertFailure(OnlineAiFailureReason.INVALID_RESPONSE) {
            AnthropicMessagesProtocolAdapter.parseEvent(
                OnlineAiSseEvent(event = null, data = "[DONE]", isDone = true),
            )
        }
        assertFailure(OnlineAiFailureReason.INVALID_RESPONSE) {
            AnthropicMessagesProtocolAdapter.parseEvent(
                event("message_stop", """{"type":"message_delta","delta":{}}"""),
            )
        }
        assertFailure(OnlineAiFailureReason.PROVIDER_ERROR) {
            AnthropicMessagesProtocolAdapter.parseEvent(
                event("error", """{"type":"error","error":{"message":"secret"}}"""),
            )
        }
        assertFailure(OnlineAiFailureReason.PROVIDER_ERROR) {
            AnthropicMessagesProtocolAdapter.parseEvent(
                event(
                    "message_delta",
                    """{"type":"message_delta","delta":{"stop_reason":"refusal"}}""",
                ),
            )
        }
        assertFailure(OnlineAiFailureReason.PROVIDER_ERROR) {
            AnthropicMessagesProtocolAdapter.parseJson(
                """{"content":[{"type":"text","text":"no"}],"stop_reason":"refusal"}"""
                    .toResponseBody("application/json".toMediaType()),
            )
        }
    }

    private fun profile(baseUrl: String = "https://api.anthropic.com/v1") = OnlineAiProfile(
        profileId = "11111111-1111-4111-8111-111111111111",
        displayName = "Anthropic",
        provider = OnlineAiProvider.ANTHROPIC,
        baseUrl = baseUrl,
        modelId = "claude-test",
    )

    private fun request(
        maximumOutputTokens: Int? = null,
        samplingOptions: GenerationSamplingOptions? = null,
        reportUsage: Boolean = false,
        responseJsonSchema: String? = null,
    ) = GenerationRequest(
        history = emptyList(),
        prompt = GenerationMessage(GenerationRole.USER, listOf("hello")),
        maximumOutputTokens = maximumOutputTokens,
        samplingOptions = samplingOptions,
        reportUsage = reportUsage,
        responseJsonSchema = responseJsonSchema,
    )

    private fun event(type: String, data: String) = OnlineAiSseEvent(
        event = type,
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
