package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.ResponseBody
import java.math.BigDecimal

/** Parses only the text and exact usage surface declared by the unified backend. */
internal object OpenAiCompatibleResponseParser {
    fun parseEvent(event: OnlineAiSseEvent, tools: OnlineAiToolCollector? = null): OnlineAiStreamChunk {
        if (event.isDone) return OnlineAiStreamChunk(done = true)
        val root = OnlineAiResponseSupport.parseObject(event.data)
        if (event.event == "error" || root.isTypedError() || root.hasProviderError()) providerError()
        tools?.event(root)

        val choice = root.firstChoice()
        val delta = choice?.get("delta")?.let { element ->
            if (!element.isJsonObject) invalidResponse()
            element.asJsonObject
        }
        val text = buildString {
            delta?.get("content").appendText(this)
            delta?.get("refusal").appendText(this)
            if (isEmpty()) choice?.get("text").appendText(this)
        }
        return OnlineAiStreamChunk(
            text = text,
            usage = root.usageOrNull(),
            contentSeen = choice != null,
        )
    }

    fun parseJson(body: ResponseBody, tools: OnlineAiToolCollector? = null): OnlineAiJsonResponse {
        val root = OnlineAiResponseSupport.readObject(body)
        if (root.isTypedError() || root.hasProviderError()) providerError()
        tools?.json(root)
        val choice = root.firstChoice() ?: invalidResponse()
        val message = choice.get("message")?.let { element ->
            if (!element.isJsonObject) invalidResponse()
            element.asJsonObject
        }
        val text = buildString {
            message?.get("content").appendText(this)
            message?.get("refusal").appendText(this)
            if (isEmpty()) choice.get("text").appendText(this)
        }
        return OnlineAiJsonResponse(
            text = text,
            usage = root.usageOrNull(),
        )
    }

    private fun JsonObject.firstChoice(): JsonObject? {
        val choices = get("choices") ?: return null
        if (!choices.isJsonArray) invalidResponse()
        val first = choices.asJsonArray.firstOrNull() ?: return null
        if (!first.isJsonObject) invalidResponse()
        return first.asJsonObject
    }

    private fun JsonObject.hasProviderError(): Boolean {
        val error = get("error") ?: return false
        return !error.isJsonNull
    }

    private fun JsonObject.isTypedError(): Boolean {
        val type = get("type") ?: return false
        return type.isJsonPrimitive &&
            type.asJsonPrimitive.isString &&
            type.asString == "error"
    }

    private fun JsonObject.usageOrNull(): OnlineAiUsageUpdate? {
        val element = get("usage") ?: return null
        if (element.isJsonNull) return null
        if (!element.isJsonObject) invalidResponse()
        val usage = element.asJsonObject
        val input = usage.countOrNull("prompt_tokens") ?: usage.countOrNull("input_tokens")
        val output = usage.countOrNull("completion_tokens") ?: usage.countOrNull("output_tokens")
        val total = usage.countOrNull("total_tokens")
        if (input == null || output == null) return null
        val expectedTotal = try {
            Math.addExact(input, output)
        } catch (_: ArithmeticException) {
            invalidResponse()
        }
        if (total != null && total != expectedTotal) invalidResponse()
        val inputDetails = usage.optionalObject("prompt_tokens_details")
            ?: usage.optionalObject("input_tokens_details")
        val cached = inputDetails?.countOrNull("cached_tokens")
        val cacheWrite = inputDetails?.countOrNull("cache_write_tokens")
        return OnlineAiUsageUpdate(
            inputTokens = input,
            outputTokens = output,
            totalTokens = total,
            cachedInputTokens = cached,
            cacheWriteInputTokens = cacheWrite,
            cacheEligibleInputTokens = input.takeIf { cached != null || cacheWrite != null },
        )
    }

    private fun JsonObject.optionalObject(name: String): JsonObject? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonObject) invalidResponse()
        return value.asJsonObject
    }

    private fun JsonObject.countOrNull(name: String): Long? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) invalidResponse()
        val count = try {
            BigDecimal(value.asString).longValueExact()
        } catch (_: RuntimeException) {
            invalidResponse()
        }
        if (count < 0L) invalidResponse()
        return count
    }

    private fun JsonElement?.appendText(target: StringBuilder) {
        val content = this ?: return
        when {
            content.isJsonNull -> Unit
            content.isJsonPrimitive && content.asJsonPrimitive.isString ->
                target.append(content.asString)
            content.isJsonArray -> content.asJsonArray.forEach { partElement ->
                if (!partElement.isJsonObject) invalidResponse()
                val part = partElement.asJsonObject
                val text = part.get("text") ?: part.get("refusal")
                if (text != null && !text.isJsonNull) {
                    if (!text.isJsonPrimitive || !text.asJsonPrimitive.isString) invalidResponse()
                    target.append(text.asString)
                }
            }
            else -> invalidResponse()
        }
    }

    private fun invalidResponse(): Nothing = throw OnlineAiFailureException(
        OnlineAiFailureReason.INVALID_RESPONSE,
    )

    private fun providerError(): Nothing = throw OnlineAiFailureException(
        OnlineAiFailureReason.PROVIDER_ERROR,
    )
}
