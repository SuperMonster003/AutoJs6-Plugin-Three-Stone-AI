package io.github.supermonster003.autojs6.plugin.ai.text.model

import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.security.MessageDigest

internal data class ModelCopyResult(
    val byteCount: Long,
    val sha256: String,
)

internal object ModelImportCopier {
    private const val COPY_BUFFER_BYTES = 1024 * 1024

    fun copy(
        input: InputStream,
        output: OutputStream,
        maximumBytes: Long,
        limitFailureReason: ModelImportFailureReason,
        progressListener: (processedBytes: Long) -> Unit = {},
    ): ModelCopyResult {
        if (maximumBytes < ModelImportPolicy.LITERTLM_MAGIC_BYTES) {
            throw ModelImportFailureException(
                limitFailureReason,
                "The selected model exceeds the import limit",
            )
        }

        val header = ByteArray(ModelImportPolicy.LITERTLM_MAGIC_BYTES)
        var headerCount = 0
        while (headerCount < header.size) {
            ensureNotInterrupted()
            val count = readWithProgress(input, header, headerCount, header.size - headerCount)
            if (count < 0) break
            headerCount += count
        }
        ModelImportPolicy.requireLiteRtLmHeader(header.copyOf(headerCount))

        val digest = MessageDigest.getInstance("SHA-256")
        output.write(header)
        digest.update(header)
        var copied = header.size.toLong()
        progressListener(copied)
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            ensureNotInterrupted()
            val remaining = maximumBytes - copied
            val readLimit = if (remaining >= buffer.size) buffer.size else (remaining + 1L).toInt()
            val count = readWithProgress(input, buffer, 0, readLimit)
            if (count < 0) break
            if (count > remaining) {
                throw ModelImportFailureException(
                    limitFailureReason,
                    "The selected model exceeds the import limit",
                )
            }
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            copied += count
            progressListener(copied)
        }
        return ModelCopyResult(
            byteCount = copied,
            sha256 = digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xFF)
            },
        )
    }

    private fun readWithProgress(
        input: InputStream,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = try {
        val count = input.read(buffer, offset, length)
        if (count != 0) {
            count
        } else {
            val singleByte = input.read()
            if (singleByte < 0) {
                -1
            } else {
                buffer[offset] = singleByte.toByte()
                1
            }
        }
    } catch (error: ModelImportFailureException) {
        throw error
    } catch (error: InterruptedException) {
        throw error
    } catch (error: InterruptedIOException) {
        throw ModelImportFailureException(
            ModelImportFailureReason.INTERRUPTED,
            "Model import was interrupted while reading the selected file",
            error,
        )
    } catch (error: Exception) {
        throw ModelImportFailureException(
            ModelImportFailureReason.SOURCE_UNAVAILABLE,
            "The selected model could not be read",
            error,
        )
    }

    private fun ensureNotInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Model import was interrupted")
        }
    }
}
