package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.View
import android.view.WindowInsetsController
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal object HostAppearanceResolver {
    private const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    private const val HOST_THEME_RESOURCE_NAME = "theme_color_default"

    fun themeColor(context: Context): Int {
        val hostContext = runCatching {
            context.createPackageContext(HOST_PACKAGE_NAME, 0)
        }.getOrNull() ?: return AppSettingsPolicy.AUTOJS6_DEFAULT_THEME_COLOR
        val resourceId = hostContext.resources.getIdentifier(
            HOST_THEME_RESOURCE_NAME,
            "color",
            HOST_PACKAGE_NAME,
        )
        return if (resourceId == 0) {
            AppSettingsPolicy.AUTOJS6_DEFAULT_THEME_COLOR
        } else {
            runCatching { hostContext.getColor(resourceId) }
                .getOrDefault(AppSettingsPolicy.AUTOJS6_DEFAULT_THEME_COLOR)
        }
    }

    /**
     * Android normally restricts querying another app's per-app locale to its installer or IME.
     * Keep this best-effort so a future host contract or privileged installation works without a
     * settings migration, while ordinary installations honestly fall back to AutoJs6's default
     * follow-system policy.
     */
    fun locale(context: Context): Locale? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching {
            context.getSystemService(LocaleManager::class.java)
                .getApplicationLocales(HOST_PACKAGE_NAME)
                .takeUnless(LocaleList::isEmpty)
                ?.get(0)
        }.getOrNull()
    }
}

internal object AppConfiguration {
    fun wrap(base: Context): Context {
        val settings = ApplicationSettingsStore(base).load()
        val configuration = Configuration(base.resources.configuration)
        val systemDark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val resolvedDark = AppSettingsPolicy.resolveDarkMode(settings.darkMode, systemDark)
        configuration.uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
            if (resolvedDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO

        val locale = when (settings.language) {
            AppLanguage.FOLLOW_AUTOJS6 -> HostAppearanceResolver.locale(base)
            AppLanguage.FOLLOW_SYSTEM -> null
            else -> settings.language.languageTag?.let(Locale::forLanguageTag)
        }
        if (locale != null) {
            configuration.setLocale(locale)
            configuration.setLocales(LocaleList(locale))
            configuration.setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }
}

internal object AppColorPolicy {
    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = (color shr shift and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Matches the light/dark foreground decision used by AutoJs6. */
    fun onThemeColor(themeColor: Int, darkSurface: Boolean): Int {
        val threshold = if (darkSurface) 0.141 else 0.224
        return if (luminance(themeColor) >= threshold) OPAQUE_BLACK else OPAQUE_WHITE
    }

    fun contrastRatio(first: Int, second: Int): Double {
        val lighter = max(luminance(first), luminance(second))
        val darker = min(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun readableAccent(themeColor: Int, backgroundColor: Int): Int {
        if (contrastRatio(themeColor, backgroundColor) >= MINIMUM_ACCENT_CONTRAST) return themeColor
        val target = if (luminance(backgroundColor) >= 0.5) OPAQUE_BLACK else OPAQUE_WHITE
        var low = 0.0
        var high = 1.0
        repeat(18) {
            val middle = (low + high) / 2.0
            val candidate = blend(themeColor, target, middle)
            if (contrastRatio(candidate, backgroundColor) >= MINIMUM_ACCENT_CONTRAST) {
                high = middle
            } else {
                low = middle
            }
        }
        return blend(themeColor, target, high)
    }

    fun withAlpha(color: Int, alpha: Int): Int = color and 0xFFFFFF or (alpha.coerceIn(0, 255) shl 24)

    private fun blend(first: Int, second: Int, ratio: Double): Int {
        fun channel(shift: Int): Int {
            val start = first shr shift and 0xFF
            val end = second shr shift and 0xFF
            return (start + (end - start) * ratio).toInt().coerceIn(0, 255)
        }
        return -0x1000000 or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val MINIMUM_ACCENT_CONTRAST = 3.0
    private const val OPAQUE_BLACK = -0x1000000
    private const val OPAQUE_WHITE = -0x1
}

internal data class AppThemePalette(
    val primary: Int,
    val onPrimary: Int,
    val accent: Int,
    val windowBackground: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val divider: Int,
    val isDark: Boolean,
) {
    companion object {
        fun resolve(context: Context): AppThemePalette {
            val settings = ApplicationSettingsStore(context).load()
            val primary = when (settings.themeSelection) {
                AppThemeSelection.FOLLOW_AUTOJS6 -> HostAppearanceResolver.themeColor(context)
                AppThemeSelection.CUSTOM -> settings.customThemeColor
            }.let(AppSettingsPolicy::normalizeOpaqueColor)
            val background = context.getColor(R.color.window_background)
            val isDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            return AppThemePalette(
                primary = primary,
                onPrimary = AppColorPolicy.onThemeColor(primary, isDark),
                accent = AppColorPolicy.readableAccent(primary, background),
                windowBackground = background,
                primaryText = context.getColor(R.color.text_color_primary),
                secondaryText = context.getColor(R.color.text_color_secondary),
                divider = context.getColor(R.color.divider),
                isDark = isDark,
            )
        }
    }
}

abstract class ConfiguredActivity : Activity() {
    internal lateinit var appPalette: AppThemePalette
        private set

    private var appliedSettingsRevision = Long.MIN_VALUE
    private var recreationRequested = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppConfiguration.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedSettingsRevision = ApplicationSettingsStore(this).revision()
        appPalette = AppThemePalette.resolve(this)
        applyWindowAppearance()
    }

    override fun onResume() {
        super.onResume()
        if (
            !recreationRequested &&
            ApplicationSettingsStore(this).revision() != appliedSettingsRevision
        ) {
            recreationRequested = true
            recreate()
        }
    }

    private fun applyWindowAppearance() {
        window.statusBarColor = appPalette.primary
        window.navigationBarColor = appPalette.windowBackground
        val lightStatusBackground = AppColorPolicy.luminance(appPalette.primary) >= 0.179
        val lightNavigationBackground = AppColorPolicy.luminance(appPalette.windowBackground) >= 0.179
        val decorView = window.decorView
        decorView.post {
            if (isFinishing || isDestroyed) return@post
            applySystemBarIconAppearance(
                decorView,
                lightStatusBackground,
                lightNavigationBackground,
            )
        }
    }

    private fun applySystemBarIconAppearance(
        decorView: View,
        lightStatusBackground: Boolean,
        lightNavigationBackground: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var appearance = 0
            var mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            if (lightStatusBackground) appearance = appearance or
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mask = mask or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                if (lightNavigationBackground) appearance = appearance or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            }
            decorView.windowInsetsController?.setSystemBarsAppearance(appearance, mask)
        } else {
            @Suppress("DEPRECATION")
            var visibility = decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            visibility = if (lightStatusBackground) {
                visibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                visibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                visibility = if (lightNavigationBackground) {
                    visibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    visibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
            }
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = visibility
        }
    }
}
