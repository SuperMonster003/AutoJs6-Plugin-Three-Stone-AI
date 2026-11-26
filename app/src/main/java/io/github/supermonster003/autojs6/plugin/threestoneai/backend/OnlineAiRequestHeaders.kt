package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiBuiltInHeaders
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderSecrets
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderValueKind
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import java.security.MessageDigest
import java.util.UUID

/**
 * Custom request header values resolved for one request (D160, D165): fixed values from the
 * profile, secret values captured together with the API Key, and the per-conversation session ID
 * (T73). Secret headers without a stored value are left out (D176). [toString] never prints values.
 */
internal class OnlineAiRequestHeaders private constructor(
    val entries: List<Entry>,
) {
    class Entry internal constructor(val name: String, val value: String) {
        override fun toString(): String = "Entry(name=$name, value=[REDACTED])"
    }

    override fun toString(): String =
        "OnlineAiRequestHeaders(names=${entries.map(Entry::name)}, values=[REDACTED])"

    companion object {
        val NONE = OnlineAiRequestHeaders(emptyList())

        /**
         * Resolves the headers of [profile] in their configured order. [secrets] must come from the
         * same credential snapshot as the API Key of the request; [conversationSessionId] is the
         * value of every "per conversation" header.
         */
        fun resolve(
            profile: OnlineAiProfile,
            secrets: OnlineAiHeaderSecrets,
            conversationSessionId: String,
        ): OnlineAiRequestHeaders {
            if (profile.headers.isEmpty()) return NONE
            val entries = profile.headers.mapNotNull { header ->
                val value = when (header.kind) {
                    OnlineAiHeaderValueKind.FIXED -> checkNotNull(header.value) { "Fixed header value is missing" }
                    OnlineAiHeaderValueKind.SECRET ->
                        secrets.valueOf(header.name)?.let { bytes -> String(bytes, Charsets.US_ASCII) }
                    OnlineAiHeaderValueKind.CONVERSATION -> conversationSessionId
                } ?: return@mapNotNull null
                entry(header.name, value)
            }
            return if (entries.isEmpty()) NONE else OnlineAiRequestHeaders(entries)
        }

        /** Headers given directly, for protocol tests; checked like resolved ones. */
        internal fun of(vararg entries: Pair<String, String>): OnlineAiRequestHeaders =
            if (entries.isEmpty()) NONE else OnlineAiRequestHeaders(entries.map { (name, value) -> entry(name, value) })

        /** Checks again so that a value never reaches OkHttp, whose errors quote header values. */
        private fun entry(name: String, value: String): Entry {
            require(OnlineAiHeaderPolicy.isValidName(name) && OnlineAiBuiltInHeaders.ownerOf(name) == null) {
                "Online AI custom header name is not allowed"
            }
            require(OnlineAiHeaderPolicy.isValidValue(value)) { "Online AI custom header value is invalid" }
            return Entry(name, value)
        }
    }
}

/**
 * Values of "per conversation" headers (D163, D164, T73): `ses_` plus the first 32 lower-case
 * hexadecimal digits of SHA-256("3-stone/x-session/v1" NUL profileId NUL conversationKey). The same
 * service and conversation always give the same ID; another service or conversation gives another.
 */
internal object OnlineAiConversationSessionIds {
    private const val DOMAIN = "3-stone/x-session/v1"
    private const val PREFIX = "ses_"
    private const val HEX_DIGITS = 32
    private const val SEPARATOR: Byte = 0
    private val HEX = "0123456789abcdef".toCharArray()

    fun derive(profileId: String, conversationKey: String): String {
        require(profileId.isNotEmpty()) { "Online AI profile ID is empty" }
        require(conversationKey.isNotEmpty()) { "Online AI conversation key is empty" }
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(DOMAIN.toByteArray(Charsets.UTF_8))
            update(SEPARATOR)
            update(profileId.toByteArray(Charsets.UTF_8))
            update(SEPARATOR)
            update(conversationKey.toByteArray(Charsets.UTF_8))
            digest()
        }
        val result = StringBuilder(PREFIX.length + HEX_DIGITS).append(PREFIX)
        for (index in 0 until HEX_DIGITS / 2) {
            val byte = digest[index].toInt() and 0xff
            result.append(HEX[byte ushr 4]).append(HEX[byte and 0x0f])
        }
        return result.toString()
    }

    /** A fresh conversation key for requests that belong to no conversation (connection tests, one-off requests). */
    fun randomKey(): String = UUID.randomUUID().toString()
}

/**
 * Applies the plugin's headers and then the custom ones to one request (D161, D179). Every header the
 * plugin sets is set once; a second plugin or custom header with the same name fails instead of
 * replacing it silently. A custom User-Agent replaces the default; a custom anthropic-beta is merged
 * with the value the plugin computed for the request, or sent unchanged when there is none (D178).
 */
internal class OnlineAiOutgoingHeaders(private val builder: okhttp3.Request.Builder) {
    private val values = HashMap<String, String>()

    fun plugin(name: String, value: String) {
        check(values.put(OnlineAiHeaderPolicy.key(name), value) == null) {
            "Online AI request header $name is set twice"
        }
        builder.header(name, value)
    }

    fun custom(headers: OnlineAiRequestHeaders) {
        headers.entries.forEach { entry ->
            val key = OnlineAiHeaderPolicy.key(entry.name)
            val pluginValue = values[key]
            when {
                key == OnlineAiBuiltInHeaders.USER_AGENT -> {
                    values[key] = entry.value
                    builder.header(entry.name, entry.value)
                }
                key == OnlineAiBuiltInHeaders.ANTHROPIC_BETA && pluginValue != null -> {
                    val merged = mergeAnthropicBeta(pluginValue, entry.value)
                    values[key] = merged
                    builder.header(OnlineAiBuiltInHeaders.ANTHROPIC_BETA, merged)
                }
                else -> {
                    require(pluginValue == null && OnlineAiBuiltInHeaders.ownerOf(key) == null) {
                        "Online AI custom header ${entry.name} collides with a header the plugin sends"
                    }
                    values[key] = entry.value
                    builder.addHeader(entry.name, entry.value)
                }
            }
        }
    }

    companion object {
        /**
         * The plugin's beta flags first, then the custom ones; flags are split at commas, trimmed,
         * and empty or repeated flags are dropped (D178).
         */
        fun mergeAnthropicBeta(pluginValue: String, customValue: String): String =
            (pluginValue.split(',') + customValue.split(','))
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .joinToString(",")
    }
}
