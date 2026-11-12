package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.autojs.plugin.ai.provider.api.AiProviderSettingsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Settings home and its subpages (roadmap P5.1 / D80) plus the host action landing (D103, D110). */
class SettingsNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun homeOffersTheFiveSubpagesAndEachOneOpens() {
        assertEquals(5, AppSettingsActivity.SUBPAGES.size)
        ActivityScenario.launch<AppSettingsActivity>(Intent(context, AppSettingsActivity::class.java)).use { scenario ->
            for (page in AppSettingsActivity.SUBPAGES) {
                val monitor = instrumentation.addMonitor(page.activity.name, null, false)
                try {
                    scenario.onActivity { activity ->
                        val row = activity.window.decorView.findViewWithTag<View>(page.tag)
                        assertNotNull("Entry ${page.tag}", row)
                        assertTrue(page.tag, activity.getString(page.titleResource) in texts(row))
                        row.performClick()
                    }
                    val opened = instrumentation.waitForMonitorWithTimeout(monitor, TIMEOUT_MILLIS)
                    assertNotNull("Subpage ${page.activity.simpleName} did not open", opened)
                    instrumentation.waitForIdleSync()
                    assertTrue(
                        "${page.activity.simpleName} shows its title",
                        opened.getString(page.titleResource) in texts(opened.window.decorView),
                    )
                    instrumentation.runOnMainSync { opened.finish() }
                    awaitStage(opened, Stage.DESTROYED)
                } finally {
                    instrumentation.removeMonitor(monitor)
                }
            }
        }
    }

    @Test
    fun conversationPageCarriesTheRenamedOutputSection() {
        ActivityScenario.launch<ConversationSettingsActivity>(
            Intent(context, ConversationSettingsActivity::class.java),
        ).use { scenario ->
            scenario.onActivity { activity ->
                val shown = texts(activity.window.decorView)
                assertTrue(activity.getString(R.string.chat_settings_generation_section) in shown)
                assertTrue(activity.getString(R.string.settings_conversation_limits) in shown)
            }
        }
    }

    @Test
    fun updatesAndAboutIsTheOnlyPlaceWithTheVersionHistory() {
        ActivityScenario.launch<UpdatesAboutActivity>(Intent(context, UpdatesAboutActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.getString(R.string.release_history_title) in texts(activity.window.decorView))
            }
        }
        ActivityScenario.launch<AboutActivity>(Intent(context, AboutActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.getString(R.string.release_history_title) in texts(activity.window.decorView))
            }
        }
    }

    @Test
    fun hostActionLandsOnModelsAndServicesAboveTheSettingsHome() {
        val homeMonitor = instrumentation.addMonitor(AppSettingsActivity::class.java.name, null, false)
        val modelsMonitor = instrumentation.addMonitor(ModelManagerActivity::class.java.name, null, false)
        var home: Activity? = null
        var models: Activity? = null
        try {
            context.startActivity(
                Intent(AiProviderSettingsContract.ACTION_OPEN_SETTINGS)
                    .setClassName(context, AppSettingsActivity::class.java.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            home = instrumentation.waitForMonitorWithTimeout(homeMonitor, TIMEOUT_MILLIS)
            models = instrumentation.waitForMonitorWithTimeout(modelsMonitor, TIMEOUT_MILLIS)
            assertNotNull("Settings home was not created", home)
            assertNotNull("Models and services page was not opened", models)
            awaitStage(models!!, Stage.RESUMED)
            assertEquals("One task: home below the target page", home!!.taskId, models.taskId)
            assertFalse(home.isFinishing)

            instrumentation.runOnMainSync { (models as ComponentActivity).onBackPressedDispatcher.onBackPressed() }
            awaitStage(models, Stage.DESTROYED)
            awaitStage(home, Stage.RESUMED)
            assertTrue(
                "Back lands on the settings home",
                home.getString(R.string.app_settings_title) in texts(home.window.decorView),
            )
        } finally {
            instrumentation.removeMonitor(homeMonitor)
            instrumentation.removeMonitor(modelsMonitor)
            instrumentation.runOnMainSync {
                models?.finish()
                home?.finish()
            }
        }
    }

    /** P5.2 (D95, D97): one page, the default target row on top, then online services and local models. */
    @Test
    fun modelsAndServicesShowsTheDefaultTargetAboveBothSections() {
        ActivityScenario.launch<ModelManagerActivity>(Intent(context, ModelManagerActivity::class.java)).use { scenario ->
            val expected = listOf(
                R.string.model_manager_title,
                R.string.models_default_target,
                R.string.online_ai_allow_metered_network,
                R.string.online_ai_profiles_section,
                R.string.online_ai_add_profile,
                R.string.models_local_section,
                R.string.button_browse_litert_models,
                R.string.button_import_model,
            ).map(context::getString)
            var shown = emptyList<String>()
            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity -> shown = texts(activity.window.decorView) }
                if (shown.containsAll(expected)) break
                SystemClock.sleep(POLL_MILLIS)
            }
            expected.forEach { text -> assertEquals("\"$text\" shown once", 1, shown.count { it == text }) }
            assertEquals("Rows in page order", expected, shown.filter { it in expected })
            assertFalse(context.getString(R.string.online_ai_settings_unavailable) in shown)
        }
    }

    private fun awaitStage(activity: Activity, stage: Stage) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            var current: Stage? = null
            instrumentation.runOnMainSync {
                current = ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity)
            }
            if (current == stage) return
            SystemClock.sleep(POLL_MILLIS)
        }
        fail("${activity.javaClass.simpleName} did not reach $stage")
    }

    private fun texts(root: View): List<String> =
        views(root).filterIsInstance<TextView>().map { it.text.toString() }

    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) {
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) }
    } else {
        emptyList()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
    }
}
