package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.app.Application
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.EngineMemoryPressurePolicy
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.LiteRtLmEngineRuntime
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.liteRtLmCacheDirectory

class OnDeviceAiApplication : Application() {
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
