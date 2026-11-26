package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderSecrets
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderValueKind
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Custom request headers on the wire (P11.4 - P11.6): every protocol sends them after the plugin's own
 * headers without rewriting those, the "per conversation" value follows T73, and provider details never
 * carry a secret header value or the API Key. All keys and values are placeholders (D170).
 */
class OnlineAiRequestHeadersTest {
    @Test
    fun everyProtocolSendsTheConfiguredHeadersAndKeepsItsOwnValues() {
        Protocols.entries.forEach { protocol ->
            val plain = capture(protocol, headers = emptyList())
            val custom = capture(protocol, headers = STANDARD_HEADERS, secrets = mapOf("x-token" to SECRET_VALUE))

            assertEquals(protocol.name, listOf("tenant-a"), custom.headers("X-Tenant"))
            assertEquals(protocol.name, listOf(SECRET_VALUE), custom.headers("X-Token"))
            assertEquals(
                protocol.name,
                listOf(OnlineAiConversationSessionIds.derive(PROFILE_ID, CONVERSATION_KEY)),
                custom.headers("X-Session"),
            )
            // The custom names keep the user's spelling and come after every header of the plugin.
            val names = custom.headerNames()
            assertEquals(protocol.name, plain.headerNames() + listOf("X-Tenant", "X-Token", "X-Session"), names)
            plain.headerNames().forEach { name ->
                assertEquals("${protocol.name} $name", plain.headers(name), custom.headers(name))
            }
            assertEquals(protocol.name, listOf(protocol.credentialValue), custom.headers(protocol.credentialHeader))
        }
    }

    @Test
    fun aCustomUserAgentReplacesTheDefaultOnEveryProtocol() {
        Protocols.entries.forEach { protocol ->
            assertEquals(
                listOf(OnlineAiRequestSupport.DEFAULT_USER_AGENT),
                capture(protocol, headers = emptyList()).headers("User-Agent"),
            )
            val request = capture(
                protocol,
                headers = listOf(OnlineAiCustomHeader("user-agent", OnlineAiHeaderValueKind.FIXED, "Placeholder-Agent/2")),
            )
            assertEquals(protocol.name, listOf("Placeholder-Agent/2"), request.headers("User-Agent"))
        }
    }

    @Test
    fun aCustomAnthropicBetaIsMergedWithTheFastModeBetaWithoutRepeatingFlags() {
        val beta = listOf(
            OnlineAiCustomHeader(
                "Anthropic-Beta",
                OnlineAiHeaderValueKind.FIXED,
                "placeholder-beta-1, ${OnlineAiEffortSupport.ANTHROPIC_FAST_BETA},placeholder-beta-2",
            ),
        )

        val fast = capture(Protocols.ANTHROPIC, headers = beta, fast = true)
        assertEquals(
            listOf("${OnlineAiEffortSupport.ANTHROPIC_FAST_BETA},placeholder-beta-1,placeholder-beta-2"),
            fast.headers("anthropic-beta"),
        )
        assertEquals(listOf(OnlineAiEffortSupport.ANTHROPIC_FAST_BETA), capture(Protocols.ANTHROPIC, emptyList(), fast = true).headers("anthropic-beta"))

        // Without a beta of the plugin the custom value is an ordinary header, unchanged.
        val standard = capture(Protocols.ANTHROPIC, headers = beta)
        assertEquals(listOf(beta.single().value), standard.headers("anthropic-beta"))
        val openAi = capture(Protocols.OPENAI, headers = beta)
        assertEquals(listOf(beta.single().value), openAi.headers("anthropic-beta"))
    }

    @Test
    fun aSecretHeaderWithoutAStoredValueIsLeftOut() {
        val request = capture(Protocols.OPENAI, headers = STANDARD_HEADERS)

        assertNull(request.header("X-Token"))
        assertEquals("tenant-a", request.header("X-Tenant"))
        assertEquals(listOf("X-Tenant", "X-Session"), request.headerNames().takeLast(2))
    }

    @Test
    fun outgoingHeadersRejectEveryRepeatedOrReservedName() {
        val outgoing = OnlineAiOutgoingHeaders(Request.Builder())
        outgoing.plugin("Accept", "text/event-stream")
        outgoing.plugin("X-Protocol-Only", "1")

        assertThrows(IllegalStateException::class.java) { outgoing.plugin("accept", "application/json") }
        assertThrows(IllegalArgumentException::class.java) {
            outgoing.custom(OnlineAiRequestHeaders.of("x-protocol-only" to "2"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiOutgoingHeaders(Request.Builder())
                .custom(OnlineAiRequestHeaders.of("X-Token" to "a", "x-token" to "b"))
        }
        listOf("Authorization", "x-api-key", "X-Goog-Api-Key", "anthropic-version", "Content-Type", "Host").forEach { name ->
            assertThrows(name, IllegalArgumentException::class.java) { OnlineAiRequestHeaders.of(name to "value") }
        }
        assertThrows(IllegalArgumentException::class.java) { OnlineAiRequestHeaders.of("X-Token" to " padded") }
    }

    @Test
    fun anthropicBetaFlagsAreMergedInOrderWithoutEmptyOrRepeatedFlags() {
        assertEquals("a,b", OnlineAiOutgoingHeaders.mergeAnthropicBeta("a", "b"))
        assertEquals("a,b,c", OnlineAiOutgoingHeaders.mergeAnthropicBeta("a,b", " b , ,c,a"))
        assertEquals("a", OnlineAiOutgoingHeaders.mergeAnthropicBeta("a", ","))
    }

    @Test
    fun theConversationSessionIdFollowsT73() {
        val digest = MessageDigest.getInstance("SHA-256").run {
            update("3-stone/x-session/v1".toByteArray())
            update(0.toByte())
            update(PROFILE_ID.toByteArray())
            update(0.toByte())
            update(CONVERSATION_KEY.toByteArray())
            digest()
        }
        val expected = "ses_" + digest.take(16).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        val actual = OnlineAiConversationSessionIds.derive(PROFILE_ID, CONVERSATION_KEY)

        assertEquals(expected, actual)
        assertTrue(actual.matches(Regex("ses_[0-9a-f]{32}")))
        assertThrows(IllegalArgumentException::class.java) { OnlineAiConversationSessionIds.derive(PROFILE_ID, "") }
    }

    @Test
    fun theSameConversationKeepsItsSessionIdAcrossRebuildsModelSwitchesAndRestarts() {
        fun sessionHeader(
            key: String?,
            profileId: String = PROFILE_ID,
            modelId: String = "remote-model",
        ): String = checkNotNull(
            capture(
                Protocols.OPENAI,
                headers = STANDARD_HEADERS,
                conversationKey = key,
                profileId = profileId,
                modelId = modelId,
            ).header("X-Session"),
        )

        val first = sessionHeader(CONVERSATION_KEY)
        // A rebuilt backend after compaction and a restored one after a process restart are new sessions
        // with the same key; switching the model keeps the service.
        assertEquals(first, sessionHeader(CONVERSATION_KEY))
        assertEquals(first, sessionHeader(CONVERSATION_KEY, modelId = "remote-model-b"))

        assertNotEquals(first, sessionHeader("conversation-2"))
        assertNotEquals(first, sessionHeader(CONVERSATION_KEY, profileId = OTHER_PROFILE_ID))
        // Sessions without a conversation (connection tests) get a new ID every time.
        val once = sessionHeader(null)
        val again = sessionHeader(null)
        assertNotEquals(once, again)
        assertTrue(once.matches(Regex("ses_[0-9a-f]{32}")))
    }

    @Test
    fun oneSessionKeepsItsConversationIdForEveryTurn() {
        val calls = ScriptedCallFactory(Protocols.OPENAI.success(), Protocols.OPENAI.success())
        val profile = profile(Protocols.OPENAI, STANDARD_HEADERS)
        val session = session(calls, profile, emptyMap(), conversationKey = null)

        session.stream(request(), RecordingListener())
        session.streamNext(request(prompt = "again"), RecordingListener())

        assertEquals(2, calls.requests.size)
        assertEquals(calls.requests[0].header("X-Session"), calls.requests[1].header("X-Session"))
    }

    @Test
    fun aRejectedRequestThatEchoesSecretsKeepsThemOutOfTheProviderDetail() {
        val spacedSecret = "placeholder  spaced-secret"
        val json = response(
            code = 400,
            contentType = "application/json",
            body = """{"error":{"type":"invalid_request_error","message":"Bad header X-Token: $SECRET_VALUE (key $API_KEY)"}}""",
        )
        val text = response(
            code = 403,
            contentType = "text/plain",
            body = "Rejected X-Other: $spacedSecret with X-Token: $SECRET_VALUE",
        )
        val headers = STANDARD_HEADERS + OnlineAiCustomHeader("X-Other", OnlineAiHeaderValueKind.SECRET)
        val secrets = mapOf("x-token" to SECRET_VALUE, "x-other" to spacedSecret)

        val jsonListener = RecordingListener()
        session(ScriptedCallFactory(json), profile(Protocols.ANTHROPIC, headers), secrets)
            .stream(request(), jsonListener)
        val jsonFailure = jsonListener.failure as OnlineAiFailureException
        assertEquals(400, jsonFailure.statusCode)
        assertEquals(
            "invalid_request_error: Bad header X-Token: [redacted] (key [redacted])",
            jsonFailure.providerDetail,
        )

        val textListener = RecordingListener()
        session(ScriptedCallFactory(text), profile(Protocols.GEMINI, headers), secrets)
            .stream(request(), textListener)
        val textFailure = textListener.failure as OnlineAiFailureException
        assertEquals(403, textFailure.statusCode)
        assertEquals("Rejected X-Other: [redacted] with X-Token: [redacted]", textFailure.providerDetail)

        listOf(jsonFailure, textFailure).forEach { failure ->
            listOf(SECRET_VALUE, API_KEY, "spaced-secret").forEach { secret ->
                assertFalse(failure.toString().contains(secret))
                assertFalse(failure.message.orEmpty().contains(secret))
                assertFalse(failure.providerDetail.orEmpty().contains(secret))
            }
        }
    }

    @Test
    fun resolvedHeadersNeverPrintTheirValues() {
        val headers = OnlineAiRequestHeaders.of("X-Token" to SECRET_VALUE)

        assertFalse(headers.toString().contains(SECRET_VALUE))
        assertFalse(headers.entries.single().toString().contains(SECRET_VALUE))
        assertTrue(headers.toString().contains("X-Token"))
    }

    private enum class Protocols(
        val provider: OnlineAiProvider,
        val baseUrl: String,
        val modelId: String,
        val credentialHeader: String,
        val credentialValue: String,
        private val sse: List<String>,
    ) {
        OPENAI(
            OnlineAiProvider.OPENAI_COMPATIBLE,
            "https://example.com/v1",
            "remote-model",
            "Authorization",
            "Bearer $API_KEY",
            listOf("""{"choices":[{"delta":{"content":"OK"}}]}""", "[DONE]"),
        ),
        ANTHROPIC(
            OnlineAiProvider.ANTHROPIC,
            "https://api.anthropic.com/v1",
            "claude-test",
            "x-api-key",
            API_KEY,
            listOf(
                """{"type":"message_start","message":{"usage":{"input_tokens":5}}}""",
                """{"type":"content_block_start","content_block":{"type":"text","text":""}}""",
                """{"type":"content_block_delta","delta":{"type":"text_delta","text":"OK"}}""",
                """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}""",
                """{"type":"message_stop"}""",
            ),
        ),
        GEMINI(
            OnlineAiProvider.GEMINI,
            "https://generativelanguage.googleapis.com/v1beta",
            "gemini-test",
            "x-goog-api-key",
            API_KEY,
            listOf(
                """{"candidates":[{"content":{"parts":[{"text":"OK"}]},"finishReason":"STOP"}],""" +
                    """"usageMetadata":{"promptTokenCount":6,"candidatesTokenCount":1,"totalTokenCount":7}}""",
            ),
        ),
        ;

        fun success(): (Request) -> Response =
            response(contentType = "text/event-stream", body = sse.joinToString("") { payload -> "data: $payload\n\n" })
    }

    private fun capture(
        protocol: Protocols,
        headers: List<OnlineAiCustomHeader>,
        secrets: Map<String, String> = emptyMap(),
        fast: Boolean? = null,
        conversationKey: String? = CONVERSATION_KEY,
        profileId: String = PROFILE_ID,
        modelId: String = protocol.modelId,
    ): Request {
        val calls = ScriptedCallFactory(protocol.success())
        val listener = RecordingListener()
        session(calls, profile(protocol, headers, profileId, modelId), secrets, conversationKey)
            .stream(request(fast = fast), listener)
        assertNull(listener.failure)
        return calls.requests.single()
    }

    private fun session(
        calls: Call.Factory,
        profile: OnlineAiProfile,
        secrets: Map<String, String>,
        conversationKey: String? = CONVERSATION_KEY,
    ) = OnlineAiSession(
        target = target(profile),
        profile = profile,
        callFactory = calls,
        credentialRunner = SecretCredentialRunner(API_KEY, secrets),
        conversationKey = conversationKey,
    )

    private fun profile(
        protocol: Protocols,
        headers: List<OnlineAiCustomHeader>,
        profileId: String = PROFILE_ID,
        modelId: String = protocol.modelId,
    ) = OnlineAiProfile(
        profileId = profileId,
        displayName = "Remote",
        provider = protocol.provider,
        baseUrl = protocol.baseUrl,
        modelId = modelId,
        headers = headers,
    )

    private fun target(profile: OnlineAiProfile) = AiTarget(
        targetId = AiTargetIds.profile(profile.profileId),
        backendId = OnlineAiBackend.BACKEND_ID,
        providerId = profile.provider.providerId,
        profileId = profile.profileId,
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

    private fun request(prompt: String = "hello", fast: Boolean? = null) = GenerationRequest(
        history = emptyList(),
        prompt = GenerationMessage(GenerationRole.USER, listOf(prompt)),
        maximumOutputTokens = null,
        samplingOptions = null,
        reportUsage = false,
        fast = fast,
    )

    private fun Request.headerNames(): List<String> = (0 until headers.size).map(headers::name)

    /** Hands over the placeholder key and secret header values together, like the profile store. */
    private class SecretCredentialRunner(
        private val credential: String,
        private val secrets: Map<String, String>,
    ) : OnlineAiCredentialRunner {
        override fun run(action: (ByteArray) -> Unit) {
            runWithHeaderSecrets { credential, _ -> action(credential) }
        }

        override fun runWithHeaderSecrets(action: (ByteArray, OnlineAiHeaderSecrets) -> Unit) {
            val key = credential.toByteArray()
            val values = OnlineAiHeaderSecrets.takingOwnership(
                secrets.mapValues { (_, value) -> value.toByteArray(Charsets.US_ASCII) },
            )
            try {
                action(key, values)
            } finally {
                key.fill(0)
                values.close()
            }
        }
    }

    private class RecordingListener : GenerationListener {
        @Volatile
        var failure: Throwable? = null

        override fun onTextDelta(text: String) = Unit

        override fun onCompleted(statistics: GenerationStatistics?) = Unit

        override fun onFailed(error: Throwable, statistics: GenerationStatistics?) {
            failure = error
        }
    }

    private class ScriptedCallFactory(
        vararg scripts: (Request) -> Response,
    ) : Call.Factory {
        private val scripts = ArrayDeque(scripts.toList())
        val requests = CopyOnWriteArrayList<Request>()

        override fun newCall(request: Request): Call {
            requests += request
            val script = scripts.removeFirst()
            return FakeCall(request) {
                Buffer().use { buffer -> request.body?.writeTo(buffer) }
                script(request)
            }
        }
    }

    private class FakeCall(
        private val request: Request,
        private val executeAction: () -> Response,
    ) : Call {
        private val executed = AtomicBoolean(false)
        private val cancelled = AtomicBoolean(false)

        override fun request(): Request = request

        override fun execute(): Response {
            check(executed.compareAndSet(false, true))
            if (cancelled.get()) throw IOException("cancelled")
            return executeAction()
        }

        override fun enqueue(responseCallback: Callback) {
            try {
                responseCallback.onResponse(this, execute())
            } catch (error: IOException) {
                responseCallback.onFailure(this, error)
            }
        }

        override fun cancel() {
            cancelled.set(true)
        }

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = cancelled.get()

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = FakeCall(request, executeAction)
    }

    private companion object {
        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
        const val OTHER_PROFILE_ID = "22222222-2222-4222-8222-222222222222"
        const val CONVERSATION_KEY = "conversation-1"
        const val API_KEY = "placeholder-api-key"
        const val SECRET_VALUE = "placeholder-secret-token"

        val STANDARD_HEADERS = listOf(
            OnlineAiCustomHeader("X-Tenant", OnlineAiHeaderValueKind.FIXED, "tenant-a"),
            OnlineAiCustomHeader("X-Token", OnlineAiHeaderValueKind.SECRET),
            OnlineAiCustomHeader("X-Session", OnlineAiHeaderValueKind.CONVERSATION),
        )

        fun response(code: Int = 200, contentType: String, body: String): (Request) -> Response = { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Failure")
                .body(body.toResponseBody(contentType.toMediaType()))
                .build()
        }
    }
}
