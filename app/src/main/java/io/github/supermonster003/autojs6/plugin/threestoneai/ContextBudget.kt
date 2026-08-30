package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics

internal data class ContextBudget(
    val applicationInputTokens: Long,
    val targetMaximumContextTokens: Long?,
    val outputReserveTokens: Long,
    val safetyMarginTokens: Long,
    val effectiveInputTokens: Long,
    val softWatermarkTokens: Long,
    val hardWatermarkTokens: Long,
    val absoluteProtectionTokens: Long,
    val compactionTargetTokens: Long,
) {
    init {
        require(applicationInputTokens > 0L)
        require(targetMaximumContextTokens == null || targetMaximumContextTokens > 0L)
        require(outputReserveTokens >= 0L)
        require(safetyMarginTokens >= 0L)
        require(effectiveInputTokens in 1L..applicationInputTokens)
        require(softWatermarkTokens in 1L..effectiveInputTokens)
        require(hardWatermarkTokens in softWatermarkTokens..effectiveInputTokens)
        require(absoluteProtectionTokens in hardWatermarkTokens..effectiveInputTokens)
        require(compactionTargetTokens in 1L..hardWatermarkTokens)
    }
}

/** Calculates a cost cap without pretending an unknown provider window is a capacity claim. */
internal object ContextBudgetCalculator {
    fun calculate(
        targetLimits: AiTargetLimits,
        applicationInputTokenBudget: Int = ContextPolicy.DEFAULT_INPUT_TOKEN_BUDGET,
        maximumOutputTokens: Int? = null,
    ): ContextBudget {
        require(applicationInputTokenBudget > 0)
        require(maximumOutputTokens == null || maximumOutputTokens > 0)
        val applicationBudget = applicationInputTokenBudget.toLong()
        val configuredOutputReserve = (
            maximumOutputTokens ?: ContextPolicy.DEFAULT_OUTPUT_TOKEN_RESERVE
        ).toLong()
        val outputReserve = targetLimits.maximumOutputTokens
            ?.toLong()
            ?.let { targetMaximum -> minOf(configuredOutputReserve, targetMaximum) }
            ?: configuredOutputReserve
        val targetMaximum = targetLimits.maximumContextTokens?.toLong()
        val safetyMargin = targetMaximum?.let { maximum ->
            ceilPercentage(maximum, ContextPolicy.SAFETY_MARGIN_PERCENT)
        } ?: 0L
        val capacityBound = targetMaximum?.let { maximum ->
            (maximum - outputReserve - safetyMargin).coerceAtLeast(1L)
        }
        val effective = minOf(applicationBudget, capacityBound ?: applicationBudget)
        return ContextBudget(
            applicationInputTokens = applicationBudget,
            targetMaximumContextTokens = targetMaximum,
            outputReserveTokens = outputReserve,
            safetyMarginTokens = safetyMargin,
            effectiveInputTokens = effective,
            softWatermarkTokens = watermark(effective, ContextPolicy.SOFT_WATERMARK_PERCENT),
            hardWatermarkTokens = watermark(effective, ContextPolicy.HARD_WATERMARK_PERCENT),
            absoluteProtectionTokens = watermark(
                effective,
                ContextPolicy.ABSOLUTE_PROTECTION_PERCENT,
            ),
            compactionTargetTokens = watermark(
                effective,
                ContextPolicy.COMPACTION_TARGET_PERCENT,
            ),
        )
    }

    private fun ceilPercentage(value: Long, percent: Int): Long {
        require(value > 0L)
        require(percent in 0..100)
        return (value * percent.toLong() + 99L) / 100L
    }

    private fun watermark(value: Long, percent: Int): Long {
        require(value > 0L)
        require(percent in 1..100)
        return (value * percent.toLong() / 100L).coerceAtLeast(1L)
    }
}

internal enum class ContextAccountingSource {
    BACKEND_EXACT,
    FULL_ESTIMATE,
    INCREMENTAL_ESTIMATE,
}

internal data class ContextAccounting(
    val tokens: Long,
    val source: ContextAccountingSource,
) {
    init {
        require(tokens >= 0L)
    }

    companion object {
        fun initial(estimatedTokens: Long = 0L) = ContextAccounting(
            tokens = estimatedTokens,
            source = ContextAccountingSource.FULL_ESTIMATE,
        )
    }
}

/** Normalizes exact backend counters and conservative estimation behind one rotation decision. */
internal object ContextAccountingPolicy {
    const val MAXIMUM_BACKEND_TURNS = 64

    fun afterSuccessfulTurn(
        current: ContextAccounting,
        statistics: GenerationStatistics?,
        estimatedContextTokensAfterTurn: Long?,
        estimatedTurnTokens: Long,
    ): ContextAccounting {
        require(estimatedContextTokensAfterTurn == null || estimatedContextTokensAfterTurn >= 0L)
        require(estimatedTurnTokens >= 0L)
        statistics?.contextTokensAfterTurn?.let { exact ->
            return ContextAccounting(exact, ContextAccountingSource.BACKEND_EXACT)
        }
        estimatedContextTokensAfterTurn?.let { estimated ->
            return ContextAccounting(estimated, ContextAccountingSource.FULL_ESTIMATE)
        }
        return ContextAccounting(
            tokens = saturatedAdd(current.tokens, estimatedTurnTokens),
            source = ContextAccountingSource.INCREMENTAL_ESTIMATE,
        )
    }

    fun shouldRotateBackend(
        accounting: ContextAccounting,
        budget: ContextBudget,
        completedTurnsOnBackend: Int,
    ): Boolean {
        require(completedTurnsOnBackend >= 0)
        return accounting.tokens >= budget.hardWatermarkTokens ||
            completedTurnsOnBackend >= MAXIMUM_BACKEND_TURNS
    }
}
