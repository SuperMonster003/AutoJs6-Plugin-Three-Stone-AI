package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiAttachmentInput
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachment
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachmentKind
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiAttachments
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal enum class ChatAttachmentKind(
    val code: Int,
    val generationKind: GenerationAttachmentKind,
) {
    IMAGE(1, GenerationAttachmentKind.IMAGE),
    PDF(2, GenerationAttachmentKind.PDF),
    TEXT(3, GenerationAttachmentKind.TEXT),
    ;

    companion object {
        fun fromCode(code: Int): ChatAttachmentKind? = entries.firstOrNull { kind -> kind.code == code }
    }
}

/**
 * Metadata of one file attached to a user message (roadmap P4, D71). The file itself lives in
 * `filesDir/attachments/<conversation id>/[fileName]` ([ChatAttachmentStore]); before sending it
 * waits in `filesDir/attachments/.pending/`. The file name is derived from [id] and [mimeType] only,
 * never from the user's file name.
 */
internal data class ChatAttachment(
    val id: String,
    val kind: ChatAttachmentKind,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val pageCount: Int = 0,
) {
    init {
        require(ID_PATTERN.matches(id)) { "Invalid attachment ID" }
        require(name.isNotEmpty() && name.length <= ChatAttachmentLimits.MAXIMUM_NAME_CHARACTERS)
        require(mimeType in ChatAttachmentLimits.mimeTypes(kind)) { "Unsupported attachment type" }
        require(sizeBytes in 1..ChatAttachmentLimits.maximumBytes(kind))
        require(width >= 0 && height >= 0 && pageCount >= 0)
        require(kind == ChatAttachmentKind.IMAGE || (width == 0 && height == 0))
        require(kind == ChatAttachmentKind.PDF || pageCount == 0)
    }

    val fileName: String
        get() = "$id.${ChatAttachmentLimits.extension(mimeType)}"

    fun toGeneration(source: () -> ByteArray) = GenerationAttachment(
        kind = kind.generationKind,
        name = name,
        mimeType = mimeType,
        byteCount = sizeBytes,
        width = width,
        height = height,
        pageCount = pageCount,
        source = source,
    )

    companion object {
        val ID_PATTERN = Regex("^[0-9a-f]{32}$")
    }
}

/** Why the composer refuses another attachment (roadmap D70 / D71). */
internal enum class ChatAttachmentRefusal {
    TOO_MANY,
    MESSAGE_TOO_LARGE,
    CONVERSATION_TOO_LARGE,
}

/** Limits of chat attachments (roadmap D70, D71, D132). */
internal object ChatAttachmentLimits {
    const val MAXIMUM_PER_MESSAGE = 20
    const val MAXIMUM_IMAGE_BYTES = 5L * 1024L * 1024L
    /** D132: larger images are scaled down to this long edge (the high-resolution tier of Claude). */
    const val MAXIMUM_IMAGE_EDGE = 2576
    const val MAXIMUM_TEXT_BYTES = 256L * 1024L
    const val MAXIMUM_PDF_BYTES = OnlineAiAttachments.MAXIMUM_PDF_BYTES
    const val MAXIMUM_PDF_PAGES = 100
    /** Raw bytes of one message; base64 of 32 MiB still fits the 48 MiB request body bound. */
    const val MAXIMUM_MESSAGE_BYTES = OnlineAiAttachments.MAXIMUM_REQUEST_BYTES
    const val MAXIMUM_CONVERSATION_BYTES = 48L * 1024L * 1024L
    const val MAXIMUM_NAME_CHARACTERS = 120

    const val MIME_JPEG = "image/jpeg"
    const val MIME_PNG = "image/png"
    const val MIME_WEBP = "image/webp"
    const val MIME_PDF = OnlineAiAttachments.PDF_MIME_TYPE
    const val MIME_TEXT = "text/plain"

    fun mimeTypes(kind: ChatAttachmentKind): Set<String> = when (kind) {
        ChatAttachmentKind.IMAGE -> setOf(MIME_JPEG, MIME_PNG, MIME_WEBP)
        ChatAttachmentKind.PDF -> setOf(MIME_PDF)
        ChatAttachmentKind.TEXT -> setOf(MIME_TEXT)
    }

    fun maximumBytes(kind: ChatAttachmentKind): Long = when (kind) {
        ChatAttachmentKind.IMAGE -> MAXIMUM_IMAGE_BYTES
        ChatAttachmentKind.PDF -> MAXIMUM_PDF_BYTES
        ChatAttachmentKind.TEXT -> MAXIMUM_TEXT_BYTES
    }

    fun extension(mimeType: String): String = when (mimeType) {
        MIME_JPEG -> "jpg"
        MIME_PNG -> "png"
        MIME_WEBP -> "webp"
        MIME_PDF -> "pdf"
        else -> "txt"
    }

    /** Null when [candidate] may join [pending]; [conversationBytes] counts the sent attachments. */
    fun refusal(pending: List<ChatAttachment>, conversationBytes: Long, candidate: ChatAttachment): ChatAttachmentRefusal? {
        val messageBytes = pending.sumOf(ChatAttachment::sizeBytes) + candidate.sizeBytes
        return when {
            pending.size >= MAXIMUM_PER_MESSAGE -> ChatAttachmentRefusal.TOO_MANY
            messageBytes > MAXIMUM_MESSAGE_BYTES -> ChatAttachmentRefusal.MESSAGE_TOO_LARGE
            conversationBytes + messageBytes > MAXIMUM_CONVERSATION_BYTES -> ChatAttachmentRefusal.CONVERSATION_TOO_LARGE
            else -> null
        }
    }

    fun conversationBytes(messages: List<ChatMessage>): Long =
        messages.sumOf { message -> message.attachments.sumOf(ChatAttachment::sizeBytes) }

    /** Attachments of one outgoing message that [input] cannot take, in their original order. */
    fun unsupported(attachments: List<ChatAttachment>, input: AiAttachmentInput?): List<ChatAttachment> {
        if (input == null) return attachments
        return attachments.filter { attachment ->
            !input.accepts(attachment.kind.generationKind) ||
                (attachment.kind == ChatAttachmentKind.PDF && attachment.sizeBytes > input.maximumPdfBytes)
        }
    }

    /** True when the whole message fits one request of [input] (bytes and image count). */
    fun fitsRequest(attachments: List<ChatAttachment>, input: AiAttachmentInput): Boolean =
        attachments.sumOf(ChatAttachment::sizeBytes) <= input.maximumRequestBytes &&
            attachments.count { attachment -> attachment.kind == ChatAttachmentKind.IMAGE } <= input.maximumRequestImages
}

/**
 * Value of the history v6 message key "attachments" (roadmap P4.3, D135): a version byte (1), a
 * big-endian `Int` count, then per attachment the ID, kind code, name, MIME type, size, width,
 * height and page count. Strings are a big-endian `Int` byte length plus UTF-8 bytes.
 */
internal object ChatAttachmentCodec {
    private const val VERSION = 1
    private const val MAXIMUM_TEXT_BYTES = 1_024

    fun encode(attachments: List<ChatAttachment>): ByteArray {
        require(attachments.size <= ChatAttachmentLimits.MAXIMUM_PER_MESSAGE)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(VERSION)
            data.writeInt(attachments.size)
            attachments.forEach { attachment ->
                data.writeText(attachment.id)
                data.writeByte(attachment.kind.code)
                data.writeText(attachment.name)
                data.writeText(attachment.mimeType)
                data.writeLong(attachment.sizeBytes)
                data.writeInt(attachment.width)
                data.writeInt(attachment.height)
                data.writeInt(attachment.pageCount)
            }
        }
        return output.toByteArray()
    }

    /** Null for a value this build cannot read (a newer version or a damaged entry). */
    fun decode(bytes: ByteArray): List<ChatAttachment>? = runCatching {
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readUnsignedByte() == VERSION)
            val count = data.readInt()
            require(count in 0..ChatAttachmentLimits.MAXIMUM_PER_MESSAGE)
            val attachments = List(count) {
                ChatAttachment(
                    id = data.readText(),
                    kind = requireNotNull(ChatAttachmentKind.fromCode(data.readUnsignedByte())),
                    name = data.readText(),
                    mimeType = data.readText(),
                    sizeBytes = data.readLong(),
                    width = data.readInt(),
                    height = data.readInt(),
                    pageCount = data.readInt(),
                )
            }
            require(data.read() == -1)
            require(attachments.map(ChatAttachment::id).distinct().size == attachments.size)
            attachments
        }
    }.getOrNull()

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAXIMUM_TEXT_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(): String {
        val size = readInt()
        require(size in 0..MAXIMUM_TEXT_BYTES)
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }
}

/**
 * How [ChatConversationPolicy.compileContext] resends attachments of earlier turns (roadmap D71,
 * D134). [input] is the target's attachment input (null: none are resent); [resolve] returns null
 * when the file is gone.
 */
internal class ChatAttachmentReplay(
    val input: AiAttachmentInput?,
    val resolve: (ChatAttachment) -> GenerationAttachment?,
)

/**
 * D137: a persistent online session resends every committed attachment with each turn. It is kept
 * for a prompt with attachments only while all of them fit one request and the prompt's attachment
 * tokens stay below the hard watermark; otherwise the session is rebuilt and [ChatAttachmentReplay]
 * chooses what to resend.
 */
internal object ChatAttachmentSessionPolicy {
    fun fitsSession(
        committed: List<GenerationMessage>,
        prompt: GenerationMessage,
        input: AiAttachmentInput?,
        accountingTokens: Long,
        hardWatermarkTokens: Long,
        tokensPerUtf8Byte: Double,
    ): Boolean {
        if (prompt.attachments.isEmpty()) return true
        if (input == null) return false
        val all = committed.flatMap(GenerationMessage::attachments) + prompt.attachments
        if (all.sumOf(GenerationAttachment::byteCount) > input.maximumRequestBytes) return false
        if (all.count { attachment -> attachment.kind == GenerationAttachmentKind.IMAGE } > input.maximumRequestImages) return false
        val promptTokens = prompt.attachments.fold(0L) { total, attachment ->
            val tokens = attachment.estimatedTokens(tokensPerUtf8Byte)
            if (Long.MAX_VALUE - total < tokens) Long.MAX_VALUE else total + tokens
        }
        return accountingTokens < hardWatermarkTokens && promptTokens < hardWatermarkTokens - accountingTokens
    }
}

internal object ChatAttachmentNotes {
    private const val MAXIMUM_LISTED_NAMES = 5

    /** Model-facing note for attachments of an earlier turn that this request leaves out. */
    fun omitted(attachments: List<ChatAttachment>): String? {
        if (attachments.isEmpty()) return null
        val names = attachments.take(MAXIMUM_LISTED_NAMES).joinToString(", ") { attachment -> attachment.name }
        val more = attachments.size - MAXIMUM_LISTED_NAMES
        val suffix = if (more > 0) ", +$more" else ""
        return "[Attachments not included in this request: $names$suffix]"
    }
}
