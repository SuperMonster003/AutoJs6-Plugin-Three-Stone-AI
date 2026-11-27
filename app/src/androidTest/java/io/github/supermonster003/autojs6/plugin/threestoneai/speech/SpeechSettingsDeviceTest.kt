package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatUiSettingsStore
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import io.github.supermonster003.autojs6.plugin.threestoneai.SpeechSettingsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P5-1 and P6-1 on a real device: the voice settings page follows what the device offers (roadmap D8, D17, D22, D80),
 * and its status row tells live dictation by 3-Macaw Speech apart from the sheet and says why it is not available
 * (D208). Restores the chat settings afterwards.
 */
class SpeechSettingsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences("chat-ui-settings", Context.MODE_PRIVATE)
    private lateinit var saved: Map<String, *>

    @Before
    fun backUp() {
        saved = preferences.all.toMap()
    }

    @After
    fun restore() {
        MacawSpeechTestHooks.availability = null
        preferences.edit().clear().apply {
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }.commit()
    }

    @Test
    fun voicePageFollowsTheResolvableSpeechApps() {
        val store = ChatUiSettingsStore(context)
        store.save(store.load().copy(preferMacawSpeech = true))
        val state = SpeechDeviceProbe.probe(context)
        val macaw = probeMacaw()
        val preferred = SpeechAvailabilityPolicy.resolve(state, preferMacaw = true, macaw = macaw)
        val values = linkedMapOf<String, Any?>(
            "macawRecognizer" to state.macawRecognizer,
            "anyRecognizer" to state.anyRecognizer,
            "macawTtsEngine" to state.macawTtsEngine,
            "anyTtsEngine" to state.anyTtsEngine,
            "dictation" to preferred.dictation.name,
            "readAloud" to preferred.readAloud.name,
            "macaw" to macaw.status.name,
            "liveDictation" to preferred.liveDictation,
        )
        launch().use { scenario ->
            awaitStatus(scenario, preferred)
            scenario.onActivity { activity -> verify(activity, preferred) }
            SpeechDeviceReport.screenshot("settings-voice-preferred")
            if (preferred.showPreferMacawSetting) {
                scenario.onActivity { activity ->
                    activity.window.decorView.findViewWithTag<View>(SpeechSettingsActivity.TAG_SPEECH_PREFER_MACAW)
                        .performClick()
                }
                instrumentation.waitForIdleSync()
                assertEquals(false, store.load().preferMacawSpeech)
                val off = SpeechAvailabilityPolicy.resolve(state, preferMacaw = false, macaw = macaw)
                awaitStatus(scenario, off)
                scenario.onActivity { activity -> verify(activity, off) }
                    SpeechDeviceReport.screenshot("settings-voice-not-preferred")
                values["dictationNotPreferred"] = off.dictation.name
                values["readAloudNotPreferred"] = off.readAloud.name
            }
        }
        values["preferRowShown"] = preferred.showPreferMacawSetting
        values["installHintShown"] = preferred.showMacawInstallHint
        SpeechDeviceReport.receipt("settings", values)
    }

    @Test
    fun statusTellsLiveDictationApartAndExplainsWhyNot() {
        val store = ChatUiSettingsStore(context)
        store.save(store.load().copy(preferMacawSpeech = true))
        val state = SpeechDeviceProbe.probe(context)
        val notes = linkedMapOf<String, Any?>()
        MacawSpeechStatus.entries.forEach { status ->
            val macaw = MacawSpeechAvailability(status, contractVersion = 3, resident = false)
            MacawSpeechTestHooks.availability = macaw
            val expected = SpeechAvailabilityPolicy.resolve(state, preferMacaw = true, macaw = macaw)
            launch().use { scenario ->
                awaitStatus(scenario, expected)
                scenario.onActivity { activity -> verify(activity, expected) }
                if (status == MacawSpeechStatus.AVAILABLE) SpeechDeviceReport.screenshot("settings-voice-live")
            }
            notes[status.name] = "pass"
        }
        SpeechDeviceReport.receipt("settings-live", notes)
    }

    /** The status row is built before 3-Macaw Speech answers its probe and updated in place. */
    private fun awaitStatus(scenario: ActivityScenario<SpeechSettingsActivity>, expected: SpeechAvailability) {
        ChatTestEnvironment.waitUntil("the status row shows the probed state", 8_000L) {
            var shown = false
            scenario.onActivity { activity -> shown = status(activity).contains(activity.getString(dictationLine(expected))) }
            shown
        }
    }

    private fun dictationLine(expected: SpeechAvailability): Int = when {
        expected.liveDictation -> R.string.speech_status_dictation_macaw_live
        expected.dictation == DictationProvider.MACAW -> R.string.speech_status_dictation_macaw
        expected.dictation == DictationProvider.SYSTEM -> R.string.speech_status_dictation_system
        else -> R.string.speech_status_dictation_none
    }

    private fun status(activity: SpeechSettingsActivity): String =
        texts(activity.window.decorView.findViewWithTag(SpeechSettingsActivity.TAG_SPEECH_STATUS)).joinToString("\n")

    private fun probeMacaw(): MacawSpeechAvailability {
        var result: MacawSpeechAvailability? = null
        instrumentation.runOnMainSync { MacawSpeech.probeOnce(context) { availability -> result = availability } }
        ChatTestEnvironment.waitUntil("3-Macaw Speech is probed", MacawSpeech.PROBE_TIMEOUT_MILLIS + 3_000L) { result != null }
        return result!!
    }

    private fun verify(activity: SpeechSettingsActivity, expected: SpeechAvailability) {
        val root = activity.window.decorView
        val autoRead = root.findViewWithTag<View>(SpeechSettingsActivity.TAG_SPEECH_AUTO_READ)
        assertNotNull("Auto-read row", autoRead)
        assertEquals(expected.readAloudAvailable, autoRead.isEnabled)
        val prefer = root.findViewWithTag<View>(SpeechSettingsActivity.TAG_SPEECH_PREFER_MACAW)
        // Decided when the page is built, before the probe answers: the resolvable services, or 3-Macaw Speech being
        // installed at all, so the page does not jump when the probe ends.
        val built = SpeechAvailabilityPolicy.resolve(SpeechDeviceProbe.probe(activity), preferMacaw = true, macaw = null)
        if (built.showPreferMacawSetting || MacawSpeech.installed(activity)) assertNotNull(prefer) else assertNull(prefer)
        val status = texts(root.findViewWithTag(SpeechSettingsActivity.TAG_SPEECH_STATUS))
            .joinToString("\n")
        val dictation = dictationLine(expected)
        val reading = when (expected.readAloud) {
            ReadAloudProvider.MACAW -> R.string.speech_status_read_aloud_macaw
            ReadAloudProvider.SYSTEM -> R.string.speech_status_read_aloud_system
            ReadAloudProvider.NONE -> R.string.speech_status_read_aloud_none
        }
        assertTrue(status, activity.getString(dictation) in status)
        assertTrue(status, activity.getString(reading) in status)
        assertEquals(
            expected.showMacawInstallHint,
            activity.getString(R.string.speech_status_install_macaw) in status,
        )
        val note = when (expected.macaw?.status) {
            MacawSpeechStatus.NOT_BINDABLE -> R.string.speech_status_live_unbindable
            MacawSpeechStatus.INCOMPATIBLE -> R.string.speech_status_live_update
            MacawSpeechStatus.NO_RECOGNITION_MODEL -> R.string.speech_status_live_model
            MacawSpeechStatus.NO_MICROPHONE_PERMISSION -> R.string.speech_status_live_microphone
            else -> null
        }?.takeIf { ChatUiSettingsStore(activity).load().preferMacawSpeech }
        listOf(
            R.string.speech_status_live_unbindable,
            R.string.speech_status_live_update,
            R.string.speech_status_live_model,
            R.string.speech_status_live_microphone,
            R.string.speech_status_live_checking,
        ).forEach { line -> assertEquals(status, line == note, activity.getString(line) in status) }
    }

    private fun texts(view: View): List<String> =
        views(view).filterIsInstance<TextView>().map { it.text.toString() }

    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) {
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) }
    } else {
        emptyList()
    }

    private fun launch() = ActivityScenario.launch<SpeechSettingsActivity>(
        Intent(context, SpeechSettingsActivity::class.java),
    )
}
