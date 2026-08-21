package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.ondeviceai.OnDeviceAiPlugin
import io.github.supermonster003.autojs6.plugin.ondeviceai.OnDeviceAiApplication
import io.github.supermonster003.autojs6.plugin.ondeviceai.aiProviderInfo
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationBackendFactory
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.LiteRtLmBackendCompatibilityDetector
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelRepository
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.IAiModelListCallback
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiCallback
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiProvider
import org.autojs.plugin.ondeviceai.api.IOnDeviceAiSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference

class OnDeviceAiProviderService : Service() {
    private lateinit var callerVerifier: HostCallerVerifier
    private lateinit var worker: ExecutorService
    private lateinit var timeoutScheduler: ScheduledExecutorService
    private lateinit var callbackLane: SerialCallbackLane
    private lateinit var repository: ModelRepository
    private lateinit var modelPager: ModelPager
    private lateinit var backendFactory: GenerationBackendFactory
    private lateinit var backendCompatibilityDetector: LiteRtLmBackendCompatibilityDetector
    private val activeSession = AtomicReference<RemoteOnDeviceAiSession?>()
    private val sessions = ConcurrentHashMap.newKeySet<RemoteOnDeviceAiSession>()

    override fun onCreate() {
        super.onCreate()
        callerVerifier = HostCallerVerifier(this)
        worker = BoundedExecutors.worker()
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "on-device-ai-timeout").apply { isDaemon = true }
        }
        callbackLane = SerialCallbackLane()
        repository = ModelRepository(this)
        backendCompatibilityDetector = LiteRtLmBackendCompatibilityDetector()
        modelPager = ModelPager(repository, backendCompatibilityDetector::profiles)
        val engineRuntime = (application as OnDeviceAiApplication).engineRuntime
        backendFactory = GenerationBackendFactory { modelSha256, modelPath, backendProfile ->
            engineRuntime.createBackend(
                modelSha256,
                modelPath,
                backendCompatibilityDetector.requireAvailable(backendProfile),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        activeSession.set(null)
        sessions.toList().forEach(RemoteOnDeviceAiSession::serviceDestroyed)
        sessions.clear()
        worker.shutdownNow()
        modelPager.close()
        timeoutScheduler.shutdownNow()
        callbackLane.close()
        super.onDestroy()
    }

    private val binder = object : IOnDeviceAiProvider.Stub() {
        override fun getProviderInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return AiCommonCodec.encodeProviderInfo(aiProviderInfo())
        }

        override fun getCapabilities(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return OnDeviceAiCodec.encodeCapabilities(OnDeviceAiPlugin.capabilities)
        }

        override fun listModels(request: ByteArray, callback: IAiModelListCallback) {
            callerVerifier.enforceAllowedCaller()
            try {
                BinderInputPolicy.requireEnvelopeSize(request.size)
            } catch (_: IllegalArgumentException) {
                publishModelListError(callback, AiErrorCode.INVALID_REQUEST, "Model-list request is invalid")
                return
            }
            val requestCopy = request.copyOf()
            if (!submitListWork {
                    val result = runCatching { decodeListRequest(requestCopy) }
                        .mapCatching(modelPager::page)
                        .mapCatching(::encodeModelPage)
                    callbackLane.dispatch(
                        callback = {
                            result.fold(
                                onSuccess = callback::onPage,
                                onFailure = { error ->
                                    val failure = classifyModelListFailure(error)
                                    callback.onFailed(
                                        AiCommonCodec.encodeError(
                                            AiError(
                                                failure.code,
                                                failure.message,
                                            ),
                                        ),
                                    )
                                },
                            )
                        },
                        onFailure = {},
                    )
                }
            ) {
                publishModelListError(callback, AiErrorCode.BACKPRESSURE, "Model-list queue is full")
            }
        }

        override fun openSession(
            request: ByteArray?,
            descriptors: Array<out ParcelFileDescriptor>?,
            callback: IOnDeviceAiCallback?,
        ): IOnDeviceAiSession {
            val ownerUid: Int
            val safeRequest: ByteArray
            val safeDescriptors: Array<out ParcelFileDescriptor>
            val safeCallback: IOnDeviceAiCallback
            try {
                ownerUid = callerVerifier.enforceAllowedCaller()
                safeRequest = requireNotNull(request) { "on-device AI request metadata is missing" }
                safeDescriptors = requireNotNull(descriptors) { "on-device AI request descriptors are missing" }
                safeCallback = requireNotNull(callback) { "on-device AI callback is missing" }
                BinderInputPolicy.requireEnvelopeSize(safeRequest.size)
                BinderInputPolicy.requireRequestDescriptorCount(
                    safeDescriptors.size,
                    minOf(
                        OnDeviceAiPlugin.capabilities.maximumRequestDescriptors,
                        AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
                    ),
                )
            } catch (error: Throwable) {
                OwnedParcelFileDescriptors.closeIncoming(descriptors)
                throw error
            }
            val ownedDescriptors = OwnedParcelFileDescriptors.duplicateBeforeAsync(safeDescriptors)
            val session = try {
                RemoteOnDeviceAiSession(
                    ownerUid = ownerUid,
                    requestMetadata = safeRequest.copyOf(),
                    descriptors = ownedDescriptors,
                    callback = safeCallback,
                    callerVerifier = callerVerifier,
                    repository = repository,
                    backendFactory = backendFactory,
                    worker = worker,
                    timeoutScheduler = timeoutScheduler,
                    callbackLane = callbackLane,
                    onFinished = { finished ->
                        activeSession.compareAndSet(finished, null)
                        sessions.remove(finished)
                    },
                )
            } catch (error: Throwable) {
                ownedDescriptors.close()
                throw error
            }
            sessions += session
            if (activeSession.compareAndSet(null, session)) session.start() else session.rejectBusy()
            return session
        }
    }

    private fun submitListWork(block: () -> Unit): Boolean = try {
        worker.execute(block)
        true
    } catch (_: RejectedExecutionException) {
        false
    }

    private fun publishModelListError(callback: IAiModelListCallback, code: Int, message: String) {
        callbackLane.dispatch(
            callback = { callback.onFailed(AiCommonCodec.encodeError(AiError(code, message))) },
            onFailure = {},
        )
    }

    private fun decodeListRequest(request: ByteArray) = try {
        OnDeviceAiCodec.decodeModelListRequest(request)
    } catch (error: Throwable) {
        throw InvalidModelListRequestException()
    }

    private fun encodeModelPage(page: org.autojs.plugin.ondeviceai.api.AiModelPage): ByteArray = try {
        OnDeviceAiCodec.encodeModelPage(page)
    } catch (error: Throwable) {
        throw ModelListingFailedException(error)
    }

    private fun classifyModelListFailure(error: Throwable): ModelListFailure = when (error) {
        is UnsupportedModelListProtocolException ->
            ModelListFailure(AiErrorCode.UNSUPPORTED_PROTOCOL, "Model-list protocol is unsupported")
        is ModelListingUnavailableException ->
            ModelListFailure(AiErrorCode.PROVIDER_UNAVAILABLE, "Model catalog is unavailable")
        is ModelListingFailedException ->
            ModelListFailure(AiErrorCode.PROVIDER_FAILED, "Model listing failed")
        else -> ModelListFailure(AiErrorCode.INVALID_REQUEST, "Model-list request is invalid")
    }

    private data class ModelListFailure(val code: Int, val message: String)
}
