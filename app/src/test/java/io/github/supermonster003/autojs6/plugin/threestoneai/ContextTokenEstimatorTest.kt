package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContextTokenEstimatorTest {
    private val estimator = ContextTokenEstimator()

    @Test
    fun `text estimation uses utf8 bytes for english chinese and code`() {
        assertEquals(2L, estimator.estimateText("hello"))
        assertEquals(4L, estimator.estimateText("上下文"))
        assertEquals(5L, estimator.estimateText("fun x() = 1"))
    }

    @Test
    fun `empty text has no text cost while an empty message retains role overhead`() {
        assertEquals(0L, estimator.estimateText(""))

        val estimate = estimator.estimateMessage(message(GenerationRole.USER, emptyList()))

        assertEquals(0L, estimate.utf8Bytes)
        assertEquals(1, estimate.messageCount)
        assertEquals(4L, estimate.estimatedTokens)
    }

    @Test
    fun `multi part message measures combined utf8 payload once`() {
        val estimate = estimator.estimateMessage(
            message(GenerationRole.USER, listOf("你", "abc")),
        )

        assertEquals(6L, estimate.utf8Bytes)
        assertEquals(1, estimate.messageCount)
        assertEquals(7L, estimate.estimatedTokens)
    }

    @Test
    fun `message list charges one role overhead per message`() {
        val estimate = estimator.estimateMessages(
            listOf(
                message(GenerationRole.SYSTEM, listOf("rules")),
                message(GenerationRole.USER, listOf("hello")),
                message(GenerationRole.ASSISTANT, listOf("world")),
            ),
        )

        assertEquals(15L, estimate.utf8Bytes)
        assertEquals(3, estimate.messageCount)
        assertEquals(18L, estimate.estimatedTokens)
    }

    @Test
    fun `custom calibrated coefficient is applied exactly`() {
        val calibrated = ContextTokenEstimator(tokensPerUtf8Byte = 0.25)

        assertEquals(3L, calibrated.estimateText("123456789"))
    }

    @Test
    fun `premeasured payload uses the same text and role accounting`() {
        val estimate = estimator.estimatePayload(15L, 3)

        assertEquals(15L, estimate.utf8Bytes)
        assertEquals(3, estimate.messageCount)
        assertEquals(18L, estimate.estimatedTokens)
    }

    @Test
    fun `invalid estimator parameters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContextTokenEstimator(tokensPerUtf8Byte = 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContextTokenEstimator(tokensPerUtf8Byte = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContextTokenEstimator(messageRoleOverheadTokens = -1)
        }
    }

    @Test
    fun `calibration input follows backend usage counter semantics`() {
        val history = listOf(message(GenerationRole.USER, listOf("old")))
        val prompt = message(GenerationRole.USER, listOf("new"))

        assertEquals(
            listOf(prompt),
            ContextTokenObservationPolicy.calibrationInputMessages(
                AiTargetLocality.LOCAL,
                rebuilt = false,
                committedMessages = history,
                prompt = prompt,
            ),
        )
        assertEquals(
            history + prompt,
            ContextTokenObservationPolicy.calibrationInputMessages(
                AiTargetLocality.LOCAL,
                rebuilt = true,
                committedMessages = history,
                prompt = prompt,
            ),
        )
        assertEquals(
            history + prompt,
            ContextTokenObservationPolicy.calibrationInputMessages(
                AiTargetLocality.REMOTE,
                rebuilt = false,
                committedMessages = history,
                prompt = prompt,
            ),
        )
    }

    private fun message(role: GenerationRole, parts: List<String>) = GenerationMessage(
        role = role,
        textParts = parts,
    )
}
