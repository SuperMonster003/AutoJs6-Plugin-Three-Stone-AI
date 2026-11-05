package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * "key -> bytes" property tables of conversation history format 6 (roadmap D81 / D91).
 *
 * Format 6 writes one table per message, right after the message target, and one table per
 * conversation, right after its context state. Later phases add keys without bumping the format
 * version, so a reader keeps working on files written by newer builds:
 *
 * - Table: a big-endian `Int` entry count (0 - [MAXIMUM_ENTRIES]), then the entries in key order.
 * - Entry: the key as a big-endian `Int` byte length plus ASCII bytes, then the value as a
 *   big-endian `Int` byte length (0 - [MAXIMUM_VALUE_BYTES], 64 KiB) plus the raw bytes.
 * - Keys match [KEY_PATTERN] (lowercase ASCII, at most [MAXIMUM_KEY_BYTES] bytes) and are unique
 *   within one table; a duplicate or malformed key rejects the file as corrupt.
 * - A reader skips keys it does not know and known keys whose value it cannot parse, so newer
 *   values (for example a future effort level) fall back to the defaults instead of failing.
 *   Skipped entries are not written back.
 *
 * Conversation keys (an absent key means "use the profile default", roadmap D72):
 * - [CONVERSATION_EFFORT] "effort": [GenerationEffort.persistentId] as ASCII.
 * - [CONVERSATION_FAST] "fast": one byte, 0 = off, 1 = on.
 *
 * Message keys (assistant replies only, roadmap P3 / D94; absent when the reply had no thinking text):
 * - [MESSAGE_REASONING] "reasoning": the thinking text as UTF-8, at most [MAXIMUM_VALUE_BYTES] bytes,
 *   cut on a code point boundary ([ChatMessageReasoning.bounded]).
 * - [MESSAGE_REASONING_MILLIS] "reasoning.ms": the thinking time as a big-endian `Long` (8 bytes,
 *   not negative); absent when it was not measured.
 * - [MESSAGE_REASONING_TRUNCATED] "reasoning.cut": one byte 1 when the text was cut.
 *
 * The "reasoning.*" keys are ignored without "reasoning". Builds before 1.6.0 skip all three.
 */
internal object ConversationHistoryProperties {
    const val CONVERSATION_EFFORT = "effort"
    const val CONVERSATION_FAST = "fast"
    const val MESSAGE_REASONING = "reasoning"
    const val MESSAGE_REASONING_MILLIS = "reasoning.ms"
    const val MESSAGE_REASONING_TRUNCATED = "reasoning.cut"

    const val MAXIMUM_ENTRIES = 64
    const val MAXIMUM_KEY_BYTES = 32
    const val MAXIMUM_VALUE_BYTES = 64 * 1_024
    private val KEY_PATTERN = Regex("[a-z][a-z0-9_.]*")

    fun conversationTable(conversation: StoredConversation): Map<String, ByteArray> = buildMap {
        conversation.effortOverride?.let { effort ->
            put(CONVERSATION_EFFORT, effort.persistentId.toByteArray(Charsets.US_ASCII))
        }
        conversation.fastOverride?.let { fast -> put(CONVERSATION_FAST, byteArrayOf(if (fast) 1 else 0)) }
    }

    fun messageTable(message: ChatMessage): Map<String, ByteArray> = buildMap {
        val stored = message.reasoning
            ?.let { value -> ChatMessageReasoning.bounded(value.text, value.durationMillis, value.truncated) }
            ?.takeIf { value -> value.text.isNotEmpty() }
            ?: return@buildMap
        put(MESSAGE_REASONING, stored.text.toByteArray(Charsets.UTF_8))
        stored.durationMillis?.let { millis ->
            put(MESSAGE_REASONING_MILLIS, ByteBuffer.allocate(Long.SIZE_BYTES).putLong(millis).array())
        }
        if (stored.truncated) put(MESSAGE_REASONING_TRUNCATED, byteArrayOf(1))
    }

    fun reasoning(table: Map<String, ByteArray>): ChatMessageReasoning? {
        val text = table[MESSAGE_REASONING]?.toString(Charsets.UTF_8)?.takeIf(String::isNotEmpty) ?: return null
        val millis = table[MESSAGE_REASONING_MILLIS]
            ?.takeIf { bytes -> bytes.size == Long.SIZE_BYTES }
            ?.let { bytes -> ByteBuffer.wrap(bytes).long }
            ?.takeIf { value -> value >= 0L }
        val truncated = table[MESSAGE_REASONING_TRUNCATED]?.singleOrNull()?.toInt() == 1
        return ChatMessageReasoning(text, millis, truncated)
    }

    fun effortOverride(table: Map<String, ByteArray>): GenerationEffort? =
        table[CONVERSATION_EFFORT]?.let { bytes ->
            GenerationEffort.fromPersistentId(bytes.toString(Charsets.US_ASCII))
        }

    fun fastOverride(table: Map<String, ByteArray>): Boolean? =
        table[CONVERSATION_FAST]?.singleOrNull()?.let { value ->
            when (value.toInt()) {
                0 -> false
                1 -> true
                else -> null
            }
        }

    fun write(output: DataOutputStream, table: Map<String, ByteArray>) {
        require(table.size <= MAXIMUM_ENTRIES) { "Too many conversation history properties" }
        output.writeInt(table.size)
        table.toSortedMap().forEach { (key, value) ->
            requireValidKey(key)
            require(value.size <= MAXIMUM_VALUE_BYTES) { "Conversation history property is too large" }
            val keyBytes = key.toByteArray(Charsets.US_ASCII)
            output.writeInt(keyBytes.size)
            output.write(keyBytes)
            output.writeInt(value.size)
            output.write(value)
        }
    }

    fun read(input: DataInputStream): Map<String, ByteArray> {
        val count = input.readInt()
        require(count in 0..MAXIMUM_ENTRIES) { "Invalid conversation history property count" }
        val table = LinkedHashMap<String, ByteArray>(count)
        repeat(count) {
            val keySize = input.readInt()
            require(keySize in 1..MAXIMUM_KEY_BYTES) { "Invalid conversation history property key" }
            val key = ByteArray(keySize).also(input::readFully).toString(Charsets.US_ASCII)
            requireValidKey(key)
            val valueSize = input.readInt()
            require(valueSize in 0..MAXIMUM_VALUE_BYTES) { "Invalid conversation history property value" }
            val value = ByteArray(valueSize).also(input::readFully)
            require(table.put(key, value) == null) { "Duplicate conversation history property" }
        }
        return table
    }

    private fun requireValidKey(key: String) {
        require(key.length in 1..MAXIMUM_KEY_BYTES && KEY_PATTERN.matches(key)) {
            "Invalid conversation history property key"
        }
    }
}
