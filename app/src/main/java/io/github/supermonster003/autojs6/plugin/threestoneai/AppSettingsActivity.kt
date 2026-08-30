package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.buildScaffold
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formBottomSheet
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formTextField
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.hairline
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.inputDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.sectionHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.singleChoiceDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.switchRow
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

    private fun createContentView(): View {
        val scaffold = buildScaffold(R.string.app_settings_title)
        buildSettingsContent(scaffold.content)
        applyThemeToControls(scaffold.content)
        return scaffold.root
    }

    private fun buildSettingsContent(content: LinearLayout) {
        content.addView(sectionHeader(R.string.app_settings_appearance))
        content.addView(settingRow(
            title = getString(R.string.app_settings_language),
            summary = followAwareLabel(settings.language == AppLanguage.FOLLOW_AUTOJS6) {
                getString(settings.language.labelResource())
            },
            iconResource = R.drawable.ic_language_24,
            onClick = ::showLanguageDialog,
        ).view)
        content.addView(settingRow(
            title = getString(R.string.app_settings_dark_mode),
            summary = followAwareLabel(settings.darkMode == AppDarkMode.FOLLOW_AUTOJS6) {
                getString(settings.darkMode.labelResource())
            },
            iconResource = R.drawable.ic_dark_mode_24,
            onClick = ::showDarkModeDialog,
        ).view)
        content.addView(settingRow(
            title = getString(R.string.app_settings_theme_color),
            summary = themeSummary(settings),
            iconResource = R.drawable.ic_palette_24,
            onClick = ::showThemeColorDialog,
        ).view)
        content.addView(hairline())

        content.addView(sectionHeader(R.string.app_settings_conversation))
        content.addView(settingRow(
            title = getString(R.string.chat_font_size),
            summary = getString(chatSettings.fontSize.labelResource()),
            iconResource = R.drawable.ic_edit_24,
            onClick = ::showFontSizeDialog,
        ).view)
        content.addView(switchRow(
            title = getString(R.string.chat_follow_streaming_output),
            summary = getString(R.string.app_settings_follow_streaming_summary),
            iconResource = R.drawable.ic_arrow_down_24,
            checked = chatSettings.followStreamingOutput,
        ) { checked -> saveChatSettings(chatSettings.copy(followStreamingOutput = checked)) }.view)
        content.addView(switchRow(
            title = getString(R.string.chat_show_generation_usage),
            summary = getString(R.string.app_settings_show_usage_summary),
            iconResource = R.drawable.ic_check_circle_24,
            checked = chatSettings.showGenerationUsage,
        ) { checked -> saveChatSettings(chatSettings.copy(showGenerationUsage = checked)) }.view)
        content.addView(settingRow(
            title = getString(R.string.chat_enter_key_behavior),
            summary = getString(chatSettings.enterKeyBehavior.labelResource()),
            iconResource = R.drawable.ic_send_24,
            onClick = ::showEnterKeyDialog,
        ).view)
        content.addView(settingRow(
            title = getString(R.string.chat_context_token_budget),
            summary = contextTokenBudgetLabel(chatSettings.contextTokenBudget),
            iconResource = R.drawable.ic_history_24,
            onClick = ::showContextTokenBudgetDialog,
        ).view)
        content.addView(settingRow(
            title = getString(R.string.chat_settings_generation_section),
            summary = generationSettingsSummary(),
            iconResource = R.drawable.ic_tune_24,
            onClick = ::showGenerationSettingsDialog,
        ).view)
        content.addView(hairline())

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
            onClick = {
                startActivity(Intent(this, ReleaseHistoryActivity::class.java))
            },
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
        }
        singleChoiceDialog(
            title = getString(R.string.app_settings_theme_color),
            labels = labels,
            checkedIndex = selected,
            enabledAt = { index -> index != 0 || hostResult.selectable },
        ) { index ->
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
    }

    private fun showCustomThemeColorDialog() {
        inputDialog(
            title = getString(R.string.app_settings_custom_color_title),
            initialValue = AppSettingsPolicy.colorHex(settings.customThemeColor),
            hint = getString(R.string.app_settings_custom_color_hint),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
            maxLength = 7,
            positiveResource = R.string.chat_settings_save,
            validate = { value ->
                if (AppSettingsPolicy.parseOpaqueColor(value) == null) {
                    getString(R.string.app_settings_custom_color_error)
                } else {
                    null
                }
            },
        ) { value ->
            val color = AppSettingsPolicy.parseOpaqueColor(value) ?: return@inputDialog
            saveSettings(
                settings.copy(
                    themeSelection = AppThemeSelection.CUSTOM,
                    customThemeColor = color,
                ),
            )
        }
    }

    private fun showDarkModeDialog() {
        val values = AppDarkMode.entries
        val labels = values.mapIndexed { index, value ->
            if (index == 0 && !hostResult.selectable) {
                getString(R.string.app_settings_follow_autojs6_unavailable)
            } else {
                getString(value.labelResource())
            }
        }
        singleChoiceDialog(
            title = getString(R.string.app_settings_dark_mode),
            labels = labels,
            checkedIndex = values.indexOf(settings.darkMode),
            enabledAt = { index -> index != 0 || hostResult.selectable },
        ) { index -> saveSettings(settings.copy(darkMode = values[index])) }
    }

    private fun showLanguageDialog() {
        val values = AppLanguage.entries
        val labels = values.mapIndexed { index, value ->
            if (index == 0 && !hostResult.selectable) {
                getString(R.string.app_settings_follow_autojs6_unavailable)
            } else {
                getString(value.labelResource())
            }
        }
        singleChoiceDialog(
            title = getString(R.string.app_settings_language),
            labels = labels,
            checkedIndex = values.indexOf(settings.language),
            enabledAt = { index -> index != 0 || hostResult.selectable },
        ) { index -> saveSettings(settings.copy(language = values[index])) }
    }

    private fun showFontSizeDialog() {
        val values = ChatFontSize.entries
        singleChoiceDialog(
            title = getString(R.string.chat_font_size),
            labels = values.map { getString(it.labelResource()) },
            checkedIndex = values.indexOf(chatSettings.fontSize),
        ) { index -> saveChatSettings(chatSettings.copy(fontSize = values[index]), recreate = true) }
    }

    private fun showEnterKeyDialog() {
        val values = EnterKeyBehavior.entries
        singleChoiceDialog(
            title = getString(R.string.chat_enter_key_behavior),
            labels = values.map { getString(it.labelResource()) },
            checkedIndex = values.indexOf(chatSettings.enterKeyBehavior),
        ) { index ->
            saveChatSettings(chatSettings.copy(enterKeyBehavior = values[index]), recreate = true)
        }
    }

    private fun showContextTokenBudgetDialog() {
        val presets = CONTEXT_TOKEN_BUDGET_PRESETS
        val customIndex = presets.size
        val selected = presets.indexOf(chatSettings.contextTokenBudget).takeIf { it >= 0 }
            ?: customIndex
        singleChoiceDialog(
            title = getString(R.string.chat_context_token_budget),
            labels = presets.map(::contextTokenBudgetLabel) +
                getString(R.string.chat_context_token_budget_custom),
            checkedIndex = selected,
        ) { index ->
            if (index == customIndex) {
                showCustomContextTokenBudgetDialog()
            } else {
                saveChatSettings(
                    chatSettings.copy(contextTokenBudget = presets[index]),
                    recreate = true,
                )
            }
        }
    }

    private fun showCustomContextTokenBudgetDialog() {
        inputDialog(
            title = getString(R.string.chat_context_token_budget_custom_title),
            initialValue = chatSettings.contextTokenBudget.toString(),
            hint = getString(R.string.chat_context_token_budget_custom_hint),
            inputType = InputType.TYPE_CLASS_NUMBER,
            maxLength = 10,
            positiveResource = R.string.chat_settings_save,
            validate = { value ->
                if (value.toIntOrNull()?.takeIf { it > 0 } == null) {
                    getString(R.string.chat_positive_integer_error)
                } else {
                    null
                }
            },
        ) { value ->
            val budget = value.toIntOrNull()?.takeIf { it > 0 } ?: return@inputDialog
            saveChatSettings(
                chatSettings.copy(contextTokenBudget = budget),
                recreate = true,
            )
        }
    }

    private fun showGenerationSettingsDialog() {
        val working = chatSettings
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(uiDp(Ui.SPACE_XXL), 0, uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
        }
        val unlimitedTokens = sheetCheckBox(
            R.string.chat_maximum_output_tokens_unlimited,
            working.maximumOutputTokens == null,
        )
        container.addView(unlimitedTokens)
        val (maximumTokensField, maximumTokensInput) = formTextField(
            initialValue = working.maximumOutputTokensDraft.toString(),
            hint = getString(R.string.chat_maximum_output_tokens),
            inputType = InputType.TYPE_CLASS_NUMBER,
        )
        container.addView(sheetField(maximumTokensField))
        fun updateMaximumTokensState(unlimited: Boolean) {
            maximumTokensField.isEnabled = !unlimited
            maximumTokensField.alpha = if (unlimited) Ui.DISABLED_ALPHA else 1f
        }
        updateMaximumTokensState(unlimitedTokens.isChecked)
        unlimitedTokens.setOnCheckedChangeListener { _, checked ->
            updateMaximumTokensState(checked)
        }

        val useModelDefaults = sheetCheckBox(
            R.string.chat_use_model_sampling_defaults,
            working.useModelSamplingDefaults,
        ).apply { setPaddingRelative(0, uiDp(Ui.SPACE_MD), 0, 0) }
        container.addView(useModelDefaults)
        val (temperatureField, temperatureInput) = formTextField(
            initialValue = working.temperature.toString(),
            hint = getString(R.string.chat_temperature),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
        )
        container.addView(sheetField(temperatureField))
        val (topKField, topKInput) = formTextField(
            initialValue = working.topK.toString(),
            hint = getString(R.string.chat_top_k),
            inputType = InputType.TYPE_CLASS_NUMBER,
        )
        container.addView(sheetField(topKField))
        val (topPField, topPInput) = formTextField(
            initialValue = working.topP.toString(),
            hint = getString(R.string.chat_top_p),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
        )
        container.addView(sheetField(topPField))
        container.addView(TextView(this).apply {
            text = getString(R.string.chat_generation_settings_help)
            textSize = Ui.TEXT_CAPTION
            setTextColor(appPalette.secondaryText)
            setLineSpacing(0f, 1.15f)
            setPaddingRelative(0, uiDp(Ui.SPACE_MD), 0, uiDp(Ui.SPACE_XS))
        })
        val samplingFields = listOf(temperatureField, topKField, topPField)
        fun updateSamplingState(useDefaults: Boolean) {
            samplingFields.forEach { field ->
                field.isEnabled = !useDefaults
                field.alpha = if (useDefaults) Ui.DISABLED_ALPHA else 1f
            }
        }
        updateSamplingState(useModelDefaults.isChecked)
        useModelDefaults.setOnCheckedChangeListener { _, checked -> updateSamplingState(checked) }

        formBottomSheet(
            title = getString(R.string.chat_settings_generation_section),
            content = container,
            positiveResource = R.string.chat_settings_save,
            onPositive = onPositive@{
                val maximumTokens = if (unlimitedTokens.isChecked) {
                    null
                } else {
                    maximumTokensInput.text.toString().toIntOrNull()?.takeIf { it > 0 }
                        ?: run {
                            maximumTokensField.error = getString(R.string.chat_positive_integer_error)
                            return@onPositive false
                        }
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
                        ?: run {
                            temperatureField.error = getString(R.string.chat_nonnegative_number_error)
                            return@onPositive false
                        }
                }
                val topK = if (useDefaults) {
                    working.topK
                } else {
                    topKInput.text.toString().toIntOrNull()?.takeIf { it > 0 }
                        ?: run {
                            topKField.error = getString(R.string.chat_positive_integer_error)
                            return@onPositive false
                        }
                }
                val topP = if (useDefaults) {
                    working.topP
                } else {
                    topPInput.text.toString().toDoubleOrNull()
                        ?.takeIf { it.isFinite() && it in 0.0..1.0 }
                        ?: run {
                            topPField.error = getString(R.string.chat_probability_error)
                            return@onPositive false
                        }
                }
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
                true
            },
        )
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

    private fun sheetCheckBox(textResource: Int, checked: Boolean) = MaterialCheckBox(this).apply {
        text = getString(textResource)
        isChecked = checked
        setTextColor(appPalette.primaryText)
        buttonTintList = controlTintList()
        minimumHeight = uiDp(Ui.TOUCH_TARGET)
    }

    private fun sheetField(field: TextInputLayout): TextInputLayout = field.apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = uiDp(Ui.SPACE_MD) }
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

    private fun contextTokenBudgetLabel(value: Int): String {
        val display = if (value % TOKENS_PER_K == 0) {
            "${value / TOKENS_PER_K}K"
        } else {
            value.toString()
        }
        return getString(R.string.chat_context_token_budget_value, display)
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

    private data class ThemeChoice(
        val labelResource: Int,
        val color: Int?,
        val followAutoJs6: Boolean = false,
    )

    private companion object {
        const val TOKENS_PER_K = 1_024
        val CONTEXT_TOKEN_BUDGET_PRESETS = listOf(8_192, 16_384, 32_768)
    }
}
