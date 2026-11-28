package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * F5 / D224: on API 31+ the resolved night mode is handed to the system, which draws the next
 * starting window and starts the next process with it. Each case opens a screen, then checks the
 * mode this process recorded and the night mode that screen is drawn with. The running process's
 * application configuration is not checked: on XQ-AT72 (API 31) it keeps the system's night mode
 * until the next process start, which `device_splash_night_mode.py` covers frame by frame. Run the
 * class once with the system dark and once with it light: with the system light, following the
 * system after a recorded dark mode must draw the screen light. The appearance settings are
 * restored afterwards and the original mode recorded again.
 */
class ApplicationNightModeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Before
    fun requireApi31() {
        assumeTrue("UiModeManager.setApplicationNightMode needs API 31", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }

    @Test
    fun explicitDarkAndLightAreRecordedAndFollowTheSetting() = withRestoredAppearance {
        save(AppDarkMode.DARK)
        awaitRecorded(UiModeManager.MODE_NIGHT_YES, expectDark = true, displayed = openSettings())

        save(AppDarkMode.LIGHT)
        awaitRecorded(UiModeManager.MODE_NIGHT_NO, expectDark = false, displayed = openSettings())
    }

    @Test
    fun followingTheSystemRecordsAutomatic() = withRestoredAppearance {
        save(AppDarkMode.DARK)
        awaitRecorded(UiModeManager.MODE_NIGHT_YES, expectDark = true, displayed = openSettings())

        save(AppDarkMode.FOLLOW_SYSTEM)
        val displayed = openSettings()
        val global = context.getSystemService(UiModeManager::class.java).nightMode
        // Automatic and custom schedules depend on the time of day; only fixed system modes are compared.
        val expectDark = when (global) {
            UiModeManager.MODE_NIGHT_YES -> true
            UiModeManager.MODE_NIGHT_NO -> false
            else -> null
        }
        awaitRecorded(UiModeManager.MODE_NIGHT_AUTO, expectDark, displayed)
    }

    @Test
    fun followingAutoJs6RecordsTheHostAppearance() = withRestoredAppearance {
        val host = refreshedHost()
        assumeTrue("AutoJs6 must be installed and expose its settings", host.snapshot != null)
        save(AppDarkMode.FOLLOW_AUTOJS6)
        val displayed = openSettings()
        val dark = checkNotNull(host.snapshot).darkModeActive
        awaitRecorded(if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO, expectDark = dark, displayed = displayed)
    }

    /**
     * `device_splash_night_mode.py` only (`-e coldStart DARK|LIGHT|restore`): sets the appearance and records it
     * for the cold starts the script films. The user's settings wait in a file of this app until `restore` puts them
     * back and records their resolution again.
     */
    @Test
    fun holdAppearanceForColdStart() {
        val request = InstrumentationRegistry.getArguments().getString("coldStart")
        assumeTrue("only for device_splash_night_mode.py", request != null)
        val backup = File(context.noBackupFilesDir, COLD_START_BACKUP)
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (request == "restore") {
            if (backup.isFile) {
                val saved = JSONObject(backup.readText())
                val values = saved.keys().asSequence().associateWith { key -> decode(saved.getJSONObject(key)) }
                replaceAll(values)
                assertTrue(backup.delete())
            }
            refreshedHost()
            openSettings()
            return
        }
        if (!backup.isFile) {
            val saved = JSONObject()
            preferences.all.forEach { (key, value) -> encode(value)?.let { saved.put(key, it) } }
            backup.writeText(saved.toString())
        }
        val dark = when (request) {
            "DARK" -> true
            "LIGHT" -> false
            else -> error("coldStart is DARK, LIGHT or restore")
        }
        save(if (dark) AppDarkMode.DARK else AppDarkMode.LIGHT)
        awaitRecorded(
            if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO,
            expectDark = dark,
            displayed = openSettings(),
        )
    }

    private fun save(darkMode: AppDarkMode) {
        val store = ApplicationSettingsStore(context)
        store.save(store.load().copy(darkMode = darkMode))
    }

    /** Opens the settings screen and returns the night mode bits it is drawn with. */
    private fun openSettings(): Int {
        var night = -1
        ActivityScenario.launch<AppSettingsActivity>(Intent(context, AppSettingsActivity::class.java)).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            }
        }
        return night
    }

    /** Waits until this process recorded [mode]; [displayed] is the night mode the screen was drawn with. */
    private fun awaitRecorded(mode: Int, expectDark: Boolean?, displayed: Int) {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < deadline && ApplicationNightModeSync.appliedForTest != mode) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(50L)
        }
        if (ApplicationNightModeSync.appliedForTest != mode) {
            fail("Night mode $mode was not recorded (process recorded ${ApplicationNightModeSync.appliedForTest})")
        }
        if (expectDark != null) {
            assertEquals(
                "The settings screen is drawn ${if (expectDark) "dark" else "light"}",
                if (expectDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
                displayed,
            )
        }
    }

    private fun refreshedHost(): AutoJs6HostSettingsResult {
        val result = AtomicReference<AutoJs6HostSettingsResult>()
        val done = CountDownLatch(1)
        AutoJs6HostSettingsClient.refresh(context) { next ->
            AutoJs6HostSettingsClient.publish(next)
            result.set(next)
            done.countDown()
        }
        assertTrue(done.await(10L, TimeUnit.SECONDS))
        return result.get()
    }

    private fun withRestoredAppearance(block: () -> Unit) {
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).all.toMap()
        try {
            block()
        } finally {
            replaceAll(saved)
            // Record the original resolution again, like the next screen the user opens would.
            refreshedHost()
            openSettings()
        }
    }

    private fun replaceAll(values: Map<String, *>) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val restored = preferences.edit().clear().apply {
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
        assertTrue(restored)
        assertEquals(values, preferences.all)
    }

    private fun encode(value: Any?): JSONObject? = when (value) {
        is String -> JSONObject().put("type", "string").put("value", value)
        is Int -> JSONObject().put("type", "int").put("value", value)
        is Long -> JSONObject().put("type", "long").put("value", value)
        is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
        is Boolean -> JSONObject().put("type", "boolean").put("value", value)
        is Set<*> -> JSONObject().put("type", "set").put("value", JSONArray(value.filterIsInstance<String>()))
        else -> null
    }

    private fun decode(item: JSONObject): Any = when (val type = item.getString("type")) {
        "string" -> item.getString("value")
        "int" -> item.getInt("value")
        "long" -> item.getLong("value")
        "float" -> item.getDouble("value").toFloat()
        "boolean" -> item.getBoolean("value")
        "set" -> item.getJSONArray("value").let { array -> (0 until array.length()).map(array::getString).toSet() }
        else -> error("Unknown preference type $type")
    }

    private companion object {
        const val PREFERENCES = "application-settings"
        const val COLD_START_BACKUP = "night-mode-cold-start-backup.json"
    }
}
