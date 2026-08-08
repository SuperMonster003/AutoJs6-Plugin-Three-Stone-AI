package io.github.supermonster003.autojs6.plugin.ai.text.provider

internal class AiTextSessionState {
    enum class State { CREATED, STARTED, TERMINAL, CLOSED }
    enum class Terminal { COMPLETED, FAILED, CANCELLED }

    var state: State = State.CREATED
        private set
    var terminal: Terminal? = null
        private set

    val isActive: Boolean
        @Synchronized get() = state == State.CREATED || state == State.STARTED

    @Synchronized
    fun start() {
        check(state == State.CREATED) { "AI text session start is duplicated or out of order" }
        state = State.STARTED
    }

    @Synchronized
    fun complete(): Boolean {
        check(state == State.STARTED) { "AI text session has not started" }
        return terminal(Terminal.COMPLETED)
    }

    @Synchronized
    fun fail(): Boolean {
        check(state != State.CLOSED) { "AI text session is closed" }
        return terminal(Terminal.FAILED)
    }

    @Synchronized
    fun cancel(): Boolean {
        if (!isActive) return false
        return terminal(Terminal.CANCELLED)
    }

    @Synchronized
    fun close(): Boolean {
        if (state == State.CLOSED) return false
        state = State.CLOSED
        return true
    }

    private fun terminal(value: Terminal): Boolean {
        if (terminal != null) return false
        terminal = value
        state = State.TERMINAL
        return true
    }
}
