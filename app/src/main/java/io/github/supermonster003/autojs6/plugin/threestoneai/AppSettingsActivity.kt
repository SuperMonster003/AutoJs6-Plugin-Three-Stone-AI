package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.SettingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.confirmedChoiceDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.hairline
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.sectionHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingsPage
import java.util.Locale
import org.autojs.plugin.ai.provider.api.AiProviderSettingsContract

/**
 * Settings home (roadmap D80): the shared appearance group, then one entry per subpage. The host's
 * "AI service settings" action still resolves here and continues to "Models and services" (D103,
 * D110), so Back from that page returns to this home and then to the host.
 */
class AppSettingsActivity : ConfiguredActivity() {
    internal var appearanceDialog: androidx.appcompat.app.AlertDialog? = null; private set
    internal var launcherIconDialog: androidx.appcompat.app.AlertDialog? = null; private set
    private var launcherIconRow: SettingRow? = null
    private lateinit var settingsStore: ApplicationSettingsStore
    private lateinit var settings: ApplicationSettings
    private lateinit var hostResult: AutoJs6HostSettingsResult

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!acceptsSettingsIntent(intent)) {
            finish()
            return
        }
        settingsStore = ApplicationSettingsStore(applicationContext)
        hostResult = AutoJs6HostSettingsClient.query(this)
        settings = settingsStore.load()
        setContentView(settingsPage(R.string.app_settings_title, ::buildSettingsContent))
        if (savedInstanceState == null && intent.action == AiProviderSettingsContract.ACTION_OPEN_SETTINGS) {
            // A real back stack (home below the target page) instead of a synthesized one (D110).
            startActivity(Intent(this, ModelManagerActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun hasUnconfirmedDialog() = appearanceDialog?.isShowing == true || launcherIconDialog?.isShowing == true

    override fun onDestroy() {
        appearanceDialog?.dismiss()
        launcherIconDialog?.dismiss()
        super.onDestroy()
    }

    private fun acceptsSettingsIntent(intent: Intent): Boolean {
        val action = intent.action ?: return true
        return action == AiProviderSettingsContract.ACTION_OPEN_SETTINGS &&
            intent.data == null &&
            intent.clipData == null &&
            intent.extras == null
    }

    private fun buildSettingsContent(content: LinearLayout) {
        content.addView(sectionHeader(R.string.app_settings_appearance))
        content.addView(settingRow(
            title = getString(R.string.app_settings_language),
            summary = languageSummary(settings.language),
            iconResource = R.drawable.ic_settings_language,
            onClick = ::showLanguageDialog,
        ).view)
        content.addView(settingRow(
            title = getString(R.string.app_settings_dark_mode),
            summary = darkModeSummary(settings.darkMode),
            iconResource = R.drawable.ic_settings_night,
            onClick = ::showDarkModeDialog,
        ).view)
        content.addView(settingRow(
            title = getString(R.string.app_settings_theme_color),
            summary = themeSummary(settings),
            iconResource = R.drawable.ic_settings_theme,
            onClick = ::showThemeColorDialog,
        ).view)
        launcherIconRow = settingRow(
            title = getString(R.string.launcher_icon_title),
            summary = getString(launcherIconLabels[LauncherIcons.current(this).ordinal]),
            iconResource = R.drawable.ic_settings_launcher,
            onClick = ::showLauncherIconDialog,
        ).also { it.view.tag = "launcher-icon" }
        content.addView(launcherIconRow!!.view)
        content.addView(hairline())

        SUBPAGES.forEach { page ->
            content.addView(settingRow(
                title = getString(page.titleResource),
                summary = getString(page.summaryResource),
                iconResource = page.iconResource,
                onClick = { startActivity(Intent(this, page.activity)) },
            ).view.apply { tag = page.tag })
        }
    }

    private fun showLauncherIconDialog() {
        val labels = launcherIconLabels.mapIndexed { index, resource ->
            val title = getString(resource)
            val note = when (LauncherIconMode.entries[index]) {
                LauncherIconMode.AUTO -> getString(R.string.launcher_icon_auto_note)
                LauncherIconMode.TRANSPARENT -> getString(R.string.launcher_icon_transparent_note)
                else -> null
            }
            if (note == null) title else android.text.SpannableString("$title\n$note").apply {
                setSpan(android.text.style.RelativeSizeSpan(14f / 16f), title.length + 1, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        launcherIconDialog = confirmedChoiceDialog(getString(R.string.launcher_icon_title), labels, LauncherIcons.current(this).ordinal) { index ->
            val result = runCatching { LauncherIcons.select(this, LauncherIconMode.entries[index]) }
            Toast.makeText(this, if (result.isSuccess) R.string.launcher_icon_applied_note else R.string.launcher_icon_failed, Toast.LENGTH_LONG).show()
            launcherIconRow?.summaryView?.text = getString(launcherIconLabels[LauncherIcons.current(this).ordinal])
        }.also { dialog -> dialog.listView.post { dialog.listView.setSelection(0) } }
    }

    internal val launcherIconLabels = listOf(R.string.launcher_icon_light, R.string.launcher_icon_dark,
        R.string.launcher_icon_auto, R.string.launcher_icon_transparent)

    private fun showThemeColorDialog() {
        hostResult = AutoJs6HostSettingsClient.query(this)
        val current = if (settings.themeSelection == AppThemeSelection.FOLLOW_AUTOJS6) null else settings.customThemeColor
        appearanceDialog = ThemeColorChooser.show(this, current, followedThemeColor(),
            ThemeColorChooser.Palette(appPalette.accent, appPalette.surface, appPalette.primaryText, appPalette.secondaryText, appPalette.outline),
            ThemeColorChooser.Labels(getString(R.string.app_settings_theme_color), getString(R.string.app_settings_follow_autojs6),
            getString(R.string.theme_picker_presets), getString(R.string.theme_picker_custom),
            getString(R.string.theme_picker_input), getString(R.string.theme_picker_invalid), getString(R.string.theme_picker_preview))) { color ->
                saveSettings(if (color == null) settings.copy(themeSelection = AppThemeSelection.FOLLOW_AUTOJS6)
                    else settings.copy(themeSelection = AppThemeSelection.CUSTOM, customThemeColor = color))
            }
    }

    private fun showDarkModeDialog() {
        hostResult = AutoJs6HostSettingsClient.query(this)
        val values = AppDarkMode.entries
        val labels = values.mapIndexed { index, value ->
            getString(value.labelResource()).let { label ->
                if (index == 0) {
                    followAutoJs6ChoiceLabel(label, quotedFollowSystemLabel())
                } else {
                    label
                }
            }
        }
        appearanceDialog = confirmedChoiceDialog(
            title = getString(R.string.app_settings_dark_mode),
            labels = labels,
            checkedIndex = values.indexOf(settings.darkMode),
        ) { index -> saveSettings(settings.copy(darkMode = values[index])) }
    }

    private fun showLanguageDialog() {
        hostResult = AutoJs6HostSettingsClient.query(this)
        val values = AppLanguage.entries
        val labels = values.mapIndexed { index, value ->
            getString(value.labelResource()).let { label ->
                if (index == 0) {
                    followAutoJs6ChoiceLabel(label, quotedFollowSystemLabel())
                } else {
                    label
                }
            }
        }
        appearanceDialog = confirmedChoiceDialog(
            title = getString(R.string.app_settings_language),
            labels = labels,
            checkedIndex = values.indexOf(settings.language),
        ) { index -> saveSettings(settings.copy(language = values[index])) }
    }

    private fun saveSettings(updated: ApplicationSettings) {
        settingsStore.save(updated)
        settings = updated
        Toast.makeText(this, R.string.app_settings_saved, Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun languageSummary(value: AppLanguage): String = when (value) {
        AppLanguage.FOLLOW_AUTOJS6 -> followAutoJs6Summary(resolvedHostLanguageLabel())
        else -> getString(value.labelResource())
    }

    private fun darkModeSummary(value: AppDarkMode): String = when (value) {
        AppDarkMode.FOLLOW_AUTOJS6 -> followAutoJs6Summary(
            getString(
                hostResult.snapshot?.darkModePolicy?.labelResource()
                    ?: R.string.app_settings_follow_system,
            ),
        )
        else -> getString(value.labelResource())
    }

    private fun themeSummary(value: ApplicationSettings): String = when (value.themeSelection) {
        AppThemeSelection.FOLLOW_AUTOJS6 -> followAutoJs6Summary(
            AppSettingsPolicy.colorHex(followedThemeColor()),
        )
        AppThemeSelection.CUSTOM -> AppSettingsPolicy.colorHex(value.customThemeColor)
    }

    private fun followAutoJs6Summary(resolvedValue: String): String = getString(
        R.string.app_settings_follow_autojs6_summary,
        resolvedValue,
    )

    private fun followedThemeColor(): Int = AppSettingsPolicy.resolveAutoJs6ThemeColor(
        hostResult.snapshot?.themeColorPrimary,
    )

    private fun resolvedHostLanguageLabel(): String {
        val languageTag = hostResult.snapshot?.resolvedLanguageTag
            ?.takeIf(String::isNotBlank)
            ?: return getString(R.string.app_settings_follow_system)
        AppSettingsPolicy.languageForResolvedTag(languageTag)?.let { language ->
            return getString(language.labelResource())
        }
        return Locale.forLanguageTag(languageTag)
            .getDisplayName(resources.configuration.locales[0])
            .takeIf(String::isNotBlank)
            ?: languageTag
    }

    private fun quotedFollowSystemLabel(): String =
        "\"${getString(R.string.app_settings_follow_system)}\""

    private fun followAutoJs6ChoiceLabel(title: String, fallbackValue: String): CharSequence {
        if (hostResult.selectable) return title
        val subtitle = getString(
            R.string.app_settings_follow_autojs6_fallback_subtitle,
            fallbackValue,
        )
        return SpannableString("$title\n$subtitle").apply {
            val start = title.length + 1
            setSpan(
                ForegroundColorSpan(appPalette.secondaryText),
                start,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(RelativeSizeSpan(14f / 16f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TypefaceSpan("sans-serif-light"), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun AppDarkMode.labelResource(): Int = when (this) {
        AppDarkMode.FOLLOW_AUTOJS6 -> R.string.app_settings_follow_autojs6
        AppDarkMode.FOLLOW_SYSTEM -> R.string.app_settings_follow_system
        AppDarkMode.LIGHT -> R.string.app_settings_always_light
        AppDarkMode.DARK -> R.string.app_settings_always_dark
    }

    private fun AutoJs6DarkModePolicy.labelResource(): Int = when (this) {
        AutoJs6DarkModePolicy.FOLLOW_SYSTEM -> R.string.app_settings_follow_system
        AutoJs6DarkModePolicy.LIGHT -> R.string.app_settings_always_light
        AutoJs6DarkModePolicy.DARK -> R.string.app_settings_always_dark
    }

    private fun AppLanguage.labelResource(): Int = when (this) {
        AppLanguage.FOLLOW_AUTOJS6 -> R.string.app_settings_follow_autojs6
        AppLanguage.FOLLOW_SYSTEM -> R.string.app_settings_follow_system
        AppLanguage.CHINESE_SIMPLIFIED -> R.string.app_language_zh_hans
        AppLanguage.CHINESE_TRADITIONAL_HONG_KONG -> R.string.app_language_zh_hant_hk
        AppLanguage.CHINESE_TRADITIONAL_TAIWAN -> R.string.app_language_zh_hant_tw
        AppLanguage.ENGLISH -> R.string.app_language_en
        AppLanguage.FRENCH -> R.string.app_language_fr
        AppLanguage.SPANISH -> R.string.app_language_es
        AppLanguage.JAPANESE -> R.string.app_language_ja
        AppLanguage.KOREAN -> R.string.app_language_ko
        AppLanguage.RUSSIAN -> R.string.app_language_ru
        AppLanguage.ARABIC -> R.string.app_language_ar
    }

    /** One settings subpage entry of the home (D80); [tag] lets tests find the row. */
    internal class Subpage(
        val tag: String,
        @StringRes val titleResource: Int,
        @StringRes val summaryResource: Int,
        @DrawableRes val iconResource: Int,
        val activity: Class<out android.app.Activity>,
    )

    internal companion object {
        val SUBPAGES = listOf(
            Subpage(
                "settings-models-services",
                R.string.model_manager_title,
                R.string.settings_models_services_summary,
                R.drawable.ic_model_24,
                ModelManagerActivity::class.java,
            ),
            Subpage(
                "settings-conversation",
                R.string.app_settings_conversation,
                R.string.settings_conversation_summary,
                R.drawable.ic_article_24,
                ConversationSettingsActivity::class.java,
            ),
            Subpage(
                "settings-speech",
                R.string.app_settings_speech,
                R.string.settings_speech_summary,
                R.drawable.ic_volume_up_24,
                SpeechSettingsActivity::class.java,
            ),
            Subpage(
                "settings-privacy",
                R.string.settings_privacy_title,
                R.string.settings_privacy_summary,
                R.drawable.ic_key_24,
                PrivacySettingsActivity::class.java,
            ),
            Subpage(
                "settings-updates-about",
                R.string.settings_updates_about_title,
                R.string.settings_updates_about_summary,
                R.drawable.ic_info_24,
                UpdatesAboutActivity::class.java,
            ),
        )
    }
}
