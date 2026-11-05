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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Visible reasoning across the three protocols (roadmap P3.1, D93). */
class ReasoningProtocolTest {
    @Test
    fun anthropicStreamSeparatesThinkingFromTextAndIgnoresSignatures() {
        val adapter = AnthropicMessagesProtocolAdapter
        val start = adapter.parseEvent(
            event("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}"""),
        )
        val thinking = adapter.parseEvent(
            event("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Weigh both."}}"""),
        )
        val signature = adapter.parseEvent(
            event("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"EqQBCgIYAh"}}"""),
        )
        val redacted = adapter.parseEvent(
            event("content_block_start", """{"type":"content_block_start","index":1,"content_block":{"type":"redacted_thinking","data":"EmwKAhgB"}}"""),
        )
        val text = adapter.parseEvent(
            event("content_block_delta", """{"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"Answer"}}"""),
        )

        assertEquals("", start.reasoning)
        assertFalse(start.contentSeen)
        assertEquals("Weigh both.", thinking.reasoning)
        assertEquals("", thinking.text)
        assertFalse(thinking.contentSeen)
        listOf(signature, redacted).forEach { chunk ->
            assertEquals("", chunk.reasoning)
            assertEquals("", chunk.text)
            assertFalse(chunk.contentSeen)
        }
        assertEquals("Answer", text.text)
        assertEquals("", text.reasoning)
        assertTrue(text.contentSeen)
    }

    @Test
    fun anthropicJsonCollectsThinkingBlocksApartFromText() {
        val response = AnthropicMessagesProtocolAdapter.parseJson(
            """
            {"type":"message","role":"assistant","stop_reason":"end_turn","content":[
              {"type":"thinking","thinking":"First. ","signature":"sig"},
              {"type":"redacted_thinking","data":"opaque"},
              {"type":"thinking","thinking":"Second."},
              {"type":"text","text":"Done"}
            ]}
            """.trimIndent().toResponseBody("application/json".toMediaType()),
        )

        assertEquals("Done", response.text)
        assertEquals("First. Second.", response.reasoning)
    }

    @Test
    fun anthropicRequestAsksForSummarizedThinkingOnlyOnClaude5AndMythosChat() {
        listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-mythos-preview", "anthropic/claude-haiku-5-5")
            .forEach { model ->
                val json = anthropicBody(model, includeReasoning = true, effort = GenerationEffort.HIGH)
                val thinking = json.getAsJsonObject("thinking")
                assertEquals(model, "adaptive", thinking.get("type").asString)
                assertEquals(model, "summarized", thinking.get("display").asString)
                assertEquals(model, "high", json.getAsJsonObject("output_config").get("effort").asString)
            }
        assertFalse(anthropicBody("claude-opus-5-5", includeReasoning = false).has("thinking"))
        // Opus 4.6 - 4.8 / Sonnet 4.6 would start thinking; the 4.5 family rejects adaptive (Q14).
        listOf("claude-opus-4-7", "claude-sonnet-4-6", "claude-sonnet-4-5", "claude-haiku-4-5", "claude-test")
            .forEach { model -> assertFalse(model, anthropicBody(model, includeReasoning = true).has("thinking")) }
    }

    @Test
    fun openAiAcceptsBothReasoningFieldNamesAndPrefersReasoningContent() {
        val parser = OpenAiCompatibleResponseParser
        val deepSeek = parser.parseEvent(event("""{"choices":[{"delta":{"reasoning_content":"Step one"}}]}"""))
        val openRouter = parser.parseEvent(event("""{"choices":[{"delta":{"reasoning":"Step two"}}]}"""))
        val both = parser.parseEvent(event("""{"choices":[{"delta":{"reasoning_content":"Same","reasoning":"Same"}}]}"""))
        val emptyFirst = parser.parseEvent(event("""{"choices":[{"delta":{"reasoning_content":"","reasoning":"Fallback"}}]}"""))
        val text = parser.parseEvent(event("""{"choices":[{"delta":{"content":"Hi","reasoning_content":null}}]}"""))

        assertEquals("Step one", deepSeek.reasoning)
        assertEquals("", deepSeek.text)
        assertEquals("Step two", openRouter.reasoning)
        assertEquals("Same", both.reasoning)
        assertEquals("Fallback", emptyFirst.reasoning)
        assertEquals("Hi", text.text)
        assertEquals("", text.reasoning)

        val json = parser.parseJson(
            """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Final","reasoning":"Why"}}]}"""
                .toResponseBody("application/json".toMediaType()),
        )
        assertEquals("Final", json.text)
        assertEquals("Why", json.reasoning)
    }

    @Test
    fun openAiRequestBodyDoesNotChangeWhenReasoningIsRequested() {
        val profile = OnlineAiProfile(
            profileId = "33333333-3333-4333-8333-333333333333",
            displayName = "Codex",
            provider = OnlineAiProvider.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            modelId = "gpt-6.1-sol",
        )
        val bodies = listOf(false, true).map { includeReasoning ->
            val turn = request(includeReasoning = includeReasoning, effort = GenerationEffort.MAX)
            OnlineAiProtocolAdapters.forProfile(profile)
                .prepare(profile, listOf(turn.prompt), turn, "key".toByteArray())
                .use { prepared -> prepared.request.bodyJson() }
        }

        assertEquals(bodies[0], bodies[1])
        assertEquals("max", bodies[1].get("reasoning_effort").asString)
    }

    @Test
    fun geminiMergesIncludeThoughtsWithThinkingLevel() {
        val config = geminiBody("gemini-3-flash", includeReasoning = true, effort = GenerationEffort.HIGH)
            .getAsJsonObject("generationConfig")
            .getAsJsonObject("thinkingConfig")
        assertEquals("high", config.get("thinkingLevel").asString)
        assertTrue(config.get("includeThoughts").asBoolean)

        val withoutLevel = geminiBody("models/gemini-2.5-pro", includeReasoning = true)
            .getAsJsonObject("generationConfig")
            .getAsJsonObject("thinkingConfig")
        assertFalse(withoutLevel.has("thinkingLevel"))
        assertTrue(withoutLevel.get("includeThoughts").asBoolean)

        assertFalse(
            geminiBody("gemini-3-flash", includeReasoning = false)
                .getAsJsonObject("generationConfig")?.has("thinkingConfig") ?: false,
        )
        assertFalse(
            geminiBody("gemini-2.0-flash", includeReasoning = true)
                .getAsJsonObject("generationConfig")?.has("thinkingConfig") ?: false,
        )
    }

    @Test
    fun geminiThoughtPartsBecomeReasoningInStreamAndJson() {
        val chunk = GeminiGenerateContentProtocolAdapter.parseEvent(
            event("""{"candidates":[{"content":{"parts":[{"text":"Consider X. ","thought":true},{"text":"Reply"}]}}]}"""),
        )
        assertEquals("Consider X. ", chunk.reasoning)
        assertEquals("Reply", chunk.text)
        assertTrue(chunk.contentSeen)

        val thoughtOnly = GeminiGenerateContentProtocolAdapter.parseEvent(
            event("""{"candidates":[{"content":{"parts":[{"text":"Only thinking","thought":true}]}}]}"""),
        )
        assertEquals("Only thinking", thoughtOnly.reasoning)
        assertEquals("", thoughtOnly.text)
        assertFalse(thoughtOnly.contentSeen)

        val json = GeminiGenerateContentProtocolAdapter.parseJson(
            """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Plan","thought":true},{"text":"Answer"}]}}]}"""
                .toResponseBody("application/json".toMediaType()),
        )
        assertEquals("Answer", json.text)
        assertEquals("Plan", json.reasoning)
    }

    @Test
    fun binderRequestsNeverAskForReasoning() {
        val request = GenerationRequest(
            history = emptyList(),
            prompt = GenerationMessage(GenerationRole.USER, listOf("script")),
            maximumOutputTokens = null,
            samplingOptions = null,
            reportUsage = false,
        )

        assertFalse(request.includeReasoning)
        assertNull(request.effort)
    }

    private fun anthropicBody(
        modelId: String,
        includeReasoning: Boolean,
        effort: GenerationEffort? = null,
    ): JsonObject {
        val profile = OnlineAiProfile(
            profileId = "11111111-1111-4111-8111-111111111111",
            displayName = "Anthropic",
            provider = OnlineAiProvider.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            modelId = modelId,
        )
        val turn = request(includeReasoning = includeReasoning, effort = effort)
        return AnthropicMessagesProtocolAdapter
            .prepare(profile = profile, messages = listOf(turn.prompt), turn = turn, credential = "key".toByteArray())
            .use { prepared -> prepared.request.bodyJson() }
    }

    private fun geminiBody(
        modelId: String,
        includeReasoning: Boolean,
        effort: GenerationEffort? = null,
    ): JsonObject {
        val profile = OnlineAiProfile(
            profileId = "22222222-2222-4222-8222-222222222222",
            displayName = "Gemini",
            provider = OnlineAiProvider.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            modelId = modelId,
        )
        val turn = request(includeReasoning = includeReasoning, effort = effort)
        return GeminiGenerateContentProtocolAdapter
            .prepare(profile = profile, messages = listOf(turn.prompt), turn = turn, credential = "key".toByteArray())
            .use { prepared -> prepared.request.bodyJson() }
    }

    private fun request(includeReasoning: Boolean, effort: GenerationEffort? = null) = GenerationRequest(
        history = emptyList(),
        prompt = GenerationMessage(GenerationRole.USER, listOf("hello")),
        maximumOutputTokens = null,
        samplingOptions = null,
        reportUsage = false,
        effort = effort,
        includeReasoning = includeReasoning,
    )

    private fun event(type: String, data: String) = OnlineAiSseEvent(event = type, data = data, isDone = false)

    private fun event(data: String) = OnlineAiSseEvent(event = null, data = data, isDone = false)

    private fun okhttp3.Request.bodyJson(): JsonObject {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return JsonParser.parseString(buffer.readUtf8()).asJsonObject
    }
}
