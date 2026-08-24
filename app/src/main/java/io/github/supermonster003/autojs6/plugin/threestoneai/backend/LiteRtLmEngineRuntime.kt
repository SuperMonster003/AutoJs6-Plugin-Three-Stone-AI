package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import android.content.ComponentCallbacks2
import com.google.ai.edge.litertlm.Engine
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService

/** Process-scoped owner for initialized engines; bound Service instances may come and go per call. */
internal class LiteRtLmEngineRuntime(
    private val cacheDirectory: File,
) : AutoCloseable {
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "three-stone-ai-engine-cache").apply { isDaemon = true }
        }
    private val engineCache = ReusableResourceCache<EngineCacheKey, Engine>(
        scheduler = scheduler,
        idleTimeoutMillis = ENGINE_IDLE_TIMEOUT_MILLIS,
    )

    fun createSession(
        target: AiTarget,
        modelSha256: String,
        modelPath: String,
        backendProfile: LiteRtLmBackendProfile,
    ): AiBackendSession = LiteRtLocalSession(
        target,
        modelSha256,
        modelPath,
        backendProfile,
        cacheDirectory,
        engineCache,
    )

    fun requestEviction() {
        try {
            scheduler.execute(engineCache::evict)
        } catch (_: RejectedExecutionException) {
            engineCache.evict()
        }
    }

    override fun close() {
        engineCache.close()
        scheduler.shutdownNow()
    }

    companion object {
        const val ENGINE_IDLE_TIMEOUT_MILLIS = 5L * 60L * 1_000L
    }
}

internal data class EngineCacheKey(
    val modelSha256: String,
    val backendProfile: LiteRtLmBackendProfile,
)

/** Ignores ordinary UI/background transitions while reacting to explicit Android pressure levels. */
internal object EngineMemoryPressurePolicy {
    @Suppress("DEPRECATION")
    fun shouldEvict(level: Int): Boolean = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        ComponentCallbacks2.TRIM_MEMORY_MODERATE,
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        -> true
        else -> false
    }
}
