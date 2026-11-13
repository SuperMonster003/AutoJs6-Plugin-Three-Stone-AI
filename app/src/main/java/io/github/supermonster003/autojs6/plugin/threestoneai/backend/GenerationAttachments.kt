package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonObject
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import okio.ByteString.Companion.toByteString
import kotlin.math.ceil

internal enum class GenerationAttachmentKind {
    IMAGE,
    PDF,
    TEXT,
}

/**
 * Chat attachment input a target accepts (roadmap P4, D70); `null` in [AiTargetCapabilities] means
 * no attachments at all (local LiteRT targets). Text files go to every target that has this object.
 */
internal data class AiAttachmentInput(
    val images: Boolean,
    val pdf: Boolean,
    /** Largest single PDF in bytes, before base64. */
    val maximumPdfBytes: Long,
    /** All attachment bytes of one request, before base64. */
    val maximumRequestBytes: Long,
    val maximumRequestImages: Int,
) {
    init {
        require(maximumPdfBytes > 0L)
        require(maximumRequestBytes > 0L)
        require(maximumRequestImages >= 0)
    }

    fun accepts(kind: GenerationAttachmentKind): Boolean = when (kind) {
        GenerationAttachmentKind.IMAGE -> images
        GenerationAttachmentKind.PDF -> pdf
        GenerationAttachmentKind.TEXT -> true
    }
}

/**
 * One chat attachment of a request (roadmap P4, D70). The file is read only while the HTTP body is
 * built, off the main thread; [toString] never shows the content.
 */
internal class GenerationAttachment(
    val kind: GenerationAttachmentKind,
    val name: String,
    val mimeType: String,
    val byteCount: Long,
    val width: Int = 0,
    val height: Int = 0,
    val pageCount: Int = 0,
    private val source: () -> ByteArray,
) {
    init {
        require(name.isNotEmpty())
        require(mimeType.isNotEmpty())
        require(byteCount > 0L)
        require(width >= 0 && height >= 0 && pageCount >= 0)
    }

    fun bytes(): ByteArray = source().also { bytes ->
        require(bytes.size.toLong() == byteCount) { "Attachment file changed" }
    }

    /** D71: images about width x height / 750, PDF about 2000 per page, text by its bytes. */
    fun estimatedTokens(tokensPerUtf8Byte: Double): Long = when (kind) {
        GenerationAttachmentKind.IMAGE -> if (width > 0 && height > 0) {
            maxOf(1L, ceil(width.toDouble() * height.toDouble() / IMAGE_PIXELS_PER_TOKEN).toLong())
        } else {
            UNKNOWN_IMAGE_TOKENS
        }
        GenerationAttachmentKind.PDF -> maxOf(1, pageCount).toLong() * PDF_TOKENS_PER_PAGE
        GenerationAttachmentKind.TEXT -> {
            val bytes = byteCount + TEXT_ENVELOPE_OVERHEAD_BYTES + name.toByteArray(Charsets.UTF_8).size
            val scaled = bytes.toDouble() * tokensPerUtf8Byte
            if (!scaled.isFinite() || scaled >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else ceil(scaled).toLong()
        }
    }

    override fun toString() = "GenerationAttachment(kind=$kind, mimeType=$mimeType, bytes=$byteCount)"

    companion object {
        const val IMAGE_PIXELS_PER_TOKEN = 750.0
        const val UNKNOWN_IMAGE_TOKENS = 1_600L
        const val PDF_TOKENS_PER_PAGE = 2_000L
        const val TEXT_ENVELOPE_OVERHEAD_BYTES = 40L
    }
}

/** Request checks and wire parts for chat attachments (roadmap P4, D70, D132 - D134). */
internal object OnlineAiAttachments {
    const val MAXIMUM_REQUEST_IMAGES = 20
    const val MAXIMUM_REQUEST_BYTES = 32L * 1024L * 1024L
    const val MAXIMUM_PDF_BYTES = 32L * 1024L * 1024L
    const val MAXIMUM_GEMINI_INLINE_BYTES = 20L * 1024L * 1024L

    fun input(protocol: OnlineAiProtocol, vision: Boolean): AiAttachmentInput = AiAttachmentInput(
        images = vision,
        pdf = protocol != OnlineAiProtocol.OPENAI_COMPATIBLE,
        maximumPdfBytes = if (protocol == OnlineAiProtocol.GEMINI_GENERATE_CONTENT) MAXIMUM_GEMINI_INLINE_BYTES else MAXIMUM_PDF_BYTES,
        maximumRequestBytes = if (protocol == OnlineAiProtocol.GEMINI_GENERATE_CONTENT) MAXIMUM_GEMINI_INLINE_BYTES else MAXIMUM_REQUEST_BYTES,
        maximumRequestImages = MAXIMUM_REQUEST_IMAGES,
    )

    fun requireSupported(profile: OnlineAiProfile, messages: List<GenerationMessage>) {
        val attachments = messages.flatMap(GenerationMessage::attachments)
        if (attachments.isEmpty()) return
        messages.forEach { message ->
            require(message.role == GenerationRole.USER || message.attachments.isEmpty()) {
                "Attachments must belong to user messages"
            }
            require(message.nativeToolMessage == null || message.attachments.isEmpty()) {
                "Mixed native and attachment content is unsupported"
            }
        }
        val input = input(profile.provider.protocol, profile.modelId in profile.visionModelIds)
        attachments.forEach { attachment ->
            when (attachment.kind) {
                GenerationAttachmentKind.IMAGE -> require(input.images) { "Image input is not enabled for this online model" }
                GenerationAttachmentKind.PDF -> {
                    require(input.pdf) { "PDF input is not supported by this online protocol" }
                    require(attachment.byteCount <= input.maximumPdfBytes) { "PDF attachment is too large" }
                }
                GenerationAttachmentKind.TEXT -> Unit
            }
        }
        require(attachments.count { it.kind == GenerationAttachmentKind.IMAGE } <= input.maximumRequestImages) {
            "Too many image attachments"
        }
        require(attachments.sumOf(GenerationAttachment::byteCount) <= input.maximumRequestBytes) {
            "Attachments are too large"
        }
    }

    /** Binary attachments come before text, as the Anthropic and Gemini guides recommend. */
    fun parts(attachments: List<GenerationAttachment>, protocol: OnlineAiProtocol): List<JsonObject> =
        attachments.sortedBy { attachment -> if (attachment.kind == GenerationAttachmentKind.TEXT) 1 else 0 }
            .map { attachment -> part(attachment, protocol) }

    /** D133: a text file becomes "File: name" plus its content between tags. */
    fun textEnvelope(name: String, content: String): String =
        "File: ${name.replace(LINE_BREAKS, " ")}\n<file_content>\n$content\n</file_content>"

    private fun part(attachment: GenerationAttachment, protocol: OnlineAiProtocol): JsonObject = JsonObject().apply {
        val bytes = attachment.bytes()
        when (attachment.kind) {
            GenerationAttachmentKind.TEXT -> {
                val text = textEnvelope(attachment.name, bytes.toString(Charsets.UTF_8))
                if (protocol != OnlineAiProtocol.GEMINI_GENERATE_CONTENT) addProperty("type", "text")
                addProperty("text", text)
            }
            GenerationAttachmentKind.IMAGE -> when (protocol) {
                OnlineAiProtocol.OPENAI_COMPATIBLE -> {
                    addProperty("type", "image_url")
                    add("image_url", JsonObject().apply {
                        addProperty("url", "data:${attachment.mimeType};base64,${bytes.toByteString().base64()}")
                    })
                }
                OnlineAiProtocol.ANTHROPIC_MESSAGES -> {
                    addProperty("type", "image")
                    add("source", base64Source(attachment.mimeType, bytes))
                }
                OnlineAiProtocol.GEMINI_GENERATE_CONTENT -> add("inlineData", inlineData(attachment.mimeType, bytes))
            }
            GenerationAttachmentKind.PDF -> when (protocol) {
                OnlineAiProtocol.ANTHROPIC_MESSAGES -> {
                    addProperty("type", "document")
                    add("source", base64Source(PDF_MIME_TYPE, bytes))
                }
                OnlineAiProtocol.GEMINI_GENERATE_CONTENT -> add("inlineData", inlineData(PDF_MIME_TYPE, bytes))
                OnlineAiProtocol.OPENAI_COMPATIBLE -> throw IllegalArgumentException(
                    "PDF input is not supported by this online protocol",
                )
            }
        }
    }

    private fun base64Source(mimeType: String, bytes: ByteArray) = JsonObject().apply {
        addProperty("type", "base64")
        addProperty("media_type", mimeType)
        addProperty("data", bytes.toByteString().base64())
    }

    private fun inlineData(mimeType: String, bytes: ByteArray) = JsonObject().apply {
        addProperty("mimeType", mimeType)
        addProperty("data", bytes.toByteString().base64())
    }

    const val PDF_MIME_TYPE = "application/pdf"
    private val LINE_BREAKS = Regex("[\\r\\n]+")
}
