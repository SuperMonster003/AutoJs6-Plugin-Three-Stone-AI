package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmEngineCachesTest {
    private val directory: File = Files.createTempDirectory("litertlm-cache-test").toFile()
    private val nowMillis = 1_800_000_000_000L
    private val oldMillis = nowMillis - 24L * 60L * 60L * 1_000L

    @After
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun everyLiteRtLmCacheSuffixMapsToTheModelFileItWasBuiltFrom() {
        val model = modelFile('a')
        // Basename(model) + "_" + <mtime seconds> + "_" + <size> + [sub-model infix] + suffix.
        listOf(
            "${model}_1763101680_1546125312.xnnpack_cache",
            "${model}_1763101680_1546125312_mldrift_program_cache.bin",
            "${model}_1763101680_1546125312_mldrift_weight_cache.bin",
            "${model}_1763101680_1546125312.vision_encoder.xnnpack_cache",
            "${model}_1763101680_1546125312.mtp_drafter",
            "$model.xnnpack_cache",
        ).forEach { cache ->
            assertEquals(cache, model, LiteRtLmEngineCachePolicy.ownerModelFileName(cache))
        }
    }

    @Test
    fun namesThatAreNotManagedModelCachesHaveNoOwner() {
        val model = modelFile('b')
        listOf(
            model,
            "${model}x_1_2.xnnpack_cache",
            "model-${"b".repeat(63)}.litertlm_1_2.xnnpack_cache",
            "model-${"B".repeat(64)}.litertlm_1_2.xnnpack_cache",
            "other.litertlm_1_2.xnnpack_cache",
            "gemma.task_1_2.xnnpack_cache",
            "README",
            "",
        ).forEach { name ->
            assertNull(name, LiteRtLmEngineCachePolicy.ownerModelFileName(name))
        }
    }

    @Test
    fun cachesOfARetainedModelAreNeverSelectedForTheDeletedModel() {
        val deleted = modelFile('c')
        val kept = modelFile('d')
        val names = listOf(cache(deleted, 1), cache(deleted, 2), cache(kept, 1), "unrelated.bin")

        assertEquals(
            listOf(cache(deleted, 1), cache(deleted, 2)),
            LiteRtLmEngineCachePolicy.cachesOwnedBy(deleted, names, setOf(kept)),
        )
        assertEquals(
            emptyList<String>(),
            LiteRtLmEngineCachePolicy.cachesOwnedBy(deleted, names, setOf(deleted, kept)),
        )
    }

    @Test
    fun orphansAreCachesWhoseModelFileIsNoLongerRetained() {
        val present = modelFile('e')
        val gone = modelFile('f')
        val names = listOf(cache(present, 1), cache(gone, 1), cache(gone, 2), "notes.txt")

        assertEquals(
            listOf(cache(gone, 1), cache(gone, 2)),
            LiteRtLmEngineCachePolicy.orphanCaches(names, setOf(present)),
        )
        assertEquals(
            emptyList<String>(),
            LiteRtLmEngineCachePolicy.orphanCaches(names, setOf(present, gone)),
        )
    }

    @Test
    fun recentWritesAreDeferredInBothClockDirections() {
        val grace = LiteRtLmEngineCachePolicy.RECENT_WRITE_GRACE_MILLIS
        assertTrue(LiteRtLmEngineCachePolicy.isRecentlyWritten(nowMillis, nowMillis))
        assertTrue(LiteRtLmEngineCachePolicy.isRecentlyWritten(nowMillis - grace + 1, nowMillis))
        assertTrue(LiteRtLmEngineCachePolicy.isRecentlyWritten(nowMillis + grace - 1, nowMillis))
        assertFalse(LiteRtLmEngineCachePolicy.isRecentlyWritten(nowMillis - grace, nowMillis))
        assertFalse(LiteRtLmEngineCachePolicy.isRecentlyWritten(nowMillis + grace + 1, nowMillis))
    }

    @Test
    fun deletingAModelRemovesOnlyItsOwnOldCaches() {
        val deleted = modelFile('1')
        val kept = modelFile('2')
        val deletedCpu = write(cache(deleted, 1), 7)
        val deletedGpu = write("${deleted}_1_2_mldrift_weight_cache.bin", 5)
        val deletedBuilding = write(cache(deleted, 3), 11, lastModified = nowMillis - 1_000L)
        val keptCache = write(cache(kept, 1), 13)
        val unrelated = write("unrelated.bin", 17)

        val result = store().deleteCachesOf(deleted, retainedModelFileNames = setOf(kept))

        assertEquals(LiteRtLmEngineCacheCleanup(removedFileCount = 2, releasedBytes = 12L, deferredFileCount = 1), result)
        assertFalse(deletedCpu.exists())
        assertFalse(deletedGpu.exists())
        assertTrue(deletedBuilding.exists())
        assertTrue(keptCache.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun aModelStillOnDiskOrInTheCatalogKeepsItsCaches() {
        val model = modelFile('3')
        val cacheFile = write(cache(model, 1), 19)

        assertEquals(
            LiteRtLmEngineCacheCleanup.NONE,
            store().deleteCachesOf(model, retainedModelFileNames = setOf(model)),
        )
        assertEquals(LiteRtLmEngineCacheCleanup.NONE, store().deleteOrphans(setOf(model)))
        assertTrue(cacheFile.exists())
    }

    @Test
    fun orphanSweepKeepsRetainedRecentUnrelatedAndNonRegularEntries() {
        val present = modelFile('4')
        val gone = modelFile('5')
        val presentCache = write(cache(present, 1), 3)
        val goneCache = write(cache(gone, 1), 23)
        val goneRecent = write(cache(gone, 2), 29, lastModified = nowMillis)
        val unrelated = write("model-notes.litertlm_1_2.xnnpack_cache", 31)
        val goneDirectory = File(directory, cache(gone, 3)).apply { check(mkdir()) }

        val result = store().deleteOrphans(retainedModelFileNames = setOf(present))

        assertEquals(LiteRtLmEngineCacheCleanup(removedFileCount = 1, releasedBytes = 23L, deferredFileCount = 1), result)
        assertTrue(presentCache.exists())
        assertFalse(goneCache.exists())
        assertTrue(goneRecent.exists())
        assertTrue(unrelated.exists())
        assertTrue(goneDirectory.isDirectory)
    }

    @Test
    fun aMissingCacheDirectoryIsNotCreated() {
        val missing = File(directory, "litertlm")

        val result = LiteRtLmEngineCacheStore(missing, { nowMillis }, ::jvmEntryKind).deleteOrphans(emptySet())

        assertEquals(LiteRtLmEngineCacheCleanup.NONE, result)
        assertFalse(missing.exists())
    }

    @Test
    fun theCleanupDirectoryPathMatchesTheEngineCacheDirectory() {
        val root = File(directory, "cache-root").apply { check(mkdir()) }

        assertFalse(liteRtLmCacheDirectoryPath(root).exists())
        assertEquals(liteRtLmCacheDirectoryPath(root), liteRtLmCacheDirectory(root))
        assertTrue(liteRtLmCacheDirectoryPath(root).isDirectory)
    }

    private fun store() = LiteRtLmEngineCacheStore(directory, { nowMillis }, ::jvmEntryKind)

    /** The JVM counterpart of the lstat check the app uses on Android. */
    private fun jvmEntryKind(file: File): LiteRtLmCacheEntryKind = try {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        when {
            attributes.isDirectory -> LiteRtLmCacheEntryKind.DIRECTORY
            attributes.isRegularFile -> LiteRtLmCacheEntryKind.REGULAR_FILE
            else -> LiteRtLmCacheEntryKind.OTHER
        }
    } catch (missing: NoSuchFileException) {
        LiteRtLmCacheEntryKind.MISSING
    }

    private fun write(name: String, size: Int, lastModified: Long = oldMillis): File =
        File(directory, name).apply {
            writeBytes(ByteArray(size))
            check(setLastModified(lastModified))
        }

    private fun modelFile(digit: Char) = "model-${digit.toString().repeat(64)}.litertlm"

    private fun cache(model: String, generation: Int) = "${model}_176310168${generation}_1546125312.xnnpack_cache"
}
