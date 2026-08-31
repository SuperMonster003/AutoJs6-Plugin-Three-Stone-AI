package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.ContextAccounting
import io.github.supermonster003.autojs6.plugin.threestoneai.ContextAccountingPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.ContextBudget
import io.github.supermonster003.autojs6.plugin.threestoneai.ContextBudgetCalculator
import io.github.supermonster003.autojs6.plugin.threestoneai.ContextPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.ContextTokenEstimator
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import org.autojs.plugin.ai.common.api.AiErrorCode

internal object PersistentSessionContextLimits {
    const val MAXIMUM_TRANSCRIPT_BYTES = 512L * 1_024L
    const val COMPACTION_TARGET_PERCENT = 45
    const val MINIMUM_RECENT_TURNS = ContextPolicy.MINIMUM_RECENT_TURNS
}

internal object PersistentSessionFailurePolicy {
    val contextExhaustedErrorCode: Int = AiErrorCode.INVALID_REQUEST
    const val CONTEXT_EXHAUSTED_MESSAGE = "Persistent AI context exceeds the safe limit"
}

internal class PersistentSessionContextExhaustedException : IllegalArgumentException(
    PersistentSessionFailurePolicy.CONTEXT_EXHAUSTED_MESSAGE,
)

internal enum class PersistentBackendTurnMode {
    INITIAL,
    CONTINUE,
    REBUILD,
}

/** Keeps initial/rebuilt prefill and ordinary KV-cache continuation dispatch in one tested rule. */
internal fun AiBackendSession.streamPreparedPersistentTurn(
    plan: PreparedPersistentTurn,
    listener: GenerationListener,
) {
    when (plan.mode) {
        PersistentBackendTurnMode.INITIAL,
        PersistentBackendTurnMode.REBUILD,
        -> stream(plan.generationRequest, listener)
        PersistentBackendTurnMode.CONTINUE -> streamNext(plan.generationRequest, listener)
    }
}

internal data class PreparedPersistentTurn(
    val mode: PersistentBackendTurnMode,
    val generationRequest: GenerationRequest,
    val budget: ContextBudget,
    val estimatedInputTokens: Long,
    val retainedTranscriptBytes: Long,
    val retainedTurnCount: Int,
    val evictedTurnCount: Int,
    val backendEpoch: Long,
    internal val planId: Long,
    internal val stateVersion: Long,
    internal val accountingBefore: ContextAccounting,
    internal val baseTranscript: PersistentTranscript,
)

internal data class PersistentContextDiagnostics(
    val initialized: Boolean,
    val transcriptBytes: Long,
    val systemMessageCount: Int,
    val anchoredMessageCount: Int,
    val completedTurnCount: Int,
    val accounting: ContextAccounting,
    val completedTurnsOnBackend: Int,
    val backendEpoch: Long,
    val rebuildRequired: Boolean,
    val contextExhausted: Boolean,
)

internal data class PersistentContextCommit(
    val diagnostics: PersistentContextDiagnostics,
    val evictedByMemoryGuard: Int,
)

internal data class PersistentContextTurn(
    val user: GenerationMessage,
    val assistant: GenerationMessage,
) {
    init {
        require(user.role == GenerationRole.USER)
        require(assistant.role == GenerationRole.ASSISTANT)
    }

    fun messages(): List<GenerationMessage> = listOf(user, assistant)
}

/**
 * Plugin-owned transcript and rotation policy for one Binder persistent session.
 *
 * Only [complete] mutates durable in-memory context. Preparing, failing, or cancelling a turn
 * leaves the previous successful transcript untouched. No model call is made by this component.
 */
internal class PersistentSessionContext(
    private val estimator: ContextTokenEstimator = ContextTokenEstimator(),
    private val applicationInputTokenBudget: Int = ContextPolicy.DEFAULT_INPUT_TOKEN_BUDGET,
    private val maximumTranscriptBytes: Long =
        PersistentSessionContextLimits.MAXIMUM_TRANSCRIPT_BYTES,
    private val transcriptCompactionTargetBytes: Long =
        maximumTranscriptBytes * PersistentSessionContextLimits.COMPACTION_TARGET_PERCENT / 100L,
    private val minimumRecentTurns: Int = PersistentSessionContextLimits.MINIMUM_RECENT_TURNS,
    private val defaultMaximumRequestBytes: Long = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES,
) {
    private var initialized = false
    private var transcript = PersistentTranscript.EMPTY
    private var accounting = ContextAccounting.initial()
    private var completedTurnsOnBackend = 0
    private var backendEpoch = 0L
    private var rebuildRequired = false
    private var contextExhausted = false
    private var stateVersion = 0L
    private var nextPlanId = 1L
    private var activePlanId: Long? = null

    init {
        require(applicationInputTokenBudget > 0)
        require(maximumTranscriptBytes > 0L)
        require(transcriptCompactionTargetBytes in 1L..maximumTranscriptBytes)
        require(minimumRecentTurns >= 0)
        require(defaultMaximumRequestBytes > 0L)
    }

    @Synchronized
    fun prepareFirst(
        request: GenerationRequest,
        target: AiTarget,
    ): PreparedPersistentTurn {
        check(!initialized) { "Persistent context is already initialized" }
        require(request.prompt.role == GenerationRole.USER)
        val base = PersistentTranscript.fromInitialHistory(request.history)
        if (base.utf8Bytes > maximumTranscriptBytes) throw exhausted()
        val budget = budget(target, request)
        val estimate = estimator.estimateMessages(request.history + request.prompt)
        requireWithinAbsoluteLimits(
            estimatedTokens = estimate.estimatedTokens,
            utf8Bytes = estimate.utf8Bytes,
            budget = budget,
            target = target,
        )
        return activate(
            mode = PersistentBackendTurnMode.INITIAL,
            request = request.snapshot(),
            budget = budget,
            estimatedInputTokens = estimate.estimatedTokens,
            base = base,
            evictedTurnCount = 0,
            proposedBackendEpoch = incrementEpoch(backendEpoch),
            accountingBefore = ContextAccounting.initial(
                estimator.estimateMessages(request.history).estimatedTokens,
            ),
        )
    }

    @Synchronized
    fun prepareNext(
        request: GenerationRequest,
        target: AiTarget,
    ): PreparedPersistentTurn {
        check(initialized) { "Persistent context is not initialized" }
        require(request.history.isEmpty()) { "A persistent continuation may contain only its prompt" }
        require(request.prompt.role == GenerationRole.USER)
        if (contextExhausted) throw exhausted()
        val budget = budget(target, request)
        val promptEstimate = estimator.estimateMessage(request.prompt)
        val projectedTokens = saturatedAdd(accounting.tokens, promptEstimate.estimatedTokens)
        val projectedBytes = saturatedAdd(transcript.utf8Bytes, promptEstimate.utf8Bytes)
        val requestByteLimit = requestByteLimit(target)
        val rotate = rebuildRequired ||
            projectedTokens >= budget.hardWatermarkTokens ||
            projectedBytes > requestByteLimit ||
            ContextAccountingPolicy.shouldRotateBackend(
                accounting = accounting,
                budget = budget,
                completedTurnsOnBackend = completedTurnsOnBackend,
            )
        if (!rotate) {
            return activate(
                mode = PersistentBackendTurnMode.CONTINUE,
                request = request.snapshot(),
                budget = budget,
                estimatedInputTokens = projectedTokens,
                base = transcript,
                evictedTurnCount = 0,
                proposedBackendEpoch = backendEpoch,
                accountingBefore = accounting,
            )
        }

        val compilation = compile(
            transcript = transcript,
            prompt = request.prompt,
            maximumInputTokens = budget.compactionTargetTokens,
            maximumRequestBytes = requestByteLimit,
            accountedTranscriptTokens = accounting.tokens,
        )
        requireWithinAbsoluteLimits(
            estimatedTokens = compilation.estimatedInputTokens,
            utf8Bytes = compilation.estimatedInputBytes,
            budget = budget,
            target = target,
        )
        val rebuiltRequest = request.copy(
            history = compilation.transcript.messages().map(GenerationMessage::snapshot),
            prompt = request.prompt.snapshot(),
        )
        return activate(
            mode = PersistentBackendTurnMode.REBUILD,
            request = rebuiltRequest,
            budget = budget,
            estimatedInputTokens = compilation.estimatedInputTokens,
            base = compilation.transcript,
            evictedTurnCount = compilation.evictedTurnCount,
            proposedBackendEpoch = incrementEpoch(backendEpoch),
            accountingBefore = ContextAccounting.initial(
                compilation.estimatedHistoryTokens,
            ),
        )
    }

    @Synchronized
    fun complete(
        plan: PreparedPersistentTurn,
        assistantText: String,
        statistics: GenerationStatistics?,
    ): PersistentContextCommit {
        requireActive(plan)
        val assistant = GenerationMessage(
            role = GenerationRole.ASSISTANT,
            textParts = listOf(assistantText),
        )
        val appended = plan.baseTranscript.append(
            PersistentContextTurn(
                user = plan.generationRequest.prompt.snapshot(),
                assistant = assistant,
            ),
        )
        val guarded = enforceMemoryGuard(appended)
        val estimatedTurnTokens = estimator.estimateMessages(
            listOf(plan.generationRequest.prompt, assistant),
        ).estimatedTokens
        val updatedAccounting = ContextAccountingPolicy.afterSuccessfulTurn(
            current = plan.accountingBefore,
            statistics = statistics,
            estimatedContextTokensAfterTurn = null,
            estimatedTurnTokens = estimatedTurnTokens,
        )

        activePlanId = null
        initialized = true
        transcript = guarded.transcript
        accounting = updatedAccounting
        completedTurnsOnBackend = if (plan.mode == PersistentBackendTurnMode.CONTINUE) {
            (completedTurnsOnBackend + 1).coerceAtMost(Int.MAX_VALUE)
        } else {
            1
        }
        backendEpoch = plan.backendEpoch
        rebuildRequired = guarded.evictedTurnCount > 0
        contextExhausted = guarded.droppedNewestTurn
        stateVersion = incrementVersion(stateVersion)
        return PersistentContextCommit(
            diagnostics = diagnosticsLocked(),
            evictedByMemoryGuard = guarded.evictedTurnCount,
        )
    }

    /** Releases a prepared turn after failure or cancellation without committing its prompt. */
    @Synchronized
    fun abandon(plan: PreparedPersistentTurn) {
        if (activePlanId == plan.planId) activePlanId = null
    }

    @Synchronized
    fun diagnostics(): PersistentContextDiagnostics = diagnosticsLocked()

    private fun activate(
        mode: PersistentBackendTurnMode,
        request: GenerationRequest,
        budget: ContextBudget,
        estimatedInputTokens: Long,
        base: PersistentTranscript,
        evictedTurnCount: Int,
        proposedBackendEpoch: Long,
        accountingBefore: ContextAccounting,
    ): PreparedPersistentTurn {
        check(activePlanId == null) { "A persistent context turn is already active" }
        val planId = nextPlanId
        nextPlanId = incrementVersion(nextPlanId)
        activePlanId = planId
        return PreparedPersistentTurn(
            mode = mode,
            generationRequest = request,
            budget = budget,
            estimatedInputTokens = estimatedInputTokens,
            retainedTranscriptBytes = base.utf8Bytes,
            retainedTurnCount = base.turns.size,
            evictedTurnCount = evictedTurnCount,
            backendEpoch = proposedBackendEpoch,
            planId = planId,
            stateVersion = stateVersion,
            accountingBefore = accountingBefore,
            baseTranscript = base,
        )
    }

    private fun requireActive(plan: PreparedPersistentTurn) {
        check(activePlanId == plan.planId) { "Persistent context turn is stale" }
        check(stateVersion == plan.stateVersion) { "Persistent context changed during generation" }
    }

    private fun compile(
        transcript: PersistentTranscript,
        prompt: GenerationMessage,
        maximumInputTokens: Long,
        maximumRequestBytes: Long,
        accountedTranscriptTokens: Long,
    ): PersistentCompilation {
        val transcriptEstimate = estimator.estimateMessages(transcript.messages())
        val calibration = PersistentTokenCalibration.from(
            estimatedTranscriptTokens = transcriptEstimate.estimatedTokens,
            accountedTranscriptTokens = accountedTranscriptTokens,
        )
        val retainedReversed = ArrayList<PersistentContextTurn>()
        transcript.turns.asReversed().take(minimumRecentTurns).forEach(retainedReversed::add)
        for (turn in transcript.turns.asReversed().drop(retainedReversed.size)) {
            val candidate = retainedReversed + turn
            val candidateTranscript = transcript.withTurns(candidate.asReversed())
            val estimate = estimator.estimateMessages(candidateTranscript.messages() + prompt)
            if (
                calibration.apply(estimate.estimatedTokens) > maximumInputTokens ||
                estimate.utf8Bytes > maximumRequestBytes
            ) {
                break
            }
            retainedReversed += turn
        }
        val retained = transcript.withTurns(retainedReversed.asReversed())
        val historyEstimate = estimator.estimateMessages(retained.messages())
        val requestEstimate = estimator.estimateMessages(retained.messages() + prompt)
        return PersistentCompilation(
            transcript = retained,
            estimatedHistoryTokens = calibration.apply(historyEstimate.estimatedTokens),
            estimatedInputTokens = calibration.apply(requestEstimate.estimatedTokens),
            estimatedInputBytes = requestEstimate.utf8Bytes,
            evictedTurnCount = transcript.turns.size - retained.turns.size,
        )
    }

    private fun enforceMemoryGuard(value: PersistentTranscript): GuardedTranscript {
        if (value.utf8Bytes <= maximumTranscriptBytes) {
            return GuardedTranscript(value, evictedTurnCount = 0, droppedNewestTurn = false)
        }
        val retained = value.turns.toMutableList()
        var evicted = 0
        var candidate = value
        while (
            retained.size > minimumRecentTurns &&
            candidate.utf8Bytes > transcriptCompactionTargetBytes
        ) {
            retained.removeAt(0)
            evicted += 1
            candidate = value.withTurns(retained)
        }
        while (retained.isNotEmpty() && candidate.utf8Bytes > maximumTranscriptBytes) {
            retained.removeAt(0)
            evicted += 1
            candidate = value.withTurns(retained)
        }
        check(candidate.utf8Bytes <= maximumTranscriptBytes) {
            "Pinned persistent context exceeds its memory guard"
        }
        return GuardedTranscript(
            transcript = candidate,
            evictedTurnCount = evicted,
            droppedNewestTurn = retained.isEmpty(),
        )
    }

    private fun requireWithinAbsoluteLimits(
        estimatedTokens: Long,
        utf8Bytes: Long,
        budget: ContextBudget,
        target: AiTarget,
    ) {
        if (
            estimatedTokens > budget.absoluteProtectionTokens ||
            utf8Bytes > requestByteLimit(target)
        ) {
            throw exhausted()
        }
    }

    private fun budget(target: AiTarget, request: GenerationRequest): ContextBudget =
        ContextBudgetCalculator.calculate(
            targetLimits = target.limits,
            applicationInputTokenBudget = applicationInputTokenBudget,
            maximumOutputTokens = request.maximumOutputTokens,
        )

    private fun requestByteLimit(target: AiTarget): Long = minOf(
        maximumTranscriptBytes,
        target.limits.maximumContextBytes ?: defaultMaximumRequestBytes,
    )

    private fun diagnosticsLocked() = PersistentContextDiagnostics(
        initialized = initialized,
        transcriptBytes = transcript.utf8Bytes,
        systemMessageCount = transcript.systemMessages.size,
        anchoredMessageCount = transcript.anchoredMessages.size,
        completedTurnCount = transcript.turns.size,
        accounting = accounting,
        completedTurnsOnBackend = completedTurnsOnBackend,
        backendEpoch = backendEpoch,
        rebuildRequired = rebuildRequired,
        contextExhausted = contextExhausted,
    )

    private fun exhausted() = PersistentSessionContextExhaustedException()
}

internal class PersistentTranscript private constructor(
    val systemMessages: List<GenerationMessage>,
    val anchoredMessages: List<GenerationMessage>,
    val turns: List<PersistentContextTurn>,
    val utf8Bytes: Long,
) {
    fun messages(): List<GenerationMessage> = buildList {
        addAll(systemMessages)
        addAll(anchoredMessages)
        turns.forEach { turn -> addAll(turn.messages()) }
    }

    fun append(turn: PersistentContextTurn): PersistentTranscript = create(
        systemMessages = systemMessages,
        anchoredMessages = anchoredMessages,
        turns = turns + turn,
    )

    fun withTurns(value: List<PersistentContextTurn>): PersistentTranscript = create(
        systemMessages = systemMessages,
        anchoredMessages = anchoredMessages,
        turns = value,
    )

    companion object {
        val EMPTY = create(emptyList(), emptyList(), emptyList())

        fun fromInitialHistory(history: List<GenerationMessage>): PersistentTranscript {
            val system = history.filter { message -> message.role == GenerationRole.SYSTEM }
                .map(GenerationMessage::snapshot)
            val raw = history.filter { message -> message.role != GenerationRole.SYSTEM }
                .map(GenerationMessage::snapshot)
            val isCompleteTurnSequence = raw.size % 2 == 0 && raw.chunked(2).all { pair ->
                pair[0].role == GenerationRole.USER && pair[1].role == GenerationRole.ASSISTANT
            }
            val turns = if (isCompleteTurnSequence) {
                raw.chunked(2).map { pair -> PersistentContextTurn(pair[0], pair[1]) }
            } else {
                emptyList()
            }
            val anchored = if (isCompleteTurnSequence) emptyList() else raw
            return create(system, anchored, turns)
        }

        private fun create(
            systemMessages: List<GenerationMessage>,
            anchoredMessages: List<GenerationMessage>,
            turns: List<PersistentContextTurn>,
        ): PersistentTranscript {
            val system = systemMessages.map(GenerationMessage::snapshot)
            val anchored = anchoredMessages.map(GenerationMessage::snapshot)
            val copiedTurns = turns.map { turn ->
                PersistentContextTurn(turn.user.snapshot(), turn.assistant.snapshot())
            }
            return PersistentTranscript(
                systemMessages = system,
                anchoredMessages = anchored,
                turns = copiedTurns,
                utf8Bytes = messageBytes(
                    system + anchored + copiedTurns.flatMap(PersistentContextTurn::messages),
                ),
            )
        }
    }
}

private data class PersistentCompilation(
    val transcript: PersistentTranscript,
    val estimatedHistoryTokens: Long,
    val estimatedInputTokens: Long,
    val estimatedInputBytes: Long,
    val evictedTurnCount: Int,
)

/**
 * Scales the byte estimator with the latest full-context backend counter before a rebuild.
 * The ratio is never allowed below 1, so missing or unusually small counters cannot make the
 * compiler less conservative than its configured estimator.
 */
private data class PersistentTokenCalibration(
    val numerator: Long,
    val denominator: Long,
) {
    init {
        require(numerator > 0L)
        require(denominator > 0L)
        require(numerator >= denominator)
    }

    fun apply(estimatedTokens: Long): Long {
        require(estimatedTokens >= 0L)
        if (estimatedTokens == 0L || numerator == denominator) return estimatedTokens
        if (estimatedTokens > Long.MAX_VALUE / numerator) return Long.MAX_VALUE
        val product = estimatedTokens * numerator
        val quotient = product / denominator
        return if (product % denominator == 0L) quotient else saturatedAdd(quotient, 1L)
    }

    companion object {
        fun from(
            estimatedTranscriptTokens: Long,
            accountedTranscriptTokens: Long,
        ): PersistentTokenCalibration {
            require(estimatedTranscriptTokens >= 0L)
            require(accountedTranscriptTokens >= 0L)
            val denominator = estimatedTranscriptTokens.coerceAtLeast(1L)
            return PersistentTokenCalibration(
                numerator = maxOf(denominator, accountedTranscriptTokens),
                denominator = denominator,
            )
        }
    }
}

private data class GuardedTranscript(
    val transcript: PersistentTranscript,
    val evictedTurnCount: Int,
    val droppedNewestTurn: Boolean,
)

private fun GenerationRequest.snapshot() = copy(
    history = history.map(GenerationMessage::snapshot),
    prompt = prompt.snapshot(),
)

private fun GenerationMessage.snapshot() = copy(textParts = textParts.toList())

private fun messageBytes(messages: List<GenerationMessage>): Long {
    var total = 0L
    messages.forEach { message ->
        message.textParts.forEach { part ->
            total = saturatedAdd(total, part.toByteArray(Charsets.UTF_8).size.toLong())
        }
    }
    return total
}

private fun saturatedAdd(left: Long, right: Long): Long {
    require(left >= 0L && right >= 0L)
    return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}

private fun incrementEpoch(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1L

private fun incrementVersion(value: Long): Long = if (value == Long.MAX_VALUE) {
    throw IllegalStateException("Persistent context version is exhausted")
} else {
    value + 1L
}
