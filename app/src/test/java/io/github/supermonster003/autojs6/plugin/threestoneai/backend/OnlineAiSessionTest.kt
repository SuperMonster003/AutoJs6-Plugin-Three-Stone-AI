package io.github.supermonster003.autojs6.plugin.threestoneai.backend

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OnlineAiSessionTest {
    @Test
    fun sseTurnsStreamTextReportUsageAndRetainOnlyCompletedConversation() {
        val calls = ScriptedCallFactory(
            response(
                contentType = "text/event-stream",
                body = sse(
                    "{\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}",
                    "{\"choices\":[{\"delta\":{\"content\":\" world\"}}]}",
                    "{\"choices\":[],\"usage\":{\"prompt_tokens\":7," +
                        "\"completion_tokens\":2,\"total_tokens\":9}}",
                    "[DONE]",
                ),
            ),
            response(
                contentType = "text/event-stream; charset=utf-8",
                body = sse(
                    "{\"choices\":[{\"delta\":{\"content\":\"Again\"}}]}",
                    "{\"choices\":[],\"usage\":{\"prompt_tokens\":10," +
                        "\"completion_tokens\":1,\"total_tokens\":11}}",
                    "[DONE]",
                ),
            ),
        )
        val credentials = ClearingCredentialRunner("session-key")
        val session = session(calls, credentials)
        val first = RecordingListener()

        session.stream(
            request(
                history = listOf(
                    GenerationMessage(GenerationRole.SYSTEM, listOf("rules")),
                ),
                prompt = "first",
                reportUsage = true,
            ),
            first,
        )

        assertEquals(listOf("Hello", " world"), first.deltas)
        assertEquals(7L, first.completed?.inputTokens)
        assertEquals(2L, first.completed?.outputTokens)
        assertTrue(requireNotNull(first.completed).durationMillis >= 0L)
        assertNull(first.failure)

        val second = RecordingListener()
        session.streamNext(request(prompt = "second", reportUsage = true), second)

        assertEquals(listOf("Again"), second.deltas)
        assertEquals(10L, second.completed?.inputTokens)
        assertEquals(1L, second.completed?.outputTokens)
        assertNull(second.failure)
        assertEquals(2, credentials.invocations)
        assertTrue(credentials.retiredBuffers.all { bytes -> bytes.all { it == 0.toByte() } })

        val firstMessages = calls.bodies[0].messages()
        assertEquals(listOf("system", "user"), firstMessages.map { it.first })
        assertEquals(listOf("rules", "first"), firstMessages.map { it.second })

        val secondMessages = calls.bodies[1].messages()
        assertEquals(
            listOf("system", "user", "assistant", "user"),
            secondMessages.map { it.first },
        )
        assertEquals(
            listOf("rules", "first", "Hello world", "second"),
            secondMessages.map { it.second },
        )
    }

    @Test
    fun jsonFallbackCompletesThroughTheSameStreamingListener() {
        val calls = ScriptedCallFactory(
            response(
                contentType = "application/problem+json",
                body = """
                    {
                      "choices":[{"message":{"content":"one result"}}],
                      "usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}
                    }
                """.trimIndent(),
            ),
        )
        val listener = RecordingListener()

        session(calls).stream(request(reportUsage = true), listener)

        assertEquals(listOf("one result"), listener.deltas)
        assertEquals(
            GenerationStatistics(
                inputTokens = 3L,
                outputTokens = 2L,
                durationMillis = listener.completed!!.durationMillis,
                contextTokensAfterTurn = 5L,
            ),
            listener.completed,
        )
        assertNull(listener.failure)
    }

    @Test
    fun providerNativeStreamsUseTheirAdaptersAndMergePartialUsage() {
        val anthropicCalls = ScriptedCallFactory(
            response(
                contentType = "text/event-stream",
                body = sse(
                    """{"type":"message_start","message":{"usage":{"input_tokens":5}}}""",
                    """{"type":"content_block_start","content_block":{"type":"text","text":""}}""",
                    """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Claude"}}""",
                    """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":3}}""",
                    """{"type":"message_stop"}""",
                ),
            ),
        )
        val anthropicProfile = profile(
            provider = OnlineAiProvider.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            modelId = "claude-test",
        )
        val anthropicListener = RecordingListener()

        session(anthropicCalls, profile = anthropicProfile)
            .stream(request(reportUsage = true), anthropicListener)

        assertEquals(listOf("Claude"), anthropicListener.deltas)
        assertEquals(5L, anthropicListener.completed?.inputTokens)
        assertEquals(3L, anthropicListener.completed?.outputTokens)
        assertNull(anthropicListener.failure)
        assertEquals("session-key", anthropicCalls.requests.single().header("x-api-key"))

        val geminiCalls = ScriptedCallFactory(
            response(
                contentType = "text/event-stream",
                body = sse(
                    """{"candidates":[{"content":{"parts":[{"text":"Gem"}]}}],"usageMetadata":{"promptTokenCount":6,"candidatesTokenCount":1,"totalTokenCount":9}}""",
                    """{"candidates":[{"content":{"parts":[{"text":"ini"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":6,"candidatesTokenCount":2,"totalTokenCount":10}}""",
                ),
            ),
        )
        val geminiProfile = profile(
            provider = OnlineAiProvider.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            modelId = "gemini-test",
        )
        val geminiListener = RecordingListener()

        session(geminiCalls, profile = geminiProfile)
            .stream(request(reportUsage = true), geminiListener)

        assertEquals(listOf("Gem", "ini"), geminiListener.deltas)
        assertEquals(6L, geminiListener.completed?.inputTokens)
        assertEquals(2L, geminiListener.completed?.outputTokens)
        assertNull(geminiListener.failure)
        assertEquals("session-key", geminiCalls.requests.single().header("x-goog-api-key"))
    }

    @Test
    fun httpAndProviderFailuresAreNormalizedWithoutResponseOrCredentialText() {
        val credential = "private-key"
        val responseSecret = "provider-echoed-private-key"
        val httpCalls = ScriptedCallFactory(
            response(
                code = 401,
                contentType = "application/json",
                body = "{\"error\":{\"message\":\"$responseSecret\"}}",
            ),
        )
        val httpListener = RecordingListener()

        session(httpCalls, ClearingCredentialRunner(credential)).stream(request(), httpListener)

        val httpFailure = httpListener.failure as OnlineAiFailureException
        assertEquals(OnlineAiFailureReason.AUTHENTICATION_FAILED, httpFailure.reason)
        assertEquals(401, httpFailure.statusCode)
        assertFalse(httpFailure.toString().contains(credential))
        assertFalse(httpFailure.toString().contains(responseSecret))
        assertNull(httpListener.completed)

        val providerCalls = ScriptedCallFactory(
            response(
                contentType = "text/event-stream",
                body = sse(
                    "{\"error\":{\"message\":\"$responseSecret\"}}",
                ),
            ),
        )
        val providerListener = RecordingListener()

        session(providerCalls, ClearingCredentialRunner(credential)).stream(request(), providerListener)

        val providerFailure = providerListener.failure as OnlineAiFailureException
        assertEquals(OnlineAiFailureReason.PROVIDER_ERROR, providerFailure.reason)
        assertFalse(providerFailure.toString().contains(credential))
        assertFalse(providerFailure.toString().contains(responseSecret))
    }

    @Test
    fun redirectsAreNotFollowedOrRetried() {
        val calls = ScriptedCallFactory(
            response(code = 307, contentType = "text/plain", body = "redirect"),
        )
        val listener = RecordingListener()

        session(calls).stream(request(), listener)

        val failure = listener.failure as OnlineAiFailureException
        assertEquals(OnlineAiFailureReason.REDIRECT_REFUSED, failure.reason)
        assertEquals(307, failure.statusCode)
        assertEquals(1, calls.requests.size)
    }

    @Test
    fun lowLevelNetworkDetailsAreReplacedByAStableFailure() {
        val secret = "network-secret"
        val calls = ScriptedCallFactory(
            { _, _ -> throw IOException("socket failed while sending $secret") },
        )
        val listener = RecordingListener()

        session(calls, ClearingCredentialRunner(secret)).stream(request(), listener)

        val failure = listener.failure as OnlineAiFailureException
        assertEquals(OnlineAiFailureReason.NETWORK_UNAVAILABLE, failure.reason)
        assertFalse(failure.toString().contains(secret))
    }

    @Test
    fun networkPolicyRunsBeforeCredentialOrHttpAccess() {
        val calls = ScriptedCallFactory()
        val credentials = ClearingCredentialRunner("must-not-be-read")
        val listener = RecordingListener()
        val denied = OnlineAiNetworkAccess {
            throw OnlineAiFailureException(OnlineAiFailureReason.METERED_NETWORK_DISALLOWED)
        }

        session(calls, credentials, networkAccess = denied).stream(request(), listener)

        assertEquals(0, credentials.invocations)
        assertTrue(calls.requests.isEmpty())
        assertEquals(
            OnlineAiFailureReason.METERED_NETWORK_DISALLOWED,
            (listener.failure as OnlineAiFailureException).reason,
        )
    }

    @Test
    fun cancellationCancelsActiveCallSuppressesTerminalCallbacksAndClearsCredential() {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val calls = BlockingCallFactory(entered, released)
        val credentials = ClearingCredentialRunner("cancel-key")
        val listener = RecordingListener()
        val session = session(calls, credentials)
        val worker = Thread {
            session.stream(request(), listener)
        }
        worker.start()
        assertTrue(entered.await(2L, TimeUnit.SECONDS))

        session.cancel()
        released.countDown()
        worker.join(2_000L)

        assertFalse(worker.isAlive)
        assertTrue(calls.cancelled.get())
        assertTrue(listener.deltas.isEmpty())
        assertNull(listener.completed)
        assertNull(listener.failure)
        assertTrue(credentials.retiredBuffers.single().all { it == 0.toByte() })
    }

    @Test
    fun partialFailedTurnIsNotCommittedToTheNextRequest() {
        val calls = ScriptedCallFactory(
            response(
                contentType = "text/event-stream",
                body = sse("{\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}")
                    .removeSuffix("\n\n"),
            ),
            response(
                contentType = "text/event-stream",
                body = sse(
                    "{\"choices\":[{\"delta\":{\"content\":\"recovered\"}}]}",
                    "[DONE]",
                ),
            ),
        )
        val session = session(calls)
        val failed = RecordingListener()
        session.stream(
            request(
                history = listOf(
                    GenerationMessage(GenerationRole.SYSTEM, listOf("base")),
                ),
                prompt = "failed prompt",
            ),
            failed,
        )
        assertEquals(listOf("partial"), failed.deltas)
        assertEquals(
            OnlineAiFailureReason.INVALID_RESPONSE,
            (failed.failure as OnlineAiFailureException).reason,
        )

        val recovered = RecordingListener()
        session.streamNext(request(prompt = "retry prompt"), recovered)

        assertEquals(listOf("recovered"), recovered.deltas)
        assertNull(recovered.failure)
        assertEquals(
            listOf("base", "retry prompt"),
            calls.bodies[1].messages().map { it.second },
        )
    }

    @Test
    fun completionCallbackMaySynchronouslyStartTheNextTurn() {
        val calls = ScriptedCallFactory(
            response(
                contentType = "text/event-stream",
                body = sse(
                    "{\"choices\":[{\"delta\":{\"content\":\"first answer\"}}]}",
                    "[DONE]",
                ),
            ),
            response(
                contentType = "text/event-stream",
                body = sse(
                    "{\"choices\":[{\"delta\":{\"content\":\"second answer\"}}]}",
                    "[DONE]",
                ),
            ),
        )
        val session = session(calls)
        val second = RecordingListener()
        val firstCompleted = AtomicBoolean(false)
        val first = object : GenerationListener {
            override fun onTextDelta(text: String) = Unit

            override fun onCompleted(statistics: GenerationStatistics?) {
                firstCompleted.set(true)
                session.streamNext(request(prompt = "second"), second)
            }

            override fun onFailed(error: Throwable, statistics: GenerationStatistics?) = Unit
        }

        session.stream(request(prompt = "first"), first)

        assertTrue(firstCompleted.get())
        assertEquals(listOf("second answer"), second.deltas)
        assertNull(second.failure)
        assertEquals(2, calls.bodies.size)
    }

    @Test
    fun responseContentTypeAndOutputLimitAreEnforcedBeforeDelivery() {
        val wrongType = RecordingListener()
        session(
            ScriptedCallFactory(response(contentType = "text/plain", body = "not accepted")),
        ).stream(request(), wrongType)
        assertEquals(
            OnlineAiFailureReason.INVALID_RESPONSE,
            (wrongType.failure as OnlineAiFailureException).reason,
        )

        val noChoice = RecordingListener()
        session(
            ScriptedCallFactory(
                response(
                    contentType = "text/event-stream",
                    body = sse(
                        "{\"choices\":[],\"usage\":{\"prompt_tokens\":1," +
                            "\"completion_tokens\":0,\"total_tokens\":1}}",
                        "[DONE]",
                    ),
                ),
            ),
        ).stream(request(reportUsage = true), noChoice)
        assertEquals(
            OnlineAiFailureReason.INVALID_RESPONSE,
            (noChoice.failure as OnlineAiFailureException).reason,
        )

        val oversized = "x".repeat(
            OnlineAiTransportLimits.MAXIMUM_OUTPUT_BYTES.toInt() + 1,
        )
        val oversizedListener = RecordingListener()
        session(
            ScriptedCallFactory(
                response(
                    contentType = "application/json",
                    body = "{\"choices\":[{\"message\":{\"content\":\"$oversized\"}}]}",
                ),
            ),
        ).stream(request(), oversizedListener)
        assertTrue(oversizedListener.deltas.isEmpty())
        assertEquals(
            OnlineAiFailureReason.RESPONSE_TOO_LARGE,
            (oversizedListener.failure as OnlineAiFailureException).reason,
        )
    }

    private fun session(
        calls: Call.Factory,
        credentials: ClearingCredentialRunner = ClearingCredentialRunner("session-key"),
        profile: OnlineAiProfile = profile(),
        networkAccess: OnlineAiNetworkAccess = OnlineAiNetworkAccess.UNRESTRICTED,
    ) = OnlineAiSession(
        target = target(profile),
        profile = profile,
        callFactory = calls,
        networkAccess = networkAccess,
        credentialRunner = credentials,
    )

    private fun request(
        history: List<GenerationMessage> = emptyList(),
        prompt: String = "hello",
        reportUsage: Boolean = false,
    ) = GenerationRequest(
        history = history,
        prompt = GenerationMessage(GenerationRole.USER, listOf(prompt)),
        maximumOutputTokens = null,
        samplingOptions = null,
        reportUsage = reportUsage,
    )

    private fun profile(
        provider: OnlineAiProvider = OnlineAiProvider.OPENAI_COMPATIBLE,
        baseUrl: String = "https://example.com/v1",
        modelId: String = "remote-model",
    ) = OnlineAiProfile(
        profileId = PROFILE_ID,
        displayName = "Remote",
        provider = provider,
        baseUrl = baseUrl,
        modelId = modelId,
    )

    private fun target(profile: OnlineAiProfile = profile()) = AiTarget(
        targetId = AiTargetIds.profile(PROFILE_ID),
        backendId = OnlineAiBackend.BACKEND_ID,
        providerId = profile.provider.providerId,
        profileId = PROFILE_ID,
        modelId = profile.modelId,
        displayName = profile.displayName,
        locality = AiTargetLocality.REMOTE,
        credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf(profile.declaredHttpsOrigin),
        configured = true,
        available = true,
        capabilities = AiTargetCapabilities(true, true, true, true, false, false),
        limits = AiTargetLimits(
            OnlineAiTransportLimits.MAXIMUM_CONTEXT_BYTES,
            OnlineAiTransportLimits.MAXIMUM_OUTPUT_BYTES,
        ),
        executionProfiles = emptyList(),
    )

    private fun response(
        code: Int = 200,
        contentType: String,
        body: String,
    ): (Request, FakeCall) -> Response = { request, _ ->
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Failure")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()
    }

    private fun sse(vararg payloads: String): String = payloads.joinToString(separator = "") { payload ->
        "data: $payload\n\n"
    }

    private fun String.messages(): List<Pair<String, String>> =
        JsonParser.parseString(this).asJsonObject.getAsJsonArray("messages").map { element ->
            val message = element.asJsonObject
            message.get("role").asString to message.get("content").asString
        }

    private class RecordingListener : GenerationListener {
        val deltas = CopyOnWriteArrayList<String>()

        @Volatile
        var completed: GenerationStatistics? = null

        @Volatile
        var failure: Throwable? = null

        override fun onTextDelta(text: String) {
            deltas += text
        }

        override fun onCompleted(statistics: GenerationStatistics?) {
            completed = statistics
        }

        override fun onFailed(error: Throwable, statistics: GenerationStatistics?) {
            failure = error
        }
    }

    private class ClearingCredentialRunner(secret: String) : OnlineAiCredentialRunner {
        private val secret = secret.toByteArray()
        val retiredBuffers = CopyOnWriteArrayList<ByteArray>()

        @Volatile
        var invocations = 0

        override fun run(action: (ByteArray) -> Unit) {
            val current = secret.copyOf()
            invocations += 1
            try {
                action(current)
            } finally {
                current.fill(0)
                retiredBuffers += current
            }
        }
    }

    private class ScriptedCallFactory(
        vararg scripts: (Request, FakeCall) -> Response,
    ) : Call.Factory {
        private val scripts = ArrayDeque(scripts.toList())
        val requests = CopyOnWriteArrayList<Request>()
        val bodies = CopyOnWriteArrayList<String>()

        override fun newCall(request: Request): Call {
            requests += request
            val script = scripts.removeFirst()
            return FakeCall(request) { call ->
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                bodies += buffer.readUtf8()
                script(request, call)
            }
        }
    }

    private class BlockingCallFactory(
        private val entered: CountDownLatch,
        private val released: CountDownLatch,
    ) : Call.Factory {
        val cancelled = AtomicBoolean(false)

        override fun newCall(request: Request): Call = FakeCall(
            request = request,
            onCancel = {
                cancelled.set(true)
                released.countDown()
            },
        ) { call ->
            val body = Buffer()
            request.body?.writeTo(body)
            body.clear()
            entered.countDown()
            released.await(2L, TimeUnit.SECONDS)
            if (call.isCanceled()) throw IOException("sensitive network detail")
            throw IOException("blocking test unexpectedly released")
        }
    }

    private class FakeCall(
        private val request: Request,
        private val onCancel: () -> Unit = {},
        private val executeAction: (FakeCall) -> Response,
    ) : Call {
        private val executed = AtomicBoolean(false)
        private val cancelled = AtomicBoolean(false)

        override fun request(): Request = request

        override fun execute(): Response {
            check(executed.compareAndSet(false, true))
            if (cancelled.get()) throw IOException("cancelled")
            return executeAction(this)
        }

        override fun enqueue(responseCallback: Callback) {
            try {
                responseCallback.onResponse(this, execute())
            } catch (error: IOException) {
                responseCallback.onFailure(this, error)
            }
        }

        override fun cancel() {
            if (cancelled.compareAndSet(false, true)) onCancel()
        }

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = cancelled.get()

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = FakeCall(request, onCancel, executeAction)
    }

    private companion object {
        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    }
}
