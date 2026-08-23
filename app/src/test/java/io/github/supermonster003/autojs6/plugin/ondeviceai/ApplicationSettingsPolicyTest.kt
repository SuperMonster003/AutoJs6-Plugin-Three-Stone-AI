package io.github.supermonster003.autojs6.plugin.ondeviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ApplicationSettingsPolicyTest {
    @Test
    fun `application settings follow AutoJs6 by default`() {
        val settings = ApplicationSettings()

        assertEquals(AppThemeSelection.FOLLOW_AUTOJS6, settings.themeSelection)
        assertEquals(AppDarkMode.FOLLOW_AUTOJS6, settings.darkMode)
        assertEquals(AppLanguage.FOLLOW_AUTOJS6, settings.language)
        assertEquals("#FFDEAD", AppSettingsPolicy.colorHex(settings.customThemeColor))
    }

    @Test
    fun `theme colors accept exactly six hexadecimal digits`() {
        assertEquals(0xFF12ABEF.toInt(), AppSettingsPolicy.parseOpaqueColor("#12abef"))
        assertEquals(0xFF12ABEF.toInt(), AppSettingsPolicy.parseOpaqueColor(" 12ABEF "))
        assertNull(AppSettingsPolicy.parseOpaqueColor("#123"))
        assertNull(AppSettingsPolicy.parseOpaqueColor("#12345G"))
        assertNull(AppSettingsPolicy.parseOpaqueColor("#AA12ABEF"))
    }

    @Test
    fun `AutoJs6 luminance policy selects readable foreground`() {
        assertEquals(0xFF000000.toInt(), AppColorPolicy.onThemeColor(0xFFFFDEAD.toInt(), false))
        assertEquals(0xFFFFFFFF.toInt(), AppColorPolicy.onThemeColor(0xFF263238.toInt(), false))
        assertTrue(
            AppColorPolicy.contrastRatio(
                AppColorPolicy.readableAccent(0xFFFFDEAD.toInt(), 0xFFFFFFFF.toInt()),
                0xFFFFFFFF.toInt(),
            ) >= 3.0,
        )
    }

    @Test
    fun `dark mode defaults track system state`() {
        assertEquals(false, AppSettingsPolicy.resolveDarkMode(AppDarkMode.FOLLOW_AUTOJS6, false))
        assertEquals(true, AppSettingsPolicy.resolveDarkMode(AppDarkMode.FOLLOW_AUTOJS6, true))
        assertEquals(false, AppSettingsPolicy.resolveDarkMode(AppDarkMode.LIGHT, true))
        assertEquals(true, AppSettingsPolicy.resolveDarkMode(AppDarkMode.DARK, false))
    }

    @Test
    fun `follow AutoJs6 language uses the host resolved compatible tag`() {
        assertEquals(
            "zh-Hans",
            AppSettingsPolicy.resolveLanguageTag(AppLanguage.FOLLOW_AUTOJS6, "zh-Hans"),
        )
        assertNull(AppSettingsPolicy.resolveLanguageTag(AppLanguage.FOLLOW_AUTOJS6, ""))
        assertNull(AppSettingsPolicy.resolveLanguageTag(AppLanguage.FOLLOW_SYSTEM, "ja"))
        assertEquals(
            "fr",
            AppSettingsPolicy.resolveLanguageTag(AppLanguage.FRENCH, "zh-Hans"),
        )
    }

    @Test
    fun `missing or disabled host migrates only follow selections to app defaults`() {
        val fallback = AppSettingsPolicy.fallbackWithoutAutoJs6(ApplicationSettings())

        assertEquals(AppThemeSelection.CUSTOM, fallback.themeSelection)
        assertEquals(AppSettingsPolicy.ON_DEVICE_AI_THEME_COLOR, fallback.customThemeColor)
        assertEquals(AppDarkMode.FOLLOW_SYSTEM, fallback.darkMode)
        assertEquals(AppLanguage.FOLLOW_SYSTEM, fallback.language)

        val explicit = ApplicationSettings(
            themeSelection = AppThemeSelection.CUSTOM,
            customThemeColor = 0xFF123456.toInt(),
            darkMode = AppDarkMode.DARK,
            language = AppLanguage.JAPANESE,
        )
        assertEquals(explicit, AppSettingsPolicy.fallbackWithoutAutoJs6(explicit))
    }

    @Test
    fun `release history resolves every supported locale`() {
        assertEquals("CHANGELOG-zh-Hans.md", ReleaseHistoryAssetPolicy.assetFor(Locale.forLanguageTag("zh-CN")))
        assertEquals("CHANGELOG-zh-Hant-HK.md", ReleaseHistoryAssetPolicy.assetFor(Locale.forLanguageTag("zh-Hant-HK")))
        assertEquals("CHANGELOG-zh-Hant-TW.md", ReleaseHistoryAssetPolicy.assetFor(Locale.forLanguageTag("zh-Hant-TW")))
        assertEquals("CHANGELOG-ja.md", ReleaseHistoryAssetPolicy.assetFor(Locale.JAPANESE))
        assertEquals("CHANGELOG-en.md", ReleaseHistoryAssetPolicy.assetFor(Locale.GERMAN))
    }
}
