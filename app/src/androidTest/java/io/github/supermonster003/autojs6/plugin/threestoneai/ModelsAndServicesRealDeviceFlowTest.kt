package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.download.AvailableLiteRtModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadState
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.model.PRIVATE_MODEL_DIRECTORY_NAME
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in flows for a real device (roadmap 1.7.0 P5.7, D130). Every test is skipped unless the run
 * passes `-e realDeviceFlow true`; the normal suite never runs them.
 *
 * - Add a service on the service details page with a placeholder key, add two models, save, make the
 *   second model the default target from the "Models and services" page, and check that a new
 *   conversation uses it. The address ends in `.invalid`, so nothing can be sent there.
 * - With `-e realRequest true`: make an existing ready target whose model is `realRequestModel`
 *   (default [REQUEST_MODEL]) the default from the same page and get one short reply in a new
 *   conversation.
 * - With `-e realDownload true`: download `downloadModel` (default [DOWNLOAD_MODEL]) from the page in
 *   one step, check that it was verified and imported, then delete that model only.
 *
 * Each test puts back what it touched: the test service and the conversations are deleted; the
 * default target, the favorites, the base URL history, the token calibration, the remembered
 * conversation and the selected local model are restored. Nothing about the existing services
 * (keys included) is read or logged.
 */
class ModelsAndServicesRealDeviceFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val application get() = context.applicationContext as ThreeStoneAiApplication
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun aNewServiceWithAddedModelsBecomesTheDefaultAndANewConversationUsesIt() {
        requireFlow()
        val backup = takeBackup()
        val profilesBefore = profileIds()
        var created: String? = null
        try {
            ActivityScenario.launch<ServiceDetailActivity>(ServiceDetailActivity.intent(context, null)).use { scenario ->
                instrumentation.waitForIdleSync()
                onMain {
                    field(scenario, R.string.online_ai_profile_name).setText(SERVICE_NAME)
                    field(scenario, R.string.online_ai_base_url).setText(BASE_URL)
                    field(scenario, R.string.online_ai_credential).setText(PLACEHOLDER_KEY)
                }
                addTypedModels(scenario, listOf(MODEL_A, MODEL_B).joinToString(System.lineSeparator()))
                waitUntil("the typed models are listed") {
                    onActivity(scenario) { it.shownModelOrder.containsAll(listOf(MODEL_A, MODEL_B)) }
                }
                assertTrue("saving the new service succeeds", onActivity(scenario) { it.save() })
                created = (profileIds() - profilesBefore.toSet()).single()
            }
            val id = checkNotNull(created)
            val target = targetMatching("the saved service's second model") { it.profileId == id && it.modelId == MODEL_B }
            assertTrue("the saved service is ready", TargetPickerTexts.isUsable(target))

            chooseDefaultOnModelsPage(target)
            assertEquals("the page stored the chosen default", target.targetId, storedDefault())

            ChatTestEnvironment(scripted = false).use { environment ->
                environment.launchChat().use { scenario ->
                    assertEquals("a new conversation uses the new default", target.targetId, conversationTarget(scenario))
                    assertEquals(0, onChat(scenario) { it.messageCountForTest })
                }
            }
            log("service flow: PASS")
        } finally {
            created?.let(application.onlineProfileRegistry::delete)
            backup.restore()
        }
        assertEquals("the test service is gone", profilesBefore, profileIds())
        assertEquals("the default target is back", backup.defaultTarget, storedDefault())
    }

    @Test
    fun anExistingServiceMadeTheDefaultAnswersInANewConversation() {
        requireFlow()
        assumeTrue("Opt-in: -e realRequest true", flag("realRequest"))
        val modelId = arguments.getString("realRequestModel") ?: REQUEST_MODEL
        val backup = takeBackup()
        try {
            val target = targetMatching("a ready online target for $modelId") {
                it.profileId != null && it.modelId == modelId && TargetPickerTexts.isUsable(it)
            }
            chooseDefaultOnModelsPage(target)
            assertEquals(target.targetId, storedDefault())

            ChatTestEnvironment(scripted = false).use { environment ->
                environment.launchChat().use { scenario ->
                    assertEquals("a new conversation uses the new default", target.targetId, conversationTarget(scenario))
                    onChat(scenario) { activity ->
                        composer(activity).setText(PROMPT)
                        activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
                    }
                    waitUntil("the reply finishes", REPLY_TIMEOUT_MILLIS) {
                        onChat(scenario) { activity ->
                            val reply = activity.messagesForTest.lastOrNull { it.role == ChatMessageRole.ASSISTANT }
                            !activity.generatingForTest && reply != null && reply.status != ChatMessageStatus.GENERATING
                        }
                    }
                    val reply = onChat(scenario) { activity ->
                        activity.messagesForTest.last { it.role == ChatMessageRole.ASSISTANT }
                    }
                    assertEquals("the reply completed", ChatMessageStatus.COMPLETE, reply.status)
                    assertTrue("the reply has text", reply.text.isNotBlank())
                    assertEquals("the reply came from the default", target.targetId, reply.target?.targetId)
                    log("real request: PASS, reply characters ${reply.text.length}")
                }
            }
        } finally {
            backup.restore()
        }
        assertEquals("the default target is back", backup.defaultTarget, storedDefault())
    }

    @Test
    fun aRecommendedModelDownloadsAndIsImportedInOneStep() {
        requireFlow()
        assumeTrue("Opt-in: -e realDownload true", flag("realDownload"))
        val model = checkNotNull(RecommendedModelCatalog.find(arguments.getString("downloadModel") ?: DOWNLOAD_MODEL))
        val listed = AvailableLiteRtModelCatalog.models.single { it.id == model.id }
        val repository = ModelRepository(context)
        val before = repository.catalogSnapshot()
        val directory = File(context.filesDir, PRIVATE_MODEL_DIRECTORY_NAME)
        assumeTrue("${model.id} is already imported", before.entries.none { it.sha256 == model.expectedSha256 })
        assumeTrue(
            "A partial download already exists",
            directory.listFiles().orEmpty().none { ModelDownloadPolicy.partFileSha256(it.name) != null },
        )
        assumeTrue(
            "Private storage cannot hold ${model.id}",
            ModelDownloadPolicy.hasStorageFor(directory.usableSpace, model.expectedSizeBytes),
        )
        val backup = takeBackup()
        val coordinator = ModelDownloadCoordinator.get(context)
        val timeout = (arguments.getString("downloadTimeoutMinutes")?.toLongOrNull() ?: 60L) * 60_000L
        try {
            ActivityScenario.launch<ModelManagerActivity>(Intent(context, ModelManagerActivity::class.java)).use { scenario ->
                val browse = R.string.button_browse_litert_models
                waitUntil("the download row", PAGE_TIMEOUT_MILLIS) { onModels(scenario) { row(it, browse) != null } }
                val monitor = instrumentation.addMonitor(LiteRtModelCatalogActivity::class.java.name, null, false)
                val catalog = try {
                    onModels(scenario) { checkNotNull(row(it, browse)).performClick() }
                    instrumentation.waitForMonitorWithTimeout(monitor, PAGE_TIMEOUT_MILLIS)
                } finally {
                    instrumentation.removeMonitor(monitor)
                }
                checkNotNull(catalog) { "the catalog did not open" }
                val download = onMain { catalog.getString(R.string.litert_catalog_download) }
                onMain {
                    views(catalog.window.decorView).filterIsInstance<Button>()
                        .filter { it.text.toString().equals(download, ignoreCase = true) }
                        .single { button ->
                            val entry = (button.parent as View).parent as View
                            views(entry).any { it is TextView && it.text.toString() == listed.displayName }
                        }
                        .performClick()
                }
                waitUntil("the download is confirmed") { onMain { dialogButton(model.displayName, download, catalog) != null } }
                onMain { checkNotNull(dialogButton(model.displayName, download, catalog)).performClick() }

                val started = SystemClock.uptimeMillis()
                var lastLog = 0L
                waitUntil("the download finishes", timeout) {
                    val state = coordinator.state()
                    if (state is ModelDownloadState.Failed) error("the download failed: ${state.reason}")
                    if (state is ModelDownloadState.Running && SystemClock.uptimeMillis() - lastLog > LOG_INTERVAL_MILLIS) {
                        lastLog = SystemClock.uptimeMillis()
                        val percent = state.progress.processedBytes * 100 / model.expectedSizeBytes
                        log("download: ${state.stage} $percent%")
                    }
                    state is ModelDownloadState.Succeeded && state.model == model
                }
                val minutes = (SystemClock.uptimeMillis() - started) / 60_000.0
                val imported = repository.catalogSnapshot().entries.single { it.sha256 == model.expectedSha256 }
                assertEquals("the imported size matches the catalog", model.expectedSizeBytes, imported.sizeBytes)
                assertEquals(model.expectedSizeBytes, File(directory, imported.fileName).length())
                assertTrue(
                    "no partial file is left",
                    directory.listFiles().orEmpty().none { ModelDownloadPolicy.partFileSha256(it.name) != null },
                )
                assertTrue(
                    "the models imported before are untouched",
                    repository.catalogSnapshot().entries.containsAll(before.entries),
                )
                waitUntil("the page lists the new model", PAGE_TIMEOUT_MILLIS) {
                    onModels(scenario) { activity ->
                        views(activity.window.decorView).any { it is TextView && it.isShown && it.text.toString() == imported.displayName }
                    }
                }
                log("download: PASS, ${model.id}, ${model.expectedSizeBytes} bytes, ${"%.1f".format(minutes)} min")
            }
        } finally {
            when (val state = coordinator.state()) {
                is ModelDownloadState.Running -> coordinator.cancelDownload(state.operationId)
                is ModelDownloadState.Paused -> coordinator.cancelDownload(state.operationId)
                is ModelDownloadState.Failed -> coordinator.cancelDownload(state.operationId)
                else -> Unit
            }
            repository.catalogSnapshot().entries.filter { it.sha256 == model.expectedSha256 }
                .forEach { repository.deleteModel(it.modelId) }
            before.selectedModelId?.let { selected ->
                if (repository.catalogSnapshot().selectedModelId != selected) repository.selectExisting(selected)
            }
            backup.restore()
        }
        val after = repository.catalogSnapshot()
        assertEquals("only the downloaded model was removed", before.entries, after.entries)
        assertEquals("the selected local model is back", before.selectedModelId, after.selectedModelId)
        assertFalse(
            "no partial file is left",
            directory.listFiles().orEmpty().any { ModelDownloadPolicy.partFileSha256(it.name) != null },
        )
        assertEquals("the default target is back", backup.defaultTarget, storedDefault())
    }

    /** Opens "Models and services", taps "Default target" and picks [target]'s row in the sheet. */
    private fun chooseDefaultOnModelsPage(target: AiTarget) {
        ActivityScenario.launch<ModelManagerActivity>(Intent(context, ModelManagerActivity::class.java)).use { scenario ->
            val title = R.string.models_default_target
            waitUntil("the default target row", PAGE_TIMEOUT_MILLIS) { onModels(scenario) { row(it, title) != null } }
            val (sheetTitle, rowTitle, rowSubtitle) = onModels(scenario) { activity ->
                val texts = TargetPickerTexts(activity)
                Triple(activity.getString(R.string.models_default_picker_title), texts.rowTitle(target), texts.rowSubtitle(target))
            }
            onModels(scenario) { checkNotNull(row(it, title)).performClick() }
            val page = onModels(scenario) { it }
            waitUntil("the picker opens") { onMain { sheet(sheetTitle, page) != null } }
            onMain {
                val rows = views(checkNotNull(sheet(sheetTitle, page))).filterIsInstance<LinearLayout>().filter { row ->
                    row.isClickable && row.orientation == LinearLayout.HORIZONTAL &&
                        views(row).filterIsInstance<TextView>().map { it.text.toString() }.let { shown ->
                            rowTitle in shown && (rowSubtitle == null || rowSubtitle in shown)
                        }
                }
                rows.single().performClick()
            }
            waitUntil("the picker closes") { onMain { sheet(sheetTitle, page) == null } }
            waitUntil("the default is stored") { storedDefault() == target.targetId }
        }
    }

    private fun conversationTarget(scenario: ActivityScenario<ChatActivity>): String? {
        waitUntil("the conversation has a target", PAGE_TIMEOUT_MILLIS) {
            onChat(scenario) { it.conversationTargetIdForTest } != null
        }
        return onChat(scenario) { it.conversationTargetIdForTest }
    }

    private fun targetMatching(what: String, predicate: (AiTarget) -> Boolean): AiTarget {
        var found: AiTarget? = null
        val deadline = SystemClock.uptimeMillis() + PAGE_TIMEOUT_MILLIS
        while (found == null && SystemClock.uptimeMillis() < deadline) {
            found = application.aiBackend.catalog().targets.firstOrNull(predicate)
            if (found == null) SystemClock.sleep(200L)
        }
        assumeTrue("No $what on this device", found != null)
        return checkNotNull(found)
    }

    /** Opens "Add models", types [lines] into the custom field and confirms. */
    private fun addTypedModels(scenario: ActivityScenario<ServiceDetailActivity>, lines: String) {
        val (addTitle, hint, confirm) = onActivity(scenario) { activity ->
            Triple(
                activity.getString(R.string.service_detail_add_model),
                activity.getString(R.string.online_ai_custom_model_ids),
                activity.getString(R.string.service_detail_add_confirm),
            )
        }
        onActivity(scenario) { activity ->
            var row: View = views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == addTitle }
            while (!row.isClickable) row = row.parent as View
            row.performClick()
        }
        waitUntil("the add models sheet opens") { onMain { sheetField(hint) != null } }
        onMain { checkNotNull(sheetField(hint)).setText(lines) }
        onMain {
            WindowInspector.getGlobalWindowViews().flatMap(::views).filterIsInstance<Button>()
                .first { it.isShown && it.text.toString().equals(confirm, ignoreCase = true) }
                .performClick()
        }
        waitUntil("the add models sheet closes") { onMain { sheetField(hint) == null } }
    }

    private fun field(scenario: ActivityScenario<ServiceDetailActivity>, @StringRes hint: Int): EditText {
        var result: EditText? = null
        scenario.onActivity { activity ->
            val text = activity.getString(hint)
            result = views(activity.window.decorView).filterIsInstance<TextInputLayout>().first { it.hint?.toString() == text }.editText
        }
        return checkNotNull(result)
    }

    private fun sheetField(hint: String): EditText? = WindowInspector.getGlobalWindowViews()
        .filter { it.isShown }
        .flatMap(::views)
        .filterIsInstance<TextInputLayout>()
        .firstOrNull { it.isShown && it.hint?.toString() == hint }
        ?.editText

    /** The shown window, other than [page]'s own, that contains the text [title]. */
    private fun sheet(title: String, page: Activity): View? = WindowInspector.getGlobalWindowViews()
        .filter { root -> root.isShown && root !== page.window.decorView }
        .firstOrNull { root -> views(root).any { it is TextView && it.isShown && it.text.toString() == title } }

    private fun dialogButton(title: String, label: String, page: Activity): Button? =
        sheet(title, page)?.let(::views)?.filterIsInstance<Button>()
            ?.firstOrNull { it.isShown && it.text.toString().equals(label, ignoreCase = true) }

    /** The clickable settings row titled [title] on [activity], if it is built. */
    private fun row(activity: Activity, @StringRes title: Int): View? {
        val text = activity.getString(title)
        var view: View = views(activity.window.decorView)
            .firstOrNull { it is TextView && it !is Button && it.text.toString() == text } ?: return null
        while (!view.isClickable) view = view.parent as? View ?: return null
        return view
    }

    private fun takeBackup(): Backup {
        // The catalog migrates the default target first, so reading it never stores anything.
        application.aiBackend.catalog()
        return Backup(
            defaultTarget = storedDefault(),
            preferences = PREFERENCE_FILES.associateWith { name -> preferences(name).all.toMap() },
        )
    }

    private inner class Backup(val defaultTarget: String?, private val preferences: Map<String, Map<String, *>>) {
        fun restore() {
            application.defaultTargetStore.write(defaultTarget)
            preferences.forEach { (name, values) ->
                preferences(name).edit().clear().apply {
                    values.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }.commit()
            }
        }
    }

    private fun preferences(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun storedDefault(): String? =
        application.defaultTargetStore.readOrMigrate { error("the default target was not migrated") }

    private fun profileIds(): List<String> =
        application.onlineProfileRegistry.snapshot().profiles.map { it.profile.profileId }.sorted()

    private fun requireFlow() {
        assumeTrue("Opt-in: -e realDeviceFlow true", flag("realDeviceFlow"))
        assumeTrue("WindowInspector needs API 29", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    }

    private fun flag(name: String) = arguments.getString(name) == "true"

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private fun <T> onActivity(scenario: ActivityScenario<ServiceDetailActivity>, block: (ServiceDetailActivity) -> T): T =
        onScenario(scenario, block)

    private fun <T> onModels(scenario: ActivityScenario<ModelManagerActivity>, block: (ModelManagerActivity) -> T): T =
        onScenario(scenario, block)

    private fun <T> onChat(scenario: ActivityScenario<ChatActivity>, block: (ChatActivity) -> T): T =
        onScenario(scenario, block)

    private fun <A : Activity, T> onScenario(scenario: ActivityScenario<A>, block: (A) -> T): T {
        var result: Result<T>? = null
        scenario.onActivity { activity -> result = runCatching { block(activity) } }
        return checkNotNull(result).getOrThrow()
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private companion object {
        const val TAG = "P57DeviceFlow"
        const val SERVICE_NAME = "P5.7 test service"
        /** `.invalid` never resolves, so the test service cannot reach anything. */
        const val BASE_URL = "https://p57-flow.invalid/v1"
        /** Placeholder typed into the API key field; it is deleted with the test service. */
        const val PLACEHOLDER_KEY = "placeholder-not-a-key-0000"
        const val MODEL_A = "p57-model-a"
        const val MODEL_B = "p57-model-b"
        const val REQUEST_MODEL = "gpt-6.1-sol"
        const val DOWNLOAD_MODEL = "qwen2-5-1-5b-instruct"
        const val PROMPT = "Reply with the single word OK."
        const val PAGE_TIMEOUT_MILLIS = 30_000L
        const val REPLY_TIMEOUT_MILLIS = 180_000L
        const val LOG_INTERVAL_MILLIS = 60_000L
        val PREFERENCE_FILES = listOf(
            "model_picker",
            "online-ai-base-url-history",
            "context-token-calibration",
            "conversation-navigation",
        )
    }
}
