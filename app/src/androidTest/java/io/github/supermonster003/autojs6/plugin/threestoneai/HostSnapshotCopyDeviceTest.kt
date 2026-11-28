package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * D226 (3-stone-voice-1c A3): a refresh the screen accepted is stored for the next process, and a host that cannot be
 * read clears the copy. The stored copy and this process's value are put back afterwards.
 */
class HostSnapshotCopyDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences(AutoJs6HostSettingsCopy.FILE, Context.MODE_PRIVATE)
    private lateinit var storedBefore: Map<String, *>
    private lateinit var resultBefore: AutoJs6HostSettingsResult

    @Before
    fun remember() {
        storedBefore = preferences.all.toMap()
        resultBefore = AutoJs6HostSettingsClient.query(context)
    }

    @After
    fun restore() {
        val editor = preferences.edit().clear()
        storedBefore.forEach { (key, value) ->
            when (value) {
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
        }
        assertTrue(editor.commit())
        AutoJs6HostSettingsClient.publish(resultBefore)
    }

    @Test
    fun anAcceptedSnapshotIsStoredAndAnUnreadableHostClearsIt() {
        val snapshot = AutoJs6HostSettingsSnapshot(
            hostVersionCode = 6642L,
            hostVersionName = "6.8.1",
            themeColorPrimary = -0x2153,
            themeColorPrimaryDark = -0x10000,
            themeColorAccent = -0xff0100,
            darkModePolicy = AutoJs6DarkModePolicy.LIGHT,
            darkModeActive = false,
            languageTag = "ar",
            resolvedLanguageTag = "ar-EG",
        )
        val available = AutoJs6HostSettingsResult(AutoJs6HostAvailability.AVAILABLE, snapshot)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AutoJs6HostSettingsClient.accept(context, available)
            assertEquals(available, AutoJs6HostSettingsClient.query(context))
        }
        // apply() updates the in-memory copy at once, so a read of the same file sees it.
        assertEquals(snapshot, AutoJs6HostSettingsCopy.fromStored(preferences.all))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AutoJs6HostSettingsClient.accept(context, AutoJs6HostSettingsResult(AutoJs6HostAvailability.CONTRACT_UNAVAILABLE))
        }
        assertTrue(preferences.all.toString(), preferences.all.isEmpty())
    }
}
