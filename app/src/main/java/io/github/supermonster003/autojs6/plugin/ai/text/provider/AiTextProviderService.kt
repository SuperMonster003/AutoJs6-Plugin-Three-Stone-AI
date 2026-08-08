package io.github.supermonster003.autojs6.plugin.ai.text.provider

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.ai.text.AiTextPlugin
import io.github.supermonster003.autojs6.plugin.ai.text.aiProviderInfo
import io.github.supermonster003.autojs6.plugin.ai.text.backend.GenerationBackendFactory
import io.github.supermonster003.autojs6.plugin.ai.text.backend.LiteRtLmGenerationBackend
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelRepository
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.text.api.AiTextCodec
import org.autojs.plugin.ai.text.api.IAiModelListCallback
import org.autojs.plugin.ai.text.api.IAiTextCallback
import org.autojs.plugin.ai.text.api.IAiTextProvider
import org.autojs.plugin.ai.text.api.IAiTextSession
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference

class AiTextProviderService : Service() {
    private lateinit var callerVerifier: HostCallerVerifier
    private lateinit var worker: ExecutorService
    private lateinit var timeoutScheduler: ScheduledExecutorService
    private lateinit var callbackLane: SerialCallbackLane
    private lateinit var repository: ModelRepository
    private lateinit var modelPager: ModelPager
    private lateinit var backendFactory: GenerationBackendFactory
    private val activeSession = AtomicReference<RemoteAiTextSession?>()
    private val sessions = ConcurrentHashMap.newKeySet<RemoteAiTextSession>()

    override fun onCreate() {
        super.onCreate()
        callerVerifier = HostCallerVerifier(this)
        worker = BoundedExecutors.worker()
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "ai-text-timeout").apply { isDaemon = true }
        }
        callbackLane = SerialCallbackLane()
        repository = ModelRepository(this)
        modelPager = ModelPager(repository)
        val cache = File(cacheDir, "litertlm").apply { mkdirs() }
        backendFactory = GenerationBackendFactory { modelPath ->
            LiteRtLmGenerationBackend(modelPath, cache)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        activeSession.set(null)
        sessions.toList().forEach(RemoteAiTextSession::serviceDestroyed)
        sessions.clear()
        worker.shutdownNow()
        timeoutScheduler.shutdownNow()
        callbackLane.close()
        super.onDestroy()
    }

    private val binder = object : IAiTextProvider.Stub() {
        override fun getProviderInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return AiCommonCodec.encodeProviderInfo(aiProviderInfo())
        }

        override fun getCapabilities(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return AiTextCodec.encodeCapabilities(AiTextPlugin.capabilities)
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
                    val result = runCatching {
                        AiTextCodec.encodeModelPage(modelPager.page(AiTextCodec.decodeModelListRequest(requestCopy)))
                    }
                    callbackLane.dispatch(
                        callback = {
                            result.fold(
                                onSuccess = callback::onPage,
                                onFailure = {
                                    callback.onFailed(
                                        AiCommonCodec.encodeError(
                                            AiError(AiErrorCode.INVALID_REQUEST, "Model-list request is invalid"),
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
            callback: IAiTextCallback?,
        ): IAiTextSession {
            val ownerUid: Int
            val safeRequest: ByteArray
            val safeDescriptors: Array<out ParcelFileDescriptor>
            val safeCallback: IAiTextCallback
            try {
                ownerUid = callerVerifier.enforceAllowedCaller()
                safeRequest = requireNotNull(request) { "AI text request metadata is missing" }
                safeDescriptors = requireNotNull(descriptors) { "AI text request descriptors are missing" }
                safeCallback = requireNotNull(callback) { "AI text callback is missing" }
                BinderInputPolicy.requireEnvelopeSize(safeRequest.size)
                BinderInputPolicy.requireRequestDescriptorCount(
                    safeDescriptors.size,
                    minOf(
                        AiTextPlugin.capabilities.maximumRequestDescriptors,
                        AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
                    ),
                )
            } catch (error: Throwable) {
                OwnedParcelFileDescriptors.closeIncoming(descriptors)
                throw error
            }
            val ownedDescriptors = OwnedParcelFileDescriptors.duplicateBeforeAsync(safeDescriptors)
            val session = try {
                RemoteAiTextSession(
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
}
