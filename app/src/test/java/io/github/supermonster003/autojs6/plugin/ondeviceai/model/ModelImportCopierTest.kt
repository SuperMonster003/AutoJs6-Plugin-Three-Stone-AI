package io.github.supermonster003.autojs6.plugin.ondeviceai.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class ModelImportCopierTest {
    @Test
    fun copiesSplitHeaderAndPayloadWithTheExpectedDigest() {
        val content = "LITERTLM".toByteArray(Charsets.US_ASCII) + ByteArray(257) { it.toByte() }
        val output = ByteArrayOutputStream()

        val copied = ModelImportCopier.copy(
            input = ChunkedInputStream(content, maximumChunkBytes = 1),
            output = output,
            maximumBytes = content.size.toLong(),
            limitFailureReason = ModelImportFailureReason.MODEL_TOO_LARGE,
        )

        assertArrayEquals(content, output.toByteArray())
        assertEquals(content.size.toLong(), copied.byteCount)
        assertEquals(sha256(content), copied.sha256)
    }

    @Test
    fun reportsMonotonicProgressThroughTheFinalByte() {
        val content = "LITERTLM".toByteArray(Charsets.US_ASCII) + ByteArray(257) { it.toByte() }
        val progress = mutableListOf<Long>()

        ModelImportCopier.copy(
            input = ChunkedInputStream(content, maximumChunkBytes = 3),
            output = ByteArrayOutputStream(),
            maximumBytes = content.size.toLong(),
            limitFailureReason = ModelImportFailureReason.MODEL_TOO_LARGE,
            progressListener = progress::add,
        )

        assertEquals(ModelImportPolicy.LITERTLM_MAGIC_BYTES.toLong(), progress.first())
        assertTrue(progress.zipWithNext().all { (before, after) -> after > before })
        assertEquals(content.size.toLong(), progress.last())
    }

    @Test
    fun invalidMagicConsumesOnlyEightBytesAndWritesNothing() {
        val content = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0) + ByteArray(4096)
        val input = TrackingInputStream(content)
        val output = ByteArrayOutputStream()

        val failure = assertThrows(ModelImportFailureException::class.java) {
            ModelImportCopier.copy(
                input = input,
                output = output,
                maximumBytes = content.size.toLong(),
                limitFailureReason = ModelImportFailureReason.MODEL_TOO_LARGE,
            )
        }

        assertEquals(ModelImportFailureReason.INVALID_FORMAT, failure.reason)
        assertEquals(ModelImportPolicy.LITERTLM_MAGIC_BYTES, input.bytesRead)
        assertEquals(0, output.size())
    }

    @Test
    fun shortInputsAreRejectedBeforeAnythingIsWritten() {
        val magic = "LITERTLM".toByteArray(Charsets.US_ASCII)
        for (length in 0 until ModelImportPolicy.LITERTLM_MAGIC_BYTES) {
            val input = TrackingInputStream(magic.copyOf(length))
            val output = ByteArrayOutputStream()
            val failure = assertThrows(ModelImportFailureException::class.java) {
                ModelImportCopier.copy(
                    input = input,
                    output = output,
                    maximumBytes = ModelImportPolicy.LITERTLM_MAGIC_BYTES.toLong(),
                    limitFailureReason = ModelImportFailureReason.MODEL_TOO_LARGE,
                )
            }
            assertEquals(ModelImportFailureReason.INVALID_FORMAT, failure.reason)
            assertEquals(length, input.bytesRead)
            assertEquals(0, output.size())
        }
    }

    @Test
    fun exactMagicIsAcceptedAndOneByteOverTheLimitIsRejected() {
        val magic = "LITERTLM".toByteArray(Charsets.US_ASCII)
        val exactOutput = ByteArrayOutputStream()
        val exact = ModelImportCopier.copy(
            input = ByteArrayInputStream(magic),
            output = exactOutput,
            maximumBytes = magic.size.toLong(),
            limitFailureReason = ModelImportFailureReason.MODEL_TOO_LARGE,
        )
        assertArrayEquals(magic, exactOutput.toByteArray())
        assertEquals(magic.size.toLong(), exact.byteCount)

        val oversizedOutput = ByteArrayOutputStream()
        val failure = assertThrows(ModelImportFailureException::class.java) {
            ModelImportCopier.copy(
                input = ByteArrayInputStream(magic + byteArrayOf(1)),
                output = oversizedOutput,
                maximumBytes = magic.size.toLong(),
                limitFailureReason = ModelImportFailureReason.INSUFFICIENT_STORAGE,
            )
        }
        assertEquals(ModelImportFailureReason.INSUFFICIENT_STORAGE, failure.reason)
        assertArrayEquals(magic, oversizedOutput.toByteArray())
    }

    @Test
    fun zeroLengthBulkReadFallsBackWithoutLosingTheHeader() {
        val content = "LITERTLMpayload".toByteArray(Charsets.US_ASCII)
        val output = ByteArrayOutputStream()
        val copied = ModelImportCopier.copy(
            input = ZeroOnceInputStream(content),
            output = output,
            maximumBytes = content.size.toLong(),
            limitFailureReason = ModelImportFailureReason.MODEL_TOO_LARGE,
        )
        assertArrayEquals(content, output.toByteArray())
        assertEquals(sha256(content), copied.sha256)
    }

    @Test
    fun interruptionIsObservedBeforeReadingOrWriting() {
        val content = "LITERTLMpayload".toByteArray(Charsets.US_ASCII)
        val input = TrackingInputStream(content)
        val output = ByteArrayOutputStream()
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedException::class.java) {
                ModelImportCopier.copy(
                    input = input,
                    output = output,
                    maximumBytes = content.size.toLong(),
                    limitFailureReason = ModelImportFailureReason.MODEL_TOO_LARGE,
                )
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(0, input.bytesRead)
        assertEquals(0, output.size())
    }

    @Test
    fun midstreamReadFailureIsReportedAsSourceUnavailable() {
        val magic = "LITERTLM".toByteArray(Charsets.US_ASCII)
        val output = ByteArrayOutputStream()
        val failure = assertThrows(ModelImportFailureException::class.java) {
            ModelImportCopier.copy(
                input = FailingAfterHeaderInputStream(magic),
                output = output,
                maximumBytes = 1024L,
                limitFailureReason = ModelImportFailureReason.MODEL_TOO_LARGE,
            )
        }
        assertEquals(ModelImportFailureReason.SOURCE_UNAVAILABLE, failure.reason)
        assertArrayEquals(magic, output.toByteArray())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }

    private class ChunkedInputStream(
        bytes: ByteArray,
        private val maximumChunkBytes: Int,
    ) : ByteArrayInputStream(bytes) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, minOf(length, maximumChunkBytes))
    }

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var bytesRead: Int = 0
            private set

        override fun read(): Int = super.read().also { value ->
            if (value >= 0) bytesRead += 1
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { count ->
                if (count > 0) bytesRead += count
            }
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

    private class FailingAfterHeaderInputStream(private val header: ByteArray) : InputStream() {
        private var offset = 0

        override fun read(): Int {
            if (offset >= header.size) throw IOException("source disappeared")
            return header[offset++].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, targetOffset: Int, length: Int): Int {
            if (offset >= header.size) throw IOException("source disappeared")
            val count = minOf(length, header.size - offset)
            header.copyInto(buffer, targetOffset, offset, offset + count)
            offset += count
            return count
        }
    }
}
