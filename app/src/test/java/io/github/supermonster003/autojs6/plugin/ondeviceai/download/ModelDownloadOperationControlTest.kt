package io.github.supermonster003.autojs6.plugin.ondeviceai.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

class ModelDownloadOperationControlTest {
    @Test
    fun acceptedCancellationClosesEveryRegisteredResourceOnce() {
        val control = ModelDownloadOperationControl()
        val closed = AtomicInteger()
        control.registerCancellationResource(Closeable { closed.incrementAndGet() })
        control.registerCancellationResource(Closeable { closed.incrementAndGet() })

        assertTrue(control.requestCancellation())
        assertFalse(control.requestCancellation())
        control.closeCancellationResources()
        control.closeCancellationResources()

        assertEquals(2, closed.get())
        assertThrows(InterruptedException::class.java, control::ensureActive)
    }

    @Test
    fun resourceRegisteredAfterCancellationIsClosedAndRejected() {
        val control = ModelDownloadOperationControl()
        val closed = AtomicInteger()
        assertTrue(control.requestCancellation())

        assertThrows(InterruptedException::class.java) {
            control.registerCancellationResource(Closeable { closed.incrementAndGet() })
        }

        assertEquals(1, closed.get())
    }

    @Test
    fun progressIsPublishedOnlyWhileActive() {
        val seen = mutableListOf<ModelDownloadProgress>()
        val control = ModelDownloadOperationControl(seen::add)
        val progress = ModelDownloadProgress(8L, 16L)

        control.reportProgress(progress)
        assertEquals(listOf(progress), seen)
        control.requestCancellation()
        assertThrows(InterruptedException::class.java) { control.reportProgress(progress) }
    }
}
