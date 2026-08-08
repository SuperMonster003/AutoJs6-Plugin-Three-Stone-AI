package io.github.supermonster003.autojs6.plugin.ai.text.provider

import io.github.supermonster003.autojs6.plugin.ai.text.AiTextPlugin
import io.github.supermonster003.autojs6.plugin.ai.text.backend.GenerationRole
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.text.api.AiContentPart
import org.autojs.plugin.ai.text.api.AiGenerationOptions
import org.autojs.plugin.ai.text.api.AiMessage
import org.autojs.plugin.ai.text.api.AiMessageRole
import org.autojs.plugin.ai.text.api.AiTextMimeType
import org.autojs.plugin.ai.text.api.AiTextProtocol
import org.autojs.plugin.ai.text.api.AiTextRequest
import org.junit.Assert.assertEquals
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

    private fun request(roles: List<Int>): AiTextRequest = AiTextRequest(
        requestId = "request-1",
        protocolVersion = AiTextProtocol.HOST_PROTOCOL_RANGE.maximum,
        providerId = AiTextPlugin.PROVIDER_ID,
        modelId = "litertlm.${"ab".repeat(16)}",
        messages = roles.map { role ->
            val bytes = "text".toByteArray()
            AiMessage(
                role = role,
                parts = listOf(
                    AiContentPart(
                        AiPayloadReference(
                            mimeType = AiTextMimeType.PLAIN,
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
            structuredJson = false,
            reportUsage = false,
            maximumOutputBytes = AiTextPlugin.MAXIMUM_OUTPUT_BYTES,
            maximumToolRounds = 0,
            timeoutMillis = 30_000L,
        ),
    )
}
