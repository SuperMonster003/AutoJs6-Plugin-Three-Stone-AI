package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.VisibleForTesting
import java.util.Locale

/** The text-to-speech engine as [SpeechOutput] uses it; replaceable in instrumentation. */
internal interface SpeechEngine {
    /** Selects a voice for [locale]; false when the engine lacks data or support for it. */
    fun setLanguage(locale: Locale): Boolean

    /** Returns to the engine's own default voice. */
    fun useDefaultVoice()

    /** Queues [text] behind earlier utterances; false when the engine rejected it. */
    fun speak(utteranceId: String, text: String): Boolean

    /** Drops the current and every queued utterance. */
    fun stop()

    fun shutdown()
}

/** Engine events, always delivered on the main thread. */
internal interface SpeechEngineCallbacks {
    fun onEngineReady(success: Boolean)

    fun onUtteranceStarted(utteranceId: String)

    /** The utterance was spoken, failed or was dropped by [SpeechEngine.stop]. */
    fun onUtteranceFinished(utteranceId: String)
}

internal fun interface SpeechEngineFactory {
    /** [enginePackage] null means the system default engine. */
    fun create(context: Context, enginePackage: String?, callbacks: SpeechEngineCallbacks): SpeechEngine
}

internal object SpeechEngines {
    /** Media volume and speech content, the same attributes as the audio focus request. */
    val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Instrumentation only: a scripted engine instead of `TextToSpeech`. */
    @VisibleForTesting
    @Volatile
    internal var factoryOverride: SpeechEngineFactory? = null

    val factory: SpeechEngineFactory
        get() = factoryOverride ?: SpeechEngineFactory(::TextToSpeechEngine)
}

/** `TextToSpeech` behind [SpeechEngine]. Texts are never logged (roadmap D16). */
private class TextToSpeechEngine(
    context: Context,
    enginePackage: String?,
    private val callbacks: SpeechEngineCallbacks,
) : SpeechEngine {
    private val handler = Handler(Looper.getMainLooper())
    private val textToSpeech = TextToSpeech(
        context.applicationContext,
        { status -> handler.post { callbacks.onEngineReady(status == TextToSpeech.SUCCESS) } },
        enginePackage,
    )

    init {
        textToSpeech.setAudioAttributes(SpeechEngines.AUDIO_ATTRIBUTES)
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                handler.post { callbacks.onUtteranceStarted(utteranceId) }
            }

            override fun onDone(utteranceId: String) = finished(utteranceId)

            @Deprecated("Superseded by onError(String, Int)")
            override fun onError(utteranceId: String) = finished(utteranceId)

            override fun onError(utteranceId: String, errorCode: Int) = finished(utteranceId)

            override fun onStop(utteranceId: String, interrupted: Boolean) = finished(utteranceId)
        })
    }

    private fun finished(utteranceId: String) {
        handler.post { callbacks.onUtteranceFinished(utteranceId) }
    }

    override fun setLanguage(locale: Locale): Boolean =
        runCatching { textToSpeech.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)

    override fun useDefaultVoice() {
        runCatching { textToSpeech.defaultVoice?.let { voice -> textToSpeech.voice = voice } }
    }

    override fun speak(utteranceId: String, text: String): Boolean = runCatching {
        textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId) == TextToSpeech.SUCCESS
    }.getOrDefault(false)

    override fun stop() {
        runCatching { textToSpeech.stop() }
    }

    override fun shutdown() {
        runCatching { textToSpeech.shutdown() }
    }
}
