package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object BoundedExecutors {
    const val WORKER_QUEUE_CAPACITY = 32
    const val CALLBACK_QUEUE_CAPACITY = 128

    fun worker(): ThreadPoolExecutor = create(2, 2, WORKER_QUEUE_CAPACITY, "three-stone-ai-worker")

    fun callbacks(): ThreadPoolExecutor = create(1, 1, CALLBACK_QUEUE_CAPACITY, "three-stone-ai-callback")

    private fun create(
        coreThreads: Int,
        maximumThreads: Int,
        queueCapacity: Int,
        threadName: String,
    ) = ThreadPoolExecutor(
        coreThreads,
        maximumThreads,
        0L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(queueCapacity),
        { runnable -> Thread(runnable, threadName).apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
}
