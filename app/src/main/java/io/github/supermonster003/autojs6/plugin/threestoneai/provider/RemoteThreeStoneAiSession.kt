package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiRetryDisposition
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.provider.api.AiCompletionResult
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiSessionStarted
import org.autojs.plugin.ai.provider.api.IAiCallback
import org.autojs.plugin.ai.provider.api.IAiSession
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderChunk
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderQuotaPolicy
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiProviderVersionPolicy
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * One remote provider session. A normal persistent turn retains the Binder, backend, and native
 * Conversation; all abnormal terminals close the complete session so a possibly inconsistent KV
 * cache is never reused.
 */
internal class RemoteThreeStoneAiSession(
    private val ownerUid: Int,
    requestMetadata: ByteArray,
    descriptors: OwnedParcelFileDescriptors,
    private val callback: IAiCallback,
    private val callerVerifier: HostCallerVerifier,
    private val aiBackend: AiBackend,
    private val worker: ExecutorService,
    private val timeoutScheduler: ScheduledExecutorService,
    private val callbackLane: SerialCallbackLane,
    private val onFinished: (RemoteThreeStoneAiSession) -> Unit,
) : IAiSession.Stub() {

    private val initialRequestEnvelope = requestMetadata.copyOf()
    private val initialDescriptors = descriptors
    private val initialRequestHint = runCatching {
        AiProviderCodec.decodeTextRequest(initialRequestEnvelope)
    }.getOrNull()
    private val persistent = initialRequestHint?.options?.persistentSession == true
    private val callbackBinder = callback.asBinder()
    private val callbackDeathRecipient = IBinder.DeathRecipient(::callbackDied)
    private val callbackDeathLinked = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val cleanupClaimed = AtomicBoolean(false)
    private val backendSession = AtomicReference<AiBackendSession?>()
    private val activeTurn = AtomicReference<Turn?>()
    private val fixedConfiguration = AtomicReference<FixedConfiguration?>()
    private val persistentSessionId = AtomicReference<String?>()

    override fun grantCredits(count: Int) {
        callerVerifier.enforceSessionOwner(ownerUid)
        activeTurn.get()?.grantCredits(count)
    }

    override fun submitToolResults(
        results: ByteArray?,
        resultDescriptors: Array<out ParcelFileDescriptor>?,
    ) {
        try {
            callerVerifier.enforceSessionOwner(ownerUid)
        } catch (error: Throwable) {
            OwnedParcelFileDescriptors.closeIncoming(resultDescriptors)
            throw error
        }
        OwnedParcelFileDescriptors.closeIncoming(resultDescriptors)
        activeTurn.get()?.fail(
            AiErrorCode.UNSUPPORTED_CAPABILITY,
            "Tool results are not supported",
        )
    }

    override fun generateNext(
        request: ByteArray?,
        descriptors: Array<out ParcelFileDescriptor>?,
    ) {
        val safeRequest: ByteArray
        val ownedDescriptors: OwnedParcelFileDescriptors
        try {
            callerVerifier.enforceSessionOwner(ownerUid)
            require(persistent) { "This local AI session is not persistent" }
            safeRequest = requireNotNull(request) { "local AI request metadata is missing" }
            val safeDescriptors = requireNotNull(descriptors) {
                "local AI request descriptors are missing"
            }
            BinderInputPolicy.requireEnvelopeSize(safeRequest.size)
            BinderInputPolicy.requireRequestDescriptorCount(
                safeDescriptors.size,
                minOf(
                    ThreeStoneAiPlugin.capabilities.maximumRequestDescriptors,
                    AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
                ),
            )
            ownedDescriptors = OwnedParcelFileDescriptors.duplicateBeforeAsync(safeDescriptors)
        } catch (error: Throwable) {
            OwnedParcelFileDescriptors.closeIncoming(descriptors)
            if (error is SecurityException) throw error
            failSession(AiErrorCode.INVALID_REQUEST, "The next local AI turn is invalid")
            return
        }

        if (closed.get()) {
            ownedDescriptors.close()
            publishClosedSessionFailure()
            return
        }
        val turn = Turn(safeRequest.copyOf(), ownedDescriptors, firstTurn = false)
        if (!activeTurn.compareAndSet(null, turn)) {
            turn.dispose(cancelWorkers = true)
            failSession(AiErrorCode.PROTOCOL_VIOLATION, "An local AI turn is already active")
            return
        }
        turn.start()
    }

    override fun cancel() {
        callerVerifier.enforceSessionOwner(ownerUid)
        activeTurn.get()?.cancelByUser() ?: terminateSilently(cancelBackend = true)
    }

    override fun close() {
        callerVerifier.enforceSessionOwner(ownerUid)
        terminateSilently(cancelBackend = true)
    }

    fun start() {
        val turn = Turn(initialRequestEnvelope, initialDescriptors, firstTurn = true)
        if (!activeTurn.compareAndSet(null, turn)) {
            turn.dispose(cancelWorkers = true)
            terminateSilently(cancelBackend = true)
            return
        }
        if (linkCallbackDeath()) turn.start()
    }

    fun rejectBusy() {
        initialDescriptors.close()
        if (!linkCallbackDeath()) return
        if (!closed.compareAndSet(false, true)) return
        dispatchCallback {
            callback.onFailed(
                AiCommonCodec.encodeError(
                    AiError(
                        code = AiErrorCode.PROVIDER_UNAVAILABLE,
                        message = "local AI provider already has an active session",
                        retryDisposition = AiRetryDisposition.EXPLICIT_NEW_REQUEST_ONLY,
                    ),
                ),
            )
        }
        cleanupSession(cancelBackend = false)
    }

    fun serviceDestroyed() = terminateSilently(cancelBackend = true, closeBackendDirectly = true)

    private fun runTurn(turn: Turn) {
        try {
            turn.ensureActive()
            val request = turn.decodedRequest.getOrThrow()
            requireProtocolAndSurface(request)
            AiProviderQuotaPolicy.validateRequest(
                request = request,
                provider = ThreeStoneAiPlugin.capabilities,
                descriptorCount = turn.descriptors.count,
            )
            val materialized = PayloadMaterializer.materializeRequest(
                request,
                turn.descriptors::readDeclaredBytes,
            )
            turn.descriptors.close()
            turn.ensureActive()
            requireTurnConfiguration(turn, request, materialized)
            val generationRequest = PromptPlanner.plan(request, materialized)
            val activeBackend = if (turn.firstTurn) {
                try {
                    aiBackend.createSession(
                        AiBackendSessionRequest(
                            targetId = AiTargetIds.local(request.modelId),
                            executionProfileId = request.options.backendProfile,
                        ),
                    )
                } catch (_: AiTargetUnavailableException) {
                    throw ModelUnavailable()
                }.also { created ->
                    if (closed.get() || !backendSession.compareAndSet(null, created)) {
                        runCatching(created::close)
                        throw SessionStopped()
                    }
                }
            } else {
                checkNotNull(backendSession.get()) { "Persistent generation backend is unavailable" }
            }
            turn.emitStarted(request)
            turn.ensureActive()
            val listener = object : GenerationListener {
                override fun onTextDelta(text: String) = turn.backendTextDelta(text)
                override fun onCompleted(statistics: GenerationStatistics?) =
                    turn.backendCompleted(statistics)

                override fun onFailed(error: Throwable, statistics: GenerationStatistics?) =
                    turn.backendFailed(statistics)
            }
            if (turn.firstTurn) {
                activeBackend.stream(generationRequest, listener)
            } else {
                activeBackend.streamNext(generationRequest, listener)
            }
        } catch (_: SessionStopped) {
            Unit
        } catch (_: UnsupportedProtocol) {
            turn.fail(AiErrorCode.UNSUPPORTED_PROTOCOL, "local AI protocol version is unsupported")
        } catch (_: UnsupportedSurface) {
            turn.fail(AiErrorCode.UNSUPPORTED_CAPABILITY, "local AI request capability is unsupported")
        } catch (_: ModelUnavailable) {
            turn.fail(AiErrorCode.MODEL_UNAVAILABLE, "The selected local AI model is unavailable")
        } catch (_: IllegalArgumentException) {
            turn.fail(AiErrorCode.INVALID_REQUEST, "local AI request is invalid")
        } catch (_: IllegalStateException) {
            if (turn.isActive) {
                turn.fail(AiErrorCode.PROTOCOL_VIOLATION, "local AI session state is invalid")
            }
        } catch (_: Throwable) {
            if (turn.isActive) turn.fail(AiErrorCode.PROVIDER_FAILED, "local AI provider failed")
        }
    }

    private fun requireProtocolAndSurface(request: AiProviderRequest) {
        try {
            require(request.protocolVersion in AiProviderProtocol.HOST_PROTOCOL_RANGE)
        } catch (_: IllegalArgumentException) {
            throw UnsupportedProtocol()
        }
        require(request.providerId == ThreeStoneAiPlugin.PROVIDER_ID) { "Provider ID does not match" }
        val options = request.options
        if (
            (options.backendProfile != AiProviderBackendProfile.CPU &&
                request.protocolVersion < AiProviderProtocol.PROTOCOL_V1_3) ||
            options.includeReasoning ||
            options.maximumToolRounds != 0 ||
            (!options.structuredJson && options.responseMimeType != AiProviderMimeType.PLAIN) ||
            request.tools.isNotEmpty() ||
            options.requiredCapabilityIds.any {
                it != AiProviderCapabilityId.STREAMING &&
                    it != AiProviderCapabilityId.USAGE &&
                    it != AiProviderCapabilityId.PERSISTENT_SESSION &&
                    it != AiProviderCapabilityId.STRUCTURED_JSON
            } ||
            request.messages.any { it.name != null }
        ) {
            throw UnsupportedSurface()
        }
    }

    private fun requireTurnConfiguration(
        turn: Turn,
        request: AiProviderRequest,
        materialized: MaterializedRequest,
    ) {
        require(request.options.persistentSession == persistent)
        if (persistent) {
            require(AiProviderCapabilityId.PERSISTENT_SESSION in request.options.requiredCapabilityIds)
        }
        val configuration = FixedConfiguration.from(request, materialized.responseSchemaJson)
        if (turn.firstTurn) {
            require(fixedConfiguration.compareAndSet(null, configuration))
        } else {
            require(request.messages.size == 1 && request.messages.single().role == AiMessageRole.USER)
            require(fixedConfiguration.get() == configuration) {
                "Persistent local AI session configuration changed between turns"
            }
        }
    }

    private fun completeTurn(turn: Turn, reusable: Boolean, cancelBackend: Boolean) {
        if (!activeTurn.compareAndSet(turn, null)) return
        turn.dispose(cancelWorkers = false)
        if (reusable && !closed.get()) return
        closed.set(true)
        cleanupSession(cancelBackend)
    }

    private fun failTurn(turn: Turn, error: AiError, cancelled: Boolean = false) {
        if (!activeTurn.compareAndSet(turn, null)) return
        turn.dispose(cancelWorkers = true)
        closed.set(true)
        if (cancelled) {
            dispatchCallback { callback.onCancelled() }
        } else {
            dispatchCallback { callback.onFailed(AiCommonCodec.encodeError(error)) }
        }
        cleanupSession(cancelBackend = true)
    }

    private fun failSession(code: Int, message: String) {
        val turn = activeTurn.get()
        if (turn != null) {
            turn.fail(code, message)
            return
        }
        if (!closed.compareAndSet(false, true)) {
            publishClosedSessionFailure()
            return
        }
        dispatchCallback {
            callback.onFailed(AiCommonCodec.encodeError(AiError(code = code, message = message)))
        }
        cleanupSession(cancelBackend = true)
    }

    private fun publishClosedSessionFailure() {
        dispatchCallback {
            callback.onFailed(
                AiCommonCodec.encodeError(
                    AiError(
                        code = AiErrorCode.PROVIDER_UNAVAILABLE,
                        message = "The persistent local AI session is closed",
                        retryDisposition = AiRetryDisposition.EXPLICIT_NEW_REQUEST_ONLY,
                    ),
                ),
            )
        }
    }

    private fun terminateSilently(
        cancelBackend: Boolean,
        closeBackendDirectly: Boolean = false,
    ) {
        if (!closed.compareAndSet(false, true) && cleanupClaimed.get()) return
        activeTurn.getAndSet(null)?.dispose(cancelWorkers = true)
        initialDescriptors.close()
        cleanupSession(cancelBackend, closeBackendDirectly)
    }

    private fun callbackDied() = terminateSilently(cancelBackend = true)

    private fun dispatchCallback(block: () -> Unit) {
        callbackLane.dispatch(block, onFailure = { callbackDied() })
    }

    private fun linkCallbackDeath(): Boolean {
        if (!callbackDeathLinked.compareAndSet(false, true)) return !closed.get()
        try {
            callbackBinder.linkToDeath(callbackDeathRecipient, 0)
        } catch (_: RemoteException) {
            callbackDeathLinked.set(false)
            callbackDied()
            return false
        }
        if (!callbackBinder.isBinderAlive) {
            callbackDied()
            return false
        }
        return true
    }

    private fun unlinkCallbackDeath() {
        if (!callbackDeathLinked.compareAndSet(true, false)) return
        runCatching { callbackBinder.unlinkToDeath(callbackDeathRecipient, 0) }
    }

    private fun submitWork(turn: Turn, block: () -> Unit): Boolean {
        if (!turn.isActive) return false
        val future = try {
            worker.submit(block)
        } catch (_: RejectedExecutionException) {
            turn.fail(AiErrorCode.PROVIDER_FAILED, "local AI worker is unavailable")
            return false
        }
        turn.futures += future
        if (!turn.isActive) future.cancel(true)
        return true
    }

    private fun requestBackendCancel() {
        val activeBackend = backendSession.get() ?: return
        try {
            worker.execute(activeBackend::cancel)
        } catch (_: RejectedExecutionException) {
            // Final cleanup performs the same cancellation before closing the backend.
        }
    }

    private fun cleanupSession(cancelBackend: Boolean, closeBackendDirectly: Boolean = false) {
        if (!cleanupClaimed.compareAndSet(false, true)) return
        unlinkCallbackDeath()
        val action = {
            val activeBackend = backendSession.getAndSet(null)
            if (cancelBackend) {
                runCatching { activeBackend?.cancelAndClose() }
            } else {
                runCatching { activeBackend?.close() }
            }
            onFinished(this)
        }
        if (closeBackendDirectly || backendSession.get() == null) {
            action()
            return
        }
        try {
            worker.execute(action)
        } catch (_: RejectedExecutionException) {
            Thread(action, "three-stone-ai-backend-close").apply { isDaemon = true }.start()
        }
    }

    private inner class Turn(
        requestEnvelope: ByteArray,
        val descriptors: OwnedParcelFileDescriptors,
        val firstTurn: Boolean,
    ) {
        val decodedRequest = runCatching { AiProviderCodec.decodeTextRequest(requestEnvelope) }
        private val requestHint = decodedRequest.getOrNull()
        private val output = StreamingOutputBuffer(
            streaming = requestHint?.options?.stream == true,
            maximumOutputBytes = requestHint?.options?.maximumOutputBytes
                ?.coerceIn(1L, ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES)
                ?.toInt()
                ?: 1,
        )
        private val terminal = AtomicBoolean(false)
        private val drainScheduled = AtomicBoolean(false)
        private val timeoutFuture = AtomicReference<Future<*>?>()
        private val generationStatistics = AtomicReference<GenerationStatistics?>()
        private val terminalCause = AtomicReference(TerminalCause.NONE)
        val futures = ConcurrentLinkedQueue<Future<*>>()

        val isActive: Boolean
            get() = !terminal.get() && !closed.get() && activeTurn.get() === this

        fun start() {
            scheduleTimeout()
            submitWork(this) { runTurn(this) }
        }

        fun grantCredits(count: Int) {
            if (!isActive) return
            try {
                output.grantCredits(count)
                scheduleDrain()
            } catch (_: Throwable) {
                fail(AiErrorCode.PROTOCOL_VIOLATION, "Invalid chunk-credit grant")
            }
        }

        fun cancelByUser() {
            if (!terminal.compareAndSet(false, true)) return
            terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.USER_CANCEL)
            failTurn(
                this,
                AiError(AiErrorCode.CANCELLED, "local AI generation was cancelled"),
                cancelled = true,
            )
        }

        fun emitStarted(request: AiProviderRequest) {
            ensureActive()
            val sessionId = persistentSessionId.get() ?: SessionIds.create(
                request.requestId,
                UUID.randomUUID().toString(),
            ).also { created -> persistentSessionId.compareAndSet(null, created) }
            val metadata = AiSessionStarted(
                requestId = request.requestId,
                sessionId = persistentSessionId.get() ?: sessionId,
                protocolVersion = request.protocolVersion,
                providerId = ThreeStoneAiPlugin.PROVIDER_ID,
                modelId = request.modelId,
                firstChunkSequence = 0L,
                effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
                effectiveMaximumToolRounds = 0,
            )
            dispatchCallback { callback.onStarted(AiProviderCodec.encodeSessionStarted(metadata)) }
        }

        fun backendTextDelta(text: String) {
            if (!isActive || terminalCause.get() != TerminalCause.NONE) return
            val hitLimit = try {
                output.append(text)
            } catch (_: Throwable) {
                fail(AiErrorCode.PROVIDER_FAILED, "local AI output is invalid")
                return
            }
            scheduleDrain()
            if (hitLimit && terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.OUTPUT_LIMIT)) {
                requestBackendCancel()
            }
        }

        fun backendCompleted(statistics: GenerationStatistics?) {
            if (!isActive) return
            when (terminalCause.get()) {
                TerminalCause.NONE,
                TerminalCause.OUTPUT_LIMIT,
                -> {
                    generationStatistics.set(statistics)
                    output.markBackendDone()
                    scheduleDrain()
                }
                else -> Unit
            }
        }

        fun backendFailed(statistics: GenerationStatistics?) {
            when (terminalCause.get()) {
                TerminalCause.OUTPUT_LIMIT -> {
                    generationStatistics.set(statistics)
                    output.markBackendDone()
                    scheduleDrain()
                }
                TerminalCause.NONE -> fail(
                    AiErrorCode.PROVIDER_FAILED,
                    "LiteRT-LM generation failed",
                )
                else -> Unit
            }
        }

        fun fail(
            code: Int,
            message: String,
            retryDisposition: Int = AiRetryDisposition.NEVER,
        ) {
            if (!terminal.compareAndSet(false, true)) return
            terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.FAILURE)
            failTurn(this, AiError(code, message, retryDisposition))
        }

        fun dispose(cancelWorkers: Boolean) {
            timeoutFuture.getAndSet(null)?.cancel(false)
            descriptors.close()
            if (cancelWorkers) futures.forEach { it.cancel(true) }
            futures.clear()
        }

        fun ensureActive() {
            if (!isActive || Thread.currentThread().isInterrupted) throw SessionStopped()
        }

        private fun scheduleDrain() {
            if (!isActive || !output.hasDrainWork()) return
            if (!drainScheduled.compareAndSet(false, true)) return
            val accepted = submitWork(this) {
                try {
                    drainOutput()
                } finally {
                    drainScheduled.set(false)
                    if (isActive && output.hasDrainWork()) scheduleDrain()
                }
            }
            if (!accepted) drainScheduled.set(false)
        }

        private fun drainOutput() {
            while (isActive) {
                val chunk = output.takeCreditedChunk() ?: break
                dispatchCallback {
                    callback.onChunk(
                        AiProviderCodec.encodeTextChunk(
                            AiProviderChunk(sequence = chunk.sequence, textDelta = chunk.text),
                        ),
                    )
                }
            }
            if (isActive && output.isReadyForCompletion()) finishCompleted()
        }

        private fun finishCompleted() {
            val request = requestHint
                ?: return fail(AiErrorCode.INVALID_REQUEST, "local AI request is invalid")
            val snapshot = runCatching { output.snapshot() }.getOrElse {
                return fail(AiErrorCode.PROVIDER_FAILED, "local AI output finalization failed")
            }
            val bytes = snapshot.text.toByteArray(Charsets.UTF_8)
            val usage = if (request.options.reportUsage) {
                generationStatistics.get()?.toAiUsage()
                    ?: return fail(AiErrorCode.PROVIDER_FAILED, "local AI usage is unavailable")
            } else {
                null
            }
            val result = AiCompletionResult(
                output = AiPayloadReference(
                    mimeType = request.options.responseMimeType,
                    declaredLengthBytes = bytes.size.toLong(),
                    inlineBytes = bytes,
                    charset = "utf-8",
                ),
                finishReason = snapshot.finishReason,
                usage = usage,
            )
            try {
                AiProviderQuotaPolicy.validateCompletionAggregate(
                    request = request,
                    result = result,
                    streamedChunkCount = snapshot.chunkCount,
                    streamedTextBytes = snapshot.utf8Bytes.takeIf { snapshot.chunkCount > 0L } ?: 0L,
                    streamedReasoningBytes = 0L,
                    streamedTextSha256 = snapshot.sha256.takeIf { snapshot.chunkCount > 0L },
                    descriptorCount = 0,
                    maximumDescriptors = ThreeStoneAiPlugin.MAXIMUM_SESSION_DESCRIPTORS,
                    effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
                )
            } catch (_: Throwable) {
                return fail(AiErrorCode.PROVIDER_FAILED, "local AI completion validation failed")
            }
            if (!terminal.compareAndSet(false, true)) return
            val encodedUsage = usage?.let(AiCommonCodec::encodeUsage)
            val encodedCompletion = AiProviderCodec.encodeCompletionResult(result)
            val reusable = persistent && terminalCause.get() == TerminalCause.NONE
            dispatchCallback {
                try {
                    encodedUsage?.let(callback::onUsage)
                    callback.onCompleted(encodedCompletion, emptyArray())
                } finally {
                    completeTurn(
                        turn = this,
                        reusable = reusable,
                        cancelBackend = persistent && !reusable,
                    )
                }
            }
        }

        private fun scheduleTimeout() {
            val timeoutMillis = requestHint?.options?.timeoutMillis ?: return
            val scheduled = try {
                timeoutScheduler.schedule(
                    {
                        val cause = terminalCause.get()
                        if (
                            (cause == TerminalCause.NONE || cause == TerminalCause.OUTPUT_LIMIT) &&
                            terminalCause.compareAndSet(cause, TerminalCause.TIMEOUT)
                        ) {
                            requestBackendCancel()
                            fail(AiErrorCode.TIMEOUT, "local AI request timed out")
                        }
                    },
                    timeoutMillis,
                    TimeUnit.MILLISECONDS,
                )
            } catch (_: RejectedExecutionException) {
                fail(AiErrorCode.PROVIDER_FAILED, "local AI timeout scheduler is unavailable")
                return
            }
            if (!timeoutFuture.compareAndSet(null, scheduled) || !isActive) scheduled.cancel(false)
        }

        private fun GenerationStatistics.toAiUsage() = AiUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            durationMillis = durationMillis,
        )
    }

    private data class FixedConfiguration(
        val protocolVersion: AiProtocolVersion,
        val providerId: String,
        val modelId: String,
        val maximumOutputBytes: Long,
        val maximumOutputTokens: Long?,
        val reportUsage: Boolean,
        val timeoutMillis: Long,
        val temperature: Double?,
        val topK: Int?,
        val topP: Double?,
        val structuredJson: Boolean,
        val responseMimeType: String,
        val responseSchemaJson: String?,
    ) {
        companion object {
            fun from(
                request: AiProviderRequest,
                responseSchemaJson: String?,
            ) = FixedConfiguration(
                protocolVersion = request.protocolVersion,
                providerId = request.providerId,
                modelId = request.modelId,
                maximumOutputBytes = request.options.maximumOutputBytes,
                maximumOutputTokens = request.options.maximumOutputTokens,
                reportUsage = request.options.reportUsage,
                timeoutMillis = request.options.timeoutMillis,
                temperature = request.options.temperature,
                topK = request.options.topK,
                topP = request.options.topP,
                structuredJson = request.options.structuredJson,
                responseMimeType = request.options.responseMimeType,
                responseSchemaJson = responseSchemaJson,
            )
        }
    }

    private enum class TerminalCause { NONE, OUTPUT_LIMIT, USER_CANCEL, TIMEOUT, FAILURE }
    private class SessionStopped : RuntimeException()
    private class UnsupportedProtocol : RuntimeException()
    private class UnsupportedSurface : RuntimeException()
    private class ModelUnavailable : RuntimeException()
}
