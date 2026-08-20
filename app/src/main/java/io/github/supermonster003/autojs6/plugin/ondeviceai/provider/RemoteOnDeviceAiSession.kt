package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import io.github.supermonster003.autojs6.plugin.ondeviceai.OnDeviceAiPlugin
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationBackend
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationBackendFactory
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelRepository
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiRetryDisposition
import org.autojs.plugin.ondeviceai.api.AiCompletionResult
import org.autojs.plugin.ondeviceai.api.AiSessionStarted
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.OnDeviceAiChunk
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
import org.autojs.plugin.ondeviceai.api.OnDeviceAiQuotaPolicy
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import org.autojs.plugin.ondeviceai.api.OnDeviceAiVersionPolicy
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiCallback
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiSession
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

internal class RemoteOnDeviceAiSession(
    private val ownerUid: Int,
    requestMetadata: ByteArray,
    private val descriptors: OwnedParcelFileDescriptors,
    private val callback: IOnDeviceAiCallback,
    private val callerVerifier: HostCallerVerifier,
    private val repository: ModelRepository,
    private val backendFactory: GenerationBackendFactory,
    private val worker: ExecutorService,
    private val timeoutScheduler: ScheduledExecutorService,
    private val callbackLane: SerialCallbackLane,
    private val onFinished: (RemoteOnDeviceAiSession) -> Unit,
) : IOnDeviceAiSession.Stub() {
    private val decodedRequest = runCatching { OnDeviceAiCodec.decodeTextRequest(requestMetadata) }
    private val requestHint = decodedRequest.getOrNull()
    private val state = OnDeviceAiSessionState()
    private val output = StreamingOutputBuffer(
        streaming = requestHint?.options?.stream == true,
        maximumOutputBytes = requestHint?.options?.maximumOutputBytes
            ?.coerceIn(1L, OnDeviceAiPlugin.MAXIMUM_OUTPUT_BYTES)
            ?.toInt()
            ?: 1,
    )
    private val callbackBinder = callback.asBinder()
    private val callbackDeathRecipient = IBinder.DeathRecipient { callbackDied() }
    private val callbackDeathLinked = AtomicBoolean(false)
    private val cleaned = AtomicBoolean(false)
    private val drainScheduled = AtomicBoolean(false)
    private val timeoutFuture = AtomicReference<Future<*>?>()
    private val backend = AtomicReference<GenerationBackend?>()
    private val terminalCause = AtomicReference(TerminalCause.NONE)
    private val futures = ConcurrentLinkedQueue<Future<*>>()

    override fun grantCredits(count: Int) {
        callerVerifier.enforceSessionOwner(ownerUid)
        if (!state.isActive) return
        try {
            output.grantCredits(count)
            scheduleDrain()
        } catch (_: Throwable) {
            finishFailed(AiErrorCode.PROTOCOL_VIOLATION, "Invalid chunk-credit grant")
        }
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
        if (state.isActive) {
            finishFailed(AiErrorCode.UNSUPPORTED_CAPABILITY, "Tool results are not supported")
        }
    }

    override fun cancel() {
        callerVerifier.enforceSessionOwner(ownerUid)
        if (!state.isActive) return
        if (state.cancel()) {
            terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.USER_CANCEL)
            dispatchCallback { callback.onCancelled() }
            cleanup(cancelWorkers = true)
        }
    }

    override fun close() {
        callerVerifier.enforceSessionOwner(ownerUid)
        terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.CLOSE)
        state.close()
        cleanup(cancelWorkers = true)
    }

    fun start() {
        if (!linkCallbackDeath()) return
        scheduleTimeout()
        submitWork(::runSession)
    }

    fun rejectBusy() {
        if (!linkCallbackDeath()) return
        finishFailed(
            code = AiErrorCode.PROVIDER_UNAVAILABLE,
            message = "on-device AI provider already has an active session",
            retryDisposition = AiRetryDisposition.EXPLICIT_NEW_REQUEST_ONLY,
        )
    }

    fun serviceDestroyed() {
        terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.CLOSE)
        state.close()
        cleanup(cancelWorkers = true, closeBackendDirectly = true)
    }

    private fun runSession() {
        try {
            val request = decodedRequest.getOrThrow()
            requireProtocolAndSurface(request)
            OnDeviceAiQuotaPolicy.validateRequest(
                request = request,
                provider = OnDeviceAiPlugin.capabilities,
                descriptorCount = descriptors.count,
            )
            val model = repository.findByModelId(request.modelId) ?: throw ModelUnavailable()
            val materialized = PayloadMaterializer.materializeRequest(request, descriptors::readDeclaredBytes)
            descriptors.close()
            ensureActive()
            val generationRequest = PromptPlanner.plan(request, materialized)
            emitStarted(request)
            ensureActive()
            val activeBackend = backendFactory.create(model.file.absolutePath)
            if (cleaned.get() || !state.isActive) {
                runCatching { activeBackend.close() }
                throw SessionStopped()
            }
            if (!backend.compareAndSet(null, activeBackend)) {
                activeBackend.close()
                error("on-device AI backend was already installed")
            }
            if (cleaned.get() || !state.isActive) {
                if (backend.compareAndSet(activeBackend, null)) runCatching { activeBackend.close() }
                throw SessionStopped()
            }
            ensureActive()
            activeBackend.start(
                generationRequest,
                object : GenerationListener {
                    override fun onTextDelta(text: String) = backendTextDelta(text)
                    override fun onCompleted() = backendCompleted()
                    override fun onFailed(error: Throwable) = backendFailed()
                },
            )
        } catch (_: SessionStopped) {
            Unit
        } catch (_: UnsupportedProtocol) {
            finishFailed(AiErrorCode.UNSUPPORTED_PROTOCOL, "on-device AI protocol version is unsupported")
        } catch (_: UnsupportedSurface) {
            finishFailed(AiErrorCode.UNSUPPORTED_CAPABILITY, "on-device AI request capability is unsupported")
        } catch (_: ModelUnavailable) {
            finishFailed(AiErrorCode.MODEL_UNAVAILABLE, "The selected on-device AI model is unavailable")
        } catch (_: IllegalArgumentException) {
            finishFailed(AiErrorCode.INVALID_REQUEST, "on-device AI request is invalid")
        } catch (_: IllegalStateException) {
            if (state.isActive) finishFailed(AiErrorCode.PROTOCOL_VIOLATION, "on-device AI session state is invalid")
        } catch (_: Throwable) {
            if (state.isActive) finishFailed(AiErrorCode.PROVIDER_FAILED, "on-device AI provider failed")
        }
    }

    private fun requireProtocolAndSurface(request: OnDeviceAiRequest) {
        try {
            OnDeviceAiVersionPolicy.requireSelected(request.protocolVersion, OnDeviceAiProtocol.HOST_PROTOCOL_RANGE.maximum)
        } catch (_: IllegalArgumentException) {
            throw UnsupportedProtocol()
        }
        require(request.providerId == OnDeviceAiPlugin.PROVIDER_ID) { "Provider ID does not match" }
        val options = request.options
        if (
            options.includeReasoning || options.structuredJson || options.reportUsage ||
            options.maximumOutputTokens != null || options.maximumToolRounds != 0 ||
            options.responseMimeType != OnDeviceAiMimeType.PLAIN || options.responseSchema != null ||
            request.tools.isNotEmpty() ||
            options.requiredCapabilityIds.any { it != OnDeviceAiCapabilityId.STREAMING } ||
            request.messages.any { it.name != null }
        ) {
            throw UnsupportedSurface()
        }
    }

    private fun emitStarted(request: OnDeviceAiRequest) {
        state.start()
        val metadata = AiSessionStarted(
            requestId = request.requestId,
            sessionId = SessionIds.create(request.requestId, UUID.randomUUID().toString()),
            protocolVersion = request.protocolVersion,
            providerId = OnDeviceAiPlugin.PROVIDER_ID,
            modelId = request.modelId,
            firstChunkSequence = 0L,
            effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
            effectiveMaximumToolRounds = 0,
        )
        dispatchCallback { callback.onStarted(OnDeviceAiCodec.encodeSessionStarted(metadata)) }
    }

    private fun backendTextDelta(text: String) {
        if (!state.isActive || terminalCause.get() != TerminalCause.NONE) return
        val hitLimit = try {
            output.append(text)
        } catch (_: Throwable) {
            finishFailed(AiErrorCode.PROVIDER_FAILED, "on-device AI output is invalid")
            return
        }
        scheduleDrain()
        if (hitLimit && terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.OUTPUT_LIMIT)) {
            output.markBackendDone()
            requestBackendCancel()
            scheduleDrain()
        }
    }

    private fun backendCompleted() {
        if (!state.isActive || terminalCause.get() != TerminalCause.NONE) return
        output.markBackendDone()
        scheduleDrain()
    }

    private fun backendFailed() {
        when (terminalCause.get()) {
            TerminalCause.OUTPUT_LIMIT -> {
                output.markBackendDone()
                scheduleDrain()
            }
            TerminalCause.USER_CANCEL,
            TerminalCause.TIMEOUT,
            TerminalCause.CLOSE,
            TerminalCause.FAILURE,
            -> Unit
            TerminalCause.NONE -> finishFailed(AiErrorCode.PROVIDER_FAILED, "LiteRT-LM generation failed")
        }
    }

    private fun scheduleDrain() {
        if (!state.isActive || !output.hasDrainWork()) return
        if (!drainScheduled.compareAndSet(false, true)) return
        val accepted = submitWork {
            try {
                drainOutput()
            } finally {
                drainScheduled.set(false)
                if (state.isActive && output.hasDrainWork()) scheduleDrain()
            }
        }
        if (!accepted) drainScheduled.set(false)
    }

    private fun drainOutput() {
        while (state.isActive) {
            val chunk = output.takeCreditedChunk() ?: break
            dispatchCallback {
                callback.onChunk(
                    OnDeviceAiCodec.encodeTextChunk(
                        OnDeviceAiChunk(sequence = chunk.sequence, textDelta = chunk.text),
                    ),
                )
            }
        }
        if (state.isActive && output.isReadyForCompletion()) finishCompleted()
    }

    private fun finishCompleted() {
        val request = requestHint ?: return finishFailed(AiErrorCode.INVALID_REQUEST, "on-device AI request is invalid")
        val snapshot = runCatching { output.snapshot() }.getOrElse {
            return finishFailed(AiErrorCode.PROVIDER_FAILED, "on-device AI output finalization failed")
        }
        val bytes = snapshot.text.toByteArray(Charsets.UTF_8)
        val result = AiCompletionResult(
            output = AiPayloadReference(
                mimeType = OnDeviceAiMimeType.PLAIN,
                declaredLengthBytes = bytes.size.toLong(),
                inlineBytes = bytes,
                charset = "utf-8",
            ),
            finishReason = snapshot.finishReason,
        )
        try {
            OnDeviceAiQuotaPolicy.validateCompletionAggregate(
                request = request,
                result = result,
                streamedChunkCount = snapshot.chunkCount,
                streamedTextBytes = snapshot.utf8Bytes.takeIf { snapshot.chunkCount > 0L } ?: 0L,
                streamedReasoningBytes = 0L,
                streamedTextSha256 = snapshot.sha256.takeIf { snapshot.chunkCount > 0L },
                descriptorCount = 0,
                maximumDescriptors = OnDeviceAiPlugin.MAXIMUM_SESSION_DESCRIPTORS,
                effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
            )
        } catch (_: Throwable) {
            return finishFailed(AiErrorCode.PROVIDER_FAILED, "on-device AI completion validation failed")
        }
        if (!runCatching { state.complete() }.getOrDefault(false)) return
        dispatchCallback { callback.onCompleted(OnDeviceAiCodec.encodeCompletionResult(result), emptyArray()) }
        cleanup(cancelWorkers = false)
    }

    private fun finishFailed(
        code: Int,
        message: String,
        retryDisposition: Int = AiRetryDisposition.NEVER,
    ) {
        if (!state.isActive) return
        if (!runCatching { state.fail() }.getOrDefault(false)) return
        terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.FAILURE)
        val error = AiError(code = code, message = message, retryDisposition = retryDisposition)
        dispatchCallback { callback.onFailed(AiCommonCodec.encodeError(error)) }
        cleanup(cancelWorkers = true)
    }

    private fun dispatchCallback(block: () -> Unit) {
        callbackLane.dispatch(block, onFailure = { callbackFailed() })
    }

    private fun callbackFailed() {
        terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.FAILURE)
        state.close()
        cleanup(cancelWorkers = true)
    }

    private fun callbackDied() {
        terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.CLOSE)
        state.close()
        cleanup(cancelWorkers = true)
    }

    private fun linkCallbackDeath(): Boolean {
        if (!callbackDeathLinked.compareAndSet(false, true)) return state.isActive
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

    private fun submitWork(block: () -> Unit): Boolean {
        if (!state.isActive) return false
        val future = try {
            worker.submit(block)
        } catch (_: RejectedExecutionException) {
            finishFailed(AiErrorCode.PROVIDER_FAILED, "on-device AI worker is unavailable")
            return false
        }
        futures += future
        if (!state.isActive) future.cancel(true)
        return true
    }

    private fun requestBackendCancel() {
        val activeBackend = backend.get() ?: return
        try {
            worker.execute(activeBackend::cancel)
        } catch (_: RejectedExecutionException) {
            // Cleanup always closes the backend and performs a final cancellation attempt.
        }
    }

    private fun cleanup(cancelWorkers: Boolean, closeBackendDirectly: Boolean = false) {
        if (!cleaned.compareAndSet(false, true)) return
        timeoutFuture.getAndSet(null)?.cancel(false)
        descriptors.close()
        unlinkCallbackDeath()
        val activeBackend = backend.getAndSet(null)
        if (cancelWorkers) futures.forEach { it.cancel(true) }
        val closeAndFinish = {
            runCatching { activeBackend?.close() }
            onFinished(this)
        }
        if (activeBackend == null || closeBackendDirectly) {
            closeAndFinish()
        } else {
            try {
                worker.execute(closeAndFinish)
            } catch (_: RejectedExecutionException) {
                Thread(closeAndFinish, "on-device-ai-backend-close").apply { isDaemon = true }.start()
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
                        finishFailed(AiErrorCode.TIMEOUT, "on-device AI request timed out")
                    }
                },
                timeoutMillis,
                TimeUnit.MILLISECONDS,
            )
        } catch (_: RejectedExecutionException) {
            finishFailed(AiErrorCode.PROVIDER_FAILED, "on-device AI timeout scheduler is unavailable")
            return
        }
        if (!timeoutFuture.compareAndSet(null, scheduled) || cleaned.get()) scheduled.cancel(false)
    }

    private fun ensureActive() {
        if (!state.isActive || Thread.currentThread().isInterrupted) throw SessionStopped()
    }

    private enum class TerminalCause { NONE, OUTPUT_LIMIT, USER_CANCEL, TIMEOUT, CLOSE, FAILURE }
    private class SessionStopped : RuntimeException()
    private class UnsupportedProtocol : RuntimeException()
    private class UnsupportedSurface : RuntimeException()
    private class ModelUnavailable : RuntimeException()
}
