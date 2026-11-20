package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plugin-center P21.9: the release history page follows the model page of the official plugins. Its title is
 * "Release history" with the plugin name as the subtitle, a release expands and collapses (one at a time or all of
 * them), the "Release history" row of "Updates and about" and the update dialog open it, and a keyboard reaches every
 * release.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseHistoryPageInstrumentationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @After fun finishPages() {
        instrumentation.setInTouchMode(true)
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ReleaseHistoryActivity>()
                .forEach(Activity::finish)
        }
        instrumentation.waitForIdleSync()
    }

    /** Unmerged: the count chips sit inside the clickable header, which merges its children. */
    private fun exists(tag: String): Boolean =
        runCatching { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    private fun versions(scenario: ActivityScenario<ReleaseHistoryActivity>): List<String> {
        var versions = emptyList<String>()
        scenario.onActivity { activity ->
            val candidates = ReleaseHistoryAssetPolicy.candidatesFor(activity.resources.configuration.locales[0])
            versions = loadReleaseHistory(candidates) { path ->
                activity.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
            }.map { it.version }
        }
        return versions
    }

    private fun assertTitleAndSubtitle() {
        val inTopBar = hasAnyAncestor(hasTestTag("stone.topBar"))
        compose.onNode(hasText(context.getString(R.string.release_history_title)) and inTopBar).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.app_name)) and inTopBar).assertIsDisplayed()
    }

    @Test fun theTitleIsReleaseHistoryAndTheSubtitleThePluginName() {
        ActivityScenario.launch(ReleaseHistoryActivity::class.java).use {
            compose.waitUntil(TIMEOUT) { exists("stone.releaseHistory.list") }
            assertTitleAndSubtitle()
            assertFalse(exists("stone.emptyState"))
        }
    }

    /**
     * The newest release starts expanded, the others show their category counts; a header expands or collapses its
     * release, the top bar expands or collapses all of them, and the choice survives recreation.
     */
    @Test fun releasesExpandAndCollapse() {
        ActivityScenario.launch(ReleaseHistoryActivity::class.java).use { scenario ->
            compose.waitUntil(TIMEOUT) { exists("stone.releaseHistory.list") }
            val versions = versions(scenario)
            assertTrue("releases: ${versions.size}", versions.size > 2)
            val (newest, second) = versions
            assertTrue(exists("stone.releaseHistory.body.$newest"))
            assertFalse(exists("stone.releaseHistory.counts.$newest"))
            assertFalse(exists("stone.releaseHistory.body.$second"))
            assertTrue(exists("stone.releaseHistory.counts.$second"))

            compose.onNodeWithTag("stone.releaseHistory.entry.$second").performClick()
            compose.waitUntil(TIMEOUT) { exists("stone.releaseHistory.body.$second") }
            compose.onNodeWithTag("stone.releaseHistory.entry.$newest").performClick()
            compose.waitUntil(TIMEOUT) { !exists("stone.releaseHistory.body.$newest") && exists("stone.releaseHistory.counts.$newest") }

            scenario.recreate()
            compose.waitUntil(TIMEOUT) { exists("stone.releaseHistory.list") }
            assertTrue(exists("stone.releaseHistory.body.$second"))
            assertFalse(exists("stone.releaseHistory.body.$newest"))

            compose.onNodeWithTag("stone.releaseHistory.toggleAll")
                .assertContentDescriptionEquals(context.getString(R.string.release_history_expand_all))
                .performClick()
            compose.waitUntil(TIMEOUT) { exists("stone.releaseHistory.body.$newest") }
            compose.onNodeWithTag("stone.releaseHistory.toggleAll")
                .assertContentDescriptionEquals(context.getString(R.string.release_history_collapse_all))
                .performClick()
            compose.waitUntil(TIMEOUT) { !exists("stone.releaseHistory.body.$newest") && !exists("stone.releaseHistory.body.$second") }
        }
    }

    @Test fun theUpdatesAndAboutRowOpensThePage() {
        val monitor = instrumentation.addMonitor(ReleaseHistoryActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch<UpdatesAboutActivity>(Intent(context, UpdatesAboutActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    val title = activity.getString(R.string.release_history_title)
                    val label = findText(activity.window.decorView, title)
                    assertNotNull("row $title", label)
                    val row = generateSequence(label as View) { it.parent as? View }.first { it.isClickable }
                    row.performClick()
                }
                val opened = instrumentation.waitForMonitorWithTimeout(monitor, TIMEOUT)
                assertNotNull("the release history page did not open", opened)
                compose.waitUntil(TIMEOUT) { exists("stone.releaseHistory.list") }
                assertTitleAndSubtitle()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** The neutral button of the update dialog opens the page too. */
    @Test fun theUpdateDialogOpensThePage() {
        val monitor = instrumentation.addMonitor(ReleaseHistoryActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch<UpdatesAboutActivity>(Intent(context, UpdatesAboutActivity::class.java)).use {
                it.onActivity { activity ->
                    // The dialog normally follows a check against GitHub; the test hands it a newer release directly.
                    AppUpdateController::class.java
                        .getDeclaredMethod("handleRelease", AppRelease::class.java, Boolean::class.javaPrimitiveType)
                        .apply { isAccessible = true }
                        .invoke(AppUpdateController(activity), AppRelease("v99.0.0", "v99.0.0", "", "https://example.com"), true)
                }
                onView(withText(context.getString(R.string.release_history_title))).inRoot(isDialog()).perform(click())
                val opened = instrumentation.waitForMonitorWithTimeout(monitor, TIMEOUT)
                assertNotNull("the release history page did not open", opened)
                compose.waitUntil(TIMEOUT) { exists("stone.releaseHistory.list") }
                assertTitleAndSubtitle()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun findText(view: View, text: String): TextView? = when {
        view is TextView && view.text.toString() == text -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findText(view.getChildAt(it), text) }
        else -> null
    }

    private fun key(code: Int) {
        instrumentation.sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    /**
     * Leaves touch mode as a keyboard user does. Newer versions take [android.app.Instrumentation.setInTouchMode] at
     * once; older ones (API 24) learn it only from a key, so an up arrow goes first.
     */
    private fun leaveTouchMode() {
        instrumentation.setInTouchMode(false)
        key(KeyEvent.KEYCODE_DPAD_UP)
    }

    /** The test tag of the focused control (or of its nearest tagged ancestor), null when nothing has focus. */
    private fun focusedTag(): String? {
        val node = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        return generateSequence(node) { it.parent }.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) } ?: UNTAGGED
    }

    /** Presses Tab until focus returns to a control seen before; every focused control must be on screen. */
    private fun tabCycle(): List<String> {
        val visited = mutableListOf<String>()
        repeat(MAX_TABS) {
            key(KeyEvent.KEYCODE_TAB)
            val tag = focusedTag() ?: return@repeat
            if (tag in visited) return visited
            compose.onNode(isFocused(), useUnmergedTree = true).assertIsDisplayed()
            visited += tag
        }
        return visited
    }

    /** Tab walks the back button, the expand all button and every release, newest first, each scrolled into view. */
    @Test fun theReleaseHistoryCanBeReadToTheEndFromTheKeyboard() {
        ActivityScenario.launch(ReleaseHistoryActivity::class.java).use { scenario ->
            compose.waitUntil(TIMEOUT) { exists("stone.releaseHistory.list") }
            leaveTouchMode()
            val versions = versions(scenario)
            assertTrue("releases: ${versions.size}", versions.size > 10)
            val visited = tabCycle()
            assertTrue(visited.toString(), "stone.topBar.navigation" in visited)
            assertTrue(visited.toString(), "stone.releaseHistory.toggleAll" in visited)
            assertEquals(versions.map { "stone.releaseHistory.entry.$it" }, visited.filter { it.startsWith("stone.releaseHistory.entry.") })
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
        const val MAX_TABS = 80
        const val UNTAGGED = "(untagged)"
    }
}
