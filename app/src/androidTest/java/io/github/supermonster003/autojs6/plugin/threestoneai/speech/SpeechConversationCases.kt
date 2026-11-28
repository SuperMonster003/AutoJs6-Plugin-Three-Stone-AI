package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import io.github.supermonster003.autojs6.plugin.threestoneai.VoiceRoundTiming
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.mic
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.showsText
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What a listening round of the voice conversation hears in a test, whatever listens. */
internal sealed interface Heard {
    data class Said(val text: String) : Heard

    data object NoSpeech : Heard

    data object Canceled : Heard

    /** A recognizer error: a network error of the sheet, an engine failure of the session. */
    data object Failure : Heard
}

/**
 * The voice conversation cases of P5-4 (roadmap D13) with a scripted target and the silent engine: ten rounds in a
 * row and every exit path. [SpeechConversationDeviceTest] listens through the recognizer sheet (P5-4, 3-Macaw Speech
 * unusable), [SpeechLiveConversationDeviceTest] through a fake session of 3-Macaw Speech (P6-3); the cases are the same.
 */
abstract class SpeechConversationCases {
    internal lateinit var environment: ChatTestEnvironment

    /** Whether the rounds listen through the session (P6-3) rather than the sheet. */
    protected abstract val live: Boolean

    /** The receipt and screenshot names start with this. */
    protected abstract val label: String

    /** The snackbar of [Heard.Failure]. */
    protected abstract val failureMessage: Int

    internal abstract fun createEnvironment(): ChatTestEnvironment

    /** Answers successive listening rounds with [steps]; the last one repeats. */
    internal abstract fun hear(vararg steps: Heard)

    @Before
    fun setUpConversation() {
        environment = createEnvironment()
        FakeSpeechEngine.install()
        environment.backend.reply = listOf("Answer one. ", "Answer two.")
        environment.backend.chunkDelayMillis = 30L
        FakeSpeechEngine.utteranceMillis = 40L
    }

    @After
    fun tearDownConversation() {
        FakeSpeechEngine.uninstall()
        environment.close()
    }

    @Test
    fun tenRoundsInARowThenTheRecognizerIsCanceled() {
        val questions = (1..10).map { "question $it" }
        hear(*(questions.map { Heard.Said(it) } + Heard.Canceled).toTypedArray())
        environment.launchChat().use { scenario ->
            start(scenario)
            waitForExit(scenario, timeoutMillis = 60_000L)
            assertEquals(questions, environment.backend.prompts.toList())
            assertEquals(20, FakeSpeechEngine.spokenTexts().size)
            var rounds = emptyList<VoiceRoundTiming>()
            scenario.onActivity { activity ->
                rounds = activity.voiceRounds.toList()
                assertEquals("RecognitionCanceled", activity.lastVoiceExit)
                assertEquals("The voice messages never stay in the input box", "", composer(activity).text.toString())
            }
            assertEquals(10, rounds.size)
            assertTrue(rounds.all { it.firstSoundMillis != null })
            assertTrue("Every round listened through the ${if (live) "session" else "sheet"}", rounds.all { it.live == live })
            SpeechDeviceReport.receipt(
                "$label-rounds",
                mapOf(
                    "rounds" to rounds.size,
                    "maxListenMillis" to rounds.maxOf { it.listenMillis },
                    "maxFirstSoundMillis" to rounds.maxOf { it.firstSoundMillis ?: -1L },
                    "meanRoundMillis" to rounds.map { it.roundMillis }.average().toLong(),
                    "exit" to "RecognitionCanceled",
                ),
            )
        }
    }

    @Test
    fun theRecognizerEndsTheConversation() {
        val exits = linkedMapOf<String, Any?>()
        environment.launchChat().use { scenario ->
            hear(Heard.NoSpeech)
            start(scenario)
            waitForExit(scenario)
            exits["noMatch"] = exit(scenario)

            hear(Heard.Canceled)
            start(scenario)
            waitForExit(scenario)
            exits["canceled"] = exit(scenario)

            hear(Heard.Failure)
            start(scenario)
            waitForExit(scenario)
            exits["failure"] = exit(scenario)
            waitUntil("the error is reported") {
                var shown = false
                scenario.onActivity { activity -> shown = showsText(activity, activity.getString(failureMessage)) }
                shown
            }
            assertTrue(environment.backend.prompts.isEmpty())
        }
        assertEquals(
            mapOf("noMatch" to "RecognitionNoMatch", "canceled" to "RecognitionCanceled", "failure" to "RecognitionFailed"),
            exits,
        )
        SpeechDeviceReport.receipt("$label-recognizer-exits", exits)
    }

    @Test
    fun aFailedReplyEndsTheConversation() {
        environment.backend.failNext = true
        hear(Heard.Said("question"))
        environment.launchChat().use { scenario ->
            start(scenario)
            waitForExit(scenario)
            assertEquals("GenerationFailed", exit(scenario))
        }
        SpeechDeviceReport.receipt("$label-failed-reply", mapOf("exit" to "GenerationFailed"))
    }

    @Test
    fun theExitButtonAndLeavingTheChatEndTheConversation() {
        val values = linkedMapOf<String, Any?>()
        environment.launchChat().use { scenario ->
            environment.backend.hold = true
            // No finished sentence yet, so nothing sounds and the phase stays THINKING.
            environment.backend.reply = listOf("Still thinking")
            hear(Heard.Said("slow question"))
            start(scenario)
            waitUntil("the reply is generating") { phase(scenario) == VoicePhase.THINKING }
            assertTrue(statusShows(scenario, R.string.speech_conversation_thinking))
            SpeechDeviceReport.screenshot("$label-voice-thinking")
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>(ChatViewTags.VOICE_STATUS_ACTION).performClick()
            }
            waitForExit(scenario)
            values["exitButton"] = exit(scenario)
            environment.backend.release()
            environment.backend.hold = false
            waitUntil("the reply still finishes") { environment.backend.completions == 1 }
            environment.backend.reply = listOf("Answer one. ", "Answer two.")

            FakeSpeechEngine.hold = true
            hear(Heard.Said("second question"))
            start(scenario)
            waitUntil("the reply is read") { phase(scenario) == VoicePhase.SPEAKING }
            assertTrue(statusShows(scenario, R.string.speech_conversation_speaking))
            SpeechDeviceReport.screenshot("$label-voice-speaking")
            scenario.moveToState(Lifecycle.State.CREATED)
            waitForExit(scenario)
            values["leftChat"] = exit(scenario)
            scenario.moveToState(Lifecycle.State.RESUMED)
            var reading: Long? = 0L
            scenario.onActivity { activity -> reading = activity.readingMessageId }
            assertEquals(null, reading)

            hear(Heard.Said("third question"))
            start(scenario)
            waitUntil("the reply is read") { phase(scenario) == VoicePhase.SPEAKING }
            waitUntil("the reply finished generating") { environment.backend.completions == 3 }
            scenario.onActivity { activity ->
                composer(activity).setText("typed message")
                activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
            }
            waitForExit(scenario)
            values["typedMessage"] = exit(scenario)
            FakeSpeechEngine.release()
        }
        assertEquals(
            mapOf("exitButton" to "Exit", "leftChat" to "LeftChat", "typedMessage" to "ConversationChanged"),
            values,
        )
        SpeechDeviceReport.receipt("$label-user-exits", values)
    }

    @Test
    fun aTapWhileReadingStopsTheReplyAndListensAgain() {
        FakeSpeechEngine.hold = true
        environment.backend.hold = true
        hear(Heard.Said("first"), Heard.Said("second"), Heard.Canceled)
        environment.launchChat().use { scenario ->
            start(scenario)
            waitUntil("the first reply is read while it streams") { phase(scenario) == VoicePhase.SPEAKING }
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>(ChatViewTags.VOICE_STATUS).performClick()
            }
            runCatching { waitUntil("the next sentence is sent") { environment.backend.prompts.size == 2 } }
                .onFailure {
                    var state = ""
                    scenario.onActivity { activity ->
                        state = "phase=${activity.currentVoicePhase} exit=${activity.lastVoiceExit} " +
                            "pending=${activity.dictationPending} live=${activity.liveDictationActive} " +
                            "composer='${composer(activity).text}' requests=${activity.dictationRequestCount} " +
                            "sessions=${activity.liveDictationRequestCount} prompts=${environment.backend.prompts}"
                    }
                    throw AssertionError("Next sentence not sent: $state", it)
                }
            FakeSpeechEngine.hold = false
            FakeSpeechEngine.release()
            environment.backend.hold = false
            environment.backend.release()
            waitForExit(scenario, timeoutMillis = 20_000L)
            assertEquals(listOf("first", "second"), environment.backend.prompts.toList())
            assertEquals("RecognitionCanceled", exit(scenario))
        }
        SpeechDeviceReport.receipt("$label-interrupt", mapOf("interrupted" to "pass"))
    }

    @Test
    fun withoutAnEngineTheConversationStillListensAgain() {
        SpeechDeviceProbe.stateOverride = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = false,
            anyTtsEngine = false,
        )
        hear(Heard.Said("a"), Heard.Said("b"), Heard.Canceled)
        environment.launchChat().use { scenario ->
            start(scenario)
            waitForExit(scenario)
            assertEquals(listOf("a", "b"), environment.backend.prompts.toList())
            assertTrue(FakeSpeechEngine.spokenTexts().isEmpty())
        }
        SpeechDeviceReport.receipt("$label-no-engine", mapOf("rounds" to 2))
    }

    protected fun start(scenario: ActivityScenario<ChatActivity>) {
        waitUntil("the chat is idle") {
            var idle = false
            scenario.onActivity { activity -> idle = idle(activity) }
            idle
        }
        scenario.onActivity { activity -> assertTrue(mic(activity).performLongClick()) }
    }

    internal fun phase(scenario: ActivityScenario<ChatActivity>): VoicePhase {
        var phase = VoicePhase.IDLE
        scenario.onActivity { activity -> phase = activity.currentVoicePhase }
        return phase
    }

    protected fun exit(scenario: ActivityScenario<ChatActivity>): String? {
        var exit: String? = null
        scenario.onActivity { activity -> exit = activity.lastVoiceExit }
        return exit
    }

    protected fun waitForExit(scenario: ActivityScenario<ChatActivity>, timeoutMillis: Long = 10_000L) {
        waitUntil("the conversation ends", timeoutMillis) {
            var ended = false
            scenario.onActivity { activity ->
                ended = activity.currentVoicePhase == VoicePhase.IDLE && !activity.dictationPending &&
                    !activity.liveDictationActive
            }
            ended
        }
    }

    protected fun statusShows(scenario: ActivityScenario<ChatActivity>, label: Int): Boolean {
        var shown = false
        scenario.onActivity { activity ->
            val bar = activity.window.decorView.findViewWithTag<View>(ChatViewTags.VOICE_STATUS)
            shown = bar.visibility == View.VISIBLE &&
                views(bar).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(label) }
        }
        return shown
    }

    private fun idle(activity: ChatActivity): Boolean =
        activity.currentVoicePhase == VoicePhase.IDLE && !activity.dictationPending && !activity.liveDictationActive &&
            activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND)
                .contentDescription != activity.getString(R.string.chat_stop)
}
