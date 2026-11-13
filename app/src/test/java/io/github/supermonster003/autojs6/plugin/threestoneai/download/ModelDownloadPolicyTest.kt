package io.github.supermonster003.autojs6.plugin.threestoneai.download

import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadPolicyTest {
    @Test
    fun partialFilesAreNamedByTheirPinnedDigest() {
        val name = ModelDownloadPolicy.partFileName(SHA)
        assertEquals(".download-$SHA.part", name)
        assertEquals(SHA, ModelDownloadPolicy.partFileSha256(name))

        assertNull(ModelDownloadPolicy.partFileSha256("model-$SHA.litertlm"))
        assertNull(ModelDownloadPolicy.partFileSha256(".download-${SHA.uppercase()}.part"))
        assertNull(ModelDownloadPolicy.partFileSha256(".download-$SHA.part.tmp"))
        assertNull(ModelDownloadPolicy.partFileSha256(".download-${SHA.dropLast(1)}.part"))
        assertThrows(IllegalArgumentException::class.java) { ModelDownloadPolicy.partFileName("../$SHA") }
    }

    @Test
    fun onlyAPlausiblePrefixIsResumed() {
        assertEquals(0L, ModelDownloadPolicy.resumableLength(0L, 100L))
        assertEquals(0L, ModelDownloadPolicy.resumableLength(ModelImportPolicy.LITERTLM_MAGIC_BYTES - 1L, 100L))
        assertEquals(8L, ModelDownloadPolicy.resumableLength(8L, 100L))
        assertEquals(100L, ModelDownloadPolicy.resumableLength(100L, 100L))
        assertEquals(0L, ModelDownloadPolicy.resumableLength(101L, 100L))
    }

    @Test
    fun storageCheckKeepsTheImportReserveFree() {
        val reserve = ModelImportPolicy.RESERVED_FREE_BYTES
        assertTrue(ModelDownloadPolicy.hasStorageFor(reserve + 1_000L, 1_000L))
        assertFalse(ModelDownloadPolicy.hasStorageFor(reserve + 999L, 1_000L))
        assertFalse(ModelDownloadPolicy.hasStorageFor(-1L, 0L))
        assertTrue(ModelDownloadPolicy.hasStorageFor(reserve, 0L))
    }

    @Test
    fun contentAndDigestFailuresStartOver() {
        assertFalse(ModelDownloadPolicy.keepsPartialFile(ModelDownloadFailureReason.INVALID_CONTENT))
        assertFalse(ModelDownloadPolicy.keepsPartialFile(ModelDownloadFailureReason.INTEGRITY_MISMATCH))
        ModelDownloadFailureReason.entries
            .filterNot {
                it == ModelDownloadFailureReason.INVALID_CONTENT ||
                    it == ModelDownloadFailureReason.INTEGRITY_MISMATCH
            }
            .forEach { assertTrue(it.name, ModelDownloadPolicy.keepsPartialFile(it)) }
    }

    /** D107: warn below the stated minimum, with room for the memory the kernel reserves. */
    @Test
    fun memoryWarningAllowsTheKernelReserve() {
        // An 8 GB phone typically reports about 7.4 GiB; a 4 GB phone about 3.6 GiB.
        assertFalse(ModelDownloadPolicy.isBelowMemoryRequirement(gib(7.4), 8))
        assertFalse(ModelDownloadPolicy.isBelowMemoryRequirement(gib(6.8), 8))
        assertTrue(ModelDownloadPolicy.isBelowMemoryRequirement(gib(6.7), 8))
        assertTrue(ModelDownloadPolicy.isBelowMemoryRequirement(gib(3.6), 8))
        assertFalse(ModelDownloadPolicy.isBelowMemoryRequirement(gib(3.6), 4))
        assertTrue(ModelDownloadPolicy.isBelowMemoryRequirement(gib(1.9), 4))
        // Unknown memory or an unspecified minimum never warns.
        assertFalse(ModelDownloadPolicy.isBelowMemoryRequirement(0L, 8))
        assertFalse(ModelDownloadPolicy.isBelowMemoryRequirement(gib(1.0), 0))
    }

    @Test
    fun resumedResponseMustCoverExactlyTheMissingRange() {
        HttpsModelDownloadClient.requireMatchingRange("bytes 100-999/1000", 100L, 1_000L)
        HttpsModelDownloadClient.requireMatchingRange(" Bytes 100-999/1000 ", 100L, 1_000L)
        listOf(
            null,
            "",
            "bytes 99-999/1000",
            "bytes 100-998/1000",
            "bytes 100-999/1001",
            "bytes 100-999/*",
            "bytes */1000",
        ).forEach { header ->
            val failure = assertThrows(header.toString(), ModelDownloadFailureException::class.java) {
                HttpsModelDownloadClient.requireMatchingRange(header, 100L, 1_000L)
            }
            assertEquals(ModelDownloadFailureReason.INTEGRITY_MISMATCH, failure.reason)
        }
    }

    private fun gib(value: Double): Long = (value * 1024.0 * 1024.0 * 1024.0).toLong()

    private companion object {
        const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
