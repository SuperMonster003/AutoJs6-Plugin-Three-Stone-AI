package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Application
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.CompositeAiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.EngineMemoryPressurePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLocalBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmBackendCompatibilityDetector
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmEngineRuntime
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OpenAiCompatibleBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.liteRtLmCacheDirectory
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialStore
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AndroidKeystoreCredentialCipher
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.EncryptedAiCredentialStore
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.FileCredentialRecordStorage
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.FileOnlineAiProfileDocumentStorage
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRepository

class ThreeStoneAiApplication : Application() {
    private val engineRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LiteRtLmEngineRuntime(
            cacheDirectory = liteRtLmCacheDirectory(cacheDir),
        )
    }

    internal val engineRuntime: LiteRtLmEngineRuntime
        get() = engineRuntimeDelegate.value

    private val localBackendDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LiteRtLocalBackend(
            repository = ModelRepository(this),
            compatibilityDetector = LiteRtLmBackendCompatibilityDetector(),
            engineRuntime = engineRuntime,
        )
    }

    internal val localBackend: AiBackend
        get() = localBackendDelegate.value

    private val credentialStoreDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedAiCredentialStore(
            storage = FileCredentialRecordStorage(filesDir),
            cipher = AndroidKeystoreCredentialCipher(),
        )
    }

    /** Each app process owns an instance backed by the same Keystore key and atomic private files. */
    private val credentialStore: AiCredentialStore
        get() = credentialStoreDelegate.value

    private val onlineProfileRegistryDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OnlineAiProfileRegistry(
            repository = OnlineAiProfileRepository(
                FileOnlineAiProfileDocumentStorage(filesDir),
            ),
            credentialStore = credentialStore,
        )
    }

    internal val onlineProfileRegistry: OnlineAiProfileRegistry
        get() = onlineProfileRegistryDelegate.value

    private val aiBackendDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CompositeAiBackend(
            listOf(
                localBackend,
                OpenAiCompatibleBackend(onlineProfileRegistry),
            ),
        )
    }

    /** Unified local/remote dispatch. Remote profiles remain unavailable until HTTPS execution lands. */
    internal val aiBackend: AiBackend
        get() = aiBackendDelegate.value

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
