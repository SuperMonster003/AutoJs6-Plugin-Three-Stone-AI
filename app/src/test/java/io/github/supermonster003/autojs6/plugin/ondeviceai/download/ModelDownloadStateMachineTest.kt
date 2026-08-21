package io.github.supermonster003.autojs6.plugin.ondeviceai.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadStateMachineTest {
    @Test
    fun retainsDestinationFlagsAndExactLongProgressThroughSuccess() {
        val machine = ModelDownloadStateMachine<String>()
        val operationId = checkNotNull(machine.begin(MODEL, "destination", 0x43))
        val progress = ModelDownloadProgress(3_000_000_000L, MODEL.expectedSizeBytes)

        assertTrue(machine.updateProgress(operationId, progress))
        val running = machine.snapshot() as ModelDownloadState.Running
        assertEquals("destination", running.destination)
        assertEquals(0x43, running.grantedFlags)
        assertEquals(progress, running.progress)
        assertTrue(machine.succeed(operationId))

        val succeeded = machine.snapshot() as ModelDownloadState.Succeeded
        assertEquals(operationId, succeeded.operationId)
        assertEquals("destination", succeeded.destination)
        assertEquals(0x43, succeeded.grantedFlags)
    }

    @Test
    fun staleOperationsCannotMutateOrCancelTheirSuccessor() {
        val machine = ModelDownloadStateMachine<String>()
        val first = checkNotNull(machine.begin(MODEL, "first", 0))
        assertTrue(machine.fail(
            first,
            ModelDownloadFailureReason.NETWORK_UNAVAILABLE,
            ModelDownloadCleanupResult.DELETED,
        ))
        val second = checkNotNull(machine.begin(MODEL, "second", 0))

        assertNotEquals(first, second)
        assertFalse(machine.beginCancellation(first))
        assertFalse(machine.updateProgress(first, ModelDownloadProgress(8L, MODEL.expectedSizeBytes)))
        assertFalse(machine.succeed(first))
        assertEquals(second, (machine.snapshot() as ModelDownloadState.Running).operationId)
    }

    @Test
    fun cancellationIsTerminalAndReportsCleanup() {
        val machine = ModelDownloadStateMachine<String>()
        val operationId = checkNotNull(machine.begin(MODEL, "destination", 0))

        assertTrue(machine.beginCancellation(operationId))
        assertNull(machine.begin(MODEL, "other", 0))
        assertFalse(machine.updateProgress(
            operationId,
            ModelDownloadProgress(9L, MODEL.expectedSizeBytes),
        ))
        assertTrue(machine.finishCancellation(operationId, ModelDownloadCleanupResult.TRUNCATED))

        val cancelled = machine.snapshot() as ModelDownloadState.Cancelled
        assertEquals(ModelDownloadCleanupResult.TRUNCATED, cancelled.cleanup)
        assertTrue(machine.begin(MODEL, "retry", 0) != null)
    }

    @Test
    fun progressCannotMoveBackwardsOrChangeTotal() {
        val machine = ModelDownloadStateMachine<String>()
        val operationId = checkNotNull(machine.begin(MODEL, "destination", 0))
        assertTrue(machine.updateProgress(
            operationId,
            ModelDownloadProgress(100L, MODEL.expectedSizeBytes),
        ))

        assertThrows(IllegalArgumentException::class.java) {
            machine.updateProgress(operationId, ModelDownloadProgress(99L, MODEL.expectedSizeBytes))
        }
        assertThrows(IllegalArgumentException::class.java) {
            machine.updateProgress(operationId, ModelDownloadProgress(100L, 101L))
        }
    }

    private companion object {
        val MODEL = RecommendedModelCatalog.models.last()
    }
}
