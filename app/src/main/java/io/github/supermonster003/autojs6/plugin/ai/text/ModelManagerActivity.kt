package io.github.supermonster003.autojs6.plugin.ai.text

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import io.github.supermonster003.autojs6.plugin.ai.text.model.ImportedModel
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportCoordinator
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportFailureReason
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportState

class ModelManagerActivity : Activity() {
    private lateinit var importCoordinator: ModelImportCoordinator
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private lateinit var progress: ProgressBar
    private var lastNotifiedOperationId = 0L
    private val importObserver = ModelImportCoordinator.Observer(::renderImportState)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastNotifiedOperationId = savedInstanceState?.getLong(STATE_LAST_NOTIFIED_OPERATION_ID) ?: 0L
        setContentView(createContentView())
        importCoordinator = ModelImportCoordinator.get(applicationContext)
    }

    override fun onStart() {
        super.onStart()
        renderImportState(importCoordinator.attach(importObserver))
    }

    override fun onStop() {
        importCoordinator.detach(importObserver)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_LAST_NOTIFIED_OPERATION_ID, lastNotifiedOperationId)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_MODEL && resultCode == RESULT_OK) {
            data?.data?.let { uri -> importModel(uri, data.flags) }
        }
    }

    private fun createContentView(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(24))

            addView(TextView(context).apply {
                text = getString(R.string.app_name)
                textSize = 24f
            })
            addView(TextView(context).apply {
                text = getString(R.string.screen_description)
                textSize = 16f
                setPadding(0, dp(20), 0, dp(20))
            })
            status = TextView(context).apply { textSize = 15f }
            addView(status, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            progress = ProgressBar(context).apply { visibility = View.GONE }
            addView(progress)
            importButton = Button(context).apply {
                text = getString(R.string.button_import_model)
                setOnClickListener { openModelPicker() }
            }
            addView(importButton)
        }
    }

    @Suppress("DEPRECATION")
    private fun openModelPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_OPEN_MODEL)
    }

    private fun importModel(uri: Uri, grantedFlags: Int) {
        if (!importCoordinator.beginImport(uri, grantedFlags)) renderImportState(importCoordinator.snapshot())
    }

    private fun renderImportState(state: ModelImportState<ImportedModel>) {
        when (state) {
            ModelImportState.Preparing -> {
                setImportUi(inProgress = true, importEnabled = false)
                status.text = getString(R.string.import_in_progress)
            }
            ModelImportState.Unavailable -> {
                setImportUi(inProgress = false, importEnabled = false)
                status.text = getString(R.string.import_failed)
            }
            is ModelImportState.Ready -> {
                setImportUi(inProgress = false, importEnabled = true)
                showCurrent(state.current)
            }
            is ModelImportState.Running -> {
                setImportUi(inProgress = true, importEnabled = false)
                status.text = getString(R.string.import_in_progress)
            }
            is ModelImportState.Succeeded -> {
                setImportUi(inProgress = false, importEnabled = true)
                showModel(state.model)
                notifyOnce(state.operationId, R.string.import_succeeded, Toast.LENGTH_SHORT)
            }
            is ModelImportState.Failed -> {
                setImportUi(inProgress = false, importEnabled = true)
                val message = importFailureMessage(state.reason)
                state.current?.let(::showModel) ?: run { status.text = getString(message) }
                notifyOnce(state.operationId, message, Toast.LENGTH_LONG)
            }
        }
    }

    private fun showCurrent(model: ImportedModel?) {
        model?.let(::showModel) ?: run { status.text = getString(R.string.model_none) }
    }

    private fun showModel(model: ImportedModel) {
        status.text = getString(
            R.string.model_status_format,
            model.displayName,
            Formatter.formatFileSize(this, model.sizeBytes),
            model.sha256,
        )
    }

    private fun setImportUi(inProgress: Boolean, importEnabled: Boolean) {
        progress.visibility = if (inProgress) View.VISIBLE else View.GONE
        importButton.isEnabled = importEnabled
    }

    private fun importFailureMessage(reason: ModelImportFailureReason): Int = when (reason) {
        ModelImportFailureReason.INVALID_FORMAT -> R.string.import_failed_invalid_format
        ModelImportFailureReason.MODEL_TOO_LARGE -> R.string.import_failed_model_too_large
        ModelImportFailureReason.INSUFFICIENT_STORAGE -> R.string.import_failed_insufficient_storage
        ModelImportFailureReason.SOURCE_UNAVAILABLE -> R.string.import_failed_source_unavailable
        ModelImportFailureReason.INTERRUPTED -> R.string.import_failed_interrupted
        ModelImportFailureReason.UNKNOWN -> R.string.import_failed
    }

    private fun notifyOnce(operationId: Long, message: Int, duration: Int) {
        if (operationId <= lastNotifiedOperationId) return
        lastNotifiedOperationId = operationId
        Toast.makeText(this, message, duration).show()
    }

    private companion object {
        const val REQUEST_OPEN_MODEL = 1001
        const val STATE_LAST_NOTIFIED_OPERATION_ID = "lastNotifiedOperationId"
    }
}
