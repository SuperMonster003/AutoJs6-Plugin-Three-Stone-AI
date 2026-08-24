package io.github.supermonster003.autojs6.plugin.threestoneai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelImportStateMachineTest {
    @Test
    fun recreatedObserverSeesRunningTaskAndItsCompletion() {
        val state = ModelImportStateMachine<String>()
        assertTrue(state.snapshot() === ModelImportState.Preparing)
        state.finishPreparation("old-model")

        val operationId = state.begin()!!
        val beforeRecreation = state.snapshot() as ModelImportState.Running
        assertEquals(operationId, beforeRecreation.operationId)
        assertEquals("old-model", beforeRecreation.previous)
        assertNull(state.begin())

        // A rebuilt Activity reads the same process-scoped state instead of starting another task.
        val afterRecreation = state.snapshot() as ModelImportState.Running
        assertEquals(beforeRecreation, afterRecreation)
        assertTrue(state.succeed(operationId, "new-model"))
        val completed = state.snapshot() as ModelImportState.Succeeded
        assertEquals(operationId, completed.operationId)
        assertEquals("new-model", completed.model)
    }

    @Test
    fun preparationFailureCannotStartImportAndFailedImportPreservesVisibleCurrent() {
        val unavailable = ModelImportStateMachine<String>()
        unavailable.failPreparation()
        assertTrue(unavailable.snapshot() === ModelImportState.Unavailable)
        assertNull(unavailable.begin())

        val ready = ModelImportStateMachine<String>()
        ready.finishPreparation("old-model")
        val operationId = ready.begin()!!
        assertTrue(
            ready.fail(
                operationId,
                "old-model",
                ModelImportFailureReason.INVALID_FORMAT,
            ),
        )
        assertFalse(ready.succeed(operationId, "late-model"))
        val failed = ready.snapshot() as ModelImportState.Failed
        assertEquals("old-model", failed.current)
        assertEquals(ModelImportFailureReason.INVALID_FORMAT, failed.reason)
    }

    @Test
    fun currentSelectionCannotChangeWhileImportOwnsTheStateMachine() {
        val state = ModelImportStateMachine<String>()
        state.finishPreparation("model-a")
        val operationId = state.begin()!!

        assertFalse(state.replaceCurrent("model-b"))
        assertFalse(state.makeUnavailable())
        assertEquals(
            ModelImportState.Running(operationId, "model-a"),
            state.snapshot(),
        )

        assertTrue(state.succeed(operationId, "model-c"))
        assertTrue(state.replaceCurrent("model-b"))
        assertEquals(ModelImportState.Ready("model-b"), state.snapshot())
    }

    @Test
    fun cancellationBecomesTerminalOnlyAfterTheWorkerFinishes() {
        val cancellationWins = ModelImportStateMachine<String>()
        cancellationWins.finishPreparation("old-model")
        val cancelledOperation = cancellationWins.begin()!!

        assertTrue(cancellationWins.beginCancellation(cancelledOperation))
        val cancelling = cancellationWins.snapshot() as ModelImportState.Cancelling
        assertEquals(cancelledOperation, cancelling.operationId)
        assertEquals("old-model", cancelling.previous)
        assertNull(cancellationWins.begin())
        assertFalse(cancellationWins.succeed(cancelledOperation, "late-model"))
        assertTrue(cancellationWins.finishCancellation(cancelledOperation, "old-model"))
        assertFalse(
            cancellationWins.fail(
                cancelledOperation,
                "late-model",
                ModelImportFailureReason.UNKNOWN,
            ),
        )
        assertEquals(
            ModelImportState.Cancelled(cancelledOperation, "old-model"),
            cancellationWins.snapshot(),
        )

        val completionWins = ModelImportStateMachine<String>()
        completionWins.finishPreparation("old-model")
        val completedOperation = completionWins.begin()!!

        assertTrue(completionWins.succeed(completedOperation, "new-model"))
        assertFalse(completionWins.beginCancellation(completedOperation))
        assertEquals(
            ModelImportState.Succeeded(completedOperation, "new-model"),
            completionWins.snapshot(),
        )
    }

    @Test
    fun unknownTotalCopyStillPublishesMonotonicFinalByteProgress() {
        val state = ModelImportStateMachine<String>()
        state.finishPreparation("old-model")
        val operationId = state.begin()!!

        assertTrue(
            state.updateProgress(
                operationId,
                ModelImportProgress(ModelImportStage.COPYING, processedBytes = 8L, totalBytes = null),
            ),
        )
        assertTrue(
            state.updateProgress(
                operationId,
                ModelImportProgress(ModelImportStage.COPYING, processedBytes = 24L, totalBytes = null),
            ),
        )
        val copying = state.snapshot() as ModelImportState.Running
        assertNull(copying.progress.totalBytes)
        assertTrue(
            state.updateProgress(
                operationId,
                ModelImportProgress(ModelImportStage.PUBLISHING, processedBytes = 24L, totalBytes = 24L),
            ),
        )
        assertTrue(
            state.updateProgress(
                operationId,
                ModelImportProgress(
                    ModelImportStage.CHECKING_COMPATIBILITY,
                    processedBytes = 24L,
                    totalBytes = 24L,
                ),
            ),
        )

        val running = state.snapshot() as ModelImportState.Running
        assertEquals(ModelImportStage.CHECKING_COMPATIBILITY, running.progress.stage)
        assertEquals(24L, running.progress.processedBytes)
        assertEquals(24L, running.progress.totalBytes)
    }

    @Test
    fun rejectsByteRegressionAcrossImportStages() {
        val state = ModelImportStateMachine<String>()
        state.finishPreparation(null)
        val operationId = state.begin()!!
        assertTrue(
            state.updateProgress(
                operationId,
                ModelImportProgress(ModelImportStage.COPYING, processedBytes = 24L, totalBytes = null),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            state.updateProgress(
                operationId,
                ModelImportProgress(ModelImportStage.PUBLISHING, processedBytes = 23L, totalBytes = 23L),
            )
        }
    }

    @Test
    fun rejectsProcessedBytesBeyondTheKnownTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelImportProgress(
                stage = ModelImportStage.COPYING,
                processedBytes = 9L,
                totalBytes = 8L,
            )
        }
    }
}
