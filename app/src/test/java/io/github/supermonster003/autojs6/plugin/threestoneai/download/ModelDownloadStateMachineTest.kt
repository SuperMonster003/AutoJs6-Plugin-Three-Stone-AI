package io.github.supermonster003.autojs6.plugin.threestoneai.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadStateMachineTest {
    @Test
    fun keepsExactLongProgressThroughStagesAndSuccess() {
        val machine = ModelDownloadStateMachine()
        val operationId = checkNotNull(machine.begin(MODEL, 0L))
        assertEquals(ModelDownloadStage.CONNECTING, running(machine).stage)

        assertTrue(machine.updateStage(operationId, ModelDownloadStage.DOWNLOADING))
        assertFalse(machine.updateStage(operationId, ModelDownloadStage.DOWNLOADING))
        val progress = ModelDownloadProgress(3_000_000_000L, MODEL.expectedSizeBytes)
        assertTrue(machine.updateProgress(operationId, progress))
        assertEquals(progress, running(machine).progress)
        assertEquals(MODEL, machine.snapshot().cardModel)

        assertTrue(machine.updateStage(operationId, ModelDownloadStage.IMPORTING))
        assertNull("The import panel takes over the card", machine.snapshot().cardModel)
        assertTrue(machine.succeed(operationId))
        val succeeded = machine.snapshot() as ModelDownloadState.Succeeded
        assertEquals(operationId, succeeded.operationId)
        assertNull(succeeded.cardModel)
    }

    @Test
    fun resumingStartsByCheckingThePartialFile() {
        val machine = ModelDownloadStateMachine()
        val operationId = checkNotNull(machine.begin(MODEL, 4_096L))
        val running = running(machine)

        assertEquals(operationId, running.operationId)
        assertEquals(ModelDownloadStage.CHECKING_PARTIAL, running.stage)
        assertEquals(4_096L, running.progress.processedBytes)
    }

    @Test
    fun staleOperationsCannotMutateOrCancelTheirSuccessor() {
        val machine = ModelDownloadStateMachine()
        val first = checkNotNull(machine.begin(MODEL, 0L))
        assertTrue(machine.fail(first, ModelDownloadFailureReason.NETWORK_UNAVAILABLE, 0L))
        val second = checkNotNull(machine.begin(MODEL, 0L))

        assertNotEquals(first, second)
        assertFalse(machine.beginCancellation(first))
        assertFalse(machine.beginPause(first))
        assertFalse(machine.updateProgress(first, ModelDownloadProgress(8L, MODEL.expectedSizeBytes)))
        assertFalse(machine.restartProgress(first))
        assertFalse(machine.succeed(first))
        assertFalse(machine.discard(first))
        assertEquals(second, running(machine).operationId)
    }

    @Test
    fun pauseKeepsTheVerifiedLengthAndResumesWithANewOperation() {
        val machine = ModelDownloadStateMachine()
        val operationId = checkNotNull(machine.begin(MODEL, 0L))
        assertTrue(machine.updateProgress(operationId, ModelDownloadProgress(500L, MODEL.expectedSizeBytes)))

        assertTrue(machine.beginPause(operationId))
        assertNull("A pausing download is still active", machine.begin(MODEL, 0L))
        assertFalse(machine.updateProgress(operationId, ModelDownloadProgress(600L, MODEL.expectedSizeBytes)))
        assertTrue(machine.finishPause(operationId, 480L))

        val paused = machine.snapshot() as ModelDownloadState.Paused
        assertEquals(480L, paused.progress.processedBytes)
        assertEquals(MODEL, paused.cardModel)
        val resumed = checkNotNull(machine.begin(MODEL, paused.progress.processedBytes))
        assertNotEquals(operationId, resumed)
        assertEquals(ModelDownloadStage.CHECKING_PARTIAL, running(machine).stage)
    }

    @Test
    fun onlyTransferStagesCanBePausedAndImportCannotBeCancelledHere() {
        val machine = ModelDownloadStateMachine()
        val operationId = checkNotNull(machine.begin(MODEL, 0L))
        assertTrue(machine.updateStage(operationId, ModelDownloadStage.WAITING_FOR_IMPORT))
        assertFalse(machine.beginPause(operationId))

        assertTrue(machine.updateStage(operationId, ModelDownloadStage.IMPORTING))
        assertFalse(machine.beginPause(operationId))
        assertFalse(machine.beginCancellation(operationId))
        assertTrue(running(machine).stage == ModelDownloadStage.IMPORTING)

        assertTrue(ModelDownloadStage.CHECKING_PARTIAL.pausable)
        assertTrue(ModelDownloadStage.CONNECTING.pausable)
        assertTrue(ModelDownloadStage.DOWNLOADING.pausable)
        assertTrue(ModelDownloadStage.WAITING_FOR_IMPORT.cancellable)
    }

    @Test
    fun cancellationIsTerminalAndCanFollowAPause() {
        val machine = ModelDownloadStateMachine()
        val operationId = checkNotNull(machine.begin(MODEL, 0L))

        assertTrue(machine.beginPause(operationId))
        assertTrue(machine.beginCancellation(operationId))
        assertFalse(machine.finishPause(operationId, 100L))
        assertNull(machine.begin(MODEL, 0L))
        assertTrue(machine.finishCancellation(operationId))

        val cancelled = machine.snapshot() as ModelDownloadState.Cancelled
        assertNull(cancelled.cardModel)
        assertNotNull(machine.begin(MODEL, 0L))
    }

    @Test
    fun coldStartRestoresAPausedDownloadOnlyWhenIdle() {
        val machine = ModelDownloadStateMachine()
        assertTrue(machine.restorePaused(MODEL, 1_024L))
        val paused = machine.snapshot() as ModelDownloadState.Paused
        assertEquals(1_024L, paused.progress.processedBytes)
        assertFalse(machine.restorePaused(MODEL, 2_048L))

        assertTrue(machine.discard(paused.operationId))
        assertTrue(machine.snapshot() is ModelDownloadState.Cancelled)
        assertFalse(machine.restorePaused(MODEL, 2_048L))
    }

    @Test
    fun failureRecordsTheResumableLengthAndCanBeDiscarded() {
        val machine = ModelDownloadStateMachine()
        val operationId = checkNotNull(machine.begin(MODEL, 0L))
        assertThrows(IllegalArgumentException::class.java) {
            machine.fail(operationId, ModelDownloadFailureReason.NETWORK_UNAVAILABLE, MODEL.expectedSizeBytes + 1L)
        }
        assertTrue(machine.fail(operationId, ModelDownloadFailureReason.NETWORK_UNAVAILABLE, 777L))

        val failed = machine.snapshot() as ModelDownloadState.Failed
        assertEquals(777L, failed.downloadedBytes)
        assertEquals(MODEL, failed.cardModel)
        assertTrue(machine.discard(operationId))
        assertFalse(machine.discard(operationId))
    }

    @Test
    fun restartProgressIsTheOnlyWayBackToZero() {
        val machine = ModelDownloadStateMachine()
        val operationId = checkNotNull(machine.begin(MODEL, 2_000L))
        assertThrows(IllegalArgumentException::class.java) {
            machine.updateProgress(operationId, ModelDownloadProgress(0L, MODEL.expectedSizeBytes))
        }
        assertTrue(machine.restartProgress(operationId))
        assertEquals(0L, running(machine).progress.processedBytes)
    }

    @Test
    fun cancelledImportKeepsACompleteFileAsAPausedDownload() {
        val machine = ModelDownloadStateMachine()
        val operationId = checkNotNull(machine.begin(MODEL, 0L))
        assertFalse(
            "Only an import that took over the file can end this way",
            machine.finishHandOffCancellation(operationId, MODEL.expectedSizeBytes),
        )
        assertTrue(machine.updateStage(operationId, ModelDownloadStage.IMPORTING))
        assertThrows(IllegalArgumentException::class.java) {
            machine.finishHandOffCancellation(operationId, MODEL.expectedSizeBytes + 1L)
        }
        assertTrue(machine.finishHandOffCancellation(operationId, MODEL.expectedSizeBytes))
        val paused = machine.snapshot() as ModelDownloadState.Paused
        assertEquals(MODEL.expectedSizeBytes, paused.progress.processedBytes)

        val next = checkNotNull(machine.begin(MODEL, 0L))
        assertTrue(machine.updateStage(next, ModelDownloadStage.IMPORTING))
        assertTrue(machine.finishHandOffCancellation(next, 0L))
        assertTrue(machine.snapshot() is ModelDownloadState.Cancelled)
    }

    @Test
    fun progressCannotMoveBackwardsOrChangeTotal() {
        val machine = ModelDownloadStateMachine()
        val operationId = checkNotNull(machine.begin(MODEL, 0L))
        assertTrue(machine.updateProgress(operationId, ModelDownloadProgress(100L, MODEL.expectedSizeBytes)))

        assertThrows(IllegalArgumentException::class.java) {
            machine.updateProgress(operationId, ModelDownloadProgress(99L, MODEL.expectedSizeBytes))
        }
        assertThrows(IllegalArgumentException::class.java) {
            machine.updateProgress(operationId, ModelDownloadProgress(100L, 101L))
        }
    }

    private fun running(machine: ModelDownloadStateMachine): ModelDownloadState.Running =
        machine.snapshot() as ModelDownloadState.Running

    private companion object {
        val MODEL = checkNotNull(RecommendedModelCatalog.find("gemma-4-e4b-it"))
    }
}
