package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextUsageTest {
    @Test
    fun layersAddSafelyAndSnapshotReconcilesExactProviderOverhead() {
        val layers = ContextLayerTokenUsage(
            fixedInstructionTokens = 10L,
            workingMemoryTokens = 20L,
            summaryTokens = 30L,
            recalledHistoryTokens = 40L,
            recentRawTokens = 50L,
        ).withAdditionalRecentTokens(25L)
        val snapshot = ContextUsagePolicy.snapshot(
            accounting = ContextAccounting(200L, ContextAccountingSource.BACKEND_EXACT),
            budget = budget(),
            layers = layers,
        )

        assertEquals(175L, layers.estimatedTotalTokens)
        assertEquals(25L, snapshot.unattributedTokens)
        assertEquals(50, snapshot.usedPercent)
        assertTrue(snapshot.exact)
    }

    @Test
    fun estimatedLayersAboveAccountingDoNotCreateNegativeProviderOverhead() {
        val snapshot = ContextUsagePolicy.snapshot(
            accounting = ContextAccounting(90L, ContextAccountingSource.FULL_ESTIMATE),
            budget = budget(),
            layers = ContextLayerTokenUsage(recentRawTokens = 100L),
        )

        assertEquals(0L, snapshot.unattributedTokens)
        assertFalse(snapshot.exact)
    }

    private fun budget() = ContextBudget(
        applicationInputTokens = 400L,
        targetMaximumContextTokens = null,
        outputReserveTokens = 0L,
        safetyMarginTokens = 0L,
        effectiveInputTokens = 400L,
        softWatermarkTokens = 260L,
        hardWatermarkTokens = 320L,
        absoluteProtectionTokens = 360L,
        compactionTargetTokens = 180L,
    )
}
