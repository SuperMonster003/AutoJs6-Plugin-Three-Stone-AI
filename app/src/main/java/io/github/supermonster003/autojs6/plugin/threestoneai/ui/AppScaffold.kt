package io.github.supermonster003.autojs6.plugin.threestoneai.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ConfiguredActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.applySystemBarInsets

internal class Scaffold(
    val root: LinearLayout,
    val toolbar: Toolbar,
    val scroll: ScrollView?,
    val content: LinearLayout,
)

/**
 * Shared screen shell: status bar spacer, neutral toolbar, hairline, and a
 * scrollable content column with system bar insets wired up. Replaces the
 * per-activity copies of the same structure.
 */
internal fun ConfiguredActivity.buildScaffold(
    @StringRes titleResource: Int,
    showBack: Boolean = true,
    subtitle: CharSequence? = null,
    scrollable: Boolean = true,
    contentPadding: ContentPadding = ContentPadding.NONE,
): Scaffold {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)
    }
    val statusBarBackground = createStatusBarBackground()
    root.addView(
        statusBarBackground,
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0),
    )
    val toolbar = createAppToolbar(titleResource, showBack, subtitle)
    root.addView(toolbar)
    root.addView(
        View(this).apply { setBackgroundColor(appPalette.divider) },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, uiDp(1)),
    )
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(
            uiDp(contentPadding.horizontalDp),
            uiDp(contentPadding.topDp),
            uiDp(contentPadding.horizontalDp),
            uiDp(contentPadding.bottomDp),
        )
    }
    var scroll: ScrollView? = null
    if (scrollable) {
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                content,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
    } else {
        root.addView(
            content,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
    }
    applySystemBarInsets(root, statusBarBackground)
    return Scaffold(root, toolbar, scroll, content)
}

internal class ContentPadding(
    val horizontalDp: Int,
    val topDp: Int,
    val bottomDp: Int,
) {
    companion object {
        /** Rows manage their own horizontal padding (settings-style screens). */
        val NONE = ContentPadding(0, Ui.SPACE_SM, Ui.SECTION_GAP)

        /** Card lists and prose screens share the standard screen margin. */
        val SCREEN = ContentPadding(Ui.SCREEN_MARGIN, Ui.SPACE_LG, Ui.SECTION_GAP)
    }
}
