package io.github.supermonster003.autojs6.plugin.threestoneai

import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.LoopbackModelServer.Answer
import io.github.supermonster003.autojs6.plugin.threestoneai.download.HttpsModelDownloadTransport
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadStage
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadState
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadStorage
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModel
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.model.PRIVATE_MODEL_DIRECTORY_NAME
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The one-step download (D99, D126) on a device, on the shared download library (plugin-center
 * P13.3): the card on "Models and services", pause / continue / retry / cancel, automatic retries
 * that continue a cut transfer, the partial file and its restoration after a cold start, a partial
 * file of the older downloader (Q51), and a file whose hash does not match. A loopback HTTP server
 * in the test process stands in for Hugging Face; it serves a LiteRT-LM header followed by zeros
 * from the requested offset, slowly, so a catalog download never reaches verification and nothing
 * is imported. Each test deletes what it downloaded and puts the production transport back.
 */
class ModelDownloadDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val model: RecommendedModel = checkNotNull(RecommendedModelCatalog.find(MODEL_ID))
    private val directory = File(context.filesDir, PRIVATE_MODEL_DIRECTORY_NAME)
    private val legacyPart = File(directory, ModelDownloadPolicy.partFileName(model.expectedSha256))
    private val downloads = File(context.filesDir, ModelDownloadStorage.DOWNLOAD_DIRECTORY_NAME)
    private val part = File(File(downloads, model.expectedSha256), ModelDownloadStorage.MODEL_FILE_NAME + ".part")
    private lateinit var server: LoopbackModelServer

    @Before
    fun requireACleanDownloadArea() {
        val existingParts = directory.listFiles().orEmpty().filter { ModelDownloadPolicy.partFileSha256(it.name) != null }
        assumeTrue("A partial download already exists on this device", existingParts.isEmpty())
        assumeTrue("The download store is not empty on this device", downloads.listFiles().isNullOrEmpty())
        val imported = ModelRepository(context).catalogSnapshot().entries.any { it.sha256 == model.expectedSha256 }
        assumeTrue("$MODEL_ID is already imported on this device", !imported)
        directory.mkdirs()
        assumeTrue(
            "Private storage cannot hold $MODEL_ID",
            ModelDownloadPolicy.hasStorageFor(directory.usableSpace, model.expectedSizeBytes),
        )
        server = LoopbackModelServer()
        server.script(path(model), Answer.Serve(model.expectedSizeBytes))
    }

    @After
    fun restoreTheProductionTransport() {
        val coordinator = ModelDownloadCoordinator.replaceForTesting(context, HttpsModelDownloadTransport)
        waitUntil("restore finished", TIMEOUT_MILLIS) { coordinator.state() !is ModelDownloadState.Running }
        server.close()
        legacyPart.delete()
        downloads.deleteRecursively()
    }

    @Test
    fun theCardPausesResumesFromThePartialFileAndCancels() {
        val coordinator = ModelDownloadCoordinator.replaceForTesting(context, server.Transport())
        ActivityScenario.launch(ModelManagerActivity::class.java).use { scenario ->
            val importWasEnabled = onActivity(scenario) { rowTitle(it, R.string.button_import_model).isEnabled }
            assertTrue(coordinator.beginDownload(model))
            waitForBytes(coordinator, MIN_BYTES)
            waitUntil("pause button") { onActivity(scenario) { button(it, R.string.button_pause_download) != null } }
            if (importWasEnabled) {
                assertFalse(
                    "Import is unavailable while downloading",
                    onActivity(scenario) { rowTitle(it, R.string.button_import_model).isEnabled },
                )
            }

            onActivity(scenario) { checkNotNull(button(it, R.string.button_pause_download)).performClick() }
            val paused = waitForState<ModelDownloadState.Paused>(coordinator)
            assertEquals(model, paused.model)
            assertEquals("The partial file holds exactly the paused length", paused.progress.processedBytes, part.length())
            assertTrue(paused.progress.processedBytes >= MIN_BYTES)
            waitUntil("resume button") { onActivity(scenario) { button(it, R.string.button_resume_download) != null } }

            onActivity(scenario) { checkNotNull(button(it, R.string.button_resume_download)).performClick() }
            waitUntil("resumed request") { server.ranges(path(model)).size == 2 }
            assertEquals(listOf(null, "bytes=${paused.progress.processedBytes}-"), server.ranges(path(model)))
            assertEquals(LoopbackModelServer.ETAG, server.requests.last().ifRange)
            waitForBytes(coordinator, paused.progress.processedBytes + MIN_BYTES)
            evidence("pause-resume", "paused=${paused.progress.processedBytes} ranges=${server.ranges(path(model))}")

            onActivity(scenario) { checkNotNull(button(it, R.string.button_cancel_download)).performClick() }
            waitForState<ModelDownloadState.Cancelled>(coordinator)
            waitUntil("card hidden") { onActivity(scenario) { button(it, R.string.button_cancel_download) == null } }
            assertFalse("Cancelling deletes the partial file", part.exists())
            assertEquals(0L, coordinator.storage.storedBytes(model))
            if (importWasEnabled) {
                waitUntil("import enabled again") {
                    onActivity(scenario) { rowTitle(it, R.string.button_import_model).isEnabled }
                }
            }
        }
    }

    /** P13.3 "interrupted transfer": the library retries by itself after 2 s and continues with Range. */
    @Test
    fun aCutTransferRetriesByItselfAndContinuesFromTheCut() {
        server.script(
            path(model),
            Answer.Serve(model.expectedSizeBytes, cutAfter = CUT_BYTES),
            Answer.Serve(model.expectedSizeBytes),
        )
        val coordinator = ModelDownloadCoordinator.replaceForTesting(context, server.Transport())
        val startedAt = System.currentTimeMillis()
        assertTrue(coordinator.beginDownload(model))
        waitUntil("resumed request", TIMEOUT_MILLIS) { server.ranges(path(model)).size == 2 }
        val retriedAfter = System.currentTimeMillis() - startedAt
        assertEquals(listOf(null, "bytes=$CUT_BYTES-"), server.ranges(path(model)))
        assertEquals("Same server: If-Range guards the resume", LoopbackModelServer.ETAG, server.requests.last().ifRange)
        waitForBytes(coordinator, CUT_BYTES + MIN_BYTES)
        assertTrue("No failure was shown", coordinator.state() is ModelDownloadState.Running)
        evidence("cut-retry", "cut=$CUT_BYTES retriedWithin=${retriedAfter}ms ranges=${server.ranges(path(model))}")

        val running = coordinator.state() as ModelDownloadState.Running
        assertTrue(coordinator.cancelDownload(running.operationId))
        waitForState<ModelDownloadState.Cancelled>(coordinator)
        assertFalse(part.exists())
    }

    @Test
    fun aFailureKeepsThePartialFileAndRetryContinuesFromIt() {
        server.script(
            path(model),
            Answer.Serve(model.expectedSizeBytes, cutAfter = CUT_BYTES),
            Answer.Refuse,
            Answer.Refuse,
            Answer.Serve(model.expectedSizeBytes),
        )
        val coordinator = ModelDownloadCoordinator.replaceForTesting(context, server.Transport(), QUICK_RETRIES)
        ActivityScenario.launch(ModelManagerActivity::class.java).use { scenario ->
            assertTrue(coordinator.beginDownload(model))
            val failed = waitForState<ModelDownloadState.Failed>(coordinator)
            assertEquals(ModelDownloadFailureReason.NETWORK_UNAVAILABLE, failed.reason)
            assertEquals(CUT_BYTES, failed.downloadedBytes)
            assertEquals(failed.downloadedBytes, part.length())
            assertEquals("The cut transfer and two automatic retries", 3, server.requests.size)
            waitUntil("retry button") { onActivity(scenario) { button(it, R.string.button_retry_download) != null } }

            onActivity(scenario) { checkNotNull(button(it, R.string.button_retry_download)).performClick() }
            waitUntil("resumed request") { server.requests.size == 4 }
            assertEquals("bytes=${failed.downloadedBytes}-", server.requests.last().range)
            waitForBytes(coordinator, failed.downloadedBytes + MIN_BYTES)
            evidence("failure-retry", "failedAt=${failed.downloadedBytes} ranges=${server.ranges(path(model))}")

            val running = coordinator.state() as ModelDownloadState.Running
            assertTrue(coordinator.cancelDownload(running.operationId))
            waitForState<ModelDownloadState.Cancelled>(coordinator)
            assertFalse(part.exists())
        }
    }

    @Test
    fun aColdStartShowsTheLeftoverPartialFileAsAPausedDownload() {
        val first = ModelDownloadCoordinator.replaceForTesting(context, server.Transport())
        assertTrue(first.beginDownload(model))
        waitForBytes(first, MIN_BYTES)
        assertTrue(first.pauseDownload((first.state() as ModelDownloadState.Running).operationId))
        val keptBytes = waitForState<ModelDownloadState.Paused>(first).progress.processedBytes

        // A new instance is what the next process creates; it finds the file on its own.
        val restarted = ModelDownloadCoordinator.replaceForTesting(context, server.Transport())
        val restored = waitForState<ModelDownloadState.Paused>(restarted)
        assertEquals(model, restored.model)
        assertEquals(keptBytes, restored.progress.processedBytes)
        ActivityScenario.launch(ModelManagerActivity::class.java).use { scenario ->
            waitUntil("resume button") { onActivity(scenario) { button(it, R.string.button_resume_download) != null } }
            onActivity(scenario) { checkNotNull(button(it, R.string.button_cancel_download)).performClick() }
            waitForState<ModelDownloadState.Cancelled>(restarted)
        }
        assertFalse(part.exists())
    }

    /** Q51 A: a partial file of the downloader before 1.7.1 is moved into the store and continued. */
    @Test
    fun aPartialFileOfTheOlderDownloaderIsAdoptedAndContinued() {
        legacyPart.outputStream().use { output ->
            val buffer = ByteArray(LoopbackModelServer.CHUNK_BYTES)
            var position = 0L
            while (position < LEGACY_BYTES) {
                val count = minOf(buffer.size.toLong(), LEGACY_BYTES - position).toInt()
                for (index in 0 until count) {
                    buffer[index] = LoopbackModelServer.byteAt(position + index, model.expectedSizeBytes, 0)
                }
                output.write(buffer, 0, count)
                position += count
            }
        }
        val coordinator = ModelDownloadCoordinator.replaceForTesting(context, server.Transport())
        val restored = waitForState<ModelDownloadState.Paused>(coordinator)
        assertEquals(model, restored.model)
        assertEquals(LEGACY_BYTES, restored.progress.processedBytes)
        assertFalse("The older file moved into the download store", legacyPart.exists())
        assertEquals(LEGACY_BYTES, part.length())

        ActivityScenario.launch(ModelManagerActivity::class.java).use { scenario ->
            waitUntil("resume button") { onActivity(scenario) { button(it, R.string.button_resume_download) != null } }
            onActivity(scenario) { checkNotNull(button(it, R.string.button_resume_download)).performClick() }
            waitUntil("resumed request") { server.requests.size == 1 }
            assertEquals(listOf<String?>("bytes=$LEGACY_BYTES-"), server.ranges(path(model)))
            waitForBytes(coordinator, LEGACY_BYTES + MIN_BYTES)
            evidence("legacy-adopt", "legacy=$LEGACY_BYTES ranges=${server.ranges(path(model))}")
            onActivity(scenario) { checkNotNull(button(it, R.string.button_cancel_download)).performClick() }
            waitForState<ModelDownloadState.Cancelled>(coordinator)
        }
        assertFalse(part.exists())
        assertFalse(legacyPart.exists())
    }

    /** P13.3 "hash mismatch": a file of the pinned size whose SHA-256 differs is refused and deleted. */
    @Test
    fun aFileWithTheWrongHashIsRejected() {
        val size = 2L * 1024L * 1024L
        val pinned = RecommendedModel(
            id = "p13-hash-check",
            displayName = "P13 hash check",
            fileName = "p13-hash-check.litertlm",
            sourceUrl = "$TEST_REPOSITORY/blob/$TEST_REVISION/p13-hash-check.litertlm",
            downloadUrl = "$TEST_REPOSITORY/resolve/$TEST_REVISION/p13-hash-check.litertlm?download=true",
            expectedSizeBytes = size,
            expectedSha256 = LoopbackModelServer.sha256Of(size, lastByte = 1),
            license = "Apache-2.0",
        )
        // The server sends the same size with a different last byte.
        server.script(path(pinned), Answer.Serve(size, lastByte = 0, chunkDelayMillis = 0L))
        val coordinator = ModelDownloadCoordinator.replaceForTesting(context, server.Transport(), QUICK_RETRIES)
        assertTrue(coordinator.beginDownload(pinned))
        val failed = waitForState<ModelDownloadState.Failed>(coordinator)
        assertEquals(ModelDownloadFailureReason.INTEGRITY_MISMATCH, failed.reason)
        assertEquals("Nothing is kept to resume", 0L, failed.downloadedBytes)
        assertEquals(0L, coordinator.storage.storedBytes(pinned))
        assertEquals("Downloaded once, not retried", 1, server.requests.size)
        assertFalse(File(directory, ModelDownloadPolicy.partFileName(pinned.expectedSha256)).exists())
        evidence("hash-mismatch", "size=$size reason=${failed.reason} kept=${failed.downloadedBytes} requests=${server.requests.size}")
        assertTrue(coordinator.cancelDownload(failed.operationId))
    }

    private fun path(target: RecommendedModel) = "/${target.id}"

    private fun evidence(name: String, line: String) {
        Log.i(EVIDENCE_TAG, "$name: $line")
    }

    private fun waitForBytes(coordinator: ModelDownloadCoordinator, bytes: Long) {
        waitUntil("$bytes bytes downloaded", TIMEOUT_MILLIS) {
            val state = coordinator.state()
            state is ModelDownloadState.Running && state.stage == ModelDownloadStage.DOWNLOADING &&
                state.progress.processedBytes >= bytes
        }
    }

    private inline fun <reified T : ModelDownloadState> waitForState(coordinator: ModelDownloadCoordinator): T {
        waitUntil(T::class.java.simpleName, TIMEOUT_MILLIS) { coordinator.state() is T }
        return coordinator.state() as T
    }

    private fun button(activity: ModelManagerActivity, @StringRes label: Int): Button? =
        views(activity.window.decorView)
            .filterIsInstance<Button>()
            .firstOrNull { it.isShown && it.text.toString() == activity.getString(label) }

    private fun rowTitle(activity: ModelManagerActivity, @StringRes title: Int): TextView =
        views(activity.window.decorView)
            .filterIsInstance<TextView>()
            .first { it !is Button && it.text.toString() == activity.getString(title) }

    private fun <T> onActivity(scenario: ActivityScenario<ModelManagerActivity>, block: (ModelManagerActivity) -> T): T {
        var result: Result<T>? = null
        scenario.onActivity { result = runCatching { block(it) } }
        return checkNotNull(result).getOrThrow()
    }

    private companion object {
        const val MODEL_ID = "qwen2-5-1-5b-instruct"
        const val MIN_BYTES = 1L shl 20
        const val CUT_BYTES = 2L shl 20
        const val LEGACY_BYTES = 3L shl 20
        const val TIMEOUT_MILLIS = 30_000L
        const val EVIDENCE_TAG = "P13Evidence"
        const val TEST_REPOSITORY = "https://huggingface.co/autojs6-test/p13-hash-check"
        const val TEST_REVISION = "0123456789abcdef0123456789abcdef01234567"
        val QUICK_RETRIES = longArrayOf(0L, 0L)
    }
}
