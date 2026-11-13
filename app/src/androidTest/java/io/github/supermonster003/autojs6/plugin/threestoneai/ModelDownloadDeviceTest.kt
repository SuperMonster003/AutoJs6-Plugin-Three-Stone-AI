package io.github.supermonster003.autojs6.plugin.threestoneai

import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.download.HttpsModelDownloadClient
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadClient
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadOperationControl
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadStage
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadState
import io.github.supermonster003.autojs6.plugin.threestoneai.download.OpenModelDownload
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModel
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportPolicy
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
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The one-step download (D99, D126) on a device: the card on "Models and services", pause /
 * continue / retry / cancel, the hash-named partial file and its restoration after a cold start.
 * A throttled fake source stands in for the network; it serves a valid LiteRT-LM header followed
 * by zeros, so a download never reaches verification and nothing is imported. Each test deletes
 * the partial file it created and puts the production HTTPS client back.
 */
class ModelDownloadDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val model: RecommendedModel = checkNotNull(RecommendedModelCatalog.find(MODEL_ID))
    private val directory = File(context.filesDir, PRIVATE_MODEL_DIRECTORY_NAME)
    private val part = File(directory, ModelDownloadPolicy.partFileName(model.expectedSha256))

    @Before
    fun requireACleanDownloadArea() {
        val existingParts = directory.listFiles().orEmpty().filter { ModelDownloadPolicy.partFileSha256(it.name) != null }
        assumeTrue("A partial download already exists on this device", existingParts.isEmpty())
        val imported = ModelRepository(context).catalogSnapshot().entries.any { it.sha256 == model.expectedSha256 }
        assumeTrue("$MODEL_ID is already imported on this device", !imported)
        directory.mkdirs()
        assumeTrue(
            "Private storage cannot hold $MODEL_ID",
            ModelDownloadPolicy.hasStorageFor(directory.usableSpace, model.expectedSizeBytes),
        )
    }

    @After
    fun restoreTheProductionClient() {
        ModelDownloadCoordinator.replaceForTesting(context, HttpsModelDownloadClient)
        part.delete()
    }

    @Test
    fun theCardPausesResumesFromThePartialFileAndCancels() {
        val source = FakeSource()
        val coordinator = ModelDownloadCoordinator.replaceForTesting(context, source)
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
            waitUntil("resumed request") { source.offsets.size == 2 }
            assertEquals(listOf(0L, paused.progress.processedBytes), source.offsets.toList())
            waitForBytes(coordinator, paused.progress.processedBytes + MIN_BYTES)

            onActivity(scenario) { checkNotNull(button(it, R.string.button_cancel_download)).performClick() }
            waitForState<ModelDownloadState.Cancelled>(coordinator)
            waitUntil("card hidden") { onActivity(scenario) { button(it, R.string.button_cancel_download) == null } }
            assertFalse("Cancelling deletes the partial file", part.exists())
            if (importWasEnabled) {
                waitUntil("import enabled again") {
                    onActivity(scenario) { rowTitle(it, R.string.button_import_model).isEnabled }
                }
            }
        }
    }

    @Test
    fun aFailureKeepsThePartialFileAndRetryContinuesFromIt() {
        val source = FakeSource(endAfterBytes = 2L * MIN_BYTES)
        val coordinator = ModelDownloadCoordinator.replaceForTesting(context, source)
        ActivityScenario.launch(ModelManagerActivity::class.java).use { scenario ->
            assertTrue(coordinator.beginDownload(model))
            val failed = waitForState<ModelDownloadState.Failed>(coordinator)
            assertEquals(ModelDownloadFailureReason.NETWORK_UNAVAILABLE, failed.reason)
            assertEquals(2L * MIN_BYTES, failed.downloadedBytes)
            assertEquals(failed.downloadedBytes, part.length())
            waitUntil("retry button") { onActivity(scenario) { button(it, R.string.button_retry_download) != null } }

            source.endAfterBytes = null
            onActivity(scenario) { checkNotNull(button(it, R.string.button_retry_download)).performClick() }
            waitUntil("resumed request") { source.offsets.size == 2 }
            assertEquals(listOf(0L, failed.downloadedBytes), source.offsets.toList())
            waitForBytes(coordinator, failed.downloadedBytes + MIN_BYTES)

            val running = coordinator.state() as ModelDownloadState.Running
            assertTrue(coordinator.cancelDownload(running.operationId))
            waitForState<ModelDownloadState.Cancelled>(coordinator)
            assertFalse(part.exists())
        }
    }

    @Test
    fun aColdStartShowsTheLeftoverPartialFileAsAPausedDownload() {
        val first = ModelDownloadCoordinator.replaceForTesting(context, FakeSource())
        assertTrue(first.beginDownload(model))
        waitForBytes(first, MIN_BYTES)
        assertTrue(first.pauseDownload((first.state() as ModelDownloadState.Running).operationId))
        val keptBytes = waitForState<ModelDownloadState.Paused>(first).progress.processedBytes

        // A new instance is what the next process creates; it finds the file on its own.
        val restarted = ModelDownloadCoordinator.replaceForTesting(context, FakeSource())
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

    /** Serves "LITERTLM" and zeros from the requested offset, slowly, optionally ending early. */
    private class FakeSource(@Volatile var endAfterBytes: Long? = null) : ModelDownloadClient {
        val offsets = CopyOnWriteArrayList<Long>()

        override fun open(model: RecommendedModel, control: ModelDownloadOperationControl, offset: Long): OpenModelDownload {
            offsets += offset
            val stream = ThrottledStream(offset, endAfterBytes)
            control.registerCancellationResource(stream)
            return OpenModelDownload(stream, model.expectedSizeBytes - offset, startOffset = offset)
        }
    }

    private class ThrottledStream(private var position: Long, private val endAt: Long?) : InputStream() {
        @Volatile
        private var closed = false

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (closed) throw IOException("Fake source closed")
            try {
                Thread.sleep(CHUNK_DELAY_MILLIS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Fake source interrupted")
            }
            if (closed) throw IOException("Fake source closed")
            val limit = endAt?.let { it - position } ?: Long.MAX_VALUE
            if (limit <= 0L) return -1
            val count = minOf(length.toLong(), CHUNK_BYTES.toLong(), limit).toInt()
            for (index in 0 until count) {
                val at = position + index
                buffer[offset + index] = if (at < MAGIC.size) MAGIC[at.toInt()] else 0
            }
            position += count
            return count
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val MODEL_ID = "qwen2-5-1-5b-instruct"
        const val MIN_BYTES = 1L shl 20
        const val CHUNK_BYTES = 64 * 1024
        const val CHUNK_DELAY_MILLIS = 15L
        const val TIMEOUT_MILLIS = 20_000L
        val MAGIC: ByteArray = "LITERTLM".toByteArray(StandardCharsets.US_ASCII)

        init {
            check(MAGIC.size == ModelImportPolicy.LITERTLM_MAGIC_BYTES)
        }
    }
}
