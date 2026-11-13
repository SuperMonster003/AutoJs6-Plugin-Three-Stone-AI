package io.github.supermonster003.autojs6.plugin.threestoneai

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.snackbar.Snackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.ConfiguredOnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCredentialUpdate
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiModelCatalogRefreshResult
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiModelCatalogRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileCodec
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfilePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistrySnapshot
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileTransferCodec
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.createOnlineAiModelCatalogRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.BadgeTone
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.CardBadge
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.buildScaffold
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.confirmDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.inputDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.insertRowHairlines
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.itemCard
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.sectionHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.switchRow
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * The models and services page (roadmap 1.7.0 P5.2, D95, D97, D115): the page frame, the default
 * target row, the metered-network switch and the online services section. [ModelManagerActivity],
 * the only concrete page, adds the local models section below.
 */
abstract class OnlineServicesActivity : ConfiguredActivity() {
    private lateinit var applicationState: ThreeStoneAiApplication
    private lateinit var registry: OnlineAiProfileRegistry
    private lateinit var modelCatalog: OnlineAiModelCatalogRepository
    private val catalogSettings by lazy {
        getSharedPreferences("online-model-catalog-settings", MODE_PRIVATE)
    }
    private val catalogExecutor = Executors.newSingleThreadExecutor { action ->
        Thread(action, "three-stone-ai-model-catalog").apply { isDaemon = true }
    }
    private var catalogRefresh: Future<*>? = null
    private var catalogRefreshing = false
    private lateinit var topContent: LinearLayout
    private lateinit var settingsContent: LinearLayout
    protected lateinit var screenRoot: View
        private set
    private var snapshot: OnlineAiProfileRegistrySnapshot? = null
    private var targets: List<AiTarget> = emptyList()
    private var defaultTargetId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val testerDelegate = lazy { OnlineServiceConnectionTester(this, { screenRoot }) { reloadSnapshot() } }
    private val tester by testerDelegate
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
        modelCatalog = createOnlineAiModelCatalogRepository(applicationContext)
        setContentView(createContentView(savedInstanceState))
        reloadSnapshot()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_ADD_PROFILE, false)) {
            // Kept for callers of the old "add service" entry: it now opens the details page (P5.4).
            intent.removeExtra(EXTRA_ADD_PROFILE)
            openServiceDetails(null)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::settingsContent.isInitialized) reloadSnapshot()
        if (::modelCatalog.isInitialized && catalogSettings.getBoolean(KEY_AUTOMATIC_CATALOG, true)) {
            refreshModelCatalog(manual = false)
        }
    }

    override fun onDestroy() {
        if (::modelCatalog.isInitialized) modelCatalog.cancelRefresh()
        catalogRefresh?.cancel(true)
        catalogExecutor.shutdownNow()
        if (testerDelegate.isInitialized()) tester.close()
        super.onDestroy()
    }

    private fun createContentView(savedInstanceState: Bundle?): View {
        val scaffold = buildScaffold(R.string.model_manager_title)
        screenRoot = scaffold.root
        topContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        settingsContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scaffold.content.addView(topContent)
        scaffold.content.addView(settingsContent)
        buildLocalSection(scaffold.content, savedInstanceState)
        applyThemeToControls(scaffold.content)
        return scaffold.root
    }

    /** Adds the local models section below the online services; called once from onCreate. */
    protected abstract fun buildLocalSection(content: LinearLayout, savedInstanceState: Bundle?)

    /** Called after [refreshTargets] loaded the catalog, so the local cards can redraw their badges. */
    protected open fun onTargetsChanged() = Unit

    /**
     * Reloads the merged target catalog and redraws everything that shows the default target. Runs on
     * the main thread like the chat page's catalog refresh; the catalog reads only small private files.
     */
    protected fun refreshTargets() {
        val catalog = runCatching { applicationState.aiBackend.catalog() }.getOrNull()
        targets = catalog?.targets.orEmpty()
        defaultTargetId = catalog?.defaultTargetId
        renderTopRows()
        renderSettings()
        onTargetsChanged()
    }

    protected fun isDefaultTarget(targetId: String): Boolean = targetId == defaultTargetId

    internal fun targetFor(targetId: String): AiTarget? = targets.firstOrNull { it.targetId == targetId }

    /** Stores [targetId] as the one global default target (D97, D114). */
    protected fun makeDefault(targetId: String) {
        runCatching { applicationState.defaultTargetStore.write(targetId) }
            .onSuccess { showSnackbar(screenRoot, getString(R.string.models_default_saved)) }
            .onFailure {
                showSnackbar(screenRoot, getString(R.string.models_default_save_failed), Snackbar.LENGTH_LONG)
            }
        refreshTargets()
    }

    private fun reloadSnapshot() {
        val result = runCatching(registry::snapshot)
        snapshot = result.getOrNull()
        refreshTargets()
    }

    private fun renderTopRows() {
        topContent.removeAllViews()
        val current = defaultTargetId?.let(::targetFor)
        topContent.addView(
            settingRow(
                title = getString(R.string.models_default_target),
                summary = current?.let(TargetPickerTexts(this)::summary)
                    ?: getString(R.string.models_default_target_none),
                iconResource = R.drawable.ic_tune_24,
                onClick = ::showDefaultTargetPicker,
            ).view,
        )
        snapshot?.let { settings ->
            topContent.addView(
                switchRow(
                    title = getString(R.string.online_ai_allow_metered_network),
                    summary = getString(R.string.online_ai_allow_metered_network_summary),
                    iconResource = R.drawable.ic_cloud_24,
                    checked = settings.allowMeteredNetwork,
                ) { checked -> updateMeteredNetworkPolicy(checked) }.view,
            )
        }
        insertRowHairlines(topContent)
        applyThemeToControls(topContent)
    }

    private fun renderSettings() {
        settingsContent.removeAllViews()
        settingsContent.addView(sectionHeader(R.string.online_ai_profiles_section, ::showOnlineSectionMenu))
        val current = snapshot
        if (current == null) {
            settingsContent.addView(messageBlock(R.string.online_ai_settings_unavailable))
            return
        }
        current.profiles
            .sortedBy { state -> state.profile.displayName.lowercase(Locale.ROOT) }
            .forEach { state -> settingsContent.addView(profileCard(state)) }
        settingsContent.addView(
            settingRow(
                title = getString(R.string.online_ai_add_profile),
                summary = getString(R.string.online_ai_add_profile_summary),
                iconResource = R.drawable.ic_add_24,
                onClick = { openServiceDetails(null) },
            ).view,
        )
        applyThemeToControls(settingsContent)
    }

    /** The default target picker lists only targets that can answer now; there is no "none" choice. */
    private fun showDefaultTargetPicker() {
        val usable = targets.filter(TargetPickerTexts::isUsable)
        if (usable.isEmpty()) {
            showSnackbar(screenRoot, getString(R.string.models_default_none_ready), Snackbar.LENGTH_LONG)
            return
        }
        val texts = TargetPickerTexts(this)
        val favorites = ModelPickerFavoritesStore(this)
        showModelPickerSheet(
            state = ModelPickerState(
                targets = usable,
                selectedTargetId = defaultTargetId,
                favorites = favorites.load(),
                currentTarget = null,
                effort = null,
                fast = null,
            ),
            actions = ModelPickerActions(
                rowTitle = texts::rowTitle,
                rowSubtitle = texts::rowSubtitle,
                groupTitle = texts::groupTitle,
                searchText = texts::searchText,
                isReady = TargetPickerTexts::isUsable,
                onSelect = { target -> makeDefault(target.targetId) },
                onToggleFavorite = favorites::toggle,
                onEffort = {},
                onFast = {},
                onManage = null,
            ),
            title = getString(R.string.models_default_picker_title),
        )
    }

    private fun showOnlineSectionMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_IMPORT, 0, R.string.online_ai_import_profiles)
            menu.add(0, MENU_EXPORT, 1, R.string.online_ai_export_profiles)
            menu.add(0, MENU_AUTOMATIC_CATALOG, 2, R.string.online_ai_catalog_automatic).apply {
                isCheckable = true
                isChecked = catalogSettings.getBoolean(KEY_AUTOMATIC_CATALOG, true)
            }
            menu.add(0, MENU_REFRESH_CATALOG, 3, R.string.online_ai_catalog_refresh).isEnabled = !catalogRefreshing
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_IMPORT -> openProfileImportPicker()
                    MENU_EXPORT -> openProfileExportPicker()
                    MENU_AUTOMATIC_CATALOG -> setAutomaticCatalog(!item.isChecked)
                    MENU_REFRESH_CATALOG -> refreshModelCatalog(manual = true)
                }
                true
            }
            show()
        }
    }

    private fun setAutomaticCatalog(enabled: Boolean) {
        catalogSettings.edit().putBoolean(KEY_AUTOMATIC_CATALOG, enabled).apply()
        showSnackbar(
            screenRoot,
            getString(
                if (enabled) R.string.online_ai_catalog_automatic_summary
                else R.string.online_ai_catalog_automatic_off,
            ),
            Snackbar.LENGTH_LONG,
        )
        if (enabled) refreshModelCatalog(manual = false)
    }

    private fun refreshModelCatalog(manual: Boolean) {
        if (catalogRefreshing || isFinishing || isDestroyed) return
        catalogRefreshing = true
        if (manual) showSnackbar(screenRoot, getString(R.string.online_ai_catalog_checking))
        catalogRefresh = catalogExecutor.submit {
            val result = runCatching { modelCatalog.refresh(force = manual) }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                catalogRefreshing = false
                catalogRefresh = null
                // The page shows no catalog state; an open editor keeps its unsaved selection.
                if (manual) {
                    val failure = result.exceptionOrNull()
                    val message = when {
                        failure is OnlineAiFailureException &&
                            failure.reason == OnlineAiFailureReason.METERED_NETWORK_DISALLOWED ->
                            R.string.online_ai_catalog_metered
                        failure != null -> R.string.online_ai_catalog_failed
                        result.getOrNull() == OnlineAiModelCatalogRefreshResult.UPDATED ->
                            R.string.online_ai_catalog_updated
                        else -> R.string.online_ai_catalog_current
                    }
                    showSnackbar(screenRoot, getString(message), Snackbar.LENGTH_LONG)
                }
            }
        }
    }

    private fun profileCard(state: ConfiguredOnlineAiProfile): View {
        val profile = state.profile
        val models = resources.getQuantityString(
            R.plurals.models_service_model_count,
            profile.modelIds.size,
            profile.modelIds.size,
        )
        val badges = buildList {
            if (defaultTargetId?.let(::targetFor)?.profileId == profile.profileId) {
                add(CardBadge(getString(R.string.models_badge_default), BadgeTone.ACCENT))
            }
            add(
                if (state.configured) CardBadge(getString(R.string.models_badge_ready), BadgeTone.NEUTRAL)
                else CardBadge(getString(R.string.models_badge_missing_key), BadgeTone.WARNING),
            )
        }
        return itemCard(
            title = profile.displayName,
            subtitle = "${onlineProviderLabel(profile.provider)} | $models",
            badges = badges,
            actionsDescription = R.string.online_ai_profile_action_hint,
            onActions = { anchor -> showProfileActions(anchor, state) },
            onClick = { openServiceDetails(profile.profileId) },
        )
    }

    /** The service details page (P5.4, D96) replaces the old editor sheet; null adds a service. */
    private fun openServiceDetails(profileId: String?) {
        startActivity(ServiceDetailActivity.intent(this, profileId))
    }

    private fun messageBlock(resource: Int) = secondaryText(getString(resource), 15f).apply {
        setPaddingRelative(uiDp(20), uiDp(24), uiDp(20), uiDp(24))
    }

    private fun secondaryText(value: CharSequence, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(appPalette.secondaryText)
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

    /** The service's preferred model as a target: what "Set as default" stores. */
    private fun preferredTarget(profile: OnlineAiProfile): AiTarget? = targets.firstOrNull { target ->
        target.profileId == profile.profileId && target.modelId == profile.modelId
    }

    /** One fixed order on every card (D100); actions that do not apply now stay visible but disabled. */
    private fun showProfileActions(anchor: View, state: ConfiguredOnlineAiProfile) {
        val preferred = preferredTarget(state.profile)
        PopupMenu(this, anchor).apply {
            menu.add(0, ACTION_SET_DEFAULT, 0, R.string.online_ai_action_set_default).isEnabled =
                preferred != null && TargetPickerTexts.isUsable(preferred) && !isDefaultTarget(preferred.targetId)
            menu.add(0, ACTION_TEST, 1, R.string.online_ai_action_test).isEnabled = state.configured
            menu.add(0, ACTION_RENAME, 2, R.string.button_rename_model)
            menu.add(0, ACTION_CLONE, 3, R.string.online_ai_action_clone)
            menu.add(0, ACTION_CLEAR_KEY, 4, R.string.online_ai_action_clear_key).isEnabled = state.configured
            menu.add(0, ACTION_DELETE, 5, R.string.online_ai_action_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    ACTION_SET_DEFAULT -> preferred?.let { makeDefault(it.targetId) }
                    ACTION_TEST -> tester.confirm(state.profile.profileId, state.profile.displayName)
                    ACTION_RENAME -> confirmProfileRename(state)
                    ACTION_CLONE -> cloneProfile(state)
                    ACTION_CLEAR_KEY -> confirmClearKey(state)
                    ACTION_DELETE -> confirmDeleteProfile(state)
                }
                true
            }
            show()
        }
    }

    private fun confirmProfileRename(state: ConfiguredOnlineAiProfile) {
        inputDialog(
            title = getString(R.string.online_ai_rename_title),
            initialValue = state.profile.displayName,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            positiveResource = R.string.button_rename_model,
            validate = { value ->
                if (renamedProfile(state, value) == null) getString(R.string.online_ai_rename_invalid) else null
            },
        ) { value -> renameProfile(state, value) }
    }

    /** The renamed profile, or null when the name is empty, too long or used by another service. */
    private fun renamedProfile(state: ConfiguredOnlineAiProfile, value: String): OnlineAiProfile? {
        val renamed = runCatching {
            OnlineAiProfilePolicy.normalizeProfile(state.profile.copy(displayName = value))
        }.getOrNull() ?: return null
        val taken = snapshot?.profiles.orEmpty().any { other ->
            other.profile.profileId != state.profile.profileId &&
                other.profile.displayName.lowercase(Locale.ROOT) == renamed.displayName.lowercase(Locale.ROOT)
        }
        return renamed.takeUnless { taken }
    }

    private fun renameProfile(state: ConfiguredOnlineAiProfile, value: String) {
        val renamed = renamedProfile(state, value)
        if (renamed == null) {
            showSnackbar(screenRoot, getString(R.string.online_ai_rename_invalid), Snackbar.LENGTH_LONG)
            return
        }
        if (renamed.displayName == state.profile.displayName) return
        runCatching { registry.save(renamed, OnlineAiCredentialUpdate.Keep) }
            .onSuccess { showSnackbar(screenRoot, getString(R.string.online_ai_renamed)) }
            .onFailure {
                showSnackbar(screenRoot, getString(R.string.online_ai_rename_failed), Snackbar.LENGTH_LONG)
            }
        reloadSnapshot()
    }

    private fun cloneProfile(state: ConfiguredOnlineAiProfile) {
        val profile = state.profile
        val clone = runCatching {
            OnlineAiProfile.create(
                displayName = OnlineServiceText.uniqueName(
                    getString(R.string.online_ai_clone_name, profile.displayName),
                    snapshot?.profiles.orEmpty().map { it.profile.displayName },
                ),
                provider = profile.provider,
                baseUrl = profile.baseUrl,
                modelId = profile.modelId,
                modelIds = profile.modelIds,
                visionModelIds = profile.visionModelIds,
                defaultEffort = profile.defaultEffort,
                fastMode = profile.fastMode,
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
                        defaultEffort = source.defaultEffort,
                        fastMode = source.fastMode,
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
        if (tester.activeProfileId == state.profile.profileId) tester.cancel(showFeedback = false)
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

    companion object {
        const val EXTRA_ADD_PROFILE = "addOnlineProfile"

        private const val MENU_IMPORT = 2201
        private const val MENU_EXPORT = 2202
        private const val MENU_AUTOMATIC_CATALOG = 2203
        private const val MENU_REFRESH_CATALOG = 2204
        private const val ACTION_SET_DEFAULT = 2301
        private const val ACTION_TEST = 2302
        private const val ACTION_RENAME = 2303
        private const val ACTION_CLONE = 2304
        private const val ACTION_CLEAR_KEY = 2305
        private const val ACTION_DELETE = 2306
        private const val MIME_JSON = "application/json"
        private const val PROFILE_EXPORT_FILE_NAME = "3-stone-ai-online-profiles.json"
        private const val KEY_AUTOMATIC_CATALOG = "automatic-checks"
    }
}
