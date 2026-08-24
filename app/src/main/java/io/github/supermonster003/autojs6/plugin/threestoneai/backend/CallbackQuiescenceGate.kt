package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Prevents native resource close from racing a listener callback already executing in Java. */
internal class CallbackQuiescenceGate {
    private val lock = ReentrantLock()
    private val quiescent = lock.newCondition()
    private var activeCallbacks = 0
    private var closing = false
    private var sealed = false

    fun runCallback(block: () -> Unit) {
        val entered = lock.withLock {
            if (sealed) return
            activeCallbacks += 1
            !closing
        }
        try {
            if (entered) block()
        } finally {
            lock.withLock {
                activeCallbacks -= 1
                check(activeCallbacks >= 0)
                if (activeCallbacks == 0) quiescent.signalAll()
            }
        }
    }

    fun stopDelivering() {
        lock.withLock { closing = true }
    }

    fun awaitQuiescenceAndSeal() {
        lock.withLock {
            closing = true
            while (activeCallbacks != 0) quiescent.awaitUninterruptibly()
            sealed = true
        }
    }
}
