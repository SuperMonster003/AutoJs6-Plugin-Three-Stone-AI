package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.io.File

/**
 * Receipts and screenshots for `.python/device_speech_check.py`. Files go to the app's external files
 * directory, which adb can read on every supported API level. No recognized or spoken text is written.
 */
internal object SpeechDeviceReport {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun directory(): File {
        val context = instrumentation.targetContext
        return File(checkNotNull(context.getExternalFilesDir(null)), DIRECTORY).apply { mkdirs() }
    }

    fun receipt(name: String, values: Map<String, Any?>) {
        val json = JSONObject()
        values.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
        File(directory(), "$name.json").writeText(json.toString(2))
    }

    fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        // Some devices (HyperOS on API 35) present the frame after a scroll noticeably later.
        SystemClock.sleep(SETTLE_MILLIS)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(directory(), "$name.png").outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
    }

    const val DIRECTORY = "speech-check"
    private const val SETTLE_MILLIS = 600L
}
