package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkdownExportTest {
    @Test
    fun `a conversation becomes headings, metadata, attachment names and escaped plain text`() {
        val text = format(sampleConversation(), includeReasoning = false)

        val expected = """
            |# Plan # trip
            |
            |- **Model:** Test Model
            |- **Created:** T1000
            |- **Updated:** T2000
            |- **Exported:** T3000 (3-Stone AI 1.7.0)
            |
            |## You
            |
            |**Attachments:**
            |
            |- photo.png
            |- 1\. notes.txt
            |
            |Hi \*there\*$HARD_BREAK
            |second line
            |
            |## Assistant (Test Model)
            |
            |Here:
            |
            |```kotlin
            |val x = 1
            |```
            |
            |> Model switched
            |
            |## You
            |
            |\# not a heading
            |
            |## Assistant (Test Model)
            |
            |*Generation failed*
            |
        """.trimMargin()
        assertEquals(expected, text)
    }

    @Test
    fun `reasoning is written as a quote only when it is included`() {
        val conversation = sampleConversation()
        assertTrue(ConversationMarkdownExport.hasReasoning(conversation))

        val without = format(conversation, includeReasoning = false)
        val with = format(conversation, includeReasoning = true)

        assertFalse(without.contains("Think"))
        assertFalse(without.contains("Thought for"))
        val quote = """
            |## Assistant (Test Model)
            |
            |> **Thought for 2 s**
            |>
            |> Think \<a\>$HARD_BREAK
            |> step two
            |
            |Here:
        """.trimMargin()
        assertTrue(with, with.contains(quote))
        assertFalse(ConversationMarkdownExport.hasReasoning(conversation.copy(messages = listOf(user(1, "Q"), reply(2, "A")))))
    }

    @Test
    fun `reasoning without a duration and cut reasoning use their own labels`() {
        val conversation = conversation(
            listOf(user(1, "Q"), reply(2, "A", reasoning = ChatMessageReasoning("R", durationMillis = null, truncated = true))),
        )

        val text = format(conversation, includeReasoning = true)

        val quote = """
            |> **Reasoning**
            |>
            |> R
            |>
            |> *Reasoning was cut*
        """.trimMargin()
        assertTrue(text, text.contains(quote))
    }

    @Test
    fun `only the versions shown are exported`() {
        val first = listOf(user(1, "Question v1"), reply(2, "Answer v1"), user(3, "Follow-up v1"), reply(4, "Reply v1"))
        val second = checkNotNull(ChatBranchPolicy.withNewVersion(first, 1L, user(5, "Question v2"))) + reply(6, "Answer v2")

        val shownSecond = format(conversation(second), includeReasoning = false)
        val shownFirst = format(conversation(checkNotNull(ChatBranchPolicy.withSelectedVersion(second, 5L, 0))), includeReasoning = false)

        assertTrue(shownSecond.contains("Question v2") && shownSecond.contains("Answer v2"))
        listOf("Question v1", "Answer v1", "Follow-up v1", "Reply v1").forEach { hidden -> assertFalse(hidden, shownSecond.contains(hidden)) }
        listOf("Question v1", "Answer v1", "Follow-up v1", "Reply v1").forEach { shown -> assertTrue(shown, shownFirst.contains(shown)) }
        assertFalse(shownFirst.contains("v2"))
    }

    @Test
    fun `plain text keeps its characters, indentation and line breaks`() {
        assertEquals("a\\_b \\| c \\~ d \\& e \\[x\\](y) \\`code\\` \\*\\*", ConversationMarkdownExport.plain("a_b | c ~ d & e [x](y) `code` **"))
        assertEquals("&nbsp;&nbsp;indented  \n&nbsp;&nbsp;&nbsp;&nbsp;tab", ConversationMarkdownExport.plain("  indented\n\ttab"))
        assertEquals("line 1\n\nline 3", ConversationMarkdownExport.plain("line 1\r\n\r\nline 3\n\n"))
        assertEquals("\\- item  \n12\\) twelve  \n\\> quote  \n\\+ plus  \n\\===", ConversationMarkdownExport.plain("- item\n12) twelve\n> quote\n+ plus\n==="))
        assertEquals("a b", ConversationMarkdownExport.inline("a\n  b"))
        assertEquals("C\\#", format(conversation(listOf(user(1, "x")), title = "C#"), includeReasoning = false).lines().first().removePrefix("# "))
    }

    @Test
    fun `an unclosed code fence is closed and other fences are left alone`() {
        assertEquals("```js\nlet a\n```", ConversationMarkdownExport.closeOpenFence("```js\nlet a"))
        assertEquals("~~~~\nx\n~~~\n~~~~", ConversationMarkdownExport.closeOpenFence("~~~~\nx\n~~~"))
        assertEquals("  ````\nx\n  ````", ConversationMarkdownExport.closeOpenFence("  ````\nx\n  ````"))
        assertEquals("```\na\n```\n- ```py\n  b", ConversationMarkdownExport.closeOpenFence("```\na\n```\n- ```py\n  b"))
        assertEquals("```\na\n```\n  ```py\n  b\n  ```", ConversationMarkdownExport.closeOpenFence("```\na\n```\n  ```py\n  b"))
        assertEquals("``` a`b\nnot code", ConversationMarkdownExport.closeOpenFence("``` a`b\nnot code"))
        assertEquals("```\n~~~\n```", ConversationMarkdownExport.closeOpenFence("```\n~~~\n```"))

        val stopped = conversation(listOf(user(1, "Q"), reply(2, "```py\nprint(1)", status = ChatMessageStatus.STOPPED)))
        assertTrue(format(stopped, includeReasoning = false).endsWith("```py\nprint(1)\n```\n\n*Generation stopped*\n"))
    }

    @Test
    fun `a cut reply and an empty reply get a note`() {
        val truncated = format(conversation(listOf(user(1, "Q"), reply(2, "A", truncated = true))), includeReasoning = false)
        val empty = format(conversation(listOf(user(1, "Q"), reply(2, ""))), includeReasoning = false)

        assertTrue(truncated.endsWith("A\n\n*Output limit reached*\n"))
        assertTrue(empty.endsWith("*Empty response*\n"))
    }

    @Test
    fun `file names drop characters file systems refuse`() {
        assertEquals("a b c.md", ConversationMarkdownExport.fileName("a/b:c*?", "fallback"))
        assertEquals("fallback.md", ConversationMarkdownExport.fileName(" ... ", "fallback"))
        assertEquals("x".repeat(80) + ".md", ConversationMarkdownExport.fileName("x".repeat(100), "fallback"))
        val emoji = String(Character.toChars(0x1F600))
        assertEquals(emoji.repeat(80) + ".md", ConversationMarkdownExport.fileName(emoji.repeat(81), "fallback"))
        assertEquals("tab line.md", ConversationMarkdownExport.fileName("tab\tline\n", "fallback"))
    }

    private fun format(conversation: StoredConversation, includeReasoning: Boolean) = ConversationMarkdownExport.format(
        conversation = conversation,
        labels = labels,
        includeReasoning = includeReasoning,
        formatTime = { millis -> "T$millis" },
        exportedAtMillis = 3_000L,
        appVersion = "1.7.0",
    )

    private fun sampleConversation() = conversation(
        listOf(
            user(
                1,
                "Hi *there*\nsecond line",
                listOf(
                    ChatAttachment("a".repeat(32), ChatAttachmentKind.IMAGE, "photo.png", "image/png", 10, width = 4, height = 4),
                    ChatAttachment("b".repeat(32), ChatAttachmentKind.TEXT, "1. notes.txt", "text/plain", 10),
                ),
            ),
            reply(2, "Here:\n\n```kotlin\nval x = 1\n```\n", reasoning = ChatMessageReasoning("Think <a>\nstep two", 2_400L)),
            ChatMessage(3, ChatMessageRole.NOTICE, "Model switched"),
            user(4, "# not a heading"),
            reply(5, "", status = ChatMessageStatus.FAILED),
        ),
        title = "Plan # trip",
    )

    private fun conversation(messages: List<ChatMessage>, title: String = "Title") = StoredConversation(
        id = "conversation",
        title = title,
        createdAtMillis = 1_000L,
        updatedAtMillis = 2_000L,
        target = target,
        messages = messages,
    )

    private fun user(id: Long, text: String, attachments: List<ChatAttachment> = emptyList()) =
        ChatMessage(id, ChatMessageRole.USER, text, attachments = attachments)

    private fun reply(
        id: Long,
        text: String,
        status: ChatMessageStatus = ChatMessageStatus.COMPLETE,
        reasoning: ChatMessageReasoning? = null,
        truncated: Boolean = false,
    ) = ChatMessage(id, ChatMessageRole.ASSISTANT, text, status = status, target = target, reasoning = reasoning, truncated = truncated)

    private val target = ConversationTargetSnapshot(
        targetId = "local:test-model",
        providerId = "autojs6.three-stone-ai",
        modelId = "test-model",
        displayName = "Test Model",
        locality = AiTargetLocality.LOCAL,
    )

    private val labels = MarkdownExportLabels(
        user = "You",
        assistant = "Assistant",
        model = "Model",
        created = "Created",
        updated = "Updated",
        exported = "Exported",
        attachments = "Attachments",
        reasoning = "Reasoning",
        reasoningDuration = { seconds -> "Thought for $seconds s" },
        reasoningTruncated = "Reasoning was cut",
        stopped = "Generation stopped",
        failed = "Generation failed",
        truncated = "Output limit reached",
        emptyResponse = "Empty response",
    )

    private companion object {
        /** Two trailing spaces: a Markdown hard line break. */
        const val HARD_BREAK = "  "
    }
}
