package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

internal enum class SearchActionIcon {
    PREVIOUS,
    NEXT,
    CLOSE,
}

/** Consistent, font-independent search controls with one optical size and stroke weight. */
internal class SearchActionView(
    context: Context,
    private val icon: SearchActionIcon,
    color: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.25f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val halfWidth = dp(6.5f)
        val halfHeight = dp(3.75f)
        when (icon) {
            SearchActionIcon.PREVIOUS -> {
                canvas.drawLine(
                    centerX - halfWidth,
                    centerY + halfHeight,
                    centerX,
                    centerY - halfHeight,
                    paint,
                )
                canvas.drawLine(
                    centerX,
                    centerY - halfHeight,
                    centerX + halfWidth,
                    centerY + halfHeight,
                    paint,
                )
            }
            SearchActionIcon.NEXT -> {
                canvas.drawLine(
                    centerX - halfWidth,
                    centerY - halfHeight,
                    centerX,
                    centerY + halfHeight,
                    paint,
                )
                canvas.drawLine(
                    centerX,
                    centerY + halfHeight,
                    centerX + halfWidth,
                    centerY - halfHeight,
                    paint,
                )
            }
            SearchActionIcon.CLOSE -> {
                val halfSize = dp(5.5f)
                canvas.drawLine(
                    centerX - halfSize,
                    centerY - halfSize,
                    centerX + halfSize,
                    centerY + halfSize,
                    paint,
                )
                canvas.drawLine(
                    centerX + halfSize,
                    centerY - halfSize,
                    centerX - halfSize,
                    centerY + halfSize,
                    paint,
                )
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
