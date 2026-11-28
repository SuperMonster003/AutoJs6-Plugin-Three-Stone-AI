package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognizerIntent
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.mic
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * P6-1 with the real 3-Macaw Speech, whatever this device has: the probe matches what is installed; without a usable
 * session (3-Macaw Speech or AutoJs6 missing, the emulator) the microphone opens the sheet as in 1.7.2; with one (a
 * phone with 3-Macaw Speech, its recognition model and the microphone) a session opens, gets ready, cancels and finishes.
 * Nothing is played; the sessions record only the room for a moment.
 */
class MacawSpeechDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val main = Handler(Looper.getMainLooper())
    private var environment: ChatTestEnvironment? = null

    @After
    fun tearDown() {
        environment?.close()
        MacawSpeechTestHooks.availability = null
        MacawSpeechTestHooks.source = null
    }

    @Test
    fun theProbeMatchesWhatIsInstalled() {
        val availability = probe()
        val macaw = installed(MacawSpeech.PACKAGE)
        val host = installed(HOST)
        when {
            !macaw -> assertEquals(MacawSpeechStatus.NOT_INSTALLED, availability.status)
            // No AutoJs6: org.autojs.permission.PLUGIN does not exist, so binding throws SecurityException.
            !host -> assertEquals(MacawSpeechStatus.NOT_BINDABLE, availability.status)
            else -> assertTrue(availability.status.name, availability.status != MacawSpeechStatus.NOT_INSTALLED)
        }
        SpeechDeviceReport.receipt(
            "macaw-probe",
            mapOf(
                "macawInstalled" to macaw,
                "hostInstalled" to host,
                "macawVersion" to version(MacawSpeech.PACKAGE),
                "status" to availability.status.name,
                "contractVersion" to availability.contractVersion,
                "liveDictation" to availability.liveDictation,
                "resident" to availability.resident,
            ),
        )
    }

    @Test
    fun withoutAUsableSessionTheMicrophoneOpensTheSheet() {
        val probed = probe()
        assumeTrue("3-Macaw Speech dictates live on this device", !probed.liveDictation)
        val env = ChatTestEnvironment(macaw = null).also { environment = it }
        SpeechDeviceProbe.stateOverride = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = false,
            anyTtsEngine = false,
        )
        DictationIntents.testPackage = env.fakeRecognizerPackage
        DictationIntents.testExtras = Bundle().apply {
            putInt(FakeRecognizerActivity.EXTRA_RESULT_CODE, Activity.RESULT_OK)
            putString(FakeRecognizerActivity.EXTRA_RESULT_TEXT, "from the sheet")
        }
        env.launchChat().use { scenario ->
            waitUntil("the chat probed 3-Macaw Speech", 8_000L) {
                var probedInChat = false
                scenario.onActivity { activity -> probedInChat = activity.macawSpeechForTest != null }
                probedInChat
            }
            // Answered and counted by a blocking monitor (the chat may recreate itself once the AutoJs6 appearance
            // arrives on a cold start, which resets its own counters). Added only now: an intent without an action
            // matches any action filter, so it would also swallow the launch of the chat.
            val answer = Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("from the sheet"))
            val sheets = Instrumentation.ActivityMonitor(
                IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                Instrumentation.ActivityResult(Activity.RESULT_OK, answer),
                true,
            )
            instrumentation.addMonitor(sheets)
            var status = ""
            try {
                scenario.onActivity { activity ->
                    status = activity.macawSpeechForTest!!.status.name
                    composer(activity).setText("")
                    mic(activity).performClick()
                }
                waitUntil("the sheet answers") {
                    var text = ""
                    scenario.onActivity { activity -> text = composer(activity).text.toString() }
                    text == "from the sheet"
                }
            } finally {
                instrumentation.removeMonitor(sheets)
            }
            assertEquals("The sheet opened once", 1, sheets.hits)
            scenario.onActivity { activity -> assertEquals(0, activity.liveDictationRequestCount) }
            SpeechDeviceReport.receipt(
                "macaw-fallback",
                mapOf("status" to status, "macawInstalled" to installed(MacawSpeech.PACKAGE), "hostInstalled" to installed(HOST), "sheet" to "pass"),
            )
        }
    }

    @Test
    fun aRealSessionOpensCancelsAndFinishes() {
        val probed = probe()
        assumeTrue("3-Macaw Speech is not ready for a session here: ${probed.status}", probed.status == MacawSpeechStatus.AVAILABLE)
        // The chat is in front, as when its microphone is pressed: on API 34+ 3-Macaw Speech records for a visible
        // client only. The chat itself sees no 3-Macaw Speech (the hook), so the sessions below are the only ones.
        val env = ChatTestEnvironment().also { environment = it }
        val chat = env.launchChat()
        MacawSpeechTestHooks.availability = null
        val events = CopyOnWriteArrayList<String>()
        var connection: MacawSpeechConnection? = null
        instrumentation.runOnMainSync { connection = MacawSpeechConnection(context) {}.also(MacawSpeechConnection::connect) }
        try {
            waitUntil("the chat connection probes", 8_000L) { connection!!.availability != null }
            assertEquals(MacawSpeechStatus.AVAILABLE, connection!!.availability!!.status)

            // Opened, ready, cancelled: the end is local and immediate.
            val openedAt = SystemClock.elapsedRealtime()
            var readyAt = 0L
            var session: LiveDictationSession? = null
            instrumentation.runOnMainSync {
                session = connection.open(LiveDictationMode.CONTINUOUS, recorder(events) { readyAt = SystemClock.elapsedRealtime() })
            }
            awaitEvents("the session is ready", events, 15_000L) { "ready" in events }
            instrumentation.runOnMainSync { session!!.cancel() }
            awaitEvents("the cancel ends it", events, 10_000L) { events.any { it.startsWith("end:") } }
            assertEquals("end:${LiveDictationEnd.Cancelled}", events.last())
            val readyMillis = readyAt - openedAt

            // Opened again (the microphone was given back), ready, finished: 3-Macaw Speech ends it as stopped.
            events.clear()
            var finishedAt = 0L
            instrumentation.runOnMainSync {
                session = connection.open(LiveDictationMode.CONTINUOUS, recorder(events) {})
            }
            awaitEvents("the second session is ready", events, 15_000L) { "ready" in events }
            instrumentation.runOnMainSync {
                finishedAt = SystemClock.elapsedRealtime()
                session!!.finish()
            }
            awaitEvents("the finish ends it", events, 8_000L) { events.any { it.startsWith("end:") } }
            val stopMillis = SystemClock.elapsedRealtime() - finishedAt
            assertEquals("end:${LiveDictationEnd.Stopped}", events.last())
            SpeechDeviceReport.receipt(
                "macaw-session",
                mapOf(
                    "macawVersion" to version(MacawSpeech.PACKAGE),
                    "contractVersion" to probed.contractVersion,
                    "resident" to probed.resident,
                    "readyMillis" to readyMillis,
                    "stopMillis" to stopMillis,
                    "openCancel" to "pass",
                    "openFinish" to "pass",
                ),
            )
        } finally {
            instrumentation.runOnMainSync { connection?.disconnect() }
            chat.close()
        }
    }

    /**
     * A4 (D225): after 3-Macaw Speech's process is stopped, the first voice round says "preparing" from the long press
     * until the real session is ready, then "listening". Nothing is played and nothing is sent; the exit button ends it.
     */
    @Test
    fun theFirstRoundAfterAColdStartPreparesUntilTheSessionIsReady() {
        val probed = probe()
        assumeTrue("3-Macaw Speech is not ready for a session here: ${probed.status}", probed.status == MacawSpeechStatus.AVAILABLE)
        instrumentation.uiAutomation.executeShellCommand("am force-stop ${MacawSpeech.PACKAGE}").close()
        SystemClock.sleep(1_000L)
        val env = ChatTestEnvironment(macaw = null).also { environment = it }
        val settings = io.github.supermonster003.autojs6.plugin.threestoneai.ChatUiSettingsStore(env.context)
        settings.save(settings.load().copy(preferMacawSpeech = true))
        env.launchChat().use { scenario ->
            waitUntil("the chat probed 3-Macaw Speech", 10_000L) {
                var live = false
                scenario.onActivity { activity -> live = activity.liveDictationAvailableForTest }
                live
            }
            var pressedAt = 0L
            var readyAt = 0L
            var labelAtPress: VoiceStatusLabel? = null
            var labelAtReady: VoiceStatusLabel? = null
            scenario.onActivity { activity ->
                activity.liveDictationObserverForTest = object : LiveDictationListener {
                    override fun onReady() {
                        readyAt = SystemClock.elapsedRealtime()
                        labelAtReady = activity.voiceStatusLabelForTest
                    }

                    override fun onPartial(text: String) = Unit

                    override fun onFinal(text: String) = Unit

                    override fun onEnd(end: LiveDictationEnd) = Unit
                }
                pressedAt = SystemClock.elapsedRealtime()
                assertTrue(mic(activity).performLongClick())
                labelAtPress = activity.voiceStatusLabelForTest
            }
            waitUntil("the first round's session is ready", 20_000L) { readyAt != 0L }
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<android.view.View>(
                    io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags.VOICE_STATUS_ACTION,
                ).performClick()
            }
            waitUntil("the conversation ends", 10_000L) {
                var ended = false
                scenario.onActivity { activity -> ended = activity.currentVoicePhase == VoicePhase.IDLE && !activity.liveDictationActive }
                ended
            }
            SpeechDeviceReport.receipt(
                "macaw-cold-round",
                mapOf(
                    "macawVersion" to version(MacawSpeech.PACKAGE),
                    "labelAtPress" to labelAtPress,
                    "labelAtReady" to labelAtReady,
                    "preparingMillis" to readyAt - pressedAt,
                ),
            )
            assertEquals(VoiceStatusLabel.PREPARING, labelAtPress)
            assertEquals(VoiceStatusLabel.LISTENING, labelAtReady)
            assertTrue(env.backend.prompts.isEmpty())
        }
    }

    /** Names the events seen so far (never their text) when the wait times out. */
    private fun awaitEvents(what: String, events: List<String>, timeoutMillis: Long, condition: () -> Boolean) {
        try {
            waitUntil(what, timeoutMillis, condition)
        } catch (error: AssertionError) {
            throw AssertionError("${error.message}; events $events", error)
        }
    }

    /** Events without their text (D16): partials and finals are only counted. */
    private fun recorder(events: MutableList<String>, ready: () -> Unit) = object : LiveDictationListener {
        override fun onReady() {
            ready()
            events += "ready"
        }

        override fun onPartial(text: String) {
            events += "partial"
        }

        override fun onFinal(text: String) {
            events += "final"
        }

        override fun onEnd(end: LiveDictationEnd) {
            events += "end:$end"
        }
    }

    private fun probe(): MacawSpeechAvailability {
        MacawSpeechTestHooks.availability = null
        var result: MacawSpeechAvailability? = null
        main.post { MacawSpeech.probeOnce(context) { availability -> result = availability } }
        waitUntil("3-Macaw Speech is probed", MacawSpeech.PROBE_TIMEOUT_MILLIS + 3_000L) { result != null }
        return result!!
    }

    private fun installed(name: String) = version(name) != null

    private fun version(name: String): Long? = runCatching {
        @Suppress("DEPRECATION")
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(name, 0))
    }.getOrNull()

    private companion object {
        const val HOST = "org.autojs.autojs6"
    }
}
