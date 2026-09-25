package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiProviderPayloadPolicy
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationToolDefinition

internal data class MaterializedMessage(
    val role: Int,
    val textParts: List<String>,
    val images: List<io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationImage> = emptyList(),
)

internal data class MaterializedRequest(
    val messages: List<MaterializedMessage>,
    val inputBytes: Long,
    val responseSchemaJson: String? = null,
    val tools: List<GenerationToolDefinition> = emptyList(),
)

internal object PayloadMaterializer {
    fun materializeRequest(
        request: AiProviderRequest,
        readDescriptor: (index: Int, declaredLengthBytes: Long) -> ByteArray,
        readImage: (org.autojs.plugin.ai.provider.api.AiContentPart, (Int, Long) -> ByteArray) -> io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationImage = ProviderImageDecoder::materialize,
    ): MaterializedRequest {
        var inputBytes = 0L
        val messages = request.messages.map { message ->
            require(message.name == null) { "Named AI messages are not supported" }
            MaterializedMessage(
                role = message.role,
                textParts = message.parts.filterNot { it.isImage }.map { part ->
                    val bytes = materialize(part.payload, readDescriptor)
                    require(inputBytes <= Long.MAX_VALUE - bytes.size) { "Materialized byte count overflow" }
                    inputBytes += bytes.size
                    AiValidation.decodeUtf8(bytes)
                },
                images = message.parts.filter { it.isImage }.map { readImage(it, readDescriptor) },
            )
        }
        val responseSchemaJson = request.options.responseSchema?.let { schema ->
            AiValidation.decodeUtf8(materialize(schema, readDescriptor)).also { value ->
                require(value.trimStart().startsWith('{')) {
                    "The response schema must be a JSON object"
                }
            }
        }
        return MaterializedRequest(
            messages = messages,
            inputBytes = inputBytes,
            responseSchemaJson = responseSchemaJson,
            tools = request.tools.map { tool ->
                GenerationToolDefinition(tool.name, tool.description,
                    AiValidation.decodeUtf8(materialize(tool.inputSchema, readDescriptor)))
            },
        )
    }

    private fun materialize(
        payload: AiPayloadReference,
        readDescriptor: (index: Int, declaredLengthBytes: Long) -> ByteArray,
    ): ByteArray {
        val bytes = payload.inlineBytes ?: readDescriptor(
            requireNotNull(payload.descriptorIndex),
            payload.declaredLengthBytes,
        )
        AiProviderPayloadPolicy.validateMaterialized(payload, bytes)
        return bytes
    }
}
