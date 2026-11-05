package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.graphics.PorterDuff
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.accentRipple
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.boundedRipple
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.uiDp

/** Which state the thinking card of one assistant reply shows (roadmap D79, P3.2). */
internal object ChatReasoningCardPolicy {
    /**
     * Expanded while generating when [ChatUiSettings.showReasoningWhileGenerating] is on, collapsed
     * afterwards when [ChatUiSettings.collapseReasoningWhenDone] is on; a tap ([userChoice]) wins
     * until the reply finishes.
     */
    fun expanded(status: ChatMessageStatus, settings: ChatUiSettings, userChoice: Boolean?): Boolean =
        userChoice ?: if (status == ChatMessageStatus.GENERATING) {
            settings.showReasoningWhileGenerating
        } else {
            !settings.collapseReasoningWhenDone
        }

    /** Whole seconds for "Thought for N s", rounded and at least 1. */
    fun seconds(durationMillis: Long): Long = ((durationMillis + 500L) / 1_000L).coerceAtLeast(1L)
}

/**
 * Card above an assistant reply that holds its thinking text: a header ("Thinking..." or "Thought for
 * N s") that toggles a plain-text body. The layout follows RikkaHub's reasoning card; no code is
 * taken from it (AGPL).
 */
internal class ReasoningCardView(
    context: Context,
    private val palette: AppThemePalette,
) : LinearLayout(context) {
    private val title = TextView(context)
    private val chevron = ImageView(context)
    private val body = TextView(context)
    private var shownBody: String? = null

    /** Called when the header is tapped. */
    var onToggle: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        background = context.roundedFill(android.graphics.Color.TRANSPARENT, Ui.RADIUS_CONTROL, palette.chatBorder)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.uiDp(40)
            setPaddingRelative(context.uiDp(12), context.uiDp(6), context.uiDp(8), context.uiDp(6))
            background = context.boundedRipple(accentRipple(palette.accent), Ui.RADIUS_CONTROL)
            isClickable = true
            isFocusable = true
            setOnClickListener { onToggle?.invoke() }
        }
        title.setTextColor(palette.secondaryText)
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        chevron.setImageResource(R.drawable.ic_chevron_right_24)
        chevron.setColorFilter(palette.secondaryText, PorterDuff.Mode.SRC_IN)
        chevron.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        header.addView(chevron, LayoutParams(context.uiDp(20), context.uiDp(20)))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.setTextColor(palette.secondaryText)
        body.setTextIsSelectable(true)
        body.setPaddingRelative(context.uiDp(12), 0, context.uiDp(12), context.uiDp(10))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun show(headerText: String, bodyText: String, expanded: Boolean, textSizeSp: Float) {
        title.text = headerText
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        chevron.rotation = when {
            expanded -> 90f
            rtl -> 180f
            else -> 0f
        }
        val action = context.getString(
            if (expanded) R.string.chat_reasoning_collapse else R.string.chat_reasoning_expand,
        )
        (title.parent as View).contentDescription = "$headerText, $action"
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        if (expanded && shownBody != bodyText) {
            body.text = bodyText
            shownBody = bodyText
        }
    }
}
