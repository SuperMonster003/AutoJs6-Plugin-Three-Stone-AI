package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream

/**
 * Bounded, user-facing explanation taken from a provider error response, so that a failed turn can say
 * what the provider said (an exhausted quota, a rejected parameter, a blocked prompt) instead of only a
 * fixed category. The text is whitespace-collapsed, stripped of control characters, capped at
 * [MAXIMUM_LENGTH] characters and has key-shaped tokens masked; the execution layer additionally removes
 * the request credential with [withoutSecret]. It is never part of [OnlineAiFailureException.message].
 */
internal object OnlineAiProviderDetail {
    const val MAXIMUM_LENGTH = 300
    const val REDACTED = "[redacted]"
    private val CONTROL = Regex("\\p{Cntrl}")
    private val WHITESPACE = Regex("\\s+")
    private val KEY_LIKE = Regex("(?i)\\b(?:sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|Bearer [A-Za-z0-9._~+/=-]{8,})")

    /**
     * Reads provider error envelopes: Anthropic `{"type":"error","error":{"type","message"}}`, OpenAI
     * `{"error":{"message","type","code"}}`, Gemini `{"error":{"code","message","status"}}`, a string
     * `error`, or a bare `message`. Returns "kind: message" or null when nothing readable is present.
     */
    fun fromJson(root: JsonObject): String? {
        val error = root.get("error")
        val kind: String?
        val message: String?
        when {
            error != null && error.isJsonObject -> {
                val envelope = error.asJsonObject
                message = envelope.plainString("message")
                kind = envelope.plainString("type") ?: envelope.plainString("status") ?: envelope.plainString("code")
            }
            error != null && error.isJsonPrimitive && error.asJsonPrimitive.isString -> {
                message = error.asString
                kind = null
            }
            else -> {
                message = root.plainString("message")
                kind = root.plainString("type")?.takeIf { it != "error" }
            }
        }
        return sanitize(listOfNotNull(kind, message).joinToString(": "))
    }

    /** Plain-text bodies are kept; HTML pages (proxies, gateways) carry nothing worth showing. */
    fun fromText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("<")) return null
        return sanitize(trimmed)
    }

    fun sanitize(text: String): String? {
        val collapsed = text.replace(CONTROL, " ").replace(WHITESPACE, " ").trim()
        if (collapsed.isEmpty()) return null
        val masked = collapsed.replace(KEY_LIKE, REDACTED)
        return if (masked.length <= MAXIMUM_LENGTH) masked else masked.take(MAXIMUM_LENGTH - 3) + "..."
    }

    /**
     * Removes every occurrence of [secret] (the request credential: printable ASCII) from [detail] without
     * ever holding the secret as a String; the UTF-8 copy of the detail is zeroed before returning.
     */
    fun withoutSecret(detail: String, secret: ByteArray): String {
        if (secret.isEmpty()) return detail
        val bytes = detail.toByteArray(Charsets.UTF_8)
        try {
            if (bytes.size < secret.size) return detail
            val output = ByteArrayOutputStream(bytes.size)
            val replacement = REDACTED.toByteArray(Charsets.US_ASCII)
            var index = 0
            var replaced = false
            while (index < bytes.size) {
                if (bytes.size - index >= secret.size && bytes.matchesAt(index, secret)) {
                    output.write(replacement, 0, replacement.size)
                    index += secret.size
                    replaced = true
                } else {
                    output.write(bytes[index].toInt())
                    index += 1
                }
            }
            return if (replaced) output.toString(Charsets.UTF_8.name()) else detail
        } finally {
            bytes.fill(0)
        }
    }

    private fun ByteArray.matchesAt(offset: Int, pattern: ByteArray): Boolean {
        for (position in pattern.indices) {
            if (this[offset + position] != pattern[position]) return false
        }
        return true
    }

    private fun JsonObject.plainString(name: String): String? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        return if (primitive.isString || primitive.isNumber) primitive.asString else null
    }
}
