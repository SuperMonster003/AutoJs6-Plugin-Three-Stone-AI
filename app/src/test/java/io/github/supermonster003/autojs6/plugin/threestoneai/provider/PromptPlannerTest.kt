package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationSamplingOptions
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.provider.api.AiContentPart
import org.autojs.plugin.ai.provider.api.AiGenerationOptions
import org.autojs.plugin.ai.provider.api.AiMessage
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PromptPlannerTest {
    @Test
    fun mapsPlainTextHistoryAndFinalUserPrompt() {
        val request = request(listOf(AiMessageRole.SYSTEM, AiMessageRole.USER, AiMessageRole.ASSISTANT, AiMessageRole.USER))
        val materialized = MaterializedRequest(
            messages = request.messages.mapIndexed { index, message ->
                MaterializedMessage(message.role, listOf("part-$index"))
            },
            inputBytes = 24L,
        )
        val plan = PromptPlanner.plan(request, materialized)
        assertEquals(3, plan.history.size)
        assertEquals(GenerationRole.SYSTEM, plan.history.first().role)
        assertEquals(GenerationRole.ASSISTANT, plan.history.last().role)
        assertEquals(GenerationRole.USER, plan.prompt.role)
        assertEquals(listOf("part-3"), plan.prompt.textParts)
        assertEquals(false, plan.reportUsage)
    }

    @Test
    fun rejectsAssistantAsFinalPrompt() {
        val request = request(listOf(AiMessageRole.USER, AiMessageRole.ASSISTANT))
        val materialized = MaterializedRequest(
            request.messages.map { MaterializedMessage(it.role, listOf("text")) },
            8L,
        )
        assertThrows(IllegalArgumentException::class.java) { PromptPlanner.plan(request, materialized) }
    }

    @Test
    fun mapsExactGenerationOptionsAndCompletesPartialSamplerWithStableDefaults() {
        val exactRequest = request(
            roles = listOf(AiMessageRole.USER),
            maximumOutputTokens = 512,
            temperature = 0.75,
            topK = 32,
            topP = 0.9,
            reportUsage = true,
        )
        val exact = PromptPlanner.plan(
            exactRequest,
            MaterializedRequest(listOf(MaterializedMessage(AiMessageRole.USER, listOf("text"))), 4L),
        )
        assertEquals(512, exact.maximumOutputTokens)
        assertEquals(emptyList<Any>(), exact.history)
        assertEquals(GenerationSamplingOptions(0.75, 32, 0.9), exact.samplingOptions)
        assertEquals(true, exact.reportUsage)

        val partialRequest = request(
            roles = listOf(AiMessageRole.USER),
            temperature = 0.25,
        )
        val partial = PromptPlanner.plan(
            partialRequest,
            MaterializedRequest(listOf(MaterializedMessage(AiMessageRole.USER, listOf("text"))), 4L),
        )
        assertEquals(GenerationSamplingOptions(0.25, 1, 0.95), partial.samplingOptions)

        val defaultsRequest = request(listOf(AiMessageRole.USER))
        val defaults = PromptPlanner.plan(
            defaultsRequest,
            MaterializedRequest(listOf(MaterializedMessage(AiMessageRole.USER, listOf("text"))), 4L),
        )
        assertNull(defaults.maximumOutputTokens)
        assertNull(defaults.samplingOptions)
    }

    @Test
    fun mapsStructuredJsonSchemaAndUsesAStableObjectDefault() {
        val schema = """{"type":"object","properties":{"answer":{"type":"string"}}}"""
        val schemaRequest = request(
            roles = listOf(AiMessageRole.USER),
            structuredJson = true,
            responseSchemaJson = schema,
        )
        val schemaPlan = PromptPlanner.plan(
            schemaRequest,
            MaterializedRequest(
                messages = listOf(MaterializedMessage(AiMessageRole.USER, listOf("text"))),
                inputBytes = 4L,
                responseSchemaJson = schema,
            ),
        )
        assertEquals(schema, schemaPlan.responseJsonSchema)

        val defaultRequest = request(
            roles = listOf(AiMessageRole.USER),
            structuredJson = true,
        )
        val defaultPlan = PromptPlanner.plan(
            defaultRequest,
            MaterializedRequest(listOf(MaterializedMessage(AiMessageRole.USER, listOf("text"))), 4L),
        )
        assertEquals("""{"type":"object"}""", defaultPlan.responseJsonSchema)
        assertTrue(defaultPlan.responseJsonSchema!!.isNotEmpty())
    }

    private fun request(
        roles: List<Int>,
        maximumOutputTokens: Long? = null,
        temperature: Double? = null,
        topK: Int? = null,
        topP: Double? = null,
        reportUsage: Boolean = false,
        structuredJson: Boolean = false,
        responseSchemaJson: String? = null,
    ): AiProviderRequest = AiProviderRequest(
        requestId = "request-1",
        protocolVersion = AiProviderProtocol.HOST_PROTOCOL_RANGE.maximum,
        providerId = ThreeStoneAiPlugin.PROVIDER_ID,
        modelId = "litertlm.${"ab".repeat(16)}",
        messages = roles.map { role ->
            val bytes = "text".toByteArray()
            AiMessage(
                role = role,
                parts = listOf(
                    AiContentPart(
                        AiPayloadReference(
                            mimeType = AiProviderMimeType.PLAIN,
                            declaredLengthBytes = bytes.size.toLong(),
                            inlineBytes = bytes,
                            charset = "utf-8",
                        ),
                    ),
                ),
            )
        },
        options = AiGenerationOptions(
            stream = true,
            includeReasoning = false,
            structuredJson = structuredJson,
            reportUsage = reportUsage,
            maximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
            maximumOutputTokens = maximumOutputTokens,
            maximumToolRounds = 0,
            timeoutMillis = 30_000L,
            responseMimeType = if (structuredJson) AiProviderMimeType.JSON else AiProviderMimeType.PLAIN,
            responseSchema = responseSchemaJson?.let { schema ->
                val bytes = schema.toByteArray(Charsets.UTF_8)
                AiPayloadReference(
                    mimeType = AiProviderMimeType.JSON,
                    declaredLengthBytes = bytes.size.toLong(),
                    inlineBytes = bytes,
                    charset = "utf-8",
                )
            },
            temperature = temperature,
            topK = topK,
            topP = topP,
        ),
    )
}
