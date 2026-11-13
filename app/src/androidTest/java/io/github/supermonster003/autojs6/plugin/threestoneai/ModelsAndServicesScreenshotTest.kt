package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Light and dark screenshots of "Models and services" (roadmap 1.6.0 P5.2) for
 * `.python/device_settings_screenshots.py`. Runs only with `-e screenshots true`. The page shows the
 * device's own services and models; only the plugin's dark mode is switched for the run, and the
 * appearance preferences are written back exactly as they were before the test returns.
 */
class ModelsAndServicesScreenshotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun lightAndDarkPages() {
        assumeTrue(
            "Run with -e screenshots true",
            InstrumentationRegistry.getArguments().getString("screenshots") == "true",
        )
        val preferences = context.getSharedPreferences("application-settings", Context.MODE_PRIVATE)
        val saved = preferences.all.toMap()
        val store = ApplicationSettingsStore(context)
        val original = store.load()
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            for ((name, mode) in listOf("light" to AppDarkMode.LIGHT, "dark" to AppDarkMode.DARK)) {
                store.save(original.copy(darkMode = mode))
                ActivityScenario.launch<ModelManagerActivity>(Intent(context, ModelManagerActivity::class.java)).use { scenario ->
                    awaitRows(scenario)
                    capture(File(directory, "models-and-services-$name-top.png"))
                    scenario.onActivity { activity ->
                        val header = views(activity.window.decorView).filterIsInstance<TextView>()
                            .first { it.text.toString() == activity.getString(R.string.models_local_section) }
                        val scroll = generateSequence(header.parent as? View) { it.parent as? View }
                            .filterIsInstance<ScrollView>().firstOrNull()
                        assertNotNull("The page scrolls", scroll)
                        val bounds = Rect()
                        header.getDrawingRect(bounds)
                        scroll!!.offsetDescendantRectToMyCoords(header, bounds)
                        scroll.scrollTo(0, bounds.top)
                    }
                    capture(File(directory, "models-and-services-$name-local.png"))
                }
            }
        } finally {
            val restored = preferences.edit().clear().apply {
                saved.forEach { (key, value) ->
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
            assertTrue("Appearance preferences written back", restored)
            assertEquals(saved, preferences.all)
        }
    }

    /**
     * Waits until both sections are built, then lets the catalog and health checks settle. The labels come
     * from the Activity, whose language may follow AutoJs6 rather than the system language.
     */
    private fun awaitRows(scenario: ActivityScenario<ModelManagerActivity>) {
        val labels = listOf(R.string.models_default_target, R.string.online_ai_add_profile, R.string.models_local_section)
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        var missing = labels.map(context::getString)
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val shown = views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                missing = labels.map(activity::getString).filterNot(shown::contains)
            }
            if (missing.isEmpty()) break
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Both sections are shown, missing $missing", missing.isEmpty())
        SystemClock.sleep(SETTLE_MILLIS)
    }

    private fun capture(file: File) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(FRAME_MILLIS)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "No screenshot" }
        file.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        bitmap.recycle()
    }

    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) {
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) }
    } else {
        emptyList()
    }

    private companion object {
        const val DIRECTORY = "settings-screenshots"
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 100L
        const val SETTLE_MILLIS = 2_000L
        const val FRAME_MILLIS = 600L
    }
}
