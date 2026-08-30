package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTokenCalibrationTest {
    @Test
    fun `ema converges toward repeated target observations`() {
        var calibration = ContextTokenCalibration()
        val observation = ContextTokenCalibrationObservation(
            utf8Bytes = 1_000L,
            messageCount = 2,
            actualInputTokens = 258L,
        )

        repeat(40) {
            calibration = ContextTokenCalibrationPolicy.update(calibration, observation)
        }

        assertEquals(0.25, calibration.tokensPerUtf8Byte, 0.0001)
        assertEquals(40L, calibration.sampleCount)
    }

    @Test
    fun `observed coefficient is always clamped to calibration band`() {
        var low = ContextTokenCalibration()
        var high = ContextTokenCalibration()
        repeat(100) {
            low = ContextTokenCalibrationPolicy.update(
                low,
                ContextTokenCalibrationObservation(1_000L, 0, 1L),
            )
            high = ContextTokenCalibrationPolicy.update(
                high,
                ContextTokenCalibrationObservation(1L, 0, 10_000L),
            )
        }

        assertTrue(low.tokensPerUtf8Byte >= ContextPolicy.MINIMUM_TOKENS_PER_UTF8_BYTE)
        assertEquals(ContextPolicy.MINIMUM_TOKENS_PER_UTF8_BYTE, low.tokensPerUtf8Byte, 0.0001)
        assertTrue(high.tokensPerUtf8Byte <= ContextPolicy.MAXIMUM_TOKENS_PER_UTF8_BYTE)
        assertEquals(ContextPolicy.MAXIMUM_TOKENS_PER_UTF8_BYTE, high.tokensPerUtf8Byte, 0.0001)
    }

    @Test
    fun `missing usage and unusable empty samples leave calibration unchanged`() {
        val current = ContextTokenCalibration(tokensPerUtf8Byte = 0.33, sampleCount = 7L)

        assertSame(current, ContextTokenCalibrationPolicy.update(current, null))
        assertSame(
            current,
            ContextTokenCalibrationPolicy.update(
                current,
                ContextTokenCalibrationObservation(0L, 1, 4L),
            ),
        )
        assertSame(
            current,
            ContextTokenCalibrationPolicy.update(
                current,
                ContextTokenCalibrationObservation(100L, 1, 4L),
            ),
        )
    }

    @Test
    fun `store persists target scoped updates and reloads them`() {
        val persistence = FakePersistence()
        val firstStore = ContextTokenCalibrationStore(persistence)

        val updated = firstStore.record(
            "profile:model-a",
            ContextTokenCalibrationObservation(100L, 1, 34L),
        )
        val reloaded = ContextTokenCalibrationStore(persistence).current("profile:model-a")

        assertEquals(updated, reloaded)
        assertEquals(ContextTokenCalibration(), firstStore.current("profile:model-b"))
        assertEquals(1, persistence.saveCount)
    }

    @Test
    fun `store does not write when usage is absent`() {
        val persistence = FakePersistence()
        val store = ContextTokenCalibrationStore(persistence)

        val calibration = store.record("local:model", null)

        assertEquals(ContextTokenCalibration(), calibration)
        assertEquals(0, persistence.saveCount)
    }

    private class FakePersistence : ContextTokenCalibrationPersistence {
        private val values = HashMap<String, ContextTokenCalibration>()
        var saveCount = 0
            private set

        override fun load(targetId: String): ContextTokenCalibration? = values[targetId]

        override fun save(targetId: String, calibration: ContextTokenCalibration) {
            values[targetId] = calibration
            saveCount++
        }
    }
}
