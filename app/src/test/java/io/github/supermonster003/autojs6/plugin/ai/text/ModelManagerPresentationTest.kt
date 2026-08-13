package io.github.supermonster003.autojs6.plugin.ai.text

import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportFailureReason
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportProgress
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelManagerPresentationTest {
    @Test
    fun exposesTheModelThatCanStillBeCopiedForEveryState() {
        assertNull(ModelManagerPresentation.visibleModel(ModelImportState.Preparing))
        assertNull(ModelManagerPresentation.visibleModel(ModelImportState.Unavailable))
        assertNull(ModelManagerPresentation.visibleModel(ModelImportState.Ready<String>(null)))
        assertEquals("ready", ModelManagerPresentation.visibleModel(ModelImportState.Ready("ready")))
        assertEquals(
            "previous",
            ModelManagerPresentation.visibleModel(ModelImportState.Running(1L, "previous")),
        )
        assertEquals(
            "previous-while-cancelling",
            ModelManagerPresentation.visibleModel(
                ModelImportState.Cancelling(
                    operationId = 2L,
                    previous = "previous-while-cancelling",
                    progress = ModelImportProgress.initial(),
                ),
            ),
        )
        assertEquals(
            "imported",
            ModelManagerPresentation.visibleModel(ModelImportState.Succeeded(3L, "imported")),
        )
        assertEquals(
            "current-after-cancel",
            ModelManagerPresentation.visibleModel(
                ModelImportState.Cancelled(4L, "current-after-cancel"),
            ),
        )
        assertEquals(
            "current",
            ModelManagerPresentation.visibleModel(
                ModelImportState.Failed(5L, "current", ModelImportFailureReason.INVALID_FORMAT),
            ),
        )
    }

    @Test
    fun runningWithoutPreviousModelDoesNotExposeACopyAction() {
        assertNull(ModelManagerPresentation.visibleModel(ModelImportState.Running<String>(1L, null)))
    }
}
