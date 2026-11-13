package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import java.io.ByteArrayOutputStream
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
 *
 * Message key of user messages (roadmap P4.3, D135; absent without attachments):
 * - [MESSAGE_ATTACHMENTS] "attachments": the attachment metadata in [ChatAttachmentCodec] format
 *   (version byte 1). The files stay in `filesDir/attachments/<conversation id>/`. Builds before
 *   1.7.0 skip the key, so the message shows without its attachments there.
 *
 * Message keys of a user message that starts a branched turn (roadmap P6, D148; absent with one version):
 * - [MESSAGE_BRANCH] "branch": three bytes: format 1, the 0-based index of the shown version among
 *   all versions (0 - 7), and the number of chunks (1 - [MAXIMUM_BRANCH_CHUNKS]).
 * - "branch.00", "branch.01", ...: the chunks, at most [MAXIMUM_VALUE_BYTES] each, of the payload
 *   [ConversationHistoryCodec.encodeBranchVersions] writes: the other versions, each one the messages
 *   from its user message to the end of that branch in this message format, property tables (and so
 *   nested branches) included. The message itself starts the shown version.
 *
 * A turn whose versions do not fit the chunks loses its oldest other versions when it is written; a
 * header, chunk or payload that cannot be read leaves the turn with its shown version only. Builds
 * before 1.7.0 skip the keys: they show the selected versions, and the other versions are lost when
 * such a build writes the history again.
 */
internal object ConversationHistoryProperties {
    const val CONVERSATION_EFFORT = "effort"
    const val CONVERSATION_FAST = "fast"
    const val MESSAGE_REASONING = "reasoning"
    const val MESSAGE_REASONING_MILLIS = "reasoning.ms"
    const val MESSAGE_REASONING_TRUNCATED = "reasoning.cut"
    const val MESSAGE_ATTACHMENTS = "attachments"
    const val MESSAGE_BRANCH = "branch"

    /** Up to 3 MiB of other versions per turn, within the [MAXIMUM_ENTRIES] of one table. */
    const val MAXIMUM_BRANCH_CHUNKS = 48
    private const val BRANCH_FORMAT: Byte = 1

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
        if (message.attachments.isNotEmpty()) put(MESSAGE_ATTACHMENTS, ChatAttachmentCodec.encode(message.attachments))
        message.branches?.let { branches -> putBranches(branches) }
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

    /** The other versions of the turn, or null without them or when they cannot be read (D148). */
    fun branches(table: Map<String, ByteArray>, branchDepth: Int): ChatMessageBranches? {
        val header = table[MESSAGE_BRANCH]?.takeIf { bytes -> bytes.size == 3 && bytes[0] == BRANCH_FORMAT } ?: return null
        val selected = header[1].toInt()
        val chunks = header[2].toInt()
        if (chunks !in 1..MAXIMUM_BRANCH_CHUNKS) return null
        val payload = ByteArrayOutputStream()
        repeat(chunks) { index -> payload.write(table[branchChunkKey(index)] ?: return null) }
        val others = ConversationHistoryCodec.decodeBranchVersions(payload.toByteArray(), branchDepth) ?: return null
        if (selected !in 0..others.size) return null
        return try {
            ChatMessageBranches(selected, others)
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    fun branchChunkKey(index: Int): String = MESSAGE_BRANCH + "." + index.toString().padStart(2, '0')

    /** Writes the other versions, leaving out the oldest ones while they do not fit the chunks. */
    private fun MutableMap<String, ByteArray>.putBranches(branches: ChatMessageBranches) {
        var fitting: ChatMessageBranches? = branches
        while (fitting != null) {
            val payload = ConversationHistoryCodec.encodeBranchVersions(fitting.others)
            val chunks = (payload.size + MAXIMUM_VALUE_BYTES - 1) / MAXIMUM_VALUE_BYTES
            if (chunks <= MAXIMUM_BRANCH_CHUNKS) {
                put(MESSAGE_BRANCH, byteArrayOf(BRANCH_FORMAT, fitting.selected.toByte(), chunks.toByte()))
                repeat(chunks) { index ->
                    val start = index * MAXIMUM_VALUE_BYTES
                    put(branchChunkKey(index), payload.copyOfRange(start, minOf(payload.size, start + MAXIMUM_VALUE_BYTES)))
                }
                return
            }
            fitting = ChatBranchPolicy.withoutOldestOther(fitting)
        }
    }

    fun attachments(table: Map<String, ByteArray>): List<ChatAttachment> =
        table[MESSAGE_ATTACHMENTS]?.let(ChatAttachmentCodec::decode).orEmpty()

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
