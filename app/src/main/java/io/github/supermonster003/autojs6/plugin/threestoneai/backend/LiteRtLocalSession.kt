package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class LiteRtLocalSession(
    override val target: AiTarget,
    private val modelSha256: String,
    private val modelPath: String,
    private val backendProfile: LiteRtLmBackendProfile,
    private val cacheDirectory: File,
    private val engineCache: ReusableResourceCache<EngineCacheKey, Engine>,
) : AiBackendSession {
    override val supportsInPlacePersistentRebuild: Boolean = true

    private val lifecycleLock = Any()
    private val nativeLifecycleLock = Any()
    private val callbackGate = CallbackQuiescenceGate()
    private val started = AtomicBoolean(false)
    private val turnActive = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var engineLease: ResourceLease<Engine>? = null

    @Volatile
    private var conversation: Conversation? = null

    override fun stream(request: GenerationRequest, listener: GenerationListener) {
        check(started.compareAndSet(false, true)) { "LiteRT-LM session was already started" }
        check(request.prompt.role == GenerationRole.USER) { "The final LiteRT-LM prompt must be a user message" }
        try {
            synchronized(nativeLifecycleLock) {
                if (closed.get() || cancelled.get()) return
                val localEngineLease = engineCache.acquire(EngineCacheKey(modelSha256, backendProfile)) {
                    LiteRtLmEngineFactory.create(modelPath, cacheDirectory, backendProfile).also { created ->
                        try {
                            created.initialize()
                        } catch (error: Throwable) {
                            runCatching(created::close)
                            throw error
                        }
                    }
                }
                synchronized(lifecycleLock) { engineLease = localEngineLease }
                if (closed.get() || cancelled.get()) return

                val localConversation = createConversation(localEngineLease.value, request)
                synchronized(lifecycleLock) { conversation = localConversation }
                if (closed.get() || cancelled.get()) return
                runTurn(
                    request = request,
                    listener = listener,
                    localConversation = localConversation,
                    localEngineLease = localEngineLease,
                    tokenBaseline = 0L,
                )
            }
        } catch (error: Throwable) {
            synchronized(lifecycleLock) { engineLease }?.invalidate()
            callbackGate.runCallback {
                if (!closed.get() && !cancelled.get()) listener.onFailed(error, null)
            }
        }
    }

    override fun streamNext(request: GenerationRequest, listener: GenerationListener) {
        check(started.get()) { "LiteRT-LM session has not been started" }
        check(request.history.isEmpty()) { "A continued LiteRT-LM turn must not resend history" }
        check(request.prompt.role == GenerationRole.USER) { "A continued LiteRT-LM prompt must be a user message" }
        try {
            synchronized(nativeLifecycleLock) {
                check(!closed.get() && !cancelled.get()) { "LiteRT-LM session is closed" }
                val localConversation = checkNotNull(synchronized(lifecycleLock) { conversation })
                val localEngineLease = checkNotNull(synchronized(lifecycleLock) { engineLease })
                val tokenBaseline = collectTokenCount(localConversation).getOrElse { error ->
                    localEngineLease.invalidate()
                    throw error
                }
                runTurn(request, listener, localConversation, localEngineLease, tokenBaseline)
            }
        } catch (error: Throwable) {
            synchronized(lifecycleLock) { engineLease }?.invalidate()
            callbackGate.runCallback {
                if (!closed.get() && !cancelled.get()) listener.onFailed(error, null)
            }
        }
    }

    override fun streamRebuilt(request: GenerationRequest, listener: GenerationListener) {
        check(started.get()) { "LiteRT-LM session has not been started" }
        check(request.prompt.role == GenerationRole.USER) {
            "The final LiteRT-LM prompt must be a user message"
        }
        try {
            synchronized(nativeLifecycleLock) {
                check(!closed.get() && !cancelled.get()) { "LiteRT-LM session is closed" }
                check(!turnActive.get()) { "LiteRT-LM generation turn is already active" }
                val localEngineLease = checkNotNull(synchronized(lifecycleLock) { engineLease })
                val previousConversation = checkNotNull(
                    synchronized(lifecycleLock) {
                        conversation.also { conversation = null }
                    },
                )
                try {
                    previousConversation.close()
                } catch (error: Throwable) {
                    localEngineLease.invalidate()
                    throw error
                }
                if (closed.get() || cancelled.get()) return

                val replacement = createConversation(localEngineLease.value, request)
                synchronized(lifecycleLock) { conversation = replacement }
                if (closed.get() || cancelled.get()) return
                runTurn(
                    request = request,
                    listener = listener,
                    localConversation = replacement,
                    localEngineLease = localEngineLease,
                    tokenBaseline = 0L,
                )
            }
        } catch (error: Throwable) {
            synchronized(lifecycleLock) { engineLease }?.invalidate()
            callbackGate.runCallback {
                if (!closed.get() && !cancelled.get()) listener.onFailed(error, null)
            }
        }
    }

    private fun runTurn(
        request: GenerationRequest,
        listener: GenerationListener,
        localConversation: Conversation,
        localEngineLease: ResourceLease<Engine>,
        tokenBaseline: Long,
    ) {
        check(turnActive.compareAndSet(false, true)) { "LiteRT-LM generation turn is already active" }
        val generationStartedNanos = System.nanoTime()
        try {
            localConversation.sendMessageAsync(
                toLiteRtMessage(request.prompt),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        callbackGate.runCallback {
                            val delta = message.contents.contents
                                .filterIsInstance<Content.Text>()
                                .joinToString(separator = "") { it.text }
                            if (delta.isNotEmpty()) listener.onTextDelta(delta)
                        }
                    }

                    override fun onDone() {
                        val statistics = collectStatistics(
                            conversation = localConversation,
                            reportUsage = request.reportUsage,
                            generationStartedNanos = generationStartedNanos,
                            tokenBaseline = tokenBaseline,
                        )
                        statistics.exceptionOrNull()?.let { localEngineLease.invalidate() }
                        turnActive.set(false)
                        callbackGate.runCallback {
                            statistics.fold(
                                onSuccess = listener::onCompleted,
                                onFailure = { error -> listener.onFailed(error, null) },
                            )
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        val statistics = collectStatistics(
                            conversation = localConversation,
                            reportUsage = request.reportUsage,
                            generationStartedNanos = generationStartedNanos,
                            tokenBaseline = tokenBaseline,
                        ).getOrNull()
                        localEngineLease.invalidate()
                        turnActive.set(false)
                        callbackGate.runCallback { listener.onFailed(throwable, statistics) }
                    }
                },
                maxOutputToken = request.maximumOutputTokens,
                responseFormat = request.toLiteRtResponseFormat(),
            )
        } catch (error: Throwable) {
            turnActive.set(false)
            throw error
        }
    }

    override fun cancel() {
        cancelled.set(true)
        synchronized(nativeLifecycleLock) {
            val active = synchronized(lifecycleLock) { conversation }
            runCatching { active?.cancelProcess() }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        callbackGate.stopDelivering()
        cancelled.set(true)
        callbackGate.awaitQuiescenceAndSeal()
        synchronized(nativeLifecycleLock) {
            val activeConversation = synchronized(lifecycleLock) {
                conversation.also { conversation = null }
            }
            val activeEngineLease = synchronized(lifecycleLock) {
                engineLease.also { engineLease = null }
            }
            if (turnActive.get()) runCatching { activeConversation?.cancelProcess() }
            val conversationClosed = runCatching { activeConversation?.close() }.isSuccess
            if (!conversationClosed) activeEngineLease?.invalidate()
            runCatching { activeEngineLease?.close() }
        }
    }

    private fun createConversation(engine: Engine, request: GenerationRequest): Conversation =
        engine.createConversation(
            ConversationConfig(
                initialMessages = request.history.map(::toLiteRtMessage),
                samplerConfig = request.samplingOptions?.toLiteRtSamplerConfig(),
                automaticToolCalling = false,
                maxOutputToken = request.maximumOutputTokens,
                enableResponseFormat = request.responseJsonSchema != null,
            ),
        )

    private fun toLiteRtMessage(message: GenerationMessage): Message {
        val contents = Contents.of(message.textParts.map(Content::Text))
        return when (message.role) {
            GenerationRole.SYSTEM -> Message.system(contents)
            GenerationRole.USER -> Message.user(contents)
            GenerationRole.ASSISTANT -> Message.model(contents)
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun collectStatistics(
        conversation: Conversation,
        reportUsage: Boolean,
        generationStartedNanos: Long,
        tokenBaseline: Long,
    ): Result<GenerationStatistics?> {
        if (!reportUsage) return Result.success(null)
        return runCatching {
            val benchmark = conversation.getBenchmarkInfo()
            val totalTokens = conversation.getTokenCount().toLong()
            val outputTokens = benchmark.lastDecodeTokenCount.toLong()
            require(totalTokens >= tokenBaseline && totalTokens - tokenBaseline >= outputTokens) {
                "LiteRT-LM returned inconsistent token counters"
            }
            GenerationStatistics(
                // First-turn baseline zero includes its complete initial preface. Later baselines
                // exclude the retained KV cache, so usage accounts only for the new user message.
                inputTokens = totalTokens - tokenBaseline - outputTokens,
                outputTokens = outputTokens,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(
                    (System.nanoTime() - generationStartedNanos).coerceAtLeast(0L),
                ),
                contextTokensAfterTurn = totalTokens,
            )
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun collectTokenCount(conversation: Conversation): Result<Long> = runCatching {
        conversation.getTokenCount().toLong().also { require(it >= 0L) }
    }
}

internal fun GenerationSamplingOptions.toLiteRtSamplerConfig() = SamplerConfig(
    topK = topK,
    topP = topP,
    temperature = temperature,
)

internal fun GenerationRequest.toLiteRtResponseFormat(): ResponseFormat? =
    responseJsonSchema?.let(ResponseFormat::json)
