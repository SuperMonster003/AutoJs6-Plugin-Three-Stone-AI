package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.AppSettingsActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatUiSettingsStore
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P5-1 on a real device: the "Voice" group follows what the device offers (roadmap D8, D17, D22).
 * Restores the chat settings afterwards.
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
    fun voiceGroupFollowsTheResolvableSpeechApps() {
        val store = ChatUiSettingsStore(context)
        store.save(store.load().copy(preferMacawSpeech = true))
        val state = SpeechDeviceProbe.probe(context)
        val preferred = SpeechAvailabilityPolicy.resolve(state, preferMacaw = true)
        val values = linkedMapOf<String, Any?>(
            "macawRecognizer" to state.macawRecognizer,
            "anyRecognizer" to state.anyRecognizer,
            "macawTtsEngine" to state.macawTtsEngine,
            "anyTtsEngine" to state.anyTtsEngine,
            "dictation" to preferred.dictation.name,
            "readAloud" to preferred.readAloud.name,
        )
        launch().use { scenario ->
            scenario.onActivity { activity -> verify(activity, preferred) }
            scenario.onActivity(::scrollToVoiceGroup)
            SpeechDeviceReport.screenshot("settings-voice-preferred")
            if (preferred.showPreferMacawSetting) {
                scenario.onActivity { activity ->
                    activity.window.decorView.findViewWithTag<View>(AppSettingsActivity.TAG_SPEECH_PREFER_MACAW)
                        .performClick()
                }
                instrumentation.waitForIdleSync()
                assertEquals(false, store.load().preferMacawSpeech)
                val off = SpeechAvailabilityPolicy.resolve(state, preferMacaw = false)
                scenario.onActivity { activity -> verify(activity, off) }
                scenario.onActivity(::scrollToVoiceGroup)
                SpeechDeviceReport.screenshot("settings-voice-not-preferred")
                values["dictationNotPreferred"] = off.dictation.name
                values["readAloudNotPreferred"] = off.readAloud.name
            }
        }
        values["preferRowShown"] = preferred.showPreferMacawSetting
        values["installHintShown"] = preferred.showMacawInstallHint
        SpeechDeviceReport.receipt("settings", values)
    }

    private fun verify(activity: AppSettingsActivity, expected: SpeechAvailability) {
        val root = activity.window.decorView
        val autoRead = root.findViewWithTag<View>(AppSettingsActivity.TAG_SPEECH_AUTO_READ)
        assertNotNull("Auto-read row", autoRead)
        assertEquals(expected.readAloudAvailable, autoRead.isEnabled)
        val prefer = root.findViewWithTag<View>(AppSettingsActivity.TAG_SPEECH_PREFER_MACAW)
        if (expected.showPreferMacawSetting) assertNotNull(prefer) else assertNull(prefer)
        val status = texts(root.findViewWithTag(AppSettingsActivity.TAG_SPEECH_STATUS))
            .joinToString("\n")
        val dictation = when (expected.dictation) {
            DictationProvider.MACAW -> R.string.speech_status_dictation_macaw
            DictationProvider.SYSTEM -> R.string.speech_status_dictation_system
            DictationProvider.NONE -> R.string.speech_status_dictation_none
        }
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
    }

    private fun scrollToVoiceGroup(activity: AppSettingsActivity) {
        val header = views(activity.window.decorView).filterIsInstance<TextView>()
            .first { view -> view.text.toString() == activity.getString(R.string.app_settings_speech) }
        var parent = header.parent
        while (parent != null && parent !is ScrollView) parent = parent.parent
        val scroll = parent as? ScrollView ?: return
        // The header's top is relative to its own parent; add the offsets up to the scroll content.
        var offset = header.top
        var current: View = header
        while (current.parent !== scroll) {
            current = current.parent as View
            offset += current.top
        }
        scroll.scrollTo(0, offset)
    }

    private fun texts(view: View): List<String> =
        views(view).filterIsInstance<TextView>().map { it.text.toString() }

    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) {
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) }
    } else {
        emptyList()
    }

    private fun launch() = ActivityScenario.launch<AppSettingsActivity>(
        Intent(context, AppSettingsActivity::class.java),
    )
}
