package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * D226 (3-stone-voice-1c A3): the copy of the last AutoJs6 snapshot a cold start draws its first frame from, instead of
 * the system language and night mode; a missing or malformed part means no copy, so the screen falls back to the
 * system until the host answers.
 */
class AutoJs6HostSettingsCopyTest {
    private val snapshot = AutoJs6HostSettingsSnapshot(
        hostVersionCode = 6642L,
        hostVersionName = "6.8.1",
        themeColorPrimary = -0x2153,
        themeColorPrimaryDark = -0x10000,
        themeColorAccent = -0xff0100,
        darkModePolicy = AutoJs6DarkModePolicy.DARK,
        darkModeActive = true,
        languageTag = "auto",
        resolvedLanguageTag = "zh-Hans-CN",
    )

    @Test
    fun `a stored snapshot comes back unchanged`() {
        val entries = AutoJs6HostSettingsCopy.entries(snapshot)
        assertEquals(snapshot, AutoJs6HostSettingsCopy.fromStored(entries))
        val light = snapshot.copy(darkModePolicy = AutoJs6DarkModePolicy.FOLLOW_SYSTEM, darkModeActive = false, resolvedLanguageTag = "en")
        assertEquals(light, AutoJs6HostSettingsCopy.fromStored(AutoJs6HostSettingsCopy.entries(light)))
    }

    @Test
    fun `a missing or malformed part means no copy`() {
        val entries = AutoJs6HostSettingsCopy.entries(snapshot)
        assertNull("empty", AutoJs6HostSettingsCopy.fromStored(emptyMap<String, Any>()))
        entries.keys.forEach { key -> assertNull(key, AutoJs6HostSettingsCopy.fromStored(entries - key)) }
        assertNull("dark as a string", AutoJs6HostSettingsCopy.fromStored(entries + ("dark" to "true")))
        assertNull("primary as a long", AutoJs6HostSettingsCopy.fromStored(entries + ("primary" to 1L)))
        assertNull("version code as an int", AutoJs6HostSettingsCopy.fromStored(entries + ("versionCode" to 6642)))
        assertNull("unknown policy", AutoJs6HostSettingsCopy.fromStored(entries + ("darkModePolicy" to "AUTO")))
        listOf("", "x", "en_US", "en--US", "zh-Hans-", "a".repeat(81), "123").forEach { tag ->
            assertNull(tag, AutoJs6HostSettingsCopy.fromStored(entries + ("resolvedLanguage" to tag)))
        }
    }
}
