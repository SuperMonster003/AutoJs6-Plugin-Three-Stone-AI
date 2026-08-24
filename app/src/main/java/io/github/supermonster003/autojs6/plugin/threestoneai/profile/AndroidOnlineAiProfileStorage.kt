package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Private, crash-durable profile metadata shared by the launcher and Binder processes. */
internal class FileOnlineAiProfileDocumentStorage(
    privateFilesDirectory: File,
) : OnlineAiProfileDocumentStorage {
    private val privateFilesDirectory = privateFilesDirectory.absoluteFile
    private val directory = File(this.privateFilesDirectory, DIRECTORY_NAME)
    private val documentFile = File(directory, DOCUMENT_FILE_NAME)
    private val lockFile = File(directory, LOCK_FILE_NAME)
    private val processLock = PROCESS_LOCKS.computeIfAbsent(lockFile.absolutePath) { Any() }

    override fun <T> withExclusiveAccess(action: (OnlineAiProfileDocumentAccess) -> T): T =
        synchronized(processLock) {
            ensureDirectory()
            requireSafeDirectChild(lockFile)
            RandomAccessFile(lockFile, "rw").use { lockHandle ->
                requireSafeRegularFile(lockFile)
                val fileLock = lockHandle.channel.lock()
                try {
                    val access = LockedDocumentAccess()
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

    private inner class LockedDocumentAccess : OnlineAiProfileDocumentAccess {
        private var valid = true

        override fun read(): ByteArray? {
            check(valid) { "Online AI profile document access has ended" }
            if (!documentFile.exists()) return null
            requireSafeRegularFile(documentFile)
            require(documentFile.length() in 1L..OnlineAiProfileCodec.MAXIMUM_DOCUMENT_BYTES.toLong()) {
                "Online AI profile document size is invalid"
            }
            return documentFile.readBytes().also { bytes ->
                require(bytes.size in 1..OnlineAiProfileCodec.MAXIMUM_DOCUMENT_BYTES) {
                    "Online AI profile document size changed while reading"
                }
            }
        }

        override fun write(encodedDocument: ByteArray) {
            check(valid) { "Online AI profile document access has ended" }
            require(encodedDocument.size in 1..OnlineAiProfileCodec.MAXIMUM_DOCUMENT_BYTES) {
                "Online AI profile document size is invalid"
            }
            val temporary = File(directory, ".profiles-${UUID.randomUUID()}.tmp")
            requireSafeDirectChild(temporary)
            require(!temporary.exists()) { "Online AI profile transaction file already exists" }

            val publicationFailure = try {
                FileOutputStream(temporary).use { output ->
                    output.write(encodedDocument)
                    output.fd.sync()
                }
                Os.rename(temporary.absolutePath, documentFile.absolutePath)
                syncDirectory()
                null
            } catch (error: Throwable) {
                error
            } finally {
                if (temporary.exists()) temporary.delete()
            }
            if (publicationFailure == null) return

            val publicationReachedTarget = runCatching { read()?.contentEquals(encodedDocument) == true }
                .getOrDefault(false)
            if (!publicationReachedTarget) throw publicationFailure
            try {
                syncDirectory()
            } catch (retryFailure: Throwable) {
                retryFailure.addSuppressed(publicationFailure)
                throw retryFailure
            }
        }

        fun invalidate() {
            valid = false
        }
    }

    private fun ensureDirectory() {
        require(privateFilesDirectory.isDirectory) { "Private online AI profile root is unavailable" }
        require(directory.isDirectory || directory.mkdir()) { "Private online AI profile storage is unavailable" }
        require(directory.canonicalFile == directory.absoluteFile) {
            "Private online AI profile storage must not use a link"
        }
        require(directory.canonicalFile.parentFile == privateFilesDirectory.canonicalFile) {
            "Private online AI profile storage escaped its root"
        }
    }

    private fun requireSafeRegularFile(file: File) {
        require(file.isFile) { "Private online AI profile path is not a regular file" }
        requireSafeDirectChild(file)
    }

    private fun requireSafeDirectChild(file: File) {
        require(file.absoluteFile.parentFile == directory.absoluteFile) {
            "Private online AI profile path escaped its directory"
        }
        require(file.canonicalFile == file.absoluteFile) {
            "Private online AI profile path must not use a link"
        }
        require(file.canonicalFile.parentFile == directory.canonicalFile) {
            "Private online AI profile path escaped through a link"
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
        const val DIRECTORY_NAME = "ai-profiles"
        const val DOCUMENT_FILE_NAME = "profiles.json"
        const val LOCK_FILE_NAME = ".profiles.lock"
        val PROCESS_LOCKS = ConcurrentHashMap<String, Any>()
    }
}
