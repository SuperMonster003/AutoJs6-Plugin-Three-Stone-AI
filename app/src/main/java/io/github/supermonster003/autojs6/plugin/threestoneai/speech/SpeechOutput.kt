package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.Locale

/** Why a reading ended before its last sentence. */
internal enum class ReadingStopReason {
    /** Another app took the audio focus (a call, other playback) or refused it (D18). */
    AUDIO_FOCUS,

    /** The engine did not start. */
    ENGINE_FAILED,
}

/**
 * Reads one reply at a time through a [SpeechEngine] (roadmap D10, D12, D18, D19, D24). The engine
 * starts on first use and is replaced when the preferred engine changes. Sentences may arrive while
 * the reply streams; [endInput] marks the last one. The audio focus is held for the whole reading
 * with `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`; losing it stops the reading. Main thread only.
 */
internal class SpeechOutput(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        /** The message being read changed; null when nothing is read any more. */
        fun onReadingChanged(messageId: Long?)

        fun onReadingFinished(messageId: Long)

        fun onReadingStopped(messageId: Long, reason: ReadingStopReason)

        /** The first sentence of a reading started to sound. */
        fun onFirstUtteranceStarted(messageId: Long)

        /** None of the requested languages is available; the engine's default voice is used. */
        fun onLanguageUnavailable(locale: Locale)
    }

    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = this.context.getSystemService(AudioManager::class.java)
    private val queue = ReadingQueue()
    private val waiting = ArrayList<Utterance>()
    private var engine: SpeechEngine? = null
    private var enginePackage: String? = null
    private var engineReady = false
    private var languages: List<Locale> = emptyList()
    private var languageApplied = false
    private var firstUtteranceReported = false
    private var focusRequest: AudioFocusRequest? = null
    private var holdsFocus = false
    private var shutDown = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> interrupt(ReadingStopReason.AUDIO_FOCUS)
        }
    }

    private val callbacks = object : SpeechEngineCallbacks {
        override fun onEngineReady(success: Boolean) {
            if (shutDown) return
            if (!success) {
                engine?.shutdown()
                engine = null
                engineReady = false
                interrupt(ReadingStopReason.ENGINE_FAILED)
                return
            }
            engineReady = true
            if (queue.active) {
                applyLanguage()
                val pending = waiting.toList()
                waiting.clear()
                pending.forEach(::speak)
            }
        }

        override fun onUtteranceStarted(utteranceId: String) {
            val messageId = queue.messageId ?: return
            if (!firstUtteranceReported && queue.owns(utteranceId)) {
                firstUtteranceReported = true
                listener.onFirstUtteranceStarted(messageId)
            }
        }

        override fun onUtteranceFinished(utteranceId: String) {
            val messageId = queue.messageId ?: return
            if (queue.completed(utteranceId)) complete(messageId)
        }
    }

    val readingMessageId: Long? get() = queue.messageId

    /**
     * Starts reading [messageId], replacing any current reading. [languages] are tried in order
     * (D19, D36). Returns false when the audio focus is refused, which also reports a stop.
     */
    fun begin(messageId: Long, enginePackage: String?, languages: List<Locale>): Boolean {
        check(!shutDown)
        silence()
        queue.begin(messageId)
        if (!requestFocus()) {
            queue.clear()
            listener.onReadingStopped(messageId, ReadingStopReason.AUDIO_FOCUS)
            listener.onReadingChanged(null)
            return false
        }
        ensureEngine(enginePackage)
        this.languages = languages
        languageApplied = false
        firstUtteranceReported = false
        if (engineReady) applyLanguage()
        listener.onReadingChanged(messageId)
        return true
    }

    fun append(sentences: List<String>) {
        val utterances = queue.enqueue(sentences)
        if (engineReady) utterances.forEach(::speak) else waiting += utterances
    }

    /** No more sentences follow; the reading finishes once the queued ones are spoken. */
    fun endInput() {
        val messageId = queue.messageId ?: return
        if (queue.endInput()) complete(messageId)
    }

    /** Stops on the app's own request; reports only the change. */
    fun stop() {
        if (!queue.active) return
        silence()
        listener.onReadingChanged(null)
    }

    fun shutdown() {
        silence()
        shutDown = true
        engine?.shutdown()
        engine = null
        engineReady = false
    }

    private fun ensureEngine(packageName: String?) {
        if (engine != null && packageName == enginePackage) return
        engine?.shutdown()
        engineReady = false
        enginePackage = packageName
        engine = SpeechEngines.factory.create(context, packageName, callbacks)
    }

    private fun applyLanguage() {
        if (languageApplied) return
        val current = engine ?: return
        languageApplied = true
        if (languages.any(current::setLanguage)) return
        current.useDefaultVoice()
        val reported = languages.lastOrNull() ?: return
        if (REPORTED_LANGUAGES.add(reported.toLanguageTag())) listener.onLanguageUnavailable(reported)
    }

    private fun speak(utterance: Utterance) {
        val current = engine
        if (current == null || !current.speak(utterance.id, utterance.text)) {
            // A rejected sentence is skipped like a failed one.
            handler.post { callbacks.onUtteranceFinished(utterance.id) }
        }
    }

    private fun complete(messageId: Long) {
        waiting.clear()
        abandonFocus()
        listener.onReadingFinished(messageId)
        listener.onReadingChanged(null)
    }

    private fun interrupt(reason: ReadingStopReason) {
        val messageId = queue.messageId ?: return
        silence()
        listener.onReadingStopped(messageId, reason)
        listener.onReadingChanged(null)
    }

    /** Ends the current reading without reporting it. */
    private fun silence() {
        if (queue.clear() != null) engine?.stop()
        waiting.clear()
        abandonFocus()
    }

    private fun requestFocus(): Boolean {
        if (holdsFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(SpeechEngines.AUDIO_ATTRIBUTES)
                .setOnAudioFocusChangeListener(focusListener, handler)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
        holdsFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holdsFocus
    }

    private fun abandonFocus() {
        if (!holdsFocus) return
        holdsFocus = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private companion object {
        /** "Shown once" (D19) holds for the app process. */
        val REPORTED_LANGUAGES = HashSet<String>()
    }
}
