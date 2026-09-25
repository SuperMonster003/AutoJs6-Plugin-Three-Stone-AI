package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics

/** HTTP usage is per invocation; the Binder surface publishes a cumulative request snapshot. */
internal class ToolGenerationUsage {
    private var cumulative: GenerationStatistics? = null
    private var incomplete = false

    @Synchronized
    fun add(next: GenerationStatistics?): GenerationStatistics? {
        if (next == null) incomplete = true
        if (incomplete) return null
        next!!
        val previous = cumulative
        return (if (previous == null) next else GenerationStatistics(
            inputTokens = Math.addExact(previous.inputTokens, next.inputTokens),
            outputTokens = Math.addExact(previous.outputTokens, next.outputTokens),
            durationMillis = Math.addExact(previous.durationMillis, next.durationMillis),
            contextTokensAfterTurn = next.contextTokensAfterTurn,
            cachedInputTokens = sumKnown(previous.cachedInputTokens, next.cachedInputTokens),
            cacheWriteInputTokens = sumKnown(previous.cacheWriteInputTokens, next.cacheWriteInputTokens),
            cacheEligibleInputTokens = sumKnown(previous.cacheEligibleInputTokens, next.cacheEligibleInputTokens),
        )).also { cumulative = it }
    }

    private fun sumKnown(a: Long?, b: Long?): Long? = if (a != null && b != null) Math.addExact(a, b) else null
}
