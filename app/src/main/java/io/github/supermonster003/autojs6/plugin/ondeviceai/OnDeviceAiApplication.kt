package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.app.Application
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.EngineMemoryPressurePolicy
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.LiteRtLmEngineRuntime
import java.io.File

class OnDeviceAiApplication : Application() {
    private val engineRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LiteRtLmEngineRuntime(
            cacheDirectory = File(cacheDir, "litertlm").apply {
                check(isDirectory || mkdirs()) { "LiteRT-LM cache directory is unavailable" }
            },
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
