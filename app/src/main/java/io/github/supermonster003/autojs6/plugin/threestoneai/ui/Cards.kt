package io.github.supermonster003.autojs6.plugin.threestoneai.ui

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import io.github.supermonster003.autojs6.plugin.threestoneai.AppColorPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.ConfiguredActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.R

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

/** Tone of a card status badge (D98): default target, ready, or something the user must fix. */
internal enum class BadgeTone { ACCENT, NEUTRAL, WARNING }

internal class CardBadge(val text: String, val tone: BadgeTone)

/** Small status pill on a card. */
internal fun ConfiguredActivity.statusBadge(badge: CardBadge): TextView = TextView(this).apply {
    text = badge.text
    textSize = Ui.TEXT_CAPTION
    typeface = Ui.mediumTypeface
    maxLines = 1
    val (fill, textColor, stroke) = when (badge.tone) {
        BadgeTone.ACCENT -> Triple(
            accentTone(appPalette.accent),
            AppColorPolicy.readableAccent(appPalette.accent, appPalette.surface),
            null,
        )
        BadgeTone.NEUTRAL -> Triple(appPalette.surfaceVariant, appPalette.secondaryText, null)
        BadgeTone.WARNING -> Triple(appPalette.surface, appPalette.primaryText, appPalette.outline)
    }
    setTextColor(textColor)
    background = roundedFill(fill, BADGE_RADIUS_DP, stroke)
    setPaddingRelative(uiDp(Ui.SPACE_SM), uiDp(2), uiDp(Ui.SPACE_SM), uiDp(2))
}

/**
 * The one card shape of the models and services page (roadmap 1.7.0 P5.2, D98): title and "more"
 * button, a subtitle, then status badges. Online services and local models use it alike.
 */
internal fun ConfiguredActivity.itemCard(
    title: CharSequence,
    subtitle: CharSequence,
    badges: List<CardBadge>,
    @StringRes actionsDescription: Int,
    onActions: (View) -> Unit,
    onClick: () -> Unit,
): LinearLayout {
    val card = cardContainer(interactive = true)
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    header.addView(
        TextView(this).apply {
            text = title
            textSize = Ui.TEXT_ITEM
            typeface = Ui.mediumTypeface
            setTextColor(appPalette.primaryText)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        },
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
    )
    header.addView(
        iconButton(R.drawable.ic_more_vert_24, actionsDescription) { }.also { button ->
            button.setOnClickListener { onActions(button) }
        },
    )
    card.addView(header)
    card.addView(
        TextView(this).apply {
            text = subtitle
            textSize = Ui.TEXT_SECONDARY
            setTextColor(appPalette.secondaryText)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        },
    )
    if (badges.isNotEmpty()) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPaddingRelative(0, uiDp(Ui.SPACE_SM), 0, uiDp(Ui.SPACE_XS))
        }
        badges.forEachIndexed { index, badge ->
            row.addView(
                statusBadge(badge),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { if (index > 0) marginStart = uiDp(Ui.SPACE_SM) },
            )
        }
        card.addView(row)
    }
    card.contentDescription = (listOf(title, subtitle) + badges.map { it.text }).joinToString(", ")
    card.setOnClickListener { onClick() }
    card.layoutParams = cardListParams().apply {
        marginStart = uiDp(Ui.SCREEN_MARGIN)
        marginEnd = uiDp(Ui.SCREEN_MARGIN)
    }
    return card
}

private const val BADGE_RADIUS_DP = 8
