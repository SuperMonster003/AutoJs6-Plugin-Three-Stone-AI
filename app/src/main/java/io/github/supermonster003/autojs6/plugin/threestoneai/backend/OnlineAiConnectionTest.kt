package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One explicit, user-triggered generation used to verify a configured online profile. */
internal class OnlineAiConnectionTest(
    private val backend: AiBackend,
    profileId: String,
) : Closeable {
    private val targetId = AiTargetIds.profile(profileId)
    private val cancelled = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val activeSession = AtomicReference<AiBackendSession?>()

    fun execute() {
        if (cancelled.get()) throw CancellationException()
        check(started.compareAndSet(false, true)) { "Connection test was already started" }
        val session = backend.createSession(AiBackendSessionRequest(targetId))
        check(activeSession.compareAndSet(null, session))
        try {
            if (cancelled.get()) {
                session.cancel()
                throw CancellationException()
            }
            val terminal = AtomicReference<Terminal?>()
            session.stream(
                GenerationRequest(
                    history = emptyList(),
                    prompt = GenerationMessage(
                        role = GenerationRole.USER,
                        textParts = listOf(TEST_PROMPT),
                    ),
                    maximumOutputTokens = MAXIMUM_OUTPUT_TOKENS,
                    samplingOptions = null,
                    reportUsage = false,
                ),
                object : GenerationListener {
                    override fun onTextDelta(text: String) = Unit

                    override fun onCompleted(statistics: GenerationStatistics?) {
                        terminal.compareAndSet(null, Terminal.Completed)
                    }

                    override fun onFailed(error: Throwable, statistics: GenerationStatistics?) {
                        terminal.compareAndSet(null, Terminal.Failed(error))
                    }
                },
            )
            if (cancelled.get()) throw CancellationException()
            when (val result = terminal.get()) {
                Terminal.Completed -> Unit
                is Terminal.Failed -> throw result.error
                null -> throw OnlineAiFailureException(OnlineAiFailureReason.INVALID_RESPONSE)
            }
        } finally {
            activeSession.compareAndSet(session, null)
            session.close()
        }
    }

    fun cancel() {
        cancelled.set(true)
        activeSession.get()?.cancel()
    }

    override fun close() = cancel()

    private sealed interface Terminal {
        data object Completed : Terminal
        data class Failed(val error: Throwable) : Terminal
    }

    private companion object {
        const val TEST_PROMPT = "Reply with OK."
        const val MAXIMUM_OUTPUT_TOKENS = 8
    }
}
