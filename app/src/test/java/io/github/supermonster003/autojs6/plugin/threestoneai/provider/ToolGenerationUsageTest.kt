package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import org.junit.Assert.*
import org.junit.Test

class ToolGenerationUsageTest {
    @Test
    fun sumsInvocationsButKeepsOnlyLatestContextOccupancy() {
        val usage = ToolGenerationUsage()
        assertEquals(5L, usage.add(GenerationStatistics(3, 2, 7, 5))!!.totalTokens)
        val combined = usage.add(GenerationStatistics(8, 1, 11, 9))!!
        assertEquals(11L, combined.inputTokens)
        assertEquals(3L, combined.outputTokens)
        assertEquals(14L, combined.totalTokens)
        assertEquals(18L, combined.durationMillis)
        assertEquals(9L, combined.contextTokensAfterTurn)
    }

    @Test
    fun missingUsageNeverBecomesAnApparentlyCompleteSnapshot() {
        val usage = ToolGenerationUsage()
        assertNotNull(usage.add(GenerationStatistics(3, 2, 1)))
        assertNull(usage.add(null))
        assertNull(usage.add(GenerationStatistics(8, 1, 1)))
    }

    @Test
    fun arithmeticOverflowIsRejected() {
        val usage = ToolGenerationUsage()
        usage.add(GenerationStatistics(Long.MAX_VALUE, 0, 0))
        assertThrows(ArithmeticException::class.java) { usage.add(GenerationStatistics(1, 0, 0)) }
    }
}
