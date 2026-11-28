package io.github.supermonster003.autojs6.plugin.threestoneai.backend

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

    /**
     * F2 (3-stone-voice-1c): the chat closes a replaced session on its own thread and immediately
     * opens the next one; the next borrower used to fail with "already has an active lease".
     */
    @Test
    fun borrowerWaitsForTheLeaseAnotherThreadIsStillReturning() {
        val cache = newCache()
        val resource = FakeResource()
        val previous = cache.acquire("sha-a") { resource }
        val returner = Thread {
            Thread.sleep(150L)
            previous.close()
        }
        returner.start()

        val startedNanos = System.nanoTime()
        val next = cache.acquire("sha-a") { error("same key must be reused") }

        assertSame(resource, next.value)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos) >= 100L)
        returner.join(2_000L)
        next.close()
        cache.close()
        assertEquals(1, resource.closeCount)
    }

    @Test
    fun borrowerWaitsForAValueThatIsStillLoading() {
        val cache = newCache()
        val resource = FakeResource()
        val loading = CountDownLatch(1)
        val finishLoading = CountDownLatch(1)
        val first = AtomicReference<ResourceLease<FakeResource>>()
        val loader = Thread {
            first.set(
                cache.acquire("sha-a") {
                    loading.countDown()
                    check(finishLoading.await(2L, TimeUnit.SECONDS))
                    resource
                },
            )
            Thread.sleep(50L)
            first.get().close()
        }
        loader.start()
        assertTrue(loading.await(2L, TimeUnit.SECONDS))
        finishLoading.countDown()

        val next = cache.acquire("sha-a") { error("the loaded value must be reused") }

        assertSame(resource, next.value)
        loader.join(2_000L)
        next.close()
        cache.close()
    }

    @Test
    fun borrowerGivesUpWithBusyAfterTheMaximumWait() {
        val cache = ReusableResourceCache<String, FakeResource>(
            newScheduler(),
            idleTimeoutMillis = TimeUnit.DAYS.toMillis(1L),
            maximumWaitMillis = 100L,
        )
        val holder = cache.acquire("sha-a") { FakeResource() }

        val startedNanos = System.nanoTime()
        val failure = runCatching { cache.acquire("sha-a") { error("must not load") } }.exceptionOrNull()

        assertTrue(failure is ResourceBusyException)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos) >= 100L)
        // The holder keeps its lease; returning it makes the resource available again.
        holder.close()
        cache.acquire("sha-a") { error("same key must be reused") }.close()
        cache.close()
    }

    @Test
    fun cancelledBorrowerStopsWaitingWithoutTakingTheLease() {
        val cache = newCache()
        val resource = FakeResource()
        val holder = cache.acquire("sha-a") { resource }
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>()
        val waiter = Thread {
            failure.set(runCatching { cache.acquire("sha-a", shouldStop = stop::get) { FakeResource() } }.exceptionOrNull())
        }
        waiter.start()
        Thread.sleep(100L)
        assertTrue(waiter.isAlive)

        stop.set(true)
        waiter.join(1_000L)

        assertFalse(waiter.isAlive)
        assertTrue(failure.get() is ResourceWaitCancelledException)
        holder.close()
        val next = cache.acquire("sha-a") { error("same key must be reused") }
        assertSame(resource, next.value)
        next.close()
        cache.close()
    }

    @Test
    fun `the load hook runs before each new value but not on a cache hit (D227)`() {
        val events = mutableListOf<String>()
        val cache = ReusableResourceCache<String, FakeResource>(
            newScheduler(),
            idleTimeoutMillis = TimeUnit.DAYS.toMillis(1L),
            beforeLoad = { events += "before-load" },
        )

        cache.acquire("sha-a") { FakeResource().also { events += "create-a" } }.close()
        cache.acquire("sha-a") { error("same key must be reused") }.close()
        cache.evict()
        cache.acquire("sha-a") { FakeResource().also { events += "create-a" } }.close()
        cache.acquire("sha-b") { FakeResource().also { events += "create-b" } }.close()

        assertEquals(
            listOf("before-load", "create-a", "before-load", "create-a", "before-load", "create-b"),
            events,
        )
        cache.close()
    }

    @Test
    fun `a failing load hook does not prevent the load (D227)`() {
        val cache = ReusableResourceCache<String, FakeResource>(
            newScheduler(),
            idleTimeoutMillis = TimeUnit.DAYS.toMillis(1L),
            beforeLoad = { throw IllegalStateException("no answer") },
        )
        val resource = FakeResource()

        cache.acquire("sha-a") { resource }.use { lease -> assertSame(resource, lease.value) }
        cache.close()
    }

    @Test
    fun `awaitEmpty reports the memory as given back only once an evicted lease is returned and closed (D227)`() {
        val cache = newCache()
        val resource = FakeResource()
        assertTrue("an empty cache holds nothing", cache.awaitEmpty(0L))

        val lease = cache.acquire("sha-a") { resource }
        cache.evict()
        assertFalse("a lent value is kept until it is returned", cache.awaitEmpty(100L))
        assertEquals(0, resource.closeCount)

        val returner = Thread {
            Thread.sleep(100L)
            lease.close()
        }.apply { start() }
        assertTrue("the returned value is closed at once", cache.awaitEmpty(2_000L))
        assertEquals(1, resource.closeCount)
        returner.join()

        cache.acquire("sha-a") { FakeResource() }.close()
        cache.evict()
        assertTrue("an idle value is closed by evict", cache.awaitEmpty(2_000L))
        cache.close()
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
