package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileChangedException
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileCredentialAccess
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfilePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProviderCatalog
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import java.io.Closeable
import java.io.InterruptedIOException
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException

internal fun interface OnlineAiCredentialRunner {
    fun run(action: (ByteArray) -> Unit)
}

/** Production HTTPS execution boundary shared by all online provider protocols. */
internal class OnlineAiHttpExecution private constructor(
    private val callFactory: Call.Factory,
    private val ownedClient: OkHttpClient?,
    private val networkAccess: OnlineAiNetworkAccess,
) : OnlineAiExecution, Closeable {
    private val closed = AtomicBoolean(false)

    private val baseCapabilities = AiTargetCapabilities(
        streaming = true,
        persistentSession = true,
        structuredJson = true,
        usage = true,
        reasoning = false,
        tools = true,
    )

    private val transportLimits = AiTargetLimits(
        maximumContextBytes = OnlineAiTransportLimits.MAXIMUM_CONTEXT_BYTES,
        maximumOutputBytes = OnlineAiTransportLimits.MAXIMUM_OUTPUT_BYTES,
        maximumOutputTokens = null,
    )

    override val available: Boolean
        get() = !closed.get()

    internal constructor(
        callFactory: Call.Factory,
        networkAccess: OnlineAiNetworkAccess = OnlineAiNetworkAccess.UNRESTRICTED,
    ) : this(callFactory, null, networkAccess)

    override fun supports(profile: OnlineAiProfile): Boolean = runCatching {
        val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
        OnlineAiProtocolAdapters.forProfile(normalized)
    }.isSuccess

    override fun capabilities(profile: OnlineAiProfile): AiTargetCapabilities {
        val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
        OnlineAiProtocolAdapters.forProfile(normalized)
        return baseCapabilities.copy(
            structuredJson = OnlineAiProviderCatalog.templateFor(normalized.provider).structuredJson,
            vision = normalized.modelId in normalized.visionModelIds,
        )
    }

    override fun limits(profile: OnlineAiProfile): AiTargetLimits {
        val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
        OnlineAiProtocolAdapters.forProfile(normalized)
        return transportLimits
    }

    override fun createSession(
        target: AiTarget,
        profile: OnlineAiProfile,
        credentialAccess: OnlineAiProfileCredentialAccess,
    ): AiBackendSession {
        if (!available) {
            throw OnlineAiFailureException(OnlineAiFailureReason.EXECUTION_CLOSED)
        }
        val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
        require(supports(normalized)) { "Online AI profile protocol is unsupported" }
        require(AiTargetIds.requireProfileId(target.targetId) == normalized.profileId)
        require(target.profileId == normalized.profileId)
        require(target.modelId == normalized.modelId)
        require(target.declaredHttpsOrigins == listOf(normalized.declaredHttpsOrigin))
        require(credentialAccess.profile.profileId == normalized.profileId)
        require(credentialAccess.profile.provider == normalized.provider)
        require(credentialAccess.profile.baseUrl == normalized.baseUrl)
        require(normalized.modelId in credentialAccess.profile.modelIds)
        return OnlineAiSession(
            target = target,
            profile = normalized,
            callFactory = callFactory,
            executionAvailable = { this@OnlineAiHttpExecution.available },
            networkAccess = networkAccess,
            credentialRunner = OnlineAiCredentialRunner { action ->
                credentialAccess.withCredential { credential -> action(credential) }
            },
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ownedClient?.let(OnlineAiHttpClient::close)
    }

    companion object {
        fun create(
            networkAccess: OnlineAiNetworkAccess = OnlineAiNetworkAccess.UNRESTRICTED,
        ): OnlineAiHttpExecution {
            val client = OnlineAiHttpClient.create()
            return OnlineAiHttpExecution(client, client, networkAccess)
        }
    }
}

internal class OnlineAiSession(
    override val target: AiTarget,
    profile: OnlineAiProfile,
    private val callFactory: Call.Factory,
    private val executionAvailable: () -> Boolean = { true },
    private val networkAccess: OnlineAiNetworkAccess = OnlineAiNetworkAccess.UNRESTRICTED,
    private val credentialRunner: OnlineAiCredentialRunner,
) : AiBackendSession {
    private val profile = OnlineAiProfilePolicy.normalizeProfile(profile)
    private val adapter = OnlineAiProtocolAdapters.forProfile(this.profile)
    private val stateLock = Any()
    private val callbackGate = CallbackQuiescenceGate()
    private val started = AtomicBoolean(false)
    private val turnActive = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val activeCall = AtomicReference<Call?>()
    private val conversation = mutableListOf<GenerationMessage>()
    private var pendingTools: PendingTools? = null
    private var toolRounds = 0
    private val seenToolIds = mutableSetOf<String>()

    private class PendingTools(
        val request: GenerationRequest,
        val calls: List<GenerationToolCall>,
        val listener: GenerationListener,
    )

    override fun stream(request: GenerationRequest, listener: GenerationListener) {
        check(started.compareAndSet(false, true)) { "Online AI session was already started" }
        check(request.prompt.role == GenerationRole.USER) {
            "The final online AI prompt must be a user message"
        }
        runTurn(request, listener, firstTurn = true)
    }

    override fun streamNext(request: GenerationRequest, listener: GenerationListener) {
        check(started.get()) { "Online AI session has not been started" }
        synchronized(stateLock) { check(pendingTools == null) { "An online AI tool turn is outstanding" } }
        check(request.history.isEmpty()) { "A continued online AI turn must not resend history" }
        check(request.prompt.role == GenerationRole.USER) {
            "A continued online AI prompt must be a user message"
        }
        runTurn(request, listener, firstTurn = false)
    }

    override fun submitToolResults(results: List<GenerationToolResult>) {
        val (pending, prompt) = synchronized(stateLock) {
            check(!isStopped()) { "Online AI session is closed" }
            check(!turnActive.get()) { "Online AI generation is still active" }
            val pending = checkNotNull(pendingTools) { "No online AI tool turn is outstanding" }
            val prompt = OnlineAiTools.resultsMessage(profile.provider.protocol, pending.calls, results.toList())
            pendingTools = null
            pending to prompt
        }
        runTurn(pending.request.copy(history = emptyList(), prompt = prompt), pending.listener, firstTurn = false)
    }

    private fun runTurn(
        rawRequest: GenerationRequest,
        listener: GenerationListener,
        firstTurn: Boolean,
    ) {
        check(turnActive.compareAndSet(false, true)) {
            "Online AI generation turn is already active"
        }
        var turnReleased = false
        fun releaseTurn() {
            if (!turnReleased) {
                turnActive.set(false)
                turnReleased = true
            }
        }
        val startedNanos = System.nanoTime()
        val progress = TurnProgress(OnlineAiToolCollector(profile.provider.protocol, rawRequest.tools))
        val delivery = TurnDelivery(listener)
        try {
            if (isStopped()) return
            networkAccess.requireAccess()
            if (isStopped()) return
            val request = rawRequest.snapshot()
            val outbound = synchronized(stateLock) {
                if (firstTurn) {
                    conversation.clear()
                    conversation.addAll(request.history)
                }
                conversation.map { message -> message.snapshot() } + request.prompt
            }

            credentialRunner.run credential@{ credential ->
                if (isStopped()) return@credential
                adapter.prepare(
                    profile = profile,
                    messages = outbound,
                    turn = request,
                    credential = credential,
                ).use { prepared ->
                    if (isStopped()) return@use
                    val call = callFactory.newCall(prepared.request)
                    check(activeCall.compareAndSet(null, call)) {
                        "Online AI generation Call is already active"
                    }
                    try {
                        if (isStopped()) {
                            call.cancel()
                            return@use
                        }
                        consume(call, progress, delivery)
                    } finally {
                        activeCall.compareAndSet(call, null)
                    }
                }
            }
            if (isStopped()) return

            val (calls, nativeMessage) = progress.tools.finish(progress.text())
            // Every tool round keeps the caller's output ceiling: the continuation method carries no new ceiling,
            // callers reserve it again per round and bound the whole turn with their own budget (3-Stove Agent
            // roadmap D51, 2026-09-29). Subtracting earlier rounds starved long turns after about ten rounds.
            val nextRequest = request
            val committed = synchronized(stateLock) {
                if (isStopped()) {
                    false
                } else {
                    if (calls.isNotEmpty()) {
                        check(toolRounds < request.maximumToolRounds) { "Online AI tool round limit exceeded" }
                        check(calls.none { it.callId in seenToolIds }) { "Online AI tool call ID was replayed" }
                        toolRounds += 1
                        seenToolIds.addAll(calls.map { it.callId })
                        pendingTools = PendingTools(nextRequest, calls, listener)
                    }
                    conversation.add(request.prompt.snapshot())
                    conversation.add(
                        GenerationMessage(
                            role = GenerationRole.ASSISTANT,
                            textParts = listOf(progress.text()),
                            nativeToolMessage = nativeMessage,
                        ),
                    )
                    true
                }
            }
            if (!committed) return
            releaseTurn()
            val statistics = progress.statistics(rawRequest.reportUsage, startedNanos)
            if (calls.isEmpty()) delivery.complete(statistics) else delivery.tools(calls, statistics)
        } catch (error: Exception) {
            if (!isStopped()) {
                releaseTurn()
                delivery.fail(
                    normalizeFailure(error),
                    progress.statistics(rawRequest.reportUsage, startedNanos),
                )
            }
        } finally {
            releaseTurn()
        }
    }

    private fun consume(
        call: Call,
        progress: TurnProgress,
        delivery: TurnDelivery,
    ) {
        call.execute().use { response ->
            if (!response.isSuccessful) {
                runCatching {
                    OnlineAiResponseSupport.discardErrorBody(response.body)
                }
                throw failureForOnlineAiHttpStatus(response.code)
            }
            val body = response.body ?: invalidResponse()
            when {
                body.isEventStream() -> consumeEventStream(body, progress, delivery)
                body.isJson() -> consumeJson(body, progress, delivery)
                else -> invalidResponse()
            }
        }
    }

    private fun consumeEventStream(
        body: ResponseBody,
        progress: TurnProgress,
        delivery: TurnDelivery,
    ) {
        var terminalSeen = false
        OnlineAiSseReader(body.source()).use { reader ->
            while (!isStopped()) {
                val event = reader.readEvent() ?: break
                val chunk = adapter.parseEvent(event, progress.tools)
                if (chunk.contentSeen) progress.recordContent()
                chunk.usage?.let(progress::recordUsage)
                if (chunk.text.isNotEmpty()) {
                    progress.append(chunk.text)
                    delivery.text(chunk.text)
                }
                if (chunk.done) {
                    terminalSeen = true
                    break
                }
            }
        }
        if ((!terminalSeen || !progress.contentSeen) && !isStopped()) invalidResponse()
    }

    private fun consumeJson(
        body: ResponseBody,
        progress: TurnProgress,
        delivery: TurnDelivery,
    ) {
        val response = adapter.parseJson(body, progress.tools)
        response.usage?.let(progress::recordUsage)
        if (response.text.isNotEmpty()) {
            progress.append(response.text)
            delivery.text(response.text)
        }
    }

    override fun cancel() {
        synchronized(stateLock) {
            cancelled.set(true)
            pendingTools = null
            conversation.clear()
        }
        activeCall.get()?.cancel()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        callbackGate.stopDelivering()
        cancel()
        callbackGate.awaitQuiescenceAndSeal()
    }

    private fun isStopped(): Boolean =
        cancelled.get() || closed.get() || !executionAvailable()

    private fun normalizeFailure(error: Exception): OnlineAiFailureException = when (error) {
        is OnlineAiFailureException -> error
        is AiCredentialUnavailableException -> OnlineAiFailureException(
            OnlineAiFailureReason.CREDENTIAL_UNAVAILABLE,
        )
        is OnlineAiProfileChangedException -> OnlineAiFailureException(
            OnlineAiFailureReason.PROFILE_CHANGED,
        )
        is SocketTimeoutException,
        is InterruptedIOException ->
            OnlineAiFailureException(OnlineAiFailureReason.TIMED_OUT)
        is SSLException -> OnlineAiFailureException(OnlineAiFailureReason.TLS_FAILED)
        is ProtocolException -> OnlineAiFailureException(
            OnlineAiFailureReason.INVALID_RESPONSE,
        )
        is IllegalArgumentException -> OnlineAiFailureException(
            OnlineAiFailureReason.INVALID_REQUEST,
        )
        is IOException -> OnlineAiFailureException(
            OnlineAiFailureReason.NETWORK_UNAVAILABLE,
        )
        else -> OnlineAiFailureException(OnlineAiFailureReason.INVALID_RESPONSE)
    }

    private fun GenerationRequest.snapshot() = copy(
        history = history.map { message -> message.snapshot() },
        prompt = prompt.snapshot(),
        tools = tools.toList(),
    )

    private fun GenerationMessage.snapshot() = copy(textParts = textParts.toList(), images = images.toList())

    private fun ResponseBody.isEventStream(): Boolean = contentType()?.let { type ->
        type.type.equals("text", ignoreCase = true) &&
            type.subtype.equals("event-stream", ignoreCase = true)
    } == true

    private fun ResponseBody.isJson(): Boolean = contentType()?.let { type ->
        type.type.equals("application", ignoreCase = true) &&
            (
                type.subtype.equals("json", ignoreCase = true) ||
                    type.subtype.lowercase().endsWith("+json")
                )
    } == true

    private fun invalidResponse(): Nothing = throw OnlineAiFailureException(
        OnlineAiFailureReason.INVALID_RESPONSE,
    )

    private inner class TurnDelivery(
        private val listener: GenerationListener,
    ) {
        private val terminal = AtomicBoolean(false)

        fun text(value: String) {
            if (terminal.get()) return
            callbackGate.runCallback {
                if (!terminal.get() && !isStopped()) listener.onTextDelta(value)
            }
        }

        fun complete(statistics: GenerationStatistics?) {
            if (!terminal.compareAndSet(false, true)) return
            callbackGate.runCallback {
                if (!isStopped()) listener.onCompleted(statistics)
            }
        }

        fun tools(calls: List<GenerationToolCall>, statistics: GenerationStatistics?) {
            if (!terminal.compareAndSet(false, true)) return
            callbackGate.runCallback {
                if (!isStopped()) listener.onToolCalls(calls, statistics)
            }
        }

        fun fail(error: Throwable, statistics: GenerationStatistics?) {
            if (!terminal.compareAndSet(false, true)) return
            callbackGate.runCallback {
                if (!isStopped()) listener.onFailed(error, statistics)
            }
        }
    }

    private class TurnProgress(val tools: OnlineAiToolCollector) {
        private val output = StringBuilder()
        private var outputBytes = 0L
        private var inputTokens: Long? = null
        private var outputTokens: Long? = null
        private var cachedInputTokens: Long? = null
        private var cacheWriteInputTokens: Long? = null
        private var cacheEligibleInputTokens: Long? = null
        var contentSeen = false
            private set

        fun append(delta: String) {
            val deltaBytes = delta.toByteArray(Charsets.UTF_8).size.toLong()
            val proposed = try {
                Math.addExact(outputBytes, deltaBytes)
            } catch (_: ArithmeticException) {
                throw OnlineAiFailureException(
                    OnlineAiFailureReason.RESPONSE_TOO_LARGE,
                )
            }
            if (proposed > OnlineAiTransportLimits.MAXIMUM_OUTPUT_BYTES) {
                throw OnlineAiFailureException(
                    OnlineAiFailureReason.RESPONSE_TOO_LARGE,
                )
            }
            output.append(delta)
            outputBytes = proposed
        }

        fun recordUsage(value: OnlineAiUsageUpdate) {
            value.inputTokens?.let { inputTokens = it }
            value.outputTokens?.let { outputTokens = it }
            value.cachedInputTokens?.let { cachedInputTokens = it }
            value.cacheWriteInputTokens?.let { cacheWriteInputTokens = it }
            value.cacheEligibleInputTokens?.let { cacheEligibleInputTokens = it }
        }

        fun recordContent() {
            contentSeen = true
        }

        fun text(): String = output.toString()

        fun statistics(reportUsage: Boolean, startedNanos: Long): GenerationStatistics? {
            if (!reportUsage) return null
            val input = inputTokens ?: return null
            val output = outputTokens ?: return null
            return GenerationStatistics(
                inputTokens = input,
                outputTokens = output,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(
                    (System.nanoTime() - startedNanos).coerceAtLeast(0L),
                ),
                contextTokensAfterTurn = Math.addExact(input, output),
                cachedInputTokens = cachedInputTokens,
                cacheWriteInputTokens = cacheWriteInputTokens,
                cacheEligibleInputTokens = cacheEligibleInputTokens,
            )
        }
    }
}
