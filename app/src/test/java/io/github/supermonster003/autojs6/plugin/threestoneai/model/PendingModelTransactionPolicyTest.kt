package io.github.supermonster003.autojs6.plugin.threestoneai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingModelTransactionPolicyTest {
    private val transactionId = "123e4567-e89b-42d3-a456-426614174000"
    private val digest = "ab".repeat(32)
    private val marker = PendingModelTransactionPolicy.create(transactionId, digest)

    @Test
    fun strictMarkerRoundTripsWithinBoundedJson() {
        val encoded = PendingModelTransactionPolicy.encode(marker)
        assertTrue(encoded.size <= PendingModelTransactionPolicy.MAXIMUM_MARKER_BYTES)
        assertEquals(marker, PendingModelTransactionPolicy.decode(encoded))
        assertEquals("model-$digest.litertlm", marker.destinationFileName)
        assertTrue(marker.destinationFileName.toByteArray(Charsets.UTF_8).size < 128)
    }

    @Test
    fun recoveryDeletesOnlyUnpublishedDestinationNamedByMarker() {
        assertEquals(
            PendingModelRecoveryDecision.DELETE_UNPUBLISHED_DESTINATION,
            PendingModelTransactionPolicy.decideRecovery(
                marker = marker,
                publishedFileNames = setOf("model-${"cd".repeat(32)}.litertlm"),
                destinationExists = true,
            ),
        )
        assertEquals(
            PendingModelRecoveryDecision.RETAIN_PUBLISHED_DESTINATION,
            PendingModelTransactionPolicy.decideRecovery(
                marker = marker,
                publishedFileNames = setOf(marker.destinationFileName, "model-${"cd".repeat(32)}.litertlm"),
                destinationExists = true,
            ),
        )
        assertEquals(
            PendingModelRecoveryDecision.DISCARD_MARKER,
            PendingModelTransactionPolicy.decideRecovery(
                marker = marker,
                publishedFileNames = emptySet(),
                destinationExists = false,
            ),
        )
    }

    @Test
    fun existingHashGenerationNeverReceivesDeletionMarker() {
        assertFalse(PendingModelTransactionPolicy.shouldCreateMarker(destinationExistsBeforeImport = true))
        assertTrue(PendingModelTransactionPolicy.shouldCreateMarker(destinationExistsBeforeImport = false))
    }

    @Test
    fun parserRejectsExtraDuplicateTraversalOversizeAndMalformedUtf8() {
        val valid = PendingModelTransactionPolicy.encode(marker).toString(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            PendingModelTransactionPolicy.decode(valid.dropLast(1).plus(",\"extra\":1}").toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            PendingModelTransactionPolicy.decode(
                valid.replace(
                    "\"schema\":1",
                    "\"schema\":1,\"schema\":1",
                ).toByteArray(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PendingModelTransactionPolicy.decode(
                valid.replace(marker.destinationFileName, "../current.json").toByteArray(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PendingModelTransactionPolicy.decode(ByteArray(PendingModelTransactionPolicy.MAXIMUM_MARKER_BYTES + 1))
        }
        assertThrows(Exception::class.java) {
            PendingModelTransactionPolicy.decode(byteArrayOf(0xC3.toByte(), 0x28))
        }
    }
}
