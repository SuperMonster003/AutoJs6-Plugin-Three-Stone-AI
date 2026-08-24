package io.github.supermonster003.autojs6.plugin.threestoneai.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

class ModelDownloadTransferTest {
    @Test
    fun copiesAndVerifiesSplitContentWithMonotonicProgress() {
        val content = validContent(4097)
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<ModelDownloadProgress>()

        val result = ModelDownloadTransfer.copyAndVerify(
            input = ChunkedInputStream(content, 3),
            output = output,
            model = modelFor(content),
            progressListener = progress::add,
        )

        assertArrayEquals(content, output.toByteArray())
        assertEquals(content.size.toLong(), result.byteCount)
        assertEquals(sha256(content), result.sha256)
        assertTrue(progress.zipWithNext().all { (before, after) ->
            before.processedBytes < after.processedBytes
        })
        assertEquals(content.size.toLong(), progress.last().processedBytes)
    }

    @Test
    fun validatesTheHeaderBeforeWritingDestinationBytes() {
        val content = "NOTMODELpayload".toByteArray()
        val output = ByteArrayOutputStream()

        val failure = assertThrows(ModelDownloadFailureException::class.java) {
            ModelDownloadTransfer.copyAndVerify(
                ByteArrayInputStream(content),
                output,
                modelFor(content),
            )
        }

        assertEquals(ModelDownloadFailureReason.INVALID_CONTENT, failure.reason)
        assertEquals(0, output.size())
    }

    @Test
    fun rejectsShortOversizedAndWrongDigestContent() {
        val content = validContent(24)

        val short = assertThrows(ModelDownloadFailureException::class.java) {
            ModelDownloadTransfer.copyAndVerify(
                ByteArrayInputStream(content.copyOf(content.size - 1)),
                ByteArrayOutputStream(),
                modelFor(content),
            )
        }
        assertEquals(ModelDownloadFailureReason.INTEGRITY_MISMATCH, short.reason)

        val oversizedOutput = ByteArrayOutputStream()
        val oversized = assertThrows(ModelDownloadFailureException::class.java) {
            ModelDownloadTransfer.copyAndVerify(
                ByteArrayInputStream(content + byteArrayOf(1)),
                oversizedOutput,
                modelFor(content),
            )
        }
        assertEquals(ModelDownloadFailureReason.INTEGRITY_MISMATCH, oversized.reason)
        assertArrayEquals(content, oversizedOutput.toByteArray())

        val wrongDigest = assertThrows(ModelDownloadFailureException::class.java) {
            ModelDownloadTransfer.copyAndVerify(
                ByteArrayInputStream(content),
                ByteArrayOutputStream(),
                modelFor(content).copy(expectedSha256 = "0".repeat(64)),
            )
        }
        assertEquals(ModelDownloadFailureReason.INTEGRITY_MISMATCH, wrongDigest.reason)
    }

    @Test
    fun mapsSourceAndDestinationIoFailuresPrecisely() {
        val content = validContent(24)
        val sourceFailure = assertThrows(ModelDownloadFailureException::class.java) {
            ModelDownloadTransfer.copyAndVerify(
                FailingInputStream(content),
                ByteArrayOutputStream(),
                modelFor(content),
            )
        }
        assertEquals(ModelDownloadFailureReason.NETWORK_UNAVAILABLE, sourceFailure.reason)

        val destinationFailure = assertThrows(ModelDownloadFailureException::class.java) {
            ModelDownloadTransfer.copyAndVerify(
                ByteArrayInputStream(content),
                FailingOutputStream(),
                modelFor(content),
            )
        }
        assertEquals(ModelDownloadFailureReason.DESTINATION_UNAVAILABLE, destinationFailure.reason)
    }

    @Test
    fun observesCancellationBeforeWritingAndHandlesZeroBulkReads() {
        val content = validContent(24)
        val cancelledOutput = ByteArrayOutputStream()
        assertThrows(InterruptedException::class.java) {
            ModelDownloadTransfer.copyAndVerify(
                ByteArrayInputStream(content),
                cancelledOutput,
                modelFor(content),
                ensureActive = { throw InterruptedException("cancelled") },
            )
        }
        assertEquals(0, cancelledOutput.size())

        val zeroOutput = ByteArrayOutputStream()
        ModelDownloadTransfer.copyAndVerify(
            ZeroOnceInputStream(content),
            zeroOutput,
            modelFor(content),
        )
        assertArrayEquals(content, zeroOutput.toByteArray())
    }

    private fun validContent(size: Int): ByteArray {
        require(size >= 8)
        return "LITERTLM".toByteArray(Charsets.US_ASCII) + ByteArray(size - 8) { it.toByte() }
    }

    private fun modelFor(content: ByteArray): RecommendedModel = RecommendedModel(
        id = "test-model",
        displayName = "Test model",
        fileName = "test.litertlm",
        sourceUrl = "$BASE/blob/$REVISION/test.litertlm",
        downloadUrl = "$BASE/resolve/$REVISION/test.litertlm?download=true",
        expectedSizeBytes = content.size.toLong(),
        expectedSha256 = sha256(content),
        license = "Apache-2.0",
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }

    private class ChunkedInputStream(
        bytes: ByteArray,
        private val maximumChunkBytes: Int,
    ) : ByteArrayInputStream(bytes) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, minOf(length, maximumChunkBytes))
    }

    private class FailingInputStream(private val bytes: ByteArray) : InputStream() {
        private var offset = 0

        override fun read(): Int = if (offset < 8) bytes[offset++].toInt() and 0xFF else {
            throw IOException("network lost")
        }

        override fun read(buffer: ByteArray, targetOffset: Int, length: Int): Int {
            if (offset >= 8) throw IOException("network lost")
            val count = minOf(length, 8 - offset)
            bytes.copyInto(buffer, targetOffset, offset, offset + count)
            offset += count
            return count
        }
    }

    private class FailingOutputStream : OutputStream() {
        override fun write(value: Int) = throw IOException("destination lost")
        override fun write(buffer: ByteArray, offset: Int, length: Int) =
            throw IOException("destination lost")
    }

    private class ZeroOnceInputStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        private var returnedZero = false

        override fun read(): Int = delegate.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!returnedZero) {
                returnedZero = true
                return 0
            }
            return delegate.read(buffer, offset, length)
        }
    }

    private companion object {
        const val BASE = "https://huggingface.co/example/test"
        const val REVISION = "0123456789abcdef0123456789abcdef01234567"
    }
}
