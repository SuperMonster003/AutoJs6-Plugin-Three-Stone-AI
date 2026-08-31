package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream

internal data class StoredConversation(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val target: ConversationTargetSnapshot?,
    val messages: List<ChatMessage>,
    val contextState: ConversationContextState = ConversationContextState.EMPTY,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(createdAtMillis >= 0L)
        require(updatedAtMillis >= createdAtMillis)
        require(messages.map(ChatMessage::id) == messages.map(ChatMessage::id).distinct().sorted()) {
            "Conversation messages must have unique increasing IDs"
        }
    }
}

internal object ConversationHistoryPolicy {
    /** Decoder guard only; persisted history is governed by its byte budget, not an item cap. */
    const val MAXIMUM_SERIALIZED_CONVERSATIONS = 100_000
    const val MAXIMUM_MESSAGES_PER_CONVERSATION = 256

    fun titleFor(messages: List<ChatMessage>, fallback: String): String {
        val firstPrompt = messages.firstOrNull { message ->
            message.role == ChatMessageRole.USER && message.text.isNotBlank()
        }?.text ?: return fallback
        val compact = firstPrompt.replace(WHITESPACE, " ").trim()
        val codePointCount = compact.codePointCount(0, compact.length)
        return if (codePointCount <= MAXIMUM_TITLE_CHARACTERS) {
            compact
        } else {
            val end = compact.offsetByCodePoints(
                0,
                MAXIMUM_TITLE_CHARACTERS - TITLE_ELLIPSIS.length,
            )
            compact.substring(0, end).trimEnd() + TITLE_ELLIPSIS
        }
    }

    fun normalized(conversations: List<StoredConversation>): List<StoredConversation> {
        val newestById = LinkedHashMap<String, StoredConversation>()
        conversations.sortedByDescending(StoredConversation::updatedAtMillis).forEach { conversation ->
            newestById.putIfAbsent(conversation.id, conversation.withBoundedMessages())
        }
        val retained = ArrayList<StoredConversation>()
        var retainedBytes = 0
        for (conversation in newestById.values) {
            val estimatedBytes = conversation.estimatedBytes()
            if (retained.isNotEmpty() && retainedBytes + estimatedBytes > MAXIMUM_HISTORY_BYTES) {
                break
            }
            retained += conversation
            retainedBytes += estimatedBytes
        }
        return retained
    }

    private fun StoredConversation.withBoundedMessages(): StoredConversation {
        if (messages.size <= MAXIMUM_MESSAGES_PER_CONVERSATION && estimatedMessageBytes() <=
            MAXIMUM_CONVERSATION_MESSAGE_BYTES
        ) {
            return this
        }
        val retainedReversed = ArrayList<ChatMessage>()
        var retainedBytes = 0
        for (message in messages.asReversed()) {
            if (retainedReversed.size >= MAXIMUM_MESSAGES_PER_CONVERSATION) break
            val messageBytes = message.text.toByteArray(Charsets.UTF_8).size +
                message.target.estimatedBytes() + MESSAGE_OVERHEAD_BYTES
            if (
                retainedReversed.isNotEmpty() &&
                retainedBytes + messageBytes > MAXIMUM_CONVERSATION_MESSAGE_BYTES
            ) {
                break
            }
            retainedReversed += message
            retainedBytes += messageBytes
        }
        val desiredStart = messages.size - retainedReversed.size
        val coveredThrough = contextState.coveredThroughMessageId ?: return this
        val firstUncoveredIndex = messages.indexOfFirst { message ->
            message.id > coveredThrough
        }.let { index -> if (index < 0) messages.size else index }
        val safeStart = minOf(desiredStart, firstUncoveredIndex)
        return if (safeStart <= 0) this else copy(messages = messages.drop(safeStart))
    }

    private fun StoredConversation.estimatedMessageBytes(): Int = messages.sumOf { message ->
        message.text.toByteArray(Charsets.UTF_8).size + message.target.estimatedBytes() +
            MESSAGE_OVERHEAD_BYTES
    }

    private fun StoredConversation.estimatedBytes(): Int = estimatedMessageBytes() +
        id.toByteArray(Charsets.UTF_8).size + title.toByteArray(Charsets.UTF_8).size +
        target.estimatedBytes() + contextState.estimatedBytes() + CONVERSATION_OVERHEAD_BYTES

    private fun ConversationContextState.estimatedBytes(): Int =
        summarySegments.sumOf { segment ->
            segment.summary.toByteArray(Charsets.UTF_8).size +
                segment.sourceHash.toByteArray(Charsets.UTF_8).size +
                segment.sourceMessageIds.size * Long.SIZE_BYTES + CONTEXT_ITEM_OVERHEAD_BYTES
        } + workingMemory.sumOf { item ->
            item.key.toByteArray(Charsets.UTF_8).size +
                item.text.toByteArray(Charsets.UTF_8).size +
                item.sourceMessageIds.size * Long.SIZE_BYTES + CONTEXT_ITEM_OVERHEAD_BYTES
        }

    private fun ConversationTargetSnapshot?.estimatedBytes(): Int = this?.let { snapshot ->
        snapshot.targetId.toByteArray(Charsets.UTF_8).size +
            snapshot.providerId.toByteArray(Charsets.UTF_8).size +
            snapshot.modelId.toByteArray(Charsets.UTF_8).size +
            snapshot.displayName.toByteArray(Charsets.UTF_8).size + TARGET_OVERHEAD_BYTES
    } ?: 0

    private val WHITESPACE = Regex("\\s+")
    private const val MAXIMUM_TITLE_CHARACTERS = 64
    private const val TITLE_ELLIPSIS = "..."
    private const val MAXIMUM_CONVERSATION_MESSAGE_BYTES = 1 * 1_024 * 1_024
    private const val MAXIMUM_HISTORY_BYTES = 24 * 1_024 * 1_024
    private const val MESSAGE_OVERHEAD_BYTES = 80
    private const val CONVERSATION_OVERHEAD_BYTES = 128
    private const val TARGET_OVERHEAD_BYTES = 64
    private const val CONTEXT_ITEM_OVERHEAD_BYTES = 64
}

internal data class ConversationSearchMatch(
    val messageId: Long,
    val start: Int,
    val end: Int,
) {
    init {
        require(messageId > 0L)
        require(start >= 0)
        require(end > start)
    }
}

internal object ConversationSearchPolicy {
    fun documentFor(message: ChatMessage): MarkdownDocument =
        if (message.role == ChatMessageRole.ASSISTANT && message.text.isNotEmpty()) {
            StreamingMarkdownParser.parse(message.text)
        } else {
            MarkdownDocument(message.text, emptyList())
        }

    fun find(messages: List<ChatMessage>, query: String): List<ConversationSearchMatch> {
        if (query.isBlank()) return emptyList()
        return buildList {
            messages.forEach { message ->
                val visibleText = documentFor(message).text
                var searchFrom = 0
                while (searchFrom <= visibleText.length - query.length) {
                    val start = visibleText.indexOf(query, searchFrom, ignoreCase = true)
                    if (start < 0) break
                    add(ConversationSearchMatch(message.id, start, start + query.length))
                    searchFrom = start + query.length
                }
            }
        }
    }
}

internal object ConversationTranscriptFormatter {
    fun format(
        conversations: List<StoredConversation>,
        userLabel: String,
        assistantLabel: String,
        noticeLabel: String,
    ): String = conversations.joinToString("\n\n") { conversation ->
        buildString {
            append(conversation.title)
            conversation.messages.filter { message -> message.text.isNotBlank() }
                .forEach { message ->
                    append("\n\n")
                    append(
                        when (message.role) {
                            ChatMessageRole.USER -> userLabel
                            ChatMessageRole.ASSISTANT -> assistantLabel
                            ChatMessageRole.NOTICE -> noticeLabel
                        },
                    )
                    append(":\n")
                    append(message.text)
                }
        }
    }
}

internal data class MessageEditImpact(
    val messageIndex: Int,
    val laterMessageCount: Int,
)

internal object ConversationEditPolicy {
    fun impact(messages: List<ChatMessage>, userMessageId: Long): MessageEditImpact? {
        val index = messages.indexOfFirst { message -> message.id == userMessageId }
        if (index < 0 || messages[index].role != ChatMessageRole.USER) return null
        return MessageEditImpact(index, messages.lastIndex - index)
    }

    fun prefixBefore(messages: List<ChatMessage>, userMessageId: Long): List<ChatMessage>? {
        val impact = impact(messages, userMessageId) ?: return null
        return messages.take(impact.messageIndex)
    }
}

internal data class MessageDeletionImpact(
    val messageIndex: Int,
    val laterMessageCount: Int,
)

internal object ConversationDeletionPolicy {
    fun impact(messages: List<ChatMessage>, userMessageId: Long): MessageDeletionImpact? {
        val index = messages.indexOfFirst { message -> message.id == userMessageId }
        if (index < 0 || messages[index].role != ChatMessageRole.USER) return null
        return MessageDeletionImpact(index, messages.lastIndex - index)
    }

    fun prefixBefore(messages: List<ChatMessage>, userMessageId: Long): List<ChatMessage>? {
        val impact = impact(messages, userMessageId) ?: return null
        return messages.take(impact.messageIndex)
    }
}

internal data class MessageRegenerationImpact(
    val userMessageId: Long,
    val laterMessageCount: Int,
    val responseTarget: ConversationTargetSnapshot,
)

internal object ConversationRegenerationPolicy {
    fun impact(messages: List<ChatMessage>, assistantMessageId: Long): MessageRegenerationImpact? {
        val assistantIndex = messages.indexOfFirst { message -> message.id == assistantMessageId }
        if (assistantIndex < 0 || messages[assistantIndex].role != ChatMessageRole.ASSISTANT) return null
        val responseTarget = messages[assistantIndex].target ?: return null
        var userIndex = assistantIndex - 1
        while (userIndex >= 0 && messages[userIndex].role == ChatMessageRole.NOTICE) userIndex--
        val user = messages.getOrNull(userIndex)?.takeIf { message ->
            message.role == ChatMessageRole.USER
        } ?: return null
        return MessageRegenerationImpact(
            userMessageId = user.id,
            laterMessageCount = messages.lastIndex - assistantIndex,
            responseTarget = responseTarget,
        )
    }
}

/** Versioned, bounded binary persistence format independent of Android framework classes. */
internal object ConversationHistoryCodec {
    fun encode(conversations: List<StoredConversation>): ByteArray {
        val normalized = ConversationHistoryPolicy.normalized(conversations)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(normalized.size)
                normalized.forEach { conversation -> writeConversation(output, conversation) }
            }
            bytes.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): List<StoredConversation> {
        require(bytes.size <= MAXIMUM_FILE_BYTES) { "Conversation history is too large" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Invalid conversation history magic" }
            require(input.readInt() == VERSION) { "Unsupported conversation history version" }
            val count = input.readBoundedCount(
                ConversationHistoryPolicy.MAXIMUM_SERIALIZED_CONVERSATIONS,
            )
            buildList(count) {
                repeat(count) { add(readConversation(input)) }
            }.also {
                require(input.available() == 0) { "Unexpected trailing conversation history data" }
            }
        }.let(ConversationHistoryPolicy::normalized)
    }

    fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1_024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total = Math.addExact(total, count)
            require(total <= MAXIMUM_FILE_BYTES) { "Conversation history is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun writeConversation(output: DataOutputStream, conversation: StoredConversation) {
        output.writeText(conversation.id, MAXIMUM_ID_BYTES)
        output.writeText(conversation.title, MAXIMUM_TITLE_BYTES)
        output.writeLong(conversation.createdAtMillis)
        output.writeLong(conversation.updatedAtMillis)
        output.writeNullableTarget(conversation.target)
        require(conversation.messages.size <= MAXIMUM_SERIALIZED_MESSAGES_PER_CONVERSATION)
        output.writeInt(conversation.messages.size)
        conversation.messages.forEach { message ->
            output.writeLong(message.id)
            output.writeText(message.role.name, MAXIMUM_ENUM_BYTES)
            output.writeText(message.status.name, MAXIMUM_ENUM_BYTES)
            output.writeText(message.text, MAXIMUM_MESSAGE_BYTES)
            output.writeBoolean(message.usage != null)
            message.usage?.let { usage ->
                output.writeLong(usage.inputTokens)
                output.writeLong(usage.outputTokens)
                output.writeLong(usage.durationMillis)
            }
            output.writeNullableTarget(message.target)
        }
        output.writeContextState(conversation.contextState)
    }

    private fun readConversation(input: DataInputStream): StoredConversation {
        val id = input.readText(MAXIMUM_ID_BYTES)
        val title = input.readText(MAXIMUM_TITLE_BYTES)
        val createdAt = input.readLong()
        val updatedAt = input.readLong()
        val target = input.readNullableTarget()
        val messageCount = input.readBoundedCount(MAXIMUM_SERIALIZED_MESSAGES_PER_CONVERSATION)
        val messages = buildList(messageCount) {
            repeat(messageCount) {
                val messageId = input.readLong()
                val role = ChatMessageRole.valueOf(input.readText(MAXIMUM_ENUM_BYTES))
                val status = ChatMessageStatus.valueOf(input.readText(MAXIMUM_ENUM_BYTES))
                val text = input.readText(MAXIMUM_MESSAGE_BYTES)
                val usage = if (input.readBoolean()) {
                    ChatMessageUsage(input.readLong(), input.readLong(), input.readLong())
                } else {
                    null
                }
                val messageTarget = input.readNullableTarget()
                add(
                    ChatMessage(
                        id = messageId,
                        role = role,
                        text = text,
                        status = status,
                        usage = usage,
                        target = messageTarget,
                    ),
                )
            }
        }
        val contextState = input.readContextState()
        return StoredConversation(
            id = id,
            title = title,
            createdAtMillis = createdAt,
            updatedAtMillis = updatedAt,
            target = target,
            messages = messages,
            contextState = contextState,
        )
    }

    private fun DataOutputStream.writeContextState(state: ConversationContextState) {
        writeInt(state.schemaVersion)
        writeBoolean(state.coveredThroughMessageId != null)
        state.coveredThroughMessageId?.let { messageId -> writeLong(messageId) }
        writeInt(state.summarySegments.size)
        state.summarySegments.forEach { segment ->
            writeLong(segment.firstMessageId)
            writeLong(segment.lastMessageId)
            writeLongList(segment.sourceMessageIds)
            writeText(segment.sourceHash, MAXIMUM_SOURCE_HASH_BYTES)
            writeText(segment.summary, ConversationContextPolicy.MAXIMUM_SUMMARY_BYTES)
        }
        writeInt(state.workingMemory.size)
        state.workingMemory.forEach { item ->
            writeText(item.key, MAXIMUM_MEMORY_KEY_BYTES)
            writeText(item.kind.name, MAXIMUM_ENUM_BYTES)
            writeText(item.text, ConversationContextPolicy.MAXIMUM_MEMORY_TEXT_BYTES)
            writeLongList(item.sourceMessageIds)
            writeText(item.status.name, MAXIMUM_ENUM_BYTES)
        }
    }

    private fun DataInputStream.readContextState(): ConversationContextState {
        val schemaVersion = readInt()
        val coveredThrough = if (readBoolean()) readLong() else null
        val segmentCount = readBoundedCount(ConversationContextPolicy.MAXIMUM_SUMMARY_SEGMENTS)
        val segments = buildList(segmentCount) {
            repeat(segmentCount) {
                add(
                    SummarySegment(
                        firstMessageId = readLong(),
                        lastMessageId = readLong(),
                        sourceMessageIds = readLongList(
                            ConversationContextPolicy.MAXIMUM_SEGMENT_SOURCE_IDS,
                        ),
                        sourceHash = readText(MAXIMUM_SOURCE_HASH_BYTES),
                        summary = readText(ConversationContextPolicy.MAXIMUM_SUMMARY_BYTES),
                    ),
                )
            }
        }
        val memoryCount = readBoundedCount(ConversationContextPolicy.MAXIMUM_WORKING_MEMORY_ITEMS)
        val workingMemory = buildList(memoryCount) {
            repeat(memoryCount) {
                add(
                    MemoryItem(
                        key = readText(MAXIMUM_MEMORY_KEY_BYTES),
                        kind = MemoryItemKind.valueOf(readText(MAXIMUM_ENUM_BYTES)),
                        text = readText(ConversationContextPolicy.MAXIMUM_MEMORY_TEXT_BYTES),
                        sourceMessageIds = readLongList(
                            ConversationContextPolicy.MAXIMUM_MEMORY_SOURCE_IDS,
                        ),
                        status = MemoryItemStatus.valueOf(readText(MAXIMUM_ENUM_BYTES)),
                    ),
                )
            }
        }
        return ConversationContextState(
            coveredThroughMessageId = coveredThrough,
            summarySegments = segments,
            workingMemory = workingMemory,
            schemaVersion = schemaVersion,
        )
    }

    private fun DataOutputStream.writeText(value: String, maximumBytes: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maximumBytes) { "Conversation history text exceeds its limit" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(maximumBytes: Int): String {
        val size = readInt()
        require(size in 0..maximumBytes) { "Invalid conversation history text length" }
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableTarget(target: ConversationTargetSnapshot?) {
        writeBoolean(target != null)
        target?.let { snapshot ->
            writeText(snapshot.targetId, MAXIMUM_TARGET_ID_BYTES)
            writeText(snapshot.providerId, MAXIMUM_PROVIDER_ID_BYTES)
            writeText(snapshot.modelId, MAXIMUM_MODEL_ID_BYTES)
            writeText(snapshot.displayName, MAXIMUM_TARGET_NAME_BYTES)
            writeText(snapshot.locality.name, MAXIMUM_ENUM_BYTES)
        }
    }

    private fun DataInputStream.readNullableTarget(): ConversationTargetSnapshot? =
        if (readBoolean()) {
            ConversationTargetSnapshot(
                targetId = readText(MAXIMUM_TARGET_ID_BYTES),
                providerId = readText(MAXIMUM_PROVIDER_ID_BYTES),
                modelId = readText(MAXIMUM_MODEL_ID_BYTES),
                displayName = readText(MAXIMUM_TARGET_NAME_BYTES),
                locality = AiTargetLocality.valueOf(readText(MAXIMUM_ENUM_BYTES)),
            )
        } else {
            null
        }

    private fun DataInputStream.readBoundedCount(maximum: Int): Int = readInt().also { count ->
        require(count in 0..maximum) { "Invalid conversation history item count" }
    }

    private fun DataOutputStream.writeLongList(values: List<Long>) {
        writeInt(values.size)
        values.forEach { value -> writeLong(value) }
    }

    private fun DataInputStream.readLongList(maximum: Int): List<Long> {
        val count = readBoundedCount(maximum)
        return List(count) { readLong() }
    }

    const val MAXIMUM_FILE_BYTES = 32 * 1_024 * 1_024
    private const val MAGIC = 0x33534143 // 3SAC
    private const val VERSION = 4
    private const val MAXIMUM_SERIALIZED_MESSAGES_PER_CONVERSATION = 100_000
    private const val MAXIMUM_ID_BYTES = 128
    private const val MAXIMUM_TITLE_BYTES = 512
    private const val MAXIMUM_TARGET_ID_BYTES = 512
    private const val MAXIMUM_PROVIDER_ID_BYTES = 256
    private const val MAXIMUM_MODEL_ID_BYTES = 512
    private const val MAXIMUM_TARGET_NAME_BYTES = 1_024
    private const val MAXIMUM_ENUM_BYTES = 32
    private const val MAXIMUM_MESSAGE_BYTES = 2 * 1_024 * 1_024
    private const val MAXIMUM_SOURCE_HASH_BYTES = 128
    private const val MAXIMUM_MEMORY_KEY_BYTES = 128
}
