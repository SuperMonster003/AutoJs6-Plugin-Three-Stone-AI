package io.github.supermonster003.autojs6.plugin.ondeviceai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal data class StoredConversation(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val modelId: String?,
    val modelDisplayName: String?,
    val messages: List<ChatMessage>,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(createdAtMillis >= 0L)
        require(updatedAtMillis >= createdAtMillis)
        require(messages.map(ChatMessage::id).distinct().size == messages.size)
    }
}

internal object ConversationHistoryPolicy {
    const val MAXIMUM_CONVERSATIONS = 50
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
            if (retained.size >= MAXIMUM_CONVERSATIONS) break
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
            val messageBytes = message.text.toByteArray(Charsets.UTF_8).size + MESSAGE_OVERHEAD_BYTES
            if (
                retainedReversed.isNotEmpty() &&
                retainedBytes + messageBytes > MAXIMUM_CONVERSATION_MESSAGE_BYTES
            ) {
                break
            }
            retainedReversed += message
            retainedBytes += messageBytes
        }
        return copy(messages = retainedReversed.asReversed())
    }

    private fun StoredConversation.estimatedMessageBytes(): Int = messages.sumOf { message ->
        message.text.toByteArray(Charsets.UTF_8).size + MESSAGE_OVERHEAD_BYTES
    }

    private fun StoredConversation.estimatedBytes(): Int = estimatedMessageBytes() +
        id.toByteArray(Charsets.UTF_8).size + title.toByteArray(Charsets.UTF_8).size +
        modelId.orEmpty().toByteArray(Charsets.UTF_8).size +
        modelDisplayName.orEmpty().toByteArray(Charsets.UTF_8).size + CONVERSATION_OVERHEAD_BYTES

    private val WHITESPACE = Regex("\\s+")
    private const val MAXIMUM_TITLE_CHARACTERS = 64
    private const val TITLE_ELLIPSIS = "..."
    private const val MAXIMUM_CONVERSATION_MESSAGE_BYTES = 1 * 1_024 * 1_024
    private const val MAXIMUM_HISTORY_BYTES = 24 * 1_024 * 1_024
    private const val MESSAGE_OVERHEAD_BYTES = 80
    private const val CONVERSATION_OVERHEAD_BYTES = 128
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

internal data class MessageRegenerationImpact(
    val userMessageId: Long,
    val laterMessageCount: Int,
)

internal object ConversationRegenerationPolicy {
    fun impact(messages: List<ChatMessage>, assistantMessageId: Long): MessageRegenerationImpact? {
        val assistantIndex = messages.indexOfFirst { message -> message.id == assistantMessageId }
        if (assistantIndex < 0 || messages[assistantIndex].role != ChatMessageRole.ASSISTANT) return null
        var userIndex = assistantIndex - 1
        while (userIndex >= 0 && messages[userIndex].role == ChatMessageRole.NOTICE) userIndex--
        val user = messages.getOrNull(userIndex)?.takeIf { message ->
            message.role == ChatMessageRole.USER
        } ?: return null
        return MessageRegenerationImpact(
            userMessageId = user.id,
            laterMessageCount = messages.lastIndex - assistantIndex,
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
            val count = input.readBoundedCount(ConversationHistoryPolicy.MAXIMUM_CONVERSATIONS)
            buildList(count) {
                repeat(count) { add(readConversation(input)) }
            }.also {
                require(input.available() == 0) { "Unexpected trailing conversation history data" }
            }
        }.let(ConversationHistoryPolicy::normalized)
    }

    private fun writeConversation(output: DataOutputStream, conversation: StoredConversation) {
        output.writeText(conversation.id, MAXIMUM_ID_BYTES)
        output.writeText(conversation.title, MAXIMUM_TITLE_BYTES)
        output.writeLong(conversation.createdAtMillis)
        output.writeLong(conversation.updatedAtMillis)
        output.writeNullableText(conversation.modelId, MAXIMUM_MODEL_ID_BYTES)
        output.writeNullableText(conversation.modelDisplayName, MAXIMUM_MODEL_NAME_BYTES)
        require(conversation.messages.size <= ConversationHistoryPolicy.MAXIMUM_MESSAGES_PER_CONVERSATION)
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
        }
    }

    private fun readConversation(input: DataInputStream): StoredConversation {
        val id = input.readText(MAXIMUM_ID_BYTES)
        val title = input.readText(MAXIMUM_TITLE_BYTES)
        val createdAt = input.readLong()
        val updatedAt = input.readLong()
        val modelId = input.readNullableText(MAXIMUM_MODEL_ID_BYTES)
        val modelName = input.readNullableText(MAXIMUM_MODEL_NAME_BYTES)
        val messageCount = input.readBoundedCount(
            ConversationHistoryPolicy.MAXIMUM_MESSAGES_PER_CONVERSATION,
        )
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
                add(ChatMessage(messageId, role, text, status, usage))
            }
        }
        return StoredConversation(id, title, createdAt, updatedAt, modelId, modelName, messages)
    }

    private fun DataOutputStream.writeText(value: String, maximumBytes: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maximumBytes) { "Conversation history text exceeds its limit" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeNullableText(value: String?, maximumBytes: Int) {
        writeBoolean(value != null)
        if (value != null) writeText(value, maximumBytes)
    }

    private fun DataInputStream.readText(maximumBytes: Int): String {
        val size = readInt()
        require(size in 0..maximumBytes) { "Invalid conversation history text length" }
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataInputStream.readNullableText(maximumBytes: Int): String? =
        if (readBoolean()) readText(maximumBytes) else null

    private fun DataInputStream.readBoundedCount(maximum: Int): Int = readInt().also { count ->
        require(count in 0..maximum) { "Invalid conversation history item count" }
    }

    const val MAXIMUM_FILE_BYTES = 32 * 1_024 * 1_024
    private const val MAGIC = 0x4F444143 // ODAC
    private const val VERSION = 1
    private const val MAXIMUM_ID_BYTES = 128
    private const val MAXIMUM_TITLE_BYTES = 512
    private const val MAXIMUM_MODEL_ID_BYTES = 512
    private const val MAXIMUM_MODEL_NAME_BYTES = 1_024
    private const val MAXIMUM_ENUM_BYTES = 32
    private const val MAXIMUM_MESSAGE_BYTES = 2 * 1_024 * 1_024
}
