package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.SystemClock
import android.speech.RecognizerIntent
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.mic
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CopyOnWriteArrayList

/**
 * P6-2 on a phone with the real 3-Macaw Speech (roadmap D214). Run only through `device_speech_check.py` and only
 * after the maintainer agreed to the sound or to the phone setup each case needs:
 *
 * - [twoSentencesThroughTheSpeaker]: the phone plays `en.wav` twice with a pause through its own speaker (media volume
 *   raised to `minVolume` percent only when lower, restored afterwards) while the chat dictates live; reports the
 *   delay of the first partial, the CER of each sentence and the number of finals. Never stores the text.
 * - [deniedMicrophoneOpensMacawsDialog]: 3-Macaw Speech's microphone permission was revoked; the press must open its
 *   permission screen, where the maintainer allows it; the next press dictates.
 * - [busyMicrophoneFallsBackToTheSheet]: another app (3-Stove Agent's resident listener) holds 3-Macaw Speech's
 *   microphone; the press must end with SESSION_BUSY and open the recognizer sheet (answered by a monitor here).
 */
class LiveDictationRealDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private lateinit var environment: ChatTestEnvironment
    private val events = CopyOnWriteArrayList<Pair<String, Long>>()
    private val finals = CopyOnWriteArrayList<Pair<String, Long>>()
    private val values = linkedMapOf<String, Any?>()

    @Before
    fun setUp() {
        environment = ChatTestEnvironment(macaw = null)
    }

    @After
    fun tearDown() {
        environment.close()
    }

    @Test
    fun twoSentencesThroughTheSpeaker() {
        assumeTrue("acoustic cases run only on request", arguments.getString("acoustic") == "true")
        environment.launchChat().use { scenario ->
            awaitProbe(scenario)
            assumeTrue("3-Macaw Speech does not dictate live here", liveAvailable(scenario))
            observe(scenario)
            setEmptyComposer(scenario)
            Speaker(environment.context, arguments.getString("minVolume")?.toIntOrNull() ?: 70).use { speaker ->
                scenario.onActivity { activity -> mic(activity).performClick() }
                waitUntil("3-Macaw Speech is ready", 15_000L) { events.any { it.first == "ready" } }
                val readyAt = events.first { it.first == "ready" }.second
                val playedAt = speaker.play(twoSentences())
                waitUntil("both sentences are final", 60_000L) { finals.size >= 2 && SystemClock.elapsedRealtime() > playedAt + playMillis() }
                scenario.onActivity { activity -> mic(activity).performClick() }
                waitUntil("the session ends", 10_000L) { events.any { it.first == "end" } }

                val firstPartial = events.firstOrNull { it.first == "partial" }?.second
                // A sentence's final arrives after the pause that follows it; the second sentence starts 2.5 s later.
                val split = playedAt + SENTENCE_MILLIS + PAUSE_MILLIS / 2 + SENTENCE_MILLIS / 2
                val first = finals.filter { it.second < split }.joinToString(" ") { it.first }
                val second = finals.filter { it.second >= split }.joinToString(" ") { it.first }
                values["volume"] = speaker.used
                values["readyMillis"] = readyAt - events.first().second
                values["firstPartialMillis"] = firstPartial?.minus(playedAt)
                values["firstFinalMillis"] = finals.firstOrNull()?.second?.minus(playedAt)
                values["finals"] = finals.size
                values["cerFirst"] = "%.3f".format(cer(TestAudio.EN_REFERENCE, first))
                values["cerSecond"] = "%.3f".format(cer(TestAudio.EN_REFERENCE, second))
                var composerChars = 0
                scenario.onActivity { activity -> composerChars = composer(activity).length() }
                values["composerChars"] = composerChars
                SpeechDeviceReport.receipt("live-acoustic", values)
                assertTrue("Two sentences need two finals: ${finals.size}", finals.size >= 2)
                assertTrue("The first partial came before the first final", firstPartial != null)
                assertTrue("First sentence CER ${values["cerFirst"]}", cer(TestAudio.EN_REFERENCE, first) < MAXIMUM_CER)
                assertTrue("Second sentence CER ${values["cerSecond"]}", cer(TestAudio.EN_REFERENCE, second) < MAXIMUM_CER)
            }
        }
    }

    @Test
    fun deniedMicrophoneOpensMacawsDialog() {
        assumeTrue("needs the maintainer on the phone", arguments.getString("permission") == "true")
        environment.launchChat().use { scenario ->
            awaitProbe(scenario)
            var status: MacawSpeechStatus? = null
            scenario.onActivity { activity -> status = activity.macawSpeechForTest?.status }
            assumeTrue("3-Macaw Speech still has the microphone: $status", status == MacawSpeechStatus.NO_MICROPHONE_PERMISSION)
            observe(scenario)
            // Not blocking: the real dialog opens and the maintainer answers it.
            val monitor = Instrumentation.ActivityMonitor(MacawSpeech.MICROPHONE_PERMISSION_ACTIVITY, null, false)
            instrumentation.addMonitor(monitor)
            try {
                scenario.onActivity { activity -> mic(activity).performClick() }
                waitUntil("the permission screen opens", 15_000L) { monitor.hits == 1 }
                values["permissionScreen"] = "pass"
                scenario.onActivity { activity ->
                    assertEquals(LiveDictationEnd.Failed(SpeechErrorCodes.PERMISSION_DENIED, heard = false), activity.lastLiveDictationEnd)
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            waitUntil("the maintainer allowed the microphone", 180_000L) {
                var granted = false
                scenario.onActivity { activity -> granted = activity.macawSpeechForTest?.status == MacawSpeechStatus.AVAILABLE }
                granted
            }
            events.clear()
            scenario.onActivity { activity -> mic(activity).performClick() }
            waitUntil("the next press is ready", 15_000L) { events.any { it.first == "ready" } }
            scenario.onActivity { activity -> mic(activity).performClick() }
            waitUntil("it ends", 10_000L) { events.any { it.first == "end" } }
            values["afterGrant"] = "pass"
        }
        SpeechDeviceReport.receipt("live-permission", values)
    }

    @Test
    fun busyMicrophoneFallsBackToTheSheet() {
        assumeTrue("needs another app's resident listener", arguments.getString("busy") == "true")
        environment.launchChat().use { scenario ->
            awaitProbe(scenario)
            assumeTrue("3-Macaw Speech does not dictate live here", liveAvailable(scenario))
            val filter = IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            val monitor = Instrumentation.ActivityMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            instrumentation.addMonitor(monitor)
            try {
                var sheetsBefore = 0
                scenario.onActivity { activity ->
                    sheetsBefore = activity.dictationRequestCount
                    mic(activity).performClick()
                }
                waitUntil("SESSION_BUSY opens the sheet", 15_000L) { monitor.hits == 1 }
                scenario.onActivity { activity ->
                    assertEquals(LiveDictationEnd.Failed(SpeechErrorCodes.SESSION_BUSY, heard = false), activity.lastLiveDictationEnd)
                    assertEquals(sheetsBefore + 1, activity.dictationRequestCount)
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            values["sessionBusySheet"] = "pass"
        }
        SpeechDeviceReport.receipt("live-busy", values)
    }

    private fun observe(scenario: ActivityScenario<ChatActivity>) {
        events.clear()
        finals.clear()
        events += "press" to SystemClock.elapsedRealtime()
        scenario.onActivity { activity ->
            activity.liveDictationObserverForTest = object : LiveDictationListener {
                override fun onReady() {
                    events += "ready" to SystemClock.elapsedRealtime()
                }

                override fun onPartial(text: String) {
                    events += "partial" to SystemClock.elapsedRealtime()
                }

                override fun onFinal(text: String) {
                    val now = SystemClock.elapsedRealtime()
                    events += "final" to now
                    if (text.isNotBlank()) finals += text to now
                }

                override fun onEnd(end: LiveDictationEnd) {
                    events += "end" to SystemClock.elapsedRealtime()
                }
            }
        }
    }

    private fun awaitProbe(scenario: ActivityScenario<ChatActivity>) {
        waitUntil("the chat probed 3-Macaw Speech", 8_000L) {
            var probed = false
            scenario.onActivity { activity -> probed = activity.macawSpeechForTest != null }
            probed
        }
    }

    private fun liveAvailable(scenario: ActivityScenario<ChatActivity>): Boolean {
        var live = false
        scenario.onActivity { activity -> live = activity.liveDictationAvailableForTest }
        return live
    }

    private fun setEmptyComposer(scenario: ActivityScenario<ChatActivity>) {
        scenario.onActivity { activity -> composer(activity).setText("") }
    }

    private fun playMillis() = 2 * SENTENCE_MILLIS + PAUSE_MILLIS + TAIL_MILLIS

    /** en.wav, 2.5 s of silence, en.wav and 1 s of silence as one WAV in the cache. */
    private fun twoSentences(): File {
        val pcm = instrumentation.context.assets.open("test-audio/en.wav").use { it.readBytes() }.copyOfRange(44, 44 + 212_000)
        val samples = pcm + ByteArray((PAUSE_MILLIS * 32).toInt()) + pcm + ByteArray((TAIL_MILLIS * 32).toInt())
        val file = File(environment.context.cacheDir, "live-dictation-two-sentences.wav")
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            fun int(value: Int) = out.write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
            fun short(value: Int) = out.write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
            out.write("RIFF".toByteArray()); int(36 + samples.size); out.write("WAVEfmt ".toByteArray())
            int(16); short(1); short(1); int(16_000); int(32_000); short(2); short(16)
            out.write("data".toByteArray()); int(samples.size); out.write(samples)
        }
        return file
    }

    /** Plays through the phone's own speaker at the media volume, raised to [minVolume] percent only when lower. */
    private class Speaker(context: Context, private val minVolume: Int) : AutoCloseable {
        private val audio = context.getSystemService(AudioManager::class.java)
        private val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        private var player: MediaPlayer? = null
        var used = "$volume/$max"
            private set

        init {
            if (volume * 100 < max * minVolume) {
                val raised = (max * minVolume + 99) / 100
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, raised, 0)
                used = "$raised/$max (raised from $volume)"
            }
        }

        /** Starts [file] and returns the time playback started. */
        fun play(file: File): Long {
            player?.release()
            val started = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            player = started
            return SystemClock.elapsedRealtime()
        }

        override fun close() {
            player?.release()
            player = null
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        }
    }

    private object TestAudio {
        const val EN_REFERENCE =
            "AFTER EARLY NIGHTFALL THE YELLOW LAMPS WOULD LIGHT UP HERE AND THERE THE SQUALID QUARTER OF THE BROTHELS"
    }

    private companion object {
        const val SENTENCE_MILLIS = 6_625L
        const val PAUSE_MILLIS = 2_500L
        const val TAIL_MILLIS = 1_000L

        /** Through a phone's speaker into its own microphone; 3-Macaw Speech reaches about 0.05 on the file itself. */
        const val MAXIMUM_CER = 0.5

        /** Character error rate, ignoring case, white space and punctuation (as 3-Macaw Speech's device tests). */
        fun cer(reference: String, hypothesis: String): Double {
            fun normalize(text: String) = text.uppercase().filter { it.isLetterOrDigit() }
            val r = normalize(reference)
            val h = normalize(hypothesis)
            if (r.isEmpty()) return if (h.isEmpty()) 0.0 else 1.0
            var previous = IntArray(h.length + 1) { it }
            for (i in 1..r.length) {
                val current = IntArray(h.length + 1)
                current[0] = i
                for (j in 1..h.length) {
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (r[i - 1] == h[j - 1]) 0 else 1)
                }
                previous = current
            }
            return previous[h.length].toDouble() / r.length
        }
    }
}
