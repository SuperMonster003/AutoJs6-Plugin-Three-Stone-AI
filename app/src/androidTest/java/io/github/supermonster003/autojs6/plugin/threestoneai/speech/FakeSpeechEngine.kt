package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Collections
import java.util.Locale

/**
 * A silent engine that "speaks" each utterance for [utteranceMillis] in queue order. With [hold]
 * utterances start but never finish until [release]. Records what was spoken and when.
 */
internal class FakeSpeechEngine(private val callbacks: SpeechEngineCallbacks) : SpeechEngine {
    data class Spoken(val id: String, val text: String, val queuedAt: Long)

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Spoken>()
    private var current: Spoken? = null

    val spoken: MutableList<Spoken> = Collections.synchronizedList(ArrayList())
    var stops = 0
        private set
    var defaultVoiceUses = 0
        private set
    var shutDown = false
        private set

    /** Every language the chat asked for, in order. */
    val languagesTried: MutableList<String> = Collections.synchronizedList(ArrayList())

    override fun setLanguage(locale: Locale): Boolean {
        languagesTried += locale.toLanguageTag()
        return locale.language in supportedLanguages
    }

    override fun useDefaultVoice() {
        defaultVoiceUses++
    }

    override fun speak(utteranceId: String, text: String): Boolean {
        val item = Spoken(utteranceId, text, SystemClock.elapsedRealtime())
        spoken += item
        queue.addLast(item)
        if (current == null) next()
        return true
    }

    override fun stop() {
        stops++
        val dropped = listOfNotNull(current) + queue
        current = null
        queue.clear()
        handler.removeCallbacksAndMessages(null)
        dropped.forEach { item -> handler.post { callbacks.onUtteranceFinished(item.id) } }
    }

    override fun shutdown() {
        shutDown = true
        handler.removeCallbacksAndMessages(null)
    }

    private fun next() {
        val item = queue.removeFirstOrNull() ?: return
        current = item
        handler.post { callbacks.onUtteranceStarted(item.id) }
        if (!hold) handler.postDelayed({ finish(item) }, utteranceMillis)
    }

    private fun finish(item: Spoken) {
        if (current != item) return
        current = null
        callbacks.onUtteranceFinished(item.id)
        next()
    }

    companion object {
        @Volatile var utteranceMillis = 60L
        @Volatile var hold = false
        @Volatile var initSucceeds = true
        @Volatile var supportedLanguages: Set<String> = setOf("en", "zh", "ja", "ko", "fr", "es", "ru", "ar")
        val engines: MutableList<FakeSpeechEngine> = Collections.synchronizedList(ArrayList())

        /** Installs the fake for every engine the chat creates. */
        fun install() {
            utteranceMillis = 60L
            hold = false
            initSucceeds = true
            supportedLanguages = setOf("en", "zh", "ja", "ko", "fr", "es", "ru", "ar")
            engines.clear()
            SpeechEngines.factoryOverride = SpeechEngineFactory { _, _, callbacks ->
                FakeSpeechEngine(callbacks).also { engine ->
                    engines += engine
                    Handler(Looper.getMainLooper()).post { callbacks.onEngineReady(initSucceeds) }
                }
            }
        }

        fun uninstall() {
            SpeechEngines.factoryOverride = null
            engines.clear()
        }

        fun release() {
            hold = false
            val engine = engines.lastOrNull() ?: return
            engine.handler.post {
                engine.current?.let(engine::finish)
            }
        }

        fun spokenTexts(): List<String> = engines.flatMap { engine -> engine.spoken.map(Spoken::text) }
    }
}
