package io.github.supermonster003.autojs6.plugin.threestoneai.ui

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.supermonster003.autojs6.plugin.threestoneai.ConfiguredActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.R

/** Accent-colored section header, the app's signature grouping element. */
internal fun ConfiguredActivity.sectionHeader(@StringRes titleResource: Int): TextView =
    TextView(this).apply {
        text = getString(titleResource)
        textSize = 14f
        typeface = Ui.mediumTypeface
        setTextColor(appPalette.secondaryText)
        setPaddingRelative(
            uiDp(Ui.SPACE_XXL),
            uiDp(Ui.SECTION_GAP),
            uiDp(Ui.SPACE_XXL),
            uiDp(Ui.SPACE_SM),
        )
    }

/**
 * Section header with a trailing "more" button that opens the section's own actions (roadmap 1.7.0
 * P5.2, D98): the models and services page has no overflow menu.
 */
internal fun ConfiguredActivity.sectionHeader(
    @StringRes titleResource: Int,
    onMenu: (View) -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPaddingRelative(0, uiDp(Ui.SECTION_GAP - Ui.SPACE_MD), uiDp(Ui.SPACE_SM), 0)
    addView(
        sectionHeader(titleResource).apply {
            setPaddingRelative(uiDp(Ui.SPACE_XXL), 0, uiDp(Ui.SPACE_SM), 0)
        },
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
    )
    addView(
        iconButton(R.drawable.ic_more_vert_24, R.string.models_section_actions) { }.also { button ->
            button.setOnClickListener { onMenu(button) }
        },
    )
}

/** Full-bleed hairline between groups; rows above/below carry their own margins. */
internal fun ConfiguredActivity.hairline(insetStartDp: Int = Ui.SPACE_XXL): View =
    View(this).apply {
        setBackgroundColor(appPalette.divider)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            uiDp(1),
        ).apply { marginStart = uiDp(insetStartDp) }
    }

/** Start inset of the hairline between two adjacent rows: past the leading icon column. */
internal const val ROW_HAIRLINE_INSET_DP = 64

/**
 * Separates every pair of adjacent setting rows in [content] with an inset hairline. Settings
 * pages build their rows first and call this once, so group dividers and headers stay untouched.
 */
internal fun ConfiguredActivity.insertRowHairlines(content: LinearLayout) {
    for (index in content.childCount - 1 downTo 1) {
        if (content.getChildAt(index) is SettingRowLayout &&
            content.getChildAt(index - 1) is SettingRowLayout
        ) {
            content.addView(hairline(ROW_HAIRLINE_INSET_DP), index)
        }
    }
}

internal class SettingRow(
    val view: LinearLayout,
    val titleView: TextView,
    val summaryView: TextView,
    val switchView: MaterialSwitch?,
)

internal class SettingRowLayout(context: android.content.Context) : LinearLayout(context)

/** Enables or disables the whole row, its texts included, so finders by text see the state too. */
internal fun SettingRow.setRowEnabled(enabled: Boolean) {
    view.isEnabled = enabled
    titleView.isEnabled = enabled
    summaryView.isEnabled = enabled
    view.alpha = if (enabled) 1f else Ui.DISABLED_ALPHA
}

private fun ConfiguredActivity.rowShell(): LinearLayout = SettingRowLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = uiDp(72)
    setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_MD), uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_MD))
}

private fun ConfiguredActivity.rowLeadingIcon(@DrawableRes iconResource: Int): ImageView =
    ImageView(this).apply {
        setImageDrawable(tintedDrawable(iconResource, appPalette.secondaryText))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(uiDp(Ui.ICON_SIZE), uiDp(Ui.ICON_SIZE)).apply {
            marginEnd = uiDp(Ui.SPACE_LG)
        }
    }

private fun ConfiguredActivity.rowTextColumn(
    title: CharSequence,
    summary: CharSequence?,
): Triple<LinearLayout, TextView, TextView> {
    val titleView = TextView(this).apply {
        text = title
        textSize = Ui.TEXT_ITEM
        setTextColor(appPalette.primaryText)
    }
    val summaryView = TextView(this).apply {
        text = summary
        textSize = 14f
        setTextColor(appPalette.secondaryText)
        setLineSpacing(0f, 1.1f)
        setPaddingRelative(0, uiDp(4), 0, 0)
        visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(titleView)
        addView(summaryView)
    }
    return Triple(column, titleView, summaryView)
}

/**
 * Standard tappable settings row: optional leading icon, title + summary,
 * optional trailing value text, and a chevron affordance.
 */
internal fun ConfiguredActivity.settingRow(
    title: CharSequence,
    summary: CharSequence? = null,
    @DrawableRes iconResource: Int? = null,
    value: CharSequence? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
): SettingRow {
    val shell = rowShell().apply { minimumHeight = uiDp(if (summary.isNullOrEmpty()) 56 else 72) }
    iconResource?.let { shell.addView(rowLeadingIcon(it)) }
    val (column, titleView, summaryView) = rowTextColumn(title, summary)
    shell.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    if (!value.isNullOrEmpty()) {
        shell.addView(
            TextView(this).apply {
                text = value
                textSize = 14f
                setTextColor(appPalette.secondaryText)
                setPaddingRelative(uiDp(Ui.SPACE_MD), 0, 0, 0)
            },
        )
    }
    if (showChevron && onClick != null) {
        shell.addView(
            ImageView(this).apply {
                setImageDrawable(tintedDrawable(R.drawable.ic_settings_chevron, appPalette.secondaryText))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(uiDp(Ui.ICON_SIZE), uiDp(Ui.ICON_SIZE)).apply {
                    marginStart = uiDp(Ui.SPACE_LG)
                }
                alpha = 0.6f
            },
        )
    }
    if (onClick != null) {
        shell.isClickable = true
        shell.isFocusable = true
        shell.applyThemedSelectableBackground()
        shell.setOnClickListener { onClick() }
    }
    return SettingRow(shell, titleView, summaryView, null)
}

/** Settings row with a trailing switch; the whole row toggles. */
internal fun ConfiguredActivity.switchRow(
    title: CharSequence,
    summary: CharSequence? = null,
    @DrawableRes iconResource: Int? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
): SettingRow {
    val shell = rowShell().apply { minimumHeight = uiDp(if (summary.isNullOrEmpty()) 56 else 72) }
    iconResource?.let { shell.addView(rowLeadingIcon(it)) }
    val (column, titleView, summaryView) = rowTextColumn(title, summary)
    shell.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    val switch = MaterialSwitch(this).apply {
        isChecked = checked
        thumbTintList = switchThumbTintList()
        trackTintList = switchTrackTintList()
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    shell.addView(
        switch,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = uiDp(Ui.SPACE_MD) },
    )
    shell.isClickable = true
    shell.isFocusable = true
    shell.applyThemedSelectableBackground()
    shell.setOnClickListener {
        switch.isChecked = !switch.isChecked
        onToggle(switch.isChecked)
    }
    shell.accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = android.widget.Switch::class.java.name
            info.isCheckable = true
            info.isChecked = switch.isChecked
            info.text = listOfNotNull(titleView.text, summaryView.text.takeIf { summaryView.visibility == View.VISIBLE }).joinToString(", ")
        }
    }
    return SettingRow(shell, titleView, summaryView, switch)
}
