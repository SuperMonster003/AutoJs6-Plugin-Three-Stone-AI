package io.github.supermonster003.autojs6.plugin.threestoneai.credential

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

internal object CredentialEnvelopeLimits {
    const val INITIALIZATION_VECTOR_BYTES = 12
    const val AUTHENTICATION_TAG_BYTES = 16
    const val MAXIMUM_PLAINTEXT_BYTES = 16 * 1024
    const val MINIMUM_CIPHERTEXT_BYTES = AUTHENTICATION_TAG_BYTES
    const val MAXIMUM_CIPHERTEXT_BYTES = MAXIMUM_PLAINTEXT_BYTES + AUTHENTICATION_TAG_BYTES
}

/** Immutable defensive copies of one AES-GCM envelope; [toString] deliberately exposes sizes only. */
internal class EncryptedCredentialRecord(
    initializationVector: ByteArray,
    ciphertext: ByteArray,
) {
    private val initializationVector = initializationVector.copyOf()
    private val ciphertext = ciphertext.copyOf()

    init {
        require(this.initializationVector.size == CredentialEnvelopeLimits.INITIALIZATION_VECTOR_BYTES) {
            "Encrypted credential initialization vector is invalid"
        }
        require(
            this.ciphertext.size in
                CredentialEnvelopeLimits.MINIMUM_CIPHERTEXT_BYTES..
                CredentialEnvelopeLimits.MAXIMUM_CIPHERTEXT_BYTES
        ) {
            "Encrypted credential ciphertext is invalid"
        }
    }

    fun copyInitializationVector(): ByteArray = initializationVector.copyOf()

    fun copyCiphertext(): ByteArray = ciphertext.copyOf()

    override fun equals(other: Any?): Boolean =
        other is EncryptedCredentialRecord &&
            initializationVector.contentEquals(other.initializationVector) &&
            ciphertext.contentEquals(other.ciphertext)

    override fun hashCode(): Int = 31 * initializationVector.contentHashCode() + ciphertext.contentHashCode()

    override fun toString(): String =
        "EncryptedCredentialRecord(ivBytes=${initializationVector.size}, ciphertextBytes=${ciphertext.size})"
}

/** Small versioned binary envelope. No profile identifier or plaintext is persisted in the record. */
internal object CredentialRecordCodec {
    private const val MAGIC = 0x33534352 // 3SCR
    private const val VERSION = 1
    private const val ALGORITHM_AES_GCM = 1
    private const val HEADER_BYTES = 12
    private const val MINIMUM_ENCODED_BYTES =
        HEADER_BYTES +
            CredentialEnvelopeLimits.INITIALIZATION_VECTOR_BYTES +
            CredentialEnvelopeLimits.MINIMUM_CIPHERTEXT_BYTES

    const val MAXIMUM_ENCODED_BYTES =
        HEADER_BYTES +
            CredentialEnvelopeLimits.INITIALIZATION_VECTOR_BYTES +
            CredentialEnvelopeLimits.MAXIMUM_CIPHERTEXT_BYTES

    fun encode(record: EncryptedCredentialRecord): ByteArray {
        val initializationVector = record.copyInitializationVector()
        val ciphertext = record.copyCiphertext()
        return try {
            val bytes = ByteArrayOutputStream(HEADER_BYTES + initializationVector.size + ciphertext.size)
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeByte(VERSION)
                output.writeByte(ALGORITHM_AES_GCM)
                output.writeShort(initializationVector.size)
                output.writeInt(ciphertext.size)
                output.write(initializationVector)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        } finally {
            initializationVector.fill(0)
            ciphertext.fill(0)
        }
    }

    fun decode(encodedRecord: ByteArray): EncryptedCredentialRecord {
        require(encodedRecord.size in MINIMUM_ENCODED_BYTES..MAXIMUM_ENCODED_BYTES) {
            "Encrypted credential record size is invalid"
        }
        try {
            DataInputStream(ByteArrayInputStream(encodedRecord)).use { input ->
                require(input.readInt() == MAGIC) { "Encrypted credential record magic is invalid" }
                require(input.readUnsignedByte() == VERSION) { "Encrypted credential record version is invalid" }
                require(input.readUnsignedByte() == ALGORITHM_AES_GCM) {
                    "Encrypted credential record algorithm is invalid"
                }
                val initializationVectorSize = input.readUnsignedShort()
                val ciphertextSize = input.readInt()
                require(initializationVectorSize == CredentialEnvelopeLimits.INITIALIZATION_VECTOR_BYTES) {
                    "Encrypted credential initialization vector size is invalid"
                }
                require(
                    ciphertextSize in
                        CredentialEnvelopeLimits.MINIMUM_CIPHERTEXT_BYTES..
                        CredentialEnvelopeLimits.MAXIMUM_CIPHERTEXT_BYTES
                ) {
                    "Encrypted credential ciphertext size is invalid"
                }
                val expectedSize = HEADER_BYTES.toLong() + initializationVectorSize + ciphertextSize
                require(expectedSize == encodedRecord.size.toLong()) {
                    "Encrypted credential record length is invalid"
                }

                val initializationVector = ByteArray(initializationVectorSize)
                val ciphertext = ByteArray(ciphertextSize)
                input.readFully(initializationVector)
                input.readFully(ciphertext)
                check(input.read() == -1) { "Encrypted credential record has trailing data" }
                return EncryptedCredentialRecord(initializationVector, ciphertext)
            }
        } catch (_: EOFException) {
            throw IllegalArgumentException("Encrypted credential record is truncated")
        }
    }
}
