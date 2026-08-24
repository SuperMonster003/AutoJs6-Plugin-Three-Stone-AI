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
 */
internal class ReusableResourceCache<K, V : AutoCloseable>(
    private val scheduler: ScheduledExecutorService,
    private val idleTimeoutMillis: Long,
) : AutoCloseable {
    private val stateLock = ReentrantLock()
    private val closeFinished = stateLock.newCondition()
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
    }

    /**
     * Reuses the cached value only when [key] matches. A different key closes the old value before
     * [create] initializes its replacement.
     */
    fun acquire(key: K, create: () -> V): ResourceLease<V> {
        var displaced: V? = null
        var cachedLease: ResourceLease<V>? = null
        stateLock.withLock {
            awaitCloseLocked()
            check(!closed) { "Resource cache is closed" }
            check(!loading && activeLeaseId == null) { "Resource cache already has an active lease" }
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
            create()
        } catch (error: Throwable) {
            stateLock.withLock {
                loading = false
                if (!closed) evictWhenIdle = false
            }
            throw error
        }

        var createdLease: ResourceLease<V>? = null
        var closeCreated = false
        stateLock.withLock {
            check(loading) { "Resource cache load state was lost" }
            loading = false
            if (closed) {
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
}

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
