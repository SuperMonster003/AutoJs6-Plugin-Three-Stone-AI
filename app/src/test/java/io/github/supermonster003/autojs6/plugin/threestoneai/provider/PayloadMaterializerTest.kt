package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.provider.api.AiContentPart
import org.autojs.plugin.ai.provider.api.AiGenerationOptions
import org.autojs.plugin.ai.provider.api.AiMessage
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PayloadMaterializerTest {

    @Test
    fun materializesStructuredSchemaSeparatelyFromPromptBytes() {
        val prompt = "Return one object"
        val schema = """{"type":"object","properties":{"ok":{"type":"boolean"}}}"""

        val materialized = PayloadMaterializer.materializeRequest(
            request(prompt, schema),
            readDescriptor = { _, _ -> error("Inline request must not read a descriptor") },
        )

        assertEquals(prompt.toByteArray(Charsets.UTF_8).size.toLong(), materialized.inputBytes)
        assertEquals(prompt, materialized.messages.single().textParts.single())
        assertEquals(schema, materialized.responseSchemaJson)
    }

    @Test
    fun rejectsAJsonValueThatIsNotASchemaObject() {
        assertThrows(IllegalArgumentException::class.java) {
            PayloadMaterializer.materializeRequest(
                request("Return JSON", "[]"),
                readDescriptor = { _, _ -> error("Inline request must not read a descriptor") },
            )
        }
    }

    private fun request(prompt: String, schema: String): AiProviderRequest {
        val promptBytes = prompt.toByteArray(Charsets.UTF_8)
        val schemaBytes = schema.toByteArray(Charsets.UTF_8)
        return AiProviderRequest(
            requestId = "request-structured-json",
            protocolVersion = AiProviderProtocol.HOST_PROTOCOL_RANGE.maximum,
            providerId = ThreeStoneAiPlugin.PROVIDER_ID,
            modelId = "litertlm.${"ab".repeat(16)}",
            messages = listOf(
                AiMessage(
                    role = AiMessageRole.USER,
                    parts = listOf(
                        AiContentPart(
                            AiPayloadReference(
                                mimeType = AiProviderMimeType.PLAIN,
                                declaredLengthBytes = promptBytes.size.toLong(),
                                inlineBytes = promptBytes,
                                charset = "utf-8",
                            ),
                        ),
                    ),
                ),
            ),
            options = AiGenerationOptions(
                stream = false,
                includeReasoning = false,
                structuredJson = true,
                reportUsage = true,
                maximumOutputBytes = 4096L,
                timeoutMillis = 30_000L,
                responseMimeType = AiProviderMimeType.JSON,
                responseSchema = AiPayloadReference(
                    mimeType = AiProviderMimeType.JSON,
                    declaredLengthBytes = schemaBytes.size.toLong(),
                    inlineBytes = schemaBytes,
                    charset = "utf-8",
                ),
            ),
        )
    }
}
