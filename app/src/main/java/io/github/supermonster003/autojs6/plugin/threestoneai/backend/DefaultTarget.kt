package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * The one global default target (roadmap 1.7.0 P5.3, D97, D114).
 *
 * The stored choice wins while it names an available target. Otherwise the automatic default of the
 * merged catalog applies: the default online service's preferred model, then the selected local model.
 */
internal object DefaultTargetPolicy {
    private const val MAX_ID_LENGTH = 1024

    fun effective(stored: String?, catalog: AiTargetCatalog): String? =
        stored?.takeIf { targetId -> catalog.targets.any { it.targetId == targetId && it.available } }
            ?: catalog.defaultTargetId

    /** First-run value: what 1.6 used, so the upgrade itself changes nothing. */
    fun migrated(catalog: AiTargetCatalog): String? = catalog.defaultTargetId

    fun isValidId(targetId: String): Boolean =
        targetId.length in 1..MAX_ID_LENGTH && targetId.none { it.isWhitespace() || it.isISOControl() }
}

internal interface DefaultTargetStore {
    /** The stored choice (null when none was made); the first call stores and returns [migrated]. */
    fun readOrMigrate(migrated: () -> String?): String?

    /** Stores [targetId] as the choice; null means "no choice", which leaves the automatic default. */
    fun write(targetId: String?)

    /**
     * Replaces the stored choice when [mapping] has an entry for it (a service's models were
     * reordered, D122). Does nothing when no choice was stored or migrated yet.
     */
    fun replace(mapping: Map<String, String>)
}

/**
 * One small document shared by the UI and provider processes: written under a process lock plus a
 * file lock and replaced by an atomic rename, so readers never see a partial value. A missing or
 * unreadable document counts as "not migrated yet".
 */
internal class FileDefaultTargetStore(
    privateFilesDirectory: File,
    private val replaceFile: (File, File) -> Unit = { source, target -> Os.rename(source.absolutePath, target.absolutePath) },
) : DefaultTargetStore {
    private val directory = File(privateFilesDirectory, DIRECTORY_NAME)
    private val document = File(directory, DOCUMENT_NAME)
    private val lockFile = File(directory, LOCK_FILE_NAME)
    private val processLock = PROCESS_LOCKS.computeIfAbsent(lockFile.absolutePath) { Any() }

    override fun readOrMigrate(migrated: () -> String?): String? {
        read()?.let { return it.targetId }
        return exclusive { read()?.targetId ?: migrated().also(::store) }
    }

    override fun write(targetId: String?) {
        require(targetId == null || DefaultTargetPolicy.isValidId(targetId)) { "Invalid default target ID" }
        exclusive { store(targetId) }
    }

    override fun replace(mapping: Map<String, String>) {
        if (mapping.isEmpty()) return
        require(mapping.values.all(DefaultTargetPolicy::isValidId)) { "Invalid default target ID" }
        exclusive {
            val current = read()?.targetId ?: return@exclusive
            mapping[current]?.let(::store)
        }
    }

    private fun read(): Stored? {
        val bytes = try {
            if (!document.isFile || document.length() > MAX_DOCUMENT_BYTES) return null
            document.readBytes()
        } catch (_: IOException) {
            return null
        }
        val lines = bytes.toString(Charsets.UTF_8).split('\n')
        if (lines.size != 3 || lines[0] != FORMAT || lines[2].isNotEmpty()) return null
        val targetId = lines[1]
        return when {
            targetId.isEmpty() -> Stored(null)
            DefaultTargetPolicy.isValidId(targetId) -> Stored(targetId)
            else -> null
        }
    }

    private fun store(targetId: String?) {
        val temporary = File(directory, "$DOCUMENT_NAME.tmp")
        FileOutputStream(temporary).use { output ->
            output.write("$FORMAT\n${targetId.orEmpty()}\n".toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        replaceFile(temporary, document)
    }

    private fun <T> exclusive(action: () -> T): T = synchronized(processLock) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Unable to create the default target directory")
        }
        RandomAccessFile(lockFile, "rw").use { handle ->
            val lock = handle.channel.lock()
            try {
                action()
            } finally {
                lock.release()
            }
        }
    }

    private class Stored(val targetId: String?)

    private companion object {
        const val DIRECTORY_NAME = "ai-default-target"
        const val DOCUMENT_NAME = "default-target"
        const val LOCK_FILE_NAME = ".default-target.lock"
        const val FORMAT = "v1"
        const val MAX_DOCUMENT_BYTES = 4096L
        val PROCESS_LOCKS = ConcurrentHashMap<String, Any>()
    }
}
