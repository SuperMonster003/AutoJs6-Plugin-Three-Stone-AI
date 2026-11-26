package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream

/**
 * Bounded, user-facing explanation taken from a provider error response, so that a failed turn can say
 * what the provider said (an exhausted quota, a rejected parameter, a blocked prompt) instead of only a
 * fixed category. The text is whitespace-collapsed, stripped of control characters, capped at
 * [MAXIMUM_LENGTH] characters and has key-shaped tokens masked; the execution layer additionally removes
 * the request's secrets with [withoutSecrets]. It is never part of [OnlineAiFailureException.message].
 */
internal object OnlineAiProviderDetail {
    const val MAXIMUM_LENGTH = 300
    const val REDACTED = "[redacted]"

    /** Shortest leading part of a secret that is masked when truncation cut the secret off (D181). */
    const val PARTIAL_SECRET_MINIMUM = 4
    private const val ELLIPSIS = "..."
    private val ELLIPSIS_BYTES = ELLIPSIS.toByteArray(Charsets.US_ASCII)
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
        return if (masked.length <= MAXIMUM_LENGTH) masked else masked.take(MAXIMUM_LENGTH - ELLIPSIS.length) + ELLIPSIS
    }

    /** [withoutSecrets] for the request credential alone. */
    fun withoutSecret(detail: String, secret: ByteArray): String = withoutSecrets(detail, listOf(secret))

    /**
     * Removes the request's secrets (the API Key and the values of secret custom headers, all printable
     * ASCII) from [detail] without ever holding a secret as a String (D181). Besides every exact
     * occurrence it masks the forms the detail may carry them in: runs of spaces collapsed by [sanitize],
     * JSON string escaping, and a leading part of at least [PARTIAL_SECRET_MINIMUM] bytes cut off by
     * truncation right before the trailing ellipsis. The result is truncated again to [MAXIMUM_LENGTH];
     * every temporary byte copy is zeroed before returning.
     */
    fun withoutSecrets(detail: String, secrets: List<ByteArray>): String {
        val patterns = ArrayList<ByteArray>()
        val owned = ArrayList<ByteArray>()
        val bytes = detail.toByteArray(Charsets.UTF_8)
        var output: ByteArray? = null
        try {
            secrets.forEach { secret ->
                if (secret.isEmpty()) return@forEach
                patterns += secret
                listOfNotNull(collapsedSpaces(secret), jsonEscaped(secret)).forEach { variant ->
                    owned += variant
                    patterns += variant
                }
            }
            if (patterns.isEmpty()) return detail
            // Longer patterns first, so that a secret containing another one is masked whole.
            patterns.sortByDescending(ByteArray::size)
            val masked = ByteArrayOutputStream(bytes.size + REDACTED.length)
            val replacement = REDACTED.toByteArray(Charsets.US_ASCII)
            var index = 0
            var replaced = false
            while (index < bytes.size) {
                val pattern = patterns.firstOrNull { pattern ->
                    bytes.size - index >= pattern.size && bytes.matchesAt(index, pattern)
                }
                if (pattern != null) {
                    masked.write(replacement, 0, replacement.size)
                    index += pattern.size
                    replaced = true
                } else {
                    masked.write(bytes[index].toInt())
                    index += 1
                }
            }
            output = masked.toByteArray()
            val truncated = detail.length == MAXIMUM_LENGTH && detail.endsWith(ELLIPSIS)
            val cut = if (truncated) cutSecretPrefix(output, patterns) else -1
            if (!replaced && cut < 0) return detail
            val text = if (cut < 0) {
                String(output, Charsets.UTF_8)
            } else {
                String(output, 0, cut, Charsets.UTF_8) + REDACTED + ELLIPSIS
            }
            return if (text.length <= MAXIMUM_LENGTH) text else text.take(MAXIMUM_LENGTH - ELLIPSIS.length) + ELLIPSIS
        } finally {
            bytes.fill(0)
            output?.fill(0)
            owned.forEach { variant -> variant.fill(0) }
        }
    }

    /**
     * Start of a leading part of some secret that ends right before the trailing ellipsis of [masked],
     * or -1. Only parts of at least [PARTIAL_SECRET_MINIMUM] bytes count, so that ordinary words before
     * the ellipsis survive.
     */
    private fun cutSecretPrefix(masked: ByteArray, patterns: List<ByteArray>): Int {
        val end = masked.size - ELLIPSIS.length
        if (end <= 0 || !masked.matchesAt(end, ELLIPSIS_BYTES)) return -1
        var best = -1
        patterns.forEach { pattern ->
            for (length in minOf(pattern.size - 1, end) downTo PARTIAL_SECRET_MINIMUM) {
                val start = end - length
                if (masked.matchesAt(start, pattern, length)) {
                    if (best < 0 || start < best) best = start
                    break
                }
            }
        }
        return best
    }

    /** The secret with every run of spaces collapsed to one, as [sanitize] does; null when unchanged. */
    private fun collapsedSpaces(secret: ByteArray): ByteArray? {
        val space = ' '.code.toByte()
        var changed = false
        val collapsed = ByteArrayOutputStream(secret.size)
        for (index in secret.indices) {
            if (secret[index] == space && index > 0 && secret[index - 1] == space) {
                changed = true
            } else {
                collapsed.write(secret[index].toInt())
            }
        }
        return if (changed) collapsed.toByteArray() else null
    }

    /** The secret as it appears inside a JSON string literal; null when no character needs escaping. */
    private fun jsonEscaped(secret: ByteArray): ByteArray? {
        val quote = '"'.code.toByte()
        val backslash = '\\'.code.toByte()
        if (secret.none { byte -> byte == quote || byte == backslash }) return null
        val escaped = ByteArrayOutputStream(secret.size * 2)
        secret.forEach { byte ->
            if (byte == quote || byte == backslash) escaped.write(backslash.toInt())
            escaped.write(byte.toInt())
        }
        return escaped.toByteArray()
    }

    private fun ByteArray.matchesAt(offset: Int, pattern: ByteArray, length: Int = pattern.size): Boolean {
        for (position in 0 until length) {
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
