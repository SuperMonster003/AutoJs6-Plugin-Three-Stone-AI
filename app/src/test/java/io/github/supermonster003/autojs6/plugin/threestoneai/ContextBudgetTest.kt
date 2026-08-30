package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetTest {
    @Test
    fun `unknown target capacity is governed only by the application cost cap`() {
        val budget = ContextBudgetCalculator.calculate(
            limits(),
            applicationInputTokenBudget = 16_384,
            maximumOutputTokens = null,
        )

        assertEquals(16_384L, budget.effectiveInputTokens)
        assertEquals(4_096L, budget.outputReserveTokens)
        assertEquals(0L, budget.safetyMarginTokens)
        assertEquals(10_649L, budget.softWatermarkTokens)
        assertEquals(13_107L, budget.hardWatermarkTokens)
        assertEquals(14_745L, budget.absoluteProtectionTokens)
        assertEquals(7_372L, budget.compactionTargetTokens)
    }

    @Test
    fun `known target capacity deducts output reserve and safety before app cap`() {
        val budget = ContextBudgetCalculator.calculate(
            limits(maximumContextTokens = 12_000, maximumOutputTokens = 2_048),
            applicationInputTokenBudget = 16_384,
            maximumOutputTokens = 8_192,
        )

        assertEquals(12_000L, budget.targetMaximumContextTokens)
        assertEquals(2_048L, budget.outputReserveTokens)
        assertEquals(960L, budget.safetyMarginTokens)
        assertEquals(8_992L, budget.effectiveInputTokens)
    }

    @Test
    fun `application budget remains the lower cost cap for a large target`() {
        val budget = ContextBudgetCalculator.calculate(
            limits(maximumContextTokens = 200_000),
            applicationInputTokenBudget = 8_192,
            maximumOutputTokens = 1_024,
        )

        assertEquals(8_192L, budget.effectiveInputTokens)
        assertEquals(16_000L, budget.safetyMarginTokens)
    }

    @Test
    fun `impossibly small capacity is clamped without inventing extra capacity`() {
        val budget = ContextBudgetCalculator.calculate(
            limits(maximumContextTokens = 32),
            applicationInputTokenBudget = 8_192,
            maximumOutputTokens = 64,
        )

        assertEquals(1L, budget.effectiveInputTokens)
        assertEquals(1L, budget.softWatermarkTokens)
        assertEquals(1L, budget.hardWatermarkTokens)
        assertEquals(1L, budget.absoluteProtectionTokens)
        assertEquals(1L, budget.compactionTargetTokens)
    }

    @Test
    fun `accounting prefers exact full context then full and incremental estimates`() {
        val initial = ContextAccounting.initial(100L)
        val exact = ContextAccountingPolicy.afterSuccessfulTurn(
            current = initial,
            statistics = GenerationStatistics(9L, 2L, 3L, contextTokensAfterTurn = 77L),
            estimatedContextTokensAfterTurn = 60L,
            estimatedTurnTokens = 5L,
        )
        val fullEstimate = ContextAccountingPolicy.afterSuccessfulTurn(
            current = exact,
            statistics = GenerationStatistics(9L, 2L, 3L),
            estimatedContextTokensAfterTurn = 80L,
            estimatedTurnTokens = 5L,
        )
        val incremental = ContextAccountingPolicy.afterSuccessfulTurn(
            current = fullEstimate,
            statistics = null,
            estimatedContextTokensAfterTurn = null,
            estimatedTurnTokens = 7L,
        )

        assertEquals(ContextAccounting(77L, ContextAccountingSource.BACKEND_EXACT), exact)
        assertEquals(ContextAccounting(80L, ContextAccountingSource.FULL_ESTIMATE), fullEstimate)
        assertEquals(
            ContextAccounting(87L, ContextAccountingSource.INCREMENTAL_ESTIMATE),
            incremental,
        )
    }

    @Test
    fun `rotation uses hard watermark with a 64 turn leak guard`() {
        val budget = ContextBudgetCalculator.calculate(
            limits(),
            applicationInputTokenBudget = 100,
        )

        assertFalse(
            ContextAccountingPolicy.shouldRotateBackend(
                ContextAccounting.initial(79L),
                budget,
                completedTurnsOnBackend = 63,
            ),
        )
        assertTrue(
            ContextAccountingPolicy.shouldRotateBackend(
                ContextAccounting.initial(80L),
                budget,
                completedTurnsOnBackend = 0,
            ),
        )
        assertTrue(
            ContextAccountingPolicy.shouldRotateBackend(
                ContextAccounting.initial(0L),
                budget,
                completedTurnsOnBackend = 64,
            ),
        )
    }

    @Test
    fun `invalid inputs and token limits are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContextBudgetCalculator.calculate(limits(), applicationInputTokenBudget = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContextBudgetCalculator.calculate(limits(), maximumOutputTokens = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            limits(maximumContextTokens = -1)
        }
    }

    private fun limits(
        maximumContextTokens: Int? = null,
        maximumOutputTokens: Int? = null,
    ) = AiTargetLimits(
        maximumContextBytes = null,
        maximumOutputBytes = null,
        maximumOutputTokens = maximumOutputTokens,
        maximumContextTokens = maximumContextTokens,
    )
}
