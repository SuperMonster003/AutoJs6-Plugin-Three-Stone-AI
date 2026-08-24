package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import kotlin.math.max

/** Makes programmatic screens edge-to-edge while keeping controls outside system bars/cutouts. */
@Suppress("DEPRECATION")
internal fun Activity.applySystemBarInsets(root: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
    } else {
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
    root.setOnApplyWindowInsetsListener { view, insets ->
        val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val systemBars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            SystemBarPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                max(systemBars.bottom, keyboard.bottom),
            )
        } else {
            SystemBarPadding(
                left = insets.systemWindowInsetLeft,
                top = insets.systemWindowInsetTop,
                right = insets.systemWindowInsetRight,
                bottom = insets.systemWindowInsetBottom,
            )
        }
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
    root.requestApplyInsets()
}

private data class SystemBarPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
