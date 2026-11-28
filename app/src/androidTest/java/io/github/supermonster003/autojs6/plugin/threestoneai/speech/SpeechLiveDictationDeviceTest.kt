package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.mic
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.showsText
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.NumberFormat

/**
 * P6-2 with an injected session source ([FakeLiveDictationSource], nothing records): partial and final text in the
 * input box, the stop button, finishing, cancelling when the chat leaves the screen or is recreated, sending, the
 * input limit, dictation while a reply streams, and what each failure leads to. The recognizer sheet of the fallback
 * is the fake recognizer of the test APK.
 */
class SpeechLiveDictationDeviceTest {
    private lateinit var environment: ChatTestEnvironment
    private lateinit var source: FakeLiveDictationSource
    private val values = linkedMapOf<String, Any?>()

    @Before
    fun setUp() {
        environment = ChatTestEnvironment(
            macaw = MacawSpeechAvailability(MacawSpeechStatus.AVAILABLE, contractVersion = 3, resident = true),
        )
        source = FakeLiveDictationSource()
        MacawSpeechTestHooks.source = source
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
    fun sentencesAppearLiveAndTheButtonFinishes() {
        environment.launchChat().use { scenario ->
            scenario.onActivity { activity -> assertTrue(activity.liveDictationAvailableForTest) }
            setComposer(scenario, "", 0, 0)
            press(scenario)
            val session = source.last
            assertEquals(LiveDictationMode.CONTINUOUS, session.mode)
            scenario.onActivity { activity ->
                assertTrue(activity.liveDictationActive)
                assertEquals(activity.getString(R.string.speech_dictation_stop), mic(activity).contentDescription)
                // Preparing until the session is ready (A4, D225), then listening.
                assertEquals(activity.getString(R.string.speech_live_dictation_preparing), composer(activity).hint.toString())
                assertEquals("No sheet was opened", 0, activity.dictationRequestCount)
            }
            values["stopButton"] = "pass"

            session.ready()
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.speech_live_dictation_listening), composer(activity).hint.toString())
            }
            SpeechDeviceReport.screenshot("live-dictation-listening")
            values["preparingThenListening"] = "pass"
            session.partial("Hello")
            scenario.onActivity { activity ->
                assertEquals("Hello", composer(activity).text.toString())
                assertEquals("Hello", activity.livePartialForTest)
                assertTrue("The partial is gray", grayed(activity, "Hello"))
            }
            SpeechDeviceReport.screenshot("live-dictation-partial")
            session.final("Hello there.")
            assertComposer(scenario, "Hello there.")
            scenario.onActivity { activity ->
                assertNull(activity.livePartialForTest)
                assertFalse("The final is ordinary text", grayed(activity, "Hello there."))
                assertTrue(activity.liveDictationActive)
            }
            session.partial("How")
            assertComposer(scenario, "Hello there. How")
            session.partial("How are")
            session.final("How are you?")
            assertComposer(scenario, "Hello there. How are you?")
            values["partialAndFinal"] = "pass"

            // Pressed again: the pending sentence is flushed, then the session ends.
            source.finishFinal = "Fine"
            session.partial("Fi")
            press(scenario, opensSession = false)
            waitUntil("the session ends") { !active(scenario) }
            assertEquals(1, session.finishes)
            assertComposer(scenario, "Hello there. How are you? Fine")
            scenario.onActivity { activity ->
                assertEquals(LiveDictationEnd.Stopped, activity.lastLiveDictationEnd)
                assertEquals(activity.getString(R.string.speech_dictate), mic(activity).contentDescription)
                assertEquals(activity.getString(R.string.chat_input_hint), composer(activity).hint.toString())
            }
            values["finishByButton"] = "pass"

            // Silence and five minutes end quietly.
            press(scenario)
            source.last.end(SpeechContract.REASON_TIMEOUT)
            waitUntil("the timeout ends it") { !active(scenario) }
            press(scenario)
            source.last.end(SpeechContract.REASON_MAX_DURATION)
            waitUntil("five minutes end it") { !active(scenario) }
            scenario.onActivity { activity -> assertEquals(LiveDictationEnd.MaxDuration, activity.lastLiveDictationEnd) }
            assertComposer(scenario, "Hello there. How are you? Fine")
            values["timeoutQuiet"] = "pass"
        }
        SpeechDeviceReport.receipt("live-dictation", values)
    }

    @Test
    fun theSelectionIsReplacedAndTheLimitEndsTheSession() {
        environment.launchChat().use { scenario ->
            setComposer(scenario, "Hello old world", 6, 9)
            press(scenario)
            source.last.partial("ne")
            assertComposer(scenario, "Hello ne world")
            source.last.final("new")
            assertComposer(scenario, "Hello new world")
            environment.instrumentation.runOnMainSync { source.last.cancel() }
            waitUntil("cancelled") { !active(scenario) }
            values["selectionReplace"] = "pass"

            val filler = "x".repeat(16_380)
            setComposer(scenario, filler, filler.length, filler.length)
            press(scenario)
            val cut = source.last
            cut.final("abcdefgh")
            assertComposer(scenario, "$filler abc")
            waitUntil("the session ends at the limit") { !active(scenario) && cut.cancels == 1 }
            waitForSnackbar(scenario, R.string.speech_dictation_truncated, limit())
            values["limitTruncated"] = "pass"

            setComposer(scenario, filler, filler.length, filler.length)
            press(scenario)
            val full = source.last
            full.final("abc")
            assertComposer(scenario, "$filler abc")
            waitUntil("the session ends when full") { !active(scenario) && full.cancels == 1 }
            waitForSnackbar(scenario, R.string.speech_live_dictation_limit, limit())
            values["limitFull"] = "pass"
        }
        SpeechDeviceReport.receipt("live-dictation-limit", values)
    }

    @Test
    fun leavingOrRecreatingTheChatCancelsAndKeepsTheShownText() {
        environment.launchChat().use { scenario ->
            setComposer(scenario, "", 0, 0)
            press(scenario)
            val left = source.last
            left.final("Kept")
            left.partial("half a")
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue("onStop cancels", left.cancels == 1 && left.ended)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertComposer(scenario, "Kept half a")
            scenario.onActivity { activity ->
                assertFalse(activity.liveDictationActive)
                assertEquals(LiveDictationEnd.Cancelled, activity.lastLiveDictationEnd)
                assertFalse("No gray text is left", grayed(activity, "half a"))
            }
            values["leaveCancels"] = "pass"

            press(scenario)
            val recreated = source.last
            recreated.partial("again")
            scenario.recreate()
            assertTrue("Recreation cancels", recreated.cancels == 1)
            assertComposer(scenario, "Kept half a again")
            scenario.onActivity { activity -> assertFalse(activity.liveDictationActive) }
            values["recreationCancels"] = "pass"
        }
        SpeechDeviceReport.receipt("live-dictation-lifecycle", values)
    }

    @Test
    fun sendingEndsTheSessionAndSendsWhatIsShown() {
        environment.launchChat().use { scenario ->
            setComposer(scenario, "", 0, 0)
            press(scenario)
            val session = source.last
            session.final("Send")
            session.partial("this")
            scenario.onActivity { activity -> send(activity) }
            waitUntil("the message is sent") { environment.backend.prompts.size == 1 }
            assertEquals("Send this", environment.backend.prompts.single())
            assertTrue(session.cancels == 1)
            scenario.onActivity { activity -> assertFalse(activity.liveDictationActive) }
            values["sendCommits"] = "pass"
        }
        SpeechDeviceReport.receipt("live-dictation-send", values)
    }

    @Test
    fun liveDictationOnlyEditsTheComposerWhileAReplyStreams() {
        environment.backend.hold = true
        environment.backend.reply = listOf("Streaming ", "reply")
        environment.launchChat().use { scenario ->
            setComposer(scenario, "first question", 0, 0)
            scenario.onActivity { activity -> send(activity) }
            waitUntil("the reply starts") { environment.backend.prompts.size == 1 }
            setComposer(scenario, "", 0, 0)
            press(scenario)
            source.last.final("next question")
            assertComposer(scenario, "next question")
            // While a reply streams the send button is the stop button, so the final only edits the composer.
            source.last.partial("and more")
            scenario.onActivity { activity -> assertTrue(activity.liveDictationActive) }
            assertEquals("Live dictation never sends", 1, environment.backend.prompts.size)
            environment.instrumentation.runOnMainSync { source.last.cancel() }
            environment.backend.release()
        }
        SpeechDeviceReport.receipt("live-dictation-while-streaming", mapOf("composerOnly" to "pass"))
    }

    @Test
    fun failuresFallBackToTheSheetOrGuideToThePermission() {
        environment.launchChat().use { scenario ->
            // SESSION_BUSY and MODEL_MISSING: the sheet answers this once (D209).
            for ((index, code) in listOf(SpeechErrorCodes.SESSION_BUSY, SpeechErrorCodes.MODEL_MISSING).withIndex()) {
                setComposer(scenario, "", 0, 0)
                sheetReplies("from the sheet $index")
                source.openFailure = code
                var sheetsBefore = 0
                scenario.onActivity { activity -> sheetsBefore = activity.dictationRequestCount }
                press(scenario)
                waitUntil("$code opens the sheet") {
                    var done = false
                    scenario.onActivity { activity ->
                        done = activity.dictationRequestCount == sheetsBefore + 1 && !activity.dictationPending &&
                            activity.hasWindowFocus()
                    }
                    done
                }
                assertComposer(scenario, "from the sheet $index")
                scenario.onActivity { activity ->
                    assertEquals(LiveDictationEnd.Failed(code, heard = false), activity.lastLiveDictationEnd)
                }
                values["sheetAfter$code"] = "pass"
            }

            // PERMISSION_DENIED: 3-Macaw Speech's permission screen (blocked by the monitor here).
            val monitor = Instrumentation.ActivityMonitor(MacawSpeech.MICROPHONE_PERMISSION_ACTIVITY, null, true)
            environment.instrumentation.addMonitor(monitor)
            try {
                source.openFailure = SpeechErrorCodes.PERMISSION_DENIED
                press(scenario)
                waitUntil("the permission screen is requested") { monitor.hits == 1 }
                waitForSnackbar(scenario, R.string.speech_live_dictation_permission)
            } finally {
                environment.instrumentation.removeMonitor(monitor)
            }
            values["permissionScreen"] = "pass"

            // A failure after text arrived keeps the text and only reports.
            setComposer(scenario, "", 0, 0)
            var sheetsBefore = 0
            scenario.onActivity { activity -> sheetsBefore = activity.dictationRequestCount }
            press(scenario)
            source.last.final("Written")
            source.last.fail(SpeechErrorCodes.ENGINE_FAILED)
            waitForSnackbar(scenario, R.string.speech_dictation_failed)
            assertComposer(scenario, "Written")
            scenario.onActivity { activity -> assertEquals(sheetsBefore, activity.dictationRequestCount) }
            values["failureAfterText"] = "pass"

            // The plugin died before anything was heard: the sheet (D208).
            setComposer(scenario, "", 0, 0)
            sheetReplies("after the death")
            press(scenario)
            source.last.fail(SpeechErrorCodes.SESSION_CLOSED)
            waitUntil("the sheet answers") {
                var done = false
                scenario.onActivity { activity -> done = !activity.dictationPending && activity.hasWindowFocus() }
                done && composerText(scenario) == "after the death"
            }
            values["sheetAfterDeath"] = "pass"
        }
        SpeechDeviceReport.receipt("live-dictation-failures", values)
    }

    @Test
    fun withoutAUsableSessionTheMicrophoneOpensTheSheetAsIn172() {
        MacawSpeechTestHooks.availability =
            MacawSpeechAvailability(MacawSpeechStatus.NO_RECOGNITION_MODEL, contractVersion = 3, resident = false)
        environment.launchChat().use { scenario ->
            scenario.onActivity { activity -> assertFalse(activity.liveDictationAvailableForTest) }
            setComposer(scenario, "", 0, 0)
            sheetReplies("sheet text")
            scenario.onActivity { activity -> mic(activity).performClick() }
            waitUntil("the sheet answers") { composerText(scenario) == "sheet text" }
            assertEquals("No session was opened", 0, source.sessions.size)
        }
        SpeechDeviceReport.receipt("live-dictation-unusable", mapOf("sheet" to "pass"))
    }

    private fun press(scenario: ActivityScenario<ChatActivity>, opensSession: Boolean = true) {
        val before = source.sessions.size
        scenario.onActivity { activity -> mic(activity).performClick() }
        if (opensSession) waitUntil("a session opens") { source.sessions.size == before + 1 }
    }

    private fun active(scenario: ActivityScenario<ChatActivity>): Boolean {
        var active = false
        scenario.onActivity { activity -> active = activity.liveDictationActive }
        return active
    }

    private fun sheetReplies(text: String) {
        DictationIntents.testExtras = Bundle().apply {
            putInt(FakeRecognizerActivity.EXTRA_RESULT_CODE, Activity.RESULT_OK)
            putString(FakeRecognizerActivity.EXTRA_RESULT_TEXT, text)
        }
    }

    private fun send(activity: ChatActivity) {
        ChatTestEnvironment.views(activity.window.decorView)
            .first { view -> view.contentDescription == activity.getString(R.string.chat_send) }
            .performClick()
    }

    private fun grayed(activity: ChatActivity, text: String): Boolean {
        val editable = composer(activity).text
        val start = editable.indexOf(text).takeIf { it >= 0 } ?: return false
        return editable.getSpans(start, start + text.length, ForegroundColorSpan::class.java).any { span ->
            editable.getSpanStart(span) <= start && editable.getSpanEnd(span) >= start + text.length &&
                editable.getSpanFlags(span) and Spanned.SPAN_EXCLUSIVE_EXCLUSIVE != 0
        }
    }

    private fun limit() = NumberFormat.getIntegerInstance().format(16_384)

    private fun setComposer(scenario: ActivityScenario<ChatActivity>, text: String, start: Int, end: Int) {
        scenario.onActivity { activity ->
            composer(activity).apply {
                setText(text)
                requestFocus()
                setSelection(start, end)
            }
        }
    }

    private fun composerText(scenario: ActivityScenario<ChatActivity>): String {
        var text = ""
        scenario.onActivity { activity -> text = composer(activity).text.toString() }
        return text
    }

    private fun assertComposer(scenario: ActivityScenario<ChatActivity>, expected: String) {
        waitUntil("the input box shows the text", 5_000L) { composerText(scenario) == expected }
    }

    private fun waitForSnackbar(scenario: ActivityScenario<ChatActivity>, message: Int, vararg arguments: Any) {
        waitUntil("the snackbar shows") {
            var shown = false
            scenario.onActivity { activity -> shown = showsText(activity, activity.getString(message, *arguments)) }
            shown
        }
    }
}
