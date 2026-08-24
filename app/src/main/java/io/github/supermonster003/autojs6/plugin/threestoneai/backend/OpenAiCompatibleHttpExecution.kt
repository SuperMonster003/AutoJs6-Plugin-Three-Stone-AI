package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileChangedException
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileCredentialAccess
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfilePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
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

internal fun interface OpenAiCompatibleCredentialRunner {
    fun run(action: (ByteArray) -> Unit)
}

/** Production OpenAI-compatible HTTPS execution boundary shared by all online targets. */
internal class OpenAiCompatibleHttpExecution private constructor(
    private val callFactory: Call.Factory,
    private val ownedClient: OkHttpClient?,
) : OpenAiCompatibleExecution, Closeable {
    private val closed = AtomicBoolean(false)

    override val capabilities = AiTargetCapabilities(
        streaming = true,
        persistentSession = true,
        structuredJson = true,
        usage = true,
        reasoning = false,
        tools = false,
    )

    override val limits = AiTargetLimits(
        maximumContextBytes = OpenAiCompatibleTransportLimits.MAXIMUM_CONTEXT_BYTES,
        maximumOutputBytes = OpenAiCompatibleTransportLimits.MAXIMUM_OUTPUT_BYTES,
        maximumOutputTokens = null,
    )

    override val available: Boolean
        get() = !closed.get()

    internal constructor(callFactory: Call.Factory) : this(callFactory, null)

    override fun createSession(
        target: AiTarget,
        profile: OnlineAiProfile,
        credentialAccess: OnlineAiProfileCredentialAccess,
    ): AiBackendSession {
        if (!available) {
            throw OpenAiCompatibleFailureException(OpenAiCompatibleFailureReason.EXECUTION_CLOSED)
        }
        val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
        require(normalized.provider == OnlineAiProvider.OPENAI_COMPATIBLE)
        require(target.targetId == AiTargetIds.profile(normalized.profileId))
        require(target.profileId == normalized.profileId)
        require(target.modelId == normalized.modelId)
        require(target.declaredHttpsOrigins == listOf(normalized.declaredHttpsOrigin))
        return OpenAiCompatibleSession(
            target = target,
            profile = normalized,
            callFactory = callFactory,
            executionAvailable = { this@OpenAiCompatibleHttpExecution.available },
            credentialRunner = OpenAiCompatibleCredentialRunner { action ->
                credentialAccess.withCredential { credential -> action(credential) }
            },
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ownedClient?.let(OpenAiCompatibleHttpClient::close)
    }

    companion object {
        fun create(): OpenAiCompatibleHttpExecution {
            val client = OpenAiCompatibleHttpClient.create()
            return OpenAiCompatibleHttpExecution(client, client)
        }
    }
}

internal class OpenAiCompatibleSession(
    override val target: AiTarget,
    profile: OnlineAiProfile,
    private val callFactory: Call.Factory,
    private val executionAvailable: () -> Boolean = { true },
    private val credentialRunner: OpenAiCompatibleCredentialRunner,
) : AiBackendSession {
    private val profile = OnlineAiProfilePolicy.normalizeProfile(profile)
    private val stateLock = Any()
    private val callbackGate = CallbackQuiescenceGate()
    private val started = AtomicBoolean(false)
    private val turnActive = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val activeCall = AtomicReference<Call?>()
    private val conversation = mutableListOf<GenerationMessage>()

    override fun stream(request: GenerationRequest, listener: GenerationListener) {
        check(started.compareAndSet(false, true)) { "Online AI session was already started" }
        check(request.prompt.role == GenerationRole.USER) {
            "The final online AI prompt must be a user message"
        }
        runTurn(request, listener, firstTurn = true)
    }

    override fun streamNext(request: GenerationRequest, listener: GenerationListener) {
        check(started.get()) { "Online AI session has not been started" }
        check(request.history.isEmpty()) { "A continued online AI turn must not resend history" }
        check(request.prompt.role == GenerationRole.USER) {
            "A continued online AI prompt must be a user message"
        }
        runTurn(request, listener, firstTurn = false)
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
        val progress = TurnProgress()
        val delivery = TurnDelivery(listener)
        try {
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
                OpenAiCompatibleRequestFactory.prepare(
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

            val committed = synchronized(stateLock) {
                if (isStopped()) {
                    false
                } else {
                    conversation.add(request.prompt.snapshot())
                    conversation.add(
                        GenerationMessage(
                            role = GenerationRole.ASSISTANT,
                            textParts = listOf(progress.text()),
                        ),
                    )
                    true
                }
            }
            if (!committed) return
            releaseTurn()
            delivery.complete(progress.statistics(rawRequest.reportUsage, startedNanos))
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
                    OpenAiCompatibleResponseParser.discardErrorBody(response.body)
                }
                throw failureForHttpStatus(response.code)
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
        OpenAiCompatibleSseReader(body.source()).use { reader ->
            while (!isStopped()) {
                val event = reader.readEvent() ?: break
                val chunk = OpenAiCompatibleResponseParser.parseEvent(event)
                if (chunk.choiceSeen) progress.recordChoice()
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
        if ((!terminalSeen || !progress.choiceSeen) && !isStopped()) invalidResponse()
    }

    private fun consumeJson(
        body: ResponseBody,
        progress: TurnProgress,
        delivery: TurnDelivery,
    ) {
        val response = OpenAiCompatibleResponseParser.parseJson(body)
        response.usage?.let(progress::recordUsage)
        if (response.text.isNotEmpty()) {
            progress.append(response.text)
            delivery.text(response.text)
        }
    }

    override fun cancel() {
        synchronized(stateLock) {
            cancelled.set(true)
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

    private fun normalizeFailure(error: Exception): OpenAiCompatibleFailureException = when (error) {
        is OpenAiCompatibleFailureException -> error
        is AiCredentialUnavailableException -> OpenAiCompatibleFailureException(
            OpenAiCompatibleFailureReason.CREDENTIAL_UNAVAILABLE,
        )
        is OnlineAiProfileChangedException -> OpenAiCompatibleFailureException(
            OpenAiCompatibleFailureReason.PROFILE_CHANGED,
        )
        is SocketTimeoutException,
        is InterruptedIOException ->
            OpenAiCompatibleFailureException(OpenAiCompatibleFailureReason.TIMED_OUT)
        is SSLException -> OpenAiCompatibleFailureException(OpenAiCompatibleFailureReason.TLS_FAILED)
        is ProtocolException -> OpenAiCompatibleFailureException(
            OpenAiCompatibleFailureReason.INVALID_RESPONSE,
        )
        is IllegalArgumentException -> OpenAiCompatibleFailureException(
            OpenAiCompatibleFailureReason.INVALID_REQUEST,
        )
        is IOException -> OpenAiCompatibleFailureException(
            OpenAiCompatibleFailureReason.NETWORK_UNAVAILABLE,
        )
        else -> OpenAiCompatibleFailureException(OpenAiCompatibleFailureReason.INVALID_RESPONSE)
    }

    private fun GenerationRequest.snapshot() = copy(
        history = history.map { message -> message.snapshot() },
        prompt = prompt.snapshot(),
    )

    private fun GenerationMessage.snapshot() = copy(textParts = textParts.toList())

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

    private fun invalidResponse(): Nothing = throw OpenAiCompatibleFailureException(
        OpenAiCompatibleFailureReason.INVALID_RESPONSE,
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

        fun fail(error: Throwable, statistics: GenerationStatistics?) {
            if (!terminal.compareAndSet(false, true)) return
            callbackGate.runCallback {
                if (!isStopped()) listener.onFailed(error, statistics)
            }
        }
    }

    private class TurnProgress {
        private val output = StringBuilder()
        private var outputBytes = 0L
        private var usage: OpenAiCompatibleUsage? = null
        var choiceSeen = false
            private set

        fun append(delta: String) {
            val deltaBytes = delta.toByteArray(Charsets.UTF_8).size.toLong()
            val proposed = try {
                Math.addExact(outputBytes, deltaBytes)
            } catch (_: ArithmeticException) {
                throw OpenAiCompatibleFailureException(
                    OpenAiCompatibleFailureReason.RESPONSE_TOO_LARGE,
                )
            }
            if (proposed > OpenAiCompatibleTransportLimits.MAXIMUM_OUTPUT_BYTES) {
                throw OpenAiCompatibleFailureException(
                    OpenAiCompatibleFailureReason.RESPONSE_TOO_LARGE,
                )
            }
            output.append(delta)
            outputBytes = proposed
        }

        fun recordUsage(value: OpenAiCompatibleUsage) {
            usage = value
        }

        fun recordChoice() {
            choiceSeen = true
        }

        fun text(): String = output.toString()

        fun statistics(reportUsage: Boolean, startedNanos: Long): GenerationStatistics? {
            if (!reportUsage) return null
            val counters = usage ?: return null
            return GenerationStatistics(
                inputTokens = counters.inputTokens,
                outputTokens = counters.outputTokens,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(
                    (System.nanoTime() - startedNanos).coerceAtLeast(0L),
                ),
            )
        }
    }
}
