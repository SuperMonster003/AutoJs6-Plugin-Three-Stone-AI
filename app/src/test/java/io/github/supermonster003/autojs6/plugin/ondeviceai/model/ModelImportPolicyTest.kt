package io.github.supermonster003.autojs6.plugin.ondeviceai.model

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
        assertEquals(0L, ModelImportPolicy.maximumCopyBytes(Long.MIN_VALUE))
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
    fun storagePreflightBlocksBeforeAMinimumHeaderFitsAndReportsTheExactBudget() {
        val blocked = ModelImportPolicy.storagePreflight(
            ModelImportPolicy.RESERVED_FREE_BYTES + ModelImportPolicy.LITERTLM_MAGIC_BYTES - 1L,
        )
        assertFalse(blocked.canOpenPicker)
        assertEquals(ModelImportPolicy.LITERTLM_MAGIC_BYTES - 1L, blocked.maximumAdditionalModelBytes)

        val allowed = ModelImportPolicy.storagePreflight(
            ModelImportPolicy.RESERVED_FREE_BYTES + ModelImportPolicy.LITERTLM_MAGIC_BYTES,
        )
        assertTrue(allowed.canOpenPicker)
        assertEquals(
            ModelImportPolicy.LITERTLM_MAGIC_BYTES.toLong(),
            allowed.maximumAdditionalModelBytes,
        )
        assertEquals(ModelImportPolicy.RESERVED_FREE_BYTES, allowed.reservedFreeBytes)

        val capped = ModelImportPolicy.storagePreflight(Long.MAX_VALUE)
        assertTrue(capped.canOpenPicker)
        assertEquals(ModelImportPolicy.MAXIMUM_MODEL_BYTES, capped.maximumAdditionalModelBytes)
    }

    @Test
    fun declaredSourceSizeMustFitThePrecopyBudget() {
        ModelImportPolicy.requireDeclaredSizeWithinBudget(declaredSize = null, maximumBytes = 64L)
        ModelImportPolicy.requireDeclaredSizeWithinBudget(declaredSize = 64L, maximumBytes = 64L)

        val empty = assertThrows(ModelImportFailureException::class.java) {
            ModelImportPolicy.requireDeclaredSizeWithinBudget(declaredSize = 0L, maximumBytes = 64L)
        }
        assertEquals(ModelImportFailureReason.INVALID_FORMAT, empty.reason)

        val tooLarge = assertThrows(ModelImportFailureException::class.java) {
            ModelImportPolicy.requireDeclaredSizeWithinBudget(
                declaredSize = ModelImportPolicy.MAXIMUM_MODEL_BYTES + 1L,
                maximumBytes = 64L,
            )
        }
        assertEquals(ModelImportFailureReason.MODEL_TOO_LARGE, tooLarge.reason)

        val insufficient = assertThrows(ModelImportFailureException::class.java) {
            ModelImportPolicy.requireDeclaredSizeWithinBudget(
                declaredSize = 65L,
                maximumBytes = 64L,
            )
        }
        assertEquals(ModelImportFailureReason.INSUFFICIENT_STORAGE, insufficient.reason)
    }

    @Test
    fun validatesLiteRtLmMagicAndStableId() {
        ModelImportPolicy.requireLiteRtLmHeader(
            "LITERTLM".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 0, 0, 0),
        )
        val zipFailure = assertThrows(ModelImportFailureException::class.java) {
            ModelImportPolicy.requireLiteRtLmHeader(
                byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0),
            )
        }
        assertEquals(ModelImportFailureReason.INVALID_FORMAT, zipFailure.reason)
        val digest = "ab".repeat(32)
        assertEquals("litertlm.${digest.take(32)}", ModelImportPolicy.stableModelId(digest))
    }

    @Test
    fun rejectsShortCaseChangedAndMutatedLiteRtLmMagic() {
        val magic = "LITERTLM".toByteArray(Charsets.US_ASCII)
        for (length in 0 until ModelImportPolicy.LITERTLM_MAGIC_BYTES) {
            val failure = assertThrows(ModelImportFailureException::class.java) {
                ModelImportPolicy.requireLiteRtLmHeader(magic.copyOf(length))
            }
            assertEquals(ModelImportFailureReason.INVALID_FORMAT, failure.reason)
        }
        assertThrows(ModelImportFailureException::class.java) {
            ModelImportPolicy.requireLiteRtLmHeader("litertlm".toByteArray(Charsets.US_ASCII))
        }
        magic.indices.forEach { index ->
            val mutated = magic.copyOf().apply { this[index] = (this[index].toInt() xor 0x01).toByte() }
            assertThrows(ModelImportFailureException::class.java) {
                ModelImportPolicy.requireLiteRtLmHeader(mutated)
            }
        }
    }

    @Test
    fun truncatesDisplayNameOnUtf8BoundaryAndRetainsPublishedGenerations() {
        val safe = ModelImportPolicy.safeDisplayName("模".repeat(200) + ".litertlm")
        assertTrue(safe.toByteArray(Charsets.UTF_8).size <= 256)
        assertFalse(ModelRetentionPolicy.DELETE_SUPERSEDED_GENERATIONS_DURING_IMPORT)
    }
}
