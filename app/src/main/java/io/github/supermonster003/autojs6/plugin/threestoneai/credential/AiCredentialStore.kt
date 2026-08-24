package io.github.supermonster003.autojs6.plugin.threestoneai.credential

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Plugin-private access to encrypted online-provider credentials.
 *
 * Plaintext is never returned as persistent state. [put] takes ownership of its input and clears
 * it before returning, while [withCredential] clears the decrypted bytes immediately after the
 * synchronous callback finishes.
 */
internal interface AiCredentialStore {
    fun put(profileId: String, credential: CharArray)

    fun isConfigured(profileId: String): Boolean

    fun <T> withCredential(profileId: String, action: (ByteArray) -> T): T

    fun clear(profileId: String): Boolean
}

internal enum class AiCredentialFailureReason {
    NOT_CONFIGURED,
    UNAVAILABLE,
}

/** Uses fixed, non-sensitive messages so a credential can never enter an exception or a log. */
internal class AiCredentialUnavailableException(
    val reason: AiCredentialFailureReason,
) : IllegalStateException(
    when (reason) {
        AiCredentialFailureReason.NOT_CONFIGURED -> "AI credential is not configured"
        AiCredentialFailureReason.UNAVAILABLE -> "AI credential is unavailable"
    },
)

internal interface CredentialCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedCredentialRecord

    fun decrypt(record: EncryptedCredentialRecord, associatedData: ByteArray): ByteArray
}

/**
 * Runs record access under one transaction lock. Android storage implements both an in-process
 * mutex and an OS file lock because the launcher UI and Binder provider use separate processes.
 */
internal interface CredentialRecordStorage {
    fun <T> withExclusiveAccess(action: (CredentialRecordAccess) -> T): T
}

internal interface CredentialRecordAccess {
    fun read(profileId: String): ByteArray?

    /** Implementations must consume or copy [encodedRecord] before returning. */
    fun write(profileId: String, encodedRecord: ByteArray)

    fun delete(profileId: String): Boolean
}

internal class EncryptedAiCredentialStore(
    private val storage: CredentialRecordStorage,
    private val cipher: CredentialCipher,
) : AiCredentialStore {
    override fun put(profileId: String, credential: CharArray) {
        try {
            AiCredentialProfileIds.requireValid(profileId)
            val plaintext = CredentialUtf8.encode(credential)
            try {
                val associatedData = CredentialAssociatedData.forProfile(profileId)
                try {
                    try {
                        storage.withExclusiveAccess { records ->
                            val record = cipher.encrypt(plaintext, associatedData)
                            val encodedRecord = CredentialRecordCodec.encode(record)
                            try {
                                records.write(profileId, encodedRecord)
                            } finally {
                                encodedRecord.fill(0)
                            }
                        }
                    } catch (_: Exception) {
                        throw AiCredentialUnavailableException(AiCredentialFailureReason.UNAVAILABLE)
                    }
                } finally {
                    associatedData.fill(0)
                }
            } finally {
                plaintext.fill(0)
            }
        } finally {
            credential.fill('\u0000')
        }
    }

    override fun isConfigured(profileId: String): Boolean {
        AiCredentialProfileIds.requireValid(profileId)
        return try {
            storage.withExclusiveAccess { records ->
                val encodedRecord = records.read(profileId) ?: return@withExclusiveAccess false
                try {
                    val record = CredentialRecordCodec.decode(encodedRecord)
                    val associatedData = CredentialAssociatedData.forProfile(profileId)
                    try {
                        val plaintext = cipher.decrypt(record, associatedData)
                        try {
                            plaintext.size in 1..CredentialEnvelopeLimits.MAXIMUM_PLAINTEXT_BYTES
                        } finally {
                            plaintext.fill(0)
                        }
                    } finally {
                        associatedData.fill(0)
                    }
                } finally {
                    encodedRecord.fill(0)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun <T> withCredential(profileId: String, action: (ByteArray) -> T): T {
        AiCredentialProfileIds.requireValid(profileId)
        val plaintext = try {
            storage.withExclusiveAccess { records ->
                val encodedRecord = records.read(profileId)
                    ?: throw AiCredentialUnavailableException(AiCredentialFailureReason.NOT_CONFIGURED)
                try {
                    val record = CredentialRecordCodec.decode(encodedRecord)
                    val associatedData = CredentialAssociatedData.forProfile(profileId)
                    try {
                        cipher.decrypt(record, associatedData)
                    } finally {
                        associatedData.fill(0)
                    }
                } finally {
                    encodedRecord.fill(0)
                }
            }
        } catch (error: AiCredentialUnavailableException) {
            throw error
        } catch (_: Exception) {
            throw AiCredentialUnavailableException(AiCredentialFailureReason.UNAVAILABLE)
        }

        if (plaintext.size !in 1..CredentialEnvelopeLimits.MAXIMUM_PLAINTEXT_BYTES) {
            plaintext.fill(0)
            throw AiCredentialUnavailableException(AiCredentialFailureReason.UNAVAILABLE)
        }
        return try {
            action(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun clear(profileId: String): Boolean {
        AiCredentialProfileIds.requireValid(profileId)
        return try {
            storage.withExclusiveAccess { records -> records.delete(profileId) }
        } catch (_: Exception) {
            throw AiCredentialUnavailableException(AiCredentialFailureReason.UNAVAILABLE)
        }
    }
}

internal object AiCredentialProfileIds {
    private val VALID_PROFILE_ID = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")

    fun requireValid(profileId: String) {
        require(VALID_PROFILE_ID.matches(profileId)) { "AI credential profile ID is invalid" }
    }
}

internal object CredentialAssociatedData {
    private val DOMAIN = "io.github.supermonster003.autojs6.plugin.threestoneai/credential/v1\u0000"
        .toByteArray(StandardCharsets.UTF_8)

    fun forProfile(profileId: String): ByteArray {
        AiCredentialProfileIds.requireValid(profileId)
        val profileBytes = profileId.toByteArray(StandardCharsets.UTF_8)
        return ByteArray(DOMAIN.size + profileBytes.size).also { result ->
            DOMAIN.copyInto(result)
            profileBytes.copyInto(result, destinationOffset = DOMAIN.size)
        }
    }
}

private object CredentialUtf8 {
    fun encode(credential: CharArray): ByteArray {
        require(credential.isNotEmpty()) { "AI credential must not be empty" }
        require(credential.size <= CredentialEnvelopeLimits.MAXIMUM_PLAINTEXT_BYTES) {
            "AI credential is too large"
        }

        val scratch = ByteArray(Math.multiplyExact(credential.size, MAXIMUM_UTF8_BYTES_PER_CHAR))
        try {
            val encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val output = ByteBuffer.wrap(scratch)
            val characters = CharBuffer.wrap(credential)
            val encoded = encoder.encode(characters, output, true)
            if (encoded.isError) encoded.throwException()
            check(encoded.isUnderflow) { "UTF-8 credential encoding did not finish" }
            val flushed = encoder.flush(output)
            if (flushed.isError) flushed.throwException()
            check(flushed.isUnderflow) { "UTF-8 credential encoding did not flush" }

            val encodedSize = output.position()
            require(encodedSize in 1..CredentialEnvelopeLimits.MAXIMUM_PLAINTEXT_BYTES) {
                "AI credential is too large"
            }
            return scratch.copyOf(encodedSize)
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("AI credential text is invalid")
        } finally {
            scratch.fill(0)
        }
    }

    private const val MAXIMUM_UTF8_BYTES_PER_CHAR = 3
}
