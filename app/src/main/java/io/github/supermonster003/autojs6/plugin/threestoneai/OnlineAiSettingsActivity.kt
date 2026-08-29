package io.github.supermonster003.autojs6.plugin.threestoneai

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiConnectionTest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.ConfiguredOnlineAiProfile
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
    private lateinit var settingsContent: LinearLayout
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

    private fun createContentView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)
        val statusBarBackground = createStatusBarBackground()
        addView(
            statusBarBackground,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0),
        )
        addView(createAppToolbar(R.string.online_ai_settings_title, showBack = true))
        settingsContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(0, uiDp(8), 0, uiDp(28))
        }
        addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(settingsContent)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        applySystemBarInsets(this, statusBarBackground)
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

        settingsContent.addView(category(R.string.online_ai_general_section))
        settingsContent.addView(
            settingRow(
                title = getString(R.string.online_ai_default_target),
                summary = defaultTargetSummary(current),
                onClick = ::showDefaultTargetDialog,
            ),
        )
        settingsContent.addView(divider())
        settingsContent.addView(meteredNetworkRow(current.allowMeteredNetwork))

        settingsContent.addView(category(R.string.online_ai_profiles_section))
        settingsContent.addView(
            settingRow(
                title = getString(R.string.online_ai_add_profile),
                summary = getString(R.string.online_ai_add_profile_summary),
                onClick = { showProfileEditor(null) },
            ),
        )

        val profiles = current.profiles.sortedBy { state ->
            state.profile.displayName.lowercase(Locale.ROOT)
        }
        if (profiles.isEmpty()) {
            settingsContent.addView(emptyProfiles())
        } else {
            profiles.forEach { state -> settingsContent.addView(profileRow(state, current)) }
        }
        applyThemeToControls(settingsContent)
    }

    private fun category(resource: Int) = TextView(this).apply {
        text = getString(resource)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(appPalette.accent)
        setPaddingRelative(uiDp(20), uiDp(16), uiDp(20), uiDp(8))
    }

    private fun settingRow(title: String, summary: String, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = uiDp(68)
            isClickable = true
            isFocusable = true
            contentDescription = "$title, $summary"
            setPaddingRelative(uiDp(20), uiDp(12), uiDp(20), uiDp(12))
            setBackgroundResource(selectableBackground())
            addView(primaryText(title, 16f))
            addView(secondaryText(summary, 13f).apply {
                setPaddingRelative(0, uiDp(3), 0, 0)
            })
            setOnClickListener { onClick() }
        }

    private fun meteredNetworkRow(allowed: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPaddingRelative(uiDp(20), uiDp(12), uiDp(12), uiDp(12))
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(primaryText(getString(R.string.online_ai_allow_metered_network), 16f))
            addView(
                secondaryText(
                    getString(R.string.online_ai_allow_metered_network_summary),
                    13f,
                ).apply { setPaddingRelative(0, uiDp(3), uiDp(8), 0) },
            )
        }
        addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(SwitchCompat(context).apply {
            isChecked = allowed
            contentDescription = getString(R.string.online_ai_allow_metered_network)
            setOnCheckedChangeListener { _, checked -> updateMeteredNetworkPolicy(checked) }
        })
    }

    private fun emptyProfiles() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        setPaddingRelative(uiDp(24), uiDp(28), uiDp(24), uiDp(20))
        addView(primaryText(getString(R.string.online_ai_empty_title), 17f).apply {
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(secondaryText(getString(R.string.online_ai_empty_description), 14f).apply {
            gravity = android.view.Gravity.CENTER
            setPaddingRelative(0, uiDp(6), 0, 0)
        })
    }

    private fun profileRow(
        state: ConfiguredOnlineAiProfile,
        current: OnlineAiProfileRegistrySnapshot,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        isFocusable = true
        setPaddingRelative(uiDp(16), uiDp(14), uiDp(16), uiDp(14))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = uiDp(12).toFloat()
            setColor(appPalette.inputSurface)
            setStroke(uiDp(1), appPalette.chatBorder)
        }
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
        addView(primaryText(state.profile.displayName, 17f).apply {
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(secondaryText(summary, 13f).apply {
            setPaddingRelative(0, uiDp(5), 0, 0)
            setLineSpacing(0f, 1.08f)
        })
        addView(secondaryText(getString(R.string.online_ai_profile_action_hint), 12f).apply {
            setPaddingRelative(0, uiDp(7), 0, 0)
            setTextColor(appPalette.accent)
        })
        contentDescription = "${state.profile.displayName}, $summary"
        setOnClickListener { showProfileActions(state) }
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = uiDp(16)
            marginEnd = uiDp(16)
            topMargin = uiDp(10)
        }
    }

    private fun messageBlock(resource: Int) = secondaryText(getString(resource), 15f).apply {
        setPaddingRelative(uiDp(20), uiDp(24), uiDp(20), uiDp(24))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(appPalette.divider)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            uiDp(1),
        ).apply { marginStart = uiDp(20) }
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

    private fun selectableBackground(): Int {
        val value = android.util.TypedValue()
        return if (theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            value.resourceId
        } else {
            android.R.color.transparent
        }
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
            Toast.makeText(this, R.string.online_ai_no_configured_profiles, Toast.LENGTH_LONG).show()
            return
        }
        val labels = arrayOf(getString(R.string.online_ai_no_default_target)) + configured.map { state ->
            getString(
                R.string.online_ai_default_target_summary,
                state.profile.displayName,
                state.profile.modelId,
            )
        }
        val selected = configured.indexOfFirst { it.profile.profileId == current.defaultProfileId }
            .let { if (it < 0) 0 else it + 1 }
        AlertDialog.Builder(this)
            .setTitle(R.string.online_ai_default_target)
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                dialog.dismiss()
                updateDefaultProfile(configured.getOrNull(index - 1)?.profile?.profileId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun updateDefaultProfile(profileId: String?) {
        runCatching { registry.setDefaultProfile(profileId) }
            .onSuccess {
                Toast.makeText(this, R.string.online_ai_default_saved, Toast.LENGTH_SHORT).show()
                reloadSnapshot()
            }
            .onFailure {
                Toast.makeText(this, R.string.online_ai_default_save_failed, Toast.LENGTH_LONG).show()
                reloadSnapshot()
            }
    }

    private fun updateMeteredNetworkPolicy(allowed: Boolean) {
        val previous = snapshot?.allowMeteredNetwork ?: return
        if (allowed == previous) return
        runCatching { registry.setAllowMeteredNetwork(allowed) }
            .onSuccess {
                Toast.makeText(this, R.string.online_ai_metered_saved, Toast.LENGTH_SHORT).show()
                reloadSnapshot()
            }
            .onFailure {
                Toast.makeText(this, R.string.online_ai_metered_save_failed, Toast.LENGTH_LONG).show()
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
        AlertDialog.Builder(this)
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

    @Suppress("DEPRECATION")
    private fun showProfileEditor(existing: ConfiguredOnlineAiProfile?) {
        val templates = OnlineAiProviderCatalog.templates
        val initialProvider = existing?.profile?.provider ?: OnlineAiProvider.OPENAI
        val initialTemplate = OnlineAiProviderCatalog.templateFor(initialProvider)
        val providerSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@OnlineAiSettingsActivity,
                android.R.layout.simple_spinner_item,
                templates.map { providerLabel(it.provider) },
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(templates.indexOfFirst { it.provider == initialProvider })
        }
        var lastSuggestedName = existing?.profile?.displayName
            ?: uniqueSuggestedName(providerLabel(initialProvider))
        val nameInput = formInput(
            value = existing?.profile?.displayName ?: lastSuggestedName,
            hintResource = R.string.online_ai_profile_name_hint,
            inputTypeValue = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            maximumCharacters = PROFILE_TEXT_MAXIMUM_CHARACTERS,
        )
        val baseUrlInput = formInput(
            value = existing?.profile?.baseUrl ?: initialTemplate.defaultBaseUrl.orEmpty(),
            hintResource = R.string.online_ai_base_url_hint,
            inputTypeValue = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            maximumCharacters = BASE_URL_MAXIMUM_CHARACTERS,
        )
        val credentialInput = formInput(
            value = "",
            hintResource = if (existing == null) {
                R.string.online_ai_credential_new_hint
            } else {
                R.string.online_ai_credential_edit_hint
            },
            inputTypeValue = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            maximumCharacters = CREDENTIAL_MAXIMUM_CHARACTERS,
        ).apply {
            transformationMethod = PasswordTransformationMethod.getInstance()
            isSaveEnabled = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                setAutofillHints(null)
            }
        }
        var selectedProvider = initialProvider
        var selectedModelIds = existing?.profile?.modelIds
            ?: OnlineAiModelPresetCatalog.forProvider(initialProvider).take(1)
        var selectedDefaultModelId = existing?.profile?.modelId ?: selectedModelIds.first()
        val modelSummary = secondaryText("", 13f).apply {
            setLineSpacing(0f, 1.08f)
            setPaddingRelative(0, uiDp(4), uiDp(24), uiDp(4))
        }
        val modelPicker = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = uiDp(54)
            isClickable = true
            isFocusable = true
            setBackgroundResource(selectableBackground())
            addView(
                modelSummary,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(secondaryText("\u203a", 24f))
        }
        val activeModelAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            ArrayList(),
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val activeModelSpinner = Spinner(this).apply { adapter = activeModelAdapter }

        fun refreshModelControls() {
            modelSummary.text = selectedModelIds.joinToString("\n")
            activeModelAdapter.clear()
            activeModelAdapter.addAll(selectedModelIds)
            activeModelAdapter.notifyDataSetChanged()
            if (selectedDefaultModelId !in selectedModelIds) {
                selectedDefaultModelId = selectedModelIds.first()
            }
            activeModelSpinner.setSelection(selectedModelIds.indexOf(selectedDefaultModelId))
        }
        modelPicker.setOnClickListener {
            showModelIdSelector(selectedProvider, selectedModelIds) { selected ->
                selectedModelIds = selected
                refreshModelControls()
            }
        }
        refreshModelControls()

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val provider = templates[position].provider
                if (provider == selectedProvider) return
                selectedProvider = provider
                baseUrlInput.setText(templates[position].defaultBaseUrl.orEmpty())
                if (existing == null) {
                    selectedModelIds = OnlineAiModelPresetCatalog.forProvider(provider).take(1)
                    selectedDefaultModelId = selectedModelIds.first()
                    refreshModelControls()
                }
                if (existing == null && nameInput.text.toString() == lastSuggestedName) {
                    lastSuggestedName = uniqueSuggestedName(providerLabel(provider))
                    nameInput.setText(lastSuggestedName)
                    nameInput.setSelection(nameInput.text.length)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(uiDp(22), uiDp(4), uiDp(22), uiDp(4))
            addView(formLabel(R.string.online_ai_provider))
            addView(providerSpinner)
            addView(formLabel(R.string.online_ai_profile_name))
            addView(nameInput)
            addView(formLabel(R.string.online_ai_base_url))
            addView(baseUrlInput)
            addView(formLabel(R.string.online_ai_model_ids))
            addView(modelPicker)
            addView(formLabel(R.string.online_ai_default_model))
            addView(activeModelSpinner)
            addView(formLabel(R.string.online_ai_credential))
            addView(credentialInput)
        }
        applyThemeToControls(form)
        val dialog = AlertDialog.Builder(this)
            .setTitle(
                if (existing == null) R.string.online_ai_add_profile_title
                else R.string.online_ai_edit_profile_title,
            )
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_settings_save, null)
            .create()
        dialog.setOnShowListener {
            tintDialogButtons(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                saveProfileFromForm(
                    dialog = dialog,
                    existing = existing,
                    provider = selectedProvider,
                    nameInput = nameInput,
                    baseUrlInput = baseUrlInput,
                    modelIds = selectedModelIds,
                    defaultModelId = activeModelSpinner.selectedItem as? String
                        ?: selectedDefaultModelId,
                    credentialInput = credentialInput,
                )
            }
        }
        dialog.setOnDismissListener {
            credentialInput.text.clear()
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun showModelIdSelector(
        provider: OnlineAiProvider,
        initial: List<String>,
        onSelected: (List<String>) -> Unit,
    ) {
        val presets = OnlineAiModelPresetCatalog.forProvider(provider)
        val presetBoxes = presets.map { modelId ->
            CheckBox(this).apply {
                text = modelId
                textSize = FORM_TEXT_SIZE_SP
                isChecked = modelId in initial
                minimumHeight = uiDp(46)
                buttonTintList = controlTintList()
                setTextColor(appPalette.primaryText)
            }
        }
        val customInput = EditText(this).apply {
            hint = getString(R.string.online_ai_custom_model_ids_hint)
            setText(initial.filterNot(presets::contains).joinToString("\n"))
            textSize = FORM_TEXT_SIZE_SP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 2
            maxLines = 5
            filters = arrayOf(
                InputFilter.LengthFilter(
                    OnlineAiProfilePolicy.MAXIMUM_MODEL_ID_BYTES *
                        OnlineAiProfilePolicy.MAXIMUM_MODELS_PER_PROFILE,
                ),
            )
            setTextColor(appPalette.primaryText)
            setHintTextColor(appPalette.secondaryText)
            setPaddingRelative(uiDp(4), uiDp(8), uiDp(4), uiDp(8))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(uiDp(22), uiDp(2), uiDp(22), uiDp(8))
            addView(secondaryText(getString(R.string.online_ai_model_ids_help), 12.5f).apply {
                setPaddingRelative(0, uiDp(4), 0, uiDp(8))
            })
            addView(formLabel(R.string.online_ai_model_presets))
            presetBoxes.forEach(::addView)
            addView(formLabel(R.string.online_ai_custom_model_ids))
            addView(customInput)
        }
        applyThemeToControls(content)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.online_ai_model_ids)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_settings_save, null)
            .create()
        dialog.setOnShowListener {
            tintDialogButtons(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
                }.getOrElse {
                    customInput.error = getString(R.string.online_ai_model_ids_invalid)
                    return@setOnClickListener
                }
                dialog.dismiss()
                onSelected(selected)
            }
        }
        dialog.show()
    }

    private fun saveProfileFromForm(
        dialog: AlertDialog,
        existing: ConfiguredOnlineAiProfile?,
        provider: OnlineAiProvider,
        nameInput: EditText,
        baseUrlInput: EditText,
        modelIds: List<String>,
        defaultModelId: String,
        credentialInput: EditText,
    ) {
        val candidate = runCatching {
            val raw = existing?.profile?.copy(
                displayName = nameInput.text.toString(),
                provider = provider,
                baseUrl = baseUrlInput.text.toString(),
                modelId = defaultModelId,
                modelIds = modelIds,
            ) ?: OnlineAiProfile.create(
                displayName = nameInput.text.toString(),
                provider = provider,
                baseUrl = baseUrlInput.text.toString(),
                modelId = defaultModelId,
                modelIds = modelIds,
            )
            OnlineAiProfilePolicy.normalizeProfile(raw)
        }.getOrElse {
            Toast.makeText(this, R.string.online_ai_form_invalid, Toast.LENGTH_LONG).show()
            return
        }
        val credentialText = credentialInput.text
        if (credentialText.isNotEmpty() && !isValidCredential(credentialText)) {
            credentialInput.error = getString(R.string.online_ai_credential_invalid)
            return
        }
        val destinationChanged = existing != null &&
            !OnlineAiProfileUrls.sameCredentialDestination(existing.profile, candidate)
        if (destinationChanged && existing.configured && credentialText.isEmpty()) {
            credentialInput.error = getString(
                R.string.online_ai_credential_required_after_destination_change,
            )
            return
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
        saved.onSuccess {
            dialog.dismiss()
            Toast.makeText(this, R.string.online_ai_profile_saved, Toast.LENGTH_SHORT).show()
            reloadSnapshot()
        }.onFailure {
            Toast.makeText(this, R.string.online_ai_profile_save_failed, Toast.LENGTH_LONG).show()
            reloadSnapshot()
        }
    }

    private fun formInput(
        value: String,
        hintResource: Int,
        inputTypeValue: Int,
        maximumCharacters: Int,
    ) = EditText(this).apply {
        setText(value)
        hint = getString(hintResource)
        textSize = FORM_TEXT_SIZE_SP
        inputType = inputTypeValue
        filters = arrayOf(InputFilter.LengthFilter(maximumCharacters))
        setTextColor(appPalette.primaryText)
        setHintTextColor(appPalette.secondaryText)
        setSingleLine(true)
        setPaddingRelative(uiDp(4), uiDp(8), uiDp(4), uiDp(8))
    }

    private fun formLabel(resource: Int) = primaryText(getString(resource), 13f).apply {
        typeface = Typeface.DEFAULT_BOLD
        setPaddingRelative(0, uiDp(13), 0, uiDp(3))
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
            )
        }.getOrElse {
            Toast.makeText(this, R.string.online_ai_profile_clone_failed, Toast.LENGTH_LONG).show()
            return
        }
        runCatching { registry.save(clone, OnlineAiCredentialUpdate.Clear) }
            .onSuccess {
                Toast.makeText(this, R.string.online_ai_profile_cloned, Toast.LENGTH_LONG).show()
                reloadSnapshot()
            }
            .onFailure {
                Toast.makeText(this, R.string.online_ai_profile_clone_failed, Toast.LENGTH_LONG).show()
                reloadSnapshot()
            }
    }

    private fun openProfileImportPicker() {
        profileImportPicker.launch(arrayOf(MIME_JSON))
    }

    private fun openProfileExportPicker() {
        val current = snapshot
        if (current == null || current.profiles.isEmpty()) {
            Toast.makeText(this, R.string.online_ai_export_empty, Toast.LENGTH_LONG).show()
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
        Toast.makeText(
            this,
            if (result.isSuccess) R.string.online_ai_export_succeeded
            else R.string.online_ai_export_failed,
            Toast.LENGTH_LONG,
        ).show()
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
                    ),
                    OnlineAiCredentialUpdate.Clear,
                )
            }
            document.profiles.size
        }
        result.onSuccess { count ->
            Toast.makeText(
                this,
                getString(R.string.online_ai_import_succeeded, count),
                Toast.LENGTH_LONG,
            ).show()
            reloadSnapshot()
        }.onFailure {
            Toast.makeText(this, R.string.online_ai_import_failed, Toast.LENGTH_LONG).show()
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
        AlertDialog.Builder(this)
            .setTitle(R.string.online_ai_clear_key_title)
            .setMessage(getString(R.string.online_ai_clear_key_message, state.profile.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.online_ai_action_clear_key) { _, _ -> clearKey(state) }
            .show()
            .also(::tintDialogButtons)
    }

    private fun clearKey(state: ConfiguredOnlineAiProfile) {
        runCatching { registry.save(state.profile, OnlineAiCredentialUpdate.Clear) }
            .onSuccess {
                Toast.makeText(this, R.string.online_ai_key_cleared, Toast.LENGTH_SHORT).show()
                reloadSnapshot()
            }
            .onFailure {
                Toast.makeText(this, R.string.online_ai_key_clear_failed, Toast.LENGTH_LONG).show()
                reloadSnapshot()
            }
    }

    private fun confirmDeleteProfile(state: ConfiguredOnlineAiProfile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.online_ai_delete_title)
            .setMessage(getString(R.string.online_ai_delete_message, state.profile.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.online_ai_action_delete) { _, _ -> deleteProfile(state) }
            .show()
            .also(::tintDialogButtons)
    }

    private fun deleteProfile(state: ConfiguredOnlineAiProfile) {
        activeTest?.takeIf { it.profileId == state.profile.profileId }?.let { active ->
            cancelConnectionTest(active, showFeedback = false)
        }
        runCatching { registry.delete(state.profile.profileId) }
            .onSuccess {
                Toast.makeText(this, R.string.online_ai_profile_deleted, Toast.LENGTH_SHORT).show()
                reloadSnapshot()
            }
            .onFailure {
                Toast.makeText(this, R.string.online_ai_profile_delete_failed, Toast.LENGTH_LONG).show()
                reloadSnapshot()
            }
    }

    private fun confirmConnectionTest(state: ConfiguredOnlineAiProfile) {
        AlertDialog.Builder(this)
            .setTitle(R.string.online_ai_test_title)
            .setMessage(getString(R.string.online_ai_test_message, state.profile.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.online_ai_test_start) { _, _ -> beginConnectionTest(state) }
            .show()
            .also(::tintDialogButtons)
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
            setPaddingRelative(uiDp(22), uiDp(10), uiDp(22), uiDp(8))
            addView(ProgressBar(context).apply { isIndeterminate = true })
            addView(
                secondaryText(
                    getString(R.string.online_ai_test_progress, state.profile.displayName),
                    14f,
                ).apply { setPaddingRelative(uiDp(16), 0, 0, 0) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        applyThemeToControls(progressView)
        val dialog = AlertDialog.Builder(this)
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
            null -> Toast.makeText(this, R.string.online_ai_test_success, Toast.LENGTH_LONG).show()
            is CancellationException ->
                Toast.makeText(this, R.string.online_ai_test_cancelled, Toast.LENGTH_SHORT).show()
            else -> AlertDialog.Builder(this)
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
        Toast.makeText(this, R.string.online_ai_test_timed_out, Toast.LENGTH_LONG).show()
    }

    private fun cancelConnectionTest(active: ActiveConnectionTest?, showFeedback: Boolean) {
        if (active == null || activeTest !== active) return
        activeTest = null
        mainHandler.removeCallbacks(active.timeout)
        active.operation.cancel()
        active.future?.cancel(true)
        active.dialog.dismiss()
        if (showFeedback && !isFinishing && !isDestroyed) {
            Toast.makeText(this, R.string.online_ai_test_cancelled, Toast.LENGTH_SHORT).show()
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
