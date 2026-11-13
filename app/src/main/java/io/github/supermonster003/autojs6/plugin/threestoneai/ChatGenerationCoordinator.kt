package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachment
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiEffortSupport
import io.github.supermonster003.autojs6.plugin.threestoneai.download.AvailableLiteRtModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProviderCatalog
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/** The chat's view of the reply being generated, as handed to an attaching screen. */
internal data class ChatGenerationSnapshot(
    val generationId: Long,
    val conversationId: String,
    val assistantMessageId: Long,
    /** Every character generated so far, including text not yet delivered through onDelta. */
    val text: String,
    val target: ConversationTargetSnapshot,
    /** Thinking text so far (P3); its duration is null while the model is still thinking. */
    val reasoning: ChatMessageReasoning? = null,
)

internal data class ChatGenerationCompletion(
    val text: String,
    val target: ConversationTargetSnapshot,
    val usage: ChatMessageUsage?,
    val truncated: Boolean,
    val reasoning: ChatMessageReasoning? = null,
)

internal data class ChatGenerationAttachment(
    val active: ChatGenerationSnapshot?,
    /** Conversations the coordinator wrote to history while no screen was attached. */
    val changedConversationIds: Set<String>,
)

internal data class ChatConversationSnapshot(
    val messages: List<ChatMessage>,
    val contextState: ConversationContextState,
)

internal data class ChatTurnRequest(
    val conversationId: String,
    val assistantMessageId: Long,
    val target: AiTarget,
    val executionProfileId: String?,
    val prompt: String,
    /** The transcript including the new user message and the generating reply. */
    val transcript: List<ChatMessage>,
    val contextState: ConversationContextState,
    val settings: ChatUiSettings,
    /** Conversation override or the target default; adapters clamp it per model (roadmap D72 / D73). */
    val effort: GenerationEffort? = null,
    /** Conversation override or the target default; only Anthropic targets send it (roadmap D74). */
    val fast: Boolean? = null,
    /** Attachments of the new user message, already moved to the conversation directory (P4). */
    val promptAttachments: List<ChatAttachment> = emptyList(),
)

/** History access the coordinator needs when no screen holds the conversation (roadmap D77). */
internal interface ChatGenerationStore {
    fun find(conversationId: String): StoredConversation?

    /** Writes [conversation] without reviving one the user deleted meanwhile. */
    fun update(conversation: StoredConversation): Boolean

    fun recall(
        conversationId: String,
        query: String,
        excludedMessageIds: Set<Long>,
    ): List<ConversationRecallChunk>

    /** The stored file of [attachment] as request content; null when it is gone (P4, D134). */
    fun attachment(conversationId: String, attachment: ChatAttachment): GenerationAttachment? = null
}

internal interface ChatGenerationScheduler {
    fun post(delayMillis: Long, action: () -> Unit)
}

internal interface ChatGenerationLogger {
    fun debug(message: String)
    fun warn(message: String, error: Throwable? = null)
    fun error(message: String, error: Throwable? = null)
}

/** Localized target and failure texts shared by the chat screen and the detached coordinator. */
internal class ChatGenerationText(private val context: Context) {
    fun failure(target: ConversationTargetSnapshot, error: Throwable): String {
        val failure = ChatGenerationFailurePolicy.classify(target, error)
        val template = when (failure.locality) {
            AiTargetLocality.LOCAL -> R.string.chat_generation_failure_local
            AiTargetLocality.REMOTE -> R.string.chat_generation_failure_cloud
        }
        val summary = context.getString(
            template,
            targetSummary(target),
            context.getString(failure.kind.detailResource()),
        )
        val detail = failure.detail ?: return summary
        return summary + "\n\n" + context.getString(R.string.chat_generation_failure_detail, detail)
    }

    fun targetSummary(snapshot: ConversationTargetSnapshot): String = context.getString(
        R.string.chat_target_status_format,
        localityLabel(snapshot.locality),
        providerLabel(snapshot.providerId, snapshot.locality),
        displayName(snapshot),
        snapshot.modelId,
    )

    fun displayName(snapshot: ConversationTargetSnapshot): String =
        if (snapshot.locality == AiTargetLocality.LOCAL) {
            AvailableLiteRtModelCatalog.displayNameForImportedModel(
                snapshot.displayName,
                snapshot.modelId,
            )
        } else {
            snapshot.displayName
        }

    fun localityLabel(locality: AiTargetLocality): String = context.getString(
        when (locality) {
            AiTargetLocality.LOCAL -> R.string.chat_target_locality_local
            AiTargetLocality.REMOTE -> R.string.chat_target_locality_cloud
        },
    )

    fun providerLabel(providerId: String, locality: AiTargetLocality): String {
        if (locality == AiTargetLocality.LOCAL) return context.getString(R.string.app_name)
        val provider = runCatching { OnlineAiProvider.fromProviderId(providerId) }.getOrNull()
            ?: return providerId
        return if (provider == OnlineAiProvider.OPENAI_COMPATIBLE) {
            context.getString(R.string.online_ai_provider_custom)
        } else {
            OnlineAiProviderCatalog.templateFor(provider).displayName
        }
    }

    private fun ChatGenerationFailureKind.detailResource(): Int = when (this) {
        ChatGenerationFailureKind.TARGET_UNAVAILABLE ->
            R.string.chat_generation_failure_target_unavailable
        ChatGenerationFailureKind.LOCAL_EXECUTION ->
            R.string.chat_generation_failure_local_execution
        ChatGenerationFailureKind.CREDENTIAL -> R.string.online_ai_test_failure_credential
        ChatGenerationFailureKind.PROFILE_CHANGED ->
            R.string.online_ai_test_failure_profile_changed
        ChatGenerationFailureKind.AUTHENTICATION ->
            R.string.online_ai_test_failure_authentication
        ChatGenerationFailureKind.RATE_LIMIT -> R.string.online_ai_test_failure_rate_limit
        ChatGenerationFailureKind.PROVIDER -> R.string.online_ai_test_failure_provider
        ChatGenerationFailureKind.NETWORK -> R.string.online_ai_test_failure_network
        ChatGenerationFailureKind.METERED_NETWORK -> R.string.online_ai_test_failure_metered
        ChatGenerationFailureKind.TIMEOUT -> R.string.online_ai_test_failure_timeout
        ChatGenerationFailureKind.TLS -> R.string.online_ai_test_failure_tls
        ChatGenerationFailureKind.RESPONSE -> R.string.online_ai_test_failure_response
        ChatGenerationFailureKind.UNKNOWN -> R.string.chat_generation_failure_unknown
    }

    companion object {
        const val FAILURE_SEPARATOR = "\n\n---\n\n"

        fun withFailure(text: String, failureText: String): String =
            if (text.isBlank()) failureText else text.trimEnd() + FAILURE_SEPARATOR + failureText
    }
}

/**
 * Owns the chat reply being generated, independent of any ChatActivity instance (roadmap D77).
 *
 * The generation thread, the generation epoch, the backend session and its target, the streaming
 * buffer with its 32 ms delivery, context telemetry, summary checkpoints and the persistence of a
 * reply that finishes while no screen is attached all live here. A screen attaches in onStart,
 * receives a snapshot of the reply in progress and then [Observer] callbacks, and detaches in
 * onStop; rotation, night mode, language and font size changes therefore never interrupt a reply.
 * Stopping, a new conversation, switching conversations or targets, deleting messages and a
 * failed turn cancel the backend on its own thread, so an online HTTP call or a local inference is
 * actually cancelled instead of only being ignored by an epoch check.
 *
 * Every public method runs on the main thread; backend callbacks are posted to it.
 */
internal class ChatGenerationCoordinator internal constructor(
    private val backend: () -> AiBackend,
    private val store: ChatGenerationStore,
    private val calibrationStore: ContextTokenCalibrationStore,
    private val scheduler: ChatGenerationScheduler,
    private val failureText: (ConversationTargetSnapshot, Throwable) -> String,
    private val logger: ChatGenerationLogger,
    private val backendExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "three-stone-ai-chat").apply { isDaemon = true }
    },
    private val cancelExecutor: Executor = Executor { runnable ->
        Thread(runnable, "three-stone-ai-chat-cancel").apply { isDaemon = true }.start()
    },
    private val summaryExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "three-stone-ai-summary").apply { isDaemon = true }
    },
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    interface Observer {
        /** Newly generated text of [generation], delivered at most every 32 ms. */
        fun onDelta(generation: ChatGenerationSnapshot, delta: String)

        /**
         * The thinking text of [generation] grew or its thinking time became known; delivered with
         * the same 32 ms pacing as [onDelta] and before it.
         */
        fun onReasoning(generation: ChatGenerationSnapshot) {}

        fun onCompleted(generation: ChatGenerationSnapshot, completion: ChatGenerationCompletion)

        fun onFailed(generation: ChatGenerationSnapshot, failureText: String)

        /** The active generation, its target or the backend's context telemetry changed. */
        fun onStateChanged(active: ChatGenerationSnapshot?)

        /** The conversation the screen shows, when it is [conversationId]; null otherwise. */
        fun conversationSnapshot(conversationId: String): ChatConversationSnapshot?

        /** A summary checkpoint produced [state] for the conversation the screen shows. */
        fun onContextStateChanged(conversationId: String, state: ConversationContextState)
    }

    private data class ActiveGeneration(
        val generationId: Long,
        val conversationId: String,
        val assistantMessageId: Long,
        val requestedTarget: ConversationTargetSnapshot,
        val settings: ChatUiSettings,
        var actualTarget: ConversationTargetSnapshot? = null,
    ) {
        fun snapshot(output: GeneratedOutput) = ChatGenerationSnapshot(
            generationId = generationId,
            conversationId = conversationId,
            assistantMessageId = assistantMessageId,
            text = output.text,
            target = actualTarget ?: requestedTarget,
            reasoning = output.reasoning,
        )
    }

    /** Reply and thinking text of one generation at one moment. */
    private data class GeneratedOutput(
        val text: String,
        val reasoning: ChatMessageReasoning?,
    )

    private data class BackendContextTelemetry(
        val epoch: Long,
        val committedMessages: List<GenerationMessage>,
        val accounting: ContextAccounting,
        val contextFingerprint: String,
        val contextStateFingerprint: String,
        val recalledHistory: List<ConversationRecallChunk>,
        val layerTokens: ContextLayerTokenUsage,
    )

    private data class ContextTurnObservation(
        val generationId: Long,
        val backendEpoch: Long,
        val prompt: GenerationMessage,
        val rebuilt: Boolean,
        val inputEstimate: ContextTokenEstimate,
        val calibrationInput: ContextTokenEstimate,
        val coefficientBefore: Double,
        val hardWatermarkTokens: Long,
    )

    private val generationEpoch = AtomicLong(0L)
    private val backendLock = Any()
    private val deltaLock = Any()

    private var observer: Observer? = null
    private var active: ActiveGeneration? = null
    private val changedWhileDetached = LinkedHashSet<String>()

    // Guarded by backendLock.
    private var activeBackend: AiBackendSession? = null
    private var activeBackendTarget: ConversationTargetSnapshot? = null
    private var activeBackendAiTarget: AiTarget? = null
    private var activeBackendConversationId: String? = null
    private var activeBackendContextTelemetry: BackendContextTelemetry? = null
    private var activeContextTurnObservation: ContextTurnObservation? = null
    private var backendContextEpoch = 0L
    private var completedTurnsOnBackend = 0

    // Guarded by deltaLock.
    private var deltaGenerationId = 0L
    private val generatedText = StringBuilder()
    private var deliveredLength = 0
    private var deltaFlushScheduled = false
    // Thinking text (P3.1, D94): kept like the reply text so a re-attached screen gets all of it.
    private val generatedReasoning = StringBuilder()
    private var reasoningCut = false
    private var reasoningChanged = false
    /** Clock time the current thinking stretch began; null when the model is not thinking. */
    private var reasoningOpenSince: Long? = null
    private var reasoningMillis = 0L

    private var activeSummaryTask: SummaryCheckpointTask? = null
    private var activeSummaryEpoch = 0L
    private var activeSummaryConversationId: String? = null
    private var activeSummarySourceHash: String? = null
    private var failedSummaryPlanKey: String? = null

    val isGenerating: Boolean
        get() = active != null

    /** Attaches [value] as the only observer and returns the reply in progress. */
    fun attach(value: Observer): ChatGenerationAttachment {
        observer = value
        val changed = changedWhileDetached.toSet()
        changedWhileDetached.clear()
        val current = active ?: return ChatGenerationAttachment(null, changed)
        val output = synchronized(deltaLock) {
            // The snapshot carries all text so far; later deltas continue after it.
            deliveredLength = generatedText.length
            reasoningChanged = false
            outputLocked()
        }
        return ChatGenerationAttachment(current.snapshot(output), changed)
    }

    fun detach(value: Observer) {
        if (observer === value) observer = null
    }

    /** The reply being generated for [conversationId], whether or not a screen is attached. */
    fun activeFor(conversationId: String): ChatGenerationSnapshot? {
        val current = active?.takeIf { generation -> generation.conversationId == conversationId }
            ?: return null
        return current.snapshot(currentOutput())
    }

    /** Starts a turn; any other generation is cancelled first. Returns the generation ID. */
    fun start(request: ChatTurnRequest): Long {
        stop(reason = "superseded")
        val backendConversation = synchronized(backendLock) { activeBackendConversationId }
        if (backendConversation != null && backendConversation != request.conversationId) {
            closeBackend(reason = "conversation-changed")
        }
        val generationId = generationEpoch.incrementAndGet()
        val generation = ActiveGeneration(
            generationId = generationId,
            conversationId = request.conversationId,
            assistantMessageId = request.assistantMessageId,
            requestedTarget = ConversationTargetSnapshot.from(request.target),
            settings = request.settings,
        )
        active = generation
        synchronized(deltaLock) {
            deltaGenerationId = generationId
            resetOutputLocked()
        }
        notifyState()
        // The level the adapters send after clamping (D73) and whether fast applies (D74); device evidence for P2.6.
        logger.debug(
            "Generation start target=${request.target.targetId} " +
                "effort=${OnlineAiEffortSupport.effective(request.effort, request.target.capabilities.effortLevels).persistentId} " +
                "fast=${request.fast == true && request.target.capabilities.fastMode}",
        )
        startBackendTurn(generation, request)
        return generationId
    }

    /**
     * Stops the active generation with a hard cancel and returns its final snapshot (all text
     * generated so far), or null when nothing was being generated. A screen showing the
     * conversation stores the stopped reply itself; otherwise the coordinator writes it to history.
     */
    fun stop(reason: String = "stopped"): ChatGenerationSnapshot? {
        val current = active ?: return null
        val output = finishOutput(current.generationId)
        val backendTarget = synchronized(backendLock) { activeBackendTarget }
        generationEpoch.incrementAndGet()
        active = null
        closeBackend(reason)
        val stopped = current.snapshot(output)
            .copy(target = backendTarget ?: current.actualTarget ?: current.requestedTarget)
        if (observer?.conversationSnapshot(current.conversationId) == null) {
            persistOutside(stopped.conversationId, stopped.assistantMessageId) { message ->
                message.copy(
                    text = stopped.text,
                    status = ChatMessageStatus.STOPPED,
                    usage = null,
                    target = stopped.target,
                    reasoning = stopped.reasoning,
                )
            }
        }
        notifyState()
        return stopped
    }

    /** Closes the backend session with a hard cancel; returns whether one was open. */
    fun closeBackend(reason: String = "closed"): Boolean {
        val detached = detachBackend() ?: return false
        val requestedAt = clock()
        logger.debug("Generation cancel requested reason=$reason target=${detached.target.targetId}")
        val cancel = Runnable {
            val result = runCatching(detached::cancelAndClose)
            val diagnostics = runCatching(detached::cancellationDiagnostics).getOrNull()
            logger.debug(
                "Generation cancel complete reason=$reason elapsedMillis=${clock() - requestedAt} " +
                    "success=${result.isSuccess}" + (diagnostics?.let { value -> " $value" } ?: ""),
            )
        }
        try {
            cancelExecutor.execute(cancel)
        } catch (_: RejectedExecutionException) {
            Thread(cancel, "three-stone-ai-chat-close").apply { isDaemon = true }.start()
        }
        notifyState()
        return true
    }

    /** Accounting and layer tokens of the open backend when it serves [conversationId] on [target]. */
    fun contextUsage(conversationId: String, target: AiTarget): Pair<ContextAccounting, ContextLayerTokenUsage>? =
        synchronized(backendLock) {
            activeBackendContextTelemetry
                ?.takeIf {
                    activeBackendConversationId == conversationId &&
                        activeBackendTarget?.matchesExecutionIdentity(target) == true
                }
                ?.let { telemetry -> telemetry.accounting to telemetry.layerTokens }
        }

    fun cancelSummaryCheckpoint() {
        nextSummaryEpoch()
        activeSummaryTask?.cancel()
        activeSummaryTask = null
        activeSummaryConversationId = null
        activeSummarySourceHash = null
    }

    fun resetSummaryFailure() {
        failedSummaryPlanKey = null
    }

    private fun startBackendTurn(generation: ActiveGeneration, request: ChatTurnRequest) {
        val generationId = generation.generationId
        val target = request.target
        val settings = request.settings
        val prompt = promptMessage(request)
        val attachmentReplay = ChatAttachmentReplay(target.capabilities.attachments) { attachment ->
            store.attachment(request.conversationId, attachment)
        }
        val requestedTarget = generation.requestedTarget
        val budget = ContextBudgetCalculator.calculate(
            targetLimits = target.limits,
            applicationInputTokenBudget = settings.contextTokenBudget,
            maximumOutputTokens = settings.maximumOutputTokens,
        )
        val calibration = calibrationStore.current(requestedTarget.targetId)
        val estimator = calibration.estimator()
        val contextStateSnapshot = request.contextState
        val baseCompiled = ChatConversationPolicy.compileContext(
            transcript = request.transcript,
            prompt = prompt,
            policy = ContextCompilationPolicy(
                estimator = estimator,
                maximumInputTokens = budget.compactionTargetTokens,
                contextState = contextStateSnapshot,
            ),
        )
        val freshRecall = store.recall(
            conversationId = request.conversationId,
            query = request.prompt,
            excludedMessageIds = baseCompiled.coveredMessageIds.toSet(),
        )
        var reusableBackend: AiBackendSession? = null
        var reusableTelemetry: BackendContextTelemetry? = null
        var backendToReplace: AiBackendSession? = null
        var emergencyDerivedContextDrop = false
        var recallForRebuild = freshRecall
        synchronized(backendLock) {
            val telemetry = activeBackendContextTelemetry
            val stateFingerprint = ConversationContextPolicy.fingerprint(contextStateSnapshot)
            val targetMatches = activeBackendConversationId == request.conversationId &&
                activeBackendTarget?.matchesExecutionIdentity(target) == true
            val stateMatches = telemetry?.contextStateFingerprint == stateFingerprint && targetMatches
            val stickyRecall = telemetry?.recalledHistory
                ?.takeIf { stateMatches }
                .orEmpty()
            val mergedRecall = ConversationRecallPolicy.mergeSticky(
                sticky = stickyRecall,
                fresh = freshRecall,
            )
            val expectedFingerprint = ConversationContextPolicy.compilationFingerprint(
                state = contextStateSnapshot,
                includeDerivedContext = true,
                recalledHistoryFingerprint = stickyRecall.takeIf(List<*>::isNotEmpty)
                    ?.let(ConversationRecallPolicy::fingerprint),
            )
            val installedContextMatches = stateMatches &&
                telemetry?.contextFingerprint == expectedFingerprint
            val reusable = target.capabilities.persistentSession &&
                activeBackend != null && telemetry != null && installedContextMatches &&
                ConversationRecallPolicy.sameChunks(stickyRecall, mergedRecall) &&
                ChatAttachmentSessionPolicy.fitsSession(
                    committed = telemetry.committedMessages,
                    prompt = prompt,
                    input = target.capabilities.attachments,
                    accountingTokens = telemetry.accounting.tokens,
                    hardWatermarkTokens = budget.hardWatermarkTokens,
                    tokensPerUtf8Byte = estimator.tokensPerUtf8Byte,
                ) &&
                !ContextAccountingPolicy.shouldRotateBackend(
                    accounting = telemetry.accounting,
                    budget = budget,
                    completedTurnsOnBackend = completedTurnsOnBackend,
                )
            if (reusable) {
                reusableBackend = activeBackend
                reusableTelemetry = telemetry
            } else {
                emergencyDerivedContextDrop = telemetry?.accounting?.tokens
                    ?.let { tokens -> tokens >= budget.absoluteProtectionTokens } == true
                recallForRebuild = when {
                    emergencyDerivedContextDrop -> emptyList()
                    target.capabilities.persistentSession && installedContextMatches -> mergedRecall
                    else -> freshRecall
                }
                backendToReplace = activeBackend
                clearBackendLocked()
            }
        }

        val continuation = reusableBackend
        if (continuation != null) {
            val telemetry = checkNotNull(reusableTelemetry)
            val actualTarget = ConversationTargetSnapshot.from(continuation.target)
            observeContextTurnStart(generationId, continuation, actualTarget, prompt, telemetry, budget, rebuilt = false)
            recordActualTarget(generationId, actualTarget)
            val listener = generationListener(generationId, actualTarget)
            submitBackendWork(generationId) {
                if (!isGenerationCurrent(generationId)) return@submitBackendWork
                continuation.streamNext(generationRequest(emptyList(), prompt, request), listener)
            }
            return
        }

        val compiled = ChatConversationPolicy.compileContext(
            transcript = request.transcript,
            prompt = prompt,
            policy = ContextCompilationPolicy(
                estimator = estimator,
                maximumInputTokens = budget.compactionTargetTokens,
                contextState = contextStateSnapshot,
                includeDerivedContext = !emergencyDerivedContextDrop,
                recalledHistory = recallForRebuild,
                attachmentReplay = attachmentReplay,
            ),
        )
        if (compiled.exceedsInputBudget) {
            logger.warn(
                "Compiled context exceeds target after minimum-turn retention " +
                    "target=${requestedTarget.targetId} " +
                    "estimatedInputTokens=${compiled.estimatedInputTokens} " +
                    "compactionTargetTokens=${budget.compactionTargetTokens} " +
                    "coveredMessages=${compiled.coveredMessageIds.size}",
            )
        }
        val history = compiled.messages
        val historyEstimate = estimator.estimateMessages(history)
        logger.debug(
            "Context compile target=${requestedTarget.targetId} " +
                "estimatedInputTokens=${compiled.estimatedInputTokens} " +
                "historyTokens=${historyEstimate.estimatedTokens} " +
                "historyBytes=${historyEstimate.utf8Bytes} " +
                "coveredMessages=${compiled.coveredMessageIds.size} " +
                "workingMemoryItems=${compiled.workingMemoryItemsIncluded} " +
                "summarySegments=${compiled.summarySegmentsIncluded} " +
                "recallCandidates=${freshRecall.size} " +
                "recalledChunks=${compiled.recalledChunksIncluded} " +
                "emergencyDerivedDrop=$emergencyDerivedContextDrop " +
                "trimmed=${compiled.requiresSessionRebuild} " +
                "hardWatermarkTokens=${budget.hardWatermarkTokens} " +
                "compactionTargetTokens=${budget.compactionTargetTokens}",
        )
        // The replaced session is cancelled on its own thread like every other close.
        backendToReplace?.let(::cancelDetached)
        submitBackendWork(generationId) {
            if (!isGenerationCurrent(generationId)) return@submitBackendWork
            val created = backend().createSession(
                AiBackendSessionRequest(
                    targetId = requestedTarget.targetId,
                    executionProfileId = request.executionProfileId,
                ),
            )
            val actualTarget = ConversationTargetSnapshot.from(created.target)
            val installedTelemetry = synchronized(backendLock) {
                if (!isGenerationCurrent(generationId) || activeBackend != null) {
                    null
                } else {
                    backendContextEpoch = if (backendContextEpoch == Long.MAX_VALUE) {
                        Long.MAX_VALUE
                    } else {
                        backendContextEpoch + 1L
                    }
                    val telemetry = BackendContextTelemetry(
                        epoch = backendContextEpoch,
                        committedMessages = history.map { message -> message.contextSnapshot() },
                        accounting = ContextAccounting.initial(historyEstimate.estimatedTokens),
                        contextFingerprint = compiled.contextFingerprint,
                        contextStateFingerprint = ConversationContextPolicy.fingerprint(contextStateSnapshot),
                        recalledHistory = compiled.recalledHistory,
                        layerTokens = compiled.layerTokens,
                    )
                    activeBackend = created
                    activeBackendTarget = actualTarget
                    activeBackendAiTarget = created.target
                    activeBackendConversationId = request.conversationId
                    activeBackendContextTelemetry = telemetry
                    activeContextTurnObservation = null
                    completedTurnsOnBackend = 0
                    telemetry
                }
            }
            if (installedTelemetry == null) {
                runCatching(created::close)
                return@submitBackendWork
            }
            observeContextTurnStart(generationId, created, actualTarget, prompt, installedTelemetry, budget, rebuilt = true)
            scheduler.post(0L) { recordActualTarget(generationId, actualTarget) }
            val listener = generationListener(generationId, actualTarget)
            created.stream(generationRequest(history, prompt, request), listener)
        }
    }

    /**
     * The new user message (P4): its attachments when the target takes them, otherwise a note in
     * their place, like [ChatConversationPolicy.compileContext] does for earlier turns.
     */
    private fun promptMessage(request: ChatTurnRequest): GenerationMessage {
        val input = request.target.capabilities.attachments
        val unsupported = ChatAttachmentLimits.unsupported(request.promptAttachments, input).toSet()
        val included = ArrayList<GenerationAttachment>()
        val omitted = ArrayList<ChatAttachment>()
        request.promptAttachments.forEach { attachment ->
            val resolved = attachment.takeIf { it !in unsupported }
                ?.let { store.attachment(request.conversationId, it) }
            if (resolved == null) omitted += attachment else included += resolved
        }
        val note = ChatAttachmentNotes.omitted(omitted)
        val textParts = when {
            note == null -> listOf(request.prompt)
            request.prompt.isEmpty() -> listOf(note)
            else -> listOf(request.prompt, "\n\n$note")
        }
        return GenerationMessage(GenerationRole.USER, textParts, attachments = included)
    }

    private fun generationRequest(
        history: List<GenerationMessage>,
        prompt: GenerationMessage,
        request: ChatTurnRequest,
    ) = GenerationRequest(
        history = history,
        prompt = prompt,
        maximumOutputTokens = request.settings.maximumOutputTokens,
        samplingOptions = request.settings.samplingOptions(),
        reportUsage = true,
        effort = request.effort,
        fast = request.fast,
        // P3.1: the chat screen shows thinking text; scripts (Binder) never ask for it.
        includeReasoning = true,
    )

    private fun recordActualTarget(generationId: Long, actualTarget: ConversationTargetSnapshot) {
        val current = active?.takeIf { generation -> generation.generationId == generationId } ?: return
        if (current.actualTarget == actualTarget) return
        current.actualTarget = actualTarget
        notifyState()
    }

    private fun observeContextTurnStart(
        generationId: Long,
        backend: AiBackendSession,
        target: ConversationTargetSnapshot,
        prompt: GenerationMessage,
        telemetry: BackendContextTelemetry,
        budget: ContextBudget,
        rebuilt: Boolean,
    ) {
        val calibration = calibrationStore.current(target.targetId)
        val estimator = calibration.estimator()
        val promptSnapshot = prompt.contextSnapshot()
        val outbound = telemetry.committedMessages + promptSnapshot
        val inputEstimate = estimator.estimateMessages(outbound)
        // LiteRT reports only the newly processed prompt after its first turn. Online targets
        // report the complete replayed request on every turn.
        val calibrationInput = estimator.estimateMessages(
            ContextTokenObservationPolicy.calibrationInputMessages(
                locality = target.locality,
                rebuilt = rebuilt,
                committedMessages = telemetry.committedMessages,
                prompt = promptSnapshot,
            ),
        )
        val observation = ContextTurnObservation(
            generationId = generationId,
            backendEpoch = telemetry.epoch,
            prompt = promptSnapshot,
            rebuilt = rebuilt,
            inputEstimate = inputEstimate,
            calibrationInput = calibrationInput,
            coefficientBefore = calibration.tokensPerUtf8Byte,
            hardWatermarkTokens = budget.hardWatermarkTokens,
        )
        val installed = synchronized(backendLock) {
            if (
                activeBackend !== backend ||
                activeBackendContextTelemetry?.epoch != telemetry.epoch ||
                !isGenerationCurrent(generationId)
            ) {
                false
            } else {
                activeContextTurnObservation = observation
                true
            }
        }
        if (!installed) return
        logger.debug(
            "Context start target=${target.targetId} " +
                "estimatedInputTokens=${inputEstimate.estimatedTokens} " +
                "inputBytes=${inputEstimate.utf8Bytes} actualInputTokens=pending " +
                "accountingTokens=${telemetry.accounting.tokens} " +
                "accountingSource=${telemetry.accounting.source} " +
                "hardWatermarkTokens=${budget.hardWatermarkTokens} " +
                "compactionTargetTokens=${budget.compactionTargetTokens} " +
                "backendEpoch=${telemetry.epoch} rebuilt=$rebuilt " +
                "coefficient=${calibration.tokensPerUtf8Byte}",
        )
    }

    private fun completeContextTurnObservation(
        generationId: Long,
        actualTarget: ConversationTargetSnapshot,
        assistantText: String,
        statistics: GenerationStatistics?,
        cumulativeInputTokens: Long,
    ) {
        var observation: ContextTurnObservation? = null
        var committedMessages: List<GenerationMessage>? = null
        var previousAccounting: ContextAccounting? = null
        synchronized(backendLock) {
            val candidate = activeContextTurnObservation
            val telemetry = activeBackendContextTelemetry
            if (
                candidate != null && candidate.generationId == generationId &&
                telemetry != null && telemetry.epoch == candidate.backendEpoch
            ) {
                val committed = telemetry.committedMessages +
                    candidate.prompt.contextSnapshot() +
                    GenerationMessage(role = GenerationRole.ASSISTANT, textParts = listOf(assistantText))
                activeBackendContextTelemetry = telemetry.copy(committedMessages = committed)
                observation = candidate
                committedMessages = committed
                previousAccounting = telemetry.accounting
            }
            activeContextTurnObservation = null
        }
        val completedObservation = observation ?: return
        val completedMessages = committedMessages ?: return
        // D138: attachment tokens are only estimated, so such turns do not move the UTF-8 coefficient.
        val calibrationObservation = statistics?.takeIf {
            completedMessages.none { message -> message.attachments.isNotEmpty() }
        }?.let { usage ->
            ContextTokenCalibrationObservation(
                utf8Bytes = completedObservation.calibrationInput.utf8Bytes,
                messageCount = completedObservation.calibrationInput.messageCount,
                actualInputTokens = usage.inputTokens,
            )
        }
        val calibration = calibrationStore.record(actualTarget.targetId, calibrationObservation)
        val estimatedAfterTurn = calibration.estimator()
            .estimateMessages(completedMessages)
            .estimatedTokens
        val assistant = completedMessages.last()
        val estimatedTurnTokens = calibration.estimator()
            .estimateMessages(listOf(completedObservation.prompt, assistant))
            .estimatedTokens
        val accounting = ContextAccountingPolicy.afterSuccessfulTurn(
            current = previousAccounting ?: ContextAccounting.initial(),
            statistics = statistics,
            estimatedContextTokensAfterTurn = estimatedAfterTurn,
            estimatedTurnTokens = estimatedTurnTokens,
        )
        synchronized(backendLock) {
            val telemetry = activeBackendContextTelemetry
            if (telemetry?.epoch == completedObservation.backendEpoch) {
                activeBackendContextTelemetry = telemetry.copy(
                    accounting = accounting,
                    layerTokens = telemetry.layerTokens.withAdditionalRecentTokens(estimatedTurnTokens),
                )
            }
        }
        logger.debug(
            "Context complete target=${actualTarget.targetId} " +
                "estimatedInputTokens=${completedObservation.inputEstimate.estimatedTokens} " +
                "actualInputTokens=${statistics?.inputTokens} " +
                "actualOutputTokens=${statistics?.outputTokens} " +
                "cacheReadInputTokens=${statistics?.cachedInputTokens} " +
                "cacheWriteInputTokens=${statistics?.cacheWriteInputTokens} " +
                "cacheEligibleInputTokens=${statistics?.cacheEligibleInputTokens} " +
                "promptCacheHitRate=${statistics?.promptCacheHitRate} " +
                "accountingTokens=${accounting.tokens} accountingSource=${accounting.source} " +
                "hardWatermarkTokens=${completedObservation.hardWatermarkTokens} " +
                "cumulativeInputTokens=$cumulativeInputTokens " +
                "backendEpoch=${completedObservation.backendEpoch} " +
                "rebuilt=${completedObservation.rebuilt} " +
                "coefficientBefore=${completedObservation.coefficientBefore} " +
                "coefficientAfter=${calibration.tokensPerUtf8Byte}",
        )
    }

    private fun GenerationMessage.contextSnapshot(): GenerationMessage = copy(textParts = textParts.toList())

    private fun generationListener(
        generationId: Long,
        actualTarget: ConversationTargetSnapshot,
    ) = object : GenerationListener {
        override fun onTextDelta(text: String) = queueTextDelta(generationId, text)

        override fun onReasoningDelta(text: String) = queueReasoningDelta(generationId, text)

        override fun onCompleted(statistics: GenerationStatistics?) = onCompleted(statistics, truncated = false)

        override fun onCompleted(statistics: GenerationStatistics?, truncated: Boolean) {
            scheduler.post(0L) { completeGeneration(generationId, actualTarget, statistics, truncated) }
        }

        override fun onFailed(error: Throwable, statistics: GenerationStatistics?) {
            logger.error("Launcher chat generation failed", error)
            scheduler.post(0L) { failGeneration(generationId, error, actualTarget) }
        }
    }

    private fun submitBackendWork(generationId: Long, action: () -> Unit) {
        try {
            backendExecutor.execute {
                try {
                    action()
                } catch (error: Throwable) {
                    logger.error("Launcher chat generation crashed", error)
                    scheduler.post(0L) { failGeneration(generationId, error, null) }
                }
            }
        } catch (error: RejectedExecutionException) {
            logger.error("Launcher chat worker rejected generation", error)
            scheduler.post(0L) { failGeneration(generationId, error, null) }
        }
    }

    private fun queueTextDelta(generationId: Long, text: String) {
        if (text.isEmpty() || !isGenerationCurrent(generationId)) return
        val schedule = synchronized(deltaLock) {
            if (deltaGenerationId != generationId) return
            generatedText.append(text)
            // Reply text ends the current thinking stretch.
            closeReasoningLocked()
            scheduleFlushLocked()
        }
        if (schedule) scheduler.post(DELTA_FLUSH_INTERVAL_MILLIS) { flushTextDelta(generationId) }
    }

    private fun queueReasoningDelta(generationId: Long, text: String) {
        if (text.isEmpty() || !isGenerationCurrent(generationId)) return
        val schedule = synchronized(deltaLock) {
            if (deltaGenerationId != generationId) return
            if (reasoningOpenSince == null) {
                reasoningOpenSince = clock()
                reasoningChanged = true
            }
            // Characters never exceed UTF-8 bytes, so this keeps enough to fill the stored value.
            val room = ChatMessageReasoning.MAXIMUM_BYTES - generatedReasoning.length
            if (room > 0) {
                generatedReasoning.append(text, 0, minOf(room, text.length))
                reasoningChanged = true
            }
            if (text.length > room && !reasoningCut) {
                reasoningCut = true
                reasoningChanged = true
            }
            scheduleFlushLocked()
        }
        if (schedule) scheduler.post(DELTA_FLUSH_INTERVAL_MILLIS) { flushTextDelta(generationId) }
    }

    /** Returns whether the caller must post a flush; guarded by deltaLock. */
    private fun scheduleFlushLocked(): Boolean {
        if (deltaFlushScheduled) return false
        deltaFlushScheduled = true
        return true
    }

    /** Ends the current thinking stretch, if any; guarded by deltaLock. */
    private fun closeReasoningLocked() {
        val since = reasoningOpenSince ?: return
        reasoningMillis += (clock() - since).coerceAtLeast(0L)
        reasoningOpenSince = null
        reasoningChanged = true
    }

    private fun resetOutputLocked() {
        generatedText.clear()
        deliveredLength = 0
        deltaFlushScheduled = false
        generatedReasoning.clear()
        reasoningCut = false
        reasoningChanged = false
        reasoningOpenSince = null
        reasoningMillis = 0L
    }

    private fun outputLocked() = GeneratedOutput(
        text = generatedText.toString(),
        reasoning = generatedReasoning.takeIf { value -> value.isNotEmpty() }?.let { value ->
            ChatMessageReasoning.bounded(
                text = value.toString(),
                durationMillis = if (reasoningOpenSince == null) reasoningMillis else null,
                truncated = reasoningCut,
            )
        },
    )

    private fun flushTextDelta(generationId: Long) {
        var reasoning = false
        val delta = synchronized(deltaLock) {
            if (deltaGenerationId != generationId) return
            deltaFlushScheduled = false
            if (observer == null) return
            reasoning = reasoningChanged && generatedReasoning.isNotEmpty()
            reasoningChanged = false
            generatedText.substring(deliveredLength).also { deliveredLength = generatedText.length }
        }
        if ((delta.isEmpty() && !reasoning) || !isGenerationCurrent(generationId)) return
        val current = active ?: return
        val snapshot = current.snapshot(currentOutput())
        if (reasoning) observer?.onReasoning(snapshot)
        if (delta.isNotEmpty()) observer?.onDelta(snapshot, delta)
    }

    private fun currentOutput(): GeneratedOutput = synchronized(deltaLock) { outputLocked() }

    /** Delivers any undelivered text and ends the delta stream of [generationId]. */
    private fun finishOutput(generationId: Long): GeneratedOutput {
        flushTextDelta(generationId)
        return synchronized(deltaLock) {
            val output = if (deltaGenerationId == generationId) {
                closeReasoningLocked()
                outputLocked()
            } else {
                GeneratedOutput("", null)
            }
            deltaGenerationId = 0L
            resetOutputLocked()
            output
        }
    }

    private fun completeGeneration(
        generationId: Long,
        actualTarget: ConversationTargetSnapshot,
        statistics: GenerationStatistics?,
        truncated: Boolean,
    ) {
        if (!isGenerationCurrent(generationId)) return
        val current = active ?: return
        val output = finishOutput(generationId)
        val text = output.text
        val completion = ChatGenerationCompletion(
            text = text,
            target = actualTarget,
            usage = statistics?.let { value ->
                ChatMessageUsage(
                    inputTokens = value.inputTokens,
                    outputTokens = value.outputTokens,
                    durationMillis = value.durationMillis,
                )
            },
            truncated = truncated,
            reasoning = output.reasoning,
        )
        val cumulativeInputTokens = conversationSource(current.conversationId)?.messages
            ?.map { message ->
                if (message.id == current.assistantMessageId) {
                    message.copy(status = ChatMessageStatus.COMPLETE, usage = completion.usage)
                } else {
                    message
                }
            }
            ?.let { messages -> ChatConversationPolicy.cumulativeInputTokensThrough(messages, current.assistantMessageId) }
            ?: 0L
        completeContextTurnObservation(generationId, actualTarget, text, statistics, cumulativeInputTokens)
        synchronized(backendLock) {
            if (activeBackend != null) completedTurnsOnBackend++
        }
        active = null
        val snapshot = current.snapshot(output).copy(target = actualTarget)
        val displayed = observer?.takeIf { value -> value.conversationSnapshot(current.conversationId) != null }
        if (displayed != null) {
            displayed.onCompleted(snapshot, completion)
        } else {
            persistOutside(current.conversationId, current.assistantMessageId) { message ->
                message.copy(
                    text = text,
                    status = ChatMessageStatus.COMPLETE,
                    target = actualTarget,
                    usage = completion.usage,
                    truncated = truncated,
                    reasoning = completion.reasoning,
                )
            }
        }
        notifyState()
        maybeScheduleSummaryCheckpoint(current.conversationId, current.settings)
    }

    private fun failGeneration(
        generationId: Long,
        error: Throwable,
        actualTarget: ConversationTargetSnapshot?,
    ) {
        if (!isGenerationCurrent(generationId)) return
        val current = active ?: return
        val output = finishOutput(generationId)
        val text = output.text
        val failedTarget = actualTarget ?: current.actualTarget ?: current.requestedTarget
        val failure = failureText(failedTarget, error)
        active = null
        generationEpoch.incrementAndGet()
        closeBackend(reason = "failed")
        val snapshot = current.snapshot(output).copy(target = failedTarget)
        val displayed = observer?.takeIf { value -> value.conversationSnapshot(current.conversationId) != null }
        if (displayed != null) {
            displayed.onFailed(snapshot, failure)
        } else {
            persistOutside(current.conversationId, current.assistantMessageId) { message ->
                message.copy(
                    text = ChatGenerationText.withFailure(text, failure),
                    status = ChatMessageStatus.FAILED,
                    usage = null,
                    target = failedTarget,
                    reasoning = output.reasoning,
                )
            }
        }
        notifyState()
    }

    /** Applies [transform] to a stored message of a conversation that no screen shows. */
    private fun persistOutside(
        conversationId: String,
        messageId: Long,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        val stored = store.find(conversationId) ?: return
        var changed = false
        val messages = stored.messages.map { message ->
            if (message.id == messageId && message.role == ChatMessageRole.ASSISTANT) {
                changed = true
                transform(message)
            } else {
                message
            }
        }
        if (!changed) return
        val updated = stored.copy(
            messages = messages,
            updatedAtMillis = System.currentTimeMillis().coerceAtLeast(stored.createdAtMillis),
        )
        val persisted = runCatching { store.update(updated) }
            .onFailure { error -> logger.error("Unable to persist detached generation", error) }
            .getOrDefault(false)
        if (persisted) {
            if (observer == null) changedWhileDetached += conversationId
            logger.debug("Detached generation persisted conversation=$conversationId message=$messageId")
        }
    }

    private fun conversationSource(conversationId: String): ChatConversationSnapshot? =
        observer?.conversationSnapshot(conversationId)
            ?: store.find(conversationId)?.let { stored ->
                ChatConversationSnapshot(stored.messages, stored.contextState)
            }

    private fun maybeScheduleSummaryCheckpoint(conversationId: String, settings: ChatUiSettings) {
        if (activeSummaryTask != null) return
        val (target, accounting) = synchronized(backendLock) {
            if (activeBackendConversationId != conversationId) return
            val target = activeBackendAiTarget ?: return
            target to activeBackendContextTelemetry
        }
        if (!target.configured || !target.available || !target.capabilities.streaming) return
        val source = conversationSource(conversationId) ?: return
        if (accounting?.contextStateFingerprint != ConversationContextPolicy.fingerprint(source.contextState)) return
        val chatBudget = ContextBudgetCalculator.calculate(
            targetLimits = target.limits,
            applicationInputTokenBudget = settings.contextTokenBudget,
            maximumOutputTokens = settings.maximumOutputTokens,
        )
        if (!SummaryCheckpointTriggerPolicy.shouldSchedule(accounting.accounting, chatBudget)) return

        val summaryBudget = ContextBudgetCalculator.calculate(
            targetLimits = target.limits,
            applicationInputTokenBudget = settings.contextTokenBudget,
            maximumOutputTokens = ContextPolicy.SUMMARY_MAXIMUM_OUTPUT_TOKENS,
        )
        val maximumSourceTokens = minOf(
            ContextPolicy.SUMMARY_SOURCE_MAXIMUM_TOKENS.toLong(),
            summaryBudget.effectiveInputTokens - ContextPolicy.SUMMARY_INPUT_INSTRUCTION_RESERVE_TOKENS.toLong(),
        )
        if (maximumSourceTokens <= 0L) {
            logger.warn(
                "Context summary skipped target=${target.targetId} " +
                    "effectiveInputTokens=${summaryBudget.effectiveInputTokens} reason=no-source-budget",
            )
            return
        }

        val transcriptSnapshot = source.messages
        val stateSnapshot = source.contextState
        val estimator = calibrationStore.current(target.targetId).estimator()
        val compacted = ChatConversationPolicy.compileContext(
            transcript = transcriptSnapshot,
            prompt = GenerationMessage(GenerationRole.USER, listOf("")),
            policy = ContextCompilationPolicy(
                estimator = estimator,
                maximumInputTokens = chatBudget.compactionTargetTokens,
                contextState = stateSnapshot,
            ),
        )
        val plan = SummaryCheckpointPlanner.planWithinInputBudget(
            transcript = transcriptSnapshot,
            state = stateSnapshot,
            retainedRawMessageIds = compacted.coveredMessageIds,
            estimator = estimator,
            maximumSourceTokens = maximumSourceTokens,
            maximumInputTokens = summaryBudget.effectiveInputTokens,
        ) ?: return
        val planKey = summaryPlanKey(plan)
        if (failedSummaryPlanKey == planKey) return

        val epoch = nextSummaryEpoch()
        activeSummaryConversationId = conversationId
        activeSummarySourceHash = plan.sourceHash
        logger.debug(
            "Context summary start target=${target.targetId} " +
                "conversation=$conversationId range=${plan.firstMessageId}-${plan.lastMessageId} " +
                "sourceMessages=${plan.sourceMessages.size} " +
                "sourceTokens=${plan.sourceEstimate.estimatedTokens} " +
                "accountingTokens=${accounting.accounting.tokens} " +
                "softWatermarkTokens=${chatBudget.softWatermarkTokens}",
        )
        activeSummaryTask = SummaryCheckpointer(backend(), summaryExecutor).generate(
            SummaryCheckpointRequest(
                targetId = target.targetId,
                executionProfileId = target.chatExecutionProfileId(),
                structuredJson = target.capabilities.structuredJson,
                plan = plan,
                state = stateSnapshot,
                transcript = transcriptSnapshot,
                estimator = estimator,
                maximumInputTokens = summaryBudget.effectiveInputTokens,
            ),
        ) { outcome ->
            scheduler.post(0L) { completeSummaryCheckpoint(epoch, conversationId, planKey, plan, outcome) }
        }
    }

    private fun completeSummaryCheckpoint(
        epoch: Long,
        conversationId: String,
        planKey: String,
        plan: SummaryCheckpointPlan,
        outcome: SummaryCheckpointOutcome,
    ) {
        if (
            epoch != activeSummaryEpoch ||
            activeSummaryConversationId != conversationId ||
            activeSummarySourceHash != plan.sourceHash
        ) {
            return
        }
        activeSummaryTask = null
        activeSummaryConversationId = null
        activeSummarySourceHash = null
        when (outcome) {
            is SummaryCheckpointOutcome.Failure -> {
                failedSummaryPlanKey = planKey
                logger.warn(
                    "Context summary failed conversation=$conversationId " +
                        "range=${plan.firstMessageId}-${plan.lastMessageId} " +
                        "attempts=${outcome.attempts}; keeping previous checkpoint",
                    outcome.error,
                )
            }
            is SummaryCheckpointOutcome.Success -> {
                val source = conversationSource(conversationId) ?: return
                val validated = runCatching {
                    SummaryCheckpointValidator.apply(
                        state = source.contextState,
                        plan = plan,
                        draft = outcome.draft,
                        transcript = source.messages,
                    )
                }.onFailure { error ->
                    logger.warn(
                        "Context summary became stale conversation=$conversationId " +
                            "range=${plan.firstMessageId}-${plan.lastMessageId}; discarding result",
                        error,
                    )
                }.getOrNull()
                if (validated == null) {
                    failedSummaryPlanKey = planKey
                    return
                }
                if (validated != outcome.validatedState) {
                    failedSummaryPlanKey = planKey
                    logger.error(
                        "Context summary validation was non-deterministic; discarding result " +
                            "conversation=$conversationId range=${plan.firstMessageId}-${plan.lastMessageId}",
                    )
                    return
                }
                failedSummaryPlanKey = null
                val displayed = observer?.takeIf { value -> value.conversationSnapshot(conversationId) != null }
                if (displayed != null) {
                    displayed.onContextStateChanged(conversationId, validated)
                } else {
                    val stored = store.find(conversationId)
                    if (stored != null && store.update(stored.copy(contextState = validated)) && observer == null) {
                        changedWhileDetached += conversationId
                    }
                }
                if (active == null) closeBackend(reason = "summary-checkpoint")
                logger.debug(
                    "Context summary complete conversation=$conversationId " +
                        "range=${plan.firstMessageId}-${plan.lastMessageId} " +
                        "segments=${validated.summarySegments.size} " +
                        "workingMemoryItems=${validated.workingMemory.size} " +
                        "attempts=${outcome.attempts} " +
                        "inputTokens=${outcome.statistics?.inputTokens} " +
                        "outputTokens=${outcome.statistics?.outputTokens} " +
                        "fingerprint=${ConversationContextPolicy.fingerprint(validated)}",
                )
            }
        }
    }

    private fun nextSummaryEpoch(): Long {
        activeSummaryEpoch = if (activeSummaryEpoch == Long.MAX_VALUE) 1L else activeSummaryEpoch + 1L
        return activeSummaryEpoch
    }

    private fun summaryPlanKey(plan: SummaryCheckpointPlan): String =
        "${plan.previousCoveredThroughMessageId ?: 0L}:${plan.firstMessageId}"

    private fun detachBackend(): AiBackendSession? = synchronized(backendLock) {
        activeBackend.also { clearBackendLocked() }
    }

    private fun clearBackendLocked() {
        activeBackend = null
        activeBackendTarget = null
        activeBackendAiTarget = null
        activeBackendConversationId = null
        activeBackendContextTelemetry = null
        activeContextTurnObservation = null
        completedTurnsOnBackend = 0
    }

    private fun cancelDetached(session: AiBackendSession) {
        val cancel = Runnable { runCatching(session::cancelAndClose) }
        try {
            cancelExecutor.execute(cancel)
        } catch (_: RejectedExecutionException) {
            Thread(cancel, "three-stone-ai-chat-close").apply { isDaemon = true }.start()
        }
    }

    private fun notifyState() {
        val current = active
        observer?.onStateChanged(current?.snapshot(currentOutput()))
    }

    private fun isGenerationCurrent(generationId: Long): Boolean = generationEpoch.get() == generationId

    companion object {
        const val TAG = "ThreeStoneAiChat"
        const val DELTA_FLUSH_INTERVAL_MILLIS = 32L
        private const val NANOS_PER_MILLI = 1_000_000L

        @Volatile
        private var instance: ChatGenerationCoordinator? = null

        fun get(context: Context): ChatGenerationCoordinator = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { created -> instance = created }
        }

        private fun create(context: Context): ChatGenerationCoordinator {
            val application = context as ThreeStoneAiApplication
            val history = ConversationHistoryStore(context)
            val attachments = ChatAttachmentStore(context)
            val handler = Handler(Looper.getMainLooper())
            return ChatGenerationCoordinator(
                backend = { application.aiBackend },
                store = object : ChatGenerationStore {
                    override fun find(conversationId: String) = history.find(conversationId)

                    override fun update(conversation: StoredConversation) =
                        history.upsert(conversation, reviveDeleted = false)

                    override fun recall(conversationId: String, query: String, excludedMessageIds: Set<Long>) =
                        history.recall(conversationId, query, excludedMessageIds)

                    override fun attachment(conversationId: String, attachment: ChatAttachment) =
                        attachments.generationAttachment(conversationId, attachment)
                },
                calibrationStore = ContextTokenCalibrationStore(context),
                scheduler = object : ChatGenerationScheduler {
                    override fun post(delayMillis: Long, action: () -> Unit) {
                        if (delayMillis <= 0L) handler.post(action) else handler.postDelayed(action, delayMillis)
                    }
                },
                failureText = { target, error ->
                    ChatGenerationText(AppConfiguration.wrap(context)).failure(target, error)
                },
                logger = object : ChatGenerationLogger {
                    override fun debug(message: String) {
                        Log.d(TAG, message)
                    }

                    override fun warn(message: String, error: Throwable?) {
                        Log.w(TAG, message, error)
                    }

                    override fun error(message: String, error: Throwable?) {
                        Log.e(TAG, message, error)
                    }
                },
            )
        }
    }
}

/** The execution profile a chat turn and its summaries use for [this] target. */
internal fun AiTarget.chatExecutionProfileId(): String? = when (locality) {
    AiTargetLocality.REMOTE -> null
    AiTargetLocality.LOCAL -> executionProfiles
        .firstOrNull { profile ->
            profile.profileId == AiProviderBackendProfile.CPU && profile.available
        }
        ?.profileId
        ?: executionProfiles.firstOrNull { profile -> profile.available }?.profileId
}
