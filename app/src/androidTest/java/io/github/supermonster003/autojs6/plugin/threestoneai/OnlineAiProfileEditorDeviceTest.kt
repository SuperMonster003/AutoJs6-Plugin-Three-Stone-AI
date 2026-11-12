package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.os.Build
import android.os.Parcel
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.FORM_SHEET_CLOSE_TAG
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.SheetHandle
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formBottomSheet
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Roadmap D105 / P8.0: the online profile editor ignores swipes and outside taps, keeps its draft
 * (typed API key included) across recreation, and asks before discarding changes. Nothing is
 * saved and no request is sent; the API key is a placeholder that never leaves the form.
 */
class OnlineAiProfileEditorDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun requireWindowInspector() {
        assumeTrue("WindowInspector needs API 29", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    }

    @Test
    fun theFormIgnoresSwipesKeepsItsDraftAndAsksBeforeDiscarding() {
        val profilesBefore = profileIds()
        launchEditor().use { scenario ->
            val sheet = waitForSheet(scenario)
            assertEquals(listOf(false, false), onMain { listOf(sheet.behavior.isDraggable, sheet.behavior.isHideable) })
            onMain {
                field(sheet, R.string.online_ai_profile_name).setText(NAME)
                field(sheet, R.string.online_ai_base_url).setText(BASE_URL)
                field(sheet, R.string.online_ai_credential).setText(PLACEHOLDER_KEY)
            }

            // Control: the same swipe dismisses an ordinary sheet, so the gesture does reach sheets.
            val control = onActivity(scenario) { activity ->
                activity.formBottomSheet(title = "control", content = TextView(activity))
            }
            waitUntil("the control sheet opens") { onMain { control.behavior.state == BottomSheetBehavior.STATE_EXPANDED } }
            swipeDown(control, fromTitle = true)
            waitUntil("a swipe dismisses the control sheet") { onMain { !control.dialog.isShowing } }

            swipeDown(sheet, fromTitle = true)
            swipeDown(sheet, fromTitle = false)
            onMain { sheet.dialog.findViewById<View>(com.google.android.material.R.id.touch_outside)?.performClick() }
            SystemClock.sleep(SETTLE_MILLIS)
            assertTrue("the editor stays open after swipes and an outside tap", onMain { sheet.dialog.isShowing })
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, onMain { sheet.behavior.state })

            scenario.recreate()
            val restored = waitForSheet(scenario)
            assertNotSame(sheet, restored)
            assertFalse("the old sheet closed with its activity", onMain { sheet.dialog.isShowing })
            assertEquals(NAME, onMain { field(restored, R.string.online_ai_profile_name).text.toString() })
            assertEquals(BASE_URL, onMain { field(restored, R.string.online_ai_base_url).text.toString() })
            assertTrue(
                "the typed API key is restored",
                onMain { field(restored, R.string.online_ai_credential).text.toString() == PLACEHOLDER_KEY },
            )
            assertEquals(listOf(false, false), onMain { listOf(restored.behavior.isDraggable, restored.behavior.isHideable) })

            hideKeyboard(restored)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            val title = onActivity(scenario) { it.getString(R.string.form_discard_changes_title) }
            val discard = onActivity(scenario) { it.getString(R.string.form_discard_changes_confirm) }
            waitUntil("Back asks before discarding") { onMain { discardDialogButton(title, discard) != null } }
            assertTrue("the editor waits for the answer", onMain { restored.dialog.isShowing })
            onMain { checkNotNull(discardDialogButton(title, discard)).performClick() }
            waitUntil("discarding closes the editor") { onMain { !restored.dialog.isShowing } }
            assertNull(onActivity(scenario) { it.profileEditorSheetForTest })
        }
        assertEquals("nothing was saved", profilesBefore, profileIds())
    }

    @Test
    fun theCloseButtonAsksOnlyWhenSomethingChanged() {
        val profilesBefore = profileIds()
        launchEditor().use { scenario ->
            val title = onActivity(scenario) { it.getString(R.string.form_discard_changes_title) }
            val discard = onActivity(scenario) { it.getString(R.string.form_discard_changes_confirm) }
            val sheet = waitForSheet(scenario)
            onMain { closeButton(sheet).performClick() }
            waitUntil("an untouched form closes at once") { onMain { !sheet.dialog.isShowing } }
            assertNull(onMain { discardDialogButton(title, discard) })
        }
        launchEditor().use { scenario ->
            val title = onActivity(scenario) { it.getString(R.string.form_discard_changes_title) }
            val discard = onActivity(scenario) { it.getString(R.string.form_discard_changes_confirm) }
            val cancel = onActivity(scenario) { it.getString(android.R.string.cancel) }
            val sheet = waitForSheet(scenario)
            onMain { field(sheet, R.string.online_ai_profile_name).setText(NAME) }
            onMain { closeButton(sheet).performClick() }
            waitUntil("a changed form asks first") { onMain { discardDialogButton(title, discard) != null } }
            onMain { checkNotNull(discardDialogButton(title, cancel)).performClick() }
            waitUntil("keeping the changes closes only the question") {
                onMain { discardDialogButton(title, discard) == null }
            }
            assertTrue(onMain { sheet.dialog.isShowing })
            assertEquals(NAME, onMain { field(sheet, R.string.online_ai_profile_name).text.toString() })

            onMain { closeButton(sheet).performClick() }
            waitUntil("the question comes back") { onMain { discardDialogButton(title, discard) != null } }
            onMain { checkNotNull(discardDialogButton(title, discard)).performClick() }
            waitUntil("discarding closes the editor") { onMain { !sheet.dialog.isShowing } }
        }
        assertEquals("nothing was saved", profilesBefore, profileIds())
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

    private val SheetHandle.behavior get() = dialog.behavior

    private fun launchEditor(): ActivityScenario<ModelManagerActivity> = ActivityScenario.launch(
        Intent(context, ModelManagerActivity::class.java)
            .putExtra(OnlineServicesActivity.EXTRA_ADD_PROFILE, true),
    )

    private fun waitForSheet(scenario: ActivityScenario<ModelManagerActivity>): SheetHandle {
        waitUntil("the profile editor opens") {
            onActivity(scenario) { it.profileEditorSheetForTest?.dialog?.isShowing == true }
        }
        val sheet = checkNotNull(onActivity(scenario) { it.profileEditorSheetForTest })
        waitUntil("the profile editor settles") { onMain { sheet.behavior.state == BottomSheetBehavior.STATE_EXPANDED } }
        return sheet
    }

    private fun field(sheet: SheetHandle, @StringRes hint: Int): EditText {
        val text = sheet.dialog.context.getString(hint)
        val layout = views(checkNotNull(sheet.dialog.window).decorView).filterIsInstance<TextInputLayout>()
            .first { it.hint?.toString() == text }
        return checkNotNull(layout.editText)
    }

    private fun closeButton(sheet: SheetHandle): View =
        checkNotNull(sheet.dialog.window).decorView.findViewWithTag(FORM_SHEET_CLOSE_TAG)

    /** The button labelled [label] in the dialog titled [title] ("Discard changes?"), if it is open. */
    private fun discardDialogButton(title: String, label: String): Button? =
        WindowInspector.getGlobalWindowViews()
            .filter { root -> root.isShown && views(root).any { it is TextView && it.text.toString() == title } }
            .flatMap(::views)
            .filterIsInstance<Button>()
            .firstOrNull { it.isShown && it.text.toString().equals(label, ignoreCase = true) }

    private fun hideKeyboard(sheet: SheetHandle) = onMain {
        val decor = checkNotNull(sheet.dialog.window).decorView
        decor.context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(decor.windowToken, 0)
    }

    /** A quick downward drag from the sheet's title, or from the middle of its content. */
    private fun swipeDown(sheet: SheetHandle, fromTitle: Boolean) {
        val (x, startY) = onMain {
            val bottomSheet = checkNotNull(
                sheet.dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet),
            )
            val location = IntArray(2).also(bottomSheet::getLocationOnScreen)
            val x = location[0] + bottomSheet.width / 2f
            if (!fromTitle) return@onMain x to location[1] + bottomSheet.height / 2f
            // Start on the title row, and never in the status bar: a drag starting there belongs to
            // the system shade, which a targeted injection may not reach.
            // The title row holds the close button when the sheet is guarded, only the title otherwise.
            val anchor = bottomSheet.findViewWithTag<View>(FORM_SHEET_CLOSE_TAG)
                ?: views(bottomSheet).first { it is TextView }
            val anchorLocation = IntArray(2).also(anchor::getLocationOnScreen)
            val statusBar = ViewCompat.getRootWindowInsets(bottomSheet)
                ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            x to maxOf(anchorLocation[1] + anchor.height / 2f, statusBar + dp(16f))
        }
        val endY = context.resources.displayMetrics.heightPixels - dp(8f)
        val downTime = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, startY, 0))
        for (step in 1..SWIPE_STEPS) {
            SystemClock.sleep(8L)
            val y = startY + (endY - startY) * step / SWIPE_STEPS
            instrumentation.sendPointerSync(
                MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y, 0),
            )
        }
        instrumentation.sendPointerSync(
            MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, endY, 0),
        )
        instrumentation.waitForIdleSync()
    }

    private fun dp(value: Float) = value * context.resources.displayMetrics.density

    private fun profileIds(): List<String> =
        (context.applicationContext as ThreeStoneAiApplication).onlineProfileRegistry.snapshot()
            .profiles.map { it.profile.profileId }.sorted()

    private fun <T> onActivity(
        scenario: ActivityScenario<ModelManagerActivity>,
        block: (ModelManagerActivity) -> T,
    ): T {
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
        const val NAME = "Draft profile P8.0"
        const val BASE_URL = "https://draft.example.test/v1"
        /** Placeholder typed into the API key field; it is never saved or sent. */
        const val PLACEHOLDER_KEY = "placeholder-not-a-key-0000"
        const val SWIPE_STEPS = 10
        const val SETTLE_MILLIS = 800L
    }
}
