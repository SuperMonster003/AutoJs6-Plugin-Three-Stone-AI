package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.annotation.VisibleForTesting

/**
 * Android adapter for [SpeechAvailabilityPolicy]. The manifest declares both actions in `<queries>`
 * (action only, roadmap D22), so the package manager can see recognizers and engines on API 30+.
 */
internal object SpeechDeviceProbe {
    /** Instrumentation only: replaces the probed state so tests can use a fake recognizer. */
    @VisibleForTesting
    @Volatile
    internal var stateOverride: SpeechDeviceState? = null

    fun probe(context: Context): SpeechDeviceState {
        stateOverride?.let { return it }
        val packageManager = context.packageManager
        return runCatching {
            SpeechDeviceState(
                macawRecognizer = packageManager.resolvesActivity(
                    recognitionIntent().setPackage(SpeechAvailabilityPolicy.MACAW_PACKAGE),
                ),
                anyRecognizer = packageManager.resolvesActivity(recognitionIntent()),
                macawTtsEngine = packageManager.resolvesService(
                    ttsServiceIntent().setPackage(SpeechAvailabilityPolicy.MACAW_PACKAGE),
                ),
                anyTtsEngine = packageManager.resolvesService(ttsServiceIntent()),
            )
        }.getOrDefault(SpeechDeviceState.NONE)
    }

    fun availability(context: Context, preferMacaw: Boolean): SpeechAvailability =
        SpeechAvailabilityPolicy.resolve(probe(context), preferMacaw)

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

    private fun ttsServiceIntent() = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)

    private fun PackageManager.resolvesActivity(intent: Intent): Boolean =
        queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()

    private fun PackageManager.resolvesService(intent: Intent): Boolean =
        queryIntentServices(intent, 0).isNotEmpty()
}
