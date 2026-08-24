package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonParser
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleRequestTest {
    @Test
    fun requestMapsMessagesControlsSchemaAndUsageWithoutLeakingCredentialIntoPayload() {
        val credential = "secret-api-key".toByteArray()
        val request = generationRequest(
            maximumOutputTokens = 321,
            samplingOptions = GenerationSamplingOptions(
                temperature = 0.25,
                topK = 17,
                topP = 0.75,
            ),
            reportUsage = true,
            responseJsonSchema = "{\"type\":\"object\",\"properties\":{}}",
        )

        OpenAiCompatibleRequestFactory.prepare(
            profile = profile("https://example.com/v1"),
            messages = listOf(
                GenerationMessage(GenerationRole.SYSTEM, listOf("rules")),
                GenerationMessage(GenerationRole.ASSISTANT, listOf("prior", " answer")),
                request.prompt,
            ),
            turn = request,
            credential = credential,
        ).use { prepared ->
            val outgoing = prepared.request
            val body = outgoing.body ?: error("Missing request body")
            val buffer = Buffer()
            body.writeTo(buffer)
            val encoded = buffer.readUtf8()
            val json = JsonParser.parseString(encoded).asJsonObject

            assertEquals("https://example.com/v1/chat/completions", outgoing.url.toString())
            assertEquals("Bearer secret-api-key", outgoing.header("Authorization"))
            assertEquals("text/event-stream", outgoing.header("Accept"))
            assertEquals("no-store", outgoing.header("Cache-Control"))
            assertEquals("AutoJs6-Three-Stone-AI/1", outgoing.header("User-Agent"))
            assertTrue(body.isOneShot())
            assertEquals("remote-model", json.get("model").asString)
            assertTrue(json.get("stream").asBoolean)
            assertEquals(321, json.get("max_tokens").asInt)
            assertEquals(0.25, json.get("temperature").asDouble, 0.0)
            assertEquals(17, json.get("top_k").asInt)
            assertEquals(0.75, json.get("top_p").asDouble, 0.0)
            assertTrue(
                json.getAsJsonObject("stream_options").get("include_usage").asBoolean,
            )
            val messages = json.getAsJsonArray("messages")
            assertEquals(listOf("system", "assistant", "user"), messages.map {
                it.asJsonObject.get("role").asString
            })
            assertEquals("prior answer", messages[1].asJsonObject.get("content").asString)
            val responseFormat = json.getAsJsonObject("response_format")
            assertEquals("json_schema", responseFormat.get("type").asString)
            assertTrue(responseFormat.getAsJsonObject("json_schema").get("strict").asBoolean)
            assertFalse(encoded.contains("secret-api-key"))
            assertFalse(outgoing.url.toString().contains("secret-api-key"))
        }
    }

    @Test
    fun endpointAlreadyEndingInChatCompletionsIsNotDuplicated() {
        OpenAiCompatibleRequestFactory.prepare(
            profile = profile("https://example.com/openai/chat/completions"),
            messages = listOf(generationRequest().prompt),
            turn = generationRequest(),
            credential = "key".toByteArray(),
        ).use { prepared ->
            assertEquals(
                "https://example.com/openai/chat/completions",
                prepared.request.url.toString(),
            )
        }
    }

    @Test
    fun optionalControlsAreAbsentWhenNotRequested() {
        OpenAiCompatibleRequestFactory.prepare(
            profile = profile(),
            messages = listOf(generationRequest().prompt),
            turn = generationRequest(),
            credential = "key".toByteArray(),
        ).use { prepared ->
            val buffer = Buffer()
            prepared.request.body!!.writeTo(buffer)
            val json = JsonParser.parseString(buffer.readUtf8()).asJsonObject

            listOf(
                "max_tokens",
                "temperature",
                "top_k",
                "top_p",
                "stream_options",
                "response_format",
            ).forEach { key -> assertFalse(json.has(key)) }
        }
    }

    @Test
    fun invalidSchemaAndCredentialFailWithNoSensitiveDetail() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiCompatibleRequestFactory.prepare(
                profile = profile(),
                messages = listOf(generationRequest().prompt),
                turn = generationRequest(responseJsonSchema = "{\"type\":\"object\"} trailing"),
                credential = "key".toByteArray(),
            )
        }

        val secret = "secret\nkey"
        val failure = assertThrows(OpenAiCompatibleFailureException::class.java) {
            OpenAiCompatibleRequestFactory.prepare(
                profile = profile(),
                messages = listOf(generationRequest().prompt),
                turn = generationRequest(),
                credential = secret.toByteArray(),
            )
        }
        assertEquals(OpenAiCompatibleFailureReason.CREDENTIAL_UNAVAILABLE, failure.reason)
        assertFalse(failure.toString().contains("secret"))
        assertFalse(failure.toString().contains("key"))
    }

    @Test
    fun requestBodyIsErasedAfterPreparedRequestCloses() {
        val prepared = OpenAiCompatibleRequestFactory.prepare(
            profile = profile(),
            messages = listOf(generationRequest().prompt),
            turn = generationRequest(),
            credential = "key".toByteArray(),
        )
        val body = prepared.request.body ?: error("Missing request body")

        prepared.close()

        assertThrows(IllegalStateException::class.java) {
            body.writeTo(Buffer())
        }
    }

    @Test
    fun contextAndRequestLimitsAreEnforcedBeforeNetworkExecution() {
        val oversized = "x".repeat(
            OpenAiCompatibleTransportLimits.MAXIMUM_CONTEXT_BYTES.toInt() + 1,
        )
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiCompatibleRequestFactory.prepare(
                profile = profile(),
                messages = listOf(
                    GenerationMessage(GenerationRole.USER, listOf(oversized)),
                ),
                turn = generationRequest(),
                credential = "key".toByteArray(),
            )
        }

        val schema = "{\"type\":\"object\"}"
        val messagesAlmostAtLimit = "x".repeat(
            OpenAiCompatibleTransportLimits.MAXIMUM_CONTEXT_BYTES.toInt() -
                schema.toByteArray().size + 1,
        )
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiCompatibleRequestFactory.prepare(
                profile = profile(),
                messages = listOf(
                    GenerationMessage(GenerationRole.USER, listOf(messagesAlmostAtLimit)),
                ),
                turn = generationRequest(responseJsonSchema = schema),
                credential = "key".toByteArray(),
            )
        }
    }

    private fun profile(baseUrl: String = "https://example.com/v1") = OnlineAiProfile(
        profileId = "11111111-1111-4111-8111-111111111111",
        displayName = "Remote",
        provider = OnlineAiProvider.OPENAI_COMPATIBLE,
        baseUrl = baseUrl,
        modelId = "remote-model",
    )

    private fun generationRequest(
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
}
