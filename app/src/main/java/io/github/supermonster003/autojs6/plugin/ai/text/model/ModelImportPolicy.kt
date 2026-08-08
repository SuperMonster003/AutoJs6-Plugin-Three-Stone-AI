package io.github.supermonster003.autojs6.plugin.ai.text.model

import java.nio.charset.StandardCharsets

internal object ModelImportPolicy {
    const val MAXIMUM_MODEL_BYTES = 8L * 1024L * 1024L * 1024L
    const val RESERVED_FREE_BYTES = 256L * 1024L * 1024L
    const val MAXIMUM_SOURCE_DISPLAY_NAME_CHARS = 1_024
    private const val MAXIMUM_DISPLAY_NAME_BYTES = 256
    private val SHA_256 = Regex("^[0-9a-f]{64}$")

    fun requireImportableName(displayName: String) {
        require(displayName.length in 1..MAXIMUM_SOURCE_DISPLAY_NAME_CHARS) {
            "The selected file name is invalid"
        }
        require(displayName.trim().lowercase().endsWith(".litertlm")) {
            "The selected file must use the .litertlm extension"
        }
    }

    fun maximumCopyBytes(usableSpace: Long): Long =
        minOf(MAXIMUM_MODEL_BYTES, (usableSpace - RESERVED_FREE_BYTES).coerceAtLeast(0L))

    fun requireZipHeader(header: ByteArray) {
        require(header.size >= 4) { "The selected model is too short" }
        require(
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                ((header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) ||
                    (header[2] == 0x05.toByte() && header[3] == 0x06.toByte()) ||
                    (header[2] == 0x07.toByte() && header[3] == 0x08.toByte())),
        ) { "The selected file is not a LiteRT-LM archive" }
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
            if (bytes + encoded > MAXIMUM_DISPLAY_NAME_BYTES) break
            bytes += encoded
            end += Character.charCount(codePoint)
        }
        return normalized.substring(0, end).ifEmpty { "model.litertlm" }
    }
}
