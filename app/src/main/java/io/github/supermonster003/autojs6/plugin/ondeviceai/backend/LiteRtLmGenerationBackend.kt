package io.github.supermonster003.autojs6.plugin.ondeviceai.backend

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal class LiteRtLmGenerationBackend(
    private val modelPath: String,
    private val cacheDirectory: File,
) : GenerationBackend {
    private val lifecycleLock = Any()
    private val nativeLifecycleLock = Any()
    private val callbackGate = CallbackQuiescenceGate()
    private val started = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var conversation: Conversation? = null

    override fun start(request: GenerationRequest, listener: GenerationListener) {
        check(started.compareAndSet(false, true)) { "LiteRT-LM backend was already started" }
        check(request.prompt.role == GenerationRole.USER) { "The final LiteRT-LM prompt must be a user message" }
        try {
            synchronized(nativeLifecycleLock) {
                if (closed.get() || cancelled.get()) return
                val localEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(threadCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)),
                        cacheDir = cacheDirectory.absolutePath,
                    ),
                )
                synchronized(lifecycleLock) { engine = localEngine }
                if (closed.get() || cancelled.get()) return
                localEngine.initialize()
                if (closed.get() || cancelled.get()) return

                val localConversation = localEngine.createConversation(
                    ConversationConfig(
                        initialMessages = request.history.map(::toLiteRtMessage),
                        automaticToolCalling = false,
                        maxOutputToken = request.maximumOutputTokens,
                    ),
                )
                synchronized(lifecycleLock) { conversation = localConversation }
                if (closed.get() || cancelled.get()) return
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
                            callbackGate.runCallback(listener::onCompleted)
                        }

                        override fun onError(throwable: Throwable) {
                            callbackGate.runCallback { listener.onFailed(throwable) }
                        }
                    },
                    maxOutputToken = request.maximumOutputTokens,
                )
            }
        } catch (error: Throwable) {
            callbackGate.runCallback {
                if (!closed.get() && !cancelled.get()) listener.onFailed(error)
            }
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
        cancel()
        callbackGate.awaitQuiescenceAndSeal()
        synchronized(nativeLifecycleLock) {
            val activeConversation = synchronized(lifecycleLock) {
                conversation.also { conversation = null }
            }
            val activeEngine = synchronized(lifecycleLock) {
                engine.also { engine = null }
            }
            runCatching { activeConversation?.close() }
            runCatching { activeEngine?.close() }
        }
    }

    private fun toLiteRtMessage(message: GenerationMessage): Message {
        val contents = Contents.of(message.textParts.map(Content::Text))
        return when (message.role) {
            GenerationRole.SYSTEM -> Message.system(contents)
            GenerationRole.USER -> Message.user(contents)
            GenerationRole.ASSISTANT -> Message.model(contents)
        }
    }
}
