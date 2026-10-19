package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.annotation.VisibleForTesting
import java.util.Locale

/** Builds the `RECOGNIZE_SPEECH` request (roadmap D19, D23). Nothing here records audio. */
internal object DictationIntents {
    /** Instrumentation only: pins requests to a fake recognizer and adds its scripted reply. */
    @VisibleForTesting
    @Volatile
    internal var testPackage: String? = null

    @VisibleForTesting
    @Volatile
    internal var testExtras: Bundle? = null

    fun create(availability: SpeechAvailability, locale: Locale, prompt: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // 3-Macaw Speech only uses the language to choose a model; SenseVoice detects it anyway.
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .apply {
                val pinned = testPackage ?: availability.dictationPackage
                if (pinned != null) setPackage(pinned)
                testExtras?.let(::putExtras)
            }

    fun results(data: Intent?): List<String?>? = runCatching {
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
    }.getOrNull()
}
