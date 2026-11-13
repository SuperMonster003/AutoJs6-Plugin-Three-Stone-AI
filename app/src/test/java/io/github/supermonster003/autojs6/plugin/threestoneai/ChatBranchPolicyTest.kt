package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatBranchPolicyTest {
    @Test
    fun `a new version keeps the shown one and becomes the selected last version`() {
        val original = listOf(user(1, "Q"), reply(2, "A"), user(3, "Q2"), reply(4, "A2"))

        val second = checkNotNull(ChatBranchPolicy.withNewVersion(original, 1L, user(5, "Q edited"))) + reply(6, "B")

        assertEquals(listOf(5L, 6L), second.map(ChatMessage::id))
        assertEquals(ChatMessageBranches(1, listOf(original)), second.first().branches)
        assertEquals(2, ChatBranchPolicy.versionCount(second, 5L))

        val third = checkNotNull(ChatBranchPolicy.withNewVersion(second, 5L, user(7, "Q edited"))) + reply(8, "C")

        val branches = checkNotNull(third.first().branches)
        assertEquals(2, branches.selected)
        assertEquals(listOf(original, listOf(user(5, "Q edited"), reply(6, "B"))), branches.others)
        assertEquals(3, ChatBranchPolicy.versionCount(third, 7L))
        assertEquals(1, ChatBranchPolicy.versionCount(third, 8L))
        assertEquals(0, ChatBranchPolicy.versionCount(third, 1L))
        assertNull(ChatBranchPolicy.withNewVersion(third, 8L, user(9, "not a turn")))
        assertNull(ChatBranchPolicy.withNewVersion(third, 1L, user(9, "not shown")))
    }

    @Test
    fun `a turn takes at most eight versions`() {
        var path = listOf(user(1, "Q"), reply(2, "A"))
        var next = 3L
        repeat(ChatBranchPolicy.MAXIMUM_VERSIONS - 1) {
            path = checkNotNull(ChatBranchPolicy.withNewVersion(path, path.first().id, user(next++, "Q"))) + reply(next++, "A")
        }

        assertEquals(ChatBranchPolicy.MAXIMUM_VERSIONS, ChatBranchPolicy.versionCount(path, path.first().id))
        assertNull(ChatBranchPolicy.withNewVersion(path, path.first().id, user(next, "Q")))
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessageBranches(0, List(ChatBranchPolicy.MAXIMUM_VERSIONS) { index -> listOf(user(100L + index, "Q")) })
        }
    }

    @Test
    fun `switching shows the chosen version and switching back restores the path`() {
        val original = listOf(user(1, "Q"), reply(2, "A"), user(3, "Q2"), reply(4, "A2"))
        val second = checkNotNull(ChatBranchPolicy.withNewVersion(original, 1L, user(5, "Q"))) + reply(6, "B")
        val third = checkNotNull(ChatBranchPolicy.withNewVersion(second, 5L, user(7, "Q"))) + reply(8, "C")

        val first = checkNotNull(ChatBranchPolicy.withSelectedVersion(third, 7L, 0))

        assertEquals(listOf(1L, 2L, 3L, 4L), first.map(ChatMessage::id))
        assertEquals(0, first.first().branches?.selected)
        assertEquals(mapOf(2L to ChatBranchPosition(1L, 0, 3)), ChatBranchPolicy.positions(first))
        assertEquals(third, ChatBranchPolicy.withSelectedVersion(first, 1L, 2))
        assertNull(ChatBranchPolicy.withSelectedVersion(first, 1L, 0))
        assertNull(ChatBranchPolicy.withSelectedVersion(first, 1L, 3))
        assertNull(ChatBranchPolicy.withSelectedVersion(original, 1L, 0))
    }

    @Test
    fun `branches inside a version stay with that version`() {
        val original = listOf(user(1, "Q"), reply(2, "A"), user(3, "Q2"), reply(4, "A2"))
        val inner = checkNotNull(ChatBranchPolicy.withNewVersion(original, 3L, user(5, "Q2 edited"))) + reply(6, "B2")
        val outer = checkNotNull(ChatBranchPolicy.withNewVersion(inner, 1L, user(7, "Q edited"))) + reply(8, "B")

        assertEquals(mapOf(8L to ChatBranchPosition(7L, 1, 2)), ChatBranchPolicy.positions(outer))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), ChatBranchPolicy.allMessages(outer).map(ChatMessage::id).sorted())
        assertEquals(8L, ChatBranchPolicy.maximumId(outer))
        assertEquals(1L, ChatBranchPolicy.firstIdOfTurn(outer.first()))

        val back = checkNotNull(ChatBranchPolicy.withSelectedVersion(outer, 7L, 0))

        assertEquals(listOf(1L, 2L, 5L, 6L), back.map(ChatMessage::id))
        assertEquals(
            mapOf(2L to ChatBranchPosition(1L, 0, 2), 6L to ChatBranchPosition(5L, 1, 2)),
            ChatBranchPolicy.positions(back),
        )
        assertEquals(3L, ChatBranchPolicy.firstIdOfTurn(back[2]))
        assertEquals(listOf(1L, 2L, 3L, 4L), checkNotNull(ChatBranchPolicy.withSelectedVersion(back, 5L, 0)).map(ChatMessage::id))
    }

    @Test
    fun `deleting the shown version shows the one before it or the next for the first`() {
        val original = listOf(user(1, "Q"), reply(2, "A"))
        val second = checkNotNull(ChatBranchPolicy.withNewVersion(original, 1L, user(3, "Q"))) + reply(4, "B")
        val third = checkNotNull(ChatBranchPolicy.withNewVersion(second, 3L, user(5, "Q"))) + reply(6, "C")

        val withoutThird = checkNotNull(ChatBranchPolicy.withoutShownVersion(third, 5L))

        assertEquals(listOf(3L, 4L), withoutThird.map(ChatMessage::id))
        assertEquals(ChatMessageBranches(1, listOf(original)), withoutThird.first().branches)

        val firstShown = checkNotNull(ChatBranchPolicy.withSelectedVersion(third, 5L, 0))
        val withoutFirst = checkNotNull(ChatBranchPolicy.withoutShownVersion(firstShown, 1L))

        assertEquals(listOf(3L, 4L), withoutFirst.map(ChatMessage::id))
        assertEquals(0, withoutFirst.first().branches?.selected)

        val single = checkNotNull(ChatBranchPolicy.withoutShownVersion(withoutThird, 3L))

        assertEquals(original, single)
        assertNull(ChatBranchPolicy.withoutShownVersion(single, 1L))
    }

    @Test
    fun `the switch sits under the first reply or under the user message without one`() {
        val unanswered = checkNotNull(
            ChatBranchPolicy.withNewVersion(listOf(user(1, "Q"), reply(2, "A")), 1L, user(3, "Q")),
        )

        assertEquals(mapOf(3L to ChatBranchPosition(3L, 1, 2)), ChatBranchPolicy.positions(unanswered))
        assertEquals(
            mapOf(3L to ChatBranchPosition(3L, 1, 2)),
            ChatBranchPolicy.positions(unanswered + user(4, "next")),
        )
        assertEquals(emptyMap<Long, ChatBranchPosition>(), ChatBranchPolicy.positions(listOf(user(1, "Q"), reply(2, "A"))))
    }

    @Test
    fun `attachments of every version count once`() {
        val shared = image(1)
        val original = listOf(user(1, "Q", listOf(shared)), reply(2, "A"))
        val path = checkNotNull(ChatBranchPolicy.withNewVersion(original, 1L, user(3, "Q", listOf(shared, image(2))))) +
            reply(4, "B")

        assertEquals(setOf(shared.fileName, image(2).fileName), ChatBranchPolicy.attachmentFileNames(path))
        assertEquals(shared.sizeBytes + image(2).sizeBytes, ChatAttachmentLimits.conversationBytes(path))

        val other = checkNotNull(ChatBranchPolicy.withSelectedVersion(path, 3L, 0))

        assertEquals(setOf(shared.fileName, image(2).fileName), ChatBranchPolicy.attachmentFileNames(other))
    }

    @Test
    fun `sanitizing drops versions a damaged file could add and stops hidden generating replies`() {
        val hiddenGenerating = listOf(user(3, "Q"), reply(4, "partial", ChatMessageStatus.GENERATING))
        val repeatedId = listOf(user(2, "Q"), reply(5, "A"))
        val decreasing = listOf(user(7, "Q"), reply(6, "A"))
        val path = listOf(
            user(1, "Q").copy(branches = ChatMessageBranches(3, listOf(hiddenGenerating, repeatedId, decreasing))),
            reply(2, "A"),
        )

        val sanitized = ChatBranchPolicy.sanitized(path)

        val branches = checkNotNull(sanitized.first().branches)
        assertEquals(1, branches.selected)
        assertEquals(listOf(listOf(user(3, "Q"), reply(4, "partial", ChatMessageStatus.STOPPED))), branches.others)
        assertEquals(sanitized, ChatBranchPolicy.sanitized(sanitized))

        val allBroken = listOf(user(1, "Q").copy(branches = ChatMessageBranches(0, listOf(repeatedId))), reply(2, "A"))

        assertEquals(listOf(user(1, "Q"), reply(2, "A")), ChatBranchPolicy.sanitized(allBroken))
        val plain = listOf(user(1, "Q"), reply(2, "A"))
        assertSame(plain, ChatBranchPolicy.sanitized(plain))
    }

    @Test
    fun `fitting drops the oldest other versions of the earliest turn and never the shown path`() {
        var path = listOf(user(1, "Q"), reply(2, "x".repeat(100)))
        var next = 3L
        repeat(3) {
            path = checkNotNull(ChatBranchPolicy.withNewVersion(path, path.first().id, user(next++, "Q"))) +
                reply(next++, "x".repeat(100))
        }
        val bytesOf = { message: ChatMessage -> message.text.length }
        assertEquals(3L * 101L, ChatBranchPolicy.branchBytes(path, bytesOf))

        val fitted = ChatBranchPolicy.fitted(path, 250, bytesOf)

        assertEquals(path.map(ChatMessage::id), fitted.map(ChatMessage::id))
        val branches = checkNotNull(fitted.first().branches)
        assertEquals(listOf(3L, 4L), branches.others.first().map(ChatMessage::id))
        assertEquals(2, branches.others.size)
        assertEquals(2, branches.selected)
        assertSame(path, ChatBranchPolicy.fitted(path, 1_000, bytesOf))
        assertNull(ChatBranchPolicy.fitted(path, 0, bytesOf).first().branches)
        assertNotNull(ChatBranchPolicy.withoutOldestOther(checkNotNull(path.first().branches)))
    }

    private fun user(id: Long, text: String, attachments: List<ChatAttachment> = emptyList()) =
        ChatMessage(id, ChatMessageRole.USER, text, attachments = attachments)

    private fun reply(id: Long, text: String, status: ChatMessageStatus = ChatMessageStatus.COMPLETE) =
        ChatMessage(id, ChatMessageRole.ASSISTANT, text, status = status, target = target)

    private val target = ConversationTargetSnapshot(
        targetId = "local:test-model",
        providerId = "autojs6.three-stone-ai",
        modelId = "test-model",
        displayName = "Test model",
        locality = AiTargetLocality.LOCAL,
    )

    private fun image(index: Int) = ChatAttachment(
        index.toString(16).padStart(32, '0'),
        ChatAttachmentKind.IMAGE,
        "image$index.jpg",
        ChatAttachmentLimits.MIME_JPEG,
        1_000L * index,
    )
}
