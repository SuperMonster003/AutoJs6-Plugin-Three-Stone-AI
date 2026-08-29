package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Switch
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.drawable.DrawableCompat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal data class ResolvedApplicationSettings(
    val settings: ApplicationSettings,
    val hostResult: AutoJs6HostSettingsResult?,
)

internal object ApplicationSettingsResolver {
    fun resolve(context: Context): ResolvedApplicationSettings {
        val store = ApplicationSettingsStore(context)
        val stored = store.load()
        val followsHost = stored.themeSelection == AppThemeSelection.FOLLOW_AUTOJS6 ||
            stored.darkMode == AppDarkMode.FOLLOW_AUTOJS6 ||
            stored.language == AppLanguage.FOLLOW_AUTOJS6
        if (!followsHost) return ResolvedApplicationSettings(stored, null)

        val host = AutoJs6HostSettingsClient.query(context)
        // Keep the user's intent to follow AutoJs6 even while the host is absent. Resolution
        // falls back at runtime, so installing or re-enabling AutoJs6 later resumes following it
        // without silently rewriting the preference.
        return ResolvedApplicationSettings(stored, host)
    }
}

internal object AppConfiguration {
    fun wrap(base: Context): Context {
        val resolved = ApplicationSettingsResolver.resolve(base)
        val settings = resolved.settings
        val host = resolved.hostResult?.snapshot
        val configuration = Configuration(base.resources.configuration)
        val systemDark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val resolvedDark = if (settings.darkMode == AppDarkMode.FOLLOW_AUTOJS6 && host != null) {
            when (host.darkModePolicy) {
                AutoJs6DarkModePolicy.FOLLOW_SYSTEM -> systemDark
                AutoJs6DarkModePolicy.LIGHT -> false
                AutoJs6DarkModePolicy.DARK -> true
            }
        } else {
            AppSettingsPolicy.resolveDarkMode(settings.darkMode, systemDark)
        }
        configuration.uiMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
            if (resolvedDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO

        val locale = AppSettingsPolicy.resolveLanguageTag(
            settings.language,
            host?.resolvedLanguageTag,
        )?.let(Locale::forLanguageTag)
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

    /** Reuses a surface's brightness and saturation while associating it with the theme hue. */
    fun retoneSurface(referenceColor: Int, themeColor: Int, foregroundColor: Int): Int {
        val reference = FloatArray(3).also { Color.colorToHSV(referenceColor, it) }
        val theme = FloatArray(3).also { Color.colorToHSV(themeColor, it) }
        reference[0] = theme[0]
        reference[1] = when {
            theme[1] < 0.06f -> 0f
            else -> max(reference[1].toDouble(), (theme[1] * 0.35f).toDouble()).toFloat()
        }.coerceIn(0f, 1f)
        var candidate = Color.HSVToColor(Color.alpha(referenceColor), reference)
        if (contrastRatio(candidate, foregroundColor) >= MINIMUM_TEXT_CONTRAST) return candidate

        val moveDarker = luminance(foregroundColor) >= 0.5
        var low = 0f
        var high = 1f
        repeat(18) {
            val ratio = (low + high) / 2f
            val adjusted = reference.copyOf().apply {
                this[2] = if (moveDarker) reference[2] * (1f - ratio) else {
                    reference[2] + (1f - reference[2]) * ratio
                }
            }
            val tested = Color.HSVToColor(Color.alpha(referenceColor), adjusted)
            if (contrastRatio(tested, foregroundColor) >= MINIMUM_TEXT_CONTRAST) high = ratio
            else low = ratio
        }
        reference[2] = if (moveDarker) reference[2] * (1f - high) else {
            reference[2] + (1f - reference[2]) * high
        }
        candidate = Color.HSVToColor(Color.alpha(referenceColor), reference)
        return candidate
    }

    private fun blend(first: Int, second: Int, ratio: Double): Int {
        fun channel(shift: Int): Int {
            val start = first shr shift and 0xFF
            val end = second shr shift and 0xFF
            return (start + (end - start) * ratio).toInt().coerceIn(0, 255)
        }
        return -0x1000000 or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val MINIMUM_ACCENT_CONTRAST = 3.0
    private const val MINIMUM_TEXT_CONTRAST = 4.5
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
    val userSurface: Int,
    val assistantSurface: Int,
    val noticeSurface: Int,
    val inputSurface: Int,
    val chatBorder: Int,
    val isDark: Boolean,
) {
    companion object {
        fun resolve(context: Context): AppThemePalette {
            val resolved = ApplicationSettingsResolver.resolve(context)
            val settings = resolved.settings
            val primary = when (settings.themeSelection) {
                AppThemeSelection.FOLLOW_AUTOJS6 -> resolved.hostResult?.snapshot
                    ?.themeColorPrimary
                    ?: AppSettingsPolicy.THREE_STONE_AI_THEME_COLOR
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
                userSurface = AppColorPolicy.retoneSurface(
                    context.getColor(R.color.chat_user_surface),
                    primary,
                    context.getColor(R.color.text_color_primary),
                ),
                assistantSurface = AppColorPolicy.retoneSurface(
                    context.getColor(R.color.chat_assistant_surface),
                    primary,
                    context.getColor(R.color.text_color_primary),
                ),
                noticeSurface = AppColorPolicy.retoneSurface(
                    context.getColor(R.color.chat_notice_surface),
                    primary,
                    context.getColor(R.color.text_color_secondary),
                ),
                inputSurface = AppColorPolicy.retoneSurface(
                    context.getColor(R.color.chat_input_surface),
                    primary,
                    context.getColor(R.color.text_color_primary),
                ),
                chatBorder = AppColorPolicy.retoneSurface(
                    context.getColor(R.color.chat_border),
                    primary,
                    context.getColor(R.color.text_color_primary),
                ),
                isDark = isDark,
            )
        }
    }
}

abstract class ConfiguredActivity : AppCompatActivity() {
    internal lateinit var appPalette: AppThemePalette
        private set

    private var appliedSettingsRevision = Long.MIN_VALUE
    private var appliedHostAppearanceSignature: Int? = null
    private var recreationRequested = false

    override fun attachBaseContext(newBase: Context) {
        val configuredBase = AppConfiguration.wrap(newBase)
        val configuredNightMode = configuredBase.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        delegate.localNightMode = if (configuredNightMode == Configuration.UI_MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        super.attachBaseContext(configuredBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedSettingsRevision = ApplicationSettingsStore(this).revision()
        appliedHostAppearanceSignature = currentHostAppearanceSignature()
        appPalette = AppThemePalette.resolve(this)
        applyWindowAppearance()
    }

    override fun onResume() {
        super.onResume()
        if (
            !recreationRequested &&
            (
                ApplicationSettingsStore(this).revision() != appliedSettingsRevision ||
                    currentHostAppearanceSignature() != appliedHostAppearanceSignature
                )
        ) {
            recreationRequested = true
            recreate()
        }
    }

    private fun currentHostAppearanceSignature(): Int? {
        val settings = ApplicationSettingsStore(this).load()
        val followsHost = settings.themeSelection == AppThemeSelection.FOLLOW_AUTOJS6 ||
            settings.darkMode == AppDarkMode.FOLLOW_AUTOJS6 ||
            settings.language == AppLanguage.FOLLOW_AUTOJS6
        if (!followsHost) return null
        return AutoJs6HostSettingsClient.query(this).hashCode()
    }

    internal fun createAppToolbar(
        @StringRes titleResource: Int,
        showBack: Boolean,
        subtitleText: CharSequence? = null,
    ): Toolbar = Toolbar(this).apply {
        title = getString(titleResource)
        subtitle = subtitleText
        setBackgroundColor(appPalette.primary)
        setTitleTextColor(appPalette.onPrimary)
        setSubtitleTextColor(AppColorPolicy.withAlpha(appPalette.onPrimary, 0xB3))
        // AppCompat intentionally uses a taller action bar on large screens (normally 64 dp
        // instead of 56 dp). Keeping a hard-coded phone minimum lets the Toolbar grow to the
        // tablet height while still aligning its children against the shorter minimum, which
        // shifts navigation and action icons vertically on some vendor builds.
        minimumHeight = resolvedActionBarHeight()
        setContentInsetsRelative(uiDp(16), uiDp(8))
        this@ConfiguredActivity.setSupportActionBar(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(showBack)
        if (showBack) {
            navigationIcon = tintedDrawable(R.drawable.ic_arrow_back_24, appPalette.onPrimary)
            setNavigationContentDescription(R.string.navigation_back)
            setNavigationOnClickListener { finish() }
        }
        post { tintToolbarIcons(this) }
    }

    private fun resolvedActionBarHeight(): Int {
        val value = TypedValue()
        val resolved = theme.resolveAttribute(
            androidx.appcompat.R.attr.actionBarSize,
            value,
            true,
        )
        return if (resolved && value.type == TypedValue.TYPE_DIMENSION) {
            TypedValue.complexToDimensionPixelSize(value.data, resources.displayMetrics)
        } else {
            uiDp(56)
        }
    }

    internal fun createStatusBarBackground(): View = View(this).apply {
        setBackgroundColor(appPalette.primary)
    }

    internal fun tintToolbarIcons(toolbar: Toolbar) {
        centerToolbarChildren(toolbar)
        toolbar.navigationIcon = toolbar.navigationIcon?.tinted(appPalette.onPrimary)
        toolbar.overflowIcon = toolbar.overflowIcon?.tinted(appPalette.onPrimary)
        toolbar.menu.tintIcons(appPalette.onPrimary)
        toolbar.collapseIcon = toolbar.collapseIcon?.tinted(appPalette.onPrimary)
    }

    private fun centerToolbarChildren(toolbar: Toolbar) {
        for (index in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(index)
            val params = child.layoutParams as? Toolbar.LayoutParams ?: continue
            val horizontalGravity = if (params.gravity >= 0) {
                params.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK
            } else {
                Gravity.NO_GRAVITY
            }
            val centeredGravity = horizontalGravity or Gravity.CENTER_VERTICAL
            if (params.gravity != centeredGravity) {
                params.gravity = centeredGravity
                child.layoutParams = params
            }
        }
    }

    internal fun applyThemeToControls(root: View) {
        when (root) {
            is SwitchCompat -> {
                root.thumbTintList = switchThumbTintList()
                root.trackTintList = switchTrackTintList()
            }
            is Switch -> {
                root.thumbTintList = switchThumbTintList()
                root.trackTintList = switchTrackTintList()
            }
            is CompoundButton -> root.buttonTintList = controlTintList()
            is CheckedTextView -> root.checkMarkTintList = controlTintList()
            is EditText -> tintEditText(root)
            is ProgressBar -> {
                root.progressTintList = ColorStateList.valueOf(appPalette.accent)
                root.indeterminateTintList = ColorStateList.valueOf(appPalette.accent)
            }
            is Button -> {
                root.backgroundTintList = ColorStateList.valueOf(appPalette.primary)
                root.setTextColor(appPalette.onPrimary)
            }
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) applyThemeToControls(root.getChildAt(index))
        }
    }

    internal fun tintDialogButtons(dialog: AlertDialog) {
        dialog.listView?.let(::applyThemeToControls)
        listOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
        ).forEach { button ->
            dialog.getButton(button)?.setTextColor(appPalette.accent)
        }
    }

    internal fun tintEditText(editText: EditText) {
        editText.backgroundTintList = controlTintList()
        editText.highlightColor = AppColorPolicy.withAlpha(appPalette.accent, 0x55)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.textCursorDrawable = editText.textCursorDrawable?.tinted(appPalette.accent)
        }
    }

    internal fun controlTintList(): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(),
        ),
        intArrayOf(
            AppColorPolicy.withAlpha(appPalette.secondaryText, 0x66),
            appPalette.accent,
            appPalette.accent,
            appPalette.secondaryText,
        ),
    )

    internal fun switchThumbTintList(): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(
            AppColorPolicy.withAlpha(appPalette.secondaryText, 0x55),
            appPalette.accent,
            appPalette.secondaryText,
        ),
    )

    internal fun switchTrackTintList(): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(
            AppColorPolicy.withAlpha(appPalette.secondaryText, 0x24),
            AppColorPolicy.withAlpha(appPalette.accent, 0x66),
            AppColorPolicy.withAlpha(appPalette.secondaryText, 0x4D),
        ),
    )

    internal fun tintedDrawable(@DrawableRes resource: Int, color: Int) =
        AppCompatResources.getDrawable(this, resource)?.tinted(color)

    internal fun uiDp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    private fun android.graphics.drawable.Drawable.tinted(color: Int) =
        DrawableCompat.wrap(mutate()).also { drawable -> DrawableCompat.setTint(drawable, color) }

    private fun Menu.tintIcons(color: Int) {
        for (index in 0 until size()) {
            val item = getItem(index)
            item.icon = item.icon?.tinted(color)
            item.subMenu?.tintIcons(color)
        }
    }
}
