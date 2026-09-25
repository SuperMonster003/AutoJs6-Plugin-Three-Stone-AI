package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.buildScaffold
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.cardContainer
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.cardListParams
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.confirmDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.emptyStateView
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formBottomSheet
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formLabel
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formTextField
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.hairline
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.iconButton
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.sectionHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.singleChoiceDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.switchRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.uiDpF
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiConnectionTest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.ConfiguredOnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiBaseUrlHistoryPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiBaseUrlHistoryStore
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCredentialUpdate
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiModelPresetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileCodec
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfilePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistrySnapshot
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileTransferCodec
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileUrls
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProviderCatalog
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class OnlineAiSettingsActivity : ConfiguredActivity() {
    private lateinit var applicationState: ThreeStoneAiApplication
    private lateinit var registry: OnlineAiProfileRegistry
    private lateinit var baseUrlHistoryStore: OnlineAiBaseUrlHistoryStore
    private lateinit var settingsContent: LinearLayout
    private lateinit var screenRoot: View
    private var snapshot: OnlineAiProfileRegistrySnapshot? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val testExecutor: ExecutorService = Executors.newSingleThreadExecutor { action ->
        Thread(action, "three-stone-ai-connection-test").apply { isDaemon = true }
    }
    private var nextTestToken = 1L
    private var activeTest: ActiveConnectionTest? = null
    private val profileImportPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::importProfiles) }
    private val profileExportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri -> uri?.let(::exportProfiles) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applicationState = application as ThreeStoneAiApplication
        registry = applicationState.onlineProfileRegistry
        baseUrlHistoryStore = OnlineAiBaseUrlHistoryStore(applicationContext)
        setContentView(createContentView())
        reloadSnapshot()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_ADD_PROFILE, false)) {
            intent.removeExtra(EXTRA_ADD_PROFILE)
            settingsContent.post { showProfileEditor(null) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_IMPORT, 0, R.string.online_ai_import_profiles)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_EXPORT, 1, R.string.online_ai_export_profiles)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_IMPORT -> {
            openProfileImportPicker()
            true
        }
        MENU_EXPORT -> {
            openProfileExportPicker()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        if (::settingsContent.isInitialized) reloadSnapshot()
    }

    override fun onDestroy() {
        cancelConnectionTest(activeTest, showFeedback = false)
        testExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun createContentView(): View {
        val scaffold = buildScaffold(R.string.online_ai_settings_title)
        settingsContent = scaffold.content
        screenRoot = scaffold.root
        return scaffold.root
    }

    private fun reloadSnapshot() {
        val result = runCatching(registry::snapshot)
        snapshot = result.getOrNull()
        renderSettings()
    }

    private fun renderSettings() {
        settingsContent.removeAllViews()
        val current = snapshot
        if (current == null) {
            settingsContent.addView(messageBlock(R.string.online_ai_settings_unavailable))
            return
        }

        settingsContent.addView(sectionHeader(R.string.online_ai_general_section))
        settingsContent.addView(
            settingRow(
                title = getString(R.string.online_ai_default_target),
                summary = defaultTargetSummary(current),
                iconResource = R.drawable.ic_tune_24,
                onClick = ::showDefaultTargetDialog,
            ).view,
        )
        settingsContent.addView(
            switchRow(
                title = getString(R.string.online_ai_allow_metered_network),
                summary = getString(R.string.online_ai_allow_metered_network_summary),
                iconResource = R.drawable.ic_cloud_24,
                checked = current.allowMeteredNetwork,
            ) { checked -> updateMeteredNetworkPolicy(checked) }.view,
        )
        settingsContent.addView(hairline())

        settingsContent.addView(sectionHeader(R.string.online_ai_profiles_section))
        settingsContent.addView(
            settingRow(
                title = getString(R.string.online_ai_add_profile),
                summary = getString(R.string.online_ai_add_profile_summary),
                iconResource = R.drawable.ic_add_24,
                onClick = { showProfileEditor(null) },
            ).view,
        )

        val profiles = current.profiles.sortedBy { state ->
            state.profile.displayName.lowercase(Locale.ROOT)
        }
        if (profiles.isEmpty()) {
            settingsContent.addView(
                emptyStateView(
                    title = getString(R.string.online_ai_empty_title),
                    description = getString(R.string.online_ai_empty_description),
                    iconResource = R.drawable.ic_cloud_24,
                ),
            )
        } else {
            profiles.forEach { state -> settingsContent.addView(profileCard(state, current)) }
        }
        applyThemeToControls(settingsContent)
    }

    private fun profileCard(
        state: ConfiguredOnlineAiProfile,
        current: OnlineAiProfileRegistrySnapshot,
    ): View {
        val card = cardContainer(interactive = true)
        val summary = getString(
            R.string.online_ai_profile_summary,
            providerLabel(state.profile.provider),
            modelIdsSummary(state.profile),
            state.profile.baseUrl,
            getString(
                if (state.configured) R.string.online_ai_configured
                else R.string.online_ai_not_configured,
            ),
            getString(
                if (current.defaultProfileId == state.profile.profileId) R.string.online_ai_yes
                else R.string.online_ai_no,
            ),
        )
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(
            primaryText(state.profile.displayName, Ui.TEXT_ITEM).apply {
                typeface = Ui.mediumTypeface
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            iconButton(R.drawable.ic_more_vert_24, R.string.online_ai_profile_action_hint) {
                showProfileActions(state)
            },
        )
        card.addView(header)
        card.addView(
            secondaryText(summary, Ui.TEXT_SECONDARY).apply {
                setPaddingRelative(0, uiDp(2), 0, 0)
                setLineSpacing(0f, 1.12f)
            },
        )
        card.contentDescription = "${state.profile.displayName}, $summary"
        card.setOnClickListener { showProfileEditor(state) }
        card.layoutParams = cardListParams().apply {
            marginStart = uiDp(Ui.SCREEN_MARGIN)
            marginEnd = uiDp(Ui.SCREEN_MARGIN)
        }
        return card
    }

    private fun messageBlock(resource: Int) = secondaryText(getString(resource), 15f).apply {
        setPaddingRelative(uiDp(20), uiDp(24), uiDp(20), uiDp(24))
    }

    private fun primaryText(value: CharSequence, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(appPalette.primaryText)
    }

    private fun secondaryText(value: CharSequence, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(appPalette.secondaryText)
    }

    private fun defaultTargetSummary(current: OnlineAiProfileRegistrySnapshot): String {
        val selected = current.defaultProfileId?.let { profileId ->
            current.profiles.singleOrNull { it.profile.profileId == profileId }
        } ?: return getString(R.string.online_ai_no_default_target)
        return getString(
            R.string.online_ai_default_target_summary,
            selected.profile.displayName,
            selected.profile.modelId,
        )
    }

    private fun modelIdsSummary(profile: OnlineAiProfile): String = when (profile.modelIds.size) {
        1 -> profile.modelId
        else -> getString(
            R.string.online_ai_models_summary,
            profile.modelId,
            profile.modelIds.size,
        )
    }

    private fun showDefaultTargetDialog() {
        val current = snapshot ?: return
        val configured = current.profiles
            .filter(ConfiguredOnlineAiProfile::configured)
            .sortedBy { it.profile.displayName.lowercase(Locale.ROOT) }
        if (configured.isEmpty() && current.defaultProfileId == null) {
            showSnackbar(
                screenRoot,
                getString(R.string.online_ai_no_configured_profiles),
                Snackbar.LENGTH_LONG,
            )
            return
        }
        val labels = listOf(getString(R.string.online_ai_no_default_target)) + configured.map { state ->
            getString(
                R.string.online_ai_default_target_summary,
                state.profile.displayName,
                state.profile.modelId,
            )
        }
        val selected = configured.indexOfFirst { it.profile.profileId == current.defaultProfileId }
            .let { if (it < 0) 0 else it + 1 }
        singleChoiceDialog(
            title = getString(R.string.online_ai_default_target),
            labels = labels,
            checkedIndex = selected,
        ) { index ->
            updateDefaultProfile(configured.getOrNull(index - 1)?.profile?.profileId)
        }
    }

    private fun updateDefaultProfile(profileId: String?) {
        runCatching { registry.setDefaultProfile(profileId) }
            .onSuccess {
                showSnackbar(screenRoot, getString(R.string.online_ai_default_saved))
                reloadSnapshot()
            }
            .onFailure {
                showSnackbar(screenRoot, getString(R.string.online_ai_default_save_failed), Snackbar.LENGTH_LONG)
                reloadSnapshot()
            }
    }

    private fun updateMeteredNetworkPolicy(allowed: Boolean) {
        val previous = snapshot?.allowMeteredNetwork ?: return
        if (allowed == previous) return
        runCatching { registry.setAllowMeteredNetwork(allowed) }
            .onSuccess {
                showSnackbar(screenRoot, getString(R.string.online_ai_metered_saved))
                reloadSnapshot()
            }
            .onFailure {
                showSnackbar(screenRoot, getString(R.string.online_ai_metered_save_failed), Snackbar.LENGTH_LONG)
                reloadSnapshot()
            }
    }

    private fun showProfileActions(state: ConfiguredOnlineAiProfile) {
        val isDefault = snapshot?.defaultProfileId == state.profile.profileId
        val actions = buildList {
            add(ProfileAction.EDIT)
            add(ProfileAction.CLONE)
            if (state.configured) add(ProfileAction.TEST)
            if (isDefault) add(ProfileAction.CLEAR_DEFAULT)
            else if (state.configured) add(ProfileAction.SET_DEFAULT)
            if (state.configured) add(ProfileAction.CLEAR_KEY)
            add(ProfileAction.DELETE)
        }
        materialDialog()
            .setTitle(state.profile.displayName)
            .setItems(actions.map { getString(it.labelResource) }.toTypedArray()) { _, index ->
                when (actions[index]) {
                    ProfileAction.EDIT -> showProfileEditor(state)
                    ProfileAction.CLONE -> cloneProfile(state)
                    ProfileAction.TEST -> confirmConnectionTest(state)
                    ProfileAction.SET_DEFAULT -> updateDefaultProfile(state.profile.profileId)
                    ProfileAction.CLEAR_DEFAULT -> updateDefaultProfile(null)
                    ProfileAction.CLEAR_KEY -> confirmClearKey(state)
                    ProfileAction.DELETE -> confirmDeleteProfile(state)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun showProfileEditor(existing: ConfiguredOnlineAiProfile?) {
        val templates = OnlineAiProviderCatalog.templates
        val initialProvider = existing?.profile?.provider ?: OnlineAiProvider.OPENAI
        val initialTemplate = OnlineAiProviderCatalog.templateFor(initialProvider)
        var selectedProvider = initialProvider
        var lastSuggestedName = existing?.profile?.displayName
            ?: uniqueSuggestedName(providerLabel(initialProvider))
        val (nameField, nameInput) = formTextField(
            initialValue = existing?.profile?.displayName ?: lastSuggestedName,
            hint = getString(R.string.online_ai_profile_name),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            maxLength = PROFILE_TEXT_MAXIMUM_CHARACTERS,
        )
        nameField.placeholderText = getString(R.string.online_ai_profile_name_hint)
        val (baseUrlField, baseUrlInput) = formTextField(
            initialValue = existing?.profile?.baseUrl
                ?: initialTemplate.defaultBaseUrl
                ?: OnlineAiBaseUrlHistoryPolicy.HTTPS_PREFIX,
            hint = getString(R.string.online_ai_base_url),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            maxLength = BASE_URL_MAXIMUM_CHARACTERS,
        )
        baseUrlField.helperText = getString(R.string.online_ai_base_url_hint)
        baseUrlField.endIconMode = TextInputLayout.END_ICON_CUSTOM
        baseUrlField.setEndIconDrawable(R.drawable.ic_history_24)
        baseUrlField.endIconContentDescription =
            getString(R.string.online_ai_base_url_history)
        baseUrlField.setEndIconTintList(ColorStateList.valueOf(appPalette.accent))
        baseUrlField.setEndIconOnClickListener { showBaseUrlHistory(baseUrlInput) }
        val (credentialField, credentialInput) = formTextField(
            initialValue = "",
            hint = getString(R.string.online_ai_credential),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            maxLength = CREDENTIAL_MAXIMUM_CHARACTERS,
        )
        credentialField.helperText = getString(
            if (existing == null) {
                R.string.online_ai_credential_new_hint
            } else {
                R.string.online_ai_credential_edit_hint
            },
        )
        credentialField.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        credentialInput.transformationMethod = PasswordTransformationMethod.getInstance()
        credentialInput.isSaveEnabled = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            credentialInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            credentialInput.setAutofillHints(null)
        }
        var selectedModelIds = existing?.profile?.modelIds
            ?: OnlineAiModelPresetCatalog.forProvider(initialProvider).take(1)
        var selectedDefaultModelId = existing?.profile?.modelId ?: selectedModelIds.first()
        var selectedVisionModelIds = existing?.profile?.visionModelIds.orEmpty()

        var onProviderClick: () -> Unit = {}
        var onModelsClick: () -> Unit = {}
        var onDefaultModelClick: () -> Unit = {}
        var onVisionModelsClick: () -> Unit = {}
        val providerField = pickerField(providerLabel(initialProvider)) { onProviderClick() }
        val modelsField = pickerField("") { onModelsClick() }
        val defaultModelField = pickerField("") { onDefaultModelClick() }
        val visionModelsField = pickerField("") { onVisionModelsClick() }
        visionModelsField.first.contentDescription = getString(R.string.online_ai_vision_models)

        fun refreshModelControls() {
            modelsField.second.text = selectedModelIds.joinToString("\n")
            selectedVisionModelIds = selectedVisionModelIds.filter { it in selectedModelIds }
            visionModelsField.second.text = selectedVisionModelIds.joinToString("\n").ifEmpty { getString(R.string.online_ai_vision_none) }
            if (selectedDefaultModelId !in selectedModelIds) {
                selectedDefaultModelId = selectedModelIds.first()
            }
            defaultModelField.second.text = selectedDefaultModelId
        }

        fun applyProvider(provider: OnlineAiProvider) {
            if (provider == selectedProvider) return
            selectedProvider = provider
            selectedVisionModelIds = emptyList()
            refreshModelControls()
            baseUrlInput.setText(
                OnlineAiProviderCatalog.templateFor(provider).defaultBaseUrl
                    ?: OnlineAiBaseUrlHistoryPolicy.HTTPS_PREFIX,
            )
            if (existing == null) {
                selectedModelIds = OnlineAiModelPresetCatalog.forProvider(provider).take(1)
                selectedDefaultModelId = selectedModelIds.first()
                refreshModelControls()
            }
            if (existing == null && nameInput.text.toString() == lastSuggestedName) {
                lastSuggestedName = uniqueSuggestedName(providerLabel(provider))
                nameInput.setText(lastSuggestedName)
                nameInput.setSelection(nameInput.text?.length ?: 0)
            }
            providerField.second.text = providerLabel(provider)
        }

        onProviderClick = {
            singleChoiceDialog(
                title = getString(R.string.online_ai_provider),
                labels = templates.map { providerLabel(it.provider) },
                checkedIndex = templates.indexOfFirst { it.provider == selectedProvider },
            ) { index -> applyProvider(templates[index].provider) }
        }
        onModelsClick = {
            showModelIdSelector(selectedProvider, selectedModelIds) { selected ->
                selectedModelIds = selected
                refreshModelControls()
            }
        }
        onVisionModelsClick = {
            val boxes = selectedModelIds.map { modelId ->
                MaterialCheckBox(this).apply {
                    text = modelId
                    isChecked = modelId in selectedVisionModelIds
                    minimumHeight = uiDp(48)
                    buttonTintList = controlTintList()
                    setTextColor(appPalette.primaryText)
                }
            }
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(uiDp(Ui.SPACE_XXL), 0, uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
                addView(secondaryText(getString(R.string.online_ai_vision_help), 12.5f))
                boxes.forEach(::addView)
            }
            formBottomSheet(title = getString(R.string.online_ai_vision_models), content = content,
                positiveResource = R.string.chat_settings_save, onPositive = {
                    selectedVisionModelIds = selectedModelIds.filterIndexed { index, _ -> boxes[index].isChecked }
                    refreshModelControls()
                    true
                })
        }
        onDefaultModelClick = {
            singleChoiceDialog(
                title = getString(R.string.online_ai_default_model),
                labels = selectedModelIds,
                checkedIndex = selectedModelIds.indexOf(selectedDefaultModelId),
            ) { index ->
                selectedDefaultModelId = selectedModelIds[index]
                refreshModelControls()
            }
        }
        refreshModelControls()

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(uiDp(Ui.SPACE_XXL), 0, uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
            addView(formLabel(R.string.online_ai_provider))
            addView(providerField.first)
            addView(fieldParamsWrap(nameField))
            addView(fieldParamsWrap(baseUrlField))
            addView(formLabel(R.string.online_ai_model_ids))
            addView(modelsField.first)
            addView(formLabel(R.string.online_ai_default_model))
            addView(defaultModelField.first)
            addView(formLabel(R.string.online_ai_vision_models))
            addView(visionModelsField.first)
            addView(fieldParamsWrap(credentialField))
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        formBottomSheet(
            title = getString(
                if (existing == null) R.string.online_ai_add_profile_title
                else R.string.online_ai_edit_profile_title,
            ),
            content = form,
            positiveResource = R.string.chat_settings_save,
            onPositive = {
                saveProfileFromForm(
                    existing = existing,
                    provider = selectedProvider,
                    nameInput = nameInput,
                    baseUrlInput = baseUrlInput,
                    modelIds = selectedModelIds,
                    visionModelIds = selectedVisionModelIds,
                    defaultModelId = selectedDefaultModelId,
                    credentialField = credentialField,
                    credentialInput = credentialInput,
                )
            },
            onDismiss = {
                credentialInput.text?.clear()
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            },
        )
    }

    /** Outlined picker field visually consistent with the sheet's text fields. */
    private fun pickerField(
        value: CharSequence,
        onClick: () -> Unit,
    ): Pair<LinearLayout, TextView> {
        val valueView = TextView(this).apply {
            text = value
            textSize = Ui.TEXT_BODY
            setTextColor(appPalette.primaryText)
            setLineSpacing(0f, 1.1f)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = uiDp(52)
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                    AppColorPolicy.withAlpha(appPalette.accent, 0x2E),
                ),
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = uiDpF(Ui.RADIUS_CONTROL.toFloat())
                    setColor(appPalette.surface)
                    setStroke(uiDp(1), appPalette.outline)
                },
                null,
            )
            setPaddingRelative(uiDp(Ui.SPACE_LG), uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_MD), uiDp(Ui.SPACE_SM))
            addView(valueView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                android.widget.ImageView(context).apply {
                    setImageDrawable(
                        tintedDrawable(R.drawable.ic_arrow_down_24, appPalette.secondaryText),
                    )
                },
                LinearLayout.LayoutParams(uiDp(20), uiDp(20)),
            )
            setOnClickListener { onClick() }
        }
        return row to valueView
    }

    private fun fieldParamsWrap(field: TextInputLayout): TextInputLayout = field.apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = uiDp(Ui.SPACE_LG) }
    }

    private fun showModelIdSelector(
        provider: OnlineAiProvider,
        initial: List<String>,
        onSelected: (List<String>) -> Unit,
    ) {
        val presets = OnlineAiModelPresetCatalog.forProvider(provider)
        val presetBoxes = presets.map { modelId ->
            MaterialCheckBox(this).apply {
                text = modelId
                textSize = FORM_TEXT_SIZE_SP
                isChecked = modelId in initial
                minimumHeight = uiDp(46)
                buttonTintList = controlTintList()
                setTextColor(appPalette.primaryText)
            }
        }
        val (customField, customInput) = formTextField(
            initialValue = initial.filterNot(presets::contains).joinToString("\n"),
            hint = getString(R.string.online_ai_custom_model_ids),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            maxLength = OnlineAiProfilePolicy.MAXIMUM_MODEL_ID_BYTES *
                OnlineAiProfilePolicy.MAXIMUM_MODELS_PER_PROFILE,
            singleLine = false,
        )
        customField.placeholderText = getString(R.string.online_ai_custom_model_ids_hint)
        customInput.minLines = 2
        customInput.maxLines = 5
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(uiDp(Ui.SPACE_XXL), 0, uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
            addView(secondaryText(getString(R.string.online_ai_model_ids_help), 12.5f).apply {
                setLineSpacing(0f, 1.15f)
            })
            addView(formLabel(R.string.online_ai_model_presets))
            presetBoxes.forEach(::addView)
            addView(fieldParamsWrap(customField))
        }
        formBottomSheet(
            title = getString(R.string.online_ai_model_ids),
            content = content,
            positiveResource = R.string.chat_settings_save,
            onPositive = {
                val custom = customInput.text.toString()
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList()
                val selected = runCatching {
                    val normalized = OnlineAiProfilePolicy.normalizeModelIds(
                        presetBoxes.zip(presets)
                            .filter { (box, _) -> box.isChecked }
                            .map { (_, modelId) -> modelId } + custom,
                    )
                    val selectedSet = normalized.toSet()
                    OnlineAiProfilePolicy.normalizeModelIds(
                        initial.filter(selectedSet::contains) +
                            normalized.filterNot(initial::contains),
                    )
                }.getOrNull()
                if (selected == null) {
                    customField.error = getString(R.string.online_ai_model_ids_invalid)
                    false
                } else {
                    onSelected(selected)
                    true
                }
            },
        )
    }

    private fun saveProfileFromForm(
        existing: ConfiguredOnlineAiProfile?,
        provider: OnlineAiProvider,
        nameInput: android.widget.EditText,
        baseUrlInput: android.widget.EditText,
        modelIds: List<String>,
        visionModelIds: List<String>,
        defaultModelId: String,
        credentialField: TextInputLayout,
        credentialInput: android.widget.EditText,
    ): Boolean {
        val candidate = runCatching {
            val raw = existing?.profile?.copy(
                displayName = nameInput.text.toString(),
                provider = provider,
                baseUrl = baseUrlInput.text.toString(),
                modelId = defaultModelId,
                modelIds = modelIds,
                visionModelIds = visionModelIds,
            ) ?: OnlineAiProfile.create(
                displayName = nameInput.text.toString(),
                provider = provider,
                baseUrl = baseUrlInput.text.toString(),
                modelId = defaultModelId,
                modelIds = modelIds,
                visionModelIds = visionModelIds,
            )
            OnlineAiProfilePolicy.normalizeProfile(raw)
        }.getOrElse {
            showSnackbar(screenRoot, getString(R.string.online_ai_form_invalid), Snackbar.LENGTH_LONG)
            return false
        }
        val credentialText = credentialInput.text
        if (credentialText.isNotEmpty() && !isValidCredential(credentialText)) {
            credentialField.error = getString(R.string.online_ai_credential_invalid)
            return false
        }
        val destinationChanged = existing != null &&
            !OnlineAiProfileUrls.sameCredentialDestination(existing.profile, candidate)
        if (destinationChanged && existing.configured && credentialText.isEmpty()) {
            credentialField.error = getString(
                R.string.online_ai_credential_required_after_destination_change,
            )
            return false
        }

        val replacement = credentialText.takeIf(Editable::isNotEmpty)?.let(::copyCharacters)
        credentialText.clear()
        val update = when {
            replacement != null -> OnlineAiCredentialUpdate.Replace.takingOwnership(replacement)
            existing == null || !existing.configured -> OnlineAiCredentialUpdate.Clear
            else -> OnlineAiCredentialUpdate.Keep
        }
        val saved = try {
            runCatching { registry.save(candidate, update) }
        } finally {
            replacement?.fill('\u0000')
        }
        return saved.fold(
            onSuccess = {
                baseUrlHistoryStore.record(candidate.baseUrl)
                showSnackbar(screenRoot, getString(R.string.online_ai_profile_saved))
                reloadSnapshot()
                true
            },
            onFailure = {
                showSnackbar(
                    screenRoot,
                    getString(R.string.online_ai_profile_save_failed),
                    Snackbar.LENGTH_LONG,
                )
                reloadSnapshot()
                false
            },
        )
    }

    private fun showBaseUrlHistory(target: android.widget.EditText) {
        val values = baseUrlHistoryStore.load()
        if (values.isEmpty()) {
            materialDialog()
                .setTitle(R.string.online_ai_base_url_history)
                .setMessage(R.string.online_ai_base_url_history_empty)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
                .also(::tintDialogButtons)
            return
        }
        materialDialog()
            .setTitle(R.string.online_ai_base_url_history)
            .setItems(values.toTypedArray()) { _, index ->
                target.setText(values[index])
                target.setSelection(target.text.length)
            }
            .setNeutralButton(R.string.online_ai_base_url_history_manage) { _, _ ->
                showBaseUrlHistoryManager(values)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun showBaseUrlHistoryManager(values: List<String>) {
        val selected = BooleanArray(values.size)
        materialDialog()
            .setTitle(R.string.online_ai_base_url_history_manage)
            .setMultiChoiceItems(values.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }
            .setNeutralButton(R.string.online_ai_base_url_history_clear) { _, _ ->
                baseUrlHistoryStore.clear()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.online_ai_base_url_history_delete) { _, _ ->
                baseUrlHistoryStore.remove(
                    values.filterIndexed { index, _ -> selected[index] }.toSet(),
                )
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun uniqueSuggestedName(base: String): String {
        val names = snapshot?.profiles.orEmpty()
            .map { it.profile.displayName.lowercase(Locale.ROOT) }
            .toSet()
        if (base.lowercase(Locale.ROOT) !in names) return base
        var suffix = 2
        while ("$base $suffix".lowercase(Locale.ROOT) in names) suffix += 1
        return "$base $suffix"
    }

    private fun isValidCredential(value: Editable): Boolean =
        value.length in 1..CREDENTIAL_MAXIMUM_CHARACTERS &&
            (0 until value.length).all { index -> value[index].code in 0x21..0x7E }

    private fun copyCharacters(value: Editable): CharArray =
        CharArray(value.length) { index -> value[index] }

    private fun providerLabel(provider: OnlineAiProvider): String =
        if (provider == OnlineAiProvider.OPENAI_COMPATIBLE) {
            getString(R.string.online_ai_provider_custom)
        } else {
            OnlineAiProviderCatalog.templateFor(provider).displayName
        }

    private fun cloneProfile(state: ConfiguredOnlineAiProfile) {
        val profile = state.profile
        val clone = runCatching {
            OnlineAiProfile.create(
                displayName = uniqueSuggestedName(
                    getString(R.string.online_ai_clone_name, profile.displayName),
                ),
                provider = profile.provider,
                baseUrl = profile.baseUrl,
                modelId = profile.modelId,
                modelIds = profile.modelIds,
                visionModelIds = profile.visionModelIds,
            )
        }.getOrElse {
            showSnackbar(screenRoot, getString(R.string.online_ai_profile_clone_failed), Snackbar.LENGTH_LONG)
            return
        }
        runCatching { registry.save(clone, OnlineAiCredentialUpdate.Clear) }
            .onSuccess {
                showSnackbar(screenRoot, getString(R.string.online_ai_profile_cloned), Snackbar.LENGTH_LONG)
                reloadSnapshot()
            }
            .onFailure {
                showSnackbar(screenRoot, getString(R.string.online_ai_profile_clone_failed), Snackbar.LENGTH_LONG)
                reloadSnapshot()
            }
    }

    private fun openProfileImportPicker() {
        profileImportPicker.launch(arrayOf(MIME_JSON))
    }

    private fun openProfileExportPicker() {
        val current = snapshot
        if (current == null || current.profiles.isEmpty()) {
            showSnackbar(screenRoot, getString(R.string.online_ai_export_empty), Snackbar.LENGTH_LONG)
            return
        }
        profileExportPicker.launch(PROFILE_EXPORT_FILE_NAME)
    }

    private fun exportProfiles(uri: Uri) {
        val current = snapshot ?: return
        val result = runCatching {
            val encoded = OnlineAiProfileTransferCodec.encode(current)
            contentResolver.openOutputStream(uri, "wt")?.use { output -> output.write(encoded) }
                ?: error("The selected export document could not be opened")
        }
        showSnackbar(
            screenRoot,
            getString(
                if (result.isSuccess) R.string.online_ai_export_succeeded
                else R.string.online_ai_export_failed,
            ),
            Snackbar.LENGTH_LONG,
        )
    }

    private fun importProfiles(uri: Uri) {
        val result = runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use(::readBoundedProfileDocument)
                ?: error("The selected import document could not be opened")
            val document = OnlineAiProfileTransferCodec.decode(bytes)
            val current = registry.snapshot()
            require(
                current.profiles.size + document.profiles.size <=
                    OnlineAiProfilePolicy.MAXIMUM_PROFILES,
            ) { "Online AI profile storage is full" }
            val usedNames = current.profiles
                .map { state -> state.profile.displayName.lowercase(Locale.ROOT) }
                .toMutableSet()
            document.profiles.forEach { source ->
                val name = uniqueImportedName(source.displayName, usedNames)
                usedNames += name.lowercase(Locale.ROOT)
                registry.save(
                    OnlineAiProfile.create(
                        displayName = name,
                        provider = source.provider,
                        baseUrl = source.baseUrl,
                        modelId = source.modelId,
                        modelIds = source.modelIds,
                        visionModelIds = source.visionModelIds,
                    ),
                    OnlineAiCredentialUpdate.Clear,
                )
            }
            document.profiles.size
        }
        result.onSuccess { count ->
            showSnackbar(
                screenRoot,
                getString(R.string.online_ai_import_succeeded, count),
                Snackbar.LENGTH_LONG,
            )
            reloadSnapshot()
        }.onFailure {
            showSnackbar(screenRoot, getString(R.string.online_ai_import_failed), Snackbar.LENGTH_LONG)
            reloadSnapshot()
        }
    }

    private fun readBoundedProfileDocument(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total = Math.addExact(total, count)
            require(total <= OnlineAiProfileCodec.MAXIMUM_DOCUMENT_BYTES) {
                "Online AI profile import is too large"
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun uniqueImportedName(base: String, usedNames: Set<String>): String {
        if (base.lowercase(Locale.ROOT) !in usedNames) return base
        var suffix = 2
        while ("$base $suffix".lowercase(Locale.ROOT) in usedNames) suffix += 1
        return "$base $suffix"
    }

    private fun confirmClearKey(state: ConfiguredOnlineAiProfile) {
        confirmDialog(
            title = getString(R.string.online_ai_clear_key_title),
            message = getString(R.string.online_ai_clear_key_message, state.profile.displayName),
            positiveResource = R.string.online_ai_action_clear_key,
            destructive = true,
        ) { clearKey(state) }
    }

    private fun clearKey(state: ConfiguredOnlineAiProfile) {
        runCatching { registry.save(state.profile, OnlineAiCredentialUpdate.Clear) }
            .onSuccess {
                showSnackbar(screenRoot, getString(R.string.online_ai_key_cleared))
                reloadSnapshot()
            }
            .onFailure {
                showSnackbar(screenRoot, getString(R.string.online_ai_key_clear_failed), Snackbar.LENGTH_LONG)
                reloadSnapshot()
            }
    }

    private fun confirmDeleteProfile(state: ConfiguredOnlineAiProfile) {
        confirmDialog(
            title = getString(R.string.online_ai_delete_title),
            message = getString(R.string.online_ai_delete_message, state.profile.displayName),
            positiveResource = R.string.online_ai_action_delete,
            destructive = true,
        ) { deleteProfile(state) }
    }

    private fun deleteProfile(state: ConfiguredOnlineAiProfile) {
        activeTest?.takeIf { it.profileId == state.profile.profileId }?.let { active ->
            cancelConnectionTest(active, showFeedback = false)
        }
        runCatching { registry.delete(state.profile.profileId) }
            .onSuccess {
                showSnackbar(screenRoot, getString(R.string.online_ai_profile_deleted))
                reloadSnapshot()
            }
            .onFailure {
                showSnackbar(screenRoot, getString(R.string.online_ai_profile_delete_failed), Snackbar.LENGTH_LONG)
                reloadSnapshot()
            }
    }

    private fun confirmConnectionTest(state: ConfiguredOnlineAiProfile) {
        confirmDialog(
            title = getString(R.string.online_ai_test_title),
            message = getString(R.string.online_ai_test_message, state.profile.displayName),
            positiveResource = R.string.online_ai_test_start,
        ) { beginConnectionTest(state) }
    }

    private fun beginConnectionTest(state: ConfiguredOnlineAiProfile) {
        cancelConnectionTest(activeTest, showFeedback = false)
        val token = nextTestToken++
        val operation = OnlineAiConnectionTest(
            backend = applicationState.onlineBackend,
            profileId = state.profile.profileId,
        )
        val progressView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_MD), uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
            addView(
                CircularProgressIndicator(context).apply {
                    isIndeterminate = true
                    indicatorSize = uiDp(32)
                    setIndicatorColor(appPalette.accent)
                },
            )
            addView(
                secondaryText(
                    getString(R.string.online_ai_test_progress, state.profile.displayName),
                    14f,
                ).apply { setPaddingRelative(uiDp(16), 0, 0, 0) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        val dialog = materialDialog()
            .setTitle(R.string.online_ai_action_test)
            .setView(progressView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        val timeout = Runnable { timeOutConnectionTest(token) }
        val active = ActiveConnectionTest(
            token = token,
            profileId = state.profile.profileId,
            operation = operation,
            dialog = dialog,
            timeout = timeout,
        )
        activeTest = active
        dialog.setOnShowListener {
            tintDialogButtons(dialog)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                cancelConnectionTest(active, showFeedback = true)
            }
        }
        dialog.setOnCancelListener { cancelConnectionTest(active, showFeedback = true) }
        dialog.show()
        active.future = testExecutor.submit {
            val failure = try {
                operation.execute()
                null
            } catch (error: Exception) {
                error
            }
            mainHandler.post { finishConnectionTest(active, failure) }
        }
        mainHandler.postDelayed(timeout, CONNECTION_TEST_TIMEOUT_MILLIS)
    }

    private fun finishConnectionTest(active: ActiveConnectionTest, failure: Exception?) {
        if (activeTest !== active) return
        activeTest = null
        mainHandler.removeCallbacks(active.timeout)
        active.dialog.dismiss()
        when (failure) {
            null -> showSnackbar(screenRoot, getString(R.string.online_ai_test_success), Snackbar.LENGTH_LONG)
            is CancellationException ->
                showSnackbar(screenRoot, getString(R.string.online_ai_test_cancelled))
            else -> materialDialog()
                .setTitle(R.string.online_ai_test_failed)
                .setMessage(getString(connectionFailureMessage(failure)))
                .setPositiveButton(android.R.string.ok, null)
                .show()
                .also(::tintDialogButtons)
        }
        reloadSnapshot()
    }

    private fun timeOutConnectionTest(token: Long) {
        val active = activeTest?.takeIf { it.token == token } ?: return
        activeTest = null
        active.operation.cancel()
        active.future?.cancel(true)
        active.dialog.dismiss()
        showSnackbar(screenRoot, getString(R.string.online_ai_test_timed_out), Snackbar.LENGTH_LONG)
    }

    private fun cancelConnectionTest(active: ActiveConnectionTest?, showFeedback: Boolean) {
        if (active == null || activeTest !== active) return
        activeTest = null
        mainHandler.removeCallbacks(active.timeout)
        active.operation.cancel()
        active.future?.cancel(true)
        active.dialog.dismiss()
        if (showFeedback && !isFinishing && !isDestroyed) {
            showSnackbar(screenRoot, getString(R.string.online_ai_test_cancelled))
        }
    }

    private fun connectionFailureMessage(error: Exception): Int = when (error) {
        is OnlineAiFailureException -> when (error.reason) {
            OnlineAiFailureReason.CREDENTIAL_UNAVAILABLE ->
                R.string.online_ai_test_failure_credential
            OnlineAiFailureReason.PROFILE_CHANGED ->
                R.string.online_ai_test_failure_profile_changed
            OnlineAiFailureReason.AUTHENTICATION_FAILED,
            OnlineAiFailureReason.PERMISSION_DENIED,
            -> R.string.online_ai_test_failure_authentication
            OnlineAiFailureReason.RATE_LIMITED -> R.string.online_ai_test_failure_rate_limit
            OnlineAiFailureReason.REQUEST_REJECTED,
            OnlineAiFailureReason.REDIRECT_REFUSED,
            OnlineAiFailureReason.SERVICE_UNAVAILABLE,
            OnlineAiFailureReason.PROVIDER_ERROR,
            -> R.string.online_ai_test_failure_provider
            OnlineAiFailureReason.NETWORK_UNAVAILABLE -> R.string.online_ai_test_failure_network
            OnlineAiFailureReason.METERED_NETWORK_DISALLOWED ->
                R.string.online_ai_test_failure_metered
            OnlineAiFailureReason.TIMED_OUT -> R.string.online_ai_test_failure_timeout
            OnlineAiFailureReason.TLS_FAILED -> R.string.online_ai_test_failure_tls
            OnlineAiFailureReason.INVALID_REQUEST,
            OnlineAiFailureReason.INVALID_RESPONSE,
            OnlineAiFailureReason.RESPONSE_TOO_LARGE,
            OnlineAiFailureReason.EXECUTION_CLOSED,
            -> R.string.online_ai_test_failure_response
        }
        is AiTargetUnavailableException -> R.string.online_ai_test_failure_credential
        else -> R.string.online_ai_test_failed
    }

    private enum class ProfileAction(val labelResource: Int) {
        EDIT(R.string.online_ai_action_edit),
        CLONE(R.string.online_ai_action_clone),
        TEST(R.string.online_ai_action_test),
        SET_DEFAULT(R.string.online_ai_action_set_default),
        CLEAR_DEFAULT(R.string.online_ai_action_clear_default),
        CLEAR_KEY(R.string.online_ai_action_clear_key),
        DELETE(R.string.online_ai_action_delete),
    }

    private data class ActiveConnectionTest(
        val token: Long,
        val profileId: String,
        val operation: OnlineAiConnectionTest,
        val dialog: AlertDialog,
        val timeout: Runnable,
        var future: Future<*>? = null,
    )

    companion object {
        const val EXTRA_ADD_PROFILE = "addOnlineProfile"

        private const val FORM_TEXT_SIZE_SP = 14f
        private const val PROFILE_TEXT_MAXIMUM_CHARACTERS = 256
        private const val BASE_URL_MAXIMUM_CHARACTERS = 4096
        private const val CREDENTIAL_MAXIMUM_CHARACTERS = 8192
        private const val CONNECTION_TEST_TIMEOUT_MILLIS = 120_000L
        private const val MENU_IMPORT = 2201
        private const val MENU_EXPORT = 2202
        private const val MIME_JSON = "application/json"
        private const val PROFILE_EXPORT_FILE_NAME = "3-stone-ai-online-profiles.json"
    }
}
