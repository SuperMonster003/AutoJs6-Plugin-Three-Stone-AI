package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import kotlin.math.ceil

/** Central defaults for context accounting. Later phases add budgets and watermarks here. */
internal object ContextPolicy {
    const val DEFAULT_INPUT_TOKEN_BUDGET = 16_384
    const val DEFAULT_OUTPUT_TOKEN_RESERVE = 4_096
    const val SAFETY_MARGIN_PERCENT = 8
    const val SOFT_WATERMARK_PERCENT = 65
    const val HARD_WATERMARK_PERCENT = 80
    const val ABSOLUTE_PROTECTION_PERCENT = 90
    const val COMPACTION_TARGET_PERCENT = 45
    const val MINIMUM_RECENT_TURNS = 2
    const val INITIAL_TOKENS_PER_UTF8_BYTE = 0.40
    const val MINIMUM_TOKENS_PER_UTF8_BYTE = 0.15
    const val MAXIMUM_TOKENS_PER_UTF8_BYTE = 0.60
    const val MESSAGE_ROLE_OVERHEAD_TOKENS = 4
    const val CALIBRATION_EMA_ALPHA = 0.20
}

internal data class ContextTokenEstimate(
    val utf8Bytes: Long,
    val messageCount: Int,
    val estimatedTokens: Long,
) {
    init {
        require(utf8Bytes >= 0L)
        require(messageCount >= 0)
        require(estimatedTokens >= 0L)
    }
}

/**
 * Provider-neutral, deliberately conservative token estimate.
 *
 * Arbitrary compatible endpoints do not expose a reliable tokenizer. Text is therefore measured
 * in UTF-8 bytes and converted with a target-calibrated coefficient; one fixed role/template
 * overhead is charged per message. Transport byte limits remain a separate safety boundary.
 */
internal class ContextTokenEstimator(
    val tokensPerUtf8Byte: Double = ContextPolicy.INITIAL_TOKENS_PER_UTF8_BYTE,
    val messageRoleOverheadTokens: Int = ContextPolicy.MESSAGE_ROLE_OVERHEAD_TOKENS,
) {
    init {
        require(tokensPerUtf8Byte.isFinite() && tokensPerUtf8Byte > 0.0)
        require(messageRoleOverheadTokens >= 0)
    }

    fun estimateText(text: String): Long = estimateUtf8Bytes(
        text.toByteArray(Charsets.UTF_8).size.toLong(),
    )

    fun estimateMessage(message: GenerationMessage): ContextTokenEstimate =
        estimateMessages(listOf(message))

    fun estimateMessages(messages: List<GenerationMessage>): ContextTokenEstimate {
        var utf8Bytes = 0L
        messages.forEach { message ->
            message.textParts.forEach { part ->
                utf8Bytes = saturatedAdd(
                    utf8Bytes,
                    part.toByteArray(Charsets.UTF_8).size.toLong(),
                )
            }
        }
        val textTokens = estimateUtf8Bytes(utf8Bytes)
        val roleTokens = saturatedMultiply(messages.size.toLong(), messageRoleOverheadTokens.toLong())
        return ContextTokenEstimate(
            utf8Bytes = utf8Bytes,
            messageCount = messages.size,
            estimatedTokens = saturatedAdd(textTokens, roleTokens),
        )
    }

    private fun estimateUtf8Bytes(bytes: Long): Long {
        require(bytes >= 0L)
        if (bytes == 0L) return 0L
        val scaled = bytes.toDouble() * tokensPerUtf8Byte
        return if (!scaled.isFinite() || scaled >= Long.MAX_VALUE.toDouble()) {
            Long.MAX_VALUE
        } else {
            ceil(scaled).toLong()
        }
    }
}

/** Normalizes the different usage counter semantics exposed by online and LiteRT sessions. */
internal object ContextTokenObservationPolicy {
    fun calibrationInputMessages(
        locality: AiTargetLocality,
        rebuilt: Boolean,
        committedMessages: List<GenerationMessage>,
        prompt: GenerationMessage,
    ): List<GenerationMessage> =
        if (locality == AiTargetLocality.LOCAL && !rebuilt) {
            listOf(prompt)
        } else {
            committedMessages + prompt
        }
}

internal fun saturatedAdd(left: Long, right: Long): Long {
    require(left >= 0L && right >= 0L)
    return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}

private fun saturatedMultiply(left: Long, right: Long): Long {
    require(left >= 0L && right >= 0L)
    if (left == 0L || right == 0L) return 0L
    return if (left > Long.MAX_VALUE / right) Long.MAX_VALUE else left * right
}
