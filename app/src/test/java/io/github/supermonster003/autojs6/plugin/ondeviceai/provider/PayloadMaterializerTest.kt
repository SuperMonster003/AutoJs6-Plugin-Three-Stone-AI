package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import io.github.supermonster003.autojs6.plugin.ondeviceai.OnDeviceAiPlugin
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ondeviceai.api.AiContentPart
import org.autojs.plugin.ondeviceai.api.AiGenerationOptions
import org.autojs.plugin.ondeviceai.api.AiMessage
import org.autojs.plugin.ondeviceai.api.AiMessageRole
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
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

    private fun request(prompt: String, schema: String): OnDeviceAiRequest {
        val promptBytes = prompt.toByteArray(Charsets.UTF_8)
        val schemaBytes = schema.toByteArray(Charsets.UTF_8)
        return OnDeviceAiRequest(
            requestId = "request-structured-json",
            protocolVersion = OnDeviceAiProtocol.HOST_PROTOCOL_RANGE.maximum,
            providerId = OnDeviceAiPlugin.PROVIDER_ID,
            modelId = "litertlm.${"ab".repeat(16)}",
            messages = listOf(
                AiMessage(
                    role = AiMessageRole.USER,
                    parts = listOf(
                        AiContentPart(
                            AiPayloadReference(
                                mimeType = OnDeviceAiMimeType.PLAIN,
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
                responseMimeType = OnDeviceAiMimeType.JSON,
                responseSchema = AiPayloadReference(
                    mimeType = OnDeviceAiMimeType.JSON,
                    declaredLengthBytes = schemaBytes.size.toLong(),
                    inlineBytes = schemaBytes,
                    charset = "utf-8",
                ),
            ),
        )
    }
}
