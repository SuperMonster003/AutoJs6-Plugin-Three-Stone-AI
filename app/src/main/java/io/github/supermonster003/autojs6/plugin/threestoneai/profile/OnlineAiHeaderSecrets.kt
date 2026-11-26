package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import java.io.Closeable
import java.util.Collections
import java.util.Locale

/** How [OnlineAiProfileRegistry.save] changes the stored values of secret headers (D162, T72). */
internal sealed interface OnlineAiHeaderSecretsUpdate {
    /**
     * Keeps the values of headers that are still secret under the same name. Values of headers that
     * were removed, renamed or turned into another kind are dropped.
     */
    data object Keep : OnlineAiHeaderSecretsUpdate

    /** Drops every stored secret header value of the profile. */
    data object Clear : OnlineAiHeaderSecretsUpdate

    /**
     * Sets the values of the named secret headers (names match case-insensitively) and keeps the
     * other values like [Keep]. Takes ownership of every array; the registry clears them before
     * returning or throwing.
     */
    class Put private constructor(
        internal val values: Map<String, CharArray>,
    ) : OnlineAiHeaderSecretsUpdate {
        override fun toString(): String = "Put(names=${values.keys}, values=[REDACTED])"

        companion object {
            fun takingOwnership(values: Map<String, CharArray>): Put = Put(LinkedHashMap(values))
        }
    }
}

/**
 * Secret header values captured for one request, keyed by lower-case header name. Only headers that
 * are secret in the bound profile and have a stored value are present. [close] clears every value.
 */
internal class OnlineAiHeaderSecrets private constructor(
    private val values: Map<String, ByteArray>,
) : Closeable {
    /** Lower-case names of the secret headers that have a value. */
    val names: Set<String> = Collections.unmodifiableSet(LinkedHashSet(values.keys))

    /** The value of [name] (case-insensitive) as visible ASCII bytes; owned by this object. */
    fun valueOf(name: String): ByteArray? = values[OnlineAiHeaderPolicy.key(name)]

    /** Visits every value, for example to redact it from diagnostics; arrays stay owned by this object. */
    fun forEachValue(action: (ByteArray) -> Unit) {
        values.values.forEach(action)
    }

    override fun close() {
        values.values.forEach { value -> value.fill(0) }
    }

    override fun toString(): String = "OnlineAiHeaderSecrets(names=$names, values=[REDACTED])"

    companion object {
        val NONE = OnlineAiHeaderSecrets(emptyMap())

        /** Takes ownership of [values]; keys must already be lower case. */
        internal fun takingOwnership(values: Map<String, ByteArray>): OnlineAiHeaderSecrets =
            if (values.isEmpty()) NONE else OnlineAiHeaderSecrets(LinkedHashMap(values))
    }
}

/**
 * Plaintext of the encrypted record that holds one profile's secret header values (T72, D177):
 * `v1` and then one `name:value` line per value, every line ending with LF. Names are lower case
 * RFC 9110 tokens and values visible ASCII, so neither can contain LF and names cannot contain a
 * colon.
 */
internal object OnlineAiHeaderSecretRecord {
    private const val VERSION_LINE = "v1\n"
    private const val SUFFIX = ".headers"

    /** Record ID next to the API Key record of [profileId] in the same credential store. */
    fun recordId(profileId: String): String = profileId + SUFFIX

    /** The caller hands the result to the credential store, which clears it. */
    fun encode(values: Map<String, ByteArray>): CharArray {
        var size = VERSION_LINE.length
        values.forEach { (key, value) -> size += key.length + value.size + 2 }
        val result = CharArray(size)
        var offset = 0
        VERSION_LINE.forEach { character -> result[offset++] = character }
        values.forEach { (key, value) ->
            key.forEach { character -> result[offset++] = character }
            result[offset++] = ':'
            value.forEach { byte -> result[offset++] = (byte.toInt() and 0xff).toChar() }
            result[offset++] = '\n'
        }
        check(offset == size)
        return result
    }

    /** Returns values owned by the caller; throws [IllegalArgumentException] for a malformed record. */
    fun decode(plaintext: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        try {
            require(plaintext.size >= VERSION_LINE.length)
            VERSION_LINE.forEachIndexed { index, character -> require(plaintext[index] == character.code.toByte()) }
            var offset = VERSION_LINE.length
            while (offset < plaintext.size) {
                val end = indexOf(plaintext, '\n', offset, plaintext.size)
                val colon = indexOf(plaintext, ':', offset, end)
                require(colon > offset)
                val key = String(plaintext, offset, colon - offset, Charsets.US_ASCII)
                require(OnlineAiHeaderPolicy.isValidName(key) && key == key.lowercase(Locale.ROOT))
                require(key !in result)
                val value = plaintext.copyOfRange(colon + 1, end)
                result[key] = value
                require(value.size in 1..OnlineAiHeaderPolicy.MAXIMUM_VALUE_BYTES)
                require(value.first() != SPACE && value.last() != SPACE)
                require(value.all { byte -> byte in SPACE..TILDE })
                offset = end + 1
            }
            return result
        } catch (_: Exception) {
            result.values.forEach { value -> value.fill(0) }
            throw IllegalArgumentException("Online AI header secret record is malformed")
        }
    }

    private fun indexOf(bytes: ByteArray, character: Char, from: Int, until: Int): Int {
        for (index in from until until) {
            if (bytes[index] == character.code.toByte()) return index
        }
        throw IllegalArgumentException()
    }

    private const val SPACE = ' '.code.toByte()
    private const val TILDE = '~'.code.toByte()
}

/** Checks secret header values given to [OnlineAiHeaderSecretsUpdate.Put] (T70). */
internal object OnlineAiHeaderSecretValues {
    /**
     * Trims each value and returns it as ASCII bytes keyed by lower-case name. Problems refer to the
     * index of the header in [headers]; a value for a name that is not a secret header is a caller
     * error. Never keeps a String copy of a value.
     */
    fun encode(
        headers: List<OnlineAiCustomHeader>,
        values: Map<String, CharArray>,
    ): Map<String, ByteArray> {
        val indexByKey = HashMap<String, Int>()
        headers.forEachIndexed { index, header ->
            if (header.kind == OnlineAiHeaderValueKind.SECRET) indexByKey.putIfAbsent(header.key, index)
        }
        val result = LinkedHashMap<String, ByteArray>()
        val problems = ArrayList<OnlineAiHeaderProblem>()
        val seen = HashSet<String>()
        try {
            values.forEach { (name, characters) ->
                val key = OnlineAiHeaderPolicy.key(name)
                val index = requireNotNull(indexByKey[key]) {
                    "A secret value was given for a header that is not secret"
                }
                require(seen.add(key)) { "A secret header value was given twice" }
                var start = 0
                var end = characters.size
                while (start < end && characters[start].isWhitespace()) start++
                while (end > start && characters[end - 1].isWhitespace()) end--
                val length = end - start
                when {
                    length == 0 -> problems += OnlineAiHeaderProblem.ValueEmpty(index)
                    (start until end).any { position -> !OnlineAiHeaderPolicy.isValueCharacter(characters[position]) } ->
                        problems += OnlineAiHeaderProblem.ValueInvalidCharacter(index)
                    length > OnlineAiHeaderPolicy.MAXIMUM_VALUE_BYTES ->
                        problems += OnlineAiHeaderProblem.ValueTooLong(index, OnlineAiHeaderPolicy.MAXIMUM_VALUE_BYTES)
                    else -> result[key] = ByteArray(length) { position -> characters[start + position].code.toByte() }
                }
            }
            if (problems.isNotEmpty()) throw OnlineAiHeaderValidationException(problems)
            return result
        } catch (error: Throwable) {
            result.values.forEach { value -> value.fill(0) }
            throw error
        }
    }
}
