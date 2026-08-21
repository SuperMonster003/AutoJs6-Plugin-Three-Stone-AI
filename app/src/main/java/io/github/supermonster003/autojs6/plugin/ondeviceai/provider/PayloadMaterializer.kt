package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ondeviceai.api.OnDeviceAiPayloadPolicy
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest

internal data class MaterializedMessage(
    val role: Int,
    val textParts: List<String>,
)

internal data class MaterializedRequest(
    val messages: List<MaterializedMessage>,
    val inputBytes: Long,
    val responseSchemaJson: String? = null,
)

internal object PayloadMaterializer {
    fun materializeRequest(
        request: OnDeviceAiRequest,
        readDescriptor: (index: Int, declaredLengthBytes: Long) -> ByteArray,
    ): MaterializedRequest {
        var inputBytes = 0L
        val messages = request.messages.map { message ->
            require(message.name == null) { "Named AI messages are not supported" }
            MaterializedMessage(
                role = message.role,
                textParts = message.parts.map { part ->
                    val bytes = materialize(part.payload, readDescriptor)
                    require(inputBytes <= Long.MAX_VALUE - bytes.size) { "Materialized byte count overflow" }
                    inputBytes += bytes.size
                    AiValidation.decodeUtf8(bytes)
                },
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
        OnDeviceAiPayloadPolicy.validateMaterialized(payload, bytes)
        return bytes
    }
}
