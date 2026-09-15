package io.github.supermonster003.autojs6.plugin.threestoneai

import android.os.Build
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmEngineFactory
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmModelHealthChecker
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.liteRtLmCacheDirectory
import io.github.supermonster003.autojs6.plugin.threestoneai.download.HttpsModelDownloadClient
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadOperationControl
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadTransfer
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogCodec
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogEntry
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Real-model acceptance for the LiteRT-LM local backend.
 *
 * `.python/run_real_model_test.py --serial <device> --model <local.litertlm>` installs the debug
 * APKs and stages the model automatically in app-private storage, without a picker UI.
 * Everything after staging runs through production code: catalog integration and codec,
 * [ModelRepository] lookup, the health probe, [LiteRtLmEngineFactory] and the LiteRT-LM
 * conversation API used by the chat session. A JSON receipt is written to the external files
 * directory so device runs can be collected with `adb pull`.
 *
 * Instrumentation arguments:
 *  - `modelFile`: staged file name (default: the recommended Qwen2.5 1.5B package)
 *  - `targetFile`: an already managed model file for repeat runs
 *  - `modelSha256`: optional expected SHA-256 from the host runner
 *  - `downloadModel`: opt in to a direct HTTPS download of the pinned recommended model
 *  - `prompt`: user prompt (default asks for a short greeting)
 *  - `maxOutputTokens`: generation cap (default 48)
 */
@RunWith(AndroidJUnit4::class)
class LiteRtRealModelInferenceInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun stagedRealModelIsCatalogedInitializedAndGeneratesText() {
        val receipt = JSONObject()
        val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE)
        receipt.put("pageSize", pageSize)
        receipt.put("device", "${Build.MANUFACTURER} ${Build.MODEL} / API ${Build.VERSION.SDK_INT} / ${Build.SUPPORTED_ABIS.joinToString(",")}")
        receipt.put("fingerprint", Build.FINGERPRINT)

        val stagedName = arguments.getString("modelFile")
            ?: "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        require(stagedName.matches(Regex("[A-Za-z0-9_.-]+\\.litertlm"))) { "modelFile must be a safe leaf filename" }
        val staged = File(File(context.filesDir, STAGING_DIRECTORY), stagedName)
        val modelsDirectory = File(context.filesDir, MODELS_DIRECTORY).apply { mkdirs() }
        val existingTarget = modelsDirectory.listFiles()?.singleOrNull { it.name == arguments.getString("targetFile") }
        if (arguments.getString("downloadModel").toBoolean() && existingTarget == null && !staged.isFile) {
            downloadRecommendedModel(staged, receipt)
        }
        if (!arguments.containsKey("modelFile") && !arguments.containsKey("targetFile")) {
            assumeTrue("Real-model acceptance needs .python/run_real_model_test.py --serial <device> --model <model>", staged.isFile)
        }
        assertTrue(
            "Run .python/run_real_model_test.py --serial <device> --model <model> first (missing: $staged)",
            staged.isFile || existingTarget != null,
        )

        val digestStartedNanos = System.nanoTime()
        val source = existingTarget ?: staged
        val sha256 = sha256Of(source)
        arguments.getString("modelSha256")?.let { expected ->
            assertEquals("Device model must match the host file", expected, sha256)
        }
        receipt.put("modelSha256", sha256)
        receipt.put("modelSizeBytes", source.length())
        receipt.put("digestMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - digestStartedNanos))
        RecommendedModelCatalog.models.singleOrNull { it.fileName == stagedName }?.let { recommended ->
            assertEquals("Staged model must match the pinned catalog digest", recommended.expectedSha256, sha256)
            assertEquals(recommended.expectedSizeBytes, source.length())
            receipt.put("recommendedModelId", recommended.id)
        }

        // Production naming: model-<sha256>.litertlm, catalog id litertlm.<sha256[:32]>
        val target = File(modelsDirectory, "model-$sha256.litertlm")
        if (source != target) {
            if (target.isFile) target.delete()
            assertTrue("Unable to move the staged model into the managed directory", source.renameTo(target))
        }
        val entry = ModelCatalogEntry(
            modelId = "litertlm.${sha256.take(32)}",
            displayName = stagedName.removeSuffix(".litertlm"),
            fileName = target.name,
            sizeBytes = target.length(),
            sha256 = sha256,
            importedAtMillis = System.currentTimeMillis(),
        )
        val repository = ModelRepository(context)
        val update = ModelCatalogPolicy.integrateImport(repository.catalogSnapshot(), entry, select = true)
        File(modelsDirectory, CATALOG_FILE).writeBytes(ModelCatalogCodec.encode(update.document))
        val model = requireNotNull(repository.current()) { "Catalog must resolve the staged model" }
        assertEquals(sha256, model.sha256)
        assertEquals(target.absolutePath, model.file.absolutePath)

        val cacheDirectory = liteRtLmCacheDirectory(context.cacheDir)
        val healthStartedNanos = System.nanoTime()
        LiteRtLmModelHealthChecker(cacheDirectory).requireInitializable(model)
        receipt.put("healthCheckMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - healthStartedNanos))

        val prompt = arguments.getString("prompt") ?: "Reply with one short English sentence that greets a tester."
        val maxOutputTokens = arguments.getString("maxOutputTokens")?.toIntOrNull() ?: 48
        val initializeStartedNanos = System.nanoTime()
        val engine = LiteRtLmEngineFactory.create(model.file.absolutePath, cacheDirectory)
        engine.use {
            engine.initialize()
            receipt.put("engineInitializeMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - initializeStartedNanos))
            val conversation = engine.createConversation(
                ConversationConfig(automaticToolCalling = false, maxOutputToken = maxOutputTokens),
            )
            conversation.use {
                val generated = StringBuilder()
                val deltas = AtomicReference(0)
                val finished = CountDownLatch(1)
                val failure = AtomicReference<Throwable?>(null)
                val generationStartedNanos = System.nanoTime()
                conversation.sendMessageAsync(
                    Message.user(Contents.of(listOf(Content.Text(prompt)))),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val delta = message.contents.contents
                                .filterIsInstance<Content.Text>()
                                .joinToString(separator = "") { it.text }
                            synchronized(generated) { generated.append(delta) }
                            deltas.updateAndGet { it + 1 }
                        }

                        override fun onDone() {
                            finished.countDown()
                        }

                        override fun onError(throwable: Throwable) {
                            failure.set(throwable)
                            finished.countDown()
                        }
                    },
                    maxOutputToken = maxOutputTokens,
                )
                assertTrue("Generation did not finish within the time budget", finished.await(20, TimeUnit.MINUTES))
                failure.get()?.let { throw AssertionError("LiteRT-LM generation failed", it) }
                val text = synchronized(generated) { generated.toString() }
                receipt.put("generationMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - generationStartedNanos))
                receipt.put("prompt", prompt)
                receipt.put("generatedText", text)
                receipt.put("streamedDeltas", deltas.get())
                receipt.put("maxOutputTokens", maxOutputTokens)
                receipt.put("backend", "CPU")
                assertTrue("Generated text must not be blank", text.isNotBlank())
            }
        }
        receipt.put("ok", true)
        writeReceipt(receipt)
    }

    private fun downloadRecommendedModel(staged: File, receipt: JSONObject) {
        val model = requireNotNull(RecommendedModelCatalog.models.singleOrNull { it.fileName == staged.name }) {
            "Direct download requires an exact recommended-model filename"
        }
        arguments.getString("modelSha256")?.let { expected ->
            assertEquals("Host model must match the pinned download before transfer", expected, model.expectedSha256)
        }
        val directory = requireNotNull(staged.parentFile)
        check(directory.mkdirs() || directory.isDirectory)
        val partial = File(directory, staged.name + ".download.partial")
        val control = ModelDownloadOperationControl()
        val startedNanos = System.nanoTime()
        try {
            val download = HttpsModelDownloadClient.open(model, control)
            download.input.use { input ->
                partial.outputStream().use { output ->
                    ModelDownloadTransfer.copyAndVerify(input, output, model, control::ensureActive)
                }
            }
            check(partial.renameTo(staged)) { "Unable to stage the verified download" }
            receipt.put("stagingSource", "pinned-catalog-https")
            receipt.put("downloadMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos))
        } finally {
            control.closeCancellationResources()
            if (partial.exists()) partial.delete()
        }
    }

    private fun writeReceipt(receipt: JSONObject) {
        val directory = context.getExternalFilesDir(null) ?: context.filesDir
        File(directory, RECEIPT_FILE).writeText(receipt.toString(2))
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private companion object {
        const val STAGING_DIRECTORY = "import-staging"
        const val MODELS_DIRECTORY = "models"
        const val CATALOG_FILE = "catalog.json"
        const val RECEIPT_FILE = "litertlm-real-model-inference.json"
    }
}
