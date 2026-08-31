package io.github.supermonster003.autojs6.plugin.threestoneai

/** Estimated contribution of each context layer currently committed to one backend. */
internal data class ContextLayerTokenUsage(
    val fixedInstructionTokens: Long = 0L,
    val workingMemoryTokens: Long = 0L,
    val summaryTokens: Long = 0L,
    val recalledHistoryTokens: Long = 0L,
    val recentRawTokens: Long = 0L,
) {
    init {
        require(fixedInstructionTokens >= 0L)
        require(workingMemoryTokens >= 0L)
        require(summaryTokens >= 0L)
        require(recalledHistoryTokens >= 0L)
        require(recentRawTokens >= 0L)
    }

    val estimatedTotalTokens: Long
        get() = listOf(
            fixedInstructionTokens,
            workingMemoryTokens,
            summaryTokens,
            recalledHistoryTokens,
            recentRawTokens,
        ).fold(0L, ::saturatedAdd)

    fun withAdditionalRecentTokens(tokens: Long): ContextLayerTokenUsage {
        require(tokens >= 0L)
        return copy(recentRawTokens = saturatedAdd(recentRawTokens, tokens))
    }

    companion object {
        val EMPTY = ContextLayerTokenUsage()
    }
}

internal data class ContextUsageSnapshot(
    val usedTokens: Long,
    val budgetTokens: Long,
    val softWatermarkTokens: Long,
    val hardWatermarkTokens: Long,
    val absoluteProtectionTokens: Long,
    val accountingSource: ContextAccountingSource,
    val layers: ContextLayerTokenUsage,
    /** Exact provider/template tokens that cannot be attributed to an estimated client layer. */
    val unattributedTokens: Long,
) {
    init {
        require(usedTokens >= 0L)
        require(budgetTokens > 0L)
        require(softWatermarkTokens in 1L..budgetTokens)
        require(hardWatermarkTokens in softWatermarkTokens..budgetTokens)
        require(absoluteProtectionTokens in hardWatermarkTokens..budgetTokens)
        require(unattributedTokens >= 0L)
    }

    val exact: Boolean
        get() = accountingSource == ContextAccountingSource.BACKEND_EXACT

    val usedPercent: Int
        get() = ((usedTokens.coerceAtMost(budgetTokens) * 100L) / budgetTokens).toInt()
}

internal object ContextUsagePolicy {
    fun snapshot(
        accounting: ContextAccounting,
        budget: ContextBudget,
        layers: ContextLayerTokenUsage,
    ): ContextUsageSnapshot = ContextUsageSnapshot(
        usedTokens = accounting.tokens,
        budgetTokens = budget.effectiveInputTokens,
        softWatermarkTokens = budget.softWatermarkTokens,
        hardWatermarkTokens = budget.hardWatermarkTokens,
        absoluteProtectionTokens = budget.absoluteProtectionTokens,
        accountingSource = accounting.source,
        layers = layers,
        unattributedTokens = (accounting.tokens - layers.estimatedTotalTokens).coerceAtLeast(0L),
    )
}
