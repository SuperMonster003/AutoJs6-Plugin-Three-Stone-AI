package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class SummaryCheckpointRequest(
    val targetId: String,
    val executionProfileId: String?,
    val structuredJson: Boolean,
    val plan: SummaryCheckpointPlan,
    val state: ConversationContextState,
    val transcript: List<ChatMessage>,
    val estimator: ContextTokenEstimator,
    val maximumInputTokens: Long,
) {
    init {
        require(targetId.isNotBlank())
        require(executionProfileId == null || executionProfileId.isNotBlank())
        require(maximumInputTokens > 0L)
    }
}

internal sealed interface SummaryCheckpointOutcome {
    data class Success(
        val draft: SummaryCheckpointDraft,
        val validatedState: ConversationContextState,
        val statistics: GenerationStatistics?,
        val attempts: Int,
    ) : SummaryCheckpointOutcome

    data class Failure(
        val error: Throwable,
        val attempts: Int,
    ) : SummaryCheckpointOutcome
}

internal fun interface SummaryCheckpointTask {
    fun cancel()
}

internal class SummaryCheckpointInputLimitException(
    estimated: Long,
    maximum: Long,
) : IllegalArgumentException("Summary input estimate $estimated exceeds limit $maximum")

internal class SummaryCheckpointResponseLimitException(maximumBytes: Int) :
    IllegalArgumentException("Summary response exceeds $maximumBytes UTF-8 bytes")

/**
 * Runs a one-shot backend session independently from the chat session.
 *
 * Parsing and local provenance validation are part of an attempt, so malformed model output gets
 * the same single retry as a network/backend failure. Cancellation never reports a terminal result.
 */
internal class SummaryCheckpointer(
    private val backend: AiBackend,
    private val executor: Executor,
    private val maximumAttempts: Int = 2,
    private val maximumResponseBytes: Int = 64 * 1_024,
    private val cancellationExecutor: Executor = DAEMON_CANCELLATION_EXECUTOR,
) {
    init {
        require(maximumAttempts > 0)
        require(maximumResponseBytes > 0)
    }

    fun generate(
        request: SummaryCheckpointRequest,
        callback: (SummaryCheckpointOutcome) -> Unit,
    ): SummaryCheckpointTask {
        val generationRequest = SummaryPromptProtocol.generationRequest(
            plan = request.plan,
            state = request.state,
            structuredJson = request.structuredJson,
        )
        val inputEstimate = request.estimator.estimateMessages(
            generationRequest.history + generationRequest.prompt,
        ).estimatedTokens
        if (inputEstimate > request.maximumInputTokens) {
            val failure = SummaryCheckpointOutcome.Failure(
                SummaryCheckpointInputLimitException(inputEstimate, request.maximumInputTokens),
                attempts = 0,
            )
            dispatch { callback(failure) }
            return SummaryCheckpointTask {}
        }
        return Task(request, generationRequest, callback).also(Task::start)
    }

    private inner class Task(
        private val request: SummaryCheckpointRequest,
        private val generationRequest: io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest,
        private val callback: (SummaryCheckpointOutcome) -> Unit,
    ) : SummaryCheckpointTask {
        private val cancelled = AtomicBoolean(false)
        private val completed = AtomicBoolean(false)
        private val activeSession = AtomicReference<AiBackendSession?>()
        private var attempts = 0

        fun start() = dispatch(::runAttempt)

        override fun cancel() {
            if (!cancelled.compareAndSet(false, true)) return
            val session = activeSession.getAndSet(null) ?: return
            requestSessionCancellation(session)
        }

        private fun runAttempt() {
            if (cancelled.get() || completed.get()) return
            attempts++
            val session = try {
                backend.createSession(
                    AiBackendSessionRequest(
                        targetId = request.targetId,
                        executionProfileId = request.executionProfileId,
                    ),
                )
            } catch (error: Throwable) {
                finishAttempt(error = error)
                return
            }
            if (cancelled.get() || !activeSession.compareAndSet(null, session)) {
                runCatching(session::cancelAndClose)
                return
            }
            val attemptFinished = AtomicBoolean(false)
            val output = StringBuilder()
            var outputBytes = 0
            val listener = object : GenerationListener {
                override fun onTextDelta(text: String) {
                    if (text.isEmpty() || cancelled.get() || attemptFinished.get()) return
                    val overflow = synchronized(output) {
                        outputBytes = Math.addExact(
                            outputBytes,
                            text.toByteArray(Charsets.UTF_8).size,
                        )
                        if (outputBytes > maximumResponseBytes) {
                            true
                        } else {
                            output.append(text)
                            false
                        }
                    }
                    if (overflow && attemptFinished.compareAndSet(false, true)) {
                        activeSession.compareAndSet(session, null)
                        requestSessionCancellation(session)
                        finishAttempt(
                            error = SummaryCheckpointResponseLimitException(maximumResponseBytes),
                        )
                    }
                }

                override fun onCompleted(statistics: GenerationStatistics?) {
                    if (!attemptFinished.compareAndSet(false, true)) return
                    val response = synchronized(output) { output.toString() }
                    finishAttempt(session = session, response = response, statistics = statistics)
                }

                override fun onFailed(error: Throwable, statistics: GenerationStatistics?) {
                    if (!attemptFinished.compareAndSet(false, true)) return
                    finishAttempt(session = session, error = error)
                }
            }
            try {
                session.stream(generationRequest, listener)
            } catch (error: Throwable) {
                if (attemptFinished.compareAndSet(false, true)) {
                    finishAttempt(session = session, error = error)
                }
            }
        }

        private fun finishAttempt(
            session: AiBackendSession? = null,
            response: String? = null,
            statistics: GenerationStatistics? = null,
            error: Throwable? = null,
        ) {
            dispatch {
                session?.let { value ->
                    activeSession.compareAndSet(value, null)
                    runCatching(value::close)
                }
                if (cancelled.get() || completed.get()) return@dispatch
                val result = if (error != null) {
                    Result.failure(error)
                } else {
                    runCatching {
                        val draft = SummaryCheckpointJsonCodec.decode(checkNotNull(response))
                        val state = SummaryCheckpointValidator.apply(
                            state = request.state,
                            plan = request.plan,
                            draft = draft,
                            transcript = request.transcript,
                        )
                        SummaryCheckpointOutcome.Success(
                            draft = draft,
                            validatedState = state,
                            statistics = statistics,
                            attempts = attempts,
                        )
                    }
                }
                result.fold(
                    onSuccess = { outcome -> complete(outcome) },
                    onFailure = { failure ->
                        if (attempts < maximumAttempts) runAttempt()
                        else complete(SummaryCheckpointOutcome.Failure(failure, attempts))
                    },
                )
            }
        }

        private fun complete(outcome: SummaryCheckpointOutcome) {
            if (cancelled.get() || !completed.compareAndSet(false, true)) return
            callback(outcome)
        }
    }

    private fun dispatch(action: () -> Unit) {
        try {
            executor.execute(action)
        } catch (error: RejectedExecutionException) {
            action()
        }
    }

    /**
     * A backend's stream call may occupy [executor] until the HTTP/native turn exits. Cancellation
     * therefore needs an independent thread or it could sit behind the work it is meant to stop.
     */
    private fun requestSessionCancellation(session: AiBackendSession) {
        try {
            cancellationExecutor.execute { runCatching(session::cancelAndClose) }
        } catch (_: RejectedExecutionException) {
            runCatching(session::cancel)
        }
    }

    private companion object {
        val DAEMON_CANCELLATION_EXECUTOR = Executor { action ->
            Thread(action, "three-stone-ai-summary-cancel").apply {
                isDaemon = true
                start()
            }
        }
    }
}
