package io.github.supermonster003.autojs6.plugin.threestoneai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

class AboutIconFrameTest {
    @Test fun roundedFrameKeepsTheGlyphAndInteriorTransparent() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val icon = activity.findViewById<View>(android.R.id.content).findViewWithTag<ImageView>("about-icon")
                val size = (88 * activity.resources.displayMetrics.density + 0.5f).toInt()
                assertEquals(size, icon.layoutParams.width)
                assertEquals(size, icon.layoutParams.height)
                assertTrue(icon.clipToOutline)
                val frame = icon.background as GradientDrawable
                assertTrue(frame.cornerRadius > 0)
                assertEquals(Color.TRANSPARENT, frame.color!!.defaultColor)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                frame.setBounds(0, 0, size, size)
                frame.draw(Canvas(bitmap))
                assertEquals("Frame interior must reveal the same page as its exterior", 0, Color.alpha(bitmap.getPixel(size / 2, size / 2)))
                assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
                assertTrue("The rounded outline must remain visible", (0 until size).any { Color.alpha(bitmap.getPixel(it, size / 2)) > 0 })
                assertTrue(icon.drawable is BitmapDrawable)
                assertEquals("The icon must not add a launcher background", 0, Color.alpha((icon.drawable as BitmapDrawable).bitmap.getPixel(0, 0)))
                bitmap.recycle()
            }
        }
    }
}
