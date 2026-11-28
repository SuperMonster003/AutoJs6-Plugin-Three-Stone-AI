package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.SystemClock
import java.io.File
import java.io.RandomAccessFile

/**
 * The spoken question of the real-device voice rounds (P6-3): the first sentence of `en.wav` (the LibriSpeech file
 * that `TestAudioLockTest` pins) and a second of silence, written once to the cache.
 */
internal object VoiceTestAudio {
    const val SENTENCE_MILLIS = 6_625L
    private const val TAIL_MILLIS = 1_000L
    const val EN_REFERENCE =
        "AFTER EARLY NIGHTFALL THE YELLOW LAMPS WOULD LIGHT UP HERE AND THERE THE SQUALID QUARTER OF THE BROTHELS"

    fun sentence(testAssets: android.content.res.AssetManager, cache: File): File {
        val pcm = testAssets.open("test-audio/en.wav").use { it.readBytes() }.copyOfRange(44, 44 + 212_000)
        val samples = pcm + ByteArray((TAIL_MILLIS * 32).toInt())
        val file = File(cache, "voice-round-sentence.wav")
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            fun int(value: Int) =
                out.write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
            fun short(value: Int) = out.write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
            out.write("RIFF".toByteArray()); int(36 + samples.size); out.write("WAVEfmt ".toByteArray())
            int(16); short(1); short(1); int(16_000); int(32_000); short(2); short(16)
            out.write("data".toByteArray()); int(samples.size); out.write(samples)
        }
        return file
    }

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

/**
 * Plays through the phone's own speaker. Only while a file plays is the media volume raised to [minVolume] percent
 * (when lower); it is restored as soon as the file ends, so the replies are read at the volume the phone had (roadmap
 * D214). Main thread only.
 */
internal class VoiceRoundSpeaker(context: Context, private val minVolume: Int) : AutoCloseable {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private var player: MediaPlayer? = null

    /** The volume of the question while it plays, and the phone's own one. */
    val described: String
        get() = "${playbackVolume()}/$max while asking, $volume/$max while reading"

    private fun playbackVolume() = if (volume * 100 < max * minVolume) (max * minVolume + 99) / 100 else volume

    /** Starts [file] and returns the time playback started. */
    fun play(file: File): Long {
        player?.release()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, playbackVolume(), 0)
        val started = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            setDataSource(file.absolutePath)
            setOnCompletionListener { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) }
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
