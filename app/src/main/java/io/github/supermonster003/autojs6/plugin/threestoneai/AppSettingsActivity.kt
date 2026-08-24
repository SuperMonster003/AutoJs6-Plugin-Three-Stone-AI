package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class AppSettingsActivity : ConfiguredActivity() {
    private lateinit var settingsStore: ApplicationSettingsStore
    private lateinit var settings: ApplicationSettings
    private lateinit var hostResult: AutoJs6HostSettingsResult

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = ApplicationSettingsStore(applicationContext)
        hostResult = AutoJs6HostSettingsClient.query(this)
        val stored = settingsStore.load()
        settings = if (hostResult.selectable) stored else {
            AppSettingsPolicy.fallbackWithoutAutoJs6(stored).also { fallback ->
                if (hostResult.definitiveAbsence && fallback != stored) settingsStore.save(fallback)
            }
        }
        setContentView(createContentView())
    }

    private fun createContentView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)
        addView(createAppToolbar(R.string.app_settings_title, showBack = true))
        addView(ScrollView(context).apply {
            isFillViewport = true
            addView(createSettingsContent())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        applySystemBarInsets(this)
    }

    private fun createSettingsContent(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(0, dp(8), 0, dp(24))

        addView(category(R.string.app_settings_appearance))
        addView(settingRow(
            title = getString(R.string.app_settings_theme_color),
            summary = themeSummary(settings),
            onClick = ::showThemeColorDialog,
        ))
        addView(divider())
        addView(settingRow(
            title = getString(R.string.app_settings_dark_mode),
            summary = getString(settings.darkMode.labelResource()),
            onClick = ::showDarkModeDialog,
        ))
        addView(divider())
        addView(settingRow(
            title = getString(R.string.app_settings_language),
            summary = getString(settings.language.labelResource()),
            onClick = ::showLanguageDialog,
        ))
        addView(TextView(context).apply {
            text = getString(
                if (hostResult.selectable) {
                    R.string.app_settings_follow_autojs6_explanation
                } else {
                    R.string.app_settings_follow_autojs6_unavailable_explanation
                },
            )
            textSize = 12.5f
            setTextColor(appPalette.secondaryText)
            setLineSpacing(0f, 1.12f)
            setPaddingRelative(dp(20), dp(10), dp(20), dp(16))
        })

        addView(category(R.string.app_settings_information))
        addView(settingRow(
            title = getString(R.string.about_app_and_developer),
            summary = getString(R.string.about_app_summary),
            onClick = { startActivity(Intent(this@AppSettingsActivity, AboutActivity::class.java)) },
        ))
        addView(divider())
        addView(settingRow(
            title = getString(R.string.release_history_title),
            summary = currentVersionSummary(),
            onClick = {
                startActivity(Intent(this@AppSettingsActivity, ReleaseHistoryActivity::class.java))
            },
        ))
    }

    private fun category(titleResource: Int) = TextView(this).apply {
        text = getString(titleResource)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(appPalette.accent)
        setPaddingRelative(dp(20), dp(14), dp(20), dp(7))
    }

    private fun settingRow(title: String, summary: String, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(68)
            isClickable = true
            isFocusable = true
            contentDescription = "$title, $summary"
            setPaddingRelative(dp(20), dp(12), dp(20), dp(12))
            applySelectableBackground(this)
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTextColor(appPalette.primaryText)
            })
            addView(TextView(context).apply {
                text = summary
                textSize = 13f
                setTextColor(appPalette.secondaryText)
                setPaddingRelative(0, dp(3), 0, 0)
            })
            setOnClickListener { onClick() }
        }

    private fun divider() = View(this).apply {
        setBackgroundColor(appPalette.divider)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1),
        ).apply { marginStart = dp(20) }
    }

    private fun showThemeColorDialog() {
        val choices = listOf(
            ThemeChoice(R.string.app_settings_follow_autojs6, null, true),
            ThemeChoice(
                R.string.app_settings_theme_three_stone_ai,
                AppSettingsPolicy.THREE_STONE_AI_THEME_COLOR,
            ),
            ThemeChoice(R.string.app_settings_theme_teal, 0xFF007C8A.toInt()),
            ThemeChoice(R.string.app_settings_theme_blue, 0xFF3F51B5.toInt()),
            ThemeChoice(R.string.app_settings_theme_green, 0xFF2E7D32.toInt()),
            ThemeChoice(R.string.app_settings_theme_purple, 0xFF7E57C2.toInt()),
            ThemeChoice(R.string.app_settings_theme_custom, null),
        )
        val selected = when (settings.themeSelection) {
            AppThemeSelection.FOLLOW_AUTOJS6 -> 0
            AppThemeSelection.CUSTOM -> choices.indexOfFirst { it.color == settings.customThemeColor }
                .takeIf { it >= 1 } ?: choices.lastIndex
        }
        val labels = choices.mapIndexed { index, choice ->
            val title = getString(choice.labelResource)
            when {
                index == 0 && !hostResult.selectable -> getString(
                    R.string.app_settings_follow_autojs6_unavailable,
                )
                choice.color != null -> "$title (${AppSettingsPolicy.colorHex(choice.color)})"
                else -> title
            }
        }.toTypedArray()
        val adapter = ChoiceAdapter(labels, disabledIndex = 0.takeUnless { hostResult.selectable })
        AlertDialog.Builder(this)
            .setTitle(R.string.app_settings_theme_color)
            .setSingleChoiceItems(adapter, selected) { dialog, index ->
                if (!adapter.isEnabled(index)) return@setSingleChoiceItems
                dialog.dismiss()
                val choice = choices[index]
                when {
                    choice.followAutoJs6 -> saveSettings(
                        settings.copy(themeSelection = AppThemeSelection.FOLLOW_AUTOJS6),
                    )
                    choice.color != null -> saveSettings(
                        settings.copy(
                            themeSelection = AppThemeSelection.CUSTOM,
                            customThemeColor = choice.color,
                        ),
                    )
                    else -> showCustomThemeColorDialog()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun showCustomThemeColorDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.app_settings_custom_color_hint)
            setText(AppSettingsPolicy.colorHex(settings.customThemeColor))
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(7))
            setTextColor(appPalette.primaryText)
            setHintTextColor(appPalette.secondaryText)
            backgroundTintList = ColorStateList.valueOf(appPalette.accent)
            setPaddingRelative(dp(4), dp(8), dp(4), dp(8))
        }
        val container = LinearLayout(this).apply {
            setPaddingRelative(dp(22), 0, dp(22), 0)
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.app_settings_custom_color_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_settings_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val color = AppSettingsPolicy.parseOpaqueColor(input.text.toString())
                if (color == null) {
                    input.error = getString(R.string.app_settings_custom_color_error)
                } else {
                    dialog.dismiss()
                    saveSettings(
                        settings.copy(
                            themeSelection = AppThemeSelection.CUSTOM,
                            customThemeColor = color,
                        ),
                    )
                }
            }
        }
        dialog.show()
        tintDialogButtons(dialog)
        input.requestFocus()
    }

    private fun showDarkModeDialog() {
        val values = AppDarkMode.entries
        val labels = values.mapIndexed { index, value ->
            if (index == 0 && !hostResult.selectable) {
                getString(R.string.app_settings_follow_autojs6_unavailable)
            } else {
                getString(value.labelResource())
            }
        }.toTypedArray()
        val adapter = ChoiceAdapter(labels, disabledIndex = 0.takeUnless { hostResult.selectable })
        AlertDialog.Builder(this)
            .setTitle(R.string.app_settings_dark_mode)
            .setSingleChoiceItems(adapter, values.indexOf(settings.darkMode)) { dialog, index ->
                if (!adapter.isEnabled(index)) return@setSingleChoiceItems
                dialog.dismiss()
                saveSettings(settings.copy(darkMode = values[index]))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun showLanguageDialog() {
        val values = AppLanguage.entries
        val labels = values.mapIndexed { index, value ->
            if (index == 0 && !hostResult.selectable) {
                getString(R.string.app_settings_follow_autojs6_unavailable)
            } else {
                getString(value.labelResource())
            }
        }.toTypedArray()
        val adapter = ChoiceAdapter(labels, disabledIndex = 0.takeUnless { hostResult.selectable })
        AlertDialog.Builder(this)
            .setTitle(R.string.app_settings_language)
            .setSingleChoiceItems(adapter, values.indexOf(settings.language)) { dialog, index ->
                if (!adapter.isEnabled(index)) return@setSingleChoiceItems
                dialog.dismiss()
                saveSettings(settings.copy(language = values[index]))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun saveSettings(updated: ApplicationSettings) {
        settingsStore.save(updated)
        settings = updated
        Toast.makeText(this, R.string.app_settings_saved, Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun themeSummary(value: ApplicationSettings): String = when (value.themeSelection) {
        AppThemeSelection.FOLLOW_AUTOJS6 -> getString(
            R.string.app_settings_theme_follow_summary,
            AppSettingsPolicy.colorHex(
                hostResult.snapshot?.themeColorPrimary
                    ?: AppSettingsPolicy.AUTOJS6_DEFAULT_THEME_COLOR,
            ),
        )
        AppThemeSelection.CUSTOM -> AppSettingsPolicy.colorHex(value.customThemeColor)
    }

    private fun currentVersionSummary(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return getString(R.string.about_version_name, packageInfo.versionName.orEmpty())
    }

    private fun AppDarkMode.labelResource(): Int = when (this) {
        AppDarkMode.FOLLOW_AUTOJS6 -> R.string.app_settings_follow_autojs6
        AppDarkMode.FOLLOW_SYSTEM -> R.string.app_settings_follow_system
        AppDarkMode.LIGHT -> R.string.app_settings_always_light
        AppDarkMode.DARK -> R.string.app_settings_always_dark
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

    private fun applySelectableBackground(view: View) {
        val value = TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            view.setBackgroundResource(value.resourceId)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class ThemeChoice(
        val labelResource: Int,
        val color: Int?,
        val followAutoJs6: Boolean = false,
    )

    private inner class ChoiceAdapter(
        labels: Array<String>,
        private val disabledIndex: Int?,
    ) : ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, labels) {
        override fun isEnabled(position: Int): Boolean = position != disabledIndex

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getView(position, convertView, parent).apply {
                alpha = if (isEnabled(position)) 1f else DISABLED_ALPHA
                isEnabled = isEnabled(position)
                (this as? CheckedTextView)?.apply {
                    setTextColor(if (isEnabled(position)) appPalette.primaryText else appPalette.secondaryText)
                    checkMarkTintList = controlTintList()
                }
            }
    }

    private companion object {
        const val DISABLED_ALPHA = 0.42f
    }
}
