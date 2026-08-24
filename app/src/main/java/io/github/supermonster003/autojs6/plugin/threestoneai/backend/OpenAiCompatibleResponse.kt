package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Strictness
import okhttp3.ResponseBody
import okio.Buffer
import java.math.BigDecimal
import kotlin.math.min

internal data class OpenAiCompatibleUsage(
    val inputTokens: Long,
    val outputTokens: Long,
) {
    init {
        require(inputTokens >= 0L)
        require(outputTokens >= 0L)
        require(inputTokens <= Long.MAX_VALUE - outputTokens)
    }
}

internal data class OpenAiCompatibleStreamChunk(
    val text: String = "",
    val usage: OpenAiCompatibleUsage? = null,
    val choiceSeen: Boolean = false,
    val done: Boolean = false,
)

internal data class OpenAiCompatibleJsonResponse(
    val text: String,
    val usage: OpenAiCompatibleUsage?,
)

/** Parses only the text and exact usage surface declared by the unified backend. */
internal object OpenAiCompatibleResponseParser {
    private val STRICT_GSON: Gson = GsonBuilder()
        .setStrictness(Strictness.STRICT)
        .create()

    fun parseEvent(event: OpenAiCompatibleSseEvent): OpenAiCompatibleStreamChunk {
        if (event.isDone) return OpenAiCompatibleStreamChunk(done = true)
        val root = parseObject(event.data)
        if (event.event == "error" || root.isTypedError() || root.hasProviderError()) providerError()

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
        return OpenAiCompatibleStreamChunk(
            text = text,
            usage = root.usageOrNull(),
            choiceSeen = choice != null,
        )
    }

    fun parseJson(body: ResponseBody): OpenAiCompatibleJsonResponse {
        val root = readObject(body)
        if (root.hasProviderError()) providerError()
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
        return OpenAiCompatibleJsonResponse(
            text = text,
            usage = root.usageOrNull(),
        )
    }

    fun discardErrorBody(body: ResponseBody?) {
        body ?: return
        val source = body.source()
        val scratch = Buffer()
        var remaining = OpenAiCompatibleTransportLimits.MAXIMUM_ERROR_RESPONSE_BYTES + 1L
        while (remaining > 0L) {
            val read = source.read(scratch, min(remaining, READ_CHUNK_BYTES))
            if (read == -1L) break
            remaining -= read
            scratch.clear()
        }
        scratch.clear()
    }

    private fun readObject(body: ResponseBody): JsonObject {
        val source = body.source()
        val buffer = Buffer()
        var remaining = OpenAiCompatibleTransportLimits.MAXIMUM_JSON_RESPONSE_BYTES + 1L
        while (remaining > 0L) {
            val read = source.read(buffer, min(remaining, READ_CHUNK_BYTES))
            if (read == -1L) break
            remaining -= read
        }
        if (buffer.size > OpenAiCompatibleTransportLimits.MAXIMUM_JSON_RESPONSE_BYTES) {
            buffer.clear()
            throw OpenAiCompatibleFailureException(
                OpenAiCompatibleFailureReason.RESPONSE_TOO_LARGE,
            )
        }
        val bytes = buffer.readByteArray()
        return try {
            parseObject(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
        } finally {
            bytes.fill(0)
        }
    }

    private fun parseObject(value: String): JsonObject {
        val parsed = try {
            STRICT_GSON.fromJson(value, JsonElement::class.java)
        } catch (_: RuntimeException) {
            invalidResponse()
        }
        if (parsed == null || !parsed.isJsonObject) invalidResponse()
        return parsed.asJsonObject
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

    private fun JsonObject.usageOrNull(): OpenAiCompatibleUsage? {
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
        return OpenAiCompatibleUsage(input, output)
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

    private fun invalidResponse(): Nothing = throw OpenAiCompatibleFailureException(
        OpenAiCompatibleFailureReason.INVALID_RESPONSE,
    )

    private fun providerError(): Nothing = throw OpenAiCompatibleFailureException(
        OpenAiCompatibleFailureReason.PROVIDER_ERROR,
    )

    private const val READ_CHUNK_BYTES = 8192L
}
