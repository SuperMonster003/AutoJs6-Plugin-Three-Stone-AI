package io.github.supermonster003.autojs6.plugin.threestoneai.ui

import android.widget.LinearLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.ConfiguredActivity

/**
 * The app's single card shape: surface fill, 14dp radius, hairline outline.
 * Selected cards swap the outline for the accent and add a soft tone.
 */
internal fun ConfiguredActivity.cardContainer(
    interactive: Boolean = false,
    selected: Boolean = false,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val fill = if (selected) accentTone(appPalette.accent) else appPalette.surface
    val stroke = if (selected) appPalette.accent else appPalette.outline
    background = if (interactive) {
        roundedRippleFill(fill, accentRipple(appPalette.accent), Ui.RADIUS_CARD, stroke)
    } else {
        roundedFill(fill, Ui.RADIUS_CARD, stroke)
    }
    if (interactive) {
        isClickable = true
        isFocusable = true
    }
    setPaddingRelative(uiDp(Ui.SPACE_LG), uiDp(Ui.SPACE_LG - 2), uiDp(Ui.SPACE_LG), uiDp(Ui.SPACE_LG - 2))
}

/** Standard layout params for a card in a vertical list. */
internal fun ConfiguredActivity.cardListParams(
    topMarginDp: Int = 0,
    bottomMarginDp: Int = Ui.SPACE_MD,
): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    LinearLayout.LayoutParams.WRAP_CONTENT,
).apply {
    topMargin = uiDp(topMarginDp)
    bottomMargin = uiDp(bottomMarginDp)
}
