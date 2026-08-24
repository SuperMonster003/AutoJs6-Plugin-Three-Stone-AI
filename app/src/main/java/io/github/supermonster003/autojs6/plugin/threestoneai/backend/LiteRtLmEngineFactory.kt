package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ImportedModel
import java.io.File

/** Keeps generation and model-health probes on the exact same LiteRT-LM configuration. */
internal object LiteRtLmEngineFactory {
    @OptIn(ExperimentalApi::class)
    @Synchronized
    fun create(
        modelPath: String,
        cacheDirectory: File,
        backendProfile: LiteRtLmBackendProfile = LiteRtLmBackendProfile.CPU,
    ): Engine {
        // LiteRT-LM snapshots this process-wide flag while constructing the native engine.
        ExperimentalFlags.enableBenchmark = true
        return Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = when (backendProfile) {
                    LiteRtLmBackendProfile.CPU -> Backend.CPU(
                        threadCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 8),
                    )
                    LiteRtLmBackendProfile.GPU -> Backend.GPU()
                    LiteRtLmBackendProfile.NPU ->
                        throw IllegalArgumentException("The NPU runtime is not packaged")
                },
                cacheDir = cacheDirectory.absolutePath,
            ),
        )
    }
}

internal fun interface ModelHealthChecker {
    /** Returns normally only after Engine.initialize() succeeds and the temporary Engine closes. */
    fun requireInitializable(model: ImportedModel)
}

internal class LiteRtLmModelHealthChecker(
    private val cacheDirectory: File,
) : ModelHealthChecker {
    override fun requireInitializable(model: ImportedModel) {
        initializeAndClose(
            create = { LiteRtLmEngineFactory.create(model.file.absolutePath, cacheDirectory) },
            initialize = { engine -> engine.initialize() },
        )
    }
}

/** Small pure seam used to prove that both successful and failed probes release native resources. */
internal inline fun <T : AutoCloseable> initializeAndClose(
    create: () -> T,
    initialize: (T) -> Unit,
) {
    create().use(initialize)
}

internal fun liteRtLmCacheDirectory(cacheRoot: File): File =
    File(cacheRoot, "litertlm").apply {
        check(isDirectory || mkdirs()) { "LiteRT-LM cache directory is unavailable" }
    }
