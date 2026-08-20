package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import java.security.MessageDigest

internal object Utf8Text {
    fun byteCount(value: String): Int = value.toByteArray(Charsets.UTF_8).size

    fun requireWellFormed(value: String) {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                Character.isHighSurrogate(character) -> {
                    require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) {
                        "Text contains an unpaired UTF-16 surrogate"
                    }
                    index += 2
                }
                Character.isLowSurrogate(character) ->
                    throw IllegalArgumentException("Text contains an unpaired UTF-16 surrogate")
                else -> index += 1
            }
        }
    }

    fun prefix(value: String, maximumBytes: Int): String {
        require(maximumBytes >= 0)
        var index = 0
        var used = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val text = String(Character.toChars(codePoint))
            val bytes = byteCount(text)
            if (used + bytes > maximumBytes) break
            used += bytes
            index += Character.charCount(codePoint)
        }
        return value.substring(0, index)
    }

    fun split(value: String, maximumChunkBytes: Int): List<String> {
        require(maximumChunkBytes > 0)
        if (value.isEmpty()) return emptyList()
        val chunks = mutableListOf<String>()
        var start = 0
        var index = 0
        var used = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val charCount = Character.charCount(codePoint)
            val bytes = byteCount(String(Character.toChars(codePoint)))
            require(bytes <= maximumChunkBytes)
            if (used > 0 && used + bytes > maximumChunkBytes) {
                chunks += value.substring(start, index)
                start = index
                used = 0
            }
            used += bytes
            index += charCount
        }
        if (start < value.length) chunks += value.substring(start)
        return chunks
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
