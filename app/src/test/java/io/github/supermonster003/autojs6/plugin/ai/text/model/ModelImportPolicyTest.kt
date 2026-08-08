package io.github.supermonster003.autojs6.plugin.ai.text.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelImportPolicyTest {
    @Test
    fun acceptsOnlyLitertLmNames() {
        ModelImportPolicy.requireImportableName("Gemma.LITERTLM")
        assertThrows(IllegalArgumentException::class.java) {
            ModelImportPolicy.requireImportableName("model.task")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelImportPolicy.requireImportableName(
                "a".repeat(ModelImportPolicy.MAXIMUM_SOURCE_DISPLAY_NAME_CHARS + 1) + ".litertlm",
            )
        }
    }

    @Test
    fun reservesFreeSpaceAndAppliesHardLimit() {
        assertEquals(0L, ModelImportPolicy.maximumCopyBytes(ModelImportPolicy.RESERVED_FREE_BYTES))
        assertEquals(
            1024L,
            ModelImportPolicy.maximumCopyBytes(ModelImportPolicy.RESERVED_FREE_BYTES + 1024L),
        )
        assertEquals(
            ModelImportPolicy.MAXIMUM_MODEL_BYTES,
            ModelImportPolicy.maximumCopyBytes(Long.MAX_VALUE),
        )
    }

    @Test
    fun validatesArchiveHeaderAndStableId() {
        ModelImportPolicy.requireZipHeader(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        assertThrows(IllegalArgumentException::class.java) {
            ModelImportPolicy.requireZipHeader(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))
        }
        val digest = "ab".repeat(32)
        assertEquals("litertlm.${digest.take(32)}", ModelImportPolicy.stableModelId(digest))
    }

    @Test
    fun truncatesDisplayNameOnUtf8BoundaryAndRetainsPublishedGenerations() {
        val safe = ModelImportPolicy.safeDisplayName("模".repeat(200) + ".litertlm")
        assertTrue(safe.toByteArray(Charsets.UTF_8).size <= 256)
        assertFalse(ModelRetentionPolicy.DELETE_SUPERSEDED_GENERATIONS_DURING_IMPORT)
    }
}
