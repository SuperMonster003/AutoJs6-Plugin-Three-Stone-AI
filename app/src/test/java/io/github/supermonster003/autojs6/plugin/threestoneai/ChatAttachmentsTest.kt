package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiAttachmentInput
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachment
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachmentKind
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiAttachments
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentsTest {
    private val exactEstimator = ContextTokenEstimator(tokensPerUtf8Byte = 1.0, messageRoleOverheadTokens = 0)

    @Test
    fun `codec round trip keeps every field and order`() {
        val attachments = listOf(
            image(1, width = 1280, height = 720),
            pdf(2, pages = 3),
            text(3, name = "notes.md"),
        )

        assertEquals(attachments, ChatAttachmentCodec.decode(ChatAttachmentCodec.encode(attachments)))
        assertEquals(emptyList<ChatAttachment>(), ChatAttachmentCodec.decode(ChatAttachmentCodec.encode(emptyList())))
    }

    @Test
    fun `codec rejects damaged newer and oversized values`() {
        val encoded = ChatAttachmentCodec.encode(listOf(image(1), text(2)))

        assertNull(ChatAttachmentCodec.decode(encoded.copyOf().also { bytes -> bytes[0] = 2 }))
        assertNull(ChatAttachmentCodec.decode(encoded.copyOf(encoded.size - 1)))
        assertNull(ChatAttachmentCodec.decode(encoded + byteArrayOf(0)))
        assertNull(ChatAttachmentCodec.decode(ByteArray(0)))
        assertNull(ChatAttachmentCodec.decode(ChatAttachmentCodec.encode(listOf(text(1), text(1, name = "other.txt")))))
        val tooMany = ChatAttachmentCodec.encode(List(20) { index -> text(index + 1) }).copyOf()
        tooMany[4] = 21
        assertNull(ChatAttachmentCodec.decode(tooMany))
        assertThrows(IllegalArgumentException::class.java) {
            ChatAttachmentCodec.encode(List(21) { index -> text(index + 1) })
        }
    }

    @Test
    fun `attachment metadata is validated and file names come from the ID`() {
        assertEquals("${id(1)}.jpg", image(1).fileName)
        assertEquals("${id(2)}.png", image(2, mimeType = ChatAttachmentLimits.MIME_PNG).fileName)
        assertEquals("${id(3)}.pdf", pdf(3).fileName)
        assertEquals("${id(4)}.txt", text(4, name = "../../escape.sh").fileName)

        val invalid = listOf(
            { image(1).copy(id = "ABC") },
            { image(1).copy(id = id(1).uppercase().replace('0', 'A')) },
            { image(1).copy(name = "") },
            { image(1).copy(name = "x".repeat(121)) },
            { pdf(1).copy(mimeType = ChatAttachmentLimits.MIME_PNG) },
            { image(1).copy(mimeType = "image/gif") },
            { text(1).copy(sizeBytes = 0) },
            { image(1).copy(sizeBytes = ChatAttachmentLimits.MAXIMUM_IMAGE_BYTES + 1) },
            { text(1).copy(sizeBytes = ChatAttachmentLimits.MAXIMUM_TEXT_BYTES + 1) },
            { pdf(1).copy(sizeBytes = ChatAttachmentLimits.MAXIMUM_PDF_BYTES + 1) },
            { text(1).copy(width = 10, height = 10) },
            { image(1).copy(pageCount = 2) },
            { image(1).copy(width = -1) },
        )
        invalid.forEach { create -> assertThrows(IllegalArgumentException::class.java) { create() } }
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessage(id = 1, role = ChatMessageRole.ASSISTANT, text = "x", target = responseTarget, attachments = listOf(text(1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessage(id = 1, role = ChatMessageRole.USER, text = "x", attachments = List(21) { index -> text(index + 1) })
        }
    }

    @Test
    fun `composer refuses by count message size and conversation size`() {
        val twenty = List(20) { index -> text(index + 1) }
        assertEquals(ChatAttachmentRefusal.TOO_MANY, ChatAttachmentLimits.refusal(twenty, 0L, text(21)))
        assertNull(ChatAttachmentLimits.refusal(twenty.dropLast(1), 0L, text(21)))

        val large = pdf(1, sizeBytes = 30L * MIB)
        assertEquals(ChatAttachmentRefusal.MESSAGE_TOO_LARGE, ChatAttachmentLimits.refusal(listOf(large), 0L, pdf(2, sizeBytes = 3L * MIB)))
        assertNull(ChatAttachmentLimits.refusal(listOf(large), 0L, pdf(2, sizeBytes = 2L * MIB)))

        val nine = pdf(3, sizeBytes = 9L * MIB)
        assertEquals(ChatAttachmentRefusal.CONVERSATION_TOO_LARGE, ChatAttachmentLimits.refusal(emptyList(), 40L * MIB, nine))
        assertNull(ChatAttachmentLimits.refusal(emptyList(), 39L * MIB, nine))

        val sent = listOf(
            ChatMessage(id = 1, role = ChatMessageRole.USER, text = "a", attachments = listOf(nine, image(4, sizeBytes = MIB))),
            ChatMessage(id = 2, role = ChatMessageRole.USER, text = "b", attachments = listOf(text(5, sizeBytes = 10))),
        )
        assertEquals(10L * MIB + 10L, ChatAttachmentLimits.conversationBytes(sent))
    }

    @Test
    fun `targets decide which attachments are unsupported and whether a message fits one request`() {
        val all = listOf(image(1), pdf(2, sizeBytes = 21L * MIB), text(3))
        val openAi = OnlineAiAttachments.input(OnlineAiProtocol.OPENAI_COMPATIBLE, vision = false)
        val openAiVision = OnlineAiAttachments.input(OnlineAiProtocol.OPENAI_COMPATIBLE, vision = true)
        val gemini = OnlineAiAttachments.input(OnlineAiProtocol.GEMINI_GENERATE_CONTENT, vision = true)
        val anthropic = OnlineAiAttachments.input(OnlineAiProtocol.ANTHROPIC_MESSAGES, vision = true)

        assertEquals(all, ChatAttachmentLimits.unsupported(all, null))
        assertEquals(listOf(all[0], all[1]), ChatAttachmentLimits.unsupported(all, openAi))
        assertEquals(listOf(all[1]), ChatAttachmentLimits.unsupported(all, openAiVision))
        assertEquals(listOf(all[1]), ChatAttachmentLimits.unsupported(all, gemini))
        assertEquals(emptyList<ChatAttachment>(), ChatAttachmentLimits.unsupported(all, anthropic))
        assertEquals(emptyList<ChatAttachment>(), ChatAttachmentLimits.unsupported(listOf(pdf(4, sizeBytes = 20L * MIB)), gemini))

        assertTrue(ChatAttachmentLimits.fitsRequest(all, anthropic))
        assertFalse(ChatAttachmentLimits.fitsRequest(all, gemini))
        assertTrue(ChatAttachmentLimits.fitsRequest(listOf(pdf(4, sizeBytes = 20L * MIB)), gemini))
        assertFalse(ChatAttachmentLimits.fitsRequest(List(21) { index -> image(index + 1, sizeBytes = 10) }, anthropic))
        assertTrue(ChatAttachmentLimits.fitsRequest(List(20) { index -> image(index + 1, sizeBytes = 10) }, anthropic))
    }

    @Test
    fun `persistent sessions are kept only while all attachments fit one request and the watermark`() {
        val anthropic = OnlineAiAttachments.input(OnlineAiProtocol.ANTHROPIC_MESSAGES, vision = true)
        val plain = GenerationMessage(GenerationRole.USER, listOf("hi"))
        val withPdf = GenerationMessage(GenerationRole.USER, listOf("read"), attachments = listOf(generation(pdf(1, pages = 2))))
        val committed = listOf(GenerationMessage(GenerationRole.USER, listOf("old"), attachments = listOf(generation(pdf(2, sizeBytes = 20L * MIB)))))

        assertTrue(ChatAttachmentSessionPolicy.fitsSession(committed, plain, null, 0L, 1L, 1.0))
        assertFalse(ChatAttachmentSessionPolicy.fitsSession(emptyList(), withPdf, null, 0L, 100_000L, 1.0))
        assertTrue(ChatAttachmentSessionPolicy.fitsSession(committed, withPdf, anthropic, 1_000L, 100_000L, 1.0))
        assertFalse(ChatAttachmentSessionPolicy.fitsSession(committed, withPdf, anthropic, 96_000L, 100_000L, 1.0))
        assertFalse(ChatAttachmentSessionPolicy.fitsSession(committed, withPdf, anthropic, 100_000L, 100_000L, 1.0))
        val tooLarge = listOf(GenerationMessage(GenerationRole.USER, listOf("old"), attachments = listOf(generation(pdf(3, sizeBytes = 31L * MIB)))))
        assertFalse(ChatAttachmentSessionPolicy.fitsSession(tooLarge, withPdf.copy(attachments = listOf(generation(pdf(1, sizeBytes = 2L * MIB)))), anthropic, 0L, 1_000_000L, 1.0))
    }

    @Test
    fun `omitted note lists at most five names`() {
        assertNull(ChatAttachmentNotes.omitted(emptyList()))
        assertEquals(
            "[Attachments not included in this request: a.txt, b.txt]",
            ChatAttachmentNotes.omitted(listOf(text(1, name = "a.txt"), text(2, name = "b.txt"))),
        )
        val seven = List(7) { index -> text(index + 1, name = "f$index.txt") }
        assertEquals(
            "[Attachments not included in this request: f0.txt, f1.txt, f2.txt, f3.txt, f4.txt, +2]",
            ChatAttachmentNotes.omitted(seven),
        )
    }

    @Test
    fun `store names stay inside the attachment directory`() {
        assertEquals("8f3a2b10-0c4d-4e5f-9a6b-7c8d9e0f1a2b", ChatAttachmentStore.directoryName("8f3a2b10-0c4d-4e5f-9a6b-7c8d9e0f1a2b"))
        listOf(".pending", "../x", "a/b", "", "x".repeat(65), "with space").forEach { unsafe ->
            val name = ChatAttachmentStore.directoryName(unsafe)
            assertTrue(name, Regex("^h[0-9a-f]{32}$").matches(name))
            assertEquals(name, ChatAttachmentStore.directoryName(unsafe))
        }
        assertFalse(ChatAttachmentStore.directoryName("../x") == ChatAttachmentStore.directoryName("a/b"))
        assertTrue(ChatAttachment.ID_PATTERN.matches(ChatAttachmentStore.newId()))

        val tab = 9.toChar()
        val newline = 10.toChar()
        assertEquals("a b.txt", ChatAttachmentStore.boundedName("a${tab}${newline}b.txt"))
        assertEquals("file", ChatAttachmentStore.boundedName(" $tab "))
        val long = ChatAttachmentStore.boundedName("n".repeat(200) + ".pdf")
        assertEquals(120, long.length)
        assertTrue(long.endsWith(".pdf"))
        assertEquals(120, ChatAttachmentStore.boundedName("n".repeat(200) + ".notanextension").length)

        assertEquals(ChatAttachmentLimits.MIME_JPEG, ChatAttachmentStore.mimeFromName("Photo.JPEG"))
        assertEquals(ChatAttachmentLimits.MIME_JPEG, ChatAttachmentStore.mimeFromName("a.jpg"))
        assertEquals(ChatAttachmentLimits.MIME_PNG, ChatAttachmentStore.mimeFromName("a.png"))
        assertEquals(ChatAttachmentLimits.MIME_WEBP, ChatAttachmentStore.mimeFromName("a.webp"))
        assertEquals("image/gif", ChatAttachmentStore.mimeFromName("a.gif"))
        assertEquals(ChatAttachmentLimits.MIME_PDF, ChatAttachmentStore.mimeFromName("a.pdf"))
        assertNull(ChatAttachmentStore.mimeFromName("a.txt"))
        assertNull(ChatAttachmentStore.mimeFromName("noextension"))
    }

    @Test
    fun `text files must be strict UTF-8 without binary content`() {
        assertEquals("plain text", ChatAttachmentTextPolicy.decode("plain text".toByteArray()))
        val accented = byteArrayOf(0x63, 0x61, 0x66, 0xC3.toByte(), 0xA9.toByte())
        assertEquals("caf" + 0xE9.toChar(), ChatAttachmentTextPolicy.decode(accented))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x61)
        assertEquals("a", ChatAttachmentTextPolicy.decode(bom))
        assertEquals("", ChatAttachmentTextPolicy.decode(ByteArray(0)))
        assertNull(ChatAttachmentTextPolicy.decode(byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(ChatAttachmentTextPolicy.decode(byteArrayOf(0x61, 0x00, 0x62)))
        assertNull(ChatAttachmentTextPolicy.decode(ByteArray(40) { index -> if (index % 2 == 0) 0x01 else 0x61 }))
        val lines = "line one\r\n\tline two\n".toByteArray()
        assertEquals("line one\r\n\tline two\n", ChatAttachmentTextPolicy.decode(lines))

        assertTrue(ChatAttachmentTextPolicy.mayBeText("text/plain", "a"))
        assertTrue(ChatAttachmentTextPolicy.mayBeText("text/markdown", "a.md"))
        assertTrue(ChatAttachmentTextPolicy.mayBeText("application/json", "a"))
        assertTrue(ChatAttachmentTextPolicy.mayBeText("application/octet-stream", "a.bin"))
        assertTrue(ChatAttachmentTextPolicy.mayBeText("application/x-unknown", "Main.KT"))
        assertFalse(ChatAttachmentTextPolicy.mayBeText("image/png", "a.png"))
        assertFalse(ChatAttachmentTextPolicy.mayBeText("application/zip", "a.zip"))
    }

    @Test
    fun `history keeps attachments and names file-only conversations after the first file`() {
        val messages = listOf(
            ChatMessage(id = 1, role = ChatMessageRole.USER, text = "", attachments = listOf(pdf(1, name = "report.pdf", pages = 2), image(2))),
            ChatMessage(id = 2, role = ChatMessageRole.ASSISTANT, text = "Read it.", target = responseTarget),
            ChatMessage(id = 3, role = ChatMessageRole.USER, text = "next"),
        )
        val original = StoredConversation(
            id = "conversation-1",
            title = ConversationHistoryPolicy.titleFor(messages, "fallback"),
            createdAtMillis = 100L,
            updatedAtMillis = 200L,
            target = responseTarget,
            messages = messages,
        )

        assertEquals("report.pdf", original.title)
        assertEquals("question", ConversationHistoryPolicy.titleFor(listOf(messages[0].copy(text = "question")), "fallback"))
        assertEquals("fallback", ConversationHistoryPolicy.titleFor(listOf(messages[2].copy(text = " ")), "fallback"))
        val decoded = ConversationHistoryCodec.decode(ConversationHistoryCodec.encode(listOf(original))).single()
        assertEquals(original, decoded)
        assertEquals(emptyList<ChatAttachment>(), decoded.messages[2].attachments)
    }

    @Test
    fun `replay resends earlier attachments newest first within the token budget`() {
        val transcript = listOf(
            user(1, "a", image(1, width = 750, height = 2)),
            assistant(2, "b"),
            user(3, "c", pdf(2, name = "big.pdf", pages = 1)),
            assistant(4, "d"),
        )
        val anthropic = OnlineAiAttachments.input(OnlineAiProtocol.ANTHROPIC_MESSAGES, vision = true)

        val roomy = compile(transcript, maximumInputTokens = 10_000L, input = anthropic)
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c"), listOf("d")), roomy.messages.map(GenerationMessage::textParts))
        assertEquals(listOf(1, 0, 1, 0), roomy.messages.map { message -> message.attachments.size })
        assertEquals(GenerationAttachmentKind.PDF, roomy.messages[2].attachments.single().kind)

        val tight = compile(transcript, maximumInputTokens = 1_000L, input = anthropic)
        assertEquals(GenerationAttachmentKind.IMAGE, tight.messages[0].attachments.single().kind)
        assertEquals(emptyList<GenerationAttachment>(), tight.messages[2].attachments)
        assertEquals(listOf("c", "\n\n[Attachments not included in this request: big.pdf]"), tight.messages[2].textParts)
    }

    @Test
    fun `replay leaves notes for unsupported missing and over-bound attachments`() {
        val transcript = listOf(
            user(1, "", image(1, name = "old.jpg")),
            assistant(2, "b"),
            user(3, "c", image(2, name = "new.jpg"), text(3, name = "data.csv", sizeBytes = 8)),
            assistant(4, "d"),
        )
        val note = { names: String -> "[Attachments not included in this request: $names]" }

        val local = compile(transcript, maximumInputTokens = 10_000L, input = null)
        assertEquals(listOf(note("old.jpg")), local.messages[0].textParts)
        assertEquals(listOf("c", "\n\n" + note("new.jpg, data.csv")), local.messages[2].textParts)
        assertTrue(local.messages.all { message -> message.attachments.isEmpty() })

        val noReplay = ChatConversationPolicy.compileContext(transcript, prompt("p"), ContextCompilationPolicy(exactEstimator, 10_000L))
        assertEquals(listOf(note("old.jpg")), noReplay.messages[0].textParts)

        val textOnly = compile(transcript, 10_000L, OnlineAiAttachments.input(OnlineAiProtocol.OPENAI_COMPATIBLE, vision = false))
        assertEquals(listOf(GenerationAttachmentKind.TEXT), textOnly.messages[2].attachments.map(GenerationAttachment::kind))
        assertEquals(listOf("c", "\n\n" + note("new.jpg")), textOnly.messages[2].textParts)

        val oneImage = AiAttachmentInput(images = true, pdf = true, maximumPdfBytes = MIB, maximumRequestBytes = MIB, maximumRequestImages = 1)
        val newestImageOnly = compile(transcript, 10_000L, oneImage)
        assertEquals(listOf(note("old.jpg")), newestImageOnly.messages[0].textParts)
        assertEquals(2, newestImageOnly.messages[2].attachments.size)

        val promptImage = GenerationMessage(GenerationRole.USER, listOf("p"), attachments = listOf(generation(image(9))))
        val promptCounts = compile(transcript, 10_000L, oneImage, prompt = promptImage)
        assertEquals(listOf(GenerationAttachmentKind.TEXT), promptCounts.messages[2].attachments.map(GenerationAttachment::kind))
        assertEquals(emptyList<GenerationAttachment>(), promptCounts.messages[0].attachments)

        val missing = compile(transcript, 10_000L, oneImage) { attachment -> attachment.takeUnless { it.name == "new.jpg" }?.let { kept -> generation(kept) } }
        assertEquals(listOf(GenerationAttachmentKind.IMAGE), missing.messages[0].attachments.map(GenerationAttachment::kind))
        assertEquals(listOf("c", "\n\n" + note("new.jpg")), missing.messages[2].textParts)
    }

    private fun compile(
        transcript: List<ChatMessage>,
        maximumInputTokens: Long,
        input: AiAttachmentInput?,
        prompt: GenerationMessage = prompt("p"),
        resolve: (ChatAttachment) -> GenerationAttachment? = { attachment -> generation(attachment) },
    ) = ChatConversationPolicy.compileContext(
        transcript = transcript,
        prompt = prompt,
        policy = ContextCompilationPolicy(
            estimator = exactEstimator,
            maximumInputTokens = maximumInputTokens,
            attachmentReplay = ChatAttachmentReplay(input, resolve),
        ),
    )

    private fun prompt(text: String) = GenerationMessage(GenerationRole.USER, listOf(text))

    private fun user(id: Int, text: String, vararg attachments: ChatAttachment) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.USER,
        text = text,
        attachments = attachments.toList(),
    )

    private fun assistant(id: Int, text: String) = ChatMessage(
        id = id.toLong(),
        role = ChatMessageRole.ASSISTANT,
        text = text,
        status = ChatMessageStatus.COMPLETE,
        target = responseTarget,
    )

    private companion object {
        const val MIB = 1024L * 1024L

        val responseTarget = ConversationTargetSnapshot(
            targetId = "local:test-model",
            providerId = "autojs6.three-stone-ai",
            modelId = "test-model",
            displayName = "Test model",
            locality = AiTargetLocality.LOCAL,
        )

        fun id(index: Int) = index.toString(16).padStart(32, '0')

        fun image(
            index: Int,
            name: String = "image$index.jpg",
            mimeType: String = ChatAttachmentLimits.MIME_JPEG,
            sizeBytes: Long = 1_000L,
            width: Int = 0,
            height: Int = 0,
        ) = ChatAttachment(id(index), ChatAttachmentKind.IMAGE, name, mimeType, sizeBytes, width = width, height = height)

        fun pdf(index: Int, name: String = "doc$index.pdf", sizeBytes: Long = 2_000L, pages: Int = 1) =
            ChatAttachment(id(index), ChatAttachmentKind.PDF, name, ChatAttachmentLimits.MIME_PDF, sizeBytes, pageCount = pages)

        fun text(index: Int, name: String = "file$index.txt", sizeBytes: Long = 100L) =
            ChatAttachment(id(index), ChatAttachmentKind.TEXT, name, ChatAttachmentLimits.MIME_TEXT, sizeBytes)

        fun generation(attachment: ChatAttachment): GenerationAttachment =
            attachment.toGeneration { ByteArray(attachment.sizeBytes.toInt()) }
    }
}
