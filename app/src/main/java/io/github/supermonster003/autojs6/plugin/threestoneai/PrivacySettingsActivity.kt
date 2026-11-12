package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.provider.RuntimeAccessManagementActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.sectionHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingsPage

/** "Privacy and permissions" settings subpage (roadmap D80): which apps may use AI here. */
class PrivacySettingsActivity : ConfiguredActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(settingsPage(R.string.settings_privacy_title, ::buildContent))
    }

    private fun buildContent(content: LinearLayout) {
        content.addView(sectionHeader(R.string.ai_runtime_access_section))
        content.addView(settingRow(
            title = getString(R.string.ai_runtime_access_title),
            summary = getString(R.string.ai_runtime_access_summary),
            iconResource = R.drawable.ic_person_24,
            onClick = { startActivity(Intent(this, RuntimeAccessManagementActivity::class.java)) },
        ).view)
    }
}
