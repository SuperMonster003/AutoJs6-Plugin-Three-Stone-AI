package io.github.supermonster003.autojs6.plugin.ondeviceai.model

import java.nio.charset.StandardCharsets

internal object ModelImportPolicy {
    const val MAXIMUM_MODEL_BYTES = 8L * 1024L * 1024L * 1024L
    const val RESERVED_FREE_BYTES = 256L * 1024L * 1024L
    const val MAXIMUM_SOURCE_DISPLAY_NAME_CHARS = 1_024
    const val LITERTLM_MAGIC_BYTES = 8
    private val LITERTLM_MAGIC = "LITERTLM".toByteArray(StandardCharsets.US_ASCII)
    private val SHA_256 = Regex("^[0-9a-f]{64}$")

    fun requireImportableName(displayName: String) {
        if (displayName.length !in 1..MAXIMUM_SOURCE_DISPLAY_NAME_CHARS) {
            throw ModelImportFailureException(
                ModelImportFailureReason.INVALID_FORMAT,
                "The selected file name is invalid",
            )
        }
        if (!displayName.trim().endsWith(".litertlm", ignoreCase = true)) {
            throw ModelImportFailureException(
                ModelImportFailureReason.INVALID_FORMAT,
                "The selected file must use the .litertlm extension",
            )
        }
    }

    fun maximumCopyBytes(usableSpace: Long): Long =
        minOf(MAXIMUM_MODEL_BYTES, (usableSpace - RESERVED_FREE_BYTES).coerceAtLeast(0L))

    fun requireLiteRtLmHeader(header: ByteArray) {
        if (header.size < LITERTLM_MAGIC_BYTES) {
            throw ModelImportFailureException(
                ModelImportFailureReason.INVALID_FORMAT,
                "The selected model is too short",
            )
        }
        if (!header.copyOf(LITERTLM_MAGIC_BYTES).contentEquals(LITERTLM_MAGIC)) {
            throw ModelImportFailureException(
                ModelImportFailureReason.INVALID_FORMAT,
                "The selected file is not a LiteRT-LM model",
            )
        }
    }

    fun stableModelId(sha256: String): String {
        require(SHA_256.matches(sha256)) { "Invalid model digest" }
        return "litertlm.${sha256.take(32)}"
    }

    fun safeDisplayName(displayName: String): String {
        val normalized = displayName.trim().ifEmpty { "model.litertlm" }
        var end = 0
        var bytes = 0
        while (end < normalized.length) {
            val codePoint = Character.codePointAt(normalized, end)
            val text = String(Character.toChars(codePoint))
            val encoded = text.toByteArray(StandardCharsets.UTF_8).size
            if (bytes + encoded > ModelDisplayNamePolicy.MAXIMUM_UTF8_BYTES) break
            bytes += encoded
            end += Character.charCount(codePoint)
        }
        return normalized.substring(0, end).ifEmpty { "model.litertlm" }
    }
}
