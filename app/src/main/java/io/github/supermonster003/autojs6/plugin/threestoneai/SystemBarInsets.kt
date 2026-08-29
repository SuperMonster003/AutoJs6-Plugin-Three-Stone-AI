package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import kotlin.math.max

/** Makes programmatic screens edge-to-edge while keeping controls outside system bars/cutouts. */
@Suppress("DEPRECATION")
internal fun Activity.applySystemBarInsets(root: View) {
    applySystemBarInsets(root) { padding ->
        root.setPadding(padding.left, padding.top, padding.right, padding.bottom)
    }
}

/** Keeps a normal-height app bar below a dedicated, app-colored status-bar background. */
internal fun Activity.applySystemBarInsets(
    root: View,
    statusBarBackground: View,
) {
    val rootPadding = SystemBarPadding(
        left = root.paddingLeft,
        top = root.paddingTop,
        right = root.paddingRight,
        bottom = root.paddingBottom,
    )
    val initialStatusBarHeight = statusBarBackground.layoutParams?.height?.coerceAtLeast(0) ?: 0
    applySystemBarInsets(root) { bars ->
        root.setPadding(
            rootPadding.left + bars.left,
            rootPadding.top,
            rootPadding.right + bars.right,
            rootPadding.bottom + bars.bottom,
        )
        val params = statusBarBackground.layoutParams ?: return@applySystemBarInsets
        val targetHeight = initialStatusBarHeight + bars.top
        if (params.height != targetHeight) {
            params.height = targetHeight
            statusBarBackground.layoutParams = params
        }
    }
}

/**
 * Enables edge-to-edge drawing while allowing containers such as [androidx.drawerlayout.widget.DrawerLayout]
 * to place the insets on children that actually participate in measurement.
 */
@Suppress("DEPRECATION")
internal fun Activity.applySystemBarInsets(
    root: View,
    applyPadding: (SystemBarPadding) -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
    } else {
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
    root.setOnApplyWindowInsetsListener { _, insets ->
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
        applyPadding(bars)
        insets
    }
    root.requestApplyInsets()
    root.post(root::requestApplyInsets)
}

internal data class SystemBarPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
