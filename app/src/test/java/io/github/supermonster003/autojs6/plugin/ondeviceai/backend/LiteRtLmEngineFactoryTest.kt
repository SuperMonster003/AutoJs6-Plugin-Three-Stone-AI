package io.github.supermonster003.autojs6.plugin.ondeviceai.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmEngineFactoryTest {
    @Test
    fun successfulProbeInitializesOnceAndAlwaysCloses() {
        val resource = FakeResource()

        initializeAndClose(
            create = { resource },
            initialize = { it.initializeCount++ },
        )

        assertEquals(1, resource.initializeCount)
        assertEquals(1, resource.closeCount)
    }

    @Test
    fun failedInitializationStillClosesAndPreservesTheInitializationFailure() {
        val resource = FakeResource()
        val initializationFailure = IllegalStateException("incompatible")

        val thrown = assertThrows(IllegalStateException::class.java) {
            initializeAndClose(
                create = { resource },
                initialize = {
                    it.initializeCount++
                    throw initializationFailure
                },
            )
        }

        assertTrue(thrown === initializationFailure)
        assertEquals(1, resource.initializeCount)
        assertEquals(1, resource.closeCount)
    }

    @Test
    fun closeFailureIsSuppressedBehindAnInitializationFailure() {
        val initializationFailure = IllegalArgumentException("initialize")
        val closeFailure = IllegalStateException("close")
        val resource = FakeResource(closeFailure)

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            initializeAndClose(
                create = { resource },
                initialize = { throw initializationFailure },
            )
        }

        assertTrue(thrown === initializationFailure)
        assertEquals(listOf(closeFailure), thrown.suppressed.toList())
        assertEquals(1, resource.closeCount)
    }

    private class FakeResource(
        private val closeFailure: Throwable? = null,
    ) : AutoCloseable {
        var initializeCount = 0
        var closeCount = 0

        override fun close() {
            closeCount++
            closeFailure?.let { throw it }
        }
    }
}
