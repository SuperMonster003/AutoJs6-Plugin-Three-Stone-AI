package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Maps LiteRT-LM engine cache files to the managed model file they were built from.
 *
 * LiteRT-LM 0.15.0 (runtime/executor/executor_settings_base.cc) names every cache in the configured
 * cache directory as Basename(model) + "_" + <model mtime seconds> + "_" + <model size> + an optional
 * sub-model infix + a fixed suffix (".xnnpack_cache", "_mldrift_program_cache.bin",
 * "_mldrift_weight_cache.bin", ".mtp_drafter"). The library only deletes stale caches of the same
 * model basename when it rebuilds one, so caches of a deleted model are never reclaimed by itself.
 * Managed model files are content-addressed as model-<sha256>.litertlm, therefore the owner is the
 * name prefix up to the first "_" or "." after ".litertlm". Unrecognized names are never touched.
 */
internal object LiteRtLmEngineCachePolicy {
    private val CACHE_NAME = Regex("^(model-[0-9a-f]{64}\\.litertlm)[._].+$")

    /** Returns the managed model file name that owns [cacheFileName], or null when it is not a cache name. */
    fun ownerModelFileName(cacheFileName: String): String? =
        CACHE_NAME.matchEntire(cacheFileName)?.groupValues?.get(1)

    /** Caches built from [modelFileName], unless that model file is still retained. */
    fun cachesOwnedBy(
        modelFileName: String,
        cacheFileNames: Collection<String>,
        retainedModelFileNames: Set<String>,
    ): List<String> {
        if (modelFileName in retainedModelFileNames) return emptyList()
        return cacheFileNames.asSequence()
            .distinct()
            .filter { ownerModelFileName(it) == modelFileName }
            .sorted()
            .toList()
    }

    /** Caches whose owning model file is neither in the catalog nor on disk any more. */
    fun orphanCaches(
        cacheFileNames: Collection<String>,
        retainedModelFileNames: Set<String>,
    ): List<String> = cacheFileNames.asSequence()
        .distinct()
        .filter { name -> ownerModelFileName(name)?.let { it !in retainedModelFileNames } == true }
        .sorted()
        .toList()

    /**
     * A cache written within [RECENT_WRITE_GRACE_MILLIS] may still be under construction by an engine
     * in this or the provider process; it is left for a later sweep. Engines that already mapped a
     * cache keep using the unlinked file, so deleting an older cache never disturbs inference.
     */
    fun isRecentlyWritten(lastModifiedMillis: Long, nowMillis: Long): Boolean {
        val age = nowMillis - lastModifiedMillis
        return age in -RECENT_WRITE_GRACE_MILLIS until RECENT_WRITE_GRACE_MILLIS
    }

    const val RECENT_WRITE_GRACE_MILLIS = 10L * 60L * 1_000L
}

internal data class LiteRtLmEngineCacheCleanup(
    val removedFileCount: Int,
    val releasedBytes: Long,
    val deferredFileCount: Int,
) {
    companion object {
        val NONE = LiteRtLmEngineCacheCleanup(0, 0L, 0)
    }
}

/** What a directory entry is, without following links. */
internal enum class LiteRtLmCacheEntryKind { MISSING, DIRECTORY, REGULAR_FILE, OTHER }

/** Deletes engine caches in [directory]; never creates the directory and never follows links. */
internal class LiteRtLmEngineCacheStore(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val entryKind: (File) -> LiteRtLmCacheEntryKind = ::lstatEntryKind,
) {
    fun deleteCachesOf(
        modelFileName: String,
        retainedModelFileNames: Set<String>,
    ): LiteRtLmEngineCacheCleanup = delete { names ->
        LiteRtLmEngineCachePolicy.cachesOwnedBy(modelFileName, names, retainedModelFileNames)
    }

    fun deleteOrphans(retainedModelFileNames: Set<String>): LiteRtLmEngineCacheCleanup = delete { names ->
        LiteRtLmEngineCachePolicy.orphanCaches(names, retainedModelFileNames)
    }

    private fun delete(select: (Collection<String>) -> List<String>): LiteRtLmEngineCacheCleanup {
        if (entryKind(directory) != LiteRtLmCacheEntryKind.DIRECTORY) return LiteRtLmEngineCacheCleanup.NONE
        val names = directory.list() ?: throw IllegalStateException("Engine cache directory cannot be listed")
        require(names.size <= MAXIMUM_CACHE_DIRECTORY_ENTRIES) {
            "Engine cache directory contains too many entries for bounded cleanup"
        }
        val nowMillis = clock()
        var removed = 0
        var released = 0L
        var deferred = 0
        select(names.asList()).forEach { name ->
            val candidate = File(directory, name)
            if (entryKind(candidate) != LiteRtLmCacheEntryKind.REGULAR_FILE) return@forEach
            if (LiteRtLmEngineCachePolicy.isRecentlyWritten(candidate.lastModified(), nowMillis)) {
                deferred++
                return@forEach
            }
            val length = candidate.length()
            if (candidate.delete()) {
                removed++
                released = Math.addExact(released, length)
            } else if (candidate.exists()) {
                throw IllegalStateException("Engine cache file cannot be deleted")
            }
        }
        return LiteRtLmEngineCacheCleanup(removed, released, deferred)
    }

    companion object {
        const val MAXIMUM_CACHE_DIRECTORY_ENTRIES = 4_096
    }
}

private fun lstatEntryKind(file: File): LiteRtLmCacheEntryKind = try {
    val mode = Os.lstat(file.absolutePath).st_mode
    when {
        OsConstants.S_ISDIR(mode) -> LiteRtLmCacheEntryKind.DIRECTORY
        OsConstants.S_ISREG(mode) -> LiteRtLmCacheEntryKind.REGULAR_FILE
        else -> LiteRtLmCacheEntryKind.OTHER
    }
} catch (error: ErrnoException) {
    if (error.errno == OsConstants.ENOENT) LiteRtLmCacheEntryKind.MISSING else throw error
}
