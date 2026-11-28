package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Application
import android.os.Handler
import androidx.annotation.VisibleForTesting
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AndroidOnlineAiNetworkAccess
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.CompositeAiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.DefaultTargetStore
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.EngineMemoryPressurePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.FileDefaultTargetStore
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLocalBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmBackendCompatibilityDetector
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmEngineRuntime
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LocalEngineHandover
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiHttpExecution
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.liteRtLmCacheDirectory
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialStore
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AndroidKeystoreCredentialCipher
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.EncryptedAiCredentialStore
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.FileCredentialRecordStorage
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.FileOnlineAiProfileDocumentStorage
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ThreeStoneAiApplication : Application() {
    private val engineRuntimeDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LiteRtLmEngineRuntime(
            cacheDirectory = liteRtLmCacheDirectory(cacheDir),
            beforeLoad = { LocalEngineHandover.requestRelease(this) },
        )
    }

    internal val engineRuntime: LiteRtLmEngineRuntime
        get() = engineRuntimeDelegate.value

    private val localBackendDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LiteRtLocalBackend(
            repository = ModelRepository(this),
            compatibilityDetector = LiteRtLmBackendCompatibilityDetector(),
            engineRuntime = { engineRuntime },
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

    private val onlineExecutionDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OnlineAiHttpExecution.create(
            AndroidOnlineAiNetworkAccess(this) {
                onlineProfileRegistry.settings().allowMeteredNetwork
            },
        )
    }

    private val onlineExecution: OnlineAiHttpExecution
        get() = onlineExecutionDelegate.value

    private val onlineBackendDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OnlineAiBackend(onlineProfileRegistry, onlineExecution)
    }

    internal val onlineBackend: AiBackend
        get() = onlineBackendDelegate.value

    private val defaultTargetStoreDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FileDefaultTargetStore(filesDir)
    }

    /** The one global default target shared by chat, scripts and the models and services page (D114). */
    internal val defaultTargetStore: DefaultTargetStore
        get() = defaultTargetStoreDelegate.value

    private val aiBackendDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CompositeAiBackend(
            backends = listOf(
                localBackend,
                onlineBackend,
            ),
            preferredDefaultBackendId = OnlineAiBackend.BACKEND_ID,
            defaultTargetStore = defaultTargetStore,
        )
    }

    /** Instrumentation only: a scripted backend for chat UI tests on real devices. */
    @VisibleForTesting
    @Volatile
    internal var aiBackendOverride: AiBackend? = null

    /** Unified local/remote dispatch with no automatic fallback across the locality boundary. */
    internal val aiBackend: AiBackend
        get() = aiBackendOverride ?: aiBackendDelegate.value

    override fun onCreate() {
        super.onCreate()
        // The chat and the script provider run in two processes; only one of them holds the model (D227).
        LocalEngineHandover.register(this, ::releaseLocalEngineForAnotherProcess)
    }

    /** Runs on a worker thread when another process of the app is about to load an on-device engine. */
    private fun releaseLocalEngineForAnotherProcess(): Boolean {
        if (!engineRuntimeDelegate.isInitialized()) return true
        // The chat keeps its local session open between turns; an idle one gives the engine back.
        val chatReleased = CountDownLatch(1)
        Handler(mainLooper).post {
            try {
                ChatGenerationCoordinator.existing()?.releaseIdleLocalEngine()
            } finally {
                chatReleased.countDown()
            }
        }
        chatReleased.await(CHAT_RELEASE_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        return engineRuntime.releaseIdleEngine(LocalEngineHandover.RELEASE_WAIT_MILLIS)
    }

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

    private companion object {
        const val CHAT_RELEASE_WAIT_MILLIS = 1_000L
    }

    override fun onTerminate() {
        if (onlineExecutionDelegate.isInitialized()) onlineExecution.close()
        if (engineRuntimeDelegate.isInitialized()) engineRuntime.close()
        super.onTerminate()
    }
}
