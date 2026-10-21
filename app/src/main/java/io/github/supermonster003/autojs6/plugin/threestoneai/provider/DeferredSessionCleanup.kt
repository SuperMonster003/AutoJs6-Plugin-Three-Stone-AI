package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Service destruction can take over queued cleanup before its executor drops pending work. */
internal class DeferredSessionCleanup(private val worker: Executor) {
    private val pending = AtomicReference<Runnable?>()
    private val started = AtomicBoolean(false)

    val isScheduled: Boolean get() = pending.get() != null

    fun schedule(directly: Boolean, action: () -> Unit) {
        val candidate = Runnable { if (started.compareAndSet(false, true)) action() }
        val first = pending.compareAndSet(null, candidate)
        val selected = if (first) candidate else checkNotNull(pending.get())
        if (directly) {
            selected.run()
        } else if (first) {
            try {
                worker.execute(selected)
            } catch (_: RejectedExecutionException) {
                Thread(selected, "three-stone-ai-backend-close").apply { isDaemon = true }.start()
            }
        }
    }
}
