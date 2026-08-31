package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiApplication
import io.github.supermonster003.autojs6.plugin.threestoneai.aiProviderInfo
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.IAiCallback
import org.autojs.plugin.ai.provider.api.IAiProvider
import org.autojs.plugin.ai.provider.api.IAiSession
import org.autojs.plugin.ai.provider.api.IAiTargetListCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference

class ThreeStoneAiProviderService : Service() {
    private lateinit var callerVerifier: HostCallerVerifier
    private lateinit var worker: ExecutorService
    private lateinit var timeoutScheduler: ScheduledExecutorService
    private lateinit var callbackLane: SerialCallbackLane
    private lateinit var targetPager: TargetPager
    private lateinit var aiBackend: AiBackend
    private val activeSession = AtomicReference<RemoteThreeStoneAiSession?>()
    private val sessions = ConcurrentHashMap.newKeySet<RemoteThreeStoneAiSession>()

    override fun onCreate() {
        super.onCreate()
        callerVerifier = HostCallerVerifier(this)
        worker = BoundedExecutors.worker()
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "three-stone-ai-timeout").apply { isDaemon = true }
        }
        callbackLane = SerialCallbackLane()
        val pluginApplication = application as ThreeStoneAiApplication
        aiBackend = pluginApplication.aiBackend
        targetPager = TargetPager(aiBackend::catalog)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        activeSession.set(null)
        // ConcurrentHashMap's weakly consistent traversal tolerates serviceDestroyed() removing
        // itself through onFinished. Kotlin's size-based toList() does not: a concurrent removal
        // can leave it calling next() after the iterator has been exhausted.
        sessions.forEach(RemoteThreeStoneAiSession::serviceDestroyed)
        sessions.clear()
        worker.shutdownNow()
        targetPager.close()
        timeoutScheduler.shutdownNow()
        callbackLane.close()
        super.onDestroy()
    }

    private val binder = object : IAiProvider.Stub() {
        override fun getProviderInfo(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return AiCommonCodec.encodeProviderInfo(aiProviderInfo(aiBackend.catalog()))
        }

        override fun getCapabilities(): ByteArray {
            callerVerifier.enforceAllowedCaller()
            return AiProviderCodec.encodeCapabilities(ThreeStoneAiPlugin.capabilities)
        }

        override fun listTargets(request: ByteArray, callback: IAiTargetListCallback) {
            callerVerifier.enforceAllowedCaller()
            try {
                BinderInputPolicy.requireEnvelopeSize(request.size)
            } catch (_: IllegalArgumentException) {
                publishTargetListError(callback, AiErrorCode.INVALID_REQUEST, "Target-list request is invalid")
                return
            }
            val requestCopy = request.copyOf()
            if (!submitListWork {
                    val result = runCatching { decodeListRequest(requestCopy) }
                        .mapCatching(targetPager::page)
                        .mapCatching(::encodeTargetPage)
                    callbackLane.dispatch(
                        callback = {
                            result.fold(
                                onSuccess = callback::onPage,
                                onFailure = { error ->
                                    val failure = classifyTargetListFailure(error)
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
                publishTargetListError(callback, AiErrorCode.BACKPRESSURE, "Target-list queue is full")
            }
        }

        override fun openSession(
            request: ByteArray?,
            descriptors: Array<out ParcelFileDescriptor>?,
            callback: IAiCallback?,
        ): IAiSession {
            val ownerUid: Int
            val safeRequest: ByteArray
            val safeDescriptors: Array<out ParcelFileDescriptor>
            val safeCallback: IAiCallback
            try {
                ownerUid = callerVerifier.enforceAllowedCaller()
                safeRequest = requireNotNull(request) { "local AI request metadata is missing" }
                safeDescriptors = requireNotNull(descriptors) { "local AI request descriptors are missing" }
                safeCallback = requireNotNull(callback) { "local AI callback is missing" }
                BinderInputPolicy.requireEnvelopeSize(safeRequest.size)
                BinderInputPolicy.requireRequestDescriptorCount(
                    safeDescriptors.size,
                    minOf(
                        ThreeStoneAiPlugin.capabilities.maximumRequestDescriptors,
                        AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
                    ),
                )
            } catch (error: Throwable) {
                OwnedParcelFileDescriptors.closeIncoming(descriptors)
                throw error
            }
            val ownedDescriptors = OwnedParcelFileDescriptors.duplicateBeforeAsync(safeDescriptors)
            val session = try {
                RemoteThreeStoneAiSession(
                    ownerUid = ownerUid,
                    requestMetadata = safeRequest.copyOf(),
                    descriptors = ownedDescriptors,
                    callback = safeCallback,
                    callerVerifier = callerVerifier,
                    aiBackend = aiBackend,
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

    private fun publishTargetListError(callback: IAiTargetListCallback, code: Int, message: String) {
        callbackLane.dispatch(
            callback = { callback.onFailed(AiCommonCodec.encodeError(AiError(code, message))) },
            onFailure = {},
        )
    }

    private fun decodeListRequest(request: ByteArray) = try {
        AiProviderCodec.decodeTargetListRequest(request)
    } catch (error: Throwable) {
        throw InvalidTargetListRequestException()
    }

    private fun encodeTargetPage(page: org.autojs.plugin.ai.provider.api.AiTargetPage): ByteArray = try {
        AiProviderCodec.encodeTargetPage(page)
    } catch (error: Throwable) {
        throw TargetListingFailedException(error)
    }

    private fun classifyTargetListFailure(error: Throwable): TargetListFailure = when (error) {
        is UnsupportedTargetListProtocolException ->
            TargetListFailure(AiErrorCode.UNSUPPORTED_PROTOCOL, "Target-list protocol is unsupported")
        is TargetCatalogUnavailableException ->
            TargetListFailure(AiErrorCode.PROVIDER_UNAVAILABLE, "Target catalog is unavailable")
        is TargetListingFailedException ->
            TargetListFailure(AiErrorCode.PROVIDER_FAILED, "Target listing failed")
        else -> TargetListFailure(AiErrorCode.INVALID_REQUEST, "Target-list request is invalid")
    }

    private data class TargetListFailure(val code: Int, val message: String)
}
