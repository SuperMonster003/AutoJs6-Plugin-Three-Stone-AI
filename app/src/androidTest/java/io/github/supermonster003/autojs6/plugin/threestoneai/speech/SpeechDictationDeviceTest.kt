package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.mic
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.showsText
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.NumberFormat

/**
 * P5-2 with the fake recognizer from the test APK: insertion, every result code, the request
 * extras, the input limit, dictation while a reply streams and the two "no recognizer" paths.
 */
class SpeechDictationDeviceTest {
    private lateinit var environment: ChatTestEnvironment
    private val values = linkedMapOf<String, Any?>()

    @Before
    fun setUp() {
        environment = ChatTestEnvironment()
        SpeechDeviceProbe.stateOverride = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = false,
            anyTtsEngine = false,
        )
        DictationIntents.testPackage = environment.fakeRecognizerPackage
    }

    @After
    fun tearDown() {
        environment.close()
    }

    @Test
    fun dictationInsertsAtTheCursorAndHandlesEveryResult() {
        environment.launchChat().use { scenario ->
            scenario.onActivity { activity -> assertEquals(View.VISIBLE, mic(activity).visibility) }

            setComposer(scenario, "Hello world", 5, 5)
            dictate(scenario, Activity.RESULT_OK, "big")
            assertComposer(scenario, "Hello big world")
            SpeechDeviceReport.screenshot("chat-composer-mic")
            values["cursorInsert"] = "pass"

            setComposer(scenario, "Hello world", 6, 11)
            dictate(scenario, Activity.RESULT_OK, "there")
            assertComposer(scenario, "Hello there")
            values["selectionReplace"] = "pass"

            setComposer(scenario, "今天天气", 2, 2)
            dictate(scenario, Activity.RESULT_OK, "的")
            assertComposer(scenario, "今天的天气")

            setComposer(scenario, "unchanged", 3, 3)
            for (code in listOf(Activity.RESULT_CANCELED, RecognizerIntent.RESULT_NO_MATCH)) {
                dictate(scenario, code, null)
                assertComposer(scenario, "unchanged")
            }
            dictate(scenario, Activity.RESULT_OK, null)
            assertComposer(scenario, "unchanged")
            values["cancelAndNoMatchQuiet"] = "pass"

            val failures = mapOf(
                RecognizerIntent.RESULT_NETWORK_ERROR to R.string.speech_dictation_failed_network,
                RecognizerIntent.RESULT_AUDIO_ERROR to R.string.speech_dictation_failed_audio,
                RecognizerIntent.RESULT_CLIENT_ERROR to R.string.speech_dictation_failed,
                RecognizerIntent.RESULT_SERVER_ERROR to R.string.speech_dictation_failed,
            )
            failures.forEach { (code, message) ->
                dictate(scenario, code, null)
                assertComposer(scenario, "unchanged")
                waitForSnackbar(scenario, message)
            }
            values["failureCodesReported"] = failures.size

            setComposer(scenario, "", 0, 0)
            dictate(scenario, Activity.RESULT_OK, null, echo = true)
            var expected = ""
            scenario.onActivity { activity ->
                expected = listOf(
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    activity.resources.configuration.locales[0].toLanguageTag(),
                    activity.getString(R.string.speech_dictation_prompt),
                    "1",
                ).joinToString("|")
            }
            assertComposer(scenario, expected)
            values["requestExtras"] = "pass"

            val filler = "x".repeat(16_380)
            setComposer(scenario, filler, filler.length, filler.length)
            dictate(scenario, Activity.RESULT_OK, "abcdefgh")
            assertComposer(scenario, "$filler abc")
            waitForSnackbar(
                scenario,
                R.string.speech_dictation_truncated,
                NumberFormat.getIntegerInstance().format(16_384),
            )
            values["limitTruncation"] = "pass"
        }
        SpeechDeviceReport.receipt("dictation", values)
    }

    @Test
    fun dictationOnlyEditsTheComposerWhileAReplyStreams() {
        environment.backend.hold = true
        environment.backend.reply = listOf("Streaming ", "reply")
        environment.launchChat().use { scenario ->
            setComposer(scenario, "first question", 0, 0)
            scenario.onActivity { activity ->
                val send = ChatTestEnvironment.views(activity.window.decorView)
                    .first { it.contentDescription == activity.getString(R.string.chat_send) }
                send.performClick()
            }
            waitUntil("the reply starts") { environment.backend.prompts.size == 1 }
            setComposer(scenario, "", 0, 0)
            dictate(scenario, Activity.RESULT_OK, "next question")
            assertComposer(scenario, "next question")
            assertEquals("Dictation never sends", 1, environment.backend.prompts.size)
            environment.backend.release()
        }
        SpeechDeviceReport.receipt("dictation-while-streaming", mapOf("composerOnly" to "pass"))
    }

    @Test
    fun missingRecognizersHideOrReportTheMicrophone() {
        environment.launchChat().use { scenario ->
            // The pinned recognizer disappeared after the button was shown.
            DictationIntents.testPackage = "io.github.supermonster003.speech.test.missing"
            scenario.onActivity { activity -> mic(activity).performClick() }
            waitForSnackbar(scenario, R.string.speech_dictation_unavailable)
            scenario.onActivity { activity -> assertFalse(activity.dictationPending) }

            SpeechDeviceProbe.stateOverride = SpeechDeviceState.NONE
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity -> assertEquals(View.GONE, mic(activity).visibility) }
        }
        SpeechDeviceReport.receipt("dictation-unavailable", mapOf("activityNotFound" to "pass", "hidden" to "pass"))
    }

    private fun setComposer(scenario: ActivityScenario<ChatActivity>, text: String, start: Int, end: Int) {
        scenario.onActivity { activity ->
            composer(activity).apply {
                setText(text)
                requestFocus()
                setSelection(start, end)
            }
        }
    }

    private fun dictate(
        scenario: ActivityScenario<ChatActivity>,
        code: Int,
        text: String?,
        echo: Boolean = false,
    ) {
        DictationIntents.testExtras = Bundle().apply {
            putInt(FakeRecognizerActivity.EXTRA_RESULT_CODE, code)
            text?.let { putString(FakeRecognizerActivity.EXTRA_RESULT_TEXT, it) }
            putBoolean(FakeRecognizerActivity.EXTRA_ECHO, echo)
        }
        // The fake recognizer can answer before the next main-thread check, so count requests instead.
        var requested = false
        scenario.onActivity { activity ->
            val before = activity.dictationRequestCount
            mic(activity).performClick()
            requested = activity.dictationRequestCount == before + 1
        }
        assertTrue("The recognizer request was not sent", requested)
        waitUntil("the recognizer replies") {
            var done = false
            scenario.onActivity { activity -> done = !activity.dictationPending && activity.hasWindowFocus() }
            done
        }
    }

    private fun assertComposer(scenario: ActivityScenario<ChatActivity>, expected: String) {
        scenario.onActivity { activity -> assertEquals(expected, composer(activity).text.toString()) }
    }

    private fun waitForSnackbar(scenario: ActivityScenario<ChatActivity>, message: Int, vararg arguments: Any) {
        waitUntil("the snackbar shows") {
            var shown = false
            scenario.onActivity { activity ->
                shown = showsText(activity, activity.getString(message, *arguments))
            }
            shown
        }
    }
}
