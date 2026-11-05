package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Request bodies and headers for effort and fast mode (roadmap D73 - D75, D88 - D90). */
class GenerationEffortRequestTest {
    @Test
    fun anthropicSendsEveryEffortLevelInOutputConfig() {
        val expected = mapOf(
            null to null,
            GenerationEffort.DEFAULT to null,
            GenerationEffort.LOW to "low",
            GenerationEffort.MEDIUM to "medium",
            GenerationEffort.HIGH to "high",
            GenerationEffort.XHIGH to "xhigh",
            GenerationEffort.MAX to "max",
        )
        expected.forEach { (effort, wire) ->
            val prepared = anthropic("claude-opus-5-5", effort = effort)
            val json = prepared.first

            assertEquals(effort.toString(), wire, json.getAsJsonObject("output_config")?.get("effort")?.asString)
            assertEquals(wire != null, json.has("output_config"))
            assertFalse(json.has("speed"))
            assertNull(prepared.second["anthropic-beta"])
        }
    }

    @Test
    fun anthropicFallsBackToTheClosestSupportedLevelBelow() {
        val cases = listOf(
            Triple("claude-opus-4-6", GenerationEffort.XHIGH, "high"),
            Triple("claude-opus-4-6", GenerationEffort.MAX, "max"),
            Triple("claude-sonnet-4-6-20260101", GenerationEffort.XHIGH, "high"),
            Triple("claude-mythos-preview", GenerationEffort.XHIGH, "high"),
            Triple("claude-opus-4-5-20251101", GenerationEffort.MAX, "high"),
            Triple("claude-opus-4-5", GenerationEffort.XHIGH, "high"),
            Triple("claude-opus-4-5", GenerationEffort.LOW, "low"),
            Triple("claude-fable-5-1", GenerationEffort.XHIGH, "xhigh"),
            Triple("anthropic/claude-opus-4-8", GenerationEffort.MAX, "max"),
        )
        cases.forEach { (model, effort, wire) ->
            val json = anthropic(model, effort = effort).first

            assertEquals("$model $effort", wire, json.getAsJsonObject("output_config").get("effort").asString)
        }
    }

    @Test
    fun anthropicModelsWithoutEffortOmitTheField() {
        listOf("claude-sonnet-4-5", "claude-haiku-4-5-20251001", "claude-opus-4-1", "claude-3-7-sonnet-latest")
            .forEach { model ->
                GenerationEffort.entries.forEach { effort ->
                    assertFalse("$model $effort", anthropic(model, effort = effort).first.has("output_config"))
                }
            }
    }

    @Test
    fun anthropicMergesEffortWithStructuredOutput() {
        val json = anthropic(
            "claude-opus-5-5",
            effort = GenerationEffort.LOW,
            responseJsonSchema = """{"type":"object"}""",
        ).first
        val config = json.getAsJsonObject("output_config")

        assertEquals("low", config.get("effort").asString)
        assertEquals("json_schema", config.getAsJsonObject("format").get("type").asString)
    }

    @Test
    fun anthropicFastOnGaModelsSendsSpeedWithoutBetaHeader() {
        listOf("claude-opus-5-5", "claude-opus-5-5-20261101", "CLAUDE-OPUS-5-5").forEach { model ->
            val (json, headers) = anthropic(model, fast = true)

            assertEquals(model, "fast", json.get("speed").asString)
            assertNull(model, headers["anthropic-beta"])
            assertEquals("2023-06-01", headers["anthropic-version"])
        }
    }

    @Test
    fun anthropicFastOnPreviewModelsAddsTheBetaHeader() {
        listOf("claude-opus-5", "claude-opus-4-8", "claude-fable-5-1").forEach { model ->
            val (json, headers) = anthropic(model, fast = true)

            assertEquals(model, "fast", json.get("speed").asString)
            assertEquals(model, "fast-mode-2026-02-01", headers["anthropic-beta"])
            assertEquals("2023-06-01", headers["anthropic-version"])
        }
    }

    @Test
    fun anthropicWithoutFastSendsNoSpeedOrBetaHeader() {
        listOf(null, false).forEach { fast ->
            val (json, headers) = anthropic("claude-opus-5", fast = fast)

            assertFalse(json.has("speed"))
            assertNull(headers["anthropic-beta"])
        }
    }

    @Test
    fun anthropicNeverSendsSamplingOptions() {
        val json = anthropic(
            "claude-opus-5-5",
            samplingOptions = GenerationSamplingOptions(temperature = 0.4, topK = 9, topP = 0.6),
            effort = GenerationEffort.HIGH,
            fast = true,
        ).first

        listOf("temperature", "top_k", "top_p").forEach { name -> assertFalse(name, json.has(name)) }
    }

    @Test
    fun openAiCompatibleMapsEveryEffortToReasoningEffort() {
        val expected = mapOf(
            null to null,
            GenerationEffort.DEFAULT to null,
            GenerationEffort.LOW to "low",
            GenerationEffort.MEDIUM to "medium",
            GenerationEffort.HIGH to "high",
            GenerationEffort.XHIGH to "xhigh",
            GenerationEffort.MAX to "max",
        )
        listOf(OnlineAiProvider.OPENAI, OnlineAiProvider.OPENAI_COMPATIBLE, OnlineAiProvider.OPENROUTER).forEach { provider ->
            expected.forEach { (effort, wire) ->
                assertEquals("$provider $effort", wire, openAi(provider, "gpt-6.1-sol", effort))
            }
        }
    }

    @Test
    fun openAiCompatibleCapsEffortPerModel() {
        val cases = listOf(
            Triple("gpt-6.1-sol", GenerationEffort.MAX, "max"),
            Triple("gpt-6-astra", GenerationEffort.MAX, "max"),
            Triple("gpt-6.1-terra", GenerationEffort.XHIGH, "xhigh"),
            Triple("gpt-5.6", GenerationEffort.MAX, "max"),
            Triple("gpt-5.6-terra", GenerationEffort.MAX, "max"),
            Triple("openai/gpt-6.1-sol", GenerationEffort.MAX, "max"),
            Triple("GPT-6-Luna", GenerationEffort.MAX, "max"),
            Triple("gpt-5.5", GenerationEffort.MAX, "xhigh"),
            Triple("gpt-5.4-mini", GenerationEffort.MAX, "xhigh"),
            Triple("gpt-5.3-codex", GenerationEffort.MAX, "xhigh"),
            Triple("gpt-5.2", GenerationEffort.XHIGH, "xhigh"),
            Triple("gpt-5.1-codex-max", GenerationEffort.MAX, "xhigh"),
            Triple("gpt-5.1", GenerationEffort.XHIGH, "high"),
            Triple("gpt-5.1-codex", GenerationEffort.MAX, "high"),
            Triple("gpt-5", GenerationEffort.MAX, "high"),
            Triple("gpt-5-mini", GenerationEffort.XHIGH, "high"),
            Triple("o3", GenerationEffort.MAX, "high"),
            Triple("o4-mini", GenerationEffort.LOW, "low"),
            Triple("deepseek-reasoner", GenerationEffort.MAX, "xhigh"),
            Triple("qwen/qwq-32b", GenerationEffort.XHIGH, "xhigh"),
        )
        cases.forEach { (model, effort, wire) ->
            listOf(OnlineAiProvider.OPENAI_COMPATIBLE, OnlineAiProvider.OPENROUTER).forEach { provider ->
                assertEquals("$provider $model $effort", wire, openAi(provider, model, effort))
            }
        }
    }

    @Test
    fun geminiMapsEffortToThinkingLevelPerModel() {
        val cases = listOf(
            Triple("gemini-3-flash-preview", GenerationEffort.LOW, "low"),
            Triple("gemini-3-flash-preview", GenerationEffort.MEDIUM, "medium"),
            Triple("gemini-3-flash-preview", GenerationEffort.HIGH, "high"),
            Triple("gemini-3-flash-preview", GenerationEffort.XHIGH, "high"),
            Triple("models/gemini-3.1-pro-preview", GenerationEffort.MAX, "high"),
            Triple("gemini-3-pro-preview", GenerationEffort.MEDIUM, "low"),
            Triple("gemini-3-pro-preview", GenerationEffort.XHIGH, "high"),
            Triple("gemini-3.1-flash-lite-image-preview", GenerationEffort.LOW, "high"),
            Triple("gemini-3-flash-preview", GenerationEffort.DEFAULT, null),
            Triple("gemini-3-flash-preview", null, null),
            Triple("gemini-2.0-flash", GenerationEffort.HIGH, null),
            Triple("gemini-1.5-pro", GenerationEffort.LOW, null),
        )
        cases.forEach { (model, effort, wire) ->
            val profile = profile(OnlineAiProvider.GEMINI, "https://generativelanguage.googleapis.com/v1beta", model)
            val turn = request(effort = effort, fast = true)
            GeminiGenerateContentProtocolAdapter.prepare(profile, listOf(turn.prompt), turn, key()).use { prepared ->
                val json = prepared.request.bodyJson()
                val thinking = json.getAsJsonObject("generationConfig")?.getAsJsonObject("thinkingConfig")

                assertEquals("$model $effort", wire, thinking?.get("thinkingLevel")?.asString)
                assertFalse(thinking?.has("includeThoughts") == true)
                assertFalse(json.has("speed"))
            }
        }
    }

    @Test
    fun supportTablesExposeLevelsAndFastPerProtocol() {
        assertEquals(
            GenerationEffort.entries - GenerationEffort.DEFAULT,
            OnlineAiEffortSupport.levels(OnlineAiProtocol.ANTHROPIC_MESSAGES, "claude-opus-5-5"),
        )
        assertEquals(
            listOf(GenerationEffort.LOW, GenerationEffort.MEDIUM, GenerationEffort.HIGH, GenerationEffort.XHIGH),
            OnlineAiEffortSupport.levels(OnlineAiProtocol.OPENAI_COMPATIBLE, "anything"),
        )
        assertEquals(
            GenerationEffort.entries - GenerationEffort.DEFAULT,
            OnlineAiEffortSupport.levels(OnlineAiProtocol.OPENAI_COMPATIBLE, "gpt-6.1-sol"),
        )
        assertEquals(
            listOf(GenerationEffort.LOW, GenerationEffort.MEDIUM, GenerationEffort.HIGH, GenerationEffort.XHIGH),
            OnlineAiEffortSupport.levels(OnlineAiProtocol.OPENAI_COMPATIBLE, "gpt-5.5"),
        )
        assertEquals(
            listOf(GenerationEffort.LOW, GenerationEffort.MEDIUM, GenerationEffort.HIGH),
            OnlineAiEffortSupport.levels(OnlineAiProtocol.OPENAI_COMPATIBLE, "o3-mini"),
        )
        assertTrue(OnlineAiEffortSupport.levels(OnlineAiProtocol.ANTHROPIC_MESSAGES, "claude-sonnet-4-5").isEmpty())
        assertTrue(OnlineAiEffortSupport.supportsFast(OnlineAiProtocol.ANTHROPIC_MESSAGES))
        assertFalse(OnlineAiEffortSupport.supportsFast(OnlineAiProtocol.OPENAI_COMPATIBLE))
        assertFalse(OnlineAiEffortSupport.supportsFast(OnlineAiProtocol.GEMINI_GENERATE_CONTENT))
        assertEquals(
            GenerationEffort.DEFAULT,
            OnlineAiEffortSupport.effective(GenerationEffort.HIGH, emptyList()),
        )
        assertEquals(GenerationEffort.HIGH, OnlineAiEffortSupport.effective(GenerationEffort.LOW, listOf(GenerationEffort.HIGH)))
    }

    private fun anthropic(
        modelId: String,
        effort: GenerationEffort? = null,
        fast: Boolean? = null,
        samplingOptions: GenerationSamplingOptions? = null,
        responseJsonSchema: String? = null,
    ): Pair<JsonObject, Map<String, String?>> {
        val profile = profile(OnlineAiProvider.ANTHROPIC, "https://api.anthropic.com/v1", modelId)
        val turn = request(effort, fast, samplingOptions, responseJsonSchema)
        return AnthropicMessagesProtocolAdapter.prepare(profile, listOf(turn.prompt), turn, key()).use { prepared ->
            val request = prepared.request
            request.bodyJson() to listOf("anthropic-beta", "anthropic-version").associateWith(request::header)
        }
    }

    private fun openAi(provider: OnlineAiProvider, modelId: String, effort: GenerationEffort?): String? {
        val profile = profile(provider, "https://example.com/v1", modelId)
        val turn = request(effort = effort, fast = true)
        return OpenAiCompatibleRequestFactory.prepare(profile, listOf(turn.prompt), turn, key()).use { prepared ->
            val json = prepared.request.bodyJson()
            assertFalse(json.has("speed"))
            assertNull(prepared.request.header("anthropic-beta"))
            json.get("reasoning_effort")?.asString
        }
    }

    private fun profile(provider: OnlineAiProvider, baseUrl: String, modelId: String) = OnlineAiProfile(
        profileId = "33333333-3333-4333-8333-333333333333",
        displayName = "Effort",
        provider = provider,
        baseUrl = baseUrl,
        modelId = modelId,
    )

    private fun request(
        effort: GenerationEffort? = null,
        fast: Boolean? = null,
        samplingOptions: GenerationSamplingOptions? = null,
        responseJsonSchema: String? = null,
    ) = GenerationRequest(
        history = emptyList(),
        prompt = GenerationMessage(GenerationRole.USER, listOf("hello")),
        maximumOutputTokens = null,
        samplingOptions = samplingOptions,
        reportUsage = false,
        responseJsonSchema = responseJsonSchema,
        effort = effort,
        fast = fast,
    )

    private fun okhttp3.Request.bodyJson(): JsonObject {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return JsonParser.parseString(buffer.readUtf8()).asJsonObject
    }

    private fun key() = "secret".toByteArray()
}
