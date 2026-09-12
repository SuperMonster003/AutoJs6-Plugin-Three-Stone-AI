package io.github.supermonster003.autojs6.plugin.threestoneai

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
    fun `follow AutoJs6 theme uses host color or AutoJs6 default`() {
        val settings = ApplicationSettings(themeSelection = AppThemeSelection.FOLLOW_AUTOJS6)

        assertEquals(
            AppSettingsPolicy.AUTOJS6_DEFAULT_THEME_COLOR,
            AppSettingsPolicy.resolveThemeColor(settings, autoJs6ThemeColor = null),
        )
        assertEquals(
            0xFF12ABEF.toInt(),
            AppSettingsPolicy.resolveThemeColor(settings, autoJs6ThemeColor = 0x8012ABEF.toInt()),
        )
        assertEquals(
            0xFF654321.toInt(),
            AppSettingsPolicy.resolveThemeColor(
                settings.copy(
                    themeSelection = AppThemeSelection.CUSTOM,
                    customThemeColor = 0x80654321.toInt(),
                ),
                autoJs6ThemeColor = 0xFF12ABEF.toInt(),
            ),
        )
    }

    @Test
    fun `resolved host language tags map to supported labels`() {
        assertEquals(
            AppLanguage.CHINESE_SIMPLIFIED,
            AppSettingsPolicy.languageForResolvedTag("zh-CN"),
        )
        assertEquals(
            AppLanguage.CHINESE_TRADITIONAL_HONG_KONG,
            AppSettingsPolicy.languageForResolvedTag("zh-Hant-HK"),
        )
        assertEquals(
            AppLanguage.CHINESE_TRADITIONAL_TAIWAN,
            AppSettingsPolicy.languageForResolvedTag("zh-TW"),
        )
        assertEquals(AppLanguage.ENGLISH, AppSettingsPolicy.languageForResolvedTag("en-US"))
        assertNull(AppSettingsPolicy.languageForResolvedTag("und"))
    }

    @Test
    fun `AutoJs6 luminance policy selects readable foreground`() {
        assertEquals(0xFF000000.toInt(), AppColorPolicy.onThemeColor(0xFFFFDEAD.toInt(), false))
        assertEquals(0xFFFFFFFF.toInt(), AppColorPolicy.onThemeColor(0xFF263238.toInt(), false))
        assertTrue(
            AppColorPolicy.contrastRatio(
                AppColorPolicy.readableAccent(0xFFFFDEAD.toInt(), 0xFFFFFFFF.toInt()),
                0xFFFFFFFF.toInt(),
            ) >= 4.5,
        )
    }

    @Test
    fun `bright action fills stay separate from readable text accents`() {
        val seed = 0xFFFFDEAD.toInt()
        val background = 0xFFFFFFFF.toInt()
        val accent = AppColorPolicy.readableAccent(seed, background)
        val onFill = AppColorPolicy.onFilledColor(seed)

        assertTrue(AppColorPolicy.luminance(seed) > AppColorPolicy.luminance(accent))
        assertTrue(AppColorPolicy.contrastRatio(seed, onFill) >= 4.5)
        assertTrue(AppColorPolicy.contrastRatio(accent, background) >= 4.5)
    }

    @Test
    fun `readable accent preserves hue and stops at requested contrast`() {
        listOf(
            0xFFE89A00.toInt() to 0xFFF5F5F5.toInt(),
            0xFF805000.toInt() to 0xFF121212.toInt(),
            0xFFE89A00.toInt() to 0xFFAAAAAA.toInt(),
        ).forEach { (seed, background) ->
            val accent = AppColorPolicy.readableAccent(seed, background)
            val contrast = AppColorPolicy.contrastRatio(accent, background)

            assertTrue(contrast >= 4.5)
            assertTrue(contrast < 4.55)
            assertTrue(hueDistance(hue(seed), hue(accent)) < 1.0)
        }
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
    fun `follow AutoJs6 choices remain stored while runtime fallbacks follow the system`() {
        val settings = ApplicationSettings()

        assertEquals(AppThemeSelection.FOLLOW_AUTOJS6, settings.themeSelection)
        assertEquals(AppDarkMode.FOLLOW_AUTOJS6, settings.darkMode)
        assertEquals(AppLanguage.FOLLOW_AUTOJS6, settings.language)
        assertTrue(AppSettingsPolicy.resolveDarkMode(settings.darkMode, systemDark = true))
        assertEquals(false, AppSettingsPolicy.resolveDarkMode(settings.darkMode, systemDark = false))
        assertNull(
            AppSettingsPolicy.resolveLanguageTag(
                settings.language,
                autoJs6ResolvedLanguageTag = null,
            ),
        )
    }

    @Test
    fun `orange preset is distinct and included in the curated palette`() {
        assertEquals("#E89A00", AppSettingsPolicy.colorHex(AppSettingsPolicy.ORANGE_THEME_COLOR))
        assertTrue(AppSettingsPolicy.isCuratedThemeColor(AppSettingsPolicy.ORANGE_THEME_COLOR))
        assertTrue(AppSettingsPolicy.isCuratedThemeColor(AppSettingsPolicy.TEAL_THEME_COLOR))
        assertEquals(false, AppSettingsPolicy.isCuratedThemeColor(0xFFFFDEAD.toInt()))
    }

    @Test
    fun `release history resolves every supported locale`() {
        assertEquals("CHANGELOG-zh-Hans.md", ReleaseHistoryAssetPolicy.assetFor(Locale.forLanguageTag("zh-CN")))
        assertEquals("CHANGELOG-zh-Hant-HK.md", ReleaseHistoryAssetPolicy.assetFor(Locale.forLanguageTag("zh-Hant-HK")))
        assertEquals("CHANGELOG-zh-Hant-TW.md", ReleaseHistoryAssetPolicy.assetFor(Locale.forLanguageTag("zh-Hant-TW")))
        assertEquals("CHANGELOG-ja.md", ReleaseHistoryAssetPolicy.assetFor(Locale.JAPANESE))
        assertEquals("CHANGELOG-en.md", ReleaseHistoryAssetPolicy.assetFor(Locale.GERMAN))
    }

    private fun hue(color: Int): Double {
        val red = (color shr 16 and 0xFF) / 255.0
        val green = (color shr 8 and 0xFF) / 255.0
        val blue = (color and 0xFF) / 255.0
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val range = maximum - minimum
        if (range == 0.0) return 0.0
        val sector = when (maximum) {
            red -> (green - blue) / range
            green -> (blue - red) / range + 2.0
            else -> (red - green) / range + 4.0
        }
        return (sector * 60.0 + 360.0) % 360.0
    }

    private fun hueDistance(first: Double, second: Double): Double {
        val distance = kotlin.math.abs(first - second)
        return minOf(distance, 360.0 - distance)
    }
}
