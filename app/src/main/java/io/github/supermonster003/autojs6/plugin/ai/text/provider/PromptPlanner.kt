package io.github.supermonster003.autojs6.plugin.ai.text.provider

import io.github.supermonster003.autojs6.plugin.ai.text.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.ai.text.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.ai.text.backend.GenerationRole
import org.autojs.plugin.ai.text.api.AiMessageRole
import org.autojs.plugin.ai.text.api.AiTextRequest

internal object PromptPlanner {
    fun plan(request: AiTextRequest, materialized: MaterializedRequest): GenerationRequest {
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
            )
        }
        require(messages.last().role == GenerationRole.USER) { "The final AI message must be a user message" }
        val maximumTokens = request.options.maximumOutputTokens?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
        return GenerationRequest(
            history = messages.dropLast(1),
            prompt = messages.last(),
            maximumOutputTokens = maximumTokens,
        )
    }
}
