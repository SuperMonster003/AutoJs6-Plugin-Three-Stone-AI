package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiEffortSupport
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.ConfiguredOnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiBaseUrlHistoryPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiBaseUrlHistoryStore
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCredentialUpdate
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiModelCatalogGroups
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiModelCatalogRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfilePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileUrls
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProviderCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.createOnlineAiModelCatalogRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.BadgeTone
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.CardBadge
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.SettingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.applyThemedSelectableBackground
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.buildScaffold
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.confirmDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formBottomSheet
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formLabel
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formTextField
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.sectionHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.setRowEnabled
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.singleChoiceDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.statusBadge
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.switchRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.uiDpF

/**
 * The service details page (roadmap 1.7.0 P5.4, D96, D122 - D125): connection details on top, the
 * service's model list below. Every edit goes into one draft that the toolbar's "Save" writes to
 * the registry; a new service is not stored before that. The draft, typed API key included, lives
 * only in memory and in the saved-instance-state Bundle (as the old editor sheet did, D105).
 */
class ServiceDetailActivity : ConfiguredActivity() {
    private lateinit var applicationState: ThreeStoneAiApplication
    private lateinit var registry: OnlineAiProfileRegistry
    private lateinit var baseUrlHistoryStore: OnlineAiBaseUrlHistoryStore
    private lateinit var modelCatalog: OnlineAiModelCatalogRepository
    private lateinit var tester: OnlineServiceConnectionTester
    private lateinit var screenRoot: View

    /** Null until a new service is saved for the first time. */
    private var profileId: String? = null
    private var persisted: ConfiguredOnlineAiProfile? = null
    private lateinit var baseline: OnlineAiProfileEditorValues
    private lateinit var provider: OnlineAiProvider
    private lateinit var effort: GenerationEffort
    private var fastMode = false
    private lateinit var models: ServiceModelList
    private var lastSuggestedName: String? = null

    private lateinit var providerRow: SettingRow
    private lateinit var nameInput: TextInputEditText
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var credentialField: TextInputLayout
    private lateinit var credentialInput: TextInputEditText
    private lateinit var clearKeyRow: SettingRow
    private lateinit var effortRow: SettingRow
    private lateinit var fastRow: SettingRow
    private lateinit var testRow: SettingRow
    private lateinit var modelList: LinearLayout
    private var saveItem: MenuItem? = null
    private val drag = ModelDrag()
    private var lastTouchRawY = 0f

    private val closeCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = requestClose()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        applicationState = application as ThreeStoneAiApplication
        registry = applicationState.onlineProfileRegistry
        baseUrlHistoryStore = OnlineAiBaseUrlHistoryStore(applicationContext)
        modelCatalog = createOnlineAiModelCatalogRepository(applicationContext)
        tester = OnlineServiceConnectionTester(this, { screenRoot }) { reloadPersisted() }

        val draft = savedInstanceState?.getBundle(KEY_DRAFT)?.let(OnlineAiProfileEditorDraft::fromBundle)
        profileId = draft?.profileId ?: intent.getStringExtra(EXTRA_PROFILE_ID)
        persisted = profileId?.let(::findProfile)
        if (profileId != null && persisted == null) {
            // Deleted elsewhere (an import, another window) while this page was away.
            draft?.credential?.fill('\u0000')
            Toast.makeText(this, R.string.service_detail_missing, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        baseline = draft?.initial ?: persisted?.let { valuesOf(it.profile) } ?: newServiceValues()
        val current = draft?.current ?: baseline
        lastSuggestedName = draft?.lastSuggestedName ?: baseline.name.takeIf { persisted == null }
        provider = current.provider
        effort = current.effort
        fastMode = current.fastMode
        models = ServiceModelList(current.modelIds, current.defaultModelId, current.visionModelIds)

        setContentView(createContentView(current))
        draft?.credential?.let { characters ->
            if (characters.isNotEmpty()) credentialInput.setText(characters, 0, characters.size)
            characters.fill('\u0000')
        }
        onBackPressedDispatcher.addCallback(this, closeCallback)
        refreshState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::credentialInput.isInitialized) return
        val draft = OnlineAiProfileEditorDraft(
            profileId = profileId,
            initial = baseline,
            current = captureValues(),
            lastSuggestedName = lastSuggestedName,
            credential = credentialInput.text?.let(OnlineServiceText::copyCharacters) ?: CharArray(0),
        )
        // The bundle keeps this array by reference until it is parceled (a recreate in the same
        // process never parcels it), so it must not be cleared here; onCreate clears it after use.
        outState.putBundle(KEY_DRAFT, draft.toBundle())
    }

    override fun onDestroy() {
        if (::tester.isInitialized) tester.close()
        if (::credentialInput.isInitialized) credentialInput.text?.clear()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        saveItem = menu.add(0, MENU_SAVE, 0, R.string.chat_settings_save).apply {
            // An icon-only action item already reads its title as the content description.
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        updateSaveItem()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_SAVE) {
            save()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createContentView(current: OnlineAiProfileEditorValues): View {
        val scaffold = buildScaffold(R.string.service_detail_title)
        screenRoot = scaffold.root
        if (profileId == null) scaffold.toolbar.title = getString(R.string.online_ai_add_profile_title)
        scaffold.toolbar.setNavigationOnClickListener { requestClose() }
        val content = scaffold.content

        content.addView(sectionHeader(R.string.service_detail_connection_section))
        providerRow = settingRow(
            title = getString(R.string.online_ai_provider),
            summary = onlineProviderLabel(provider),
            iconResource = R.drawable.ic_cloud_24,
            onClick = ::chooseProvider,
        )
        content.addView(providerRow.view)

        val (nameField, name) = formTextField(
            initialValue = current.name,
            hint = getString(R.string.online_ai_profile_name),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            maxLength = OnlineServiceText.NAME_MAXIMUM_CHARACTERS,
        )
        nameField.placeholderText = getString(R.string.online_ai_profile_name_hint)
        nameInput = name
        val (baseUrlField, baseUrl) = formTextField(
            initialValue = current.baseUrl,
            hint = getString(R.string.online_ai_base_url),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            maxLength = OnlineServiceText.BASE_URL_MAXIMUM_CHARACTERS,
        )
        baseUrlInput = baseUrl
        baseUrlField.helperText = getString(R.string.online_ai_base_url_hint)
        baseUrlField.endIconMode = TextInputLayout.END_ICON_CUSTOM
        baseUrlField.setEndIconDrawable(R.drawable.ic_history_24)
        baseUrlField.endIconContentDescription = getString(R.string.online_ai_base_url_history)
        baseUrlField.setEndIconTintList(ColorStateList.valueOf(appPalette.accent))
        baseUrlField.setEndIconOnClickListener { showBaseUrlHistory(baseUrlHistoryStore, baseUrlInput) }
        val (keyField, key) = formTextField(
            initialValue = "",
            hint = getString(R.string.online_ai_credential),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            maxLength = OnlineServiceText.CREDENTIAL_MAXIMUM_CHARACTERS,
        )
        credentialField = keyField
        credentialInput = key
        credentialField.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        credentialInput.transformationMethod = PasswordTransformationMethod.getInstance()
        credentialInput.isSaveEnabled = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            credentialInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            credentialInput.setAutofillHints(null)
        }
        content.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
                addView(withTopMargin(nameField, 0))
                addView(withTopMargin(baseUrlField, Ui.SPACE_LG))
                addView(withTopMargin(credentialField, Ui.SPACE_LG))
            },
        )
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                credentialField.error = null
                refreshState()
            }
        }
        nameInput.addTextChangedListener(watcher)
        baseUrlInput.addTextChangedListener(watcher)
        credentialInput.addTextChangedListener(watcher)

        clearKeyRow = settingRow(
            title = getString(R.string.online_ai_action_clear_key),
            summary = getString(R.string.service_detail_clear_key_summary),
            iconResource = R.drawable.ic_key_24,
            showChevron = false,
            onClick = ::confirmClearKey,
        )
        content.addView(clearKeyRow.view)
        effortRow = settingRow(
            title = getString(R.string.online_ai_default_effort),
            summary = effortLabel(effort),
            iconResource = R.drawable.ic_tune_24,
            onClick = ::chooseEffort,
        )
        content.addView(effortRow.view)
        fastRow = switchRow(
            title = getString(R.string.online_ai_fast_mode),
            summary = getString(R.string.online_ai_fast_mode_help),
            iconResource = R.drawable.ic_settings_tune,
            checked = fastMode,
        ) { checked ->
            fastMode = checked
            refreshState()
        }
        content.addView(fastRow.view)
        testRow = settingRow(
            title = getString(R.string.online_ai_action_test),
            summary = getString(R.string.service_detail_test_summary),
            iconResource = R.drawable.ic_check_circle_24,
            showChevron = false,
            onClick = ::testConnection,
        )
        content.addView(testRow.view)

        content.addView(sectionHeader(R.string.service_detail_models_section))
        content.addView(
            TextView(this).apply {
                text = getString(R.string.service_detail_models_help)
                textSize = 14f
                setTextColor(appPalette.secondaryText)
                setLineSpacing(0f, 1.1f)
                setPaddingRelative(uiDp(Ui.SPACE_XXL), 0, uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
            },
        )
        modelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(modelList)
        content.addView(
            settingRow(
                title = getString(R.string.service_detail_add_model),
                summary = getString(R.string.service_detail_add_model_summary),
                iconResource = R.drawable.ic_add_24,
                onClick = ::showAddModels,
            ).view,
        )
        renderModels()
        applyThemeToControls(content)
        return scaffold.root
    }

    private fun withTopMargin(field: TextInputLayout, marginDp: Int): TextInputLayout = field.apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = uiDp(marginDp) }
    }

    // ---- State ------------------------------------------------------------------------------

    private fun findProfile(id: String): ConfiguredOnlineAiProfile? =
        runCatching { registry.snapshot() }.getOrNull()?.profiles?.firstOrNull { it.profile.profileId == id }

    private fun valuesOf(profile: OnlineAiProfile) = OnlineAiProfileEditorValues(
        provider = profile.provider,
        name = profile.displayName,
        baseUrl = profile.baseUrl,
        modelIds = profile.modelIds,
        defaultModelId = profile.modelId,
        visionModelIds = profile.visionModelIds,
        effort = profile.defaultEffort,
        fastMode = profile.fastMode,
    )

    private fun newServiceValues(): OnlineAiProfileEditorValues {
        val initialProvider = OnlineAiProvider.OPENAI
        val model = modelCatalog.snapshot().catalog.defaultForProvider(initialProvider)
        return OnlineAiProfileEditorValues(
            provider = initialProvider,
            name = suggestedName(initialProvider),
            baseUrl = defaultBaseUrl(initialProvider),
            modelIds = listOf(model),
            defaultModelId = model,
            visionModelIds = emptyList(),
            effort = GenerationEffort.DEFAULT,
            fastMode = false,
        )
    }

    private fun suggestedName(provider: OnlineAiProvider): String = OnlineServiceText.uniqueName(
        onlineProviderLabel(provider),
        runCatching { registry.snapshot() }.getOrNull()?.profiles.orEmpty()
            .filter { it.profile.profileId != profileId }
            .map { it.profile.displayName },
    )

    private fun defaultBaseUrl(provider: OnlineAiProvider): String =
        OnlineAiProviderCatalog.templateFor(provider).defaultBaseUrl ?: OnlineAiBaseUrlHistoryPolicy.HTTPS_PREFIX

    private fun captureValues() = OnlineAiProfileEditorValues(
        provider = provider,
        name = nameInput.text?.toString().orEmpty(),
        baseUrl = baseUrlInput.text?.toString().orEmpty(),
        modelIds = models.modelIds,
        defaultModelId = models.preferredModelId,
        visionModelIds = models.visionModelIds,
        effort = effort,
        fastMode = fastMode,
    )

    @VisibleForTesting
    internal fun hasUnsavedChanges(): Boolean = OnlineAiProfileEditorPolicy.hasUnsavedChanges(
        initial = baseline,
        current = captureValues(),
        credentialLength = credentialInput.text?.length ?: 0,
    )

    private fun reloadPersisted() {
        profileId?.let { id -> persisted = findProfile(id) }
        refreshState()
    }

    /** Redraws everything that depends on the draft without rebuilding the text fields. */
    private fun refreshState() {
        if (!::testRow.isInitialized) return
        val saved = persisted
        providerRow.summaryView.text = onlineProviderLabel(provider)
        effortRow.summaryView.text = effortLabel(effort)
        fastRow.view.visibility = if (OnlineAiEffortSupport.supportsFast(provider.protocol)) View.VISIBLE else View.GONE
        clearKeyRow.view.visibility = if (saved?.configured == true) View.VISIBLE else View.GONE
        credentialField.helperText = getString(
            when {
                saved == null -> R.string.online_ai_credential_new_hint
                saved.configured -> R.string.online_ai_credential_edit_hint
                else -> R.string.service_detail_credential_missing
            },
        )
        val changed = hasUnsavedChanges()
        val testable = saved != null && saved.configured && !changed
        testRow.summaryView.text = getString(
            when {
                saved == null || changed -> R.string.service_detail_test_save_first
                !saved.configured -> R.string.service_detail_test_needs_key
                else -> R.string.service_detail_test_summary
            },
        )
        testRow.summaryView.visibility = View.VISIBLE
        testRow.setRowEnabled(testable)
        updateSaveItem()
    }

    private fun updateSaveItem() {
        val item = saveItem ?: return
        val enabled = profileId == null || hasUnsavedChanges()
        item.isEnabled = enabled
        item.icon = tintedDrawable(
            R.drawable.ic_check_24,
            if (enabled) appPalette.accent else AppColorPolicy.withAlpha(appPalette.secondaryText, 0x66),
        )
    }

    // ---- Connection details -----------------------------------------------------------------

    private fun chooseProvider() {
        val templates = OnlineAiProviderCatalog.templates
        singleChoiceDialog(
            title = getString(R.string.online_ai_provider),
            labels = templates.map { onlineProviderLabel(it.provider) },
            checkedIndex = templates.indexOfFirst { it.provider == provider },
        ) { index -> applyProvider(templates[index].provider) }
    }

    /** Same rules as the old editor: a new protocol resets the Base URL and the image-input choices. */
    private fun applyProvider(next: OnlineAiProvider) {
        if (next == provider) return
        provider = next
        baseUrlInput.setText(defaultBaseUrl(next))
        models = if (persisted == null) {
            val model = modelCatalog.snapshot().catalog.defaultForProvider(next)
            ServiceModelList(listOf(model), model, emptyList())
        } else {
            models.copy(visionModelIds = emptyList())
        }
        if (persisted == null && nameInput.text?.toString() == lastSuggestedName) {
            lastSuggestedName = suggestedName(next)
            nameInput.setText(lastSuggestedName)
            nameInput.setSelection(nameInput.text?.length ?: 0)
        }
        renderModels()
        refreshState()
    }

    private fun chooseEffort() {
        singleChoiceDialog(
            title = getString(R.string.online_ai_default_effort),
            labels = GenerationEffort.entries.map { effortLabel(it) },
            checkedIndex = GenerationEffort.entries.indexOf(effort),
        ) { index ->
            effort = GenerationEffort.entries[index]
            refreshState()
        }
    }

    /** Clearing the key takes effect at once, as from the card menu (D123); the draft is untouched. */
    private fun confirmClearKey() {
        val saved = persisted ?: return
        confirmDialog(
            title = getString(R.string.online_ai_clear_key_title),
            message = getString(R.string.online_ai_clear_key_message, saved.profile.displayName),
            positiveResource = R.string.online_ai_action_clear_key,
            destructive = true,
        ) {
            val latest = profileId?.let(::findProfile) ?: return@confirmDialog
            runCatching { registry.save(latest.profile, OnlineAiCredentialUpdate.Clear) }
                .onSuccess { showSnackbar(screenRoot, getString(R.string.online_ai_key_cleared)) }
                .onFailure {
                    showSnackbar(screenRoot, getString(R.string.online_ai_key_clear_failed), Snackbar.LENGTH_LONG)
                }
            reloadPersisted()
        }
    }

    private fun testConnection() {
        val saved = persisted ?: return
        if (!saved.configured || hasUnsavedChanges()) return
        tester.confirm(saved.profile.profileId, saved.profile.displayName)
    }

    // ---- Saving and leaving -----------------------------------------------------------------

    private fun requestClose() {
        if (::credentialInput.isInitialized && hasUnsavedChanges()) {
            confirmDialog(
                title = getString(R.string.form_discard_changes_title),
                message = getString(R.string.form_discard_changes_message),
                positiveResource = R.string.form_discard_changes_confirm,
                destructive = true,
            ) { finish() }
        } else {
            finish()
        }
    }

    @VisibleForTesting
    internal fun save(): Boolean {
        val values = captureValues()
        val existing = profileId?.let(::findProfile)
        if (profileId != null && existing == null) {
            showSnackbar(screenRoot, getString(R.string.service_detail_missing), Snackbar.LENGTH_LONG)
            return false
        }
        val candidate = runCatching {
            val raw = existing?.profile?.copy(
                displayName = values.name,
                provider = values.provider,
                baseUrl = values.baseUrl,
                modelId = values.defaultModelId,
                modelIds = values.modelIds,
                visionModelIds = values.visionModelIds,
                defaultEffort = values.effort,
                fastMode = values.fastMode,
            ) ?: OnlineAiProfile.create(
                displayName = values.name,
                provider = values.provider,
                baseUrl = values.baseUrl,
                modelId = values.defaultModelId,
                modelIds = values.modelIds,
                visionModelIds = values.visionModelIds,
                defaultEffort = values.effort,
                fastMode = values.fastMode,
            )
            OnlineAiProfilePolicy.normalizeProfile(raw)
        }.getOrElse {
            showSnackbar(screenRoot, getString(R.string.online_ai_form_invalid), Snackbar.LENGTH_LONG)
            return false
        }
        val credentialText = credentialInput.text ?: return false
        if (credentialText.isNotEmpty() && !OnlineServiceText.isValidCredential(credentialText)) {
            credentialField.error = getString(R.string.online_ai_credential_invalid)
            return false
        }
        val destinationChanged = existing != null &&
            !OnlineAiProfileUrls.sameCredentialDestination(existing.profile, candidate)
        if (destinationChanged && existing.configured && credentialText.isEmpty()) {
            credentialField.error = getString(R.string.online_ai_credential_required_after_destination_change)
            return false
        }
        val replacement = credentialText.takeIf(Editable::isNotEmpty)?.let(OnlineServiceText::copyCharacters)
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
        if (saved.isFailure) {
            showSnackbar(screenRoot, getString(R.string.online_ai_profile_save_failed), Snackbar.LENGTH_LONG)
            return false
        }
        credentialText.clear()
        baseUrlHistoryStore.record(candidate.baseUrl)
        existing?.let { followReorderedModels(candidate.profileId, it.profile.modelIds, candidate.modelIds) }
        profileId = candidate.profileId
        persisted = findProfile(candidate.profileId)
        val stored = persisted?.profile ?: candidate
        baseline = valuesOf(stored)
        lastSuggestedName = null
        // Show what was stored (names and IDs are trimmed on save) and turn the page into an edit page.
        provider = stored.provider
        effort = stored.defaultEffort
        fastMode = stored.fastMode
        models = ServiceModelList(stored.modelIds, stored.modelId, stored.visionModelIds)
        if (nameInput.text?.toString() != stored.displayName) nameInput.setText(stored.displayName)
        if (baseUrlInput.text?.toString() != stored.baseUrl) baseUrlInput.setText(stored.baseUrl)
        supportActionBar?.title = getString(R.string.service_detail_title)
        renderModels()
        refreshState()
        showSnackbar(screenRoot, getString(R.string.online_ai_profile_saved))
        return true
    }

    /** D122: the stored default target and the switcher's favorites follow a changed first model. */
    private fun followReorderedModels(profileId: String, oldModelIds: List<String>, newModelIds: List<String>) {
        val mapping = ServiceModelListPolicy.targetIdRemap(profileId, oldModelIds, newModelIds)
        if (mapping.isEmpty()) return
        runCatching { applicationState.defaultTargetStore.replace(mapping) }
            .onFailure { Log.w(TAG, "Unable to move the default target with the reordered models", it) }
        runCatching { ModelPickerFavoritesStore(this).replace(mapping) }
            .onFailure { Log.w(TAG, "Unable to move the favorites with the reordered models", it) }
    }

    // ---- Model list -------------------------------------------------------------------------

    private fun renderModels() {
        modelList.removeAllViews()
        models.modelIds.forEach { modelId -> modelList.addView(modelRow(modelId)) }
        applyThemeToControls(modelList)
    }

    private fun modelRow(modelId: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = uiDp(72)
            tag = modelId
            setPaddingRelative(uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_SM))
            isClickable = true
            isFocusable = true
            applyThemedSelectableBackground()
            contentDescription = modelId
        }
        val handle = ImageView(this).apply {
            setImageDrawable(tintedDrawable(R.drawable.ic_drag_handle_24, appPalette.secondaryText))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.service_detail_drag_handle)
        }
        row.addView(handle, LinearLayout.LayoutParams(uiDp(48), uiDp(48)))
        val title = TextView(this).apply {
            text = modelId
            textSize = Ui.TEXT_ITEM
            setTextColor(appPalette.primaryText)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        val badges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPaddingRelative(0, uiDp(6), 0, 0)
        }
        modelBadges(modelId).forEach { badge ->
            badges.addView(
                statusBadge(badge),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = uiDp(Ui.SPACE_SM) },
            )
        }
        badges.visibility = if (badges.childCount == 0) View.GONE else View.VISIBLE
        row.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(uiDp(Ui.SPACE_SM), 0, uiDp(Ui.SPACE_SM), 0)
                addView(title)
                addView(badges)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val more = ImageButton(this).apply {
            setImageDrawable(tintedDrawable(R.drawable.ic_more_vert_24, appPalette.secondaryText))
            background = null
            applyThemedSelectableBackground(borderless = true)
            contentDescription = getString(R.string.service_detail_model_actions)
            setOnClickListener { anchor -> showModelActions(anchor, modelId) }
        }
        row.addView(more, LinearLayout.LayoutParams(uiDp(48), uiDp(48)))
        row.setOnClickListener { showModelActions(more, modelId) }
        row.setOnTouchListener { _, event ->
            lastTouchRawY = event.rawY
            drag.handle(row, event)
        }
        row.setOnLongClickListener {
            drag.start(row, lastTouchRawY)
            true
        }
        handle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) drag.start(row, event.rawY)
            drag.handle(row, event)
            true
        }
        ViewCompat.setStateDescription(row, modelBadges(modelId).joinToString(", ") { it.text })
        return row
    }

    private fun modelBadges(modelId: String): List<CardBadge> = buildList {
        if (modelId == models.preferredModelId) {
            add(CardBadge(getString(R.string.service_detail_preferred_badge), BadgeTone.ACCENT))
        }
        if (modelId in models.visionModelIds) {
            add(CardBadge(getString(R.string.model_picker_tag_vision), BadgeTone.NEUTRAL))
        }
        if (OnlineAiEffortSupport.levels(provider.protocol, modelId).isNotEmpty()) {
            add(CardBadge(getString(R.string.model_picker_tag_reasoning), BadgeTone.NEUTRAL))
        }
    }

    private fun showModelActions(anchor: View, modelId: String) {
        val index = models.modelIds.indexOf(modelId)
        if (index < 0) return
        PopupMenu(this, anchor).apply {
            menu.add(0, ACTION_PREFERRED, 0, R.string.service_detail_action_preferred).isEnabled =
                modelId != models.preferredModelId
            menu.add(0, ACTION_VISION, 1, R.string.model_picker_tag_vision).apply {
                isCheckable = true
                isChecked = modelId in models.visionModelIds
            }
            menu.add(0, ACTION_MOVE_UP, 2, R.string.service_detail_action_move_up).isEnabled = index > 0
            menu.add(0, ACTION_MOVE_DOWN, 3, R.string.service_detail_action_move_down).isEnabled =
                index < models.modelIds.lastIndex
            menu.add(0, ACTION_REMOVE, 4, R.string.service_detail_action_remove).isEnabled =
                ServiceModelListPolicy.canRemove(models)
            setOnMenuItemClickListener { item ->
                applyModelAction(modelId, item.itemId)
                true
            }
            show()
        }
    }

    /** One entry of a model row's menu; the device test calls it in place of the popup. */
    @VisibleForTesting
    internal fun applyModelAction(modelId: String, action: Int) {
        val index = models.modelIds.indexOf(modelId)
        if (index < 0) return
        models = when (action) {
            ACTION_PREFERRED -> ServiceModelListPolicy.setPreferred(models, modelId)
            ACTION_VISION -> ServiceModelListPolicy.setVision(models, modelId, modelId !in models.visionModelIds)
            ACTION_MOVE_UP -> ServiceModelListPolicy.move(models, index, index - 1)
            ACTION_MOVE_DOWN -> ServiceModelListPolicy.move(models, index, index + 1)
            ACTION_REMOVE -> ServiceModelListPolicy.remove(models, modelId)
            else -> models
        }
        renderModels()
        refreshState()
    }

    /** The model rows from top to bottom as shown, which a drag changes before the draft does. */
    @VisibleForTesting
    internal val shownModelOrder: List<String>
        get() = (0 until modelList.childCount).map { modelList.getChildAt(it).tag as String }

    @VisibleForTesting
    internal val draftModels: ServiceModelList
        get() = models

    /** "Add models": the available (preset) models grouped by vendor plus typed IDs (D96, D104). */
    private fun showAddModels() {
        val presets = modelCatalog.snapshot().catalog.forProvider(provider).filterNot(models.modelIds::contains)
        val boxes = presets.associateWith { modelId ->
            MaterialCheckBox(this).apply {
                text = modelId
                textSize = 14f
                minimumHeight = uiDp(48)
                buttonTintList = controlTintList()
                buttonIconTintList = ColorStateList.valueOf(appPalette.onAccent)
                setTextColor(appPalette.primaryText)
            }
        }
        val groups = OnlineAiModelCatalogGroups.group(provider, presets, getString(R.string.online_ai_catalog_other_models))
        val (customField, customInput) = formTextField(
            initialValue = "",
            hint = getString(R.string.online_ai_custom_model_ids),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            maxLength = OnlineAiProfilePolicy.MAXIMUM_MODEL_ID_BYTES * OnlineAiProfilePolicy.MAXIMUM_MODELS_PER_PROFILE,
            singleLine = false,
        )
        customField.placeholderText = getString(R.string.online_ai_custom_model_ids_hint)
        customInput.minLines = 2
        customInput.maxLines = 5
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(uiDp(Ui.SPACE_XXL), 0, uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
            addView(
                TextView(context).apply {
                    text = getString(R.string.service_detail_add_model_help, ServiceModelListPolicy.MAXIMUM_MODELS)
                    textSize = 12.5f
                    setTextColor(appPalette.secondaryText)
                    setLineSpacing(0f, 1.15f)
                },
            )
            if (groups.isNotEmpty()) addView(formLabel(R.string.service_detail_available_models))
            groups.forEach { group -> addView(presetGroup(group.title, group.modelIds.map(boxes::getValue), groups.size == 1)) }
            addView(withTopMargin(customField, Ui.SPACE_LG))
        }
        formBottomSheet(
            title = getString(R.string.service_detail_add_model),
            content = content,
            positiveResource = R.string.service_detail_add_confirm,
            onPositive = {
                val chosen = presets.filter { boxes.getValue(it).isChecked } +
                    customInput.text.toString().lineSequence().toList()
                when (val result = ServiceModelListPolicy.add(models, chosen)) {
                    is ServiceModelAddResult.Added -> {
                        models = result.list
                        renderModels()
                        refreshState()
                        true
                    }
                    ServiceModelAddResult.NothingNew -> {
                        customField.error = getString(R.string.service_detail_models_already_added)
                        false
                    }
                    ServiceModelAddResult.Invalid -> {
                        customField.error = getString(R.string.service_detail_model_ids_invalid)
                        false
                    }
                    is ServiceModelAddResult.TooMany -> {
                        customField.error = getString(R.string.service_detail_model_limit, result.limit)
                        false
                    }
                }
            },
        )
    }

    private fun presetGroup(title: String, boxes: List<MaterialCheckBox>, expandedInitially: Boolean): View {
        var expanded = expandedInitially
        val models = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(uiDp(12), 0, 0, uiDp(8))
            boxes.forEach(::addView)
        }
        var updateExpansion: () -> Unit = {}
        val header = settingRow(
            title = "$title (${boxes.size})",
            onClick = {
                expanded = !expanded
                updateExpansion()
            },
        )
        ViewCompat.setAccessibilityHeading(header.view, true)
        updateExpansion = {
            models.visibility = if (expanded) View.VISIBLE else View.GONE
            ViewCompat.setStateDescription(
                header.view,
                getString(if (expanded) R.string.online_ai_catalog_expanded else R.string.online_ai_catalog_collapsed),
            )
            (header.view as? ViewGroup)?.let { row -> row.getChildAt(row.childCount - 1)?.rotation = if (expanded) 90f else 0f }
        }
        updateExpansion()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header.view)
            addView(models)
        }
    }

    /**
     * Drag-to-reorder for the model rows (D96, D124): the drag handle starts at once, the row after a
     * long press. Neighbours swap places as the dragged row passes their middle; the row itself is
     * never detached, so it keeps receiving the touch stream. Row heights come from the last layout,
     * not from view tops, so a swap that has not been laid out yet cannot swap straight back.
     */
    private inner class ModelDrag {
        private var dragged: View? = null
        private var lastRawY = 0f
        private var restingBackground: Drawable? = null

        fun start(row: View, rawY: Float) {
            if (dragged != null || modelList.childCount < 2) return
            dragged = row
            lastRawY = rawY
            row.isPressed = false
            restingBackground = row.background
            row.setBackgroundColor(appPalette.surface)
            row.elevation = uiDpF(6f)
            row.parent?.requestDisallowInterceptTouchEvent(true)
            row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        /** Returns true while [row] is being dragged, so the row's own click handling stays out. */
        fun handle(row: View, event: MotionEvent): Boolean {
            if (dragged !== row) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> move(row, event.rawY)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drop(row)
            }
            return true
        }

        private fun move(row: View, rawY: Float) {
            row.translationY += rawY - lastRawY
            lastRawY = rawY
            var index = modelList.indexOfChild(row)
            // A neighbor swaps once the dragged row's leading edge passes the neighbor's midpoint.
            while (true) {
                val top = layoutTop(index) + row.translationY
                val above = if (index > 0) modelList.getChildAt(index - 1) else null
                val below = if (index < modelList.childCount - 1) modelList.getChildAt(index + 1) else null
                if (above != null && top < layoutTop(index - 1) + above.height / 2f) {
                    modelList.removeViewAt(index - 1)
                    modelList.addView(above, index)
                    row.translationY += above.height
                    index -= 1
                } else if (below != null && top + row.height > layoutTop(index) + row.height + below.height / 2f) {
                    modelList.removeViewAt(index + 1)
                    modelList.addView(below, index)
                    row.translationY -= below.height
                    index += 1
                } else {
                    break
                }
            }
        }

        private fun layoutTop(index: Int): Float {
            var top = modelList.paddingTop.toFloat()
            for (position in 0 until index) top += modelList.getChildAt(position).height
            return top
        }

        private fun drop(row: View) {
            dragged = null
            row.animate().translationY(0f).setDuration(DROP_ANIMATION_MILLIS).withEndAction {
                row.elevation = 0f
                row.background = restingBackground
                restingBackground = null
            }.start()
            val order = (0 until modelList.childCount).map { modelList.getChildAt(it).tag as String }
            if (order != models.modelIds) {
                models = ServiceModelListPolicy.reorder(models, order)
                refreshState()
            }
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "serviceProfileId"
        private const val KEY_DRAFT = "serviceDetailDraft"
        private const val MENU_SAVE = 2401
        internal const val ACTION_PREFERRED = 2501
        internal const val ACTION_VISION = 2502
        internal const val ACTION_MOVE_UP = 2503
        internal const val ACTION_MOVE_DOWN = 2504
        internal const val ACTION_REMOVE = 2505
        private const val DROP_ANIMATION_MILLIS = 150L
        private const val TAG = "ThreeStoneServiceDetail"

        /** Opens [profileId]'s details, or an empty "Add service" page when it is null. */
        internal fun intent(context: Context, profileId: String?): Intent =
            Intent(context, ServiceDetailActivity::class.java).apply {
                profileId?.let { putExtra(EXTRA_PROFILE_ID, it) }
            }
    }
}
