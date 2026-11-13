package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.accentRipple
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.boundedRipple
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.uiDp
import java.util.Locale

/**
 * The "< 2/3 >" switch under the first reply of a turn with several versions (roadmap P6.2, D82,
 * D148). The layout follows RikkaHub's message branch selector; no code is taken from it (AGPL).
 */
internal class ChatBranchSwitchView(
    context: Context,
    private val palette: AppThemePalette,
) : LinearLayout(context) {
    private val previous = ImageView(context)
    private val label = TextView(context)
    private val next = ImageView(context)

    /** The turn and version shown, or null before [show]. */
    var position: ChatBranchPosition? = null
        private set

    /** Called with the turn and the 0-based version to show. */
    var onSelect: ((ChatBranchPosition, Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setUpButton(previous, R.string.chat_branch_previous, step = -1)
        // The chevron mirrors itself in right-to-left layouts; flipping it again points to the start.
        previous.scaleX = -1f
        addView(previous, LayoutParams(context.uiDp(BUTTON_DP), context.uiDp(BUTTON_DP)))
        label.textSize = 12f
        label.setTextColor(palette.secondaryText)
        label.gravity = Gravity.CENTER
        label.minWidth = context.uiDp(28)
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        setUpButton(next, R.string.chat_branch_next, step = 1)
        addView(next, LayoutParams(context.uiDp(BUTTON_DP), context.uiDp(BUTTON_DP)))
    }

    fun show(position: ChatBranchPosition, enabled: Boolean) {
        this.position = position
        val shown = position.index + 1
        label.text = String.format(Locale.getDefault(), "%d/%d", shown, position.count)
        label.contentDescription = context.getString(R.string.chat_branch_position, shown, position.count)
        setButtonEnabled(previous, enabled && position.index > 0)
        setButtonEnabled(next, enabled && position.index < position.count - 1)
    }

    private fun setUpButton(button: ImageView, descriptionResource: Int, step: Int) {
        button.setImageResource(R.drawable.ic_chevron_right_24)
        button.setColorFilter(palette.secondaryText, PorterDuff.Mode.SRC_IN)
        button.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val padding = context.uiDp(10)
        button.setPadding(padding, padding, padding, padding)
        button.background = context.boundedRipple(accentRipple(palette.accent), Ui.RADIUS_CONTROL)
        button.contentDescription = context.getString(descriptionResource)
        button.setOnClickListener {
            val current = position ?: return@setOnClickListener
            val target = current.index + step
            if (target in 0 until current.count) onSelect?.invoke(current, target)
        }
    }

    private fun setButtonEnabled(button: ImageView, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else Ui.DISABLED_ALPHA
    }

    private companion object {
        const val BUTTON_DP = 44
    }
}
