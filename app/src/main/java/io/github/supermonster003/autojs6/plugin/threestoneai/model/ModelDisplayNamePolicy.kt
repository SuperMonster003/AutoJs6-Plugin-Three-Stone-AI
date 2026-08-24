package io.github.supermonster003.autojs6.plugin.threestoneai.model

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

internal object ModelDisplayNamePolicy {
    const val MAXIMUM_UTF8_BYTES = 256

    fun normalizeUserInput(value: String): String {
        val normalized = value.trim()
        require(
            normalized.none { character ->
                Character.isISOControl(character) || character == '\u2028' || character == '\u2029'
            },
        ) {
            "Model display name cannot contain line breaks or control characters"
        }
        requireCatalogValue(normalized)
        return normalized
    }

    fun requireCatalogValue(value: String) {
        val encodedBytes = runCatching {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value))
                .remaining()
        }.getOrElse { throw IllegalArgumentException("Model display name encoding is invalid", it) }
        require(encodedBytes in 1..MAXIMUM_UTF8_BYTES) {
            "Model display name must contain between 1 and $MAXIMUM_UTF8_BYTES UTF-8 bytes"
        }
    }
}
