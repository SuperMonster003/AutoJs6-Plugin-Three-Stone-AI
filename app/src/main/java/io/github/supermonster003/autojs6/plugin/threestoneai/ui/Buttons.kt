package io.github.supermonster003.autojs6.plugin.threestoneai.ui

import android.content.res.ColorStateList
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import io.github.supermonster003.autojs6.plugin.threestoneai.AppColorPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.ConfiguredActivity

/**
 * Button factories. Every variant applies the runtime [ConfiguredActivity.appPalette]
 * explicitly so an arbitrary host or custom theme color always wins over the static
 * Material theme attributes.
 */

private fun ConfiguredActivity.baseButton(): MaterialButton = MaterialButton(this).apply {
    isAllCaps = false
    textSize = 14f
    typeface = Ui.mediumTypeface
    minimumHeight = uiDp(Ui.TOUCH_TARGET)
    minHeight = uiDp(Ui.TOUCH_TARGET)
    minimumWidth = 0
    minWidth = 0
    insetTop = 0
    insetBottom = 0
    setPaddingRelative(uiDp(20), 0, uiDp(20), 0)
}

/** High-emphasis filled button: the single primary action of a screen or region. */
internal fun ConfiguredActivity.filledButton(
    @StringRes textResource: Int,
    onClick: () -> Unit,
): MaterialButton = baseButton().apply {
    text = getString(textResource)
    backgroundTintList = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(appPalette.surfaceVariant, appPalette.accent),
    )
    setTextColor(
        ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(
                AppColorPolicy.withAlpha(appPalette.secondaryText, 0x99),
                AppColorPolicy.onThemeColor(appPalette.accent, appPalette.isDark),
            ),
        ),
    )
    rippleColor = ColorStateList.valueOf(
        AppColorPolicy.withAlpha(AppColorPolicy.onThemeColor(appPalette.accent, appPalette.isDark), 0x33),
    )
    setOnClickListener { onClick() }
}

/** Medium-emphasis tonal button: secondary actions that still deserve a fill. */
internal fun ConfiguredActivity.tonalButton(
    @StringRes textResource: Int,
    onClick: () -> Unit,
): MaterialButton = baseButton().apply {
    text = getString(textResource)
    backgroundTintList = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(
            AppColorPolicy.withAlpha(appPalette.secondaryText, 0x14),
            accentTone(appPalette.accent),
        ),
    )
    setTextColor(
        ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(AppColorPolicy.withAlpha(appPalette.secondaryText, 0x99), appPalette.accent),
        ),
    )
    rippleColor = ColorStateList.valueOf(accentRipple(appPalette.accent))
    setOnClickListener { onClick() }
}

/** Low-emphasis text button. */
internal fun ConfiguredActivity.textButton(
    @StringRes textResource: Int,
    onClick: () -> Unit,
): MaterialButton = MaterialButton(
    this,
    null,
    androidx.appcompat.R.attr.borderlessButtonStyle,
).apply {
    isAllCaps = false
    textSize = 14f
    typeface = Ui.mediumTypeface
    minimumHeight = uiDp(Ui.TOUCH_TARGET)
    minHeight = uiDp(Ui.TOUCH_TARGET)
    minimumWidth = 0
    minWidth = 0
    insetTop = 0
    insetBottom = 0
    setPaddingRelative(uiDp(Ui.SPACE_MD), 0, uiDp(Ui.SPACE_MD), 0)
    text = getString(textResource)
    setTextColor(
        ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(AppColorPolicy.withAlpha(appPalette.secondaryText, 0x99), appPalette.accent),
        ),
    )
    rippleColor = ColorStateList.valueOf(accentRipple(appPalette.accent))
    setOnClickListener { onClick() }
}

/** 48dp icon button with a borderless ripple; icon tinted for a neutral surface. */
internal fun ConfiguredActivity.iconButton(
    @DrawableRes iconResource: Int,
    @StringRes contentDescriptionResource: Int,
    tint: Int = appPalette.secondaryText,
    onClick: () -> Unit,
): ImageButton = ImageButton(this).apply {
    layoutParams = ViewGroup.LayoutParams(uiDp(Ui.ICON_BUTTON_SIZE), uiDp(Ui.ICON_BUTTON_SIZE))
    background = null
    applyThemedSelectableBackground(borderless = true)
    scaleType = android.widget.ImageView.ScaleType.CENTER
    setImageDrawable(tintedDrawable(iconResource, tint))
    contentDescription = getString(contentDescriptionResource)
    setOnClickListener { onClick() }
}
