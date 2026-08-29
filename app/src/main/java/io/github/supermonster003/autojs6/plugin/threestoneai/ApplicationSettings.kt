package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context

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

    fun normalizeOpaqueColor(color: Int): Int = color or -0x1000000

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

    fun fallbackWithoutAutoJs6(settings: ApplicationSettings): ApplicationSettings = settings.copy(
        themeSelection = when (settings.themeSelection) {
            AppThemeSelection.FOLLOW_AUTOJS6 -> AppThemeSelection.CUSTOM
            AppThemeSelection.CUSTOM -> settings.themeSelection
        },
        customThemeColor = when (settings.themeSelection) {
            AppThemeSelection.FOLLOW_AUTOJS6 -> THREE_STONE_AI_THEME_COLOR
            AppThemeSelection.CUSTOM -> settings.customThemeColor
        },
        darkMode = when (settings.darkMode) {
            AppDarkMode.FOLLOW_AUTOJS6 -> AppDarkMode.FOLLOW_SYSTEM
            else -> settings.darkMode
        },
        language = when (settings.language) {
            AppLanguage.FOLLOW_AUTOJS6 -> AppLanguage.FOLLOW_SYSTEM
            else -> settings.language
        },
    )

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
