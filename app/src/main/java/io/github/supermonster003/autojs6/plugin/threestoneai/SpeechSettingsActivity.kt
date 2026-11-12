package io.github.supermonster003.autojs6.plugin.threestoneai

import android.os.Bundle
import android.widget.LinearLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.DictationProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ReadAloudProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechAvailability
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechAvailabilityPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechDeviceProbe
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechDeviceState
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingsPage
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.switchRow

/** "Voice" settings subpage (roadmap D17, D80); its rows follow what the device offers. */
class SpeechSettingsActivity : ConfiguredActivity() {
    private lateinit var chatSettingsStore: ChatUiSettingsStore
    private lateinit var chatSettings: ChatUiSettings
    private var speechDeviceState: SpeechDeviceState? = null

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
        if (SpeechDeviceProbe.probe(this) != built) recreate()
    }

    private fun buildContent(content: LinearLayout) {
        val speech = SpeechDeviceProbe.probe(this).also { speechDeviceState = it }
        val availability = SpeechAvailabilityPolicy.resolve(speech, chatSettings.preferMacawSpeech)
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
        if (availability.showPreferMacawSetting) {
            content.addView(switchRow(
                title = getString(R.string.speech_prefer_macaw),
                summary = getString(R.string.speech_prefer_macaw_summary),
                iconResource = R.drawable.ic_mic_24,
                checked = chatSettings.preferMacawSpeech,
            ) { checked ->
                saveChatSettings(chatSettings.copy(preferMacawSpeech = checked), recreate = true)
            }.view.apply { tag = TAG_SPEECH_PREFER_MACAW })
        }
        content.addView(settingRow(
            title = getString(R.string.speech_status_title),
            summary = speechStatusSummary(availability),
            iconResource = R.drawable.ic_info_24,
        ).view.apply { tag = TAG_SPEECH_STATUS })
    }

    private fun speechStatusSummary(availability: SpeechAvailability): String = buildList {
        add(getString(when (availability.dictation) {
            DictationProvider.MACAW -> R.string.speech_status_dictation_macaw
            DictationProvider.SYSTEM -> R.string.speech_status_dictation_system
            DictationProvider.NONE -> R.string.speech_status_dictation_none
        }))
        add(getString(when (availability.readAloud) {
            ReadAloudProvider.MACAW -> R.string.speech_status_read_aloud_macaw
            ReadAloudProvider.SYSTEM -> R.string.speech_status_read_aloud_system
            ReadAloudProvider.NONE -> R.string.speech_status_read_aloud_none
        }))
        if (availability.showMacawInstallHint) add(getString(R.string.speech_status_install_macaw))
        add(getString(R.string.speech_status_privacy))
    }.joinToString("\n")

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
