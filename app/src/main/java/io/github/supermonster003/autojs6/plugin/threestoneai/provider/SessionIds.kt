package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import java.security.MessageDigest

internal object SessionIds {
    fun create(requestId: String, nonce: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$requestId\u0000$nonce".toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
        return "litertlm.${digest.take(32)}"
    }
}
