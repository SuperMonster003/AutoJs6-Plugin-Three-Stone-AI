package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Parcel
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCredentialUpdate
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The service details page (roadmap 1.7.0 P5.4, D96, D122): a new service is stored only on Save,
 * the draft (typed API key included) survives recreation, leaving asks before discarding, and the
 * model list edits (drag, preferred, image input, add) are saved together. Every service this test
 * creates is deleted again and the default target, favorites and base URL history are put back.
 * No request is sent; the API key is a placeholder that never leaves the form.
 */
class ServiceDetailDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val application get() = context.applicationContext as ThreeStoneAiApplication

    /** Texts as the page shows them: it follows the AutoJs6 host language, not the system locale. */
    private val strings by lazy { AppConfiguration.wrap(context) }

    /** Saving a service records its base URL in the address history; each test puts the user's history back. */
    private var baseUrlHistory: Map<String, *> = emptyMap<String, Any>()

    @Before
    fun keepBaseUrlHistory() {
        baseUrlHistory = baseUrlHistoryPreferences().all.toMap()
    }

    @After
    fun restoreBaseUrlHistory() {
        // Written synchronously: the process may end right after the last test.
        baseUrlHistoryPreferences().edit().clear().apply {
            baseUrlHistory.forEach { (key, value) -> if (value is String) putString(key, value) }
        }.commit()
    }

    @Test
    fun aNewServiceKeepsItsDraftAcrossRecreationAndIsStoredOnlyOnSave() {
        requireWindowInspector()
        val profilesBefore = profileIds()
        launch(null).use { scenario ->
            onMain {
                activityField(scenario, R.string.online_ai_profile_name).setText(NAME)
                activityField(scenario, R.string.online_ai_base_url).setText(BASE_URL)
                activityField(scenario, R.string.online_ai_credential).setText(PLACEHOLDER_KEY)
            }
            assertTrue(onActivity(scenario) { it.hasUnsavedChanges() })

            scenario.recreate()
            assertEquals(NAME, onMain { activityField(scenario, R.string.online_ai_profile_name).text.toString() })
            assertEquals(BASE_URL, onMain { activityField(scenario, R.string.online_ai_base_url).text.toString() })
            assertTrue(
                "the typed API key is restored",
                onMain { activityField(scenario, R.string.online_ai_credential).text.toString() == PLACEHOLDER_KEY },
            )

            hideKeyboard(scenario)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val title = strings.getString(R.string.form_discard_changes_title)
            val discard = strings.getString(R.string.form_discard_changes_confirm)
            val cancel = strings.getString(android.R.string.cancel)
            waitUntil("Back asks before discarding") { onMain { dialogButton(title, discard) != null } }
            onMain { checkNotNull(dialogButton(title, cancel)).performClick() }
            waitUntil("keeping the changes closes only the question") { onMain { dialogButton(title, discard) == null } }
            assertEquals(NAME, onMain { activityField(scenario, R.string.online_ai_profile_name).text.toString() })

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitUntil("the question comes back") { onMain { dialogButton(title, discard) != null } }
            onMain { checkNotNull(dialogButton(title, discard)).performClick() }
            waitUntil("discarding closes the page") { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        }
        assertEquals("nothing was saved", profilesBefore, profileIds())
    }

    @Test
    fun savingANewServiceStoresItAndThePageThenClosesWithoutAsking() {
        requireWindowInspector()
        val profilesBefore = profileIds()
        var created: String? = null
        try {
            launch(null).use { scenario ->
                onMain { activityField(scenario, R.string.online_ai_base_url).setText(BASE_URL) }
                onMain { activityField(scenario, R.string.online_ai_profile_name).setText(NAME) }
                assertTrue("saving a new service succeeds", onActivity(scenario) { it.save() })
                created = (profileIds() - profilesBefore.toSet()).single()
                assertFalse(onActivity(scenario) { it.hasUnsavedChanges() })
                assertEquals(
                    strings.getString(R.string.service_detail_test_needs_key),
                    onMain { rowSummary(scenario, R.string.online_ai_action_test) },
                )
                hideKeyboard(scenario)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                waitUntil("a saved page closes without asking") {
                    scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED
                }
            }
            val stored = profile(checkNotNull(created))
            assertEquals(NAME, stored.displayName)
            assertEquals(BASE_URL, stored.baseUrl)
        } finally {
            created?.let { application.onlineProfileRegistry.delete(it) }
        }
        assertEquals(profilesBefore, profileIds())
    }

    @Test
    fun modelEditsAreSavedTogetherAndTheDefaultTargetFollowsTheSameModel() {
        requireWindowInspector()
        application.aiBackend.catalog()
        val targets = application.defaultTargetStore
        val originalDefault = targets.readOrMigrate { error("migrated by the catalog") }
        val favorites = ModelPickerFavoritesStore(context)
        val originalFavorites = favorites.load()
        val profile = OnlineAiProfile.create(
            displayName = NAME,
            provider = OnlineAiProvider.OPENAI_COMPATIBLE,
            baseUrl = BASE_URL,
            modelId = "model-a",
            modelIds = listOf("model-a", "model-b", "model-c"),
            visionModelIds = emptyList(),
            defaultEffort = GenerationEffort.DEFAULT,
            fastMode = false,
        )
        application.onlineProfileRegistry.save(profile, OnlineAiCredentialUpdate.Clear)
        val id = profile.profileId
        try {
            targets.write(AiTargetIds.profile(id))
            favorites.toggle(AiTargetIds.profile(id))
            launch(id).use { scenario ->
                assertEquals(listOf("model-a", "model-b", "model-c"), onActivity(scenario) { it.shownModelOrder })
                assertEquals(
                    strings.getString(R.string.service_detail_test_needs_key),
                    onMain { rowSummary(scenario, R.string.online_ai_action_test) },
                )

                dragHandle(scenario, from = 2, to = 0)
                waitUntil("the dragged row lands on top") {
                    onActivity(scenario) { it.draftModels.modelIds == listOf("model-c", "model-a", "model-b") }
                }
                assertEquals(listOf("model-c", "model-a", "model-b"), onActivity(scenario) { it.shownModelOrder })
                assertEquals(
                    strings.getString(R.string.service_detail_test_save_first),
                    onMain { rowSummary(scenario, R.string.online_ai_action_test) },
                )
                onActivity(scenario) {
                    it.applyModelAction("model-b", ServiceDetailActivity.ACTION_PREFERRED)
                    it.applyModelAction("model-a", ServiceDetailActivity.ACTION_VISION)
                }
                addTypedModels(scenario, " model-d \nmodel-a")
                waitUntil("the typed model is appended") {
                    onActivity(scenario) { it.shownModelOrder.last() == "model-d" }
                }

                assertTrue(onActivity(scenario) { it.save() })
                assertFalse(onActivity(scenario) { it.hasUnsavedChanges() })
            }
            val stored = profile(id)
            assertEquals(listOf("model-c", "model-a", "model-b", "model-d"), stored.modelIds)
            assertEquals("model-b", stored.modelId)
            assertEquals(listOf("model-a"), stored.visionModelIds)
            val sameModel = AiTargetIds.profileModel(id, "model-c", "model-a")
            assertEquals("D122: the default target still names model-a", sameModel, targets.readOrMigrate { null })
            assertTrue("D122: the favorite still names model-a", sameModel in favorites.load())
        } finally {
            application.onlineProfileRegistry.delete(id)
            targets.write(originalDefault)
            restoreFavorites(favorites, originalFavorites)
        }
    }

    @Test
    fun theDraftSurvivesParcelingAsAfterProcessDeath() {
        val values = OnlineAiProfileEditorValues(
            provider = OnlineAiProvider.entries.last(),
            name = NAME,
            baseUrl = BASE_URL,
            modelIds = listOf("model-a", "model-b"),
            defaultModelId = "model-b",
            visionModelIds = listOf("model-a"),
            effort = GenerationEffort.entries.last(),
            fastMode = true,
        )
        val draft = OnlineAiProfileEditorDraft(
            profileId = "profile-1",
            initial = values.copy(name = "Before"),
            current = values,
            lastSuggestedName = "Suggested",
            credential = PLACEHOLDER_KEY.toCharArray(),
        )
        val parcel = Parcel.obtain()
        val restored = try {
            parcel.writeBundle(draft.toBundle())
            parcel.setDataPosition(0)
            OnlineAiProfileEditorDraft.fromBundle(checkNotNull(parcel.readBundle(javaClass.classLoader)))
        } finally {
            parcel.recycle()
        }
        assertNotNull(restored)
        assertEquals("profile-1", restored!!.profileId)
        assertEquals(draft.initial, restored.initial)
        assertEquals(values, restored.current)
        assertEquals("Suggested", restored.lastSuggestedName)
        assertArrayEquals(PLACEHOLDER_KEY.toCharArray(), restored.credential)
        assertNull(OnlineAiProfileEditorDraft.fromBundle(android.os.Bundle()))
    }

    private fun requireWindowInspector() =
        assumeTrue("WindowInspector needs API 29", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

    private fun launch(profileId: String?): ActivityScenario<ServiceDetailActivity> =
        ActivityScenario.launch<ServiceDetailActivity>(ServiceDetailActivity.intent(context, profileId)).also {
            instrumentation.waitForIdleSync()
        }

    private fun activityField(scenario: ActivityScenario<ServiceDetailActivity>, @StringRes hint: Int): EditText {
        val text = strings.getString(hint)
        var root: View? = null
        scenario.onActivity { root = it.window.decorView }
        val layout = views(checkNotNull(root)).filterIsInstance<TextInputLayout>().first { it.hint?.toString() == text }
        return checkNotNull(layout.editText)
    }

    /** The summary under the settings row titled [title]. */
    private fun rowSummary(scenario: ActivityScenario<ServiceDetailActivity>, @StringRes title: Int): String {
        val text = strings.getString(title)
        var root: View? = null
        scenario.onActivity { root = it.window.decorView }
        val titleView = views(checkNotNull(root)).filterIsInstance<TextView>().first { it.text.toString() == text }
        val column = titleView.parent as android.view.ViewGroup
        return (column.getChildAt(column.indexOfChild(titleView) + 1) as TextView).text.toString()
    }

    /** Drags the handle of row [from] until the row sits at [to], with injected touches. */
    private fun dragHandle(scenario: ActivityScenario<ServiceDetailActivity>, from: Int, to: Int) {
        val description = strings.getString(R.string.service_detail_drag_handle)
        hideKeyboard(scenario)
        fun handles(): List<ImageView> {
            var root: View? = null
            scenario.onActivity { root = it.window.decorView }
            return views(checkNotNull(root)).filterIsInstance<ImageView>()
                .filter { it.contentDescription?.toString() == description }
        }
        // Bring every model row on screen; touches outside the page are refused by the system.
        onMain {
            val rows = handles().map { it.parent as View }
            val first = rows.first()
            val height = rows.last().bottom - first.top
            first.requestRectangleOnScreen(Rect(0, 0, first.width, height), true)
        }
        instrumentation.waitForIdleSync()
        val (x, startY, rowHeight) = onMain {
            val handle = handles()[from]
            val location = IntArray(2).also(handle::getLocationOnScreen)
            Triple(location[0] + handle.width / 2f, location[1] + handle.height / 2f, (handle.parent as View).height)
        }
        val endY = startY + (to - from) * rowHeight
        val downTime = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, startY, 0))
        for (step in 1..DRAG_STEPS) {
            SystemClock.sleep(16L)
            val y = startY + (endY - startY) * step / DRAG_STEPS
            instrumentation.sendPointerSync(
                MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y, 0),
            )
        }
        instrumentation.sendPointerSync(
            MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, endY, 0),
        )
        instrumentation.waitForIdleSync()
    }

    /** Opens "Add models", types [lines] into the custom field and confirms. */
    private fun addTypedModels(scenario: ActivityScenario<ServiceDetailActivity>, lines: String) {
        val addTitle = strings.getString(R.string.service_detail_add_model)
        onMain {
            var root: View? = null
            scenario.onActivity { root = it.window.decorView }
            val title = views(checkNotNull(root)).filterIsInstance<TextView>().first { it.text.toString() == addTitle }
            var row: View = title
            while (!row.isClickable) row = row.parent as View
            row.performClick()
        }
        val hint = strings.getString(R.string.online_ai_custom_model_ids)
        waitUntil("the add models sheet opens") { onMain { sheetField(hint) != null } }
        onMain { checkNotNull(sheetField(hint)).setText(lines) }
        val confirm = strings.getString(R.string.service_detail_add_confirm)
        onMain {
            WindowInspector.getGlobalWindowViews().flatMap(::views).filterIsInstance<Button>()
                .first { it.isShown && it.text.toString().equals(confirm, ignoreCase = true) }
                .performClick()
        }
        waitUntil("the add models sheet closes") { onMain { sheetField(hint) == null } }
    }

    private fun sheetField(hint: String): EditText? = WindowInspector.getGlobalWindowViews()
        .filter { it.isShown }
        .flatMap(::views)
        .filterIsInstance<TextInputLayout>()
        .firstOrNull { it.isShown && it.hint?.toString() == hint }
        ?.editText

    /** The button labelled [label] in the dialog titled [title], if it is open. */
    private fun dialogButton(title: String, label: String): Button? =
        WindowInspector.getGlobalWindowViews()
            .filter { root -> root.isShown && views(root).any { it is TextView && it.text.toString() == title } }
            .flatMap(::views)
            .filterIsInstance<Button>()
            .firstOrNull { it.isShown && it.text.toString().equals(label, ignoreCase = true) }

    /** Clears the focus and waits until no keyboard covers the page, so keys and touches reach it. */
    private fun hideKeyboard(scenario: ActivityScenario<ServiceDetailActivity>) {
        onMain {
            scenario.onActivity { activity ->
                activity.currentFocus?.clearFocus()
                activity.getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) waitUntil("the keyboard is hidden") {
            onMain {
                var visible = true
                scenario.onActivity { activity ->
                    visible = activity.window.decorView.rootWindowInsets
                        ?.isVisible(WindowInsets.Type.ime()) == true
                }
                !visible
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun restoreFavorites(store: ModelPickerFavoritesStore, original: Set<String>) {
        val current = store.load()
        (current - original).forEach(store::toggle)
        (original - store.load()).forEach(store::toggle)
        // toggle() applies asynchronously, and the process ends right after the last test: write the
        // restored set synchronously so it is not lost (a real device kept a deleted service's favorite).
        context.getSharedPreferences("model_picker", Context.MODE_PRIVATE).edit()
            .putStringSet("favorite_target_ids", store.load())
            .commit()
    }

    private fun baseUrlHistoryPreferences() =
        context.getSharedPreferences("online-ai-base-url-history", Context.MODE_PRIVATE)

    private fun profileIds(): List<String> =
        application.onlineProfileRegistry.snapshot().profiles.map { it.profile.profileId }.sorted()

    private fun profile(id: String): OnlineAiProfile =
        application.onlineProfileRegistry.snapshot().profiles.first { it.profile.profileId == id }.profile

    private fun <T> onActivity(scenario: ActivityScenario<ServiceDetailActivity>, block: (ServiceDetailActivity) -> T): T {
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
        const val NAME = "Service details P5.4"
        const val BASE_URL = "https://detail.example.test/v1"
        /** Placeholder typed into the API key field; it is never saved or sent. */
        const val PLACEHOLDER_KEY = "placeholder-not-a-key-0000"
        const val DRAG_STEPS = 24
    }
}
