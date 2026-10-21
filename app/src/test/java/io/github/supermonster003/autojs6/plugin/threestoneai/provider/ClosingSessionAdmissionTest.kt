package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import org.junit.Assert.*
import org.junit.Test

class ClosingSessionAdmissionTest {
    private class Session(var closing: Boolean = false)

    @Test fun aLiveSessionStillRejectsConcurrentInference() {
        val gate = ClosingSessionAdmission<Session> { it.closing }
        val first = Session()
        assertTrue(gate.reserve(first))
        assertFalse(gate.reserve(Session()))
        assertFalse(gate.reserve(first))
    }

    @Test fun successorStartsOnlyAfterNativeCleanupReleasesThePredecessor() {
        val gate = ClosingSessionAdmission<Session> { it.closing }
        val first = Session(); val next = Session(); val events = mutableListOf<String>()
        assertTrue(gate.reserve(first)); gate.whenReady(first) { events += "first" }
        first.closing = true
        assertTrue(gate.reserve(next)); gate.whenReady(next) { events += "next" }
        assertFalse(gate.reserve(Session()))
        assertEquals(listOf("first"), events)
        gate.release(first)
        gate.whenReady(next) { error("A ready callback must not run twice") }
        gate.release(first)
        assertEquals(listOf("first", "next"), events)
    }

    @Test fun cancellingOrTimingOutTheWaiterDoesNotReleaseTheClosingNativeSession() {
        val gate = ClosingSessionAdmission<Session> { it.closing }
        val first = Session(true); val cancelled = Session(); val replacement = Session()
        assertTrue(gate.reserve(first)); assertTrue(gate.reserve(cancelled))
        gate.whenReady(cancelled) { error("Cancelled request ran") }
        gate.release(cancelled)
        assertTrue(gate.reserve(replacement))
        var started = false
        gate.whenReady(replacement) { started = true }
        assertFalse(started)
        gate.release(first)
        assertTrue(started)
    }

    @Test fun cleanupMayFinishBeforeTheSuccessorRegistersItsReadyCallback() {
        val gate = ClosingSessionAdmission<Session> { it.closing }
        val first = Session(true); val next = Session()
        assertTrue(gate.reserve(first)); assertTrue(gate.reserve(next))
        gate.release(first)
        var started = false
        gate.whenReady(next) { started = true; gate.release(next) }
        assertTrue(started)
        assertTrue(gate.reserve(Session()))
    }

    @Test fun rejectedOrLateSessionsCannotReleaseAnUnrelatedReservation() {
        val gate = ClosingSessionAdmission<Session> { it.closing }
        val first = Session(true); val next = Session(); val rejected = Session()
        assertTrue(gate.reserve(first)); assertTrue(gate.reserve(next)); assertFalse(gate.reserve(rejected))
        var started = false
        gate.whenReady(next) { started = true }
        gate.release(rejected)
        assertFalse(started)
        assertFalse(gate.reserve(Session()))
        gate.release(next)
        gate.whenReady(next) { error("Released waiter ran") }
        gate.release(first)
        assertFalse(started)
    }
}
