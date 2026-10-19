package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags
import io.github.supermonster003.autojs6.plugin.threestoneai.ConversationHistoryStore
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiApplication
import org.junit.Assert.fail
import java.io.Closeable
import java.security.MessageDigest
import java.util.UUID

/**
 * Isolates a chat UI test on a real device: scripted backend, overridable speech apps, and a
 * fresh conversation. [close] deletes the conversations the test created and restores the chat
 * preferences, the remembered conversation and the composer draft.
 */
internal class ChatTestEnvironment(scripted: Boolean = true) : Closeable {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.targetContext
    val backend = ScriptedAiBackend()
    private val application = context.applicationContext as ThreeStoneAiApplication
    private val history = ConversationHistoryStore(context)
    private val existingConversations = history.loadAll().map { it.id }.toSet()
    private val lastConversation = history.lastConversationId()
    private val preferenceBackups = PREFERENCE_FILES.associateWith { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap()
    }

    init {
        if (scripted) application.aiBackendOverride = backend
        // An unknown remembered ID makes the chat open a fresh, empty conversation.
        history.rememberLastConversation("speech-test-${UUID.randomUUID()}")
        context.getSharedPreferences("chat-composer-draft", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Answers successive recognizer requests with [steps] (result code to text); the last repeats. */
    fun script(vararg steps: Pair<Int, String>) {
        DictationIntents.testPackage = fakeRecognizerPackage
        DictationIntents.testExtras = android.os.Bundle().apply {
            putString(FakeRecognizerActivity.EXTRA_SCRIPT_ID, UUID.randomUUID().toString())
            putIntArray(FakeRecognizerActivity.EXTRA_SCRIPT_CODES, steps.map { it.first }.toIntArray())
            putStringArray(FakeRecognizerActivity.EXTRA_SCRIPT_TEXTS, steps.map { it.second }.toTypedArray())
        }
    }

    fun launchChat(): ActivityScenario<ChatActivity> =
        ActivityScenario.launch<ChatActivity>(Intent(context, ChatActivity::class.java)).also { scenario ->
            waitUntil("the scripted target becomes ready") {
                var ready = false
                scenario.onActivity { activity -> ready = composer(activity).isEnabled }
                ready
            }
        }

    val fakeRecognizerPackage: String get() = instrumentation.context.packageName

    fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }

    override fun close() {
        DictationIntents.testPackage = null
        DictationIntents.testExtras = null
        SpeechDeviceProbe.stateOverride = null
        application.aiBackendOverride = null
        val created = history.loadAll().map { it.id }.toSet() - existingConversations
        if (created.isNotEmpty()) history.delete(created)
        lastConversation?.let(history::rememberLastConversation)
        preferenceBackups.forEach { (name, values) -> restore(name, values) }
        forgetCalibration(ScriptedAiBackend.TARGET_ID)
    }

    private fun restore(name: String, values: Map<String, *>) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply {
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

    private fun forgetCalibration(targetId: String) {
        val suffix = MessageDigest.getInstance("SHA-256").digest(targetId.toByteArray())
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        context.getSharedPreferences("context-token-calibration", Context.MODE_PRIVATE).edit()
            .remove("tokens-per-byte.$suffix")
            .remove("sample-count.$suffix")
            .commit()
    }

    companion object {
        private val PREFERENCE_FILES = listOf("chat-ui-settings", "chat-composer-draft", "voice-command", "application-settings")

        fun composer(activity: ChatActivity): EditText =
            activity.window.decorView.findViewWithTag(ChatViewTags.COMPOSER_INPUT)

        fun mic(activity: ChatActivity): View =
            activity.window.decorView.findViewWithTag(ChatViewTags.COMPOSER_MIC)

        fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) {
            (0 until root.childCount).flatMap { views(root.getChildAt(it)) }
        } else {
            emptyList()
        }

        fun showsText(activity: ChatActivity, text: String): Boolean =
            views(activity.window.decorView).filterIsInstance<TextView>()
                .any { view -> view.isShown && view.text.toString() == text }

        fun waitUntil(what: String, timeoutMillis: Long = 10_000L, condition: () -> Boolean) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val deadline = SystemClock.uptimeMillis() + timeoutMillis
            do {
                instrumentation.waitForIdleSync()
                if (condition()) return
                SystemClock.sleep(50L)
            } while (SystemClock.uptimeMillis() < deadline)
            fail("Timed out waiting until $what")
        }
    }
}
