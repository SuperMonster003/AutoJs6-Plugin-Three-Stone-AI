package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.hairline
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.sectionHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingsPage
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.switchRow

/** "Updates and about" settings subpage (roadmap D80); the only place with the version history. */
class UpdatesAboutActivity : ConfiguredActivity() {
    private lateinit var updateSettingsStore: AppUpdateSettingsStore
    private lateinit var updateController: AppUpdateController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSettingsStore = AppUpdateSettingsStore(applicationContext)
        updateController = AppUpdateController(this, updateSettingsStore)
        setContentView(settingsPage(R.string.settings_updates_about_title, ::buildContent))
    }

    override fun onDestroy() {
        if (::updateController.isInitialized) updateController.cancel()
        super.onDestroy()
    }

    private fun buildContent(content: LinearLayout) {
        content.addView(sectionHeader(R.string.app_settings_updates))
        content.addView(settingRow(
            title = getString(R.string.app_update_check),
            summary = currentVersionSummary(),
            iconResource = R.drawable.ic_restart_24,
            onClick = updateController::checkManually,
        ).view)
        content.addView(switchRow(
            title = getString(R.string.app_update_automatic),
            summary = getString(R.string.app_update_automatic_summary),
            iconResource = R.drawable.ic_download_24,
            checked = updateSettingsStore.automaticChecksEnabled,
        ) { checked -> updateSettingsStore.automaticChecksEnabled = checked }.view)
        content.addView(settingRow(
            title = getString(R.string.app_update_manage_ignored),
            summary = resources.getQuantityString(
                R.plurals.app_update_ignored_count,
                updateSettingsStore.ignoredTags.size,
                updateSettingsStore.ignoredTags.size,
            ),
            iconResource = R.drawable.ic_history_24,
            onClick = ::showIgnoredUpdatesDialog,
        ).view)
        content.addView(settingRow(
            title = getString(R.string.release_history_title),
            summary = getString(R.string.app_update_release_history_summary),
            iconResource = R.drawable.ic_article_24,
            onClick = { startActivity(Intent(this, ReleaseHistoryActivity::class.java)) },
        ).view)
        content.addView(hairline())

        content.addView(sectionHeader(R.string.app_settings_information))
        content.addView(settingRow(
            title = getString(R.string.about_app_and_developer),
            summary = getString(R.string.about_app_summary),
            iconResource = R.drawable.ic_info_24,
            onClick = { startActivity(Intent(this, AboutActivity::class.java)) },
        ).view)
    }

    private fun showIgnoredUpdatesDialog() {
        val ignored = updateSettingsStore.ignoredTags.sorted()
        if (ignored.isEmpty()) {
            materialDialog()
                .setTitle(R.string.app_update_manage_ignored)
                .setMessage(R.string.app_update_no_ignored)
                .setPositiveButton(android.R.string.ok, null)
                .show()
                .also(::tintDialogButtons)
            return
        }
        val selected = BooleanArray(ignored.size)
        materialDialog()
            .setTitle(R.string.app_update_manage_ignored)
            .setMultiChoiceItems(ignored.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.app_update_stop_ignoring) { _, _ ->
                updateSettingsStore.stopIgnoring(
                    ignored.filterIndexed { index, _ -> selected[index] },
                )
                recreate()
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun currentVersionSummary(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return getString(R.string.about_version_name, packageInfo.versionName.orEmpty())
    }
}
