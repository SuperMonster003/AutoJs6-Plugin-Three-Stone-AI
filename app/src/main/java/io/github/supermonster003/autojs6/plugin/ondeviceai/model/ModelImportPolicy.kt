package io.github.supermonster003.autojs6.plugin.ondeviceai.model

import java.nio.charset.StandardCharsets

internal data class ModelImportStoragePreflight(
    val usableBytes: Long,
    val maximumAdditionalModelBytes: Long,
    val reservedFreeBytes: Long,
) {
    init {
        require(usableBytes >= 0L) { "Usable storage cannot be negative" }
        require(maximumAdditionalModelBytes in 0L..usableBytes) {
            "Model import budget must fit usable storage"
        }
        require(reservedFreeBytes >= 0L) { "Reserved storage cannot be negative" }
    }

    val canOpenPicker: Boolean
        get() = maximumAdditionalModelBytes >= ModelImportPolicy.LITERTLM_MAGIC_BYTES
}

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

    fun maximumCopyBytes(usableSpace: Long): Long = minOf(
        MAXIMUM_MODEL_BYTES,
        (usableSpace.coerceAtLeast(0L) - RESERVED_FREE_BYTES).coerceAtLeast(0L),
    )

    fun storagePreflight(usableSpace: Long): ModelImportStoragePreflight {
        val usableBytes = usableSpace.coerceAtLeast(0L)
        return ModelImportStoragePreflight(
            usableBytes = usableBytes,
            maximumAdditionalModelBytes = maximumCopyBytes(usableBytes),
            reservedFreeBytes = RESERVED_FREE_BYTES,
        )
    }

    /** Rejects a known source size before the source stream is opened or any bytes are copied. */
    fun requireDeclaredSizeWithinBudget(declaredSize: Long?, maximumBytes: Long) {
        require(maximumBytes in 0L..MAXIMUM_MODEL_BYTES) { "Model import budget is invalid" }
        declaredSize ?: return
        when {
            declaredSize <= 0L -> throw ModelImportFailureException(
                ModelImportFailureReason.INVALID_FORMAT,
                "The selected model is empty",
            )
            declaredSize > MAXIMUM_MODEL_BYTES -> throw ModelImportFailureException(
                ModelImportFailureReason.MODEL_TOO_LARGE,
                "The selected model exceeds the import limit",
            )
            declaredSize > maximumBytes -> throw ModelImportFailureException(
                ModelImportFailureReason.INSUFFICIENT_STORAGE,
                "There is not enough free storage for the selected model",
            )
        }
    }

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
