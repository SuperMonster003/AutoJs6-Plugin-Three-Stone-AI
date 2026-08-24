package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.ai.edge.litertlm.ResponseFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun structuredJsonMapsToThePackagedLiteRtLmNativeResponseFormat() {
        val schema = """{"type":"object","properties":{"ok":{"type":"boolean"}}}"""
        val request = GenerationRequest(
            history = emptyList(),
            prompt = GenerationMessage(GenerationRole.USER, listOf("Return JSON")),
            maximumOutputTokens = 128,
            samplingOptions = null,
            reportUsage = true,
            responseJsonSchema = schema,
        )

        val format = checkNotNull(request.toLiteRtResponseFormat())
        assertEquals(ResponseFormat.Type.JSON_OBJECT, format.type)
        assertEquals(schema, format.schemaOrPattern)
        assertNull(request.copy(responseJsonSchema = null).toLiteRtResponseFormat())
    }
}
