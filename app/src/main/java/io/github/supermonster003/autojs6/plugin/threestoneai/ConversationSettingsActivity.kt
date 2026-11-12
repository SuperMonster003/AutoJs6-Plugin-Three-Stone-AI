package io.github.supermonster003.autojs6.plugin.threestoneai

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.SheetCloseGuard
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.SheetHandle
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.confirmedChoiceDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formBottomSheet
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formTextField
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.hairline
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.inputDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.sectionHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingsPage
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.switchRow

/** "Conversation" settings subpage (roadmap D80): display, reasoning, input and output limits. */
class ConversationSettingsActivity : ConfiguredActivity() {
    internal var choiceDialog: AlertDialog? = null; private set
    internal var generationSheet: SheetHandle? = null; private set
    private lateinit var chatSettingsStore: ChatUiSettingsStore
    private lateinit var chatSettings: ChatUiSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatSettingsStore = ChatUiSettingsStore(applicationContext)
        chatSettings = chatSettingsStore.load()
        setContentView(settingsPage(R.string.app_settings_conversation, ::buildContent))
    }

    override fun hasUnconfirmedDialog() =
        choiceDialog?.isShowing == true || generationSheet?.dialog?.isShowing == true

    override fun onDestroy() {
        choiceDialog?.dismiss()
        generationSheet?.dialog?.dismiss()
        super.onDestroy()
    }

    private fun buildContent(content: LinearLayout) {
        content.addView(sectionHeader(R.string.settings_conversation_display))
        content.addView(settingRow(
            title = getString(R.string.chat_font_size),
            summary = getString(chatSettings.fontSize.labelResource()),
            iconResource = R.drawable.ic_edit_24,
            onClick = ::showFontSizeDialog,
        ).view)
        content.addView(settingRow(
            title = getString(R.string.chat_user_bubble_style),
            summary = getString(chatSettings.userBubbleStyle.labelResource()),
            iconResource = R.drawable.ic_person_24,
            onClick = { showBubbleStyleDialog(userMessage = true) },
        ).view)
        content.addView(settingRow(
            title = getString(R.string.chat_assistant_bubble_style),
            summary = getString(chatSettings.assistantBubbleStyle.labelResource()),
            iconResource = R.drawable.ic_article_24,
            onClick = { showBubbleStyleDialog(userMessage = false) },
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
        content.addView(hairline())

        content.addView(sectionHeader(R.string.settings_conversation_reasoning))
        content.addView(switchRow(
            title = getString(R.string.app_settings_show_reasoning),
            summary = getString(R.string.app_settings_show_reasoning_summary),
            iconResource = R.drawable.ic_article_24,
            checked = chatSettings.showReasoningWhileGenerating,
        ) { checked -> saveChatSettings(chatSettings.copy(showReasoningWhileGenerating = checked)) }.view)
        content.addView(switchRow(
            title = getString(R.string.app_settings_collapse_reasoning),
            summary = getString(R.string.app_settings_collapse_reasoning_summary),
            iconResource = R.drawable.ic_chevron_right_24,
            checked = chatSettings.collapseReasoningWhenDone,
        ) { checked -> saveChatSettings(chatSettings.copy(collapseReasoningWhenDone = checked)) }.view)
        content.addView(hairline())

        content.addView(sectionHeader(R.string.settings_conversation_input))
        content.addView(settingRow(
            title = getString(R.string.chat_enter_key_behavior),
            summary = getString(chatSettings.enterKeyBehavior.labelResource()),
            iconResource = R.drawable.ic_send_24,
            onClick = ::showEnterKeyDialog,
        ).view)
        content.addView(hairline())

        content.addView(sectionHeader(R.string.settings_conversation_limits))
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
    }

    private fun showFontSizeDialog() {
        val values = ChatFontSize.entries
        choiceDialog = confirmedChoiceDialog(
            title = getString(R.string.chat_font_size),
            labels = values.map { getString(it.labelResource()) },
            checkedIndex = values.indexOf(chatSettings.fontSize),
        ) { index ->
            if (values[index] != chatSettings.fontSize) {
                saveChatSettings(chatSettings.copy(fontSize = values[index]), recreate = true)
            }
        }
    }

    private fun showEnterKeyDialog() {
        val values = EnterKeyBehavior.entries
        choiceDialog = confirmedChoiceDialog(
            title = getString(R.string.chat_enter_key_behavior),
            labels = values.map { getString(it.labelResource()) },
            checkedIndex = values.indexOf(chatSettings.enterKeyBehavior),
        ) { index ->
            if (values[index] != chatSettings.enterKeyBehavior) {
                saveChatSettings(chatSettings.copy(enterKeyBehavior = values[index]), recreate = true)
            }
        }
    }

    private fun showBubbleStyleDialog(userMessage: Boolean) {
        val values = ChatBubbleStyle.entries
        val current = if (userMessage) chatSettings.userBubbleStyle else chatSettings.assistantBubbleStyle
        choiceDialog = confirmedChoiceDialog(
            title = getString(
                if (userMessage) R.string.chat_user_bubble_style
                else R.string.chat_assistant_bubble_style,
            ),
            labels = values.map { value -> getString(value.labelResource()) },
            checkedIndex = values.indexOf(current),
        ) { index ->
            val selected = values[index]
            if (selected == current) return@confirmedChoiceDialog
            saveChatSettings(
                if (userMessage) {
                    chatSettings.copy(userBubbleStyle = selected)
                } else {
                    chatSettings.copy(assistantBubbleStyle = selected)
                },
                recreate = true,
            )
        }
    }

    private fun showContextTokenBudgetDialog() {
        val presets = CONTEXT_TOKEN_BUDGET_PRESETS
        val customIndex = presets.size
        val selected = presets.indexOf(chatSettings.contextTokenBudget).takeIf { it >= 0 }
            ?: customIndex
        choiceDialog = confirmedChoiceDialog(
            title = getString(R.string.chat_context_token_budget),
            labels = presets.map(::contextTokenBudgetLabel) +
                getString(R.string.chat_context_token_budget_custom),
            checkedIndex = selected,
        ) { index ->
            when {
                index == customIndex -> showCustomContextTokenBudgetDialog()
                presets[index] != chatSettings.contextTokenBudget -> saveChatSettings(
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

    /**
     * "Output length and sampling" form (formerly "Generation", roadmap D80). Like the service
     * form (D105) it stays open until closed on purpose and asks before discarding edits (D111).
     */
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
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = uiDp(Ui.SPACE_MD) }
        }
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
            text = getString(R.string.app_settings_generation_anthropic_ignored)
            textSize = Ui.TEXT_CAPTION
            setTextColor(appPalette.secondaryText)
            setLineSpacing(0f, 1.15f)
            setPaddingRelative(0, uiDp(Ui.SPACE_MD), 0, 0)
        })
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

        fun formState(): List<Any> = listOf(
            unlimitedTokens.isChecked,
            maximumTokensInput.text.toString(),
            useModelDefaults.isChecked,
            temperatureInput.text.toString(),
            topKInput.text.toString(),
            topPInput.text.toString(),
        )
        val initialState = formState()

        generationSheet = formBottomSheet(
            title = getString(R.string.chat_settings_generation_section),
            content = container,
            positiveResource = R.string.chat_settings_save,
            closeGuard = SheetCloseGuard { formState() != initialState },
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

    private fun sheetCheckBox(textResource: Int, checked: Boolean) = MaterialCheckBox(this).apply {
        text = getString(textResource)
        isChecked = checked
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(appPalette.primaryText)
        buttonTintList = controlTintList()
        buttonIconTintList = android.content.res.ColorStateList.valueOf(appPalette.onAccent)
        minimumHeight = uiDp(Ui.TOUCH_TARGET)
    }

    private fun sheetField(field: TextInputLayout): TextInputLayout = field.apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = uiDp(Ui.SPACE_MD) }
    }

    private fun saveChatSettings(updated: ChatUiSettings, recreate: Boolean = false) {
        chatSettings = updated
        chatSettingsStore.save(updated)
        if (recreate) recreate()
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

    private fun ChatBubbleStyle.labelResource(): Int = when (this) {
        ChatBubbleStyle.NONE -> R.string.chat_bubble_style_none
        ChatBubbleStyle.BACKGROUND -> R.string.chat_bubble_style_background
        ChatBubbleStyle.BORDER -> R.string.chat_bubble_style_border
    }

    private companion object {
        const val TOKENS_PER_K = 1_024
        val CONTEXT_TOKEN_BUDGET_PRESETS = listOf(8_192, 16_384, 32_768)
    }
}
