package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

internal class SerialCallbackLane : Closeable {
    private val executor: ExecutorService = BoundedExecutors.callbacks()

    fun dispatch(callback: () -> Unit, onFailure: (Throwable) -> Unit) {
        try {
            executor.execute { runCatching(callback).onFailure(onFailure) }
        } catch (error: RejectedExecutionException) {
            onFailure(error)
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
