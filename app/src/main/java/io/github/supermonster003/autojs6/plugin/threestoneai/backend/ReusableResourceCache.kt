package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Keeps at most one initialized resource and lends it to at most one caller at a time.
 *
 * The provider already enforces a single active generation session. A single-entry cache therefore
 * avoids retaining multiple model engines while still removing the repeated initialization cost for
 * consecutive requests that use the same model key.
 *
 * A caller that finds the resource lent or loading waits for it (D222): the chat closes a replaced
 * session and a summary checkpoint yields on other threads, so the next borrower usually arrives
 * before the previous one has returned the lease. The wait is bounded and stops early when the
 * borrower itself is cancelled.
 *
 * [beforeLoad] runs outside the lock right before every new value is created (not on a cache hit),
 * so the app's other process can give back its own copy of a large model first (D227).
 */
internal class ReusableResourceCache<K, V : AutoCloseable>(
    private val scheduler: ScheduledExecutorService,
    private val idleTimeoutMillis: Long,
    private val maximumWaitMillis: Long = DEFAULT_MAXIMUM_WAIT_MILLIS,
    private val beforeLoad: () -> Unit = {},
) : AutoCloseable {
    private val stateLock = ReentrantLock()
    private val closeFinished = stateLock.newCondition()
    private val leaseReturned = stateLock.newCondition()
    private var entry: Entry<K, V>? = null
    private var loading = false
    private var closing = false
    private var activeLeaseId: Long? = null
    private var nextLeaseId = 0L
    private var evictWhenIdle = false
    private var idleTaskGeneration = 0L
    private var idleTask: java.util.concurrent.Future<*>? = null
    private var closed = false

    init {
        require(idleTimeoutMillis > 0L) { "Resource cache idle timeout must be positive" }
        require(maximumWaitMillis >= 0L) { "Resource cache wait must not be negative" }
    }

    /**
     * Reuses the cached value only when [key] matches. A different key closes the old value before
     * [create] initializes its replacement.
     *
     * While another lease is active or a value is loading, waits until it is returned. Throws
     * [ResourceBusyException] when that takes longer than the cache's maximum wait, and
     * [ResourceWaitCancelledException] as soon as [shouldStop] returns true.
     */
    fun acquire(key: K, shouldStop: () -> Boolean = { false }, create: () -> V): ResourceLease<V> {
        var displaced: V? = null
        var cachedLease: ResourceLease<V>? = null
        stateLock.withLock {
            awaitCloseLocked()
            check(!closed) { "Resource cache is closed" }
            awaitLeaseLocked(shouldStop)
            check(!closed) { "Resource cache is closed" }
            cancelIdleTaskLocked()
            val current = entry
            if (current != null && current.key == key) {
                cachedLease = createLeaseLocked(current.value)
            } else {
                displaced = detachEntryLocked()
                loading = true
                evictWhenIdle = false
            }
        }
        cachedLease?.let { return it }

        closeDetached(displaced)
        val created = try {
            runCatching(beforeLoad)
            create()
        } catch (error: Throwable) {
            stateLock.withLock {
                loading = false
                if (!closed) evictWhenIdle = false
                leaseReturned.signalAll()
            }
            throw error
        }

        var createdLease: ResourceLease<V>? = null
        var closeCreated = false
        stateLock.withLock {
            check(loading) { "Resource cache load state was lost" }
            loading = false
            if (closed) {
                leaseReturned.signalAll()
                closeCreated = true
            } else {
                entry = Entry(key, created)
                createdLease = createLeaseLocked(created)
            }
        }
        if (closeCreated) {
            closeValueQuietly(created)
            throw IllegalStateException("Resource cache closed while loading a resource")
        }
        return checkNotNull(createdLease)
    }

    /** Releases the cached value now, or immediately after its active lease is returned. */
    fun evict() {
        val detached = stateLock.withLock {
            cancelIdleTaskLocked()
            if (loading || activeLeaseId != null) {
                evictWhenIdle = true
                null
            } else {
                evictWhenIdle = false
                detachEntryLocked()
            }
        }
        closeDetached(detached)
    }

    /**
     * Waits up to [timeoutMillis] until no value is cached, loading or closing, so its memory is
     * actually given back (D227). Returns false on timeout, on interruption, or when the value is
     * still lent after [evict] asked for it.
     */
    fun awaitEmpty(timeoutMillis: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxOf(timeoutMillis, 0L))
        return stateLock.withLock {
            try {
                while (entry != null || loading || closing) {
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0L) return@withLock false
                    val slice = minOf(remaining, TimeUnit.MILLISECONDS.toNanos(STOP_POLL_MILLIS))
                    if (closing) closeFinished.awaitNanos(slice) else leaseReturned.awaitNanos(slice)
                }
                true
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }
    }

    override fun close() {
        val detached = stateLock.withLock {
            if (closed) return
            closed = true
            cancelIdleTaskLocked()
            if (loading || activeLeaseId != null) {
                evictWhenIdle = true
                null
            } else {
                detachEntryLocked()
            }
        }
        closeDetached(detached)
    }

    private fun createLeaseLocked(value: V): ResourceLease<V> {
        val leaseId = Math.addExact(nextLeaseId, 1L).also { nextLeaseId = it }
        activeLeaseId = leaseId
        return ResourceLease(value) { reusable -> release(leaseId, reusable) }
    }

    private fun release(leaseId: Long, reusable: Boolean) {
        var detached: V? = null
        stateLock.withLock {
            if (activeLeaseId != leaseId) return
            activeLeaseId = null
            leaseReturned.signalAll()
            checkNotNull(entry) { "Resource cache entry disappeared while leased" }
            if (!reusable || evictWhenIdle || closed) {
                cancelIdleTaskLocked()
                evictWhenIdle = false
                detached = detachEntryLocked()
            } else {
                try {
                    scheduleIdleEvictionLocked()
                } catch (_: RejectedExecutionException) {
                    detached = detachEntryLocked()
                }
            }
        }
        closeDetached(detached)
    }

    private fun scheduleIdleEvictionLocked() {
        cancelIdleTaskLocked()
        val generation = Math.addExact(idleTaskGeneration, 1L).also { idleTaskGeneration = it }
        idleTask = scheduler.schedule(
            { evictIfStillIdle(generation) },
            idleTimeoutMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun evictIfStillIdle(generation: Long) {
        val detached = stateLock.withLock {
            if (
                generation != idleTaskGeneration || activeLeaseId != null || loading || closed
            ) {
                null
            } else {
                idleTask = null
                detachEntryLocked()
            }
        }
        closeDetached(detached)
    }

    private fun cancelIdleTaskLocked() {
        idleTaskGeneration = Math.addExact(idleTaskGeneration, 1L)
        idleTask?.cancel(false)
        idleTask = null
    }

    private fun awaitCloseLocked() {
        try {
            while (closing) closeFinished.await()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for a cached resource to close", error)
        }
    }

    /** Waits until no lease is active and nothing is loading; closing is awaited again afterwards. */
    private fun awaitLeaseLocked(shouldStop: () -> Boolean) {
        if (!loading && activeLeaseId == null) return
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maximumWaitMillis)
        try {
            while (loading || activeLeaseId != null || closing) {
                if (closed) return
                if (shouldStop()) throw ResourceWaitCancelledException()
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) throw ResourceBusyException(maximumWaitMillis)
                // Polls [shouldStop] at least every 50 ms; a returned lease wakes the wait at once.
                val slice = minOf(remaining, TimeUnit.MILLISECONDS.toNanos(STOP_POLL_MILLIS))
                if (closing) closeFinished.awaitNanos(slice) else leaseReturned.awaitNanos(slice)
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for a cached resource", error)
        }
    }

    private fun detachEntryLocked(): V? {
        val detached = entry.also { entry = null }?.value
        if (detached != null) {
            check(!closing) { "Another cached resource is already closing" }
            closing = true
        }
        return detached
    }

    private fun closeDetached(value: V?) {
        value ?: return
        try {
            closeValueQuietly(value)
        } finally {
            stateLock.withLock {
                check(closing) { "Cached resource close state was lost" }
                closing = false
                closeFinished.signalAll()
            }
        }
    }

    private fun closeValueQuietly(value: V) = runCatching(value::close)

    private data class Entry<K, V>(val key: K, val value: V)

    companion object {
        /** Covers the previous borrower loading a large model before it notices its cancellation. */
        const val DEFAULT_MAXIMUM_WAIT_MILLIS = 60_000L
        private const val STOP_POLL_MILLIS = 50L
    }
}

/** The resource stayed lent or loading for longer than the cache's maximum wait (D222). */
internal class ResourceBusyException(waitedMillis: Long) :
    IllegalStateException("Resource is still in use after waiting $waitedMillis ms")

/** The borrower was cancelled while it waited for the resource; nothing was lent. */
internal class ResourceWaitCancelledException : IllegalStateException("Stopped waiting for the resource")

/** A single-use handle that can mark a failed resource as unsafe to cache before release. */
internal class ResourceLease<V : AutoCloseable>(
    val value: V,
    private val release: (reusable: Boolean) -> Unit,
) : AutoCloseable {
    private val reusable = AtomicBoolean(true)
    private val released = AtomicBoolean(false)

    fun invalidate() {
        reusable.set(false)
    }

    override fun close() {
        if (released.compareAndSet(false, true)) release(reusable.get())
    }
}
