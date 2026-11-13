package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.supermonster003.autojs6.plugin.threestoneai.ContextTokenEstimator
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class OnlineAiAttachmentsTest {
    private val pdfBytes = "%PDF-1.4 tiny".toByteArray()
    private val pngBytes = byteArrayOf(1, 2, 3, 4)
    private val textBytes = "a,b\n1,2".toByteArray()

    @Test
    fun `anthropic sends documents and images before text files and the prompt`() {
        val parts = content(OnlineAiProvider.ANTHROPIC, message("summarize", text(), pdf(), image()))

        assertEquals(4, parts.size())
        val document = parts[0].asJsonObject
        assertEquals("document", document.get("type").asString)
        assertEquals("base64", document.getAsJsonObject("source").get("type").asString)
        assertEquals("application/pdf", document.getAsJsonObject("source").get("media_type").asString)
        assertEquals(base64(pdfBytes), document.getAsJsonObject("source").get("data").asString)
        val image = parts[1].asJsonObject
        assertEquals("image", image.get("type").asString)
        assertEquals("image/png", image.getAsJsonObject("source").get("media_type").asString)
        assertEquals(base64(pngBytes), image.getAsJsonObject("source").get("data").asString)
        assertEquals("text", parts[2].asJsonObject.get("type").asString)
        assertEquals("File: data.csv\n<file_content>\na,b\n1,2\n</file_content>", parts[2].asJsonObject.get("text").asString)
        assertEquals("summarize", parts[3].asJsonObject.get("text").asString)
    }

    @Test
    fun `gemini sends inline data parts without type fields`() {
        val parts = content(OnlineAiProvider.GEMINI, message("summarize", text(), pdf(), image()))

        assertEquals(4, parts.size())
        assertEquals("application/pdf", parts[0].asJsonObject.getAsJsonObject("inlineData").get("mimeType").asString)
        assertEquals(base64(pdfBytes), parts[0].asJsonObject.getAsJsonObject("inlineData").get("data").asString)
        assertEquals("image/png", parts[1].asJsonObject.getAsJsonObject("inlineData").get("mimeType").asString)
        assertFalse(parts[2].asJsonObject.has("type"))
        assertTrue(parts[2].asJsonObject.get("text").asString.startsWith("File: data.csv\n<file_content>\n"))
        assertEquals("summarize", parts[3].asJsonObject.get("text").asString)
    }

    @Test
    fun `openai compatible takes text files and images but refuses PDF`() {
        val parts = content(OnlineAiProvider.OPENAI_COMPATIBLE, message("look", image(), text()))

        assertEquals(3, parts.size())
        assertEquals("image_url", parts[0].asJsonObject.get("type").asString)
        assertEquals("data:image/png;base64," + base64(pngBytes), parts[0].asJsonObject.getAsJsonObject("image_url").get("url").asString)
        assertEquals("text", parts[1].asJsonObject.get("type").asString)
        assertEquals("look", parts[2].asJsonObject.get("text").asString)
        assertThrows(IllegalArgumentException::class.java) {
            prepare(OnlineAiProvider.OPENAI_COMPATIBLE, listOf(message("read", pdf())))
        }
        assertEquals(2, content(OnlineAiProvider.OPENAI_COMPATIBLE, message("plain", text()), vision = false).size())
        assertThrows(IllegalArgumentException::class.java) {
            prepare(OnlineAiProvider.OPENAI_COMPATIBLE, listOf(message("look", image())), vision = false)
        }
    }

    @Test
    fun `attachment-only messages send no empty text part and history attachments are resent`() {
        for (provider in providers) {
            val only = content(provider, message("", text()))
            assertEquals(1, only.size())
            assertTrue(only[0].asJsonObject.get("text").asString.startsWith("File: data.csv"))
        }
        val history = message("first", image())
        val reply = GenerationMessage(GenerationRole.ASSISTANT, listOf("seen"))
        val json = prepare(OnlineAiProvider.ANTHROPIC, listOf(history, reply, message("again")))
        val messages = json.getAsJsonArray("messages")
        assertEquals("image", messages[0].asJsonObject.getAsJsonArray("content")[0].asJsonObject.get("type").asString)
        assertTrue(messages[2].asJsonObject.get("content").isJsonPrimitive)
    }

    @Test
    fun `requests outside the limits fail before any file is read`() {
        val unread = { error("The file must not be read") }
        val geminiPdf = GenerationAttachment(GenerationAttachmentKind.PDF, "big.pdf", "application/pdf", 20L * MIB + 1, pageCount = 1, source = unread)
        assertThrows(IllegalArgumentException::class.java) { prepare(OnlineAiProvider.GEMINI, listOf(message("x", geminiPdf))) }

        val largePdf = GenerationAttachment(GenerationAttachmentKind.PDF, "big.pdf", "application/pdf", 32L * MIB + 1, pageCount = 1, source = unread)
        assertThrows(IllegalArgumentException::class.java) { prepare(OnlineAiProvider.ANTHROPIC, listOf(message("x", largePdf))) }

        val manyImages = List(21) { GenerationAttachment(GenerationAttachmentKind.IMAGE, "i.png", "image/png", 4, source = unread) }
        assertThrows(IllegalArgumentException::class.java) { prepare(OnlineAiProvider.ANTHROPIC, listOf(message("x", *manyImages.toTypedArray()))) }

        val half = GenerationAttachment(GenerationAttachmentKind.PDF, "half.pdf", "application/pdf", 17L * MIB, pageCount = 1, source = unread)
        assertThrows(IllegalArgumentException::class.java) {
            prepare(OnlineAiProvider.ANTHROPIC, listOf(message("a", half), GenerationMessage(GenerationRole.ASSISTANT, listOf("b")), message("c", half)))
        }

        assertThrows(IllegalArgumentException::class.java) {
            prepare(OnlineAiProvider.ANTHROPIC, listOf(GenerationMessage(GenerationRole.ASSISTANT, listOf("a"), attachments = listOf(text())), message("b")))
        }

        val changed = GenerationAttachment(GenerationAttachmentKind.TEXT, "data.csv", "text/plain", 99, source = { textBytes })
        assertThrows(IllegalArgumentException::class.java) { prepare(OnlineAiProvider.ANTHROPIC, listOf(message("x", changed))) }
    }

    @Test
    fun `inputs follow the protocol limits`() {
        val openAi = OnlineAiAttachments.input(OnlineAiProtocol.OPENAI_COMPATIBLE, vision = false)
        assertFalse(openAi.images)
        assertFalse(openAi.pdf)
        assertTrue(openAi.accepts(GenerationAttachmentKind.TEXT))
        val gemini = OnlineAiAttachments.input(OnlineAiProtocol.GEMINI_GENERATE_CONTENT, vision = true)
        assertTrue(gemini.images && gemini.pdf)
        assertEquals(20L * MIB, gemini.maximumPdfBytes)
        assertEquals(20L * MIB, gemini.maximumRequestBytes)
        val anthropic = OnlineAiAttachments.input(OnlineAiProtocol.ANTHROPIC_MESSAGES, vision = false)
        assertFalse(anthropic.images)
        assertTrue(anthropic.pdf)
        assertEquals(32L * MIB, anthropic.maximumPdfBytes)
        assertEquals(20, anthropic.maximumRequestImages)

        val newline = 10.toChar()
        assertEquals("File: a b.txt\n<file_content>\nx\n</file_content>", OnlineAiAttachments.textEnvelope("a${newline}b.txt", "x"))
    }

    @Test
    fun `token estimates follow the roadmap formulas and keep text bytes separate`() {
        val unread = { ByteArray(0) }
        assertEquals(2_000L, GenerationAttachment(GenerationAttachmentKind.IMAGE, "i.jpg", "image/jpeg", 10, 1_500, 1_000, source = unread).estimatedTokens(1.0))
        assertEquals(1L, GenerationAttachment(GenerationAttachmentKind.IMAGE, "i.jpg", "image/jpeg", 10, 1, 1, source = unread).estimatedTokens(1.0))
        assertEquals(1_600L, GenerationAttachment(GenerationAttachmentKind.IMAGE, "i.jpg", "image/jpeg", 10, source = unread).estimatedTokens(1.0))
        assertEquals(6_000L, GenerationAttachment(GenerationAttachmentKind.PDF, "d.pdf", "application/pdf", 10, pageCount = 3, source = unread).estimatedTokens(1.0))
        assertEquals(2_000L, GenerationAttachment(GenerationAttachmentKind.PDF, "d.pdf", "application/pdf", 10, source = unread).estimatedTokens(1.0))
        assertEquals(73L, GenerationAttachment(GenerationAttachmentKind.TEXT, "a.txt", "text/plain", 100, source = unread).estimatedTokens(0.5))
        assertEquals(Long.MAX_VALUE, GenerationAttachment(GenerationAttachmentKind.TEXT, "a.txt", "text/plain", 100, source = unread).estimatedTokens(Double.MAX_VALUE))

        val estimator = ContextTokenEstimator(tokensPerUtf8Byte = 1.0, messageRoleOverheadTokens = 0)
        val plain = estimator.estimateMessages(listOf(GenerationMessage(GenerationRole.USER, listOf("read"))))
        val withPdf = estimator.estimateMessages(listOf(message("read", pdf(pages = 2))))
        assertEquals(plain.estimatedTokens + 4_000L, withPdf.estimatedTokens)
        assertEquals(plain.utf8Bytes, withPdf.utf8Bytes)

        val description = pdf().toString()
        assertFalse(description.contains("report"))
        assertFalse(description.contains(base64(pdfBytes)))
    }

    private fun content(provider: OnlineAiProvider, message: GenerationMessage, vision: Boolean = true): JsonArray {
        val json = prepare(provider, listOf(message), vision)
        return when (provider) {
            OnlineAiProvider.GEMINI -> json.getAsJsonArray("contents")[0].asJsonObject.getAsJsonArray("parts")
            else -> json.getAsJsonArray("messages")[0].asJsonObject.getAsJsonArray("content")
        }
    }

    private fun prepare(provider: OnlineAiProvider, messages: List<GenerationMessage>, vision: Boolean = true): JsonObject {
        val profile = profile(provider).let { if (vision) it else it.copy(visionModelIds = emptyList()) }
        val turn = GenerationRequest(messages.dropLast(1), messages.last(), 100, null, true)
        return OnlineAiProtocolAdapters.forProfile(profile).prepare(profile, messages, turn, "test-only".toByteArray()).use { prepared ->
            val body = checkNotNull(prepared.request.body)
            JsonParser.parseString(Buffer().also(body::writeTo).readUtf8()).asJsonObject
        }
    }

    private fun message(text: String, vararg attachments: GenerationAttachment) =
        GenerationMessage(GenerationRole.USER, listOf(text), attachments = attachments.toList())

    private fun text() = GenerationAttachment(GenerationAttachmentKind.TEXT, "data.csv", "text/plain", textBytes.size.toLong(), source = { textBytes })

    private fun pdf(pages: Int = 1) =
        GenerationAttachment(GenerationAttachmentKind.PDF, "report.pdf", "application/pdf", pdfBytes.size.toLong(), pageCount = pages, source = { pdfBytes })

    private fun image() =
        GenerationAttachment(GenerationAttachmentKind.IMAGE, "shot.png", "image/png", pngBytes.size.toLong(), 2, 2, source = { pngBytes })

    private fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun profile(provider: OnlineAiProvider) = OnlineAiProfile(
        "11111111-1111-4111-8111-111111111111",
        "Attachments",
        provider,
        "https://example.com/v1",
        "model",
        visionModelIds = listOf("model"),
    )

    private val providers = listOf(OnlineAiProvider.OPENAI_COMPATIBLE, OnlineAiProvider.ANTHROPIC, OnlineAiProvider.GEMINI)

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
