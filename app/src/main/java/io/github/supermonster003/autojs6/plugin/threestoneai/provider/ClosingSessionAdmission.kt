package io.github.supermonster003.autojs6.plugin.threestoneai.provider

/** One running session and at most one successor waiting for a terminal session to release resources. */
internal class ClosingSessionAdmission<T : Any>(private val isClosing: (T) -> Boolean) {
    private class Entry<T>(val session: T) {
        var ready: (() -> Unit)? = null
        var started = false
    }
    private val lock = Any()
    private var active: Entry<T>? = null
    private var waiting: Entry<T>? = null

    fun reserve(session: T): Boolean = synchronized(lock) {
        val current = active
        if (current == null) {
            active = Entry(session)
            true
        } else if (current.session !== session && waiting == null && isClosing(current.session)) {
            waiting = Entry(session)
            true
        } else false
    }

    fun whenReady(session: T, start: () -> Unit) {
        val action = synchronized(lock) {
            val entry = active?.takeIf { it.session === session }
                ?: waiting?.takeIf { it.session === session }
                ?: return
            if (entry.ready == null) entry.ready = start
            takeReadyLocked()
        }
        action?.invoke()
    }

    fun release(session: T) {
        val action = synchronized(lock) {
            when {
                active?.session === session -> { active = waiting; waiting = null }
                waiting?.session === session -> waiting = null
            }
            takeReadyLocked()
        }
        action?.invoke()
    }

    private fun takeReadyLocked(): (() -> Unit)? {
        val current = active ?: return null
        if (current.started) return null
        val action = current.ready ?: return null
        current.started = true
        return action
    }
}
