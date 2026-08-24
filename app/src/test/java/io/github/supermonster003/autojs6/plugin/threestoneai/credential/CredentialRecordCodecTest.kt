package io.github.supermonster003.autojs6.plugin.threestoneai.credential

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class CredentialRecordCodecTest {
    @Test
    fun codecRoundTripAndRecordCopiesAreDefensive() {
        val initializationVector = ByteArray(CredentialEnvelopeLimits.INITIALIZATION_VECTOR_BYTES) { index ->
            (index + 1).toByte()
        }
        val ciphertext = ByteArray(32) { index -> (index + 20).toByte() }
        val expectedInitializationVector = initializationVector.copyOf()
        val expectedCiphertext = ciphertext.copyOf()
        val record = EncryptedCredentialRecord(initializationVector, ciphertext)

        initializationVector.fill(0)
        ciphertext.fill(0)
        val copiedInitializationVector = record.copyInitializationVector()
        val copiedCiphertext = record.copyCiphertext()
        assertArrayEquals(expectedInitializationVector, copiedInitializationVector)
        assertArrayEquals(expectedCiphertext, copiedCiphertext)

        copiedInitializationVector.fill(0)
        copiedCiphertext.fill(0)
        assertArrayEquals(expectedInitializationVector, record.copyInitializationVector())
        assertArrayEquals(expectedCiphertext, record.copyCiphertext())
        assertEquals(record, CredentialRecordCodec.decode(CredentialRecordCodec.encode(record)))
        assertEquals(
            record.hashCode(),
            CredentialRecordCodec.decode(CredentialRecordCodec.encode(record)).hashCode(),
        )
    }

    @Test
    fun recordDescriptionRevealsSizesOnly() {
        val record = record()

        assertEquals("EncryptedCredentialRecord(ivBytes=12, ciphertextBytes=32)", record.toString())
        assertFalse(record.toString().contains("20, 21"))
    }

    @Test
    fun codecRejectsUnknownHeadersAndInconsistentLengths() {
        val encoded = CredentialRecordCodec.encode(record())
        val badMagic = encoded.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        val badVersion = encoded.copyOf().also { it[4] = 2 }
        val badAlgorithm = encoded.copyOf().also { it[5] = 2 }
        val badInitializationVectorSize = encoded.copyOf().also { bytes ->
            ByteBuffer.wrap(bytes).putShort(6, 11)
        }
        val negativeCiphertextSize = encoded.copyOf().also { bytes ->
            ByteBuffer.wrap(bytes).putInt(8, -1)
        }
        val inconsistentCiphertextSize = encoded.copyOf().also { bytes ->
            ByteBuffer.wrap(bytes).putInt(8, 31)
        }
        val trailingData = encoded.copyOf(encoded.size + 1)
        val truncated = encoded.copyOf(encoded.size - 1)

        listOf(
            badMagic,
            badVersion,
            badAlgorithm,
            badInitializationVectorSize,
            negativeCiphertextSize,
            inconsistentCiphertextSize,
            trailingData,
            truncated,
        ).forEach { malformed ->
            assertThrows(IllegalArgumentException::class.java) {
                CredentialRecordCodec.decode(malformed)
            }
        }
    }

    @Test
    fun recordRejectsInvalidEnvelopeSizes() {
        val validInitializationVector = ByteArray(CredentialEnvelopeLimits.INITIALIZATION_VECTOR_BYTES)

        assertThrows(IllegalArgumentException::class.java) {
            EncryptedCredentialRecord(ByteArray(11), ByteArray(32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedCredentialRecord(
                validInitializationVector,
                ByteArray(CredentialEnvelopeLimits.MINIMUM_CIPHERTEXT_BYTES - 1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedCredentialRecord(
                validInitializationVector,
                ByteArray(CredentialEnvelopeLimits.MAXIMUM_CIPHERTEXT_BYTES + 1),
            )
        }
        assertNotEquals(record(), EncryptedCredentialRecord(validInitializationVector, ByteArray(32)))
    }

    private fun record() = EncryptedCredentialRecord(
        initializationVector = ByteArray(CredentialEnvelopeLimits.INITIALIZATION_VECTOR_BYTES) { index ->
            (index + 1).toByte()
        },
        ciphertext = ByteArray(32) { index -> (index + 20).toByte() },
    )
}
