package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatUiSettingsStore
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * P5-3 with real engines; plays sound at the device's own media volume. Measures, per engine and
 * voice, how long a three-sentence Chinese reply takes from `speak` to synthesis start and to the
 * first audio chunk (cold and warm), the longest pause between sentences, and the chat's own
 * automatic reading with the preferred engine. Do not run on adjacent phones at the same time.
 */
class SpeechEngineLatencyDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun enginesReadTheFirstSentenceQuickly() {
        val state = SpeechDeviceProbe.probe(context)
        assumeTrue("No text-to-speech engine", state.anyTtsEngine || state.macawTtsEngine)
        val values = linkedMapOf<String, Any?>()
        val runs = buildList {
            if (state.macawTtsEngine) {
                add(Run("aishell3", SpeechAvailabilityPolicy.MACAW_PACKAGE, "tts.vits.icefall.zh-aishell3/"))
                add(Run("melo", SpeechAvailabilityPolicy.MACAW_PACKAGE, "tts.vits.melo.zh-en/"))
            }
            add(Run("systemDefault", null, null))
        }
        for (run in runs) measure(run, values)
        SpeechDeviceReport.receipt("engine-latency", values)
    }

    @Test
    fun chatReadsAStreamingReplyWithTheRealEngine() {
        ChatTestEnvironment().use { environment ->
            val store = ChatUiSettingsStore(context)
            store.save(store.load().copy(autoReadReplies = true, preferMacawSpeech = true))
            val availability = SpeechDeviceProbe.availability(context, preferMacaw = true)
            assumeTrue("No text-to-speech engine", availability.readAloudAvailable)
            environment.backend.reply = listOf("第一句话来了。", "第二句", "也来了。", "最后一句。")
            environment.backend.chunkDelayMillis = 150L
            val values = linkedMapOf<String, Any?>("engine" to availability.readAloud.name)
            environment.launchChat().use { scenario ->
                scenario.onActivity { activity ->
                    composer(activity).setText("test")
                    activity.window.decorView.findViewWithTag<android.view.View>(ChatViewTags.COMPOSER_SEND).performClick()
                }
                waitUntil("reading starts") { var id: Long? = null; scenario.onActivity { id = it.readingMessageId }; id != null }
                waitUntil("the reply is read to the end", timeoutMillis = 60_000L) {
                    var id: Long? = 0L
                    scenario.onActivity { id = it.readingMessageId }
                    id == null
                }
                var latency: Long? = null
                scenario.onActivity { activity -> latency = activity.firstUtteranceLatencyMillis }
                assertNotNull("The first sentence started", latency)
                values["firstUtteranceMillis"] = latency
            }
            SpeechDeviceReport.receipt("engine-chat", values)
        }
    }

    private data class Run(val label: String, val enginePackage: String?, val voicePrefix: String?)

    private fun measure(run: Run, values: MutableMap<String, Any?>) {
        val ready = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        val created = SystemClock.elapsedRealtime()
        var initAt = 0L
        lateinit var tts: TextToSpeech
        instrumentation.runOnMainSync {
            tts = TextToSpeech(context, { result ->
                status = result
                initAt = SystemClock.elapsedRealtime()
                ready.countDown()
            }, run.enginePackage)
        }
        try {
            assertTrue("${run.label} did not initialize", ready.await(30, TimeUnit.SECONDS))
            assumeTrue("${run.label} failed to start", status == TextToSpeech.SUCCESS)
            values["${run.label}.initMillis"] = initAt - created
            if (run.voicePrefix != null) {
                val voice = tts.voices.orEmpty().filter { it.name.startsWith(run.voicePrefix) }.minByOrNull { it.name }
                if (voice == null) {
                    values["${run.label}.voice"] = "not installed"
                    return
                }
                tts.voice = voice
                values["${run.label}.voice"] = voice.name
            } else {
                values["${run.label}.engine"] = tts.defaultEngine
                tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            }
            for (pass in listOf("cold", "warm")) {
                val result = speakAll(tts, SENTENCES)
                values["${run.label}.$pass.startMillis"] = result.startMillis
                values["${run.label}.$pass.firstAudioMillis"] = result.firstAudioMillis
                values["${run.label}.$pass.maxGapMillis"] = result.maxGapMillis
                values["${run.label}.$pass.totalMillis"] = result.totalMillis
                values["${run.label}.$pass.errors"] = result.errors
            }
        } finally {
            tts.shutdown()
        }
    }

    private data class Result(
        val startMillis: Long?,
        val firstAudioMillis: Long?,
        val maxGapMillis: Long?,
        val totalMillis: Long?,
        val errors: Int,
    )

    private fun speakAll(tts: TextToSpeech, sentences: List<String>): Result {
        val starts = ConcurrentHashMap<String, Long>()
        val audio = ConcurrentHashMap<String, Long>()
        val ends = ConcurrentHashMap<String, Long>()
        val errors = ConcurrentHashMap<String, Int>()
        val done = CountDownLatch(sentences.size)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                starts.putIfAbsent(utteranceId, SystemClock.elapsedRealtime())
            }

            override fun onAudioAvailable(utteranceId: String, audioBuffer: ByteArray) {
                audio.putIfAbsent(utteranceId, SystemClock.elapsedRealtime())
            }

            override fun onDone(utteranceId: String) {
                ends[utteranceId] = SystemClock.elapsedRealtime()
                done.countDown()
            }

            @Deprecated("Superseded by onError(String, Int)")
            override fun onError(utteranceId: String) {
                errors[utteranceId] = 1
                ends[utteranceId] = SystemClock.elapsedRealtime()
                done.countDown()
            }
        })
        val begin = SystemClock.elapsedRealtime()
        sentences.forEachIndexed { index, sentence ->
            tts.speak(sentence, TextToSpeech.QUEUE_ADD, null, "s$index")
        }
        done.await(120, TimeUnit.SECONDS)
        // Audio that arrives after the previous sentence finished playing means an audible pause.
        val gaps = (1 until sentences.size).mapNotNull { index ->
            val previousEnd = ends["s${index - 1}"] ?: return@mapNotNull null
            val available = audio["s$index"] ?: return@mapNotNull null
            (available - previousEnd).coerceAtLeast(0L)
        }
        return Result(
            startMillis = starts["s0"]?.minus(begin),
            firstAudioMillis = audio["s0"]?.minus(begin),
            maxGapMillis = gaps.maxOrNull(),
            totalMillis = ends["s${sentences.lastIndex}"]?.minus(begin),
            errors = errors.size,
        )
    }

    private companion object {
        val SENTENCES = listOf(
            "你好, 这是朗读测试的第一句。",
            "第二句稍长一些, 用来观察两句之间有没有停顿。",
            "第三句结束。",
        )
    }
}
