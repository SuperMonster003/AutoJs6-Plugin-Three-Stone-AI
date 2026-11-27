package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * The recording the acoustic live dictation test plays (roadmap D214), in the androidTest assets only, never in the
 * APK. It is byte-identical to the `en.wav` that 3-Macaw Speech pins in its `locks/test-audio.lock`: the member
 * `test_wavs/en_0.wav` of the sherpa-onnx archive `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2`
 * (<https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models>), a LibriSpeech recording (CC BY 4.0) whose
 * transcript is [EN_REFERENCE]. 16 kHz mono PCM16, 6.625 s.
 */
class TestAudioLockTest {
    @Test
    fun `the english recording is the pinned one`() {
        val file = projectRoot().resolve("app/src/androidTest/assets/test-audio/en.wav")
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        assertEquals("6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f", digest)
        assertEquals(212_044L, Files.size(file))
    }

    private fun projectRoot(): Path = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
        .first { path -> Files.isDirectory(path.resolve("app/src/main")) }

    companion object {
        const val EN_REFERENCE =
            "AFTER EARLY NIGHTFALL THE YELLOW LAMPS WOULD LIGHT UP HERE AND THERE THE SQUALID QUARTER OF THE BROTHELS"
    }
}
