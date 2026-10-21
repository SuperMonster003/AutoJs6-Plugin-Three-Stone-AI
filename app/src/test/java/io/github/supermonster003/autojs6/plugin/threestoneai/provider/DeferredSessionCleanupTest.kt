package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DeferredSessionCleanupTest {
    @Test fun destructionClosesResourceEvenWhenTheQueuedTaskIsDropped() {
        val queue = mutableListOf<Runnable>()
        val cleanup = DeferredSessionCleanup(Executor(queue::add))
        var closes = 0
        cleanup.schedule(directly = false) { closes++ }
        assertEquals(0, closes)
        cleanup.schedule(directly = true) { error("The original cleanup must be retained") }
        queue.clear() // The service's shutdownNow discards its outstanding work.
        assertEquals(1, closes)
    }

    @Test fun aLateQueuedTaskCannotCloseTheResourceTwice() {
        val queue = mutableListOf<Runnable>()
        val cleanup = DeferredSessionCleanup(Executor(queue::add))
        var closes = 0
        cleanup.schedule(directly = false) { closes++ }
        cleanup.schedule(directly = true) { error("Duplicate cleanup") }
        queue.single().run()
        cleanup.schedule(directly = true) { error("Repeated destruction") }
        assertEquals(1, closes)
    }

    @Test fun destructionDoesNotRepeatAnAlreadyRunningBackendClose() {
        val worker = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val closes = AtomicInteger()
        val cleanup = DeferredSessionCleanup(worker)
        try {
            cleanup.schedule(directly = false) {
                closes.incrementAndGet()
                started.countDown()
                assertTrue(release.await(3, TimeUnit.SECONDS))
                finished.countDown()
            }
            assertTrue(started.await(3, TimeUnit.SECONDS))
            cleanup.schedule(directly = true) { error("Running cleanup duplicated") }
            release.countDown()
            assertTrue(finished.await(3, TimeUnit.SECONDS))
            assertEquals(1, closes.get())
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    @Test fun rejectionAfterExecutorShutdownStillReleasesTheResource() {
        val cleanup = DeferredSessionCleanup(Executor { throw RejectedExecutionException() })
        val closed = CountDownLatch(1)
        cleanup.schedule(directly = false) { closed.countDown() }
        assertTrue(closed.await(3, TimeUnit.SECONDS))
        assertTrue(cleanup.isScheduled)
    }
}
