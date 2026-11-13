package io.github.supermonster003.autojs6.plugin.threestoneai

import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.liteRtLmCacheDirectory
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogCodec
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogEntry
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelDeletionState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelManagerState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelStorageCleanupState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.PRIVATE_MODEL_DIRECTORY_NAME
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/**
 * P6.0 (D146) through the real model-manager coordinator: "Clean unreferenced files" removes caches
 * of models that are gone, deleting a model removes its own caches, and nothing else is touched:
 * caches of a model still in the catalog, a cache written moments ago, and unrelated files stay.
 * Two tiny fake model files are cataloged directly; no engine is created.
 */
class EngineCacheCleanupDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val modelsDirectory = File(context.filesDir, PRIVATE_MODEL_DIRECTORY_NAME)
    private val cacheDirectory = liteRtLmCacheDirectory(context.cacheDir)
    private val created = mutableListOf<File>()
    private val coordinator by lazy { ModelImportCoordinator.get(context) }

    @Test
    fun cleanupAndDeletionRemoveOnlyCachesOfModelsThatAreGone() {
        // Cold-start the coordinator first, so its own orphan sweep has already run before staging.
        waitForIdleManager()
        assumeTrue("Needs an otherwise empty model catalog", ModelRepository(context).managerSnapshot().models.isEmpty())
        val kept = stageModel("p6-0 kept")
        val deleted = stageModel("p6-0 deleted")
        val orphanFileName = "model-${sha256(Random.nextBytes(32))}.litertlm"

        val keptCache = cache("${kept.fileName}_1763101680_1546125312.xnnpack_cache", OLD)
        val deletedCpuCache = cache("${deleted.fileName}_1763101680_1546125312.xnnpack_cache", OLD)
        val deletedGpuCache = cache("${deleted.fileName}_1763101680_1546125312_mldrift_weight_cache.bin", OLD)
        val deletedRecentCache = cache("${deleted.fileName}_1763101999_1546125312.xnnpack_cache", RECENT)
        val orphanCache = cache("${orphanFileName}_1763101680_1546125312.xnnpack_cache", OLD)
        val unrelated = cache("unrelated-p6-0.bin", OLD)

        assertTrue(coordinator.beginStorageCleanup())
        waitUntil("storage cleanup finished", TIMEOUT_MILLIS) {
            coordinator.managerState().storageCleanup.let {
                it is ModelStorageCleanupState.Succeeded || it is ModelStorageCleanupState.Failed
            }
        }
        val cleanup = coordinator.managerState().storageCleanup as ModelStorageCleanupState.Succeeded
        assertFalse("orphan cache removed", orphanCache.exists())
        assertTrue("cleanup counts the orphan cache", cleanup.removedFileCount >= 1)
        assertTrue("cleanup counts the released bytes", cleanup.releasedBytes >= ORPHAN_SIZE)
        assertTrue(keptCache.exists())
        assertTrue(deletedCpuCache.exists())
        assertTrue(deletedGpuCache.exists())
        assertTrue(deletedRecentCache.exists())
        assertTrue(unrelated.exists())
        assertTrue(coordinator.managerState().snapshot!!.models.any { it.modelId == deleted.modelId })

        assertTrue(coordinator.beginDeletion(deleted.modelId))
        waitUntil("deletion finished", TIMEOUT_MILLIS) {
            coordinator.managerState().deletion.let {
                it is ModelDeletionState.Succeeded || it is ModelDeletionState.Failed
            }
        }
        assertTrue(coordinator.managerState().deletion is ModelDeletionState.Succeeded)
        assertFalse(File(modelsDirectory, deleted.fileName).exists())
        assertFalse("CPU cache of the deleted model removed", deletedCpuCache.exists())
        assertFalse("GPU cache of the deleted model removed", deletedGpuCache.exists())
        assertTrue("a cache written moments ago is left for a later sweep", deletedRecentCache.exists())
        assertTrue("cache of the remaining model kept", keptCache.exists())
        assertTrue(File(modelsDirectory, kept.fileName).exists())
        assertTrue(unrelated.exists())
        assertEquals(listOf(kept.modelId), coordinator.managerState().snapshot!!.models.map { it.modelId })
    }

    @After
    fun restore() {
        val remaining = runCatching { ModelRepository(context).managerSnapshot().models }.getOrDefault(emptyList())
        remaining.filter { model -> created.any { it.name == model.file.name } }.forEach { model ->
            runCatching {
                waitForIdleManager()
                coordinator.beginDeletion(model.modelId)
                waitForIdleManager()
            }
        }
        created.forEach { it.delete() }
    }

    private fun stageModel(displayName: String): ModelCatalogEntry {
        check(modelsDirectory.isDirectory || modelsDirectory.mkdirs())
        val bytes = Random.nextBytes(MODEL_SIZE)
        val sha256 = sha256(bytes)
        val file = File(modelsDirectory, "model-$sha256.litertlm").apply { writeBytes(bytes) }
        created += file
        val entry = ModelCatalogEntry(
            modelId = "litertlm.${sha256.take(32)}",
            displayName = displayName,
            fileName = file.name,
            sizeBytes = file.length(),
            sha256 = sha256,
            importedAtMillis = System.currentTimeMillis(),
        )
        val repository = ModelRepository(context)
        val update = ModelCatalogPolicy.integrateImport(repository.catalogSnapshot(), entry, select = true)
        File(modelsDirectory, CATALOG_FILE).writeBytes(ModelCatalogCodec.encode(update.document))
        return entry
    }

    private fun cache(name: String, age: Long): File = File(cacheDirectory, name).apply {
        writeBytes(ByteArray(if (name.startsWith("unrelated")) 3 else ORPHAN_SIZE.toInt()))
        check(setLastModified(System.currentTimeMillis() - age))
        created += this
    }

    private fun waitForIdleManager() {
        waitUntil("model manager idle", TIMEOUT_MILLIS) {
            coordinator.managerState().isIdle()
        }
    }

    private fun ModelManagerState.isIdle(): Boolean =
        snapshot != null &&
            deletion !is ModelDeletionState.Deleting &&
            storageCleanup !is ModelStorageCleanupState.Cleaning

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val CATALOG_FILE = "catalog.json"
        const val MODEL_SIZE = 4_096
        const val ORPHAN_SIZE = 8_192L
        const val OLD = 60L * 60L * 1_000L
        const val RECENT = 1_000L
        const val TIMEOUT_MILLIS = 30_000L
    }
}
