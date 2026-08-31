package io.github.supermonster003.autojs6.plugin.threestoneai

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

internal enum class MemoryItemKind {
    GOAL,
    CONSTRAINT,
    DECISION,
    OPEN_QUESTION,
    PREFERENCE,
    FACT,
}

internal enum class MemoryItemStatus {
    PROPOSED,
    CONFIRMED,
    REJECTED,
    SUPERSEDED,
}

internal data class MemoryItem(
    val key: String,
    val kind: MemoryItemKind,
    val text: String,
    val sourceMessageIds: List<Long>,
    val status: MemoryItemStatus,
) {
    init {
        require(MEMORY_KEY.matches(key)) { "Conversation memory key is invalid" }
        require(text.isNotBlank()) { "Conversation memory text must not be blank" }
        require(text.toByteArray(Charsets.UTF_8).size <= ConversationContextPolicy.MAXIMUM_MEMORY_TEXT_BYTES) {
            "Conversation memory text is too large"
        }
        require(sourceMessageIds.isNotEmpty()) { "Conversation memory must cite a source" }
        require(sourceMessageIds.size <= ConversationContextPolicy.MAXIMUM_MEMORY_SOURCE_IDS)
        require(sourceMessageIds.all { messageId -> messageId > 0L })
        require(sourceMessageIds == sourceMessageIds.distinct().sorted()) {
            "Conversation memory source IDs must be unique and ordered"
        }
    }

    private companion object {
        val MEMORY_KEY = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    }
}

internal data class SummarySegment(
    val firstMessageId: Long,
    val lastMessageId: Long,
    val sourceMessageIds: List<Long>,
    val sourceHash: String,
    val summary: String,
) {
    init {
        require(firstMessageId > 0L)
        require(lastMessageId >= firstMessageId)
        require(sourceMessageIds.isNotEmpty())
        require(sourceMessageIds.size <= ConversationContextPolicy.MAXIMUM_SEGMENT_SOURCE_IDS)
        require(sourceMessageIds == sourceMessageIds.distinct().sorted()) {
            "Summary source IDs must be unique and ordered"
        }
        require(sourceMessageIds.all { messageId -> messageId in firstMessageId..lastMessageId })
        require(SOURCE_HASH.matches(sourceHash)) { "Summary source hash is invalid" }
        require(summary.isNotBlank()) { "Summary text must not be blank" }
        require(summary.toByteArray(Charsets.UTF_8).size <= ConversationContextPolicy.MAXIMUM_SUMMARY_BYTES) {
            "Summary text is too large"
        }
    }

    private companion object {
        val SOURCE_HASH = Regex("^[0-9a-f]{64}$")
    }
}

internal data class ConversationContextState(
    val coveredThroughMessageId: Long? = null,
    val summarySegments: List<SummarySegment> = emptyList(),
    val workingMemory: List<MemoryItem> = emptyList(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported conversation context schema"
        }
        require(summarySegments.size <= ConversationContextPolicy.MAXIMUM_SUMMARY_SEGMENTS)
        require(workingMemory.size <= ConversationContextPolicy.MAXIMUM_WORKING_MEMORY_ITEMS)
        require(workingMemory.map(MemoryItem::key).distinct().size == workingMemory.size) {
            "Conversation memory keys must be unique"
        }
        if (summarySegments.isEmpty()) {
            require(coveredThroughMessageId == null)
            require(workingMemory.isEmpty()) {
                "Working memory cannot outlive every summary checkpoint"
            }
        } else {
            val coveredThrough = checkNotNull(coveredThroughMessageId)
            var previousLast = 0L
            summarySegments.forEach { segment ->
                require(segment.firstMessageId > previousLast) {
                    "Summary segments must be ordered and non-overlapping"
                }
                previousLast = segment.lastMessageId
            }
            require(coveredThrough == summarySegments.last().lastMessageId) {
                "Conversation context coverage must end at the newest summary segment"
            }
            require(
                workingMemory.all { item ->
                    item.sourceMessageIds.all { sourceId -> sourceId <= coveredThrough }
                },
            ) { "Conversation memory cannot cite messages beyond checkpoint coverage" }
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val EMPTY = ConversationContextState()
    }
}

/** Pure lifecycle and hashing rules for derived, conversation-local context. */
internal object ConversationContextPolicy {
    const val MAXIMUM_SUMMARY_SEGMENTS = 128
    const val MAXIMUM_WORKING_MEMORY_ITEMS = 32
    const val MAXIMUM_SEGMENT_SOURCE_IDS = 256
    const val MAXIMUM_MEMORY_SOURCE_IDS = 64
    const val MAXIMUM_SUMMARY_BYTES = 16 * 1_024
    const val MAXIMUM_MEMORY_TEXT_BYTES = 2 * 1_024

    fun invalidateFrom(
        state: ConversationContextState,
        messageId: Long,
    ): ConversationContextState {
        require(messageId > 0L)
        val retainedSegments = state.summarySegments.takeWhile { segment ->
            segment.lastMessageId < messageId
        }
        if (retainedSegments.isEmpty()) return ConversationContextState.EMPTY
        val retainedThrough = retainedSegments.last().lastMessageId
        return ConversationContextState(
            coveredThroughMessageId = retainedThrough,
            summarySegments = retainedSegments,
            workingMemory = state.workingMemory.filter { item ->
                item.sourceMessageIds.all { sourceId -> sourceId <= retainedThrough }
            },
        )
    }

    fun appendCheckpoint(
        state: ConversationContextState,
        segment: SummarySegment,
        workingMemory: List<MemoryItem>,
    ): ConversationContextState {
        val previousCoverage = state.coveredThroughMessageId
        require(previousCoverage == null || segment.firstMessageId > previousCoverage) {
            "A summary checkpoint cannot overlap existing coverage"
        }
        return ConversationContextState(
            coveredThroughMessageId = segment.lastMessageId,
            summarySegments = state.summarySegments + segment,
            workingMemory = workingMemory,
        )
    }

    fun fingerprint(state: ConversationContextState): String = digest {
        writeInt(state.schemaVersion)
        writeBoolean(state.coveredThroughMessageId != null)
        state.coveredThroughMessageId?.let { messageId -> writeLong(messageId) }
        writeInt(state.summarySegments.size)
        state.summarySegments.forEach { segment ->
            writeLong(segment.firstMessageId)
            writeLong(segment.lastMessageId)
            writeLongList(segment.sourceMessageIds)
            writeText(segment.sourceHash)
            writeText(segment.summary)
        }
        writeInt(state.workingMemory.size)
        state.workingMemory.forEach { item ->
            writeText(item.key)
            writeText(item.kind.name)
            writeText(item.text)
            writeLongList(item.sourceMessageIds)
            writeText(item.status.name)
        }
    }

    /** Emergency omission is part of backend identity so the next safe turn restores L1/L2. */
    fun compilationFingerprint(
        state: ConversationContextState,
        includeDerivedContext: Boolean,
    ): String = if (includeDerivedContext) {
        fingerprint(state)
    } else {
        digest {
            writeText("derived-context-omitted")
            writeText(fingerprint(state))
        }
    }

    /** Hashes the exact source range, including skipped failed/notice records, for invalidation. */
    fun sourceHash(messages: List<ChatMessage>): String {
        require(messages.isNotEmpty())
        require(messages.map(ChatMessage::id) == messages.map(ChatMessage::id).distinct().sorted()) {
            "Summary source messages must be unique and ordered"
        }
        return digest {
            writeInt(messages.size)
            messages.forEach { message ->
                writeLong(message.id)
                writeText(message.role.name)
                writeText(message.status.name)
                writeText(message.text)
                writeBoolean(message.target != null)
                message.target?.let { target ->
                    writeText(target.targetId)
                    writeText(target.providerId)
                    writeText(target.modelId)
                    writeText(target.displayName)
                    writeText(target.locality.name)
                }
            }
        }
    }

    fun messagesInRange(
        transcript: List<ChatMessage>,
        firstMessageId: Long,
        lastMessageId: Long,
    ): List<ChatMessage>? {
        val firstIndex = transcript.indexOfFirst { message -> message.id == firstMessageId }
        val lastIndex = transcript.indexOfFirst { message -> message.id == lastMessageId }
        if (firstIndex < 0 || lastIndex < firstIndex) return null
        return transcript.subList(firstIndex, lastIndex + 1)
    }

    private fun digest(block: DataOutputStream.() -> Unit): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output -> output.block() }
            buffer.toByteArray()
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeLongList(values: List<Long>) {
        writeInt(values.size)
        values.forEach { value -> writeLong(value) }
    }
}
