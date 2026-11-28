package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.app.Instrumentation
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.mic
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.showsText
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P6-3: the voice conversation cases of P5-4 ([SpeechConversationCases]) with each round on an injected session
 * source ([FakeLiveDictationSource], nothing records), then what only the session has: the round's gray text in the
 * input box and its removal once sent (Q88), the microphone finishing a round, an empty final, the exit button and
 * leaving the chat while listening, the sheet for a round whose microphone is busy or whose model is missing (D209),
 * the permission screen, and the sheet throughout when 3-Macaw Speech has no usable session.
 */
class SpeechLiveConversationDeviceTest : SpeechConversationCases() {
    private val source = FakeLiveDictationSource()
    private val values = linkedMapOf<String, Any?>()

    override val live = true
    override val label = "live-conversation"
    override val failureMessage = R.string.speech_dictation_failed

    override fun createEnvironment() = ChatTestEnvironment(
        macaw = MacawSpeechAvailability(MacawSpeechStatus.AVAILABLE, contractVersion = 3, resident = true),
    ).also { environment ->
        MacawSpeechTestHooks.source = source
        SpeechDeviceProbe.stateOverride = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = false,
            anyTtsEngine = true,
        )
        // The sheet of a fallback round is the test APK's fake recognizer.
        DictationIntents.testPackage = environment.fakeRecognizerPackage
    }

    override fun hear(vararg steps: Heard) = source.scriptRounds(*steps)

    @Test
    fun theRoundShowsItsTextGrayAndSendsItAsTheVoiceMessage() {
        environment.launchChat().use { scenario ->
            scenario.onActivity { activity -> composer(activity).setText("my draft") }
            start(scenario)
            val round = awaitRound(scenario)
            assertEquals(LiveDictationMode.SINGLE_UTTERANCE, round.mode)
            scenario.onActivity { activity ->
                assertEquals("No sheet opened", 0, activity.dictationRequestCount)
                assertEquals(VoicePhase.LISTENING, activity.currentVoicePhase)
                assertEquals(activity.getString(R.string.speech_dictation_stop), mic(activity).contentDescription)
            }
            // Until the session is ready the microphone is not recording yet (A4, D225).
            assertTrue(statusShows(scenario, R.string.speech_conversation_preparing))
            scenario.onActivity { activity -> assertEquals(VoiceStatusLabel.PREPARING, activity.voiceStatusLabelForTest) }
            round.ready()
            assertTrue(statusShows(scenario, R.string.speech_conversation_listening))
            scenario.onActivity { activity -> assertEquals(VoiceStatusLabel.LISTENING, activity.voiceStatusLabelForTest) }
            round.partial("What")
            assertComposer(scenario, "my draft What")
            scenario.onActivity { activity -> assertTrue("The round's text is gray", grayed(activity, "What")) }
            SpeechDeviceReport.screenshot("live-conversation-partial")
            round.final("What time is it?")
            waitUntil("the round is sent") { environment.backend.prompts.size == 1 }
            assertEquals("What time is it?", environment.backend.prompts.single())
            // Sent as the voice message, the text leaves the input box; the draft stays (Q88).
            assertComposer(scenario, "my draft")
            assertEquals(1, round.finishes)
            values["graySentAndRemoved"] = "pass"

            // The next round: the exit button while listening cancels it and keeps what it showed (D215, D220).
            val next = awaitRound(scenario, sessions = 2, timeoutMillis = 20_000L)
            next.ready()
            next.partial("half a")
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>(ChatViewTags.VOICE_STATUS_ACTION).performClick()
            }
            waitForExit(scenario)
            assertEquals("Exit", exit(scenario))
            assertEquals(1, next.cancels)
            assertComposer(scenario, "my draft half a")
            scenario.onActivity { activity -> assertFalse("Nothing stays gray", grayed(activity, "half a")) }
            values["exitKeepsText"] = "pass"
        }
        SpeechDeviceReport.receipt("live-conversation-composer", values)
    }

    @Test
    fun theMicrophoneFinishesTheRoundAndAnEmptyFinalKeepsListening() {
        environment.launchChat().use { scenario ->
            start(scenario)
            val round = awaitRound(scenario)
            round.ready()
            // Noise: an empty final neither ends the round nor sends anything (D220).
            round.partial("uh")
            round.final("")
            scenario.onActivity { activity ->
                assertTrue(activity.liveDictationActive)
                assertEquals("", composer(activity).text.toString())
            }
            assertEquals(0, round.finishes)
            values["emptyFinalListens"] = "pass"

            // A tap on the microphone finishes the round: what was being said is its final and is sent.
            source.finishFinal = "Send it now"
            round.partial("Send")
            scenario.onActivity { activity -> mic(activity).performClick() }
            waitUntil("the finished round is sent") { environment.backend.prompts.size == 1 }
            assertEquals("Send it now", environment.backend.prompts.single())
            assertEquals(1, round.finishes)
            values["microphoneFinishes"] = "pass"

            // Leaving the chat while listening cancels the round and keeps its text, like live dictation.
            source.finishFinal = null
            val next = awaitRound(scenario, sessions = 2, timeoutMillis = 20_000L)
            next.ready()
            next.partial("kept words")
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue("onStop cancels", next.cancels == 1 && next.ended)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals("LeftChat", exit(scenario))
            assertComposer(scenario, "kept words")
            values["leaveKeepsText"] = "pass"
        }
        SpeechDeviceReport.receipt("live-conversation-round", values)
    }

    @Test
    fun aBusyMicrophoneOrAMissingModelUseTheSheetForThatRound() {
        environment.launchChat().use { scenario ->
            for (code in listOf(SpeechErrorCodes.SESSION_BUSY, SpeechErrorCodes.MODEL_MISSING)) {
                val promptsBefore = environment.backend.prompts.size
                var fallbacksBefore = 0
                var roundsBefore = 0
                scenario.onActivity { activity ->
                    fallbacksBefore = activity.voiceSheetFallbackCount
                    roundsBefore = activity.voiceRounds.size
                }
                environment.script(Activity.RESULT_OK to "from the sheet")
                hear(Heard.Said("from the session"), Heard.Canceled)
                source.openFailure = code
                start(scenario)
                waitForExit(scenario, timeoutMillis = 20_000L)
                assertEquals(
                    code,
                    listOf("from the sheet", "from the session"),
                    environment.backend.prompts.drop(promptsBefore),
                )
                scenario.onActivity { activity ->
                    assertEquals(code, fallbacksBefore + 1, activity.voiceSheetFallbackCount)
                    val rounds = activity.voiceRounds.drop(roundsBefore)
                    assertEquals(code, listOf(false, true), rounds.map { it.live })
                    assertEquals("RecognitionCanceled", activity.lastVoiceExit)
                }
                values["sheetRoundAfter$code"] = "pass"
            }
        }
        SpeechDeviceReport.receipt("live-conversation-fallback", values)
    }

    /** A4 (D225): the exit button works while the round still prepares, and no text was sent. */
    @Test
    fun theExitButtonEndsARoundThatIsStillPreparing() {
        environment.launchChat().use { scenario ->
            start(scenario)
            val round = awaitRound(scenario)
            scenario.onActivity { activity -> assertEquals(VoiceStatusLabel.PREPARING, activity.voiceStatusLabelForTest) }
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.findViewWithTag<View>(ChatViewTags.VOICE_STATUS_ACTION).performClick())
            }
            waitForExit(scenario)
            assertEquals("Exit", exit(scenario))
            assertEquals(1, round.cancels)
            assertTrue(environment.backend.prompts.isEmpty())
            // The ready that comes late is dropped: nothing listens any more.
            round.ready()
            scenario.onActivity { activity ->
                assertFalse(activity.liveDictationActive)
                assertEquals(null, activity.voiceStatusLabelForTest)
            }
        }
        SpeechDeviceReport.receipt("live-conversation-preparing", mapOf("exitWhilePreparing" to "pass"))
    }

    /**
     * F1 (3-stone-voice-1c, A5): another app keeps 3-Macaw Speech's microphone the whole time (3-Stove Agent's v1
     * resident listener): every round is `SESSION_BUSY`, so every round listens through the sheet (D209) and the
     * conversation goes on as usual until the sheet is canceled, without looping or crashing.
     */
    @Test
    fun aMicrophoneBusyEveryRoundUsesTheSheetEveryRound() {
        environment.launchChat().use { scenario ->
            environment.script(
                Activity.RESULT_OK to "sheet one",
                Activity.RESULT_OK to "sheet two",
                Activity.RESULT_OK to "sheet three",
                Activity.RESULT_CANCELED to "",
            )
            source.everyOpenFailure = SpeechErrorCodes.SESSION_BUSY
            try {
                start(scenario)
                waitForExit(scenario, timeoutMillis = 30_000L)
            } finally {
                source.everyOpenFailure = null
            }
            assertEquals(listOf("sheet one", "sheet two", "sheet three"), environment.backend.prompts.toList())
            assertEquals("each round tried the session first", 4, source.sessions.size)
            scenario.onActivity { activity ->
                assertEquals(4, activity.voiceSheetFallbackCount)
                // The canceled fourth sheet ends the conversation and is not a round.
                assertEquals(listOf(false, false, false), activity.voiceRounds.map { it.live })
                assertEquals("RecognitionCanceled", activity.lastVoiceExit)
                assertFalse(activity.isFinishing)
            }
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
        SpeechDeviceReport.receipt("live-conversation-busy-every-round", mapOf("sheetRounds" to 4, "sent" to 3))
    }

    @Test
    fun aDeniedMicrophoneEndsTheConversationAtMacawsPermissionScreen() {
        environment.launchChat().use { scenario ->
            val monitor = Instrumentation.ActivityMonitor(MacawSpeech.MICROPHONE_PERMISSION_ACTIVITY, null, true)
            environment.instrumentation.addMonitor(monitor)
            try {
                source.openFailure = SpeechErrorCodes.PERMISSION_DENIED
                start(scenario)
                waitUntil("the permission screen is requested") { monitor.hits == 1 }
                waitForExit(scenario)
                assertEquals("RecognitionCanceled", exit(scenario))
                waitUntil("the snackbar explains it") {
                    var shown = false
                    scenario.onActivity { activity ->
                        shown = showsText(activity, activity.getString(R.string.speech_live_dictation_permission))
                    }
                    shown
                }
                scenario.onActivity { activity -> assertEquals(0, activity.dictationRequestCount) }
            } finally {
                environment.instrumentation.removeMonitor(monitor)
            }
            assertTrue(environment.backend.prompts.isEmpty())
        }
        SpeechDeviceReport.receipt("live-conversation-permission", mapOf("permissionScreen" to "pass"))
    }

    @Test
    fun withoutAUsableSessionTheConversationListensThroughTheSheet() {
        MacawSpeechTestHooks.availability =
            MacawSpeechAvailability(MacawSpeechStatus.NO_RECOGNITION_MODEL, contractVersion = 3, resident = false)
        environment.script(Activity.RESULT_OK to "sheet one", Activity.RESULT_CANCELED to "")
        environment.launchChat().use { scenario ->
            scenario.onActivity { activity -> assertFalse(activity.liveDictationAvailableForTest) }
            start(scenario)
            waitForExit(scenario, timeoutMillis = 20_000L)
            assertEquals(listOf("sheet one"), environment.backend.prompts.toList())
            assertEquals("No session was opened", 0, source.sessions.size)
            scenario.onActivity { activity -> assertEquals(listOf(false), activity.voiceRounds.map { it.live }) }
        }
        SpeechDeviceReport.receipt("live-conversation-unusable", mapOf("sheet" to "pass"))
    }

    private fun awaitRound(
        scenario: ActivityScenario<ChatActivity>,
        sessions: Int = 1,
        timeoutMillis: Long = 10_000L,
    ): FakeLiveDictationSource.Session {
        waitUntil("round $sessions listens", timeoutMillis) {
            var listening = false
            scenario.onActivity { activity -> listening = activity.liveDictationActive }
            listening && source.sessions.size == sessions
        }
        return source.last
    }

    private fun grayed(activity: ChatActivity, text: String): Boolean {
        val editable = composer(activity).text
        val start = editable.indexOf(text).takeIf { it >= 0 } ?: return false
        return editable.getSpans(start, start + text.length, ForegroundColorSpan::class.java).any { span ->
            editable.getSpanStart(span) <= start && editable.getSpanEnd(span) >= start + text.length &&
                editable.getSpanFlags(span) and Spanned.SPAN_EXCLUSIVE_EXCLUSIVE != 0
        }
    }

    private fun assertComposer(scenario: ActivityScenario<ChatActivity>, expected: String) {
        waitUntil("the input box shows '$expected'", 5_000L) {
            var text = ""
            scenario.onActivity { activity -> text = composer(activity).text.toString() }
            text == expected
        }
    }
}
