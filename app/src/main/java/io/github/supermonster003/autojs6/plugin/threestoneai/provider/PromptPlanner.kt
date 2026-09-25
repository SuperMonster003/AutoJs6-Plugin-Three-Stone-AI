package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationSamplingOptions
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiProviderSamplingDefaults
import org.autojs.plugin.ai.provider.api.AiProviderRequest

internal object PromptPlanner {
    fun plan(request: AiProviderRequest, materialized: MaterializedRequest): GenerationRequest {
        require(materialized.messages.size == request.messages.size)
        val messages = materialized.messages.map { message ->
            GenerationMessage(
                role = when (message.role) {
                    AiMessageRole.SYSTEM -> GenerationRole.SYSTEM
                    AiMessageRole.USER -> GenerationRole.USER
                    AiMessageRole.ASSISTANT -> GenerationRole.ASSISTANT
                    else -> throw IllegalArgumentException("Tool messages are not supported")
                },
                textParts = message.textParts,
                images = message.images,
            )
        }
        require(messages.last().role == GenerationRole.USER) { "The final AI message must be a user message" }
        val options = request.options
        require((materialized.responseSchemaJson != null) == (options.responseSchema != null)) {
            "The materialized response schema does not match the request"
        }
        val maximumTokens = options.maximumOutputTokens?.toInt()
        val samplingOptions = if (
            options.temperature == null && options.topK == null && options.topP == null
        ) {
            null
        } else {
            GenerationSamplingOptions(
                temperature = options.temperature ?: AiProviderSamplingDefaults.TEMPERATURE,
                topK = options.topK ?: AiProviderSamplingDefaults.TOP_K,
                topP = options.topP ?: AiProviderSamplingDefaults.TOP_P,
            )
        }
        return GenerationRequest(
            history = messages.dropLast(1),
            prompt = messages.last(),
            maximumOutputTokens = maximumTokens,
            samplingOptions = samplingOptions,
            reportUsage = options.reportUsage,
            tools = materialized.tools,
            maximumToolRounds = options.maximumToolRounds,
            responseJsonSchema = if (options.structuredJson) {
                materialized.responseSchemaJson ?: DEFAULT_RESPONSE_SCHEMA_JSON
            } else {
                null
            },
        )
    }

    private const val DEFAULT_RESPONSE_SCHEMA_JSON = "{\"type\":\"object\"}"
}
