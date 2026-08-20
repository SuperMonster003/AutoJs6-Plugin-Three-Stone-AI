package io.github.supermonster003.autojs6.plugin.ondeviceai.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LiteRtLmGenerationConfigTest {
    @Test
    fun samplingOptionsMapExactlyToLiteRtLmSamplerConfig() {
        val sampler = GenerationSamplingOptions(
            temperature = 0.75,
            topK = 32,
            topP = 0.9,
        ).toLiteRtSamplerConfig()

        assertEquals(0.75, sampler.temperature, 0.0)
        assertEquals(32, sampler.topK)
        assertEquals(0.9, sampler.topP, 0.0)
        assertEquals(0, sampler.seed)
    }

    @Test
    fun samplingOptionsRejectValuesOutsideLiteRtLmBoundary() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1).forEach { temperature ->
            assertThrows(IllegalArgumentException::class.java) {
                GenerationSamplingOptions(temperature, 1, 0.95)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSamplingOptions(1.0, 0, 0.95)
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1).forEach { topP ->
            assertThrows(IllegalArgumentException::class.java) {
                GenerationSamplingOptions(1.0, 1, topP)
            }
        }
    }
}
