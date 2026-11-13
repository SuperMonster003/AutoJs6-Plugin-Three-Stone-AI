package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.DialogInterface
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechDeviceReport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Roadmap P6.1 - P6.3 (D82, D148): regenerating or editing adds a version of the turn, "< 2/3 >"
 * switches it together with the later messages, the choice survives reopening, the next turn
 * replays only the version shown, deleting removes only the version shown, and a turn stops at
 * eight versions with a notice.
 */
class ChatBranchDeviceTest {
    private lateinit var environment: ChatTestEnvironment

    @Before
    fun setUp() {
        environment = ChatTestEnvironment()
    }

    @After
    fun tearDown() {
        environment.backend.release()
        environment.close()
    }

    @Test
    fun versionsSwitchSurviveReopeningAndAreDeletedOneAtATime() {
        val backend = environment.backend
        var conversationId = ""
        environment.launchChat().use { scenario ->
            turn(scenario, "A1") { send(scenario, "Q") }
            turn(scenario, "B1") { send(scenario, "Q2") }
            val original = shown(scenario)
            assertEquals(listOf("Q", "A1", "Q2", "B1"), original.map(ChatMessage::text))

            turn(scenario, "A2") { choose(scenario, original[1].id, R.string.chat_message_regenerate) }
            assertEquals(listOf("Q", "A2"), texts(scenario))
            assertEquals(listOf(1 to 2), switches(scenario))
            turn(scenario, "A3") { choose(scenario, shown(scenario)[1].id, R.string.chat_message_regenerate) }
            assertEquals(listOf("Q", "A3"), texts(scenario))
            assertEquals(listOf(2 to 3), switches(scenario))
            SpeechDeviceReport.screenshot("chat-branch-switch")

            clickSwitch(scenario, next = false)
            waitUntil("the second version shows") { texts(scenario) == listOf("Q", "A2") }
            clickSwitch(scenario, next = false)
            waitUntil("the first version shows with its later messages") {
                texts(scenario) == listOf("Q", "A1", "Q2", "B1")
            }
            assertEquals(listOf(0 to 3), switches(scenario))
            assertTrue("the later messages are drawn again", displays(scenario, "B1"))
            assertFalse("the hidden reply is not drawn", displays(scenario, "A3"))
            assertEquals(original.map(ChatMessage::id), shown(scenario).map(ChatMessage::id))

            // The next turn replays the version shown, not the newest one (context compile, D148).
            turn(scenario, "C1") { send(scenario, "Q3") }
            val replayed = backend.histories.last()
            assertEquals(listOf("Q", "A1", "Q2", "B1"), replayed.takeLast(4))
            assertTrue(replayed.none { text -> text == "A2" || text == "A3" })
            conversationId = conversationId(scenario)
        }

        val stored = checkNotNull(ConversationHistoryStore(environment.context).find(conversationId))
        assertEquals(ChatMessageBranches(0, stored.messages.first().branches!!.others), stored.messages.first().branches)
        assertEquals(3, stored.messages.first().branches!!.count)

        environment.launchChat().use { scenario ->
            assertEquals(conversationId, conversationId(scenario))
            waitUntil("the reopened conversation keeps the chosen version") {
                texts(scenario) == listOf("Q", "A1", "Q2", "B1", "Q3", "C1")
            }
            assertEquals(listOf(0 to 3), switches(scenario))

            // Deleting the first version shows the next one; the others stay (P6.1).
            choose(scenario, shown(scenario)[0].id, R.string.chat_message_delete)
            scenario.onActivity { activity ->
                checkNotNull(activity.deleteVersionDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            waitUntil("the next version shows") { texts(scenario) == listOf("Q", "A2") }
            assertEquals(listOf(0 to 2), switches(scenario))

            // Editing and sending again adds a version too.
            val question = shown(scenario)[0]
            choose(scenario, question.id, R.string.chat_message_edit)
            scenario.onActivity { activity -> assertEquals("Q", composer(activity).text.toString()) }
            turn(scenario, "D1") { send(scenario, "Q edited") }
            assertEquals(listOf("Q edited", "D1"), texts(scenario))
            assertEquals(listOf(2 to 3), switches(scenario))
            assertTrue(shown(scenario)[0].id > question.id)
            // The versions are now (Q, A2), (Q, A3) and the edited one.
            clickSwitch(scenario, next = false)
            waitUntil("the version before the edited one shows") { texts(scenario) == listOf("Q", "A3") }
            clickSwitch(scenario, next = true)
            waitUntil("the edited version shows again") { texts(scenario) == listOf("Q edited", "D1") }
        }
        SpeechDeviceReport.receipt(
            "chat-branches",
            mapOf("regenerate" to "pass", "switch" to "pass", "reopen" to "pass", "context" to "pass", "delete" to "pass", "edit" to "pass"),
        )
    }

    @Test
    fun aTurnStopsAtEightVersionsWithANotice() {
        val backend = environment.backend
        environment.launchChat().use { scenario ->
            turn(scenario, "R0") { send(scenario, "Q") }
            repeat(ChatBranchPolicy.MAXIMUM_VERSIONS - 1) { index ->
                turn(scenario, "R${index + 1}") { choose(scenario, shown(scenario)[1].id, R.string.chat_message_regenerate) }
            }
            assertEquals(listOf(7 to 8), switches(scenario))
            val before = shown(scenario)
            val prompts = backend.prompts.size

            choose(scenario, before[1].id, R.string.chat_message_regenerate)
            val notice = string(scenario, R.string.chat_branch_limit, ChatBranchPolicy.MAXIMUM_VERSIONS)
            waitUntil("the limit is shown") { displays(scenario, notice) }
            choose(scenario, before[0].id, R.string.chat_message_edit)
            scenario.onActivity { activity ->
                assertEquals("editing does not start", "", composer(activity).text.toString())
            }
            assertEquals(prompts, backend.prompts.size)
            assertEquals(before, shown(scenario))
        }
        SpeechDeviceReport.receipt("chat-branch-limit", mapOf("versions" to 8, "limit" to "pass"))
    }

    /** Runs [action] with the backend answering [reply] and waits until that reply is complete. */
    private fun turn(scenario: ActivityScenario<ChatActivity>, reply: String, action: () -> Unit) {
        val backend = environment.backend
        backend.reply = listOf(reply)
        val prompts = backend.prompts.size
        action()
        waitUntil("the turn is sent") { backend.prompts.size == prompts + 1 }
        waitUntil("the reply $reply completes") {
            var done = false
            scenario.onActivity { activity ->
                val last = activity.messagesForTest.lastOrNull()
                done = !activity.generatingForTest && last?.text == reply && last.status == ChatMessageStatus.COMPLETE
            }
            done
        }
    }

    private fun send(scenario: ActivityScenario<ChatActivity>, text: String) {
        scenario.onActivity { activity ->
            composer(activity).setText(text)
            activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
        }
    }

    private fun choose(scenario: ActivityScenario<ChatActivity>, messageId: Long, label: Int) {
        scenario.onActivity { activity ->
            activity.showMessageActionsForTest(messageId)
            val dialog = checkNotNull(activity.messageActionsDialog)
            val adapter = dialog.listView.adapter
            val wanted = activity.getString(label)
            val index = (0 until adapter.count).first { adapter.getItem(it).toString() == wanted }
            dialog.listView.performItemClick(null, index, adapter.getItemId(index))
            dialog.dismiss()
        }
    }

    private fun clickSwitch(scenario: ActivityScenario<ChatActivity>, next: Boolean) {
        scenario.onActivity { activity ->
            val switchView = activity.branchSwitchesForTest().values.single()
            switchView.getChildAt(if (next) 2 else 0).performClick()
        }
    }

    /** The version switches on screen as (0-based shown version, version count). */
    private fun switches(scenario: ActivityScenario<ChatActivity>): List<Pair<Int, Int>> {
        var positions = emptyList<Pair<Int, Int>>()
        scenario.onActivity { activity ->
            positions = activity.branchSwitchesForTest().values.map { view ->
                val position = checkNotNull(view.position)
                position.index to position.count
            }
        }
        return positions
    }

    private fun shown(scenario: ActivityScenario<ChatActivity>): List<ChatMessage> {
        var messages = emptyList<ChatMessage>()
        scenario.onActivity { activity -> messages = activity.messagesForTest }
        return messages
    }

    private fun texts(scenario: ActivityScenario<ChatActivity>): List<String> = shown(scenario).map(ChatMessage::text)

    private fun displays(scenario: ActivityScenario<ChatActivity>, text: String): Boolean {
        var found = false
        scenario.onActivity { activity ->
            found = views(activity.window.decorView).filterIsInstance<TextView>()
                .any { view -> view.isShown && view.text.toString().trim() == text }
        }
        return found
    }

    private fun conversationId(scenario: ActivityScenario<ChatActivity>): String {
        var id = ""
        scenario.onActivity { activity -> id = activity.conversationIdForTest }
        return id
    }

    /** Strings as the screen shows them (the app language may differ from the system one). */
    private fun string(scenario: ActivityScenario<ChatActivity>, resource: Int, vararg arguments: Any): String {
        var text = ""
        scenario.onActivity { activity -> text = activity.getString(resource, *arguments) }
        return text
    }
}
