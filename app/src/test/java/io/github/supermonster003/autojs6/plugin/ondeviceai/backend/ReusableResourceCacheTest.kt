package io.github.supermonster003.autojs6.plugin.ondeviceai.backend

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ReusableResourceCacheTest {
    private val schedulers = mutableListOf<ScheduledExecutorService>()

    @After
    fun tearDown() {
        schedulers.forEach(ScheduledExecutorService::shutdownNow)
    }

    @Test
    fun consecutiveLeasesWithSameKeyReuseInitializedResource() {
        val cache = newCache()
        val resource = FakeResource()

        cache.acquire("sha-a") { resource }.close()
        val reused = cache.acquire("sha-a") { error("same key must not be recreated") }

        assertSame(resource, reused.value)
        assertEquals(0, resource.closeCount)
        reused.close()
        cache.close()
        assertEquals(1, resource.closeCount)
    }

    @Test
    fun differentKeyClosesPreviousResourceBeforeCreatingReplacement() {
        val cache = newCache()
        val first = FakeResource()
        val second = FakeResource()

        cache.acquire("sha-a") { first }.close()
        val replacement = cache.acquire("sha-b") {
            assertEquals(1, first.closeCount)
            second
        }

        assertSame(second, replacement.value)
        replacement.close()
        cache.close()
        assertEquals(1, second.closeCount)
    }

    @Test
    fun evictionDuringActiveLeaseWaitsForSafeReleasePoint() {
        val cache = newCache()
        val resource = FakeResource()
        val lease = cache.acquire("sha-a") { resource }

        cache.evict()
        assertEquals(0, resource.closeCount)

        lease.close()
        assertEquals(1, resource.closeCount)
    }

    @Test
    fun invalidatedLeaseIsNotReused() {
        val cache = newCache()
        val failed = FakeResource()
        val lease = cache.acquire("sha-a") { failed }

        lease.invalidate()
        lease.close()
        assertEquals(1, failed.closeCount)

        val replacement = FakeResource()
        cache.acquire("sha-a") { replacement }.close()
        cache.close()
        assertEquals(1, replacement.closeCount)
    }

    @Test
    fun idleTimeoutClosesReleasedResource() {
        val scheduler = newScheduler()
        val cache = ReusableResourceCache<String, FakeResource>(scheduler, idleTimeoutMillis = 25L)
        val resource = FakeResource()

        cache.acquire("sha-a") { resource }.close()

        assertTrue(resource.closed.await(2L, TimeUnit.SECONDS))
        assertEquals(1, resource.closeCount)
        cache.close()
    }

    @Test
    fun reacquiringBeforeIdleTimeoutCancelsStaleEviction() {
        val scheduler = newScheduler()
        val cache = ReusableResourceCache<String, FakeResource>(scheduler, idleTimeoutMillis = 100L)
        val resource = FakeResource()

        cache.acquire("sha-a") { resource }.close()
        Thread.sleep(25L)
        val lease = cache.acquire("sha-a") { error("same key must remain cached") }
        Thread.sleep(125L)

        assertFalse(resource.closed.await(25L, TimeUnit.MILLISECONDS))
        lease.close()
        cache.close()
        assertEquals(1, resource.closeCount)
    }

    @Test
    fun acquireWaitsForTimedOutResourceToFinishClosing() {
        val scheduler = newScheduler()
        val cache = ReusableResourceCache<String, AutoCloseable>(scheduler, idleTimeoutMillis = 25L)
        val first = BlockingResource()
        cache.acquire("sha-a") { first }.close()
        assertTrue(first.closeStarted.await(2L, TimeUnit.SECONDS))

        val replacement = FakeResource()
        val replacementCreationStarted = CountDownLatch(1)
        val acquired = AtomicReference<ResourceLease<AutoCloseable>>()
        val acquireThread = Thread {
            acquired.set(
                cache.acquire("sha-a") {
                    replacementCreationStarted.countDown()
                    replacement
                },
            )
        }
        acquireThread.start()

        assertFalse(replacementCreationStarted.await(100L, TimeUnit.MILLISECONDS))
        first.allowClose.countDown()
        assertTrue(replacementCreationStarted.await(2L, TimeUnit.SECONDS))
        acquireThread.join(2_000L)
        assertFalse(acquireThread.isAlive)

        acquired.get().close()
        cache.close()
        assertEquals(1, replacement.closeCount)
    }

    private fun newCache(): ReusableResourceCache<String, FakeResource> =
        ReusableResourceCache(newScheduler(), idleTimeoutMillis = TimeUnit.DAYS.toMillis(1L))

    private fun newScheduler(): ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor().also(schedulers::add)

    private class FakeResource : AutoCloseable {
        val closed = CountDownLatch(1)
        var closeCount = 0
            private set

        override fun close() {
            closeCount += 1
            closed.countDown()
        }
    }

    private class BlockingResource : AutoCloseable {
        val closeStarted = CountDownLatch(1)
        val allowClose = CountDownLatch(1)

        override fun close() {
            closeStarted.countDown()
            check(allowClose.await(2L, TimeUnit.SECONDS)) { "Timed out waiting to finish fake close" }
        }
    }
}
