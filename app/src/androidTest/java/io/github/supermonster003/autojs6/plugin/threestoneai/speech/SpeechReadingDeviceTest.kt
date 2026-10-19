package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatUiSettingsStore
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags
import io.github.supermonster003.autojs6.plugin.threestoneai.MarkdownMessageView
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.showsText
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P5-3 with a silent fake engine: automatic reading while a reply streams, the message menu,
 * the status bar and every stop condition of roadmap D10 / D18, language fallback and engine failure.
 */
class SpeechReadingDeviceTest {
    private lateinit var environment: ChatTestEnvironment
    private val values = linkedMapOf<String, Any?>()

    @Before
    fun setUp() {
        environment = ChatTestEnvironment()
        FakeSpeechEngine.install()
        SpeechDeviceProbe.stateOverride = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = false,
            macawTtsEngine = false,
            anyTtsEngine = true,
        )
    }

    @After
    fun tearDown() {
        FakeSpeechEngine.uninstall()
        environment.close()
    }

    private fun autoRead(enabled: Boolean) {
        val store = ChatUiSettingsStore(environment.context)
        store.save(store.load().copy(autoReadReplies = enabled))
    }

    @Test
    fun repliesAreReadWhileTheyStream() {
        autoRead(true)
        environment.backend.reply = listOf("Hello there. ", "How are ", "you? ", "Fine.")
        environment.backend.chunkDelayMillis = 250L
        environment.launchChat().use { scenario ->
            send(scenario, "question")
            waitUntil("reading starts") { reading(scenario) != null }
            waitUntil("the first sentence is read") { FakeSpeechEngine.spokenTexts().isNotEmpty() }
            assertTrue("Status bar shows reading", statusShows(scenario, R.string.speech_reading_status))
            SpeechDeviceReport.screenshot("chat-reading-status")
            waitUntil("the reply is read to the end") {
                environment.backend.completions == 1 && reading(scenario) == null
            }
            assertEquals(listOf("Hello there.", "How are you?", "Fine."), FakeSpeechEngine.spokenTexts())
            val first = FakeSpeechEngine.engines.single().spoken.first().queuedAt
            assertTrue("The first sentence was queued before the reply finished", first < environment.backend.completedAt)
            values["leadMillis"] = environment.backend.completedAt - first
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.window.decorView.findViewWithTag<View>(ChatViewTags.VOICE_STATUS).visibility)
            }
        }
        values["streamedSentences"] = 3
        SpeechDeviceReport.receipt("reading-stream", values)
    }

    @Test
    fun theMenuReadsAFinishedReplyAndStopsIt() {
        autoRead(false)
        environment.backend.reply = listOf(
            "Para one. Para two.\n```\ncode. not read\n```\n| a | b |\n|---|---|\n| 1 | 2 |\nEnd.",
        )
        environment.launchChat().use { scenario ->
            send(scenario, "question")
            waitUntil("the reply finishes") { environment.backend.completions == 1 }
            assertNull("Automatic reading is off", reading(scenario))
            assertTrue(FakeSpeechEngine.spokenTexts().isEmpty())

            FakeSpeechEngine.hold = true
            chooseFromMenu(scenario, R.string.chat_message_read_aloud, screenshot = "chat-reply-menu")
            waitUntil("reading starts") { reading(scenario) != null }
            chooseFromMenu(scenario, R.string.chat_message_stop_reading)
            waitUntil("reading stops") { reading(scenario) == null }
            assertTrue(FakeSpeechEngine.engines.single().stops >= 1)

            FakeSpeechEngine.hold = false
            val before = FakeSpeechEngine.spokenTexts().size
            chooseFromMenu(scenario, R.string.chat_message_read_aloud)
            waitUntil("the whole reply is read") {
                reading(scenario) == null && FakeSpeechEngine.spokenTexts().size > before
            }
            var hints = emptyList<String>()
            scenario.onActivity { activity ->
                hints = listOf(
                    activity.getString(R.string.speech_hint_code_block),
                    activity.getString(R.string.speech_hint_table),
                )
            }
            assertEquals(
                listOf("Para one.", "Para two.", hints[0], hints[1], "End."),
                FakeSpeechEngine.spokenTexts().drop(before),
            )
        }
        SpeechDeviceReport.receipt("reading-menu", mapOf("readAndStop" to "pass", "hints" to "pass"))
    }

    @Test
    fun readingStopsOnEveryRoadmapCondition() {
        autoRead(true)
        FakeSpeechEngine.hold = true
        environment.backend.reply = listOf("One. Two. Three.")
        environment.launchChat().use { scenario ->
            send(scenario, "a")
            waitUntil("reading starts") { reading(scenario) != null }
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>(ChatViewTags.VOICE_STATUS_ACTION).performClick()
            }
            waitUntil("the status bar stops reading") { reading(scenario) == null }
            values["statusBar"] = "pass"

            send(scenario, "b")
            waitUntil("reading starts") { reading(scenario) != null }
            val previous = reading(scenario)
            send(scenario, "c")
            waitUntil("sending stops the previous reading") {
                val current = reading(scenario)
                current != null && current != previous
            }
            values["send"] = "pass"

            scenario.onActivity { activity ->
                val toolbar = views(activity.window.decorView).filterIsInstance<androidx.appcompat.widget.Toolbar>().first()
                activity.onOptionsItemSelected(toolbar.menu.findItem(MENU_NEW_CONVERSATION))
            }
            waitUntil("a new conversation stops reading") { reading(scenario) == null }
            values["newConversation"] = "pass"

            send(scenario, "d")
            waitUntil("reading starts") { reading(scenario) != null }
            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil("leaving the chat stops reading") { reading(scenario) == null }
            scenario.moveToState(Lifecycle.State.RESUMED)
            values["onStop"] = "pass"

            environment.backend.hold = true
            val spokenBefore = FakeSpeechEngine.spokenTexts().size
            send(scenario, "e")
            waitUntil("reading starts while generating") {
                reading(scenario) != null && FakeSpeechEngine.spokenTexts().drop(spokenBefore).firstOrNull() == "One."
            }
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
            }
            waitUntil("stopping the generation stops reading") { reading(scenario) == null }
            environment.backend.hold = false
            values["stopGeneration"] = "pass"

            send(scenario, "f")
            waitUntil("reading starts") { reading(scenario) != null }
            val audio = environment.context.getSystemService(AudioManager::class.java)
            val other = takeAudioFocus(audio)
            try {
                waitUntil("another app's audio focus stops reading") { reading(scenario) == null }
            } finally {
                other()
            }
            values["audioFocusLoss"] = "pass"
        }
        SpeechDeviceReport.receipt("reading-stops", values)
    }

    @Test
    fun unsupportedLanguagesAndFailingEnginesAreReported() {
        autoRead(false)
        environment.backend.reply = listOf("Hello.")
        FakeSpeechEngine.supportedLanguages = emptySet()
        environment.launchChat().use { scenario ->
            send(scenario, "question")
            waitUntil("the reply finishes") { environment.backend.completions == 1 }
            chooseFromMenu(scenario, R.string.chat_message_read_aloud)
            waitUntil("the default voice reads it") { FakeSpeechEngine.spokenTexts() == listOf("Hello.") }
            assertTrue(FakeSpeechEngine.engines.single().defaultVoiceUses >= 1)
            var expected = ""
            scenario.onActivity { activity ->
                val locale = activity.resources.configuration.locales[0]
                expected = activity.getString(R.string.speech_language_unavailable, locale.getDisplayName(locale))
            }
            waitUntil("the fallback is reported") {
                var shown = false
                scenario.onActivity { activity -> shown = showsText(activity, expected) }
                shown
            }
        }
        FakeSpeechEngine.supportedLanguages = setOf("en", "zh")
        FakeSpeechEngine.initSucceeds = false
        environment.launchChat().use { scenario ->
            send(scenario, "again")
            waitUntil("the reply finishes") { environment.backend.completions == 2 }
            chooseFromMenu(scenario, R.string.chat_message_read_aloud)
            waitUntil("the engine failure is reported") {
                var shown = false
                scenario.onActivity { activity ->
                    shown = showsText(activity, activity.getString(R.string.speech_read_aloud_failed))
                }
                shown && reading(scenario) == null
            }
        }
        SpeechDeviceReport.receipt("reading-failures", mapOf("languageFallback" to "pass", "engineFailure" to "pass"))
    }

    @Test
    fun withoutAnyEngineNothingIsRead() {
        autoRead(true)
        SpeechDeviceProbe.stateOverride = SpeechDeviceState.NONE
        environment.backend.reply = listOf("Not read.")
        environment.launchChat().use { scenario ->
            send(scenario, "question")
            waitUntil("the reply finishes") { environment.backend.completions == 1 }
            assertNull(reading(scenario))
            scenario.onActivity { activity -> lastReply(activity).performLongClick() }
            scenario.onActivity { activity ->
                val dialog = requireNotNull(activity.messageActionsDialog)
                val labels = (0 until dialog.listView.adapter.count).map { dialog.listView.adapter.getItem(it).toString() }
                assertFalse(activity.getString(R.string.chat_message_read_aloud) in labels)
                dialog.dismiss()
            }
        }
        assertTrue(FakeSpeechEngine.engines.isEmpty())
        SpeechDeviceReport.receipt("reading-no-engine", mapOf("hidden" to "pass"))
    }

    private fun reading(scenario: ActivityScenario<ChatActivity>): Long? {
        var id: Long? = null
        scenario.onActivity { activity -> id = activity.readingMessageId }
        return id
    }

    private fun send(scenario: ActivityScenario<ChatActivity>, text: String) {
        waitUntil("the previous reply has ended") {
            var idle = false
            scenario.onActivity { activity ->
                idle = activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND)
                    .contentDescription != activity.getString(R.string.chat_stop)
            }
            idle
        }
        val prompts = environment.backend.prompts.size
        scenario.onActivity { activity ->
            composer(activity).setText(text)
            activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
        }
        waitUntil("the message is sent") { environment.backend.prompts.size == prompts + 1 }
    }

    private fun statusShows(scenario: ActivityScenario<ChatActivity>, label: Int): Boolean {
        var shown = false
        scenario.onActivity { activity ->
            val bar = activity.window.decorView.findViewWithTag<View>(ChatViewTags.VOICE_STATUS)
            shown = bar.visibility == View.VISIBLE &&
                views(bar).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(label) }
        }
        return shown
    }

    private fun lastReply(activity: ChatActivity): View =
        views(activity.window.decorView).filterIsInstance<MarkdownMessageView>().last()

    private fun chooseFromMenu(scenario: ActivityScenario<ChatActivity>, label: Int, screenshot: String? = null) {
        scenario.onActivity { activity -> lastReply(activity).performLongClick() }
        screenshot?.let(SpeechDeviceReport::screenshot)
        scenario.onActivity { activity ->
            val dialog = activity.messageActionsDialog!!
            val adapter = dialog.listView.adapter
            val wanted = activity.getString(label)
            val index = (0 until adapter.count).first { adapter.getItem(it).toString() == wanted }
            dialog.listView.performItemClick(null, index, adapter.getItemId(index))
            dialog.dismiss()
        }
    }

    /** Takes the audio focus like another player would; returns the release action. */
    private fun takeAudioFocus(audio: AudioManager): () -> Unit {
        val listener = AudioManager.OnAudioFocusChangeListener { }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            audio.requestAudioFocus(request)
            ({ audio.abandonAudioFocusRequest(request) })
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            @Suppress("DEPRECATION")
            ({ audio.abandonAudioFocus(listener) })
        }
    }

    private companion object {
        const val MENU_NEW_CONVERSATION = 1
    }
}
