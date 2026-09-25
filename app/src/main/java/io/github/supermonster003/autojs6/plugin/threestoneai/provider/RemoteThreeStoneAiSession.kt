package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationToolCall
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationToolResult
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
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderChunk
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderQuotaPolicy
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiProviderPayloadPolicy
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiToolCallBatch
import org.autojs.plugin.ai.provider.api.AiToolTurnPolicy
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
    private val callerVerifier: SessionOwnerVerifier,
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
    private val persistentContext = PersistentSessionContext()

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
        val turn = activeTurn.get()
        if (turn == null || !turn.isActive) {
            OwnedParcelFileDescriptors.closeIncoming(resultDescriptors)
            return
        }
        turn.submitToolResults(results, resultDescriptors)
    }

    override fun generateNext(
        request: ByteArray?,
        descriptors: Array<out ParcelFileDescriptor>?,
    ) {
        val safeRequest: ByteArray
        val ownedDescriptors: OwnedParcelFileDescriptors
        try {
            callerVerifier.enforceSessionOwner(ownerUid)
            require(persistent) { "This AI session is not persistent" }
            safeRequest = requireNotNull(request) { "AI request metadata is missing" }
            val safeDescriptors = requireNotNull(descriptors) {
                "AI request descriptors are missing"
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
            failSession(AiErrorCode.INVALID_REQUEST, "The next AI turn is invalid")
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
            failSession(AiErrorCode.PROTOCOL_VIOLATION, "An AI turn is already active")
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
                        message = "AI provider already has an active session",
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
            val quota = AiProviderQuotaPolicy.validateRequest(
                request = request,
                provider = ThreeStoneAiPlugin.capabilities,
                descriptorCount = turn.descriptors.count,
            )
            val target = try {
                aiBackend.catalog().requireTarget(request.targetId)
            } catch (_: AiTargetUnavailableException) {
                throw TargetUnavailable()
            }
            try {
                TargetRequestPolicy.requireSupported(
                    target = target,
                    request = request,
                    quota = quota,
                )
            } catch (_: AiTargetUnavailableException) {
                throw TargetUnavailable()
            }
            val materialized = PayloadMaterializer.materializeRequest(
                request,
                turn.descriptors::readDeclaredBytes,
            )
            turn.descriptors.close()
            turn.ensureActive()
            requireTurnConfiguration(turn, request, materialized)
            val generationRequest = PromptPlanner.plan(request, materialized)
            val contextPlan = if (persistent) {
                val prepared = if (turn.firstTurn) {
                    persistentContext.prepareFirst(generationRequest, target)
                } else {
                    persistentContext.prepareNext(generationRequest, target)
                }
                turn.installContextPlan(prepared)
                prepared
            } else {
                null
            }
            val rebuildBackend = contextPlan?.mode == PersistentBackendTurnMode.REBUILD
            val retainedBackend = backendSession.get()
            val rebuildInPlace = rebuildBackend &&
                retainedBackend?.supportsInPlacePersistentRebuild == true
            val activeBackend = when {
                turn.firstTurn -> createAndInstallBackend(request)
                rebuildBackend && !rebuildInPlace -> {
                    runCatching { backendSession.getAndSet(null)?.close() }
                    turn.ensureActive()
                    createAndInstallBackend(request)
                }
                else -> checkNotNull(backendSession.get()) {
                    "Persistent generation backend is unavailable"
                }
            }
            val effectiveRequest = contextPlan?.generationRequest ?: generationRequest
            contextPlan?.let { plan -> logContextStart(request.targetId, plan) }
            turn.emitStarted(request)
            turn.ensureActive()
            val listener = object : GenerationListener {
                override fun onTextDelta(text: String) = turn.backendTextDelta(text)
                override fun onToolCalls(calls: List<GenerationToolCall>, statistics: GenerationStatistics?) =
                    turn.backendToolCalls(calls, statistics)
                override fun onCompleted(statistics: GenerationStatistics?) =
                    turn.backendCompleted(statistics)

                override fun onFailed(error: Throwable, statistics: GenerationStatistics?) =
                    turn.backendFailed(statistics)
            }
            if (contextPlan != null) {
                activeBackend.streamPreparedPersistentTurn(contextPlan, listener)
            } else {
                activeBackend.stream(effectiveRequest, listener)
            }
        } catch (_: SessionStopped) {
            Unit
        } catch (_: UnsupportedProtocol) {
            turn.fail(AiErrorCode.UNSUPPORTED_PROTOCOL, "AI protocol version is unsupported")
        } catch (_: UnsupportedSurface) {
            turn.fail(AiErrorCode.UNSUPPORTED_CAPABILITY, "AI request capability is unsupported")
        } catch (_: TargetUnavailable) {
            turn.fail(AiErrorCode.TARGET_UNAVAILABLE, "The selected AI target is unavailable")
        } catch (_: PersistentSessionContextExhaustedException) {
            turn.fail(
                PersistentSessionFailurePolicy.contextExhaustedErrorCode,
                PersistentSessionFailurePolicy.CONTEXT_EXHAUSTED_MESSAGE,
            )
        } catch (_: IllegalArgumentException) {
            turn.fail(AiErrorCode.INVALID_REQUEST, "AI request is invalid")
        } catch (_: IllegalStateException) {
            if (turn.isActive) {
                turn.fail(AiErrorCode.PROTOCOL_VIOLATION, "AI session state is invalid")
            }
        } catch (_: Throwable) {
            if (turn.isActive) turn.fail(AiErrorCode.PROVIDER_FAILED, "AI provider failed")
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
            options.includeReasoning ||
            (options.persistentSession && (request.tools.isNotEmpty() ||
                AiProviderCapabilityId.VISION in options.requiredCapabilityIds || request.messages.any { message -> message.parts.any { it.isImage } })) ||
            (!options.structuredJson && options.responseMimeType != AiProviderMimeType.PLAIN) ||
            options.requiredCapabilityIds.any {
                it != AiProviderCapabilityId.STREAMING &&
                    it != AiProviderCapabilityId.USAGE &&
                    it != AiProviderCapabilityId.PERSISTENT_SESSION &&
                    it != AiProviderCapabilityId.TOOLS &&
                    it != AiProviderCapabilityId.STRUCTURED_JSON &&
                    it != AiProviderCapabilityId.VISION
            } ||
            request.messages.any { it.name != null }
        ) {
            throw UnsupportedSurface()
        }
        require(if (request.tools.isEmpty()) options.maximumToolRounds == 0 else options.maximumToolRounds > 0)
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
                "Persistent AI session configuration changed between turns"
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
                        message = "The persistent AI session is closed",
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
            turn.fail(AiErrorCode.PROVIDER_FAILED, "AI worker is unavailable")
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

    private fun createAndInstallBackend(request: AiProviderRequest): AiBackendSession {
        val created = try {
            aiBackend.createSession(
                AiBackendSessionRequest(
                    targetId = request.targetId,
                    executionProfileId = request.options.backendProfile,
                ),
            )
        } catch (_: AiTargetUnavailableException) {
            throw TargetUnavailable()
        }
        if (closed.get() || !backendSession.compareAndSet(null, created)) {
            runCatching(created::close)
            throw SessionStopped()
        }
        return created
    }

    private fun logContextStart(targetId: String, plan: PreparedPersistentTurn) {
        Log.d(
            TAG,
            "Persistent context start target=$targetId mode=${plan.mode} " +
                "estimatedInputTokens=${plan.estimatedInputTokens} " +
                "hardWatermarkTokens=${plan.budget.hardWatermarkTokens} " +
                "absoluteProtectionTokens=${plan.budget.absoluteProtectionTokens} " +
                "retainedBytes=${plan.retainedTranscriptBytes} " +
                "retainedTurns=${plan.retainedTurnCount} " +
                "evictedTurns=${plan.evictedTurnCount} backendEpoch=${plan.backendEpoch}",
        )
    }

    private fun logContextComplete(
        plan: PreparedPersistentTurn,
        commit: PersistentContextCommit,
        statistics: GenerationStatistics?,
    ) {
        val diagnostics = commit.diagnostics
        Log.d(
            TAG,
            "Persistent context complete mode=${plan.mode} " +
                "actualInputTokens=${statistics?.inputTokens} " +
                "actualOutputTokens=${statistics?.outputTokens} " +
                "accountingTokens=${diagnostics.accounting.tokens} " +
                "accountingSource=${diagnostics.accounting.source} " +
                "accountingHighWaterTokens=${diagnostics.accountingHighWater.tokens} " +
                "transcriptBytes=${diagnostics.transcriptBytes} " +
                "retainedTurns=${diagnostics.completedTurnCount} " +
                "guardEvictedTurns=${commit.evictedByMemoryGuard} " +
                "rebuildRequired=${diagnostics.rebuildRequired} " +
                "backendEpoch=${diagnostics.backendEpoch}",
        )
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
        private val contextPlan = AtomicReference<PreparedPersistentTurn?>()
        private val initialImages = requestHint?.messages.orEmpty().flatMap { it.parts }.filter { it.isImage }
        private val toolPolicy = AiToolTurnPolicy(requestHint?.options?.maximumToolRounds ?: 0,
            initialImageCount = initialImages.size, initialImageBytes = initialImages.sumOf { it.payload.declaredLengthBytes })
        private val pendingToolCallback = AtomicReference<ByteArray?>()
        private val acceptingToolResults = AtomicBoolean(false)
        private val toolResultDescriptors = AtomicReference<OwnedParcelFileDescriptors?>()
        private val usageAccumulator = ToolGenerationUsage()
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
                AiError(AiErrorCode.CANCELLED, "AI generation was cancelled"),
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
                targetId = request.targetId,
                firstChunkSequence = 0L,
                effectiveMaximumOutputBytes = request.options.maximumOutputBytes,
                effectiveMaximumToolRounds = request.options.maximumToolRounds,
            )
            dispatchCallback { callback.onStarted(AiProviderCodec.encodeSessionStarted(metadata)) }
        }

        fun installContextPlan(plan: PreparedPersistentTurn) {
            check(contextPlan.compareAndSet(null, plan)) { "Persistent context plan is already set" }
        }

        fun backendTextDelta(text: String) {
            if (!isActive || terminalCause.get() != TerminalCause.NONE) return
            if (toolPolicy.hasOutstandingTurn) {
                fail(AiErrorCode.PROTOCOL_VIOLATION, "AI output arrived while waiting for tool results")
                return
            }
            val hitLimit = try {
                output.append(text)
            } catch (_: Throwable) {
                fail(AiErrorCode.PROVIDER_FAILED, "AI output is invalid")
                return
            }
            scheduleDrain()
            if (hitLimit && terminalCause.compareAndSet(TerminalCause.NONE, TerminalCause.OUTPUT_LIMIT)) {
                requestBackendCancel()
            }
        }

        fun backendCompleted(statistics: GenerationStatistics?) {
            if (!isActive) return
            if (toolPolicy.hasOutstandingTurn) {
                fail(AiErrorCode.PROTOCOL_VIOLATION, "AI completion arrived while waiting for tool results")
                return
            }
            when (terminalCause.get()) {
                TerminalCause.NONE,
                TerminalCause.OUTPUT_LIMIT,
                -> {
                    if (!recordStatistics(statistics)) return
                    output.markBackendDone()
                    scheduleDrain()
                }
                else -> Unit
            }
        }

        fun backendFailed(statistics: GenerationStatistics?) {
            when (terminalCause.get()) {
                TerminalCause.OUTPUT_LIMIT -> {
                    if (!recordStatistics(statistics)) return
                    output.markBackendDone()
                    scheduleDrain()
                }
                TerminalCause.NONE -> fail(
                    AiErrorCode.PROVIDER_FAILED,
                    "AI generation failed",
                )
                else -> Unit
            }
        }

        private fun recordStatistics(statistics: GenerationStatistics?): Boolean = try {
            val accumulated = usageAccumulator.add(statistics)
            if (requestHint?.options?.reportUsage == true) requireNotNull(accumulated)
            generationStatistics.set(accumulated)
            true
        } catch (_: Throwable) {
            fail(AiErrorCode.PROVIDER_FAILED, "AI usage is unavailable or invalid")
            false
        }

        fun backendToolCalls(calls: List<GenerationToolCall>, statistics: GenerationStatistics?) {
            if (!isActive || terminalCause.get() != TerminalCause.NONE) return
            try {
                val batch = AiToolCallBatch(calls.map(GenerationToolCall::toProviderCall))
                AiProviderQuotaPolicy.validateToolCalls(requireNotNull(requestHint), batch, 0,
                    ThreeStoneAiPlugin.MAXIMUM_SESSION_DESCRIPTORS)
                val encoded = AiProviderCodec.encodeToolCallBatch(batch)
                BinderInputPolicy.requireEnvelopeSize(encoded.size)
                toolPolicy.open(batch)
                if (!recordStatistics(statistics)) return
                check(pendingToolCallback.compareAndSet(null, encoded))
                scheduleDrain()
            } catch (_: Throwable) {
                fail(AiErrorCode.PROTOCOL_VIOLATION, "AI tool calls are invalid")
            }
        }

        fun submitToolResults(metadata: ByteArray?, incoming: Array<out ParcelFileDescriptor>?) {
            val owned: OwnedParcelFileDescriptors
            val safeMetadata: ByteArray
            try {
                check(isActive && acceptingToolResults.compareAndSet(true, false))
                safeMetadata = requireNotNull(metadata).also { BinderInputPolicy.requireEnvelopeSize(it.size) }.copyOf()
                val safeIncoming = requireNotNull(incoming)
                BinderInputPolicy.requireSessionDescriptorCount(safeIncoming.size, ThreeStoneAiPlugin.MAXIMUM_SESSION_DESCRIPTORS)
                owned = OwnedParcelFileDescriptors.duplicateBeforeAsync(safeIncoming)
            } catch (_: Throwable) {
                OwnedParcelFileDescriptors.closeIncoming(incoming)
                fail(AiErrorCode.PROTOCOL_VIOLATION, "AI tool results are invalid or unexpected")
                return
            }
            check(toolResultDescriptors.compareAndSet(null, owned))
            if (!isActive) {
                toolResultDescriptors.compareAndSet(owned, null)
                owned.close()
                return
            }
            val accepted = submitWork(this) {
                try {
                    ensureActive()
                    val batch = AiProviderCodec.decodeToolResultBatch(safeMetadata)
                    AiProviderQuotaPolicy.validateToolResults(batch, owned.count, ThreeStoneAiPlugin.MAXIMUM_SESSION_DESCRIPTORS,
                        decodedRequest.getOrThrow(), ThreeStoneAiPlugin.capabilities)
                    toolPolicy.submit(batch) // Reject IDs and declared quotas before a descriptor can block.
                    val results = batch.results.map { result ->
                        GenerationToolResult(result.callId, AiProviderPayloadPolicy.materializeAndValidateBounded(
                            result.output, AiProviderLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES, owned::readDeclaredBytes,
                        ), result.isError, result.imageParts.map { ProviderImageDecoder.materialize(it, owned::readDeclaredBytes) })
                    }
                    owned.close()
                    toolResultDescriptors.compareAndSet(owned, null)
                    ensureActive()
                    requireNotNull(backendSession.get()).submitToolResults(results)
                } catch (_: SessionStopped) {
                    Unit
                } catch (_: Throwable) {
                    fail(AiErrorCode.PROTOCOL_VIOLATION, "AI tool continuation failed")
                } finally {
                    toolResultDescriptors.compareAndSet(owned, null)
                    owned.close()
                }
            }
            if (!accepted) {
                toolResultDescriptors.compareAndSet(owned, null)
                owned.close()
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
            acceptingToolResults.set(false)
            pendingToolCallback.set(null)
            toolResultDescriptors.getAndSet(null)?.close()
            contextPlan.getAndSet(null)?.let(persistentContext::abandon)
            if (cancelWorkers) futures.forEach { it.cancel(true) }
            futures.clear()
        }

        fun ensureActive() {
            if (!isActive || Thread.currentThread().isInterrupted) throw SessionStopped()
        }

        private fun scheduleDrain() {
            if (!isActive || !hasDrainWork()) return
            if (!drainScheduled.compareAndSet(false, true)) return
            val accepted = submitWork(this) {
                try {
                    drainOutput()
                } finally {
                    drainScheduled.set(false)
                    if (isActive && hasDrainWork()) scheduleDrain()
                }
            }
            if (!accepted) drainScheduled.set(false)
        }

        private fun hasDrainWork(): Boolean = output.hasDrainWork() ||
            (pendingToolCallback.get() != null && !output.hasPendingChunks())

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
            if (isActive && !output.hasPendingChunks()) {
                pendingToolCallback.getAndSet(null)?.let { encoded ->
                    val usage = generationStatistics.get()?.takeIf { requestHint?.options?.reportUsage == true }
                        ?.toAiUsage()?.let(AiCommonCodec::encodeUsage)
                    dispatchCallback {
                        if (isActive) {
                            usage?.let(callback::onUsage)
                            acceptingToolResults.set(true)
                            callback.onToolCalls(encoded, emptyArray())
                        }
                    }
                }
            }
            if (isActive && output.isReadyForCompletion()) finishCompleted()
        }

        private fun finishCompleted() {
            val request = requestHint
                ?: return fail(AiErrorCode.INVALID_REQUEST, "AI request is invalid")
            val snapshot = runCatching { output.snapshot() }.getOrElse {
                return fail(AiErrorCode.PROVIDER_FAILED, "AI output finalization failed")
            }
            val bytes = snapshot.text.toByteArray(Charsets.UTF_8)
            val usage = if (request.options.reportUsage) {
                generationStatistics.get()?.toAiUsage()
                    ?: return fail(AiErrorCode.PROVIDER_FAILED, "AI usage is unavailable")
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
                return fail(AiErrorCode.PROVIDER_FAILED, "AI completion validation failed")
            }
            if (!terminal.compareAndSet(false, true)) return
            if (persistent && terminalCause.get() == TerminalCause.NONE) {
                val plan = contextPlan.get()
                    ?: return failClaimedTurn("Persistent context plan is unavailable")
                val commit = runCatching {
                    persistentContext.complete(plan, snapshot.text, generationStatistics.get())
                }.getOrElse {
                    return failClaimedTurn("Persistent context commit failed")
                }
                contextPlan.compareAndSet(plan, null)
                logContextComplete(plan, commit, generationStatistics.get())
            }
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

        private fun failClaimedTurn(message: String) {
            terminalCause.set(TerminalCause.FAILURE)
            contextPlan.getAndSet(null)?.let(persistentContext::abandon)
            failTurn(this, AiError(AiErrorCode.PROVIDER_FAILED, message))
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
                            fail(AiErrorCode.TIMEOUT, "AI request timed out")
                        }
                    },
                    timeoutMillis,
                    TimeUnit.MILLISECONDS,
                )
            } catch (_: RejectedExecutionException) {
                fail(AiErrorCode.PROVIDER_FAILED, "AI timeout scheduler is unavailable")
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
        val targetId: String,
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
                targetId = request.targetId,
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
    private class TargetUnavailable : RuntimeException()

    private companion object {
        const val TAG = "ThreeStoneAiBinder"
    }
}
