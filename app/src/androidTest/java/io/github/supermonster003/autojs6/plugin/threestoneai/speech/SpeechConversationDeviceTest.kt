package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.speech.RecognizerIntent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags
import io.github.supermonster003.autojs6.plugin.threestoneai.R
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

/**
 * P5-4 with the fake recognizer, a scripted target and the silent engine: ten rounds in a row and
 * every exit path of roadmap D13.
 */
class SpeechConversationDeviceTest {
    private lateinit var environment: ChatTestEnvironment
    private val ok = Activity.RESULT_OK
    private val canceled = Activity.RESULT_CANCELED

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
        environment.backend.reply = listOf("Answer one. ", "Answer two.")
        environment.backend.chunkDelayMillis = 30L
        FakeSpeechEngine.utteranceMillis = 40L
    }

    @After
    fun tearDown() {
        FakeSpeechEngine.uninstall()
        environment.close()
    }

    @Test
    fun tenRoundsInARowThenTheRecognizerIsCanceled() {
        val questions = (1..10).map { "question $it" }
        environment.script(*(questions.map { ok to it } + (canceled to "")).toTypedArray())
        environment.launchChat().use { scenario ->
            start(scenario)
            waitForExit(scenario, timeoutMillis = 60_000L)
            assertEquals(questions, environment.backend.prompts.toList())
            assertEquals(20, FakeSpeechEngine.spokenTexts().size)
            var rounds = emptyList<io.github.supermonster003.autojs6.plugin.threestoneai.VoiceRoundTiming>()
            scenario.onActivity { activity ->
                rounds = activity.voiceRounds.toList()
                assertEquals("RecognitionCanceled", activity.lastVoiceExit)
            }
            assertEquals(10, rounds.size)
            assertTrue(rounds.all { it.firstSoundMillis != null })
            SpeechDeviceReport.receipt(
                "conversation-rounds",
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
            environment.script(RecognizerIntent.RESULT_NO_MATCH to "")
            start(scenario)
            waitForExit(scenario)
            exits["noMatch"] = exit(scenario)

            environment.script(canceled to "")
            start(scenario)
            waitForExit(scenario)
            exits["canceled"] = exit(scenario)

            environment.script(RecognizerIntent.RESULT_NETWORK_ERROR to "")
            start(scenario)
            waitForExit(scenario)
            exits["networkError"] = exit(scenario)
            waitUntil("the error is reported") {
                var shown = false
                scenario.onActivity { activity ->
                    shown = showsText(activity, activity.getString(R.string.speech_dictation_failed_network))
                }
                shown
            }
            assertTrue(environment.backend.prompts.isEmpty())
        }
        assertEquals(
            mapOf("noMatch" to "RecognitionNoMatch", "canceled" to "RecognitionCanceled", "networkError" to "RecognitionFailed"),
            exits,
        )
        SpeechDeviceReport.receipt("conversation-recognizer-exits", exits)
    }

    @Test
    fun aFailedReplyEndsTheConversation() {
        environment.backend.failNext = true
        environment.script(ok to "question")
        environment.launchChat().use { scenario ->
            start(scenario)
            waitForExit(scenario)
            assertEquals("GenerationFailed", exit(scenario))
        }
        SpeechDeviceReport.receipt("conversation-failed-reply", mapOf("exit" to "GenerationFailed"))
    }

    @Test
    fun theExitButtonAndLeavingTheChatEndTheConversation() {
        val values = linkedMapOf<String, Any?>()
        environment.launchChat().use { scenario ->
            environment.backend.hold = true
            // No finished sentence yet, so nothing sounds and the phase stays THINKING.
            environment.backend.reply = listOf("Still thinking")
            environment.script(ok to "slow question")
            start(scenario)
            waitUntil("the reply is generating") { phase(scenario) == VoicePhase.THINKING }
            assertTrue(statusShows(scenario, R.string.speech_conversation_thinking))
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
            environment.script(ok to "second question")
            start(scenario)
            waitUntil("the reply is read") { phase(scenario) == VoicePhase.SPEAKING }
            assertTrue(statusShows(scenario, R.string.speech_conversation_speaking))
            scenario.moveToState(Lifecycle.State.CREATED)
            waitForExit(scenario)
            values["leftChat"] = exit(scenario)
            scenario.moveToState(Lifecycle.State.RESUMED)
            var reading: Long? = 0L
            scenario.onActivity { activity -> reading = activity.readingMessageId }
            assertEquals(null, reading)

            environment.script(ok to "third question")
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
        SpeechDeviceReport.receipt("conversation-user-exits", values)
    }

    @Test
    fun aTapWhileReadingStopsTheReplyAndListensAgain() {
        FakeSpeechEngine.hold = true
        environment.backend.hold = true
        environment.script(ok to "first", ok to "second", canceled to "")
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
                            "pending=${activity.dictationPending} composer='${composer(activity).text}' " +
                            "requests=${activity.dictationRequestCount} prompts=${environment.backend.prompts}"
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
        SpeechDeviceReport.receipt("conversation-interrupt", mapOf("interrupted" to "pass"))
    }

    @Test
    fun withoutAnEngineTheConversationStillListensAgain() {
        SpeechDeviceProbe.stateOverride = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = false,
            anyTtsEngine = false,
        )
        environment.script(ok to "a", ok to "b", canceled to "")
        environment.launchChat().use { scenario ->
            start(scenario)
            waitForExit(scenario)
            assertEquals(listOf("a", "b"), environment.backend.prompts.toList())
            assertTrue(FakeSpeechEngine.spokenTexts().isEmpty())
        }
        SpeechDeviceReport.receipt("conversation-no-engine", mapOf("rounds" to 2))
    }

    private fun start(scenario: ActivityScenario<ChatActivity>) {
        waitUntil("the chat is idle") {
            var idle = false
            scenario.onActivity { activity ->
                idle = activity.currentVoicePhase == VoicePhase.IDLE && !activity.dictationPending &&
                    activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND)
                        .contentDescription != activity.getString(R.string.chat_stop)
            }
            idle
        }
        scenario.onActivity { activity -> assertTrue(mic(activity).performLongClick()) }
    }

    private fun phase(scenario: ActivityScenario<ChatActivity>): VoicePhase {
        var phase = VoicePhase.IDLE
        scenario.onActivity { activity -> phase = activity.currentVoicePhase }
        return phase
    }

    private fun exit(scenario: ActivityScenario<ChatActivity>): String? {
        var exit: String? = null
        scenario.onActivity { activity -> exit = activity.lastVoiceExit }
        return exit
    }

    private fun waitForExit(scenario: ActivityScenario<ChatActivity>, timeoutMillis: Long = 10_000L) {
        waitUntil("the conversation ends", timeoutMillis) {
            var ended = false
            scenario.onActivity { activity ->
                ended = activity.currentVoicePhase == VoicePhase.IDLE && !activity.dictationPending
            }
            ended
        }
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
}
