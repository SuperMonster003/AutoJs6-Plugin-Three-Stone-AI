package io.github.supermonster003.autojs6.plugin.threestoneai.voice

import io.github.supermonster003.autojs6.plugin.threestoneai.ChatConversationPolicy
import java.util.Locale

/** A voice command as the chat receives it; the text is untrusted input (Macaw D50). */
internal data class VoiceCommand(
    /** Cleaned text, or null when the command only opens the chat. */
    val text: String?,
    /** A valid BCP 47 tag of the recognized language, or null. */
    val languageTag: String?,
    /** `SystemClock.elapsedRealtime()` when the entry received it. */
    val receivedAtMillis: Long,
)

/** What the chat does with a voice command (roadmap D37, D38). */
internal enum class VoiceCommandDisposition {
    /** No text: only bring the chat to the front. */
    OPEN,

    /** New conversation, text in the composer, nothing sent: no target is ready. */
    PREFILL_NO_TARGET,

    /** New conversation, text in the composer, nothing sent: within 10 s of the last immediate send. */
    PREFILL_RATE_LIMITED,

    /** New conversation, sent at once, the reply read aloud (D36). */
    SEND,
}

/**
 * Rules of the `org.autojs.plugin.action.VOICE_COMMAND` entry (roadmap D35-D38, Macaw D50). The entry
 * needs no permission, so the text is cleaned and bounded and immediate sends are rate limited.
 */
internal object VoiceCommandPolicy {
    const val ACTION = "org.autojs.plugin.action.VOICE_COMMAND"
    const val EXTRA_VOICE_LANGUAGE = "org.autojs.plugin.extra.VOICE_LANGUAGE"
    const val RATE_LIMIT_MILLIS = 10_000L
    private const val MAXIMUM_LANGUAGE_TAG_LENGTH = 35
    private val LANGUAGE_TAG = Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")

    /**
     * Control characters and lone surrogates become spaces, line breaks stay (CR LF becomes LF), the
     * ends are trimmed and the text is cut to the composer limit at a code point boundary. Null when
     * nothing remains.
     */
    fun cleanText(raw: CharSequence?, maximumLength: Int = ChatConversationPolicy.MAXIMUM_INPUT_CHARACTERS): String? {
        val text = raw?.toString() ?: return null
        val out = StringBuilder(minOf(text.length, maximumLength + 1))
        var index = 0
        while (index < text.length) {
            val character = text[index++]
            when {
                character.isHighSurrogate() && index < text.length && text[index].isLowSurrogate() -> {
                    out.append(character).append(text[index++])
                }
                character.isSurrogate() -> out.append(' ')
                character == '\n' -> out.append('\n')
                character == '\r' -> if (index < text.length && text[index] == '\n') Unit else out.append('\n')
                Character.isISOControl(character) || character == LINE_SEPARATOR || character == PARAGRAPH_SEPARATOR ->
                    out.append(' ')
                else -> out.append(character)
            }
        }
        val trimmed = out.toString().trim()
        val bounded = if (trimmed.length <= maximumLength) {
            trimmed
        } else {
            val end = if (Character.isHighSurrogate(trimmed[maximumLength - 1])) maximumLength - 1 else maximumLength
            trimmed.substring(0, end).trimEnd()
        }
        return bounded.takeIf(String::isNotEmpty)
    }

    /** The normalized BCP 47 tag, or null for anything invalid or undetermined. */
    fun languageTag(raw: String?): String? {
        val tag = raw?.trim()?.takeIf { it.length <= MAXIMUM_LANGUAGE_TAG_LENGTH && LANGUAGE_TAG.matches(it) }
            ?: return null
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isEmpty() || locale.language == "und") return null
        return locale.toLanguageTag()
    }

    /**
     * Immediate sends are at least [RATE_LIMIT_MILLIS] apart. A last send "in the future" means the
     * device restarted since then (elapsed time starts again at boot), so it no longer counts.
     */
    fun sendAllowed(receivedAtMillis: Long, lastImmediateSendAtMillis: Long?): Boolean {
        val last = lastImmediateSendAtMillis ?: return true
        if (last > receivedAtMillis) return true
        return receivedAtMillis - last >= RATE_LIMIT_MILLIS
    }

    fun disposition(command: VoiceCommand, targetReady: Boolean, lastImmediateSendAtMillis: Long?): VoiceCommandDisposition =
        when {
            command.text == null -> VoiceCommandDisposition.OPEN
            !targetReady -> VoiceCommandDisposition.PREFILL_NO_TARGET
            !sendAllowed(command.receivedAtMillis, lastImmediateSendAtMillis) -> VoiceCommandDisposition.PREFILL_RATE_LIMITED
            else -> VoiceCommandDisposition.SEND
        }

    private val LINE_SEPARATOR = Char(0x2028)
    private val PARAGRAPH_SEPARATOR = Char(0x2029)
}
