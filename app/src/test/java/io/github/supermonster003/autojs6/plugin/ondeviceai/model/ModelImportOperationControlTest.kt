package io.github.supermonster003.autojs6.plugin.ondeviceai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

class ModelImportOperationControlTest {
    @Test
    fun cancellationBeforeCommitOwnsTheTerminalDecision() {
        val control = ModelImportOperationControl()
        var acceptedCallbacks = 0

        assertTrue(control.requestCancellation { acceptedCallbacks += 1 })
        assertFalse(control.requestCancellation { acceptedCallbacks += 1 })
        assertEquals(1, acceptedCallbacks)
        assertThrows(InterruptedException::class.java) { control.ensureActive() }
        assertThrows(InterruptedException::class.java) { control.beginCommit() }

        var closeCount = 0
        val queuedSource = Closeable { closeCount += 1 }
        assertThrows(InterruptedException::class.java) {
            control.registerCancellationResource(queuedSource)
        }
        assertEquals(1, closeCount)
    }

    @Test
    fun commitBeforeCancellationRejectsTheLateRequest() {
        val control = ModelImportOperationControl()

        control.beginCommit()

        assertFalse(control.requestCancellation())
        control.ensureActive()
    }
}
