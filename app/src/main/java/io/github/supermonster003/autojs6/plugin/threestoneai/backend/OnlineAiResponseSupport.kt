package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import okhttp3.ResponseBody
import okio.Buffer
import java.math.BigDecimal
import kotlin.math.min

/** Strict, bounded primitives shared by provider-native response adapters. */
internal object OnlineAiResponseSupport {
    private val STRICT_GSON: Gson = GsonBuilder()
        .setStrictness(Strictness.STRICT)
        .create()

    fun parseObject(value: String): JsonObject {
        val parsed = try {
            STRICT_GSON.fromJson(value, JsonElement::class.java)
        } catch (_: RuntimeException) {
            invalidResponse()
        }
        if (parsed == null || !parsed.isJsonObject) invalidResponse()
        return parsed.asJsonObject
    }

    fun parseArray(value: String): JsonArray {
        val parsed = try { STRICT_GSON.fromJson(value, JsonElement::class.java) }
        catch (_: RuntimeException) { invalidResponse() }
        if (parsed == null || !parsed.isJsonArray) invalidResponse()
        return parsed.asJsonArray
    }

    fun readObject(body: ResponseBody): JsonObject {
        val source = body.source()
        val buffer = Buffer()
        var remaining = OnlineAiTransportLimits.MAXIMUM_JSON_RESPONSE_BYTES + 1L
        while (remaining > 0L) {
            val read = source.read(buffer, min(remaining, READ_CHUNK_BYTES))
            if (read == -1L) break
            remaining -= read
        }
        if (buffer.size > OnlineAiTransportLimits.MAXIMUM_JSON_RESPONSE_BYTES) {
            buffer.clear()
            throw OnlineAiFailureException(
                OnlineAiFailureReason.RESPONSE_TOO_LARGE,
            )
        }
        val bytes = buffer.readByteArray()
        return try {
            parseObject(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
        } finally {
            bytes.fill(0)
        }
    }

    fun discardErrorBody(body: ResponseBody?) {
        body ?: return
        val source = body.source()
        val scratch = Buffer()
        var remaining = OnlineAiTransportLimits.MAXIMUM_ERROR_RESPONSE_BYTES + 1L
        while (remaining > 0L) {
            val read = source.read(scratch, min(remaining, READ_CHUNK_BYTES))
            if (read == -1L) break
            remaining -= read
            scratch.clear()
        }
        scratch.clear()
    }

    fun objectOrNull(root: JsonObject, name: String): JsonObject? {
        val value = root.get(name) ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonObject) invalidResponse()
        return value.asJsonObject
    }

    fun arrayOrNull(root: JsonObject, name: String): com.google.gson.JsonArray? {
        val value = root.get(name) ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonArray) invalidResponse()
        return value.asJsonArray
    }

    fun stringOrNull(root: JsonObject, name: String): String? {
        val value = root.get(name) ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) invalidResponse()
        return value.asString
    }

    fun booleanOrNull(root: JsonObject, name: String): Boolean? {
        val value = root.get(name) ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) invalidResponse()
        return value.asBoolean
    }

    fun countOrNull(root: JsonObject, name: String): Long? {
        val value = root.get(name) ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) invalidResponse()
        val count = try {
            BigDecimal(value.asString).longValueExact()
        } catch (_: RuntimeException) {
            invalidResponse()
        }
        if (count < 0L) invalidResponse()
        return count
    }

    fun hasProviderError(root: JsonObject): Boolean {
        val error = root.get("error") ?: return false
        return !error.isJsonNull
    }

    fun invalidResponse(): Nothing = throw OnlineAiFailureException(
        OnlineAiFailureReason.INVALID_RESPONSE,
    )

    fun providerError(): Nothing = throw OnlineAiFailureException(
        OnlineAiFailureReason.PROVIDER_ERROR,
    )

    private const val READ_CHUNK_BYTES = 8192L
}
