package io.github.supermonster003.autojs6.plugin.ai.text.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertTrue(ready.fail(operationId, "old-model"))
        assertFalse(ready.succeed(operationId, "late-model"))
        val failed = ready.snapshot() as ModelImportState.Failed
        assertEquals("old-model", failed.current)
    }
}
