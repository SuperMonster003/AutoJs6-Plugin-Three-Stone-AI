package io.github.supermonster003.autojs6.plugin.threestoneai.credential

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptedAiCredentialStoreTest {
    @Test
    fun putPersistsOnlyCiphertextAndClearsOwnedCharacters() {
        val storage = MemoryCredentialRecordStorage()
        val store = EncryptedAiCredentialStore(storage, JvmAesGcmCredentialCipher())
        val credential = "credential-value-snow".toCharArray()
        val expected = "credential-value-snow".toByteArray(StandardCharsets.UTF_8)

        try {
            store.put(PRIMARY_PROFILE, credential)

            assertTrue(credential.all { it == '\u0000' })
            val persisted = requireNotNull(storage.raw(PRIMARY_PROFILE))
            assertFalse(persisted.containsSequence(expected))
            assertTrue(store.isConfigured(PRIMARY_PROFILE))
            assertArrayEquals(expected, store.withCredential(PRIMARY_PROFILE) { it.copyOf() })
        } finally {
            expected.fill(0)
        }
    }

    @Test
    fun decryptedBytesAreClearedAfterSuccessfulCallbackAndCannotEscapeDirectly() {
        val store = storeWithCredential()
        lateinit var observed: ByteArray

        val returned = store.withCredential(PRIMARY_PROFILE) { plaintext ->
            observed = plaintext
            plaintext
        }

        assertSame(observed, returned)
        assertTrue(observed.all { it == 0.toByte() })
    }

    @Test
    fun decryptedBytesAreClearedWhenCallbackFailsWithoutReplacingItsError() {
        val store = storeWithCredential()
        lateinit var observed: ByteArray
        val expected = MarkerException()

        val actual = assertThrows(MarkerException::class.java) {
            store.withCredential(PRIMARY_PROFILE) { plaintext ->
                observed = plaintext
                throw expected
            }
        }

        assertSame(expected, actual)
        assertTrue(observed.all { it == 0.toByte() })
    }

    @Test
    fun missingAndCorruptRecordsFailClosed() {
        val storage = MemoryCredentialRecordStorage()
        val store = EncryptedAiCredentialStore(storage, JvmAesGcmCredentialCipher())

        assertFalse(store.isConfigured(PRIMARY_PROFILE))
        assertEquals(
            AiCredentialFailureReason.NOT_CONFIGURED,
            assertThrows(AiCredentialUnavailableException::class.java) {
                store.withCredential(PRIMARY_PROFILE) { Unit }
            }.reason,
        )

        store.put(PRIMARY_PROFILE, "credential-value".toCharArray())
        storage.mutate(PRIMARY_PROFILE) { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }

        assertFalse(store.isConfigured(PRIMARY_PROFILE))
        assertEquals(
            AiCredentialFailureReason.UNAVAILABLE,
            assertThrows(AiCredentialUnavailableException::class.java) {
                store.withCredential(PRIMARY_PROFILE) { Unit }
            }.reason,
        )
    }

    @Test
    fun authenticatedEnvelopeIsBoundToItsProfile() {
        val storage = MemoryCredentialRecordStorage()
        val store = EncryptedAiCredentialStore(storage, JvmAesGcmCredentialCipher())
        store.put(PRIMARY_PROFILE, "credential-value".toCharArray())

        storage.copy(PRIMARY_PROFILE, SECONDARY_PROFILE)

        assertTrue(store.isConfigured(PRIMARY_PROFILE))
        assertFalse(store.isConfigured(SECONDARY_PROFILE))
        assertEquals(
            AiCredentialFailureReason.UNAVAILABLE,
            assertThrows(AiCredentialUnavailableException::class.java) {
                store.withCredential(SECONDARY_PROFILE) { Unit }
            }.reason,
        )
    }

    @Test
    fun clearRemovesOnlyTheRequestedProfile() {
        val storage = MemoryCredentialRecordStorage()
        val store = EncryptedAiCredentialStore(storage, JvmAesGcmCredentialCipher())
        store.put(PRIMARY_PROFILE, "first-credential".toCharArray())
        store.put(SECONDARY_PROFILE, "second-credential".toCharArray())

        assertTrue(store.clear(PRIMARY_PROFILE))
        assertFalse(store.clear(PRIMARY_PROFILE))
        assertFalse(store.isConfigured(PRIMARY_PROFILE))
        assertTrue(store.isConfigured(SECONDARY_PROFILE))
    }

    @Test
    fun invalidTextAndProfileInputsAreRejectedAndCleared() {
        val store = EncryptedAiCredentialStore(
            MemoryCredentialRecordStorage(),
            JvmAesGcmCredentialCipher(),
        )
        val invalidProfileCredential = "must-be-cleared".toCharArray()
        val malformedCredential = charArrayOf('\uD800')
        val excessiveUtf8Credential = CharArray(6_000) { '\u0800' }
        val excessiveCharacters = CharArray(CredentialEnvelopeLimits.MAXIMUM_PLAINTEXT_BYTES + 1) { 'a' }

        assertThrows(IllegalArgumentException::class.java) {
            store.put("Invalid Profile", invalidProfileCredential)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.put(PRIMARY_PROFILE, malformedCredential)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.put(PRIMARY_PROFILE, excessiveUtf8Credential)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.put(PRIMARY_PROFILE, excessiveCharacters)
        }

        assertTrue(invalidProfileCredential.all { it == '\u0000' })
        assertTrue(malformedCredential.all { it == '\u0000' })
        assertTrue(excessiveUtf8Credential.all { it == '\u0000' })
        assertTrue(excessiveCharacters.all { it == '\u0000' })
        assertThrows(IllegalArgumentException::class.java) { store.isConfigured("profile:invalid") }
        assertThrows(IllegalArgumentException::class.java) { store.clear("") }
    }

    @Test
    fun infrastructureErrorsUseFixedMessagesWithoutRetainingSensitiveCauses() {
        val sensitiveText = "credential-value-that-must-not-escape"
        val store = EncryptedAiCredentialStore(
            ThrowingCredentialRecordStorage(sensitiveText),
            JvmAesGcmCredentialCipher(),
        )
        val credential = sensitiveText.toCharArray()

        val putFailure = assertThrows(AiCredentialUnavailableException::class.java) {
            store.put(PRIMARY_PROFILE, credential)
        }
        val readFailure = assertThrows(AiCredentialUnavailableException::class.java) {
            store.withCredential(PRIMARY_PROFILE) { Unit }
        }

        assertEquals("AI credential is unavailable", putFailure.message)
        assertEquals("AI credential is unavailable", readFailure.message)
        assertFalse(putFailure.toString().contains(sensitiveText))
        assertFalse(readFailure.toString().contains(sensitiveText))
        assertNull(putFailure.cause)
        assertNull(readFailure.cause)
        assertTrue(credential.all { it == '\u0000' })
        assertFalse(store.isConfigured(PRIMARY_PROFILE))
    }

    private fun storeWithCredential(): EncryptedAiCredentialStore =
        EncryptedAiCredentialStore(
            MemoryCredentialRecordStorage(),
            JvmAesGcmCredentialCipher(),
        ).also { store ->
            store.put(PRIMARY_PROFILE, "credential-value".toCharArray())
        }

    private class MemoryCredentialRecordStorage : CredentialRecordStorage {
        private val records = linkedMapOf<String, ByteArray>()

        override fun <T> withExclusiveAccess(action: (CredentialRecordAccess) -> T): T =
            synchronized(records) {
                action(
                    object : CredentialRecordAccess {
                        override fun read(profileId: String): ByteArray? = records[profileId]?.copyOf()

                        override fun write(profileId: String, encodedRecord: ByteArray) {
                            records.put(profileId, encodedRecord.copyOf())?.fill(0)
                        }

                        override fun delete(profileId: String): Boolean = records.remove(profileId)?.let { removed ->
                            removed.fill(0)
                            true
                        } ?: false
                    },
                )
            }

        fun raw(profileId: String): ByteArray? = synchronized(records) { records[profileId]?.copyOf() }

        fun mutate(profileId: String, mutation: (ByteArray) -> Unit) = synchronized(records) {
            mutation(requireNotNull(records[profileId]))
        }

        fun copy(sourceProfileId: String, destinationProfileId: String) = synchronized(records) {
            records[destinationProfileId] = requireNotNull(records[sourceProfileId]).copyOf()
        }
    }

    private class ThrowingCredentialRecordStorage(
        private val sensitiveText: String,
    ) : CredentialRecordStorage {
        override fun <T> withExclusiveAccess(action: (CredentialRecordAccess) -> T): T {
            throw IllegalStateException(sensitiveText)
        }
    }

    private class JvmAesGcmCredentialCipher : CredentialCipher {
        private val key = SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
        private val sequence = AtomicInteger()

        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ): EncryptedCredentialRecord {
            val initializationVector = ByteArray(CredentialEnvelopeLimits.INITIALIZATION_VECTOR_BYTES)
            ByteBuffer.wrap(initializationVector).putInt(
                initializationVector.size - Int.SIZE_BYTES,
                sequence.incrementAndGet(),
            )
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, initializationVector))
            cipher.updateAAD(associatedData)
            val ciphertext = cipher.doFinal(plaintext)
            return try {
                EncryptedCredentialRecord(initializationVector, ciphertext)
            } finally {
                initializationVector.fill(0)
                ciphertext.fill(0)
            }
        }

        override fun decrypt(
            record: EncryptedCredentialRecord,
            associatedData: ByteArray,
        ): ByteArray {
            val initializationVector = record.copyInitializationVector()
            val ciphertext = record.copyCiphertext()
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, initializationVector))
                cipher.updateAAD(associatedData)
                cipher.doFinal(ciphertext)
            } finally {
                initializationVector.fill(0)
                ciphertext.fill(0)
            }
        }
    }

    private class MarkerException : RuntimeException()

    private fun ByteArray.containsSequence(candidate: ByteArray): Boolean {
        if (candidate.isEmpty()) return true
        return indices.any { start ->
            start <= size - candidate.size && candidate.indices.all { offset ->
                this[start + offset] == candidate[offset]
            }
        }
    }

    private companion object {
        const val PRIMARY_PROFILE = "primary"
        const val SECONDARY_PROFILE = "secondary"
    }
}
