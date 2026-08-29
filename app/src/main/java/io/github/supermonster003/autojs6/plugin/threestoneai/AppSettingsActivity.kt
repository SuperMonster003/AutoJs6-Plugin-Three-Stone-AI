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
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import org.autojs.plugin.ai.provider.api.AiProviderSettingsContract

class AppSettingsActivity : ConfiguredActivity() {
    private lateinit var settingsStore: ApplicationSettingsStore
    private lateinit var settings: ApplicationSettings
    private lateinit var hostResult: AutoJs6HostSettingsResult
    private lateinit var chatSettingsStore: ChatUiSettingsStore
    private lateinit var chatSettings: ChatUiSettings
    private lateinit var updateSettingsStore: AppUpdateSettingsStore
    private lateinit var updateController: AppUpdateController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!acceptsSettingsIntent(intent)) {
            finish()
            return
        }
        settingsStore = ApplicationSettingsStore(applicationContext)
        hostResult = AutoJs6HostSettingsClient.query(this)
        settings = settingsStore.load()
        chatSettingsStore = ChatUiSettingsStore(applicationContext)
        chatSettings = chatSettingsStore.load()
        updateSettingsStore = AppUpdateSettingsStore(applicationContext)
        updateController = AppUpdateController(this, updateSettingsStore)
        setContentView(createContentView())
    }

    override fun onDestroy() {
        if (::updateController.isInitialized) updateController.cancel()
        super.onDestroy()
    }

    private fun acceptsSettingsIntent(intent: Intent): Boolean {
        val action = intent.action ?: return true
        return action == AiProviderSettingsContract.ACTION_OPEN_SETTINGS &&
            intent.data == null &&
            intent.clipData == null &&
            intent.extras == null
    }

    private fun createContentView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)
        val statusBarBackground = createStatusBarBackground()
        addView(
            statusBarBackground,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0),
        )
        addView(createAppToolbar(R.string.app_settings_title, showBack = true))
        addView(ScrollView(context).apply {
            isFillViewport = true
            addView(createSettingsContent())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        applySystemBarInsets(this, statusBarBackground)
    }

    private fun createSettingsContent(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(0, dp(8), 0, dp(24))

        addView(category(R.string.app_settings_appearance))
        addView(settingRow(
            title = getString(R.string.app_settings_language),
            summary = followAwareLabel(settings.language == AppLanguage.FOLLOW_AUTOJS6) {
                getString(settings.language.labelResource())
            },
            onClick = ::showLanguageDialog,
        ))
        addView(divider())
        addView(settingRow(
            title = getString(R.string.app_settings_dark_mode),
            summary = followAwareLabel(settings.darkMode == AppDarkMode.FOLLOW_AUTOJS6) {
                getString(settings.darkMode.labelResource())
            },
            onClick = ::showDarkModeDialog,
        ))
        addView(divider())
        addView(settingRow(
            title = getString(R.string.app_settings_theme_color),
            summary = themeSummary(settings),
            onClick = ::showThemeColorDialog,
        ))
        // addView(TextView(context).apply {
        //     text = getString(
        //         if (hostResult.selectable) {
        //             R.string.app_settings_follow_autojs6_explanation
        //         } else {
        //             R.string.app_settings_follow_autojs6_unavailable_explanation
        //         },
        //     )
        //     textSize = 12.5f
        //     setTextColor(appPalette.secondaryText)
        //     setLineSpacing(0f, 1.12f)
        //     setPaddingRelative(dp(20), dp(10), dp(20), dp(16))
        // })

        addView(category(R.string.app_settings_conversation))
        addView(settingRow(
            title = getString(R.string.chat_font_size),
            summary = getString(chatSettings.fontSize.labelResource()),
            onClick = ::showFontSizeDialog,
        ))
        addView(divider())
        addView(switchSettingRow(
            title = getString(R.string.chat_follow_streaming_output),
            summary = getString(R.string.app_settings_follow_streaming_summary),
            checked = chatSettings.followStreamingOutput,
        ) { checked -> saveChatSettings(chatSettings.copy(followStreamingOutput = checked)) })
        addView(divider())
        addView(switchSettingRow(
            title = getString(R.string.chat_show_generation_usage),
            summary = getString(R.string.app_settings_show_usage_summary),
            checked = chatSettings.showGenerationUsage,
        ) { checked -> saveChatSettings(chatSettings.copy(showGenerationUsage = checked)) })
        addView(divider())
        addView(settingRow(
            title = getString(R.string.chat_enter_key_behavior),
            summary = getString(chatSettings.enterKeyBehavior.labelResource()),
            onClick = ::showEnterKeyDialog,
        ))
        addView(divider())
        addView(settingRow(
            title = getString(R.string.chat_settings_generation_section),
            summary = generationSettingsSummary(),
            onClick = ::showGenerationSettingsDialog,
        ))

        addView(category(R.string.app_settings_updates))
        addView(settingRow(
            title = getString(R.string.app_update_check),
            summary = currentVersionSummary(),
            onClick = updateController::checkManually,
        ))
        addView(divider())
        addView(switchSettingRow(
            title = getString(R.string.app_update_automatic),
            summary = getString(R.string.app_update_automatic_summary),
            checked = updateSettingsStore.automaticChecksEnabled,
        ) { checked -> updateSettingsStore.automaticChecksEnabled = checked })
        addView(divider())
        addView(settingRow(
            title = getString(R.string.app_update_manage_ignored),
            summary = resources.getQuantityString(
                R.plurals.app_update_ignored_count,
                updateSettingsStore.ignoredTags.size,
                updateSettingsStore.ignoredTags.size,
            ),
            onClick = ::showIgnoredUpdatesDialog,
        ))
        addView(divider())
        addView(settingRow(
            title = getString(R.string.release_history_title),
            summary = getString(R.string.app_update_release_history_summary),
            onClick = {
                startActivity(Intent(this@AppSettingsActivity, ReleaseHistoryActivity::class.java))
            },
        ))

        addView(category(R.string.app_settings_information))
        addView(settingRow(
            title = getString(R.string.about_app_and_developer),
            summary = getString(R.string.about_app_summary),
            onClick = { startActivity(Intent(this@AppSettingsActivity, AboutActivity::class.java)) },
        ))
    }.also(::applyThemeToControls)

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

    private fun switchSettingRow(
        title: String,
        summary: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        minimumHeight = dp(72)
        isClickable = true
        isFocusable = true
        contentDescription = "$title, $summary"
        setPaddingRelative(dp(20), dp(10), dp(16), dp(10))
        applySelectableBackground(this)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTextColor(appPalette.primaryText)
            })
            addView(TextView(context).apply {
                text = summary
                textSize = 13f
                setTextColor(appPalette.secondaryText)
                setPaddingRelative(0, dp(3), dp(12), 0)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = SwitchCompat(context).apply {
            isChecked = checked
            thumbTintList = switchThumbTintList()
            trackTintList = switchTrackTintList()
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        addView(toggle)
        setOnClickListener { toggle.toggle() }
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

    private fun showFontSizeDialog() {
        val values = ChatFontSize.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_font_size)
            .setSingleChoiceItems(
                values.map { getString(it.labelResource()) }.toTypedArray(),
                values.indexOf(chatSettings.fontSize),
            ) { dialog, index ->
                dialog.dismiss()
                saveChatSettings(chatSettings.copy(fontSize = values[index]), recreate = true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun showEnterKeyDialog() {
        val values = EnterKeyBehavior.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_enter_key_behavior)
            .setSingleChoiceItems(
                values.map { getString(it.labelResource()) }.toTypedArray(),
                values.indexOf(chatSettings.enterKeyBehavior),
            ) { dialog, index ->
                dialog.dismiss()
                saveChatSettings(chatSettings.copy(enterKeyBehavior = values[index]), recreate = true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun showGenerationSettingsDialog() {
        val working = chatSettings
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(22), dp(6), dp(22), dp(16))
        }
        val unlimitedTokens = themedCheckBox(
            R.string.chat_maximum_output_tokens_unlimited,
            working.maximumOutputTokens == null,
        )
        container.addView(unlimitedTokens)
        container.addView(fieldLabel(R.string.chat_maximum_output_tokens))
        val maximumTokensInput = settingsEditText(
            working.maximumOutputTokensDraft.toString(),
            InputType.TYPE_CLASS_NUMBER,
        )
        container.addView(maximumTokensInput)
        fun updateMaximumTokensState(unlimited: Boolean) {
            maximumTokensInput.isEnabled = !unlimited
            maximumTokensInput.alpha = if (unlimited) DISABLED_ALPHA else 1f
        }
        updateMaximumTokensState(unlimitedTokens.isChecked)
        unlimitedTokens.setOnCheckedChangeListener { _, checked ->
            updateMaximumTokensState(checked)
        }

        val useModelDefaults = themedCheckBox(
            R.string.chat_use_model_sampling_defaults,
            working.useModelSamplingDefaults,
        ).apply { setPaddingRelative(0, dp(12), 0, 0) }
        container.addView(useModelDefaults)
        container.addView(fieldLabel(R.string.chat_temperature))
        val temperatureInput = settingsEditText(
            working.temperature.toString(),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
        )
        container.addView(temperatureInput)
        container.addView(fieldLabel(R.string.chat_top_k))
        val topKInput = settingsEditText(working.topK.toString(), InputType.TYPE_CLASS_NUMBER)
        container.addView(topKInput)
        container.addView(fieldLabel(R.string.chat_top_p))
        val topPInput = settingsEditText(
            working.topP.toString(),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
        )
        container.addView(topPInput)
        container.addView(TextView(this).apply {
            text = getString(R.string.chat_generation_settings_help)
            textSize = 12f
            setTextColor(appPalette.secondaryText)
            setPaddingRelative(0, dp(8), 0, dp(4))
        })
        val samplingInputs = listOf(temperatureInput, topKInput, topPInput)
        fun updateSamplingState(useDefaults: Boolean) {
            samplingInputs.forEach { input ->
                input.isEnabled = !useDefaults
                input.alpha = if (useDefaults) DISABLED_ALPHA else 1f
            }
        }
        updateSamplingState(useModelDefaults.isChecked)
        useModelDefaults.setOnCheckedChangeListener { _, checked -> updateSamplingState(checked) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.chat_settings_generation_section)
            .setView(ScrollView(this).apply {
                setBackgroundColor(appPalette.windowBackground)
                addView(container)
            })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_settings_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                minOf((resources.displayMetrics.widthPixels * 0.94f).toInt(), dp(720)),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val maximumTokens = if (unlimitedTokens.isChecked) {
                    null
                } else {
                    maximumTokensInput.text.toString().toIntOrNull()?.takeIf { it > 0 }
                        ?: return@setOnClickListener showSettingError(
                            maximumTokensInput,
                            R.string.chat_positive_integer_error,
                        )
                }
                val maximumTokensDraft = maximumTokens
                    ?: maximumTokensInput.text.toString().toIntOrNull()?.takeIf { it > 0 }
                    ?: working.maximumOutputTokensDraft
                val useDefaults = useModelDefaults.isChecked
                val temperature = if (useDefaults) {
                    working.temperature
                } else {
                    temperatureInput.text.toString().toDoubleOrNull()
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                        ?: return@setOnClickListener showSettingError(
                            temperatureInput,
                            R.string.chat_nonnegative_number_error,
                        )
                }
                val topK = if (useDefaults) {
                    working.topK
                } else {
                    topKInput.text.toString().toIntOrNull()?.takeIf { it > 0 }
                        ?: return@setOnClickListener showSettingError(
                            topKInput,
                            R.string.chat_positive_integer_error,
                        )
                }
                val topP = if (useDefaults) {
                    working.topP
                } else {
                    topPInput.text.toString().toDoubleOrNull()
                        ?.takeIf { it.isFinite() && it in 0.0..1.0 }
                        ?: return@setOnClickListener showSettingError(
                            topPInput,
                            R.string.chat_probability_error,
                        )
                }
                dialog.dismiss()
                saveChatSettings(
                    working.copy(
                        maximumOutputTokens = maximumTokens,
                        maximumOutputTokensDraft = maximumTokensDraft,
                        useModelSamplingDefaults = useDefaults,
                        temperature = temperature,
                        topK = topK,
                        topP = topP,
                    ),
                    recreate = true,
                )
            }
            applyThemeToControls(container)
        }
        dialog.show()
        tintDialogButtons(dialog)
    }

    private fun showIgnoredUpdatesDialog() {
        val ignored = updateSettingsStore.ignoredTags.sorted()
        if (ignored.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.app_update_manage_ignored)
                .setMessage(R.string.app_update_no_ignored)
                .setPositiveButton(android.R.string.ok, null)
                .show()
                .also(::tintDialogButtons)
            return
        }
        val selected = BooleanArray(ignored.size)
        AlertDialog.Builder(this)
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

    private fun themedCheckBox(textResource: Int, checked: Boolean) = CheckBox(this).apply {
        text = getString(textResource)
        isChecked = checked
        setTextColor(appPalette.primaryText)
        buttonTintList = controlTintList()
        minimumHeight = dp(48)
    }

    private fun fieldLabel(textResource: Int) = TextView(this).apply {
        text = getString(textResource)
        textSize = 12.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(appPalette.secondaryText)
        setPaddingRelative(0, dp(8), 0, dp(3))
    }

    private fun settingsEditText(value: String, fieldInputType: Int) = EditText(this).apply {
        setText(value)
        setSelection(text.length)
        inputType = fieldInputType
        maxLines = 1
        setSingleLine(true)
        textSize = 14f
        setTextColor(appPalette.primaryText)
        setHintTextColor(appPalette.secondaryText)
        backgroundTintList = ColorStateList.valueOf(appPalette.accent)
    }

    private fun showSettingError(input: EditText, messageResource: Int) {
        input.error = getString(messageResource)
        input.requestFocus()
    }

    private fun saveSettings(updated: ApplicationSettings) {
        settingsStore.save(updated)
        settings = updated
        Toast.makeText(this, R.string.app_settings_saved, Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun saveChatSettings(updated: ChatUiSettings, recreate: Boolean = false) {
        chatSettings = updated
        chatSettingsStore.save(updated)
        if (recreate) recreate()
    }

    private fun themeSummary(value: ApplicationSettings): String = when (value.themeSelection) {
        AppThemeSelection.FOLLOW_AUTOJS6 -> if (hostResult.selectable) {
            getString(
                R.string.app_settings_theme_follow_summary,
                AppSettingsPolicy.colorHex(
                    hostResult.snapshot?.themeColorPrimary
                        ?: AppSettingsPolicy.THREE_STONE_AI_THEME_COLOR,
                ),
            )
        } else {
            getString(
                R.string.app_settings_theme_follow_unavailable_summary,
                AppSettingsPolicy.colorHex(AppSettingsPolicy.THREE_STONE_AI_THEME_COLOR),
            )
        }
        AppThemeSelection.CUSTOM -> AppSettingsPolicy.colorHex(value.customThemeColor)
    }

    private fun followAwareLabel(followsHost: Boolean, fallback: () -> String): String =
        if (followsHost && !hostResult.selectable) {
            getString(R.string.app_settings_follow_autojs6_unavailable)
        } else {
            fallback()
        }

    private fun currentVersionSummary(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return getString(R.string.about_version_name, packageInfo.versionName.orEmpty())
    }

    private fun generationSettingsSummary(): String {
        val output = chatSettings.maximumOutputTokens?.let { count ->
            getString(R.string.app_settings_generation_limited, count)
        } ?: getString(R.string.chat_maximum_output_tokens_unlimited)
        val sampling = getString(
            if (chatSettings.useModelSamplingDefaults) {
                R.string.app_settings_generation_model_defaults
            } else {
                R.string.app_settings_generation_custom_sampling
            },
        )
        return "$output | $sampling"
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

    private fun ChatFontSize.labelResource(): Int = when (this) {
        ChatFontSize.SMALL -> R.string.chat_font_size_small
        ChatFontSize.DEFAULT -> R.string.chat_font_size_default
        ChatFontSize.LARGE -> R.string.chat_font_size_large
        ChatFontSize.EXTRA_LARGE -> R.string.chat_font_size_extra_large
    }

    private fun EnterKeyBehavior.labelResource(): Int = when (this) {
        EnterKeyBehavior.SEND -> R.string.chat_enter_key_send
        EnterKeyBehavior.NEW_LINE -> R.string.chat_enter_key_new_line
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
