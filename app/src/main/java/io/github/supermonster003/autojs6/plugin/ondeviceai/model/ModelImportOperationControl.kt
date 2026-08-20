package io.github.supermonster003.autojs6.plugin.ondeviceai.model

import java.io.Closeable

/**
 * Per-import cancellation and publication gate shared by the coordinator and repository.
 *
 * Cancellation may win while source work or an unpublished file transaction is in progress. Once
 * [beginCommit] succeeds, the durable metadata publication owns the terminal result and a late
 * cancellation request is rejected.
 */
internal class ModelImportOperationControl(
    private val progressListener: (ModelImportProgress) -> Unit = {},
) {
    private val lock = Any()
    private var state = State.ACTIVE
    private var cancellationResource: Closeable? = null

    fun requestCancellation(onAccepted: () -> Unit = {}): Boolean = synchronized(lock) {
        if (state != State.ACTIVE) return@synchronized false
        state = State.CANCELLED
        onAccepted()
        true
    }

    fun isCancellationRequested(): Boolean = synchronized(lock) { state == State.CANCELLED }

    /** Registers the source read that cancellation may close to unblock a provider-backed stream. */
    @Throws(InterruptedException::class)
    fun registerCancellationResource(resource: Closeable) {
        val rejected = synchronized(lock) {
            check(cancellationResource == null) { "A cancellation resource is already registered" }
            when (state) {
                State.ACTIVE -> {
                    cancellationResource = resource
                    false
                }
                State.CANCELLED -> true
                State.COMMITTING -> error("A source resource cannot be registered after commit begins")
            }
        }
        if (rejected) {
            runCatching { resource.close() }
            throw InterruptedException("Model import was cancelled before source reading")
        }
    }

    fun unregisterCancellationResource(resource: Closeable) {
        synchronized(lock) {
            if (cancellationResource === resource) cancellationResource = null
        }
    }

    /** Closes outside [lock] so cancellation never holds the commit gate across provider I/O. */
    fun closeCancellationResource() {
        val resource = synchronized(lock) { cancellationResource }
        runCatching { resource?.close() }
    }

    @Throws(InterruptedException::class)
    fun ensureActive() {
        if (Thread.currentThread().isInterrupted || isCancellationRequested()) {
            throw InterruptedException("Model import was cancelled")
        }
    }

    @Throws(InterruptedException::class)
    fun reportProgress(progress: ModelImportProgress) {
        ensureActive()
        progressListener(progress)
        ensureActive()
    }

    /** Checks cancellation around a rollback-safe filesystem step without holding the UI gate. */
    @Throws(InterruptedException::class)
    fun <T> whileActive(block: () -> T): T {
        ensureActive()
        return block().also { ensureActive() }
    }

    @Throws(InterruptedException::class)
    fun beginCommit() {
        synchronized(lock) {
            if (Thread.currentThread().isInterrupted || state == State.CANCELLED) {
                throw InterruptedException("Model import was cancelled before publication")
            }
            check(state == State.ACTIVE) { "Model import publication was already committed" }
            state = State.COMMITTING
        }
    }

    private enum class State {
        ACTIVE,
        CANCELLED,
        COMMITTING,
    }
}
