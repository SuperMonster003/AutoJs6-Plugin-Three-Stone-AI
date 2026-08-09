package io.github.supermonster003.autojs6.plugin.ai.text.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelImportFailureTest {
    @Test
    fun exposesOnlyControlledFailureReasons() {
        assertEquals(
            ModelImportFailureReason.INVALID_FORMAT,
            ModelImportFailureException(
                ModelImportFailureReason.INVALID_FORMAT,
                "private diagnostic",
            ).toModelImportFailureReason(),
        )
        assertEquals(
            ModelImportFailureReason.INTERRUPTED,
            InterruptedException("private diagnostic").toModelImportFailureReason(),
        )
        assertEquals(
            ModelImportFailureReason.UNKNOWN,
            IllegalStateException("private diagnostic").toModelImportFailureReason(),
        )
    }
}
