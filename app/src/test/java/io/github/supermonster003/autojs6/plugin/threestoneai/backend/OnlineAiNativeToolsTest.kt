package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test

class OnlineAiNativeToolsTest {
    @Test
    fun openAiStreamAccumulatesInterleavedCallsAndResumesInOriginalOrder() {
        val harness = Harness(OnlineAiProvider.OPENAI_COMPATIBLE, stream(
            """{"choices":[{"delta":{"content":"Checking.","tool_calls":[{"index":1,"id":"b","type":"function","function":{"name":"observe","arguments":"{\"x\":"}},{"index":0,"id":"a","type":"function","function":{"name":"observe","arguments":"{"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"}"}},{"index":1,"function":{"arguments":"2}"}}]},"finish_reason":"tool_calls"}]}""",
            """{"choices":[],"usage":{"prompt_tokens":9,"completion_tokens":4,"total_tokens":13}}""", "[DONE]",
        ), json(openAiFinal))
        harness.start()
        assertNull(harness.listener.failure)
        assertFalse(harness.listener.completed)
        assertEquals(listOf("a", "b"), harness.listener.calls.map { it.callId })
        assertEquals("{\"x\":2}", harness.listener.calls[1].argumentsJson)
        assertEquals(9L, harness.listener.statistics.single()?.inputTokens)
        harness.session.submitToolResults(listOf(GenerationToolResult("b", "failed", true), GenerationToolResult("a", "ok")))
        assertTrue(harness.listener.completed)
        assertNull(harness.listener.failure)
        assertEquals(listOf("Checking.", "Done"), harness.listener.text)
        val messages = harness.requests[1].getAsJsonArray("messages")
        assertEquals(listOf("user", "assistant", "tool", "tool"), messages.map { it.asJsonObject.get("role").asString })
        assertEquals("a", messages[2].asJsonObject.get("tool_call_id").asString)
        assertEquals("ok", messages[2].asJsonObject.get("content").asString)
        assertTrue(messages[3].asJsonObject.get("content").asString.contains("\"isError\":true"))
        assertEquals(2, harness.credentialReads)
        assertEquals(2, harness.networkChecks)
        assertTrue(harness.retiredSecrets.all { bytes -> bytes.all { it == 0.toByte() } })
        assertTrue(harness.requests[0].getAsJsonArray("tools")[0].asJsonObject.getAsJsonObject("function").has("parameters"))
    }

    @Test
    fun anthropicStreamRetainsSignedThinkingAndMaterializesOnlyCompletedToolInput() {
        val harness = Harness(OnlineAiProvider.ANTHROPIC, stream(
            """{"type":"message_start","message":{"usage":{"input_tokens":3}}}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"private-thought"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"opaque-signature"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"a","name":"observe","input":{}}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"x\":"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"1}"}}""",
            """{"type":"content_block_stop","index":1}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":2}}""",
            """{"type":"message_stop"}""",
        ), json(anthropicFinal))
        harness.start()
        assertNull(harness.listener.failure)
        assertTrue(harness.listener.text.isEmpty())
        assertEquals("{\"x\":1}", harness.listener.calls.single().argumentsJson)
        harness.resume(error = true)
        assertTrue(harness.listener.completed)
        val messages = harness.requests[1].getAsJsonArray("messages")
        val assistant = messages[1].asJsonObject.getAsJsonArray("content")
        assertEquals("opaque-signature", assistant[0].asJsonObject.get("signature").asString)
        assertEquals(1, assistant[1].asJsonObject.getAsJsonObject("input").get("x").asInt)
        val result = messages[2].asJsonObject.getAsJsonArray("content")[0].asJsonObject
        assertEquals("tool_result", result.get("type").asString)
        assertEquals("a", result.get("tool_use_id").asString)
        assertTrue(result.get("is_error").asBoolean)
        assertTrue(harness.requests[0].getAsJsonArray("tools")[0].asJsonObject.has("input_schema"))
    }

    @Test
    fun geminiPreservesSignaturesAndWireIdsAcrossSequentialAndParallelToolRounds() {
        val harness = Harness(OnlineAiProvider.GEMINI,
            stream("""{"candidates":[{"content":{"parts":[{"functionCall":{"name":"observe","args":{"x":1}},"thoughtSignature":"first-signature"},{"functionCall":{"id":"wire-b","name":"observe","args":{"x":2}}}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":4,"candidatesTokenCount":3}}"""),
            json("""{"candidates":[{"content":{"parts":[{"functionCall":{"id":"wire-c","name":"observe","args":{}},"thoughtSignature":"second-signature"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":9,"candidatesTokenCount":2}}"""),
            json(geminiFinal),
        )
        harness.start(rounds = 2)
        assertNull(harness.listener.failure)
        assertEquals(2, harness.listener.calls.size)
        assertTrue(harness.listener.calls[0].callId.startsWith("gemini-"))
        assertNull(harness.listener.calls[0].wireId)
        harness.resume()
        assertNull(harness.listener.failure)
        assertEquals("wire-c", harness.listener.calls.single().callId)
        harness.resume()
        assertTrue(harness.listener.completed)
        val contents = harness.requests[2].getAsJsonArray("contents")
        assertEquals("first-signature", contents[1].asJsonObject.getAsJsonArray("parts")[0].asJsonObject.get("thoughtSignature").asString)
        assertEquals("second-signature", contents[3].asJsonObject.getAsJsonArray("parts")[0].asJsonObject.get("thoughtSignature").asString)
        val results = contents[2].asJsonObject.getAsJsonArray("parts")
        assertFalse(results[0].asJsonObject.getAsJsonObject("functionResponse").has("id"))
        assertEquals("wire-b", results[1].asJsonObject.getAsJsonObject("functionResponse").get("id").asString)
        assertTrue(harness.requests[0].getAsJsonArray("tools")[0].asJsonObject.has("functionDeclarations"))
    }

    @Test
    fun allProtocolsAcceptToolOnlyJsonAndKeepToolSchemasOnTheNextRequest() {
        for ((provider, pair) in fixtures) {
            val harness = Harness(provider, json(pair.first), json(pair.second))
            harness.start()
            assertNull("$provider", harness.listener.failure)
            assertFalse(harness.listener.completed)
            assertEquals(1, harness.listener.calls.size)
            harness.resume()
            assertTrue("$provider", harness.listener.completed)
            assertNull(harness.listener.failure)
            assertEquals(harness.requests[0].get("tools"), harness.requests[1].get("tools"))
        }
    }

    @Test
    fun unsolicitedUndeclaredDuplicateMalformedAndTruncatedCallsNeverReachTheCaller() {
        val invalid = listOf(
            openAiCall.replace("observe", "undeclared"),
            openAiCall.replace("\"arguments\":\"{}\"", "\"arguments\":\"[]\""),
            openAiCall.replace("\"arguments\":\"{}\"", "\"arguments\":\"{\""),
            openAiCall.replace("\"finish_reason\":\"tool_calls\"", "\"finish_reason\":\"length\""),
            JsonParser.parseString(openAiCall).asJsonObject.apply {
                val calls = getAsJsonArray("choices")[0].asJsonObject.getAsJsonObject("message").getAsJsonArray("tool_calls")
                calls.add(calls[0].deepCopy())
            }.toString(),
        )
        invalid.forEach { payload ->
            val harness = Harness(OnlineAiProvider.OPENAI_COMPATIBLE, json(payload))
            harness.start()
            assertNotNull(payload, harness.listener.failure)
            assertTrue(harness.listener.calls.isEmpty())
            assertFalse(harness.listener.completed)
        }
        for ((provider, pair) in fixtures) {
            val harness = Harness(provider, json(pair.first))
            harness.start(withTools = false)
            assertNotNull("$provider", harness.listener.failure)
            assertFalse(harness.listener.completed)
        }
    }

    @Test
    fun wrongBatchDoesNotConsumePendingCallsAndDuplicateSubmissionCannotSendAnotherRequest() {
        val harness = Harness(OnlineAiProvider.OPENAI_COMPATIBLE, json(openAiCall), json(openAiFinal))
        harness.start()
        assertThrows(IllegalArgumentException::class.java) {
            harness.session.submitToolResults(listOf(GenerationToolResult("wrong", "ok")))
        }
        assertThrows(IllegalStateException::class.java) { harness.session.streamNext(request(), Listener()) }
        assertEquals(1, harness.requests.size)
        harness.resume()
        assertThrows(IllegalStateException::class.java) { harness.resume() }
        assertEquals(2, harness.requests.size)
    }

    @Test
    fun repeatedIdsAndRoundExhaustionFailBeforePublishingAnotherCall() {
        for ((second, rounds) in listOf(openAiCall to 2, openAiCall.replace("\"a\"", "\"b\"") to 1)) {
            val harness = Harness(OnlineAiProvider.OPENAI_COMPATIBLE, json(openAiCall), json(second))
            harness.start(rounds = rounds)
            harness.resume()
            assertNotNull(harness.listener.failure)
            assertEquals(1, harness.listener.toolEvents)
            assertFalse(harness.listener.completed)
        }
    }

    @Test
    fun cancellationWhileWaitingAndNetworkRevocationPreventContinuation() {
        val cancelled = Harness(OnlineAiProvider.OPENAI_COMPATIBLE, json(openAiCall))
        cancelled.start()
        cancelled.session.close()
        assertThrows(IllegalStateException::class.java) { cancelled.resume() }
        assertEquals(1, cancelled.requests.size)
        val revoked = Harness(OnlineAiProvider.OPENAI_COMPATIBLE, json(openAiCall))
        revoked.start()
        revoked.networkAllowed = false
        revoked.resume()
        assertNotNull(revoked.listener.failure)
        assertEquals(1, revoked.requests.size)
        assertEquals(1, revoked.credentialReads)
    }

    @Test
    fun continuedContextAndToolDefinitionBytesAreBoundedBeforeHttp() {
        val harness = Harness(OnlineAiProvider.OPENAI_COMPATIBLE, json(openAiCall))
        harness.start(prompt = "x".repeat(220 * 1024))
        harness.resume(output = "x".repeat(60 * 1024))
        assertNotNull(harness.listener.failure)
        assertEquals(1, harness.requests.size)
        val oversized = Harness(OnlineAiProvider.OPENAI_COMPATIBLE)
        oversized.start(prompt = "x".repeat(256 * 1024))
        assertNotNull(oversized.listener.failure)
        assertTrue(oversized.requests.isEmpty())
    }

    @Test
    fun incompleteAnthropicInputAndGeminiTruncationFailWithoutCalls() {
        val anthropic = Harness(OnlineAiProvider.ANTHROPIC, stream(
            """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"a","name":"observe","input":{}}}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":2}}""",
            """{"type":"message_stop"}""",
        ))
        anthropic.start()
        assertNotNull(anthropic.listener.failure)
        assertTrue(anthropic.listener.calls.isEmpty())
        val gemini = Harness(OnlineAiProvider.GEMINI, json(geminiCall.replace("STOP", "MAX_TOKENS")))
        gemini.start()
        assertNotNull(gemini.listener.failure)
        assertTrue(gemini.listener.calls.isEmpty())
    }

    @Test
    fun outputTokenBudgetIsSharedByToolRoundsAndCannotBeRefilled() {
        val harness = Harness(OnlineAiProvider.OPENAI_COMPATIBLE, json(openAiCall), json(openAiFinal))
        harness.start(maxTokens = 5)
        harness.resume()
        assertEquals(5, harness.requests[0].get("max_tokens").asInt)
        assertEquals(3, harness.requests[1].get("max_tokens").asInt)
        val exhausted = Harness(OnlineAiProvider.OPENAI_COMPATIBLE, json(openAiCall))
        exhausted.start(maxTokens = 2)
        assertNotNull(exhausted.listener.failure)
        assertTrue(exhausted.listener.calls.isEmpty())
    }

    private class Listener : GenerationListener {
        val text = mutableListOf<String>()
        var calls = emptyList<GenerationToolCall>()
        var toolEvents = 0
        val statistics = mutableListOf<GenerationStatistics?>()
        var completed = false
        var failure: Throwable? = null
        override fun onTextDelta(text: String) { this.text += text }
        override fun onToolCalls(calls: List<GenerationToolCall>, statistics: GenerationStatistics?) {
            this.calls = calls
            this.statistics += statistics
            toolEvents++
        }
        override fun onCompleted(statistics: GenerationStatistics?) { completed = true; this.statistics += statistics }
        override fun onFailed(error: Throwable, statistics: GenerationStatistics?) { failure = error }
    }

    private class Harness(provider: OnlineAiProvider, vararg responses: Pair<String, String>) {
        val requests = mutableListOf<JsonObject>()
        private val responses = ArrayDeque(responses.toList())
        var credentialReads = 0
        var networkChecks = 0
        var networkAllowed = true
        val retiredSecrets = mutableListOf<ByteArray>()
        val listener = Listener()
        private val profile = OnlineAiProfile("11111111-1111-4111-8111-111111111111", "Tools", provider, "https://example.com/v1", "test-model")
        private val execution = OnlineAiHttpExecution(Call.Factory { error("Capabilities only") })
        val session = OnlineAiSession(
            target = AiTarget(AiTargetIds.profile(profile.profileId), OnlineAiBackend.BACKEND_ID,
                profile.provider.providerId, profile.profileId, profile.modelId, profile.displayName,
                AiTargetLocality.REMOTE, AiTargetCredentialMode.PLUGIN_MANAGED, listOf(profile.declaredHttpsOrigin),
                true, true, execution.capabilities(profile), execution.limits(profile), emptyList()),
            profile = profile,
            callFactory = Call.Factory { request -> factoryField(request) },
            networkAccess = OnlineAiNetworkAccess { networkChecks++; check(networkAllowed) },
            credentialRunner = OnlineAiCredentialRunner { action ->
                val secret = "test-only".toByteArray()
                credentialReads++
                try { action(secret) } finally { secret.fill(0); retiredSecrets += secret }
            },
        )

        // Reuse the fixture factory without opening a socket or introducing real credentials.
        private val factoryField: (Request) -> Call = { request ->
            object : Call {
                private var cancelled = false
                private var executed = false
                override fun request() = request
                override fun execute(): Response {
                    check(!executed && !cancelled)
                    executed = true
                    val buffer = Buffer()
                    request.body!!.writeTo(buffer)
                    requests += JsonParser.parseString(buffer.readUtf8()).asJsonObject
                    val response = this@Harness.responses.removeFirst()
                    return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(response.second.toResponseBody(response.first.toMediaType())).build()
                }
                override fun enqueue(responseCallback: Callback) = error("Synchronous test")
                override fun cancel() { cancelled = true }
                override fun isExecuted() = executed
                override fun isCanceled() = cancelled
                override fun timeout() = Timeout.NONE
                override fun clone(): Call = error("No clone")
            }
        }

        fun start(rounds: Int = 1, withTools: Boolean = true, prompt: String = "Inspect", maxTokens: Int? = null) = session.stream(request().copy(
            prompt = GenerationMessage(GenerationRole.USER, listOf(prompt)),
            maximumOutputTokens = maxTokens,
            tools = if (withTools) listOf(GenerationToolDefinition("observe", "Inspect", "{\"type\":\"object\"}")) else emptyList(),
            maximumToolRounds = if (withTools) rounds else 0,
        ), listener)
        fun resume(error: Boolean = false, output: String = "ok") = session.submitToolResults(
            listener.calls.map { GenerationToolResult(it.callId, output, error) },
        )
    }

    private companion object {
        fun request() = GenerationRequest(emptyList(), GenerationMessage(GenerationRole.USER, listOf("Inspect")), null, null, true)
        fun json(body: String) = "application/json" to body
        fun stream(vararg data: String) = "text/event-stream" to data.joinToString("") { "data: $it\n\n" }
        val openAiCall = """{"choices":[{"message":{"tool_calls":[{"id":"a","type":"function","function":{"name":"observe","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}"""
        val openAiFinal = """{"choices":[{"message":{"content":"Done"},"finish_reason":"stop"}],"usage":{"prompt_tokens":8,"completion_tokens":1,"total_tokens":9}}"""
        val anthropicCall = """{"content":[{"type":"tool_use","id":"a","name":"observe","input":{}}],"stop_reason":"tool_use","usage":{"input_tokens":3,"output_tokens":2}}"""
        val anthropicFinal = """{"content":[{"type":"text","text":"Done"}],"stop_reason":"end_turn","usage":{"input_tokens":8,"output_tokens":1}}"""
        val geminiCall = """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"observe","args":{}},"thoughtSignature":"signature"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":3,"candidatesTokenCount":2}}"""
        val geminiFinal = """{"candidates":[{"content":{"parts":[{"text":"Done"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":8,"candidatesTokenCount":1}}"""
        val fixtures = listOf(OnlineAiProvider.OPENAI_COMPATIBLE to (openAiCall to openAiFinal),
            OnlineAiProvider.ANTHROPIC to (anthropicCall to anthropicFinal), OnlineAiProvider.GEMINI to (geminiCall to geminiFinal))
    }
}
