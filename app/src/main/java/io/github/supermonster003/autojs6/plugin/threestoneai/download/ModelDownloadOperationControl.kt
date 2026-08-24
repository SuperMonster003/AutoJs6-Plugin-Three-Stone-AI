package io.github.supermonster003.autojs6.plugin.threestoneai.download

import java.io.Closeable

internal class ModelDownloadOperationControl(
    private val progressListener: (ModelDownloadProgress) -> Unit = {},
) {
    private val lock = Any()
    private val resources = CancellationResourceGroup()
    private var cancelled = false

    fun requestCancellation(onAccepted: () -> Unit = {}): Boolean = synchronized(lock) {
        if (cancelled) return@synchronized false
        cancelled = true
        onAccepted()
        true
    }

    fun isCancellationRequested(): Boolean = synchronized(lock) { cancelled }

    @Throws(InterruptedException::class)
    fun registerCancellationResource(resource: Closeable) {
        val rejected = synchronized(lock) {
            if (cancelled) true else resources.add(resource).let { false }
        }
        if (rejected) {
            runCatching { resource.close() }
            throw InterruptedException("Model download was cancelled")
        }
    }

    fun closeCancellationResources() {
        resources.close()
    }

    @Throws(InterruptedException::class)
    fun ensureActive() {
        if (Thread.currentThread().isInterrupted || isCancellationRequested()) {
            throw InterruptedException("Model download was cancelled")
        }
    }

    @Throws(InterruptedException::class)
    fun reportProgress(progress: ModelDownloadProgress) {
        ensureActive()
        progressListener(progress)
        ensureActive()
    }

    private class CancellationResourceGroup : Closeable {
        private val lock = Any()
        private val resources = mutableListOf<Closeable>()
        private var closed = false

        fun add(resource: Closeable) {
            val closeImmediately = synchronized(lock) {
                if (closed) true else false.also { resources += resource }
            }
            if (closeImmediately) runCatching { resource.close() }
        }

        override fun close() {
            val pending = synchronized(lock) {
                if (closed) return
                closed = true
                resources.toList().also { resources.clear() }
            }
            pending.asReversed().forEach { resource -> runCatching { resource.close() } }
        }
    }
}
