package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import android.system.Os
import android.system.OsConstants
import io.github.supermonster003.autojs6.plugin.threestoneai.storage.AppPrivatePathGuard
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** One private atomic document keeps the payload, validator and request timestamps together. */
internal class OnlineAiModelCatalogFileStorage(privateFilesDirectory: File) : OnlineAiModelCatalogStorage {
    private val root = privateFilesDirectory.absoluteFile
    private val directory = File(root, "online-model-catalog")
    private val document = File(directory, "catalog.cache")
    private val lockFile = File(directory, ".catalog.lock")
    private val processLock = PROCESS_LOCKS.computeIfAbsent(lockFile.absolutePath) { Any() }

    override fun <T> withExclusiveAccess(action: (OnlineAiModelCatalogCacheAccess) -> T): T = synchronized(processLock) {
        require(root.isDirectory) { "Model catalog private root is unavailable" }
        require(directory.isDirectory || directory.mkdir()) { "Model catalog cache directory is unavailable" }
        AppPrivatePathGuard.requireDirectChild(directory, root, "Model catalog cache directory")
        requireSafeChild(lockFile)
        RandomAccessFile(lockFile, "rw").use { handle ->
            require(lockFile.isFile) { "Model catalog lock is not a regular file" }
            requireSafeChild(lockFile)
            val lock = handle.channel.lock()
            val access = Access()
            try {
                action(access)
            } finally {
                access.valid = false
                lock.release()
            }
        }
    }

    private inner class Access : OnlineAiModelCatalogCacheAccess {
        var valid = true

        override fun read(): OnlineAiModelCatalogCacheRecord? {
            check(valid) { "Model catalog cache transaction has ended" }
            requireSafeChild(document)
            if (!document.exists()) return null
            require(document.isFile && document.length() in 1L..OnlineAiModelCatalogCacheCodec.MAXIMUM_CACHE_BYTES.toLong()) {
                "Model catalog cache size is invalid"
            }
            return OnlineAiModelCatalogCacheCodec.decode(document.readBytes())
        }

        override fun write(record: OnlineAiModelCatalogCacheRecord) {
            check(valid) { "Model catalog cache transaction has ended" }
            val encoded = OnlineAiModelCatalogCacheCodec.encode(record)
            val temporary = File(directory, ".catalog-${UUID.randomUUID()}.tmp")
            requireSafeChild(temporary)
            requireSafeChild(document)
            require(!temporary.exists()) { "Model catalog cache transaction already exists" }
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(encoded)
                    output.fd.sync()
                }
                Os.rename(temporary.absolutePath, document.absolutePath)
                val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
                try {
                    Os.fsync(descriptor)
                } finally {
                    Os.close(descriptor)
                }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    private fun requireSafeChild(file: File) =
        AppPrivatePathGuard.requireDirectChild(file, directory, "Model catalog cache path")

    private companion object {
        val PROCESS_LOCKS = ConcurrentHashMap<String, Any>()
    }
}

/** Local envelope version is independent of the published catalog schema. */
internal object OnlineAiModelCatalogCacheCodec {
    const val MAXIMUM_CACHE_BYTES = OnlineAiModelCatalogCodec.MAXIMUM_DOCUMENT_BYTES + 4096
    private const val MAGIC = 0x334D4341
    private val ATTEMPT_ID = Regex("[0-9a-f-]{36}")

    fun encode(record: OnlineAiModelCatalogCacheRecord): ByteArray {
        validate(record)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { writer ->
            writer.writeInt(MAGIC)
            writer.writeInt(1)
            writer.writeLong(record.lastCheckedAtMillis)
            writer.writeLong(record.lastAttemptAtMillis)
            writer.writeByte(if (record.lastAttemptFailed) 1 else 0)
            writer.writeUTF(record.etag.orEmpty())
            writer.writeUTF(record.attemptId.orEmpty())
            val payload = record.payload ?: byteArrayOf()
            writer.writeInt(payload.size)
            writer.write(payload)
        }
        return output.toByteArray().also { require(it.size <= MAXIMUM_CACHE_BYTES) }
    }

    fun decode(bytes: ByteArray): OnlineAiModelCatalogCacheRecord {
        require(bytes.size in 1..MAXIMUM_CACHE_BYTES) { "Model catalog cache envelope size is invalid" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { reader ->
            require(reader.readInt() == MAGIC && reader.readInt() == 1) { "Model catalog cache envelope is invalid" }
            val checked = reader.readLong()
            val attempted = reader.readLong()
            val failed = reader.readUnsignedByte().also { require(it in 0..1) }
            val etag = reader.readUTF().ifEmpty { null }
            val attemptId = reader.readUTF().ifEmpty { null }
            val length = reader.readInt().also { require(it in 0..OnlineAiModelCatalogCodec.MAXIMUM_DOCUMENT_BYTES) }
            val payload = if (length == 0) null else ByteArray(length).also(reader::readFully)
            require(reader.read() == -1) { "Model catalog cache envelope has trailing data" }
            OnlineAiModelCatalogCacheRecord(payload, etag, checked, attempted, failed == 1, attemptId).also(::validate)
        }
    }

    private fun validate(record: OnlineAiModelCatalogCacheRecord) {
        require(record.lastCheckedAtMillis >= 0L && record.lastAttemptAtMillis >= 0L)
        require(record.payload == null || record.payload.size in 1..OnlineAiModelCatalogCodec.MAXIMUM_DOCUMENT_BYTES)
        require(record.etag == null || validOnlineAiModelCatalogEtag(record.etag) == record.etag)
        require(record.attemptId == null || ATTEMPT_ID.matches(record.attemptId))
        require(record.payload != null || (record.etag == null && record.lastCheckedAtMillis == 0L))
    }
}
