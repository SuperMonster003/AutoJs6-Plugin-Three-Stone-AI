package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderPayloadPolicy
import org.autojs.plugin.ai.provider.api.AiToolCall
import org.autojs.plugin.ai.provider.api.AiToolDefinition

internal data class GenerationToolDefinition(val name: String, val description: String, val schema: String) {
    init {
        val payload = toolJsonPayload(schema)
        AiToolDefinition(name, description, payload)
        AiProviderPayloadPolicy.validateMaterialized(payload, requireNotNull(payload.inlineBytes))
        OnlineAiRequestSupport.parseSchema(schema, 0L)
    }

    override fun toString() = "GenerationToolDefinition(name=$name)"
}

internal data class GenerationToolCall(
    val callId: String,
    val name: String,
    val argumentsJson: String,
    /** Gemini can omit IDs on the wire; the public call still always has a unique ID. */
    val wireId: String? = callId,
) {
    fun toProviderCall(): AiToolCall = AiToolCall(
        callId, name,
        toolJsonPayload(argumentsJson),
    ).also { call ->
        AiProviderPayloadPolicy.validateMaterialized(call.arguments, requireNotNull(call.arguments.inlineBytes))
        require(OnlineAiRequestSupport.parseSchema(argumentsJson, 0L).isJsonObject)
    }

    override fun toString() = "GenerationToolCall(name=$name)"
}

private fun toolJsonPayload(json: String): AiPayloadReference = json.toByteArray(Charsets.UTF_8).let { bytes ->
    AiPayloadReference(AiProviderMimeType.JSON, bytes.size.toLong(), inlineBytes = bytes, charset = "utf-8")
}

internal data class GenerationToolResult(val callId: String, val output: String, val isError: Boolean = false) {
    override fun toString() = "GenerationToolResult(isError=$isError)"
}

/** Private, bounded replay data. Signed provider content never becomes tool arguments or logs. */
internal class NativeToolMessage(val protocol: OnlineAiProtocol, val json: String) {
    init {
        require(json.toByteArray(Charsets.UTF_8).size <= OnlineAiTransportLimits.MAXIMUM_CONTEXT_BYTES)
    }

    override fun toString() = "NativeToolMessage(protocol=$protocol)"
}

internal object OnlineAiTools {
    fun requireRequest(turn: GenerationRequest, messageBytes: Long): Long {
        require(turn.tools.size <= AiProviderLimits.MAX_TOOL_DEFINITIONS)
        require(turn.tools.map { it.name }.distinct().size == turn.tools.size)
        require(if (turn.tools.isEmpty()) turn.maximumToolRounds == 0 else turn.maximumToolRounds in 1..16)
        val bytes = turn.tools.fold(messageBytes) { count, tool ->
            Math.addExact(count, (tool.name + tool.description + tool.schema).toByteArray(Charsets.UTF_8).size.toLong())
        }
        require(bytes <= OnlineAiTransportLimits.MAXIMUM_CONTEXT_BYTES) { "Online AI context is too large" }
        return bytes
    }

    fun definitions(tools: List<GenerationToolDefinition>, protocol: OnlineAiProtocol): JsonArray = JsonArray().apply {
        tools.forEach { tool ->
            val declaration = JsonObject().apply {
                addProperty("name", tool.name)
                addProperty("description", tool.description)
                add(if (protocol == OnlineAiProtocol.ANTHROPIC_MESSAGES) "input_schema" else "parameters",
                    OnlineAiRequestSupport.parseSchema(tool.schema, 0L))
            }
            add(if (protocol == OnlineAiProtocol.OPENAI_COMPATIBLE) JsonObject().apply {
                addProperty("type", "function")
                add("function", declaration)
            } else declaration)
        }
    }

    fun resultsMessage(protocol: OnlineAiProtocol, calls: List<GenerationToolCall>, results: List<GenerationToolResult>): GenerationMessage {
        require(results.size == calls.size && results.map { it.callId }.toSet() == calls.map { it.callId }.toSet()) {
            "Tool results must exactly match pending calls"
        }
        val byId = results.associateBy { it.callId }
        val content = JsonArray().apply {
            calls.forEach { call ->
                val result = byId.getValue(call.callId)
                require(result.output.toByteArray(Charsets.UTF_8).size <= AiProviderLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES)
                add(JsonObject().apply {
                    when (protocol) {
                        OnlineAiProtocol.OPENAI_COMPATIBLE -> {
                            addProperty("role", "tool")
                            addProperty("tool_call_id", call.callId)
                            // Chat Completions has no is_error field; retain the failure explicitly.
                            addProperty("content", if (result.isError) JsonObject().apply {
                                addProperty("isError", true)
                                addProperty("output", result.output)
                            }.toString() else result.output)
                        }
                        OnlineAiProtocol.ANTHROPIC_MESSAGES -> {
                            addProperty("type", "tool_result")
                            addProperty("tool_use_id", call.callId)
                            addProperty("content", result.output)
                            addProperty("is_error", result.isError)
                        }
                        OnlineAiProtocol.GEMINI_GENERATE_CONTENT -> add("functionResponse", JsonObject().apply {
                            addProperty("name", call.name)
                            call.wireId?.let { addProperty("id", it) }
                            add("response", JsonObject().apply {
                                addProperty(if (result.isError) "error" else "output", result.output)
                            })
                        })
                    }
                })
            }
        }
        return GenerationMessage(GenerationRole.USER, emptyList(), NativeToolMessage(protocol, content.toString()))
    }
}
