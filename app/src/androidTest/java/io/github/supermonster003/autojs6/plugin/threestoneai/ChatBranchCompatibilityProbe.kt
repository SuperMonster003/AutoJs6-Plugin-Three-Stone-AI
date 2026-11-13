package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ScriptedAiBackend
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Seeds and reads a conversation with versions for the downgrade check of roadmap P6 (D148,
 * `.python/device_branch_compat.py`). Skipped unless the run passes `-e branchProbe` with one of:
 *
 * - `seed`: stores [CONVERSATION_ID] with three versions of its first turn (the third shown, the
 *   first holding the only attachment) and the plain [PLAIN_ID], then runs the attachment sweep of
 *   the storage cleanup, which has to keep the file of the hidden version.
 * - `inspect`: reports what this build reads back (shown texts, versions, the hidden file).
 * - `clean`: deletes both conversations and their files.
 *
 * Only the test's own texts are reported; other conversations are not read beyond their IDs.
 */
class ChatBranchCompatibilityProbe {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val step = InstrumentationRegistry.getArguments().getString("branchProbe")

    @Test
    fun probe() {
        assumeTrue("needs -e branchProbe", step != null)
        when (step) {
            "seed" -> seed()
            "inspect" -> inspect()
            "clean" -> clean()
            else -> error("Unknown step $step")
        }
    }

    private fun seed() {
        val history = ConversationHistoryStore(context)
        val store = ChatAttachmentStore(context)
        history.delete(setOf(CONVERSATION_ID, PLAIN_ID))
        val now = System.currentTimeMillis()
        val target = ConversationTargetSnapshot.from(ScriptedAiBackend().target)
        val first = listOf(user(1, "Q", listOf(HIDDEN_FILE)), reply(2, "A1", target), user(3, "Q2"), reply(4, "B1", target))
        val second = listOf(user(5, "Q"), reply(6, "A2", target))
        val shown = listOf(
            user(7, "Q").copy(branches = ChatMessageBranches(2, listOf(first, second))),
            reply(8, "A3", target),
        )
        val plain = listOf(user(1, "P"), reply(2, "R", target))
        // Deleting above marks both IDs as deleted for this process; seeding them again revives them.
        check(history.upsert(StoredConversation(PLAIN_ID, "P6 plain", now - 1_000L, now - 1_000L, target, plain), reviveDeleted = true))
        check(history.upsert(StoredConversation(CONVERSATION_ID, "P6 versions", now, now, target, shown), reviveDeleted = true))
        // The store remembers the last conversation with apply(), which the end of the run may lose.
        check(
            context.getSharedPreferences(NAVIGATION_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_CONVERSATION_ID, CONVERSATION_ID).commit(),
        )
        val file = store.file(CONVERSATION_ID, HIDDEN_FILE)
        checkNotNull(file.parentFile).mkdirs()
        file.writeBytes(ByteArray(HIDDEN_FILE.sizeBytes.toInt()) { index -> index.toByte() })
        // Older than the grace period the sweep gives files committed a moment ago.
        check(file.setLastModified(now - 60L * 60L * 1_000L))
        val swept = store.sweep(store.references(history.loadAll()), emptySet(), now)
        assertTrue("the sweep keeps the file of the hidden version", file.isFile)
        report("seeded" to "true", "sweptFiles" to swept.files.toString())
        inspect()
    }

    private fun inspect() {
        val history = ConversationHistoryStore(context)
        val conversation = history.find(CONVERSATION_ID)
        val messages = conversation?.messages.orEmpty()
        val branches = messages.firstOrNull()?.branches
        report(
            "found" to (conversation != null).toString(),
            "plainFound" to (history.find(PLAIN_ID) != null).toString(),
            "shown" to messages.joinToString("|") { message -> message.text },
            "versions" to (branches?.count ?: if (messages.isEmpty()) 0 else 1).toString(),
            "selected" to (branches?.selected ?: 0).toString(),
            "hiddenFile" to ChatAttachmentStore(context).file(CONVERSATION_ID, HIDDEN_FILE).isFile.toString(),
            "allIds" to ChatBranchPolicy.allMessages(messages).map(ChatMessage::id).sorted().joinToString(","),
        )
    }

    private fun clean() {
        val removed = ConversationHistoryStore(context).delete(setOf(CONVERSATION_ID, PLAIN_ID))
        ChatAttachmentStore(context).deleteConversation(CONVERSATION_ID)
        report("removed" to removed.toString())
    }

    private fun report(vararg values: Pair<String, String>) {
        instrumentation.sendStatus(0, Bundle().apply { values.forEach { (key, value) -> putString("probe.$key", value) } })
    }

    private fun user(id: Long, text: String, attachments: List<ChatAttachment> = emptyList()) =
        ChatMessage(id, ChatMessageRole.USER, text, attachments = attachments)

    private fun reply(id: Long, text: String, target: ConversationTargetSnapshot) =
        ChatMessage(id, ChatMessageRole.ASSISTANT, text, target = target)

    companion object {
        const val CONVERSATION_ID = "p6-branch-compat"
        const val PLAIN_ID = "p6-branch-plain"
        private const val NAVIGATION_PREFERENCES = "conversation-navigation"
        private const val KEY_LAST_CONVERSATION_ID = "last-conversation-id"
        private val HIDDEN_FILE = ChatAttachment(
            "0123456789abcdef0123456789abcdef",
            ChatAttachmentKind.IMAGE,
            "hidden.jpg",
            ChatAttachmentLimits.MIME_JPEG,
            2_048L,
            width = 1,
            height = 1,
        )
    }
}
