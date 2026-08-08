package io.github.supermonster003.autojs6.plugin.ai.text.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CallbackQuiescenceGateTest {
    @Test
    fun closeWaitsForInflightCallbackAndSuppressesLaterDelivery() {
        val gate = CallbackQuiescenceGate()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstDelivered = AtomicBoolean(false)
        val callbackThread = Thread {
            gate.runCallback {
                entered.countDown()
                release.await()
                firstDelivered.set(true)
            }
        }
        callbackThread.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))

        gate.stopDelivering()
        val sealed = AtomicBoolean(false)
        val closeThread = Thread {
            gate.awaitQuiescenceAndSeal()
            sealed.set(true)
        }
        closeThread.start()
        Thread.sleep(50L)
        assertFalse(sealed.get())
        release.countDown()
        callbackThread.join(2_000L)
        closeThread.join(2_000L)
        assertTrue(firstDelivered.get())
        assertTrue(sealed.get())

        val lateDelivered = AtomicBoolean(false)
        gate.runCallback { lateDelivered.set(true) }
        assertFalse(lateDelivered.get())
    }
}
