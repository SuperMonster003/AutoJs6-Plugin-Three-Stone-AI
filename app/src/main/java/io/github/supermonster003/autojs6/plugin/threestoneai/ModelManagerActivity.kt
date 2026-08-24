package io.github.supermonster003.autojs6.plugin.threestoneai

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadCleanupResult
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadProgress
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadState
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModel
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ImportedModel
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelDeletionState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelDisplayNamePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelHealthCheckState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelHealthStatus
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportProgress
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportStage
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportStoragePreflight
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelManagerState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRenameState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelSelectionState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelStorageCleanupState

class ModelManagerActivity : ConfiguredActivity() {
    private lateinit var importCoordinator: ModelImportCoordinator
    private lateinit var downloadCoordinator: ModelDownloadCoordinator
    private lateinit var downloadStatus: TextView
    private lateinit var downloadButton: Button
    private lateinit var cancelDownloadButton: Button
    private lateinit var importDownloadedButton: Button
    private lateinit var downloadProgress: ProgressBar
    private lateinit var status: TextView
    private lateinit var copyModelIdButton: Button
    private lateinit var importButton: Button
    private lateinit var checkAfterImportOption: CheckBox
    private lateinit var importStoragePreflight: TextView
    private lateinit var cancelImportButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var catalogSummary: TextView
    private lateinit var cleanupStorageButton: Button
    private lateinit var catalogRows: LinearLayout
    private var copyableModelId: String? = null
    private var cancellableOperationId: Long? = null
    private var cancellableDownloadOperationId: Long? = null
    private var downloadedDestination: ModelDownloadState.Succeeded<Uri>? = null
    private var pendingDownloadModelId: String? = null
    private var lastNotifiedImportOperationId = 0L
    private var lastNotifiedDownloadOperationId = 0L
    private var lastNotifiedSelectionOperationId = 0L
    private var lastNotifiedDeletionOperationId = 0L
    private var lastNotifiedRenameOperationId = 0L
    private var lastNotifiedStorageCleanupOperationId = 0L
    private var lastNotifiedHealthCheckOperationId = 0L
    private var checkAfterImport = true
    private val managerObserver = ModelImportCoordinator.ManagerObserver(::renderManagerState)
    private val downloadObserver = ModelDownloadCoordinator.Observer(::renderDownloadState)
    private var renderedManagerView: ModelManagerViewState? = null
    private var importStateAllowsPicker = false
    private var catalogMutationBlocksPicker = false
    private var storageAllowsPicker = false
    private var downloadStateAllowsStart = true
    private var downloadBlocksImport = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importCoordinator = ModelImportCoordinator.get(applicationContext)
        downloadCoordinator = ModelDownloadCoordinator.get(applicationContext)
        pendingDownloadModelId = savedInstanceState?.getString(STATE_PENDING_DOWNLOAD_MODEL_ID)
        checkAfterImport = savedInstanceState?.getBoolean(STATE_CHECK_AFTER_IMPORT)
            ?: getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).getBoolean(
                PREFERENCE_CHECK_AFTER_IMPORT,
                true,
            )
        savedInstanceState?.takeIf { restored ->
            restored.getString(STATE_PROCESS_SESSION_TOKEN) == importCoordinator.processSessionToken
        }?.let { restored ->
            lastNotifiedImportOperationId = restored.getLong(STATE_LAST_NOTIFIED_IMPORT_OPERATION_ID)
            lastNotifiedSelectionOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_SELECTION_OPERATION_ID)
            lastNotifiedDeletionOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_DELETION_OPERATION_ID)
            lastNotifiedRenameOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_RENAME_OPERATION_ID)
            lastNotifiedStorageCleanupOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_STORAGE_CLEANUP_OPERATION_ID)
            lastNotifiedHealthCheckOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_HEALTH_CHECK_OPERATION_ID)
        }
        savedInstanceState?.takeIf { restored ->
            restored.getString(STATE_DOWNLOAD_PROCESS_SESSION_TOKEN) ==
                downloadCoordinator.processSessionToken
        }?.let { restored ->
            lastNotifiedDownloadOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_DOWNLOAD_OPERATION_ID)
        }
        setContentView(createContentView())
    }

    override fun onStart() {
        super.onStart()
        renderManagerState(importCoordinator.attachManager(managerObserver))
        renderDownloadState(downloadCoordinator.attach(downloadObserver))
    }

    override fun onStop() {
        downloadCoordinator.detach(downloadObserver)
        importCoordinator.detachManager(managerObserver)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_LAST_NOTIFIED_IMPORT_OPERATION_ID, lastNotifiedImportOperationId)
        outState.putLong(STATE_LAST_NOTIFIED_DOWNLOAD_OPERATION_ID, lastNotifiedDownloadOperationId)
        outState.putLong(STATE_LAST_NOTIFIED_SELECTION_OPERATION_ID, lastNotifiedSelectionOperationId)
        outState.putLong(STATE_LAST_NOTIFIED_DELETION_OPERATION_ID, lastNotifiedDeletionOperationId)
        outState.putLong(STATE_LAST_NOTIFIED_RENAME_OPERATION_ID, lastNotifiedRenameOperationId)
        outState.putLong(
            STATE_LAST_NOTIFIED_STORAGE_CLEANUP_OPERATION_ID,
            lastNotifiedStorageCleanupOperationId,
        )
        outState.putLong(
            STATE_LAST_NOTIFIED_HEALTH_CHECK_OPERATION_ID,
            lastNotifiedHealthCheckOperationId,
        )
        outState.putBoolean(STATE_CHECK_AFTER_IMPORT, checkAfterImportOption.isChecked)
        outState.putString(STATE_PENDING_DOWNLOAD_MODEL_ID, pendingDownloadModelId)
        outState.putString(STATE_PROCESS_SESSION_TOKEN, importCoordinator.processSessionToken)
        outState.putString(
            STATE_DOWNLOAD_PROCESS_SESSION_TOKEN,
            downloadCoordinator.processSessionToken,
        )
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_OPEN_MODEL -> if (resultCode == RESULT_OK) {
                data?.data?.let { uri -> importModel(uri, data.flags) }
            }
            REQUEST_CREATE_MODEL_DOWNLOAD -> {
                val model = pendingDownloadModelId?.let(RecommendedModelCatalog::find)
                pendingDownloadModelId = null
                if (resultCode == RESULT_OK && model != null) {
                    data?.data?.let { uri -> downloadModel(model, uri, data.flags) }
                }
                updateDownloadButtonEnabled()
            }
        }
    }

    private fun createContentView(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(appPalette.windowBackground)
            setPadding(dp(24), dp(12), dp(24), dp(32))
            addView(TextView(context).apply {
                text = getString(R.string.screen_description)
                textSize = 16f
                setPadding(0, dp(20), 0, dp(20))
            })
            addView(TextView(context).apply {
                text = getString(R.string.download_section_title)
                textSize = 18f
                setPadding(0, 0, 0, dp(8))
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            downloadStatus = TextView(context).apply {
                text = getString(R.string.download_description)
                textSize = 14f
                setTextIsSelectable(true)
            }
            addView(
                downloadStatus,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            downloadProgress = ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleHorizontal,
            ).apply {
                max = PROGRESS_MAX
                visibility = View.GONE
            }
            addView(
                downloadProgress,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            cancelDownloadButton = Button(context).apply {
                text = getString(R.string.button_cancel_download)
                visibility = View.GONE
                setOnClickListener { cancelDownload() }
            }
            addView(cancelDownloadButton)
            downloadButton = Button(context).apply {
                text = getString(R.string.button_download_model)
                setOnClickListener { chooseRecommendedModel() }
            }
            addView(downloadButton)
            importDownloadedButton = Button(context).apply {
                text = getString(R.string.button_import_downloaded_model)
                visibility = View.GONE
                setOnClickListener { importDownloadedModel() }
            }
            addView(importDownloadedButton)
            addView(TextView(context).apply {
                text = getString(R.string.import_section_title)
                textSize = 18f
                setPadding(0, dp(24), 0, dp(8))
            }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            status = TextView(context).apply {
                textSize = 15f
                setTextIsSelectable(true)
            }
            addView(status, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            copyModelIdButton = Button(context).apply {
                text = getString(R.string.button_copy_model_id)
                visibility = View.GONE
                setOnClickListener { copyModelId() }
            }
            addView(copyModelIdButton)
            progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = PROGRESS_MAX
                visibility = View.GONE
            }
            addView(progress, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cancelImportButton = Button(context).apply {
                text = getString(R.string.button_cancel_import)
                visibility = View.GONE
                setOnClickListener { cancelImport() }
            }
            addView(cancelImportButton)
            importButton = Button(context).apply {
                text = getString(R.string.button_import_model)
                setOnClickListener { openModelPicker() }
            }
            addView(importButton)
            checkAfterImportOption = CheckBox(context).apply {
                text = getString(R.string.option_check_after_import)
                isChecked = checkAfterImport
                setOnCheckedChangeListener { _, checked ->
                    checkAfterImport = checked
                    getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREFERENCE_CHECK_AFTER_IMPORT, checked)
                        .apply()
                }
            }
            addView(checkAfterImportOption)
            importStoragePreflight = TextView(context).apply {
                textSize = 14f
                setPadding(0, dp(8), 0, 0)
            }
            addView(
                importStoragePreflight,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )

            catalogSummary = TextView(context).apply {
                textSize = 15f
                setPadding(0, dp(24), 0, dp(8))
            }
            addView(catalogSummary, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            cleanupStorageButton = Button(context).apply {
                text = getString(R.string.button_cleanup_storage)
                isEnabled = false
                setOnClickListener { cleanUnreferencedStorage() }
            }
            addView(cleanupStorageButton)
            catalogRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(catalogRows, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        applyThemeToControls(content)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(appPalette.windowBackground)
            addView(createAppToolbar(R.string.model_manager_title, showBack = true))
            addView(
                ScrollView(context).apply {
                    isFillViewport = true
                    addView(content)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
            applySystemBarInsets(this)
        }
    }

    private fun chooseRecommendedModel() {
        val models = RecommendedModelCatalog.models
        val labels = models.map { model ->
            getString(
                R.string.download_catalog_item,
                model.displayName,
                Formatter.formatFileSize(this, model.expectedSizeBytes),
                model.license,
            )
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.download_catalog_title)
            .setNegativeButton(android.R.string.cancel, null)
            .setItems(labels) { _, index -> confirmRecommendedModel(models[index]) }
            .show()
            .also(::tintDialogButtons)
    }

    private fun confirmRecommendedModel(model: RecommendedModel) {
        AlertDialog.Builder(this)
            .setTitle(model.displayName)
            .setMessage(
                getString(
                    R.string.download_confirm_message,
                    Formatter.formatFileSize(this, model.expectedSizeBytes),
                    model.expectedSha256,
                    model.license,
                ),
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.button_view_model_source) { _, _ ->
                openModelSource(model)
            }
            .setPositiveButton(R.string.button_choose_download_location) { _, _ ->
                openDownloadDestinationPicker(model)
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun openModelSource(model: RecommendedModel) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.sourceUrl)))
        }.onFailure {
            Toast.makeText(this, R.string.download_source_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun openDownloadDestinationPicker(model: RecommendedModel) {
        pendingDownloadModelId = model.id
        updateDownloadButtonEnabled()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, model.fileName)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
        runCatching { startActivityForResult(intent, REQUEST_CREATE_MODEL_DOWNLOAD) }
            .onFailure {
                pendingDownloadModelId = null
                updateDownloadButtonEnabled()
                Toast.makeText(
                    this,
                    R.string.download_destination_unavailable,
                    Toast.LENGTH_LONG,
                ).show()
            }
    }

    private fun downloadModel(model: RecommendedModel, destination: Uri, grantedFlags: Int) {
        val accepted = downloadCoordinator.beginDownload(model, destination, grantedFlags)
        renderDownloadState(downloadCoordinator.state())
        if (!accepted) {
            Toast.makeText(this, R.string.download_already_running, Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelDownload() {
        val operationId = cancellableDownloadOperationId ?: return
        cancelDownloadButton.isEnabled = false
        if (downloadCoordinator.cancelDownload(operationId)) return
        renderDownloadState(downloadCoordinator.state())
    }

    private fun importDownloadedModel() {
        val completed = downloadedDestination ?: return
        val preflight = refreshImportStoragePreflight()
        if (completed.model.expectedSizeBytes > preflight.maximumAdditionalModelBytes) {
            Toast.makeText(
                this,
                getString(
                    R.string.download_import_insufficient_storage,
                    Formatter.formatFileSize(this, completed.model.expectedSizeBytes),
                    Formatter.formatFileSize(this, preflight.maximumAdditionalModelBytes),
                ),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val accepted = importCoordinator.beginImport(
            completed.destination,
            completed.grantedFlags,
            checkAfterImportOption.isChecked,
        )
        renderManagerState(importCoordinator.managerState())
        if (!accepted) {
            Toast.makeText(this, R.string.download_import_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderDownloadState(state: ModelDownloadState<Uri>) {
        when (state) {
            ModelDownloadState.Idle -> {
                setDownloadUi(inProgress = false, startEnabled = true)
                downloadStatus.text = getString(R.string.download_description)
            }
            is ModelDownloadState.Running -> {
                setDownloadUi(
                    inProgress = true,
                    startEnabled = false,
                    cancelOperationId = state.operationId,
                )
                showDownloadProgress(state.model, state.progress)
            }
            is ModelDownloadState.Cancelling -> {
                setDownloadUi(inProgress = true, startEnabled = false)
                showDownloadProgress(state.model, state.progress)
                downloadStatus.text = getString(
                    R.string.download_cancelling,
                    state.model.displayName,
                )
            }
            is ModelDownloadState.Succeeded -> {
                setDownloadUi(inProgress = false, startEnabled = true, completed = state)
                downloadStatus.text = getString(
                    R.string.download_succeeded,
                    state.model.displayName,
                    Formatter.formatFileSize(this, state.model.expectedSizeBytes),
                )
                notifyDownloadOnce(
                    state.operationId,
                    getString(R.string.download_succeeded_toast),
                    Toast.LENGTH_SHORT,
                )
            }
            is ModelDownloadState.Cancelled -> {
                setDownloadUi(inProgress = false, startEnabled = true)
                val message = terminalDownloadMessage(
                    getString(R.string.download_cancelled),
                    state.cleanup,
                )
                downloadStatus.text = message
                notifyDownloadOnce(state.operationId, message, Toast.LENGTH_LONG)
            }
            is ModelDownloadState.Failed -> {
                setDownloadUi(inProgress = false, startEnabled = true)
                val message = terminalDownloadMessage(
                    getString(downloadFailureMessage(state.reason)),
                    state.cleanup,
                )
                downloadStatus.text = message
                notifyDownloadOnce(state.operationId, message, Toast.LENGTH_LONG)
            }
        }
    }

    private fun showDownloadProgress(model: RecommendedModel, value: ModelDownloadProgress) {
        downloadProgress.isIndeterminate = value.processedBytes == 0L
        downloadProgress.progress = if (value.processedBytes >= value.totalBytes) {
            PROGRESS_MAX
        } else {
            (value.processedBytes * PROGRESS_MAX / value.totalBytes).toInt()
        }
        downloadStatus.text = if (value.processedBytes == 0L) {
            getString(R.string.download_connecting, model.displayName)
        } else {
            getString(
                R.string.download_progress_known,
                model.displayName,
                Formatter.formatFileSize(this, value.processedBytes),
                Formatter.formatFileSize(this, value.totalBytes),
                downloadProgress.progress / (PROGRESS_MAX / 100),
            )
        }
    }

    private fun setDownloadUi(
        inProgress: Boolean,
        startEnabled: Boolean,
        cancelOperationId: Long? = null,
        completed: ModelDownloadState.Succeeded<Uri>? = null,
    ) {
        cancellableDownloadOperationId = cancelOperationId
        downloadedDestination = completed
        downloadProgress.visibility = if (inProgress) View.VISIBLE else View.GONE
        cancelDownloadButton.visibility = if (cancelOperationId == null) View.GONE else View.VISIBLE
        cancelDownloadButton.isEnabled = cancelOperationId != null
        importDownloadedButton.visibility = if (completed == null) View.GONE else View.VISIBLE
        downloadStateAllowsStart = startEnabled
        downloadBlocksImport = inProgress
        updateDownloadButtonEnabled()
        updateImportButtonEnabled()
    }

    private fun terminalDownloadMessage(
        message: String,
        cleanup: ModelDownloadCleanupResult,
    ): String = when (cleanup) {
        ModelDownloadCleanupResult.NOT_NEEDED,
        ModelDownloadCleanupResult.DELETED,
        -> message
        ModelDownloadCleanupResult.TRUNCATED ->
            "$message\n${getString(R.string.download_cleanup_truncated)}"
        ModelDownloadCleanupResult.FAILED ->
            "$message\n${getString(R.string.download_cleanup_failed)}"
    }

    private fun downloadFailureMessage(reason: ModelDownloadFailureReason): Int = when (reason) {
        ModelDownloadFailureReason.NETWORK_UNAVAILABLE -> R.string.download_failed_network
        ModelDownloadFailureReason.HTTP_ERROR -> R.string.download_failed_http
        ModelDownloadFailureReason.INVALID_CONTENT -> R.string.download_failed_invalid_content
        ModelDownloadFailureReason.INTEGRITY_MISMATCH -> R.string.download_failed_integrity
        ModelDownloadFailureReason.DESTINATION_UNAVAILABLE ->
            R.string.download_failed_destination
        ModelDownloadFailureReason.INTERRUPTED -> R.string.download_failed_interrupted
        ModelDownloadFailureReason.UNKNOWN -> R.string.download_failed
    }

    @Suppress("DEPRECATION")
    private fun openModelPicker() {
        val preflight = refreshImportStoragePreflight()
        if (!preflight.canOpenPicker) {
            Toast.makeText(
                this,
                getString(
                    R.string.import_picker_blocked_insufficient_storage,
                    Formatter.formatFileSize(this, preflight.reservedFreeBytes),
                ),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_OPEN_MODEL)
    }

    private fun importModel(uri: Uri, grantedFlags: Int) {
        importCoordinator.beginImport(uri, grantedFlags, checkAfterImportOption.isChecked)
        renderManagerState(importCoordinator.managerState())
    }

    private fun renderImportState(state: ModelImportState<ImportedModel>) {
        updateModelActions(ModelManagerPresentation.visibleModel(state))
        when (state) {
            ModelImportState.Preparing -> {
                setImportUi(inProgress = true, importEnabled = false)
                progress.isIndeterminate = true
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
                setImportUi(
                    inProgress = true,
                    importEnabled = false,
                    cancelOperationId = state.operationId,
                )
                showImportProgress(state.progress)
            }
            is ModelImportState.Cancelling -> {
                setImportUi(inProgress = true, importEnabled = false)
                showImportProgress(state.progress)
                status.text = getString(R.string.import_cancelling)
            }
            is ModelImportState.Succeeded -> {
                setImportUi(inProgress = false, importEnabled = true)
                showModel(state.model)
                notifyImportOnce(state.operationId, R.string.import_succeeded, Toast.LENGTH_SHORT)
            }
            is ModelImportState.Cancelled -> {
                setImportUi(inProgress = false, importEnabled = true)
                state.current?.let(::showModel) ?: run { status.text = getString(R.string.model_none) }
                notifyImportOnce(state.operationId, R.string.import_cancelled, Toast.LENGTH_SHORT)
            }
            is ModelImportState.Failed -> {
                setImportUi(inProgress = false, importEnabled = state.retryAllowed)
                val message = importFailureMessage(state.reason)
                state.current?.let(::showModel) ?: run { status.text = getString(message) }
                notifyImportOnce(state.operationId, message, Toast.LENGTH_LONG)
            }
        }
    }

    private fun renderManagerState(state: ModelManagerState) {
        renderImportState(state.importState)
        renderManagerCatalog(state)
        when (val selection = state.selection) {
            is ModelSelectionState.Succeeded -> notifySelectionOnce(
                selection.operationId,
                R.string.model_selection_succeeded,
                Toast.LENGTH_SHORT,
            )
            is ModelSelectionState.Failed -> notifySelectionOnce(
                selection.operationId,
                R.string.model_selection_failed,
                Toast.LENGTH_LONG,
            )
            ModelSelectionState.Idle,
            is ModelSelectionState.Selecting,
            -> Unit
        }
        when (val deletion = state.deletion) {
            is ModelDeletionState.Succeeded -> notifyDeletionOnce(
                deletion.operationId,
                R.string.model_deletion_succeeded,
                Toast.LENGTH_SHORT,
            )
            is ModelDeletionState.Failed -> notifyDeletionOnce(
                deletion.operationId,
                R.string.model_deletion_failed,
                Toast.LENGTH_LONG,
            )
            ModelDeletionState.Idle,
            is ModelDeletionState.Deleting,
            -> Unit
        }
        when (val rename = state.rename) {
            is ModelRenameState.Succeeded -> notifyRenameOnce(
                rename.operationId,
                R.string.model_rename_succeeded,
                Toast.LENGTH_SHORT,
            )
            is ModelRenameState.Failed -> notifyRenameOnce(
                rename.operationId,
                R.string.model_rename_failed,
                Toast.LENGTH_LONG,
            )
            ModelRenameState.Idle,
            is ModelRenameState.Renaming,
            -> Unit
        }
        when (val cleanup = state.storageCleanup) {
            is ModelStorageCleanupState.Succeeded -> notifyStorageCleanupOnce(
                operationId = cleanup.operationId,
                message = if (cleanup.removedFileCount == 0) {
                    getString(R.string.model_cleanup_empty)
                } else {
                    getString(
                        R.string.model_cleanup_succeeded,
                        cleanup.removedFileCount,
                        Formatter.formatFileSize(this, cleanup.releasedBytes),
                    )
                },
                duration = Toast.LENGTH_SHORT,
            )
            is ModelStorageCleanupState.Failed -> notifyStorageCleanupOnce(
                operationId = cleanup.operationId,
                message = getString(R.string.model_cleanup_failed),
                duration = Toast.LENGTH_LONG,
            )
            ModelStorageCleanupState.Idle,
            is ModelStorageCleanupState.Cleaning,
            -> Unit
        }
        when (val healthCheck = state.healthCheck) {
            is ModelHealthCheckState.Succeeded -> notifyHealthCheckOnce(
                operationId = healthCheck.operationId,
                message = when (healthCheck.status) {
                    ModelHealthStatus.AVAILABLE -> R.string.model_health_check_available
                    ModelHealthStatus.INCOMPATIBLE -> R.string.model_health_check_incompatible
                    ModelHealthStatus.NOT_CHECKED -> error("A completed health check must be terminal")
                },
                duration = if (healthCheck.status == ModelHealthStatus.AVAILABLE) {
                    Toast.LENGTH_SHORT
                } else {
                    Toast.LENGTH_LONG
                },
            )
            is ModelHealthCheckState.Failed -> notifyHealthCheckOnce(
                operationId = healthCheck.operationId,
                message = R.string.model_health_check_failed,
                duration = Toast.LENGTH_LONG,
            )
            ModelHealthCheckState.Idle,
            is ModelHealthCheckState.Checking,
            -> Unit
        }
    }

    private fun renderManagerCatalog(state: ModelManagerState) {
        if (!::catalogRows.isInitialized) return
        val view = ModelManagerPresentation.managerView(state)
        catalogMutationBlocksPicker = view.catalogMutationBusy
        updateImportButtonEnabled()
        updateDownloadButtonEnabled()
        cleanupStorageButton.isEnabled = view.cleanupEnabled
        cleanupStorageButton.text = getString(
            if (view.cleanupInProgress) {
                R.string.model_cleanup_in_progress
            } else {
                R.string.button_cleanup_storage
            },
        )
        if (view == renderedManagerView) return
        renderedManagerView = view
        refreshImportStoragePreflight()
        catalogSummary.text = when (view.availability) {
            ModelCatalogAvailability.LOADING -> getString(R.string.model_catalog_loading)
            ModelCatalogAvailability.UNAVAILABLE -> getString(R.string.model_catalog_unavailable)
            ModelCatalogAvailability.READY -> if (view.rows.isEmpty()) {
                getString(R.string.model_catalog_empty)
            } else {
                getString(
                    R.string.model_catalog_summary,
                    view.rows.size,
                    Formatter.formatFileSize(this, view.totalSizeBytes),
                )
            }
        }
        catalogRows.removeAllViews()
        view.rows.forEach { row ->
            val catalogRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val selectionButton = RadioButton(this).apply {
                text = getString(
                    R.string.model_catalog_item,
                    row.displayName,
                    Formatter.formatFileSize(this@ModelManagerActivity, row.sizeBytes),
                    row.modelId,
                    getString(
                        if (row.healthCheckInProgress) {
                            R.string.model_health_checking
                        } else {
                            healthStatusText(row.healthStatus)
                        },
                    ),
                )
                isChecked = row.selected
                isEnabled = row.selectionEnabled
                tag = row.modelId
                setOnClickListener {
                    if (row.selected) return@setOnClickListener
                    renderedManagerView = null
                    importCoordinator.beginSelection(row.modelId)
                    renderManagerState(importCoordinator.managerState())
                }
            }
            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            val healthCheckButton = Button(this).apply {
                text = getString(
                    if (row.healthCheckInProgress) {
                        R.string.model_health_checking
                    } else {
                        R.string.button_check_model
                    },
                )
                isEnabled = row.healthCheckEnabled
                setOnClickListener { checkModel(row.modelId) }
            }
            val deleteButton = Button(this).apply {
                text = getString(R.string.button_delete_model)
                isEnabled = row.deletionEnabled
                setOnClickListener { confirmModelDeletion(row) }
            }
            val renameButton = Button(this).apply {
                text = getString(R.string.button_rename_model)
                isEnabled = row.renameEnabled
                setOnClickListener { confirmModelRename(row) }
            }
            catalogRow.addView(
                selectionButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            actionRow.addView(
                healthCheckButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            actionRow.addView(
                renameButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            actionRow.addView(
                deleteButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            catalogRow.addView(
                actionRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            catalogRows.addView(
                catalogRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        applyThemeToControls(catalogRows)
    }

    private fun checkModel(modelId: String) {
        val accepted = importCoordinator.beginHealthCheck(modelId)
        renderManagerState(importCoordinator.managerState())
        if (!accepted) {
            Toast.makeText(this, R.string.model_health_check_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmModelRename(row: ModelManagerRow) {
        val input = EditText(this).apply {
            setText(row.displayName)
            selectAll()
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(ModelDisplayNamePolicy.MAXIMUM_UTF8_BYTES))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.model_rename_title)
            .setMessage(R.string.model_rename_message)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_rename_model) { _, _ ->
                renameModel(row.modelId, input.text.toString())
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun renameModel(modelId: String, requestedName: String) {
        val displayName = runCatching {
            ModelDisplayNamePolicy.normalizeUserInput(requestedName)
        }.getOrElse {
            Toast.makeText(this, R.string.model_rename_invalid, Toast.LENGTH_LONG).show()
            return
        }
        val accepted = importCoordinator.beginRename(modelId, displayName)
        renderManagerState(importCoordinator.managerState())
        if (!accepted) {
            Toast.makeText(this, R.string.model_rename_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmModelDeletion(row: ModelManagerRow) {
        if (row.selected) {
            Toast.makeText(this, R.string.model_delete_selected_blocked, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.model_delete_confirm_title)
            .setMessage(getString(R.string.model_delete_confirm_message, row.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_delete_model) { _, _ -> deleteModel(row.modelId) }
            .show()
            .also(::tintDialogButtons)
    }

    private fun deleteModel(modelId: String) {
        val before = importCoordinator.managerState()
        if (before.snapshot?.selectedModelId == modelId) {
            renderManagerState(before)
            Toast.makeText(this, R.string.model_delete_selected_blocked, Toast.LENGTH_LONG).show()
            return
        }
        val accepted = importCoordinator.beginDeletion(modelId)
        val after = importCoordinator.managerState()
        renderManagerState(after)
        if (!accepted && after.snapshot?.selectedModelId == modelId) {
            Toast.makeText(this, R.string.model_delete_selected_blocked, Toast.LENGTH_LONG).show()
        }
    }

    private fun cleanUnreferencedStorage() {
        importCoordinator.beginStorageCleanup()
        renderManagerState(importCoordinator.managerState())
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
            model.modelId,
            getString(healthStatusText(model.healthStatus)),
        )
    }

    private fun healthStatusText(status: ModelHealthStatus): Int = when (status) {
        ModelHealthStatus.NOT_CHECKED -> R.string.model_health_not_checked
        ModelHealthStatus.AVAILABLE -> R.string.model_health_available
        ModelHealthStatus.INCOMPATIBLE -> R.string.model_health_incompatible
    }

    private fun updateModelActions(model: ImportedModel?) {
        copyableModelId = model?.modelId
        copyModelIdButton.visibility = if (model == null) View.GONE else View.VISIBLE
    }

    private fun copyModelId() {
        val modelId = copyableModelId ?: return
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(getString(R.string.model_id_clipboard_label), modelId),
        )
        Toast.makeText(this, R.string.model_id_copied, Toast.LENGTH_SHORT).show()
    }

    private fun cancelImport() {
        val operationId = cancellableOperationId ?: return
        cancelImportButton.isEnabled = false
        if (importCoordinator.cancelImport(operationId)) return
        val latest = importCoordinator.managerState()
        renderManagerState(latest)
        val importState = latest.importState
        if (importState is ModelImportState.Running && importState.operationId == operationId) {
            cancellableOperationId = null
            cancelImportButton.isEnabled = false
        }
    }

    private fun showImportProgress(value: ModelImportProgress) {
        val stage = getString(
            when (value.stage) {
                ModelImportStage.VALIDATING -> R.string.import_stage_validating
                ModelImportStage.COPYING -> R.string.import_stage_copying
                ModelImportStage.PUBLISHING -> R.string.import_stage_publishing
                ModelImportStage.CHECKING_COMPATIBILITY -> R.string.import_stage_checking_compatibility
            },
        )
        val totalBytes = value.totalBytes?.takeIf { value.processedBytes <= it }
        if (totalBytes == null) {
            progress.isIndeterminate = true
            status.text = getString(
                R.string.import_progress_unknown,
                stage,
                Formatter.formatFileSize(this, value.processedBytes),
            )
            return
        }
        progress.isIndeterminate = false
        progress.progress = if (value.processedBytes >= totalBytes) {
            PROGRESS_MAX
        } else {
            (value.processedBytes * PROGRESS_MAX / totalBytes).toInt()
        }
        status.text = getString(
            R.string.import_progress_known,
            stage,
            Formatter.formatFileSize(this, value.processedBytes),
            Formatter.formatFileSize(this, totalBytes),
            progress.progress / (PROGRESS_MAX / 100),
        )
    }

    private fun setImportUi(
        inProgress: Boolean,
        importEnabled: Boolean,
        cancelOperationId: Long? = null,
    ) {
        cancellableOperationId = cancelOperationId
        progress.visibility = if (inProgress) View.VISIBLE else View.GONE
        cancelImportButton.visibility = if (cancelOperationId == null) View.GONE else View.VISIBLE
        cancelImportButton.isEnabled = cancelOperationId != null
        checkAfterImportOption.isEnabled = !inProgress
        importStateAllowsPicker = importEnabled
        updateImportButtonEnabled()
        updateDownloadButtonEnabled()
    }

    @SuppressLint("UsableSpace") // The budget is deliberately conservative and excludes reclaimable caches.
    private fun refreshImportStoragePreflight(): ModelImportStoragePreflight {
        val usableBytes = runCatching { filesDir.usableSpace }.getOrDefault(0L)
        val preflight = ModelImportPolicy.storagePreflight(usableBytes)
        storageAllowsPicker = preflight.canOpenPicker
        importStoragePreflight.text = if (preflight.canOpenPicker) {
            getString(
                R.string.import_storage_preflight_ready,
                Formatter.formatFileSize(this, preflight.usableBytes),
                Formatter.formatFileSize(this, preflight.maximumAdditionalModelBytes),
                Formatter.formatFileSize(this, preflight.reservedFreeBytes),
            )
        } else {
            getString(
                R.string.import_storage_preflight_unavailable,
                Formatter.formatFileSize(this, preflight.usableBytes),
                Formatter.formatFileSize(this, preflight.reservedFreeBytes),
            )
        }
        updateImportButtonEnabled()
        return preflight
    }

    private fun updateImportButtonEnabled() {
        val enabled = importStateAllowsPicker &&
            !catalogMutationBlocksPicker && storageAllowsPicker && !downloadBlocksImport
        importButton.isEnabled = enabled
        if (::importDownloadedButton.isInitialized) {
            importDownloadedButton.isEnabled = enabled && downloadedDestination != null
        }
    }

    private fun updateDownloadButtonEnabled() {
        if (!::downloadButton.isInitialized) return
        downloadButton.isEnabled = downloadStateAllowsStart &&
            importStateAllowsPicker && !catalogMutationBlocksPicker && pendingDownloadModelId == null
    }

    private fun importFailureMessage(reason: ModelImportFailureReason): Int = when (reason) {
        ModelImportFailureReason.INVALID_FORMAT -> R.string.import_failed_invalid_format
        ModelImportFailureReason.MODEL_TOO_LARGE -> R.string.import_failed_model_too_large
        ModelImportFailureReason.INSUFFICIENT_STORAGE -> R.string.import_failed_insufficient_storage
        ModelImportFailureReason.SOURCE_UNAVAILABLE -> R.string.import_failed_source_unavailable
        ModelImportFailureReason.INTERRUPTED -> R.string.import_failed_interrupted
        ModelImportFailureReason.UNKNOWN -> R.string.import_failed
    }

    private fun notifyImportOnce(operationId: Long, message: Int, duration: Int) {
        if (operationId <= lastNotifiedImportOperationId) return
        lastNotifiedImportOperationId = operationId
        Toast.makeText(this, message, duration).show()
    }

    private fun notifySelectionOnce(operationId: Long, message: Int, duration: Int) {
        if (operationId <= lastNotifiedSelectionOperationId) return
        lastNotifiedSelectionOperationId = operationId
        Toast.makeText(this, message, duration).show()
    }

    private fun notifyDownloadOnce(operationId: Long, message: CharSequence, duration: Int) {
        if (operationId <= lastNotifiedDownloadOperationId) return
        lastNotifiedDownloadOperationId = operationId
        Toast.makeText(this, message, duration).show()
    }

    private fun notifyDeletionOnce(operationId: Long, message: Int, duration: Int) {
        if (operationId <= lastNotifiedDeletionOperationId) return
        lastNotifiedDeletionOperationId = operationId
        Toast.makeText(this, message, duration).show()
    }

    private fun notifyRenameOnce(operationId: Long, message: Int, duration: Int) {
        if (operationId <= lastNotifiedRenameOperationId) return
        lastNotifiedRenameOperationId = operationId
        Toast.makeText(this, message, duration).show()
    }

    private fun notifyStorageCleanupOnce(operationId: Long, message: CharSequence, duration: Int) {
        if (operationId <= lastNotifiedStorageCleanupOperationId) return
        lastNotifiedStorageCleanupOperationId = operationId
        Toast.makeText(this, message, duration).show()
    }

    private fun notifyHealthCheckOnce(operationId: Long, message: Int, duration: Int) {
        if (operationId <= lastNotifiedHealthCheckOperationId) return
        lastNotifiedHealthCheckOperationId = operationId
        Toast.makeText(this, message, duration).show()
    }

    private companion object {
        const val REQUEST_OPEN_MODEL = 1001
        const val REQUEST_CREATE_MODEL_DOWNLOAD = 1002
        const val STATE_LAST_NOTIFIED_IMPORT_OPERATION_ID = "lastNotifiedImportOperationId"
        const val STATE_LAST_NOTIFIED_DOWNLOAD_OPERATION_ID = "lastNotifiedDownloadOperationId"
        const val STATE_LAST_NOTIFIED_SELECTION_OPERATION_ID = "lastNotifiedSelectionOperationId"
        const val STATE_LAST_NOTIFIED_DELETION_OPERATION_ID = "lastNotifiedDeletionOperationId"
        const val STATE_LAST_NOTIFIED_RENAME_OPERATION_ID = "lastNotifiedRenameOperationId"
        const val STATE_LAST_NOTIFIED_STORAGE_CLEANUP_OPERATION_ID =
            "lastNotifiedStorageCleanupOperationId"
        const val STATE_LAST_NOTIFIED_HEALTH_CHECK_OPERATION_ID =
            "lastNotifiedHealthCheckOperationId"
        const val STATE_CHECK_AFTER_IMPORT = "checkAfterImport"
        const val STATE_PENDING_DOWNLOAD_MODEL_ID = "pendingDownloadModelId"
        const val PREFERENCES_NAME = "model-manager"
        const val PREFERENCE_CHECK_AFTER_IMPORT = "checkAfterImport"
        const val STATE_PROCESS_SESSION_TOKEN = "processSessionToken"
        const val STATE_DOWNLOAD_PROCESS_SESSION_TOKEN = "downloadProcessSessionToken"
        const val PROGRESS_MAX = 10_000
    }
}
