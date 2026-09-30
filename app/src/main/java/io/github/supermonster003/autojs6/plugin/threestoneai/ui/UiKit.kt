package io.github.supermonster003.autojs6.plugin.threestoneai.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import io.github.supermonster003.autojs6.plugin.threestoneai.AppColorPolicy

/**
 * Design tokens for the Ink & Stone design system. All UI code should draw its
 * spacing, radii and type values from here instead of ad-hoc literals.
 */
internal object Ui {
    // Spacing (dp), on a 4dp grid.
    const val SPACE_XS = 4
    const val SPACE_SM = 8
    const val SPACE_MD = 12
    const val SPACE_LG = 16
    const val SPACE_XL = 20
    const val SPACE_XXL = 24
    const val SPACE_XXXL = 32
    const val SCREEN_MARGIN = 20
    const val SECTION_GAP = 24

    // Corner radii (dp).
    const val RADIUS_CONTROL = 10
    const val RADIUS_CARD = 14
    const val RADIUS_BUBBLE = 18
    const val RADIUS_SHEET = 24

    // Type scale (sp).
    const val TEXT_DISPLAY = 24f
    const val TEXT_PAGE_TITLE = 20f
    const val TEXT_TITLE = 17f
    const val TEXT_ITEM = 16f
    const val TEXT_BODY = 14.5f
    const val TEXT_SECONDARY = 13f
    const val TEXT_SECTION = 12.5f
    const val TEXT_CAPTION = 12f
    const val TEXT_LABEL = 11.5f

    const val LINE_SPACING_BODY = 1.15f

    // Minimum interactive target (dp).
    const val TOUCH_TARGET = 48
    const val ICON_SIZE = 24
    const val ICON_BUTTON_SIZE = 48

    const val DISABLED_ALPHA = 0.42f

    val mediumTypeface: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

internal fun Context.uiDp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

internal fun Context.uiDpF(value: Float): Float =
    value * resources.displayMetrics.density

/** Rounded solid fill with an optional 1dp stroke; the single shape factory for the app. */
internal fun Context.roundedFill(
    fillColor: Int,
    radiusDp: Int,
    strokeColor: Int? = null,
): GradientDrawable = GradientDrawable().apply {
    setColor(fillColor)
    cornerRadius = uiDpF(radiusDp.toFloat())
    if (strokeColor != null) setStroke(uiDp(1), strokeColor)
}

/** [roundedFill] wrapped in a ripple, for interactive surfaces. */
internal fun Context.roundedRippleFill(
    fillColor: Int,
    rippleColor: Int,
    radiusDp: Int,
    strokeColor: Int? = null,
): RippleDrawable = RippleDrawable(
    ColorStateList.valueOf(rippleColor),
    roundedFill(fillColor, radiusDp, strokeColor),
    null,
)

/** Transparent ripple clipped to a rounded rect, for rows and text buttons. */
internal fun Context.boundedRipple(
    rippleColor: Int,
    radiusDp: Int,
): RippleDrawable = RippleDrawable(
    ColorStateList.valueOf(rippleColor),
    null,
    roundedFill(-0x1, radiusDp),
)

internal fun View.applyThemedSelectableBackground(borderless: Boolean = false) {
    val attribute = if (borderless) {
        android.R.attr.selectableItemBackgroundBorderless
    } else {
        android.R.attr.selectableItemBackground
    }
    val value = TypedValue()
    if (context.theme.resolveAttribute(attribute, value, true)) {
        var owner: android.content.Context = context
        while (owner is android.content.ContextWrapper && owner !is io.github.supermonster003.autojs6.plugin.threestoneai.ConfiguredActivity) {
            val next = owner.baseContext
            if (next === owner) break
            owner = next
        }
        val palette = (owner as? io.github.supermonster003.autojs6.plugin.threestoneai.ConfiguredActivity)?.appPalette
        background = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, value.resourceId)?.mutate()?.also {
            if (palette != null) (it as? RippleDrawable)?.setColor(ColorStateList.valueOf(accentRipple(palette.accent)))
        }
    }
}

/** Standard ripple alpha over an accent color. */
internal fun accentRipple(accent: Int): Int = AppColorPolicy.withAlpha(accent, 0x2E)

/** Soft tonal fill derived from an accent color, readable on any window background. */
internal fun accentTone(accent: Int): Int = AppColorPolicy.withAlpha(accent, 0x1C)
