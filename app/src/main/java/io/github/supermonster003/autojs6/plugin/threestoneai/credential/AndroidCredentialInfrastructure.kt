package io.github.supermonster003.autojs6.plugin.threestoneai.credential

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.MessageDigest
import java.security.UnrecoverableKeyException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM whose non-exportable master key lives in Android Keystore. */
internal class AndroidKeystoreCredentialCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : CredentialCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): EncryptedCredentialRecord {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        return try {
            EncryptedCredentialRecord(cipher.iv, ciphertext)
        } finally {
            ciphertext.fill(0)
        }
    }

    override fun decrypt(record: EncryptedCredentialRecord, associatedData: ByteArray): ByteArray {
        val initializationVector = record.copyInitializationVector()
        val ciphertext = record.copyCiphertext()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                decryptionKey(),
                GCMParameterSpec(AUTHENTICATION_TAG_BITS, initializationVector),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext)
        } finally {
            initializationVector.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = loadKeyStore()
        existingSecretKey(keyStore)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun decryptionKey(): SecretKey = existingSecretKey(loadKeyStore())
        ?: throw UnrecoverableKeyException("AI credential master key is unavailable")

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun existingSecretKey(keyStore: KeyStore): SecretKey? {
        if (!keyStore.containsAlias(keyAlias)) return null
        return keyStore.getKey(keyAlias, null) as? SecretKey
            ?: throw UnrecoverableKeyException("AI credential master key has an invalid type")
    }

    private companion object {
        const val DEFAULT_KEY_ALIAS =
            "io.github.supermonster003.autojs6.plugin.threestoneai.credentials.v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val AUTHENTICATION_TAG_BITS = CredentialEnvelopeLimits.AUTHENTICATION_TAG_BYTES * 8
    }
}

/**
 * App-private ciphertext records shared coherently by the default and `:provider` processes.
 * Every operation is serialized by a JVM mutex and an exclusive OS file lock. Publications use
 * fsync + atomic rename + directory fsync so readers observe either the old or the new envelope.
 */
internal class FileCredentialRecordStorage(
    privateFilesDirectory: File,
) : CredentialRecordStorage {
    private val privateFilesDirectory = privateFilesDirectory.absoluteFile
    private val directory = File(this.privateFilesDirectory, DIRECTORY_NAME)
    private val lockFile = File(directory, LOCK_FILE_NAME)
    private val processLock = PROCESS_LOCKS.computeIfAbsent(lockFile.absolutePath) { Any() }

    override fun <T> withExclusiveAccess(action: (CredentialRecordAccess) -> T): T =
        synchronized(processLock) {
            ensureDirectory()
            requireSafeDirectChild(lockFile)
            RandomAccessFile(lockFile, "rw").use { lockHandle ->
                requireSafeRegularFile(lockFile)
                val fileLock = lockHandle.channel.lock()
                try {
                    val access = LockedRecordAccess()
                    try {
                        action(access)
                    } finally {
                        access.invalidate()
                    }
                } finally {
                    fileLock.release()
                }
            }
        }

    private inner class LockedRecordAccess : CredentialRecordAccess {
        private var valid = true

        override fun read(profileId: String): ByteArray? {
            check(valid) { "Credential record access has ended" }
            val target = recordFile(profileId)
            if (!target.exists()) return null
            requireSafeRegularFile(target)
            require(target.length() in 1L..CredentialRecordCodec.MAXIMUM_ENCODED_BYTES.toLong()) {
                "Encrypted credential record size is invalid"
            }
            return target.readBytes().also { bytes ->
                require(bytes.size in 1..CredentialRecordCodec.MAXIMUM_ENCODED_BYTES) {
                    "Encrypted credential record size changed while reading"
                }
            }
        }

        override fun write(profileId: String, encodedRecord: ByteArray) {
            check(valid) { "Credential record access has ended" }
            require(encodedRecord.size in 1..CredentialRecordCodec.MAXIMUM_ENCODED_BYTES) {
                "Encrypted credential record size is invalid"
            }
            val target = recordFile(profileId)
            val temporary = File(directory, ".credential-${UUID.randomUUID()}.tmp")
            requireSafeDirectChild(temporary)
            require(!temporary.exists()) { "Credential transaction file already exists" }

            val publicationFailure = try {
                FileOutputStream(temporary).use { output ->
                    output.write(encodedRecord)
                    output.fd.sync()
                }
                Os.rename(temporary.absolutePath, target.absolutePath)
                syncDirectory()
                null
            } catch (error: Throwable) {
                error
            } finally {
                if (temporary.exists()) temporary.delete()
            }
            if (publicationFailure == null) return

            val current = runCatching { read(profileId) }.getOrNull()
            val publicationReachedTarget = try {
                current?.contentEquals(encodedRecord) == true
            } finally {
                current?.fill(0)
            }
            if (!publicationReachedTarget) throw publicationFailure
            try {
                syncDirectory()
            } catch (retryFailure: Throwable) {
                retryFailure.addSuppressed(publicationFailure)
                throw retryFailure
            }
        }

        override fun delete(profileId: String): Boolean {
            check(valid) { "Credential record access has ended" }
            val target = recordFile(profileId)
            if (!target.exists()) return false
            requireSafeRegularFile(target)
            require(target.delete() || !target.exists()) { "Encrypted credential record cannot be removed" }
            try {
                syncDirectory()
            } catch (firstFailure: Throwable) {
                if (target.exists()) throw firstFailure
                try {
                    syncDirectory()
                } catch (retryFailure: Throwable) {
                    retryFailure.addSuppressed(firstFailure)
                    throw retryFailure
                }
            }
            return true
        }

        fun invalidate() {
            valid = false
        }
    }

    private fun recordFile(profileId: String): File {
        AiCredentialProfileIds.requireValid(profileId)
        val profileDigest = MessageDigest.getInstance("SHA-256")
            .digest(profileId.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        return File(directory, "credential-$profileDigest.bin").also(::requireSafeDirectChild)
    }

    private fun ensureDirectory() {
        require(privateFilesDirectory.isDirectory) { "Private credential storage root is unavailable" }
        require(directory.isDirectory || directory.mkdir()) { "Private credential storage is unavailable" }
        require(directory.canonicalFile == directory.absoluteFile) {
            "Private credential storage must not use a link"
        }
        require(directory.canonicalFile.parentFile == privateFilesDirectory.canonicalFile) {
            "Private credential storage escaped its root"
        }
    }

    private fun requireSafeRegularFile(file: File) {
        require(file.isFile) { "Private credential path is not a regular file" }
        requireSafeDirectChild(file)
    }

    private fun requireSafeDirectChild(file: File) {
        require(file.absoluteFile.parentFile == directory.absoluteFile) {
            "Private credential path escaped its directory"
        }
        require(file.canonicalFile == file.absoluteFile) {
            "Private credential path must not use a link"
        }
        require(file.canonicalFile.parentFile == directory.canonicalFile) {
            "Private credential path escaped through a link"
        }
    }

    private fun syncDirectory() {
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "ai-credentials"
        const val LOCK_FILE_NAME = ".records.lock"
        val PROCESS_LOCKS = ConcurrentHashMap<String, Any>()
    }
}
