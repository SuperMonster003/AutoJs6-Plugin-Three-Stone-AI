package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import java.util.Locale

internal enum class AppThemeSelection {
    FOLLOW_AUTOJS6,
    CUSTOM,
}

internal enum class AppDarkMode {
    FOLLOW_AUTOJS6,
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

internal enum class AppLanguage(val languageTag: String?) {
    FOLLOW_AUTOJS6(null),
    FOLLOW_SYSTEM(null),
    CHINESE_SIMPLIFIED("zh-Hans"),
    CHINESE_TRADITIONAL_HONG_KONG("zh-Hant-HK"),
    CHINESE_TRADITIONAL_TAIWAN("zh-Hant-TW"),
    ENGLISH("en"),
    FRENCH("fr"),
    SPANISH("es"),
    JAPANESE("ja"),
    KOREAN("ko"),
    RUSSIAN("ru"),
    ARABIC("ar"),
}

internal data class ApplicationSettings(
    val themeSelection: AppThemeSelection = AppThemeSelection.FOLLOW_AUTOJS6,
    val customThemeColor: Int = AppSettingsPolicy.AUTOJS6_DEFAULT_THEME_COLOR,
    val darkMode: AppDarkMode = AppDarkMode.FOLLOW_AUTOJS6,
    val language: AppLanguage = AppLanguage.FOLLOW_AUTOJS6,
)

internal object AppSettingsPolicy {
    const val AUTOJS6_DEFAULT_THEME_COLOR = -8_531 // #FFDEAD
    const val THREE_STONE_AI_THEME_COLOR = -12_952_376 // #FF3A5CC8 (lapis; rendered per-mode via R.color.brand_primary)
    const val ORANGE_THEME_COLOR = -1_533_440 // #FFE89A00
    const val TEAL_THEME_COLOR = -16_745_334 // #FF007C8A
    const val BLUE_THEME_COLOR = -12_627_531 // #FF3F51B5
    const val GREEN_THEME_COLOR = -13_730_510 // #FF2E7D32
    const val PURPLE_THEME_COLOR = -8_497_214 // #FF7E57C2

    fun isCuratedThemeColor(color: Int): Boolean = normalizeOpaqueColor(color) in setOf(
        THREE_STONE_AI_THEME_COLOR,
        ORANGE_THEME_COLOR,
        TEAL_THEME_COLOR,
        BLUE_THEME_COLOR,
        GREEN_THEME_COLOR,
        PURPLE_THEME_COLOR,
    )

    fun normalizeOpaqueColor(color: Int): Int = color or -0x1000000

    /** Keeps the settings summary and the runtime palette on the same resolved theme seed. */
    fun resolveThemeColor(
        settings: ApplicationSettings,
        autoJs6ThemeColor: Int?,
    ): Int = when (settings.themeSelection) {
        AppThemeSelection.FOLLOW_AUTOJS6 -> resolveAutoJs6ThemeColor(autoJs6ThemeColor)
        AppThemeSelection.CUSTOM -> normalizeOpaqueColor(settings.customThemeColor)
    }

    /** AutoJs6's documented default is used whenever its settings contract is unavailable. */
    fun resolveAutoJs6ThemeColor(autoJs6ThemeColor: Int?): Int =
        normalizeOpaqueColor(autoJs6ThemeColor ?: AUTOJS6_DEFAULT_THEME_COLOR)

    fun parseOpaqueColor(value: String): Int? {
        val normalized = value.trim().removePrefix("#")
        if (normalized.length != 6 || normalized.any { it.digitToIntOrNull(16) == null }) return null
        return normalized.toLong(16).toInt() or -0x1000000
    }

    fun colorHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    fun resolveDarkMode(mode: AppDarkMode, systemDark: Boolean): Boolean = when (mode) {
        AppDarkMode.FOLLOW_AUTOJS6,
        AppDarkMode.FOLLOW_SYSTEM,
        -> systemDark
        AppDarkMode.LIGHT -> false
        AppDarkMode.DARK -> true
    }

    fun resolveLanguageTag(
        language: AppLanguage,
        autoJs6ResolvedLanguageTag: String?,
    ): String? = when (language) {
        AppLanguage.FOLLOW_AUTOJS6 -> autoJs6ResolvedLanguageTag?.takeIf(String::isNotBlank)
        AppLanguage.FOLLOW_SYSTEM -> null
        else -> language.languageTag
    }

    fun languageForResolvedTag(languageTag: String?): AppLanguage? {
        val locale = languageTag
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(Locale::forLanguageTag)
            ?.takeIf { it.language.isNotEmpty() }
            ?: return null
        return when (locale.language.lowercase(Locale.ROOT)) {
            "zh" -> when {
                locale.script.equals("Hans", ignoreCase = true) ->
                    AppLanguage.CHINESE_SIMPLIFIED
                locale.country.equals("HK", ignoreCase = true) ||
                    locale.country.equals("MO", ignoreCase = true) ->
                    AppLanguage.CHINESE_TRADITIONAL_HONG_KONG
                locale.script.equals("Hant", ignoreCase = true) ||
                    locale.country.equals("TW", ignoreCase = true) ->
                    AppLanguage.CHINESE_TRADITIONAL_TAIWAN
                else -> AppLanguage.CHINESE_SIMPLIFIED
            }
            "en" -> AppLanguage.ENGLISH
            "fr" -> AppLanguage.FRENCH
            "es" -> AppLanguage.SPANISH
            "ja" -> AppLanguage.JAPANESE
            "ko" -> AppLanguage.KOREAN
            "ru" -> AppLanguage.RUSSIAN
            "ar" -> AppLanguage.ARABIC
            else -> null
        }
    }

    fun storedEnum(value: String?, fallback: AppThemeSelection): AppThemeSelection =
        value?.let { runCatching { AppThemeSelection.valueOf(it) }.getOrNull() } ?: fallback

    fun storedEnum(value: String?, fallback: AppDarkMode): AppDarkMode =
        value?.let { runCatching { AppDarkMode.valueOf(it) }.getOrNull() } ?: fallback

    fun storedEnum(value: String?, fallback: AppLanguage): AppLanguage =
        value?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: fallback
}

internal class ApplicationSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): ApplicationSettings = ApplicationSettings(
        themeSelection = AppSettingsPolicy.storedEnum(
            preferences.getString(KEY_THEME_SELECTION, null),
            AppThemeSelection.FOLLOW_AUTOJS6,
        ),
        customThemeColor = AppSettingsPolicy.normalizeOpaqueColor(
            preferences.getInt(
                KEY_CUSTOM_THEME_COLOR,
                AppSettingsPolicy.AUTOJS6_DEFAULT_THEME_COLOR,
            ),
        ),
        darkMode = AppSettingsPolicy.storedEnum(
            preferences.getString(KEY_DARK_MODE, null),
            AppDarkMode.FOLLOW_AUTOJS6,
        ),
        language = AppSettingsPolicy.storedEnum(
            preferences.getString(KEY_LANGUAGE, null),
            AppLanguage.FOLLOW_AUTOJS6,
        ),
    )

    fun save(settings: ApplicationSettings) {
        preferences.edit()
            .putString(KEY_THEME_SELECTION, settings.themeSelection.name)
            .putInt(
                KEY_CUSTOM_THEME_COLOR,
                AppSettingsPolicy.normalizeOpaqueColor(settings.customThemeColor),
            )
            .putString(KEY_DARK_MODE, settings.darkMode.name)
            .putString(KEY_LANGUAGE, settings.language.name)
            .putLong(KEY_REVISION, revision() + 1L)
            .apply()
    }

    fun revision(): Long = preferences.getLong(KEY_REVISION, 0L)

    private companion object {
        const val PREFERENCES_NAME = "application-settings"
        const val KEY_THEME_SELECTION = "theme-selection"
        const val KEY_CUSTOM_THEME_COLOR = "custom-theme-color"
        const val KEY_DARK_MODE = "dark-mode"
        const val KEY_LANGUAGE = "language"
        const val KEY_REVISION = "revision"
    }
}
