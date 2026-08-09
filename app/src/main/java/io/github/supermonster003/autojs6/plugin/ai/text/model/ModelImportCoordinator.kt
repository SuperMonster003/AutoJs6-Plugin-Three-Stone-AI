package io.github.supermonster003.autojs6.plugin.ai.text.model

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Owns the one process-local import independently of any Activity instance. A process death is
 * handled by the repository transaction marker the next time the model manager cold-starts.
 */
internal class ModelImportCoordinator private constructor(context: Context) {
    fun interface Observer {
        fun onStateChanged(state: ModelImportState<ImportedModel>)
    }

    private val applicationContext = context.applicationContext
    private val repository = ModelRepository(applicationContext)
    private val stateMachine = ModelImportStateMachine<ImportedModel>()
    private val observers = CopyOnWriteArraySet<Observer>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litertlm-model-import").apply { isDaemon = true }
    }

    init {
        try {
            executor.execute {
                runCatching {
                    releaseStaleImportPermissions()
                    repository.recoverInterruptedImportFromManagerColdStart()
                    repository.current()
                }.fold(
                    onSuccess = stateMachine::finishPreparation,
                    onFailure = { error ->
                        logFailure(operationId = null, ModelImportFailureReason.UNKNOWN, error)
                        stateMachine.failPreparation()
                    },
                )
                publishLatest()
            }
        } catch (error: RejectedExecutionException) {
            logFailure(operationId = null, ModelImportFailureReason.UNKNOWN, error)
            stateMachine.failPreparation()
        }
    }

    fun attach(observer: Observer): ModelImportState<ImportedModel> {
        observers += observer
        return stateMachine.snapshot()
    }

    fun detach(observer: Observer) {
        observers -= observer
    }

    fun snapshot(): ModelImportState<ImportedModel> = stateMachine.snapshot()

    fun beginImport(uri: Uri, grantedFlags: Int): Boolean {
        val operationId = stateMachine.begin() ?: return false
        val persistedReadPermission = persistReadPermission(uri, grantedFlags)
        publishLatest()
        try {
            executor.execute {
                try {
                    runCatching { repository.importFrom(uri) }.fold(
                        onSuccess = { stateMachine.succeed(operationId, it) },
                        onFailure = { error ->
                            val reason = error.toModelImportFailureReason()
                            logFailure(operationId, reason, error)
                            stateMachine.fail(operationId, repository.current(), reason)
                        },
                    )
                    publishLatest()
                } finally {
                    if (persistedReadPermission) releaseReadPermission(uri)
                }
            }
        } catch (error: RejectedExecutionException) {
            if (persistedReadPermission) releaseReadPermission(uri)
            val reason = ModelImportFailureReason.UNKNOWN
            logFailure(operationId, reason, error)
            stateMachine.fail(operationId, repository.current(), reason)
            publishLatest()
        }
        return true
    }

    private fun persistReadPermission(uri: Uri, grantedFlags: Int): Boolean {
        if (grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return false
        return runCatching {
            applicationContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
    }

    private fun releaseReadPermission(uri: Uri) {
        runCatching {
            applicationContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun releaseStaleImportPermissions() {
        applicationContext.contentResolver.persistedUriPermissions.forEach { permission ->
            if (permission.isReadPermission) releaseReadPermission(permission.uri)
        }
    }

    private fun publishLatest() {
        mainHandler.post {
            val latest = stateMachine.snapshot()
            observers.forEach { observer -> runCatching { observer.onStateChanged(latest) } }
        }
    }

    private fun logFailure(
        operationId: Long?,
        reason: ModelImportFailureReason,
        error: Throwable,
    ) {
        val operation = operationId?.toString() ?: "preparation"
        val message = "Model import failure: operation=$operation reason=$reason type=${error.javaClass.name}"
        val debuggable = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            Log.e(TAG, message, error)
        } else {
            Log.e(TAG, message)
        }
    }

    companion object {
        private const val TAG = "ModelImportCoordinator"

        @Volatile
        private var instance: ModelImportCoordinator? = null

        fun get(context: Context): ModelImportCoordinator = instance ?: synchronized(this) {
            instance ?: ModelImportCoordinator(context.applicationContext).also { instance = it }
        }
    }
}
