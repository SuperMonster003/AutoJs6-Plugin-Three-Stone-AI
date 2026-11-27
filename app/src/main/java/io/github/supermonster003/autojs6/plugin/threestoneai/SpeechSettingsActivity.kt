package io.github.supermonster003.autojs6.plugin.threestoneai

import android.os.Bundle
import android.widget.LinearLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.DictationProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.MacawSpeech
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.MacawSpeechAvailability
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.MacawSpeechStatus
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ReadAloudProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechAvailability
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechAvailabilityPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechDeviceProbe
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechDeviceState
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.SettingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingsPage
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.switchRow

/**
 * "Voice" settings subpage (roadmap D17, D80); its rows follow what the device offers. The status row tells live
 * dictation by 3-Macaw Speech's session apart from the recognizer sheet, and why live dictation is not available
 * (P6-1); 3-Macaw Speech is probed on every resume and the row updated in place.
 */
class SpeechSettingsActivity : ConfiguredActivity() {
    private lateinit var chatSettingsStore: ChatUiSettingsStore
    private lateinit var chatSettings: ChatUiSettings
    private var speechDeviceState: SpeechDeviceState? = null
    private var macawAvailability: MacawSpeechAvailability? = null
    private var cancelMacawProbe: (() -> Unit)? = null
    private var statusRow: SettingRow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatSettingsStore = ChatUiSettingsStore(applicationContext)
        chatSettings = chatSettingsStore.load()
        setContentView(settingsPage(R.string.app_settings_speech, ::buildContent))
    }

    override fun onResume() {
        super.onResume()
        // Recognizers and engines may be installed, enabled or removed while settings are open (D17).
        val built = speechDeviceState ?: return
        if (SpeechDeviceProbe.probe(this) != built) {
            recreate()
            return
        }
        probeMacaw()
    }

    override fun onPause() {
        cancelMacawProbe?.invoke()
        cancelMacawProbe = null
        super.onPause()
    }

    private fun probeMacaw() {
        cancelMacawProbe?.invoke()
        cancelMacawProbe = MacawSpeech.probeOnce(this) { availability ->
            cancelMacawProbe = null
            if (isFinishing || isDestroyed) return@probeOnce
            macawAvailability = availability
            val state = speechDeviceState ?: return@probeOnce
            statusRow?.summaryView?.text = speechStatusSummary(
                SpeechAvailabilityPolicy.resolve(state, chatSettings.preferMacawSpeech, availability),
            )
        }
    }

    private fun buildContent(content: LinearLayout) {
        val speech = SpeechDeviceProbe.probe(this).also { speechDeviceState = it }
        val availability = SpeechAvailabilityPolicy.resolve(speech, chatSettings.preferMacawSpeech, macawAvailability)
        content.addView(switchRow(
            title = getString(R.string.speech_auto_read_replies),
            summary = getString(
                if (availability.readAloudAvailable) R.string.speech_auto_read_replies_summary
                else R.string.speech_auto_read_unavailable,
            ),
            iconResource = R.drawable.ic_volume_up_24,
            checked = chatSettings.autoReadReplies && availability.readAloudAvailable,
        ) { checked -> saveChatSettings(chatSettings.copy(autoReadReplies = checked)) }.view.apply {
            tag = TAG_SPEECH_AUTO_READ
            if (!availability.readAloudAvailable) {
                isEnabled = false
                alpha = Ui.DISABLED_ALPHA
            }
        })
        // Shown while 3-Macaw Speech is installed even before its probe answers, so the page does not jump.
        if (availability.showPreferMacawSetting || MacawSpeech.installed(this)) {
            content.addView(switchRow(
                title = getString(R.string.speech_prefer_macaw),
                summary = getString(R.string.speech_prefer_macaw_summary),
                iconResource = R.drawable.ic_mic_24,
                checked = chatSettings.preferMacawSpeech,
            ) { checked ->
                saveChatSettings(chatSettings.copy(preferMacawSpeech = checked), recreate = true)
            }.view.apply { tag = TAG_SPEECH_PREFER_MACAW })
        }
        statusRow = settingRow(
            title = getString(R.string.speech_status_title),
            summary = speechStatusSummary(availability),
            iconResource = R.drawable.ic_info_24,
        ).also { row ->
            row.view.tag = TAG_SPEECH_STATUS
            content.addView(row.view)
        }
    }

    private fun speechStatusSummary(availability: SpeechAvailability): String = buildList {
        add(getString(when {
            availability.liveDictation -> R.string.speech_status_dictation_macaw_live
            availability.dictation == DictationProvider.MACAW -> R.string.speech_status_dictation_macaw
            availability.dictation == DictationProvider.SYSTEM -> R.string.speech_status_dictation_system
            else -> R.string.speech_status_dictation_none
        }))
        liveDictationNote(availability)?.let { note -> add(getString(note)) }
        add(getString(when (availability.readAloud) {
            ReadAloudProvider.MACAW -> R.string.speech_status_read_aloud_macaw
            ReadAloudProvider.SYSTEM -> R.string.speech_status_read_aloud_system
            ReadAloudProvider.NONE -> R.string.speech_status_read_aloud_none
        }))
        if (availability.showMacawInstallHint) add(getString(R.string.speech_status_install_macaw))
        add(getString(R.string.speech_status_privacy))
    }.joinToString("\n")

    /** Why the microphone does not dictate live, or what it still needs; only with "Prefer 3-Macaw Speech" on. */
    private fun liveDictationNote(availability: SpeechAvailability): Int? {
        if (!chatSettings.preferMacawSpeech) return null
        val macaw = availability.macaw
            ?: return R.string.speech_status_live_checking.takeIf { MacawSpeech.installed(this) }
        return when (macaw.status) {
            MacawSpeechStatus.NOT_INSTALLED, MacawSpeechStatus.AVAILABLE -> null
            MacawSpeechStatus.NOT_BINDABLE -> R.string.speech_status_live_unbindable
            MacawSpeechStatus.INCOMPATIBLE -> R.string.speech_status_live_update
            MacawSpeechStatus.NO_RECOGNITION_MODEL -> R.string.speech_status_live_model
            MacawSpeechStatus.NO_MICROPHONE_PERMISSION -> R.string.speech_status_live_microphone
        }
    }

    private fun saveChatSettings(updated: ChatUiSettings, recreate: Boolean = false) {
        chatSettings = updated
        chatSettingsStore.save(updated)
        if (recreate) recreate()
    }

    internal companion object {
        const val TAG_SPEECH_AUTO_READ = "speech-auto-read"
        const val TAG_SPEECH_PREFER_MACAW = "speech-prefer-macaw"
        const val TAG_SPEECH_STATUS = "speech-status"
    }
}
