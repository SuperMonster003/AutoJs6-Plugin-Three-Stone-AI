package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import java.util.Locale

/** How the value of one custom request header is produced (D160). */
internal enum class OnlineAiHeaderValueKind(val wireId: String) {
    /** Plain text stored with the profile and included in exports. */
    FIXED("fixed"),

    /** Encrypted like the API Key (D162); never exported, redacted from diagnostics. */
    SECRET("secret"),

    /** Derived from the service and the conversation on every request (D163, T73). */
    CONVERSATION("conversation"),
    ;

    companion object {
        fun fromWireId(wireId: String): OnlineAiHeaderValueKind = entries.singleOrNull {
            it.wireId == wireId
        } ?: throw IllegalArgumentException("Online AI custom header value kind is unsupported")
    }
}

/**
 * One user-defined request header of an online service. Only [OnlineAiHeaderValueKind.FIXED]
 * entries carry [value]; secret values live in the credential store, conversation values are
 * derived at request time.
 */
internal data class OnlineAiCustomHeader(
    val name: String,
    val kind: OnlineAiHeaderValueKind,
    val value: String? = null,
) {
    /** Case-insensitive identity used for uniqueness, reservation and secret lookup. */
    val key: String
        get() = OnlineAiHeaderPolicy.key(name)
}

/** Who sends a header that custom headers may not set (D161). */
internal enum class OnlineAiReservedHeaderOwner {
    /** Sent by the plugin itself on every request or for the selected protocol. */
    PLUGIN,

    /** Managed by the HTTP stack (connection framing, compression, cookies, proxies). */
    TRANSPORT,
}

/**
 * The single list of header names custom headers cannot use (D161, D172). Names are lower case;
 * matching is case-insensitive. User-Agent and anthropic-beta are deliberately absent: a custom
 * User-Agent replaces the default and a custom anthropic-beta is merged with the plugin's value.
 */
internal object OnlineAiBuiltInHeaders {
    const val USER_AGENT = "user-agent"
    const val ANTHROPIC_BETA = "anthropic-beta"

    val RESERVED: Map<String, OnlineAiReservedHeaderOwner> = linkedMapOf(
        "accept" to OnlineAiReservedHeaderOwner.PLUGIN,
        "cache-control" to OnlineAiReservedHeaderOwner.PLUGIN,
        "content-type" to OnlineAiReservedHeaderOwner.PLUGIN,
        "authorization" to OnlineAiReservedHeaderOwner.PLUGIN,
        "x-api-key" to OnlineAiReservedHeaderOwner.PLUGIN,
        "x-goog-api-key" to OnlineAiReservedHeaderOwner.PLUGIN,
        "anthropic-version" to OnlineAiReservedHeaderOwner.PLUGIN,
        "host" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "content-length" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "transfer-encoding" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "connection" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "keep-alive" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "proxy-connection" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "te" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "trailer" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "upgrade" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "expect" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "accept-encoding" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "content-encoding" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "cookie" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "proxy-authorization" to OnlineAiReservedHeaderOwner.TRANSPORT,
        "http2-settings" to OnlineAiReservedHeaderOwner.TRANSPORT,
    )

    fun ownerOf(name: String): OnlineAiReservedHeaderOwner? = RESERVED[OnlineAiHeaderPolicy.key(name)]
}

/**
 * Why a custom header list is invalid. [index] is the position in the list being validated, so an
 * editor can attach each problem to its row; texts belong to the UI (P12).
 */
internal sealed interface OnlineAiHeaderProblem {
    data class TooManyHeaders(val count: Int, val maximum: Int) : OnlineAiHeaderProblem

    data class NameEmpty(val index: Int) : OnlineAiHeaderProblem

    data class NameTooLong(val index: Int, val maximumBytes: Int) : OnlineAiHeaderProblem

    /** The name is not an RFC 9110 token (letters, digits and !#$%&'*+-.^_`|~ only). */
    data class NameInvalidCharacter(val index: Int) : OnlineAiHeaderProblem

    /** Same name (case-insensitive) as the entry at [firstIndex]. */
    data class NameDuplicate(val index: Int, val firstIndex: Int) : OnlineAiHeaderProblem

    /** The plugin or the HTTP stack already sends this header. */
    data class NameReserved(val index: Int, val owner: OnlineAiReservedHeaderOwner) : OnlineAiHeaderProblem

    /** A fixed or secret value is missing or blank. */
    data class ValueEmpty(val index: Int) : OnlineAiHeaderProblem

    data class ValueTooLong(val index: Int, val maximumBytes: Int) : OnlineAiHeaderProblem

    /** Only visible ASCII and spaces are allowed (no CR, LF, NUL, tabs or non-ASCII text). */
    data class ValueInvalidCharacter(val index: Int) : OnlineAiHeaderProblem

    /** Secret and per-conversation entries must not carry a stored plain value. */
    data class ValueNotAllowed(val index: Int) : OnlineAiHeaderProblem

    /** Names plus values of one service exceed [maximumBytes]. */
    data class TotalTooLarge(val bytes: Int, val maximumBytes: Int) : OnlineAiHeaderProblem
}

/** Thrown by [OnlineAiHeaderPolicy.requireValid]; the message never contains header values. */
internal class OnlineAiHeaderValidationException(
    val problems: List<OnlineAiHeaderProblem>,
) : IllegalArgumentException(
    "Online AI custom request headers are invalid: " +
        problems.joinToString(", ") { problem -> problem::class.java.simpleName },
)

/** Limits and checks of T70. */
internal object OnlineAiHeaderPolicy {
    const val MAXIMUM_HEADERS = 16
    const val MAXIMUM_NAME_BYTES = 64
    const val MAXIMUM_VALUE_BYTES = 1024
    const val MAXIMUM_TOTAL_BYTES = 8 * 1024

    /** "ses_" plus 32 lower-case hexadecimal digits (T73). */
    const val CONVERSATION_VALUE_BYTES = 36

    private const val TOKEN_SYMBOLS = "!#$%&'*+-.^_`|~"
    private val SECRET_HINTS = listOf("auth", "key", "token", "secret")

    fun key(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun isValidName(name: String): Boolean =
        name.length in 1..MAXIMUM_NAME_BYTES && name.all(::isTokenCharacter)

    fun isValidValue(value: CharSequence): Boolean =
        value.length in 1..MAXIMUM_VALUE_BYTES &&
            value.first() != ' ' && value.last() != ' ' &&
            value.all(::isValueCharacter)

    fun isValueCharacter(character: Char): Boolean = character in ' '..'~'

    /** Default for the "secret" switch of a new header (D162); the editor decides (P12). */
    fun suggestsSecret(name: String): Boolean {
        val lower = key(name)
        return SECRET_HINTS.any { hint -> hint in lower }
    }

    /** Trims names and fixed values; keeps the user's order and spelling of names. */
    fun normalize(headers: List<OnlineAiCustomHeader>): List<OnlineAiCustomHeader> = headers.map { header ->
        header.copy(name = header.name.trim(), value = header.value?.trim())
    }

    /**
     * Every problem of [headers] after trimming. [secretValueBytes] maps a lower-case name to the
     * byte length of its stored secret so the 8 KiB total can include it.
     */
    fun problems(
        headers: List<OnlineAiCustomHeader>,
        secretValueBytes: Map<String, Int> = emptyMap(),
    ): List<OnlineAiHeaderProblem> {
        val normalized = normalize(headers)
        val result = ArrayList<OnlineAiHeaderProblem>()
        if (normalized.size > MAXIMUM_HEADERS) {
            result += OnlineAiHeaderProblem.TooManyHeaders(normalized.size, MAXIMUM_HEADERS)
        }
        val firstIndexByKey = HashMap<String, Int>()
        var totalBytes = 0L
        normalized.forEachIndexed { index, header ->
            val name = header.name
            val nameBytes = name.toByteArray(Charsets.UTF_8).size
            totalBytes += nameBytes
            when {
                name.isEmpty() -> result += OnlineAiHeaderProblem.NameEmpty(index)
                else -> {
                    if (nameBytes > MAXIMUM_NAME_BYTES) {
                        result += OnlineAiHeaderProblem.NameTooLong(index, MAXIMUM_NAME_BYTES)
                    }
                    if (!name.all(::isTokenCharacter)) {
                        result += OnlineAiHeaderProblem.NameInvalidCharacter(index)
                    } else {
                        val key = key(name)
                        OnlineAiBuiltInHeaders.RESERVED[key]?.let { owner ->
                            result += OnlineAiHeaderProblem.NameReserved(index, owner)
                        }
                        val firstIndex = firstIndexByKey.putIfAbsent(key, index)
                        if (firstIndex != null) {
                            result += OnlineAiHeaderProblem.NameDuplicate(index, firstIndex)
                        }
                    }
                }
            }
            when (header.kind) {
                OnlineAiHeaderValueKind.FIXED -> {
                    val value = header.value.orEmpty()
                    val valueBytes = value.toByteArray(Charsets.UTF_8).size
                    totalBytes += valueBytes
                    valueProblem(index, value)?.let(result::add)
                    if (valueBytes > MAXIMUM_VALUE_BYTES) {
                        result += OnlineAiHeaderProblem.ValueTooLong(index, MAXIMUM_VALUE_BYTES)
                    }
                }
                OnlineAiHeaderValueKind.SECRET -> {
                    if (header.value != null) result += OnlineAiHeaderProblem.ValueNotAllowed(index)
                    totalBytes += secretValueBytes[key(name)] ?: 0
                }
                OnlineAiHeaderValueKind.CONVERSATION -> {
                    if (header.value != null) result += OnlineAiHeaderProblem.ValueNotAllowed(index)
                    totalBytes += CONVERSATION_VALUE_BYTES
                }
            }
        }
        if (totalBytes > MAXIMUM_TOTAL_BYTES) {
            result += OnlineAiHeaderProblem.TotalTooLarge(
                bytes = totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                maximumBytes = MAXIMUM_TOTAL_BYTES,
            )
        }
        return result
    }

    /** Returns the trimmed list or throws [OnlineAiHeaderValidationException] with all problems. */
    fun requireValid(
        headers: List<OnlineAiCustomHeader>,
        secretValueBytes: Map<String, Int> = emptyMap(),
    ): List<OnlineAiCustomHeader> {
        val problems = problems(headers, secretValueBytes)
        if (problems.isNotEmpty()) throw OnlineAiHeaderValidationException(problems)
        return normalize(headers)
    }

    /** Empty and character problems of an already trimmed value; length is checked by callers. */
    private fun valueProblem(index: Int, value: CharSequence): OnlineAiHeaderProblem? = when {
        value.isEmpty() -> OnlineAiHeaderProblem.ValueEmpty(index)
        !value.all(::isValueCharacter) -> OnlineAiHeaderProblem.ValueInvalidCharacter(index)
        else -> null
    }

    private fun isTokenCharacter(character: Char): Boolean =
        character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character in TOKEN_SYMBOLS
}
