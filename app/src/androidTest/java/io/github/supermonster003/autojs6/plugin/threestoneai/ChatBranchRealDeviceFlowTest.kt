package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Message versions against a real service (roadmap P6.3, D148). Skipped unless the run passes
 * `-e realBranches true`; `-e service` (the service's display name) and `-e model` pick the target.
 *
 * Sends one short question in a new conversation and regenerates the reply twice (three requests),
 * switches between 1/3 and 3/3, leaves 1/3 shown and checks after reopening that the choice and all
 * three versions are kept. The conversation is deleted and the default target and the preferences
 * the flow touches are restored. Only statuses, lengths, counts and positions are logged.
 */
class ChatBranchRealDeviceFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val application get() = context.applicationContext as ThreeStoneAiApplication
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun regenerateTwiceSwitchAndReopen() {
        assumeTrue("Opt-in: -e realBranches true", arguments.getString("realBranches") == "true")
        val serviceName = checkNotNull(arguments.getString("service")) { "-e service is required" }
        val modelId = checkNotNull(arguments.getString("model")) { "-e model is required" }
        val target = targetMatching("a ready target $modelId of $serviceName") { target ->
            target.profileId != null && target.modelId == modelId && profileName(target) == serviceName &&
                TargetPickerTexts.isUsable(target)
        }
        val backup = takeBackup()
        var conversationId = ""
        var requests = 0
        try {
            ChatTestEnvironment(scripted = false).use { environment ->
                var firstReplyId = 0L
                environment.launchChat().use { scenario ->
                    val chat = AttachmentChatDriver(scenario)
                    chat.onChat { activity -> activity.useTargetForTest(target.targetId) }
                    chat.send(PROMPT)
                    requests++
                    var reply = finished(chat, previousId = null)
                    firstReplyId = reply.id
                    conversationId = chat.conversationId()
                    repeat(2) { index ->
                        chat.onChat { activity -> choose(activity, reply.id, R.string.chat_message_regenerate) }
                        requests++
                        reply = finished(chat, previousId = reply.id)
                        assertEquals(listOf(index + 1 to index + 2), switches(chat))
                    }
                    assertEquals("a version keeps its target", target.targetId, reply.target?.targetId)

                    click(chat, next = false, times = 2)
                    waitUntil("1/3 shows the first reply") { switches(chat) == listOf(0 to 3) && lastReply(chat).id == firstReplyId }
                    click(chat, next = true, times = 2)
                    waitUntil("3/3 shows the last reply") { switches(chat) == listOf(2 to 3) && lastReply(chat).id == reply.id }
                    click(chat, next = false, times = 2)
                    waitUntil("1/3 shows again") { switches(chat) == listOf(0 to 3) }
                    log("switch: 1/3 -> 3/3 -> 1/3 pass")
                }
                environment.launchChat().use { scenario ->
                    val chat = AttachmentChatDriver(scenario)
                    assertEquals(conversationId, chat.conversationId())
                    waitUntil("the reopened conversation keeps 1/3") { switches(chat) == listOf(0 to 3) }
                    assertEquals(firstReplyId, lastReply(chat).id)
                    val stored = checkNotNull(ConversationHistoryStore(context).find(conversationId))
                    assertEquals(3, stored.messages.first().branches?.count)
                    assertEquals(0, stored.messages.first().branches?.selected)
                    log("reopen: 1/3 kept, versions stored 3")
                }
            }
        } finally {
            backup.restore()
            log("requests: $requests")
        }
        assertFalse("the test conversation is gone", ConversationHistoryStore(context).find(conversationId) != null)
        assertEquals("the default target is back", backup.defaultTarget, storedDefault())
    }

    /** Waits for the reply after [previousId] to finish and logs its status and length, not its text. */
    private fun finished(chat: AttachmentChatDriver, previousId: Long?): ChatMessage {
        val started = SystemClock.uptimeMillis()
        waitUntil("the reply finishes", REPLY_TIMEOUT_MILLIS) {
            chat.onChat { activity ->
                val last = activity.messagesForTest.lastOrNull()
                !activity.generatingForTest && last != null && last.role == ChatMessageRole.ASSISTANT &&
                    last.id != previousId && last.status != ChatMessageStatus.GENERATING
            }
        }
        val reply = lastReply(chat)
        log("reply: ${reply.status} after ${(SystemClock.uptimeMillis() - started) / 1_000L} s, characters ${reply.text.length}")
        assertEquals("the reply completed", ChatMessageStatus.COMPLETE, reply.status)
        assertTrue(reply.text.isNotBlank())
        return reply
    }

    private fun lastReply(chat: AttachmentChatDriver): ChatMessage =
        chat.messages().last { message -> message.role == ChatMessageRole.ASSISTANT }

    private fun choose(activity: ChatActivity, messageId: Long, label: Int) {
        activity.showMessageActionsForTest(messageId)
        val dialog = checkNotNull(activity.messageActionsDialog)
        val adapter = dialog.listView.adapter
        val wanted = activity.getString(label)
        val index = (0 until adapter.count).first { adapter.getItem(it).toString() == wanted }
        dialog.listView.performItemClick(null, index, adapter.getItemId(index))
        dialog.dismiss()
    }

    private fun click(chat: AttachmentChatDriver, next: Boolean, times: Int) {
        repeat(times) {
            val before = switches(chat)
            chat.onChat { activity ->
                activity.branchSwitchesForTest().values.single().getChildAt(if (next) 2 else 0).performClick()
            }
            waitUntil("the version changes") { switches(chat) != before }
        }
    }

    private fun switches(chat: AttachmentChatDriver): List<Pair<Int, Int>> = chat.onChat { activity ->
        activity.branchSwitchesForTest().values.map { view -> checkNotNull(view.position).let { it.index to it.count } }
    }

    private fun profileName(target: AiTarget): String? = application.onlineProfileRegistry.snapshot().profiles
        .firstOrNull { configured -> configured.profile.profileId == target.profileId }?.profile?.displayName

    private fun targetMatching(what: String, predicate: (AiTarget) -> Boolean): AiTarget {
        var found: AiTarget? = null
        val deadline = SystemClock.uptimeMillis() + PAGE_TIMEOUT_MILLIS
        while (found == null && SystemClock.uptimeMillis() < deadline) {
            found = application.aiBackend.catalog().targets.firstOrNull(predicate)
            if (found == null) SystemClock.sleep(200L)
        }
        assumeTrue("No $what on this device", found != null)
        return checkNotNull(found)
    }

    private fun takeBackup(): Backup {
        // The catalog migrates the default target first, so reading it never stores anything.
        application.aiBackend.catalog()
        return Backup(
            defaultTarget = storedDefault(),
            preferences = PREFERENCE_FILES.associateWith { name -> preferences(name).all.toMap() },
        )
    }

    private inner class Backup(val defaultTarget: String?, private val preferences: Map<String, Map<String, *>>) {
        fun restore() {
            application.defaultTargetStore.write(defaultTarget)
            preferences.forEach { (name, values) ->
                preferences(name).edit().clear().apply {
                    values.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }.commit()
            }
        }
    }

    private fun preferences(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun storedDefault(): String? =
        application.defaultTargetStore.readOrMigrate { error("the default target was not migrated") }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "P6Branches"
        const val PAGE_TIMEOUT_MILLIS = 30_000L
        const val REPLY_TIMEOUT_MILLIS = 180_000L
        const val PROMPT = "Reply with one short sentence about the sea."
        val PREFERENCE_FILES = listOf(
            "model_picker",
            "online-ai-base-url-history",
            "context-token-calibration",
            "conversation-navigation",
        )
    }
}
