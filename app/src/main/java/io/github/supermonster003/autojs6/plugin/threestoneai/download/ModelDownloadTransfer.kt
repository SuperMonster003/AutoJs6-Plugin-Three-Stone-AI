package io.github.supermonster003.autojs6.plugin.threestoneai.download

import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportPolicy
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.security.MessageDigest

internal data class ModelDownloadProgress(
    val processedBytes: Long,
    val totalBytes: Long,
) {
    init {
        require(totalBytes > 0L) { "Model download total must be positive" }
        require(processedBytes in 0L..totalBytes) { "Model download progress is invalid" }
    }
}

internal data class ModelDownloadTransferResult(
    val byteCount: Long,
    val sha256: String,
)

internal object ModelDownloadTransfer {
    private const val COPY_BUFFER_BYTES = 1024 * 1024

    fun copyAndVerify(
        input: InputStream,
        output: OutputStream,
        model: RecommendedModel,
        ensureActive: () -> Unit = ::ensureThreadActive,
        progressListener: (ModelDownloadProgress) -> Unit = {},
    ): ModelDownloadTransferResult = resumeAndVerify(
        input = input,
        output = output,
        model = model,
        alreadyCopied = 0L,
        digest = MessageDigest.getInstance("SHA-256"),
        ensureActive = ensureActive,
        progressListener = progressListener,
    )

    /**
     * Hashes the [length]-byte prefix already in a partial file so a resumed transfer can extend
     * the same digest. The prefix must start with the LiteRT-LM header; anything else is discarded.
     */
    fun seedPartial(
        input: InputStream,
        length: Long,
        ensureActive: () -> Unit = ::ensureThreadActive,
        progressListener: (Long) -> Unit = {},
    ): MessageDigest {
        require(length >= ModelImportPolicy.LITERTLM_MAGIC_BYTES) { "Partial download is too short" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        val header = ByteArray(ModelImportPolicy.LITERTLM_MAGIC_BYTES)
        var hashed = 0L
        while (hashed < length) {
            ensureActive()
            val count = try {
                input.read(buffer, 0, minOf(buffer.size.toLong(), length - hashed).toInt())
            } catch (error: IOException) {
                throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.DESTINATION_UNAVAILABLE,
                    "Partial model download could not be read",
                    error,
                )
            }
            if (count < 0) {
                throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.DESTINATION_UNAVAILABLE,
                    "Partial model download ended early",
                )
            }
            if (hashed < header.size) {
                val headerBytes = minOf(count.toLong(), header.size - hashed).toInt()
                buffer.copyInto(header, hashed.toInt(), 0, headerBytes)
                if (hashed + count >= header.size) requireHeader(header)
            }
            digest.update(buffer, 0, count)
            hashed += count
            progressListener(hashed)
        }
        return digest
    }

    /**
     * Appends the rest of a pinned model to [output], which already holds [alreadyCopied] bytes
     * whose hash is in [digest], then verifies the exact size and SHA-256.
     */
    fun resumeAndVerify(
        input: InputStream,
        output: OutputStream,
        model: RecommendedModel,
        alreadyCopied: Long,
        digest: MessageDigest,
        ensureActive: () -> Unit = ::ensureThreadActive,
        progressListener: (ModelDownloadProgress) -> Unit = {},
    ): ModelDownloadTransferResult {
        ModelDownloadPolicy.requireValidCatalogEntry(model)
        require(alreadyCopied in 0L..model.expectedSizeBytes) { "Resumed length is invalid" }
        var copied = alreadyCopied
        if (copied == 0L) {
            val header = ByteArray(ModelImportPolicy.LITERTLM_MAGIC_BYTES)
            var headerCount = 0
            while (headerCount < header.size) {
                ensureActive()
                val count = read(input, header, headerCount, header.size - headerCount)
                if (count < 0) break
                headerCount += count
            }
            requireHeader(header.copyOf(headerCount))
            write(output, header, 0, header.size)
            digest.update(header)
            copied = header.size.toLong()
        }
        progressListener(ModelDownloadProgress(copied, model.expectedSizeBytes))

        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (copied < model.expectedSizeBytes) {
            ensureActive()
            val remaining = model.expectedSizeBytes - copied
            val count = read(input, buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) {
                throw ModelDownloadFailureException(
                    ModelDownloadFailureReason.NETWORK_UNAVAILABLE,
                    "Downloaded model ended before its pinned size",
                )
            }
            write(output, buffer, 0, count)
            digest.update(buffer, 0, count)
            copied += count
            progressListener(ModelDownloadProgress(copied, model.expectedSizeBytes))
        }

        ensureActive()
        if (read(input, buffer, 0, 1) >= 0) {
            throw ModelDownloadFailureException(
                ModelDownloadFailureReason.INTEGRITY_MISMATCH,
                "Downloaded model exceeds its pinned size",
            )
        }
        return verifyDigest(digest, copied, model)
    }

    /** Final check for a partial file that already holds every pinned byte. */
    fun verifyDigest(
        digest: MessageDigest,
        byteCount: Long,
        model: RecommendedModel,
    ): ModelDownloadTransferResult {
        val sha256 = digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
        if (byteCount != model.expectedSizeBytes || sha256 != model.expectedSha256) {
            throw ModelDownloadFailureException(
                ModelDownloadFailureReason.INTEGRITY_MISMATCH,
                "Downloaded model SHA-256 does not match its pinned digest",
            )
        }
        return ModelDownloadTransferResult(byteCount, sha256)
    }

    private fun requireHeader(header: ByteArray) {
        try {
            ModelImportPolicy.requireLiteRtLmHeader(header)
        } catch (error: ModelImportFailureException) {
            throw ModelDownloadFailureException(
                ModelDownloadFailureReason.INVALID_CONTENT,
                "Downloaded content is not a LiteRT-LM model",
                error,
            )
        }
    }

    private fun read(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int = try {
        val count = input.read(buffer, offset, length)
        if (count != 0) {
            count
        } else {
            val singleByte = input.read()
            if (singleByte < 0) -1 else 1.also { buffer[offset] = singleByte.toByte() }
        }
    } catch (error: InterruptedIOException) {
        throw ModelDownloadFailureException(
            ModelDownloadFailureReason.INTERRUPTED,
            "Model download was interrupted while reading",
            error,
        )
    } catch (error: IOException) {
        throw ModelDownloadFailureException(
            ModelDownloadFailureReason.NETWORK_UNAVAILABLE,
            "Model download source could not be read",
            error,
        )
    }

    private fun write(output: OutputStream, buffer: ByteArray, offset: Int, length: Int) {
        try {
            output.write(buffer, offset, length)
        } catch (error: IOException) {
            throw ModelDownloadFailureException(
                ModelDownloadFailureReason.DESTINATION_UNAVAILABLE,
                "Model download destination could not be written",
                error,
            )
        }
    }

    private fun ensureThreadActive() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Model download was interrupted")
        }
    }
}
