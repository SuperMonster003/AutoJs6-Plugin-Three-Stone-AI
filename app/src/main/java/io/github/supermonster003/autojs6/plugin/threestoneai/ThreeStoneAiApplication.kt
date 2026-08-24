package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Application
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.EngineMemoryPressurePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmEngineRuntime
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.liteRtLmCacheDirectory

class ThreeStoneAiApplication : Application() {
    private val engineRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LiteRtLmEngineRuntime(
            cacheDirectory = liteRtLmCacheDirectory(cacheDir),
        )
    }

    internal val engineRuntime: LiteRtLmEngineRuntime
        get() = engineRuntimeDelegate.value

    override fun onTrimMemory(level: Int) {
        if (
            engineRuntimeDelegate.isInitialized() &&
            EngineMemoryPressurePolicy.shouldEvict(level)
        ) {
            engineRuntime.requestEviction()
        }
        super.onTrimMemory(level)
    }

    override fun onLowMemory() {
        if (engineRuntimeDelegate.isInitialized()) engineRuntime.requestEviction()
        super.onLowMemory()
    }

    override fun onTerminate() {
        if (engineRuntimeDelegate.isInitialized()) engineRuntime.close()
        super.onTerminate()
    }
}
