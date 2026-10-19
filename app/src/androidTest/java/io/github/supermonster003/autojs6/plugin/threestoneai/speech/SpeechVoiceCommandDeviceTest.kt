package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.showsText
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import io.github.supermonster003.autojs6.plugin.threestoneai.voice.VoiceCommandActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.voice.VoiceCommandPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.voice.VoiceCommandStore
import io.github.supermonster003.autojs6.plugin.threestoneai.voice.VoiceCommandInbox
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P5-5: the `VOICE_COMMAND` entry started like 3-Macaw Speech does (explicit component, new task)
 * on a cold and a running chat, while generating, while editing, without a target, twice within
 * 10 s and without text. The engine is the silent fake.
 */
class SpeechVoiceCommandDeviceTest {
    private lateinit var environment: ChatTestEnvironment
    private val values = linkedMapOf<String, Any?>()

    @Before
    fun setUp() {
        environment = ChatTestEnvironment()
        FakeSpeechEngine.install()
        SpeechDeviceProbe.stateOverride = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = false,
            anyTtsEngine = true,
        )
        VoiceCommandStore(environment.context).lastImmediateSendAtMillis = null
        environment.backend.reply = listOf("Sure. ", "Here it is.")
        FakeSpeechEngine.hold = true
    }

    @After
    fun tearDown() {
        finishChats()
        FakeSpeechEngine.uninstall()
        environment.close()
    }

    @Test
    fun aColdStartSendsInANewConversationAndReadsInTheSpokenLanguage() {
        voiceCommand("What time is it?", "en-US")
        resumedChat()
        waitUntil("the command is sent") { environment.backend.prompts.toList() == listOf("What time is it?") }
        waitUntil("the answer is read") { onChat { it.readingMessageId != null } && FakeSpeechEngine.spokenTexts().isNotEmpty() }
        assertEquals("en-US", FakeSpeechEngine.engines.last().languagesTried.first())
        assertEquals(2, onChat { it.messageCountForTest })
        assertEquals(VoicePhase.IDLE, onChat { it.currentVoicePhase })
        values["coldStart"] = "pass"
        values["language"] = FakeSpeechEngine.engines.last().languagesTried.first()
        SpeechDeviceReport.receipt("voice-command-cold", values)
    }

    @Test
    fun aRunningChatStopsTheReplyAndStartsANewConversation() {
        environment.launchChat().use { scenario ->
            FakeSpeechEngine.hold = false
            environment.backend.hold = true
            send(scenario, "typed question")
            waitUntil("the typed reply is generating") { environment.backend.prompts.size == 1 }
            val before = onChat { it.conversationIdForTest }
            voiceCommand("Spoken question", null)
            waitUntil("the command is sent") { environment.backend.prompts.size == 2 }
            assertEquals(listOf("typed question", "Spoken question"), environment.backend.prompts.toList())
            assertNotEquals(before, onChat { it.conversationIdForTest })
            assertEquals(2, onChat { it.messageCountForTest })
            environment.backend.hold = false
            environment.backend.release()
            values["warmStartWhileGenerating"] = "pass"

            // Editing a message is canceled first.
            waitUntil("the reply ends") { onChat { activity -> !isGenerating(activity) } }
            onChat { activity ->
                val editing = ChatActivity::class.java.getDeclaredMethod("enterEditing", Long::class.javaPrimitiveType)
                editing.isAccessible = true
                editing.invoke(activity, 1L)
            }
            assertEquals(1L, editingMessageId())
            waitUntil("ten seconds since the last immediate send pass", timeoutMillis = 15_000L) {
                VoiceCommandPolicy.sendAllowed(
                    android.os.SystemClock.elapsedRealtime(),
                    VoiceCommandStore(environment.context).lastImmediateSendAtMillis,
                )
            }
            voiceCommand("While editing", null)
            waitUntil("the command is sent") { environment.backend.prompts.size == 3 }
            assertNull(editingMessageId())
            values["editingCanceled"] = "pass"
        }
        SpeechDeviceReport.receipt("voice-command-warm", values)
    }

    @Test
    fun withoutATargetTheTextOnlyFillsTheComposer() {
        environment.backend.available = false
        voiceCommand("No model yet", null)
        resumedChat()
        waitUntil("the composer is filled") { onChat { composer(it).text.toString() == "No model yet" } }
        waitUntil("the reason is shown") {
            onChat { activity -> showsText(activity, activity.getString(R.string.speech_voice_command_no_target)) }
        }
        assertTrue(environment.backend.prompts.isEmpty())
        SpeechDeviceReport.receipt("voice-command-no-target", mapOf("prefilled" to "pass"))
    }

    @Test
    fun aSecondCommandWithinTenSecondsOnlyFillsTheComposer() {
        voiceCommand("First", null)
        resumedChat()
        waitUntil("the first command is sent") { environment.backend.prompts.size == 1 }
        voiceCommand("Second", null)
        waitUntil("the second command fills the composer") { onChat { composer(it).text.toString() == "Second" } }
        waitUntil("the reason is shown") {
            onChat { activity -> showsText(activity, activity.getString(R.string.speech_voice_command_rate_limited)) }
        }
        assertEquals(listOf("First"), environment.backend.prompts.toList())
        SpeechDeviceReport.receipt("voice-command-rate-limit", mapOf("prefilled" to "pass"))
    }

    @Test
    fun withoutTextTheChatOnlyComesToTheFront() {
        environment.launchChat().use {
            val before = onChat { it.conversationIdForTest }
            voiceCommand(null, null)
            voiceCommand(" \n ", "en-US")
            // The chat's own launcher intent cannot send: the inbox is empty.
            environment.context.startActivity(
                Intent(environment.context, ChatActivity::class.java)
                    .putExtra(VoiceCommandInbox.EXTRA_CHECK_INBOX, true)
                    .putExtra(Intent.EXTRA_TEXT, "Injected")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            environment.instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(1_000L)
            environment.instrumentation.waitForIdleSync()
            assertEquals(before, onChat { it.conversationIdForTest })
            assertTrue(environment.backend.prompts.isEmpty())
        }
        SpeechDeviceReport.receipt("voice-command-no-text", mapOf("openedOnly" to "pass", "launcherIntentIgnored" to "pass"))
    }

    private fun voiceCommand(text: String?, language: String?) {
        val intent = Intent(VoiceCommandPolicy.ACTION)
            .setClassName(environment.context.packageName, VoiceCommandActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        text?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
        language?.let { intent.putExtra(VoiceCommandPolicy.EXTRA_VOICE_LANGUAGE, it) }
        environment.context.startActivity(intent)
        environment.instrumentation.waitForIdleSync()
    }

    private fun resumedChat(): ChatActivity {
        var chat: ChatActivity? = null
        waitUntil("the chat is in front") {
            chat = currentChat()
            chat != null
        }
        return checkNotNull(chat)
    }

    private fun currentChat(): ChatActivity? {
        var chat: ChatActivity? = null
        environment.instrumentation.runOnMainSync {
            chat = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ChatActivity>().firstOrNull()
        }
        return chat
    }

    private fun <T> onChat(action: (ChatActivity) -> T): T {
        val chat = resumedChat()
        var result: T? = null
        environment.instrumentation.runOnMainSync { result = action(chat) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun isGenerating(activity: ChatActivity): Boolean =
        activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).contentDescription ==
            activity.getString(R.string.chat_stop)

    private fun editingMessageId(): Long? = onChat { activity ->
        ChatActivity::class.java.getDeclaredField("editingMessageId").apply { isAccessible = true }.get(activity) as Long?
    }

    private fun send(scenario: ActivityScenario<ChatActivity>, text: String) {
        scenario.onActivity { activity ->
            composer(activity).setText(text)
            activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
        }
    }

    private fun finishChats() {
        environment.instrumentation.runOnMainSync {
            Stage.values().filter { it != Stage.DESTROYED }.forEach { stage ->
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)
                    .filterIsInstance<ChatActivity>()
                    .forEach { it.finish() }
            }
        }
        waitUntil("the chats are closed") {
            var open = false
            environment.instrumentation.runOnMainSync {
                open = Stage.values().filter { it != Stage.DESTROYED }.any { stage ->
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)
                        .any { it is ChatActivity }
                }
            }
            !open
        }
    }
}
