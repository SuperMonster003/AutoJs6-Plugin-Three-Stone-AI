package io.github.supermonster003.autojs6.plugin.threestoneai

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLmBackendCompatibilityDetector
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
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportProgress
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportStage
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportStoragePreflight
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelManagerState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRenameState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelStorageCleanupState
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.BadgeTone
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.CardBadge
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.ProgressPanel
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.SettingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.confirmDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.filledButton
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.inputDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.insertRowHairlines
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.itemCard
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.sectionHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.setRowEnabled
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar

/**
 * The models and services page (roadmap 1.7.0 P5.2, D95, D104, D115). [OnlineServicesActivity] draws
 * the frame, the default target row and the online services; this class adds the local models. The
 * class keeps its name because the host settings action and the chat page open it (D110, D112).
 */
class ModelManagerActivity : OnlineServicesActivity() {
    private val localRuntimeAvailable by lazy {
        LiteRtLmBackendCompatibilityDetector().isRuntimeAvailable
    }
    private lateinit var importCoordinator: ModelImportCoordinator
    private lateinit var downloadCoordinator: ModelDownloadCoordinator
    private lateinit var downloadStatus: TextView
    private lateinit var downloadRow: SettingRow
    private lateinit var importDownloadedButton: MaterialButton
    private lateinit var downloadPanel: ProgressPanel
    private lateinit var status: TextView
    private lateinit var importRow: SettingRow
    private lateinit var importPanel: ProgressPanel
    private lateinit var catalogSummary: TextView
    private lateinit var catalogRows: LinearLayout
    private var cleanupEnabled = false
    private var cancellableOperationId: Long? = null
    private var cancellableDownloadOperationId: Long? = null
    private var downloadedDestination: ModelDownloadState.Succeeded<Uri>? = null
    private var lastNotifiedImportOperationId = 0L
    private var lastNotifiedDownloadOperationId = 0L
    private var lastNotifiedDeletionOperationId = 0L
    private var lastNotifiedRenameOperationId = 0L
    private var lastNotifiedStorageCleanupOperationId = 0L
    private var lastNotifiedHealthCheckOperationId = 0L
    private val managerObserver = ModelImportCoordinator.ManagerObserver(::renderManagerState)
    private val downloadObserver = ModelDownloadCoordinator.Observer(::renderDownloadState)
    private var renderedManagerView: ModelManagerViewState? = null
    /** The model rows the target catalog was last loaded for; a change reloads the catalog. */
    private var catalogRowsForTargets: List<ModelManagerRow>? = null
    private var importStateAllowsPicker = false
    private var catalogMutationBlocksPicker = false
    private var storageAllowsPicker = false
    private var downloadStateAllowsStart = true
    private var downloadBlocksImport = false

    override fun buildLocalSection(content: LinearLayout, savedInstanceState: Bundle?) {
        importCoordinator = ModelImportCoordinator.get(applicationContext)
        downloadCoordinator = ModelDownloadCoordinator.get(applicationContext)
        restoreNotifiedOperations(savedInstanceState)

        content.addView(sectionHeader(R.string.models_local_section, ::showLocalSectionMenu))
        if (!localRuntimeAvailable) {
            content.addView(paragraph(getString(R.string.model_local_runtime_unavailable)))
        }
        catalogSummary = paragraph()
        content.addView(catalogSummary)
        catalogRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(catalogRows)

        downloadPanel = ProgressPanel(this)
            .withCancelAction(R.string.button_cancel_download) { cancelDownload() }
        content.addView(downloadPanel.view, blockParams(topDp = Ui.SPACE_SM))
        downloadStatus = paragraph().apply {
            setTextIsSelectable(true)
            visibility = View.GONE
        }
        content.addView(downloadStatus, blockParams(topDp = Ui.SPACE_SM, horizontalDp = 0))
        importDownloadedButton = filledButton(R.string.button_import_downloaded_model) {
            importDownloadedModel()
        }.apply { visibility = View.GONE }
        content.addView(importDownloadedButton, blockParams(bottomDp = Ui.SPACE_SM).apply {
            width = LinearLayout.LayoutParams.WRAP_CONTENT
        })
        importPanel = ProgressPanel(this)
            .withCancelAction(R.string.button_cancel_import) { cancelImport() }
        content.addView(importPanel.view, blockParams(topDp = Ui.SPACE_SM))
        status = paragraph().apply {
            setTextIsSelectable(true)
            visibility = View.GONE
        }
        content.addView(status, blockParams(topDp = Ui.SPACE_SM, horizontalDp = 0))

        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        downloadRow = settingRow(
            title = getString(R.string.button_browse_litert_models),
            summary = getString(R.string.download_description),
            iconResource = R.drawable.ic_download_24,
            onClick = ::chooseRecommendedModel,
        )
        importRow = settingRow(
            title = getString(R.string.button_import_model),
            summary = getString(R.string.models_import_file_summary),
            iconResource = R.drawable.ic_upload_24,
            onClick = ::openModelPicker,
        )
        rows.addView(downloadRow.view)
        rows.addView(importRow.view)
        insertRowHairlines(rows)
        content.addView(rows)
    }

    private fun restoreNotifiedOperations(savedInstanceState: Bundle?) {
        val restoredManagerNotifications = savedInstanceState?.takeIf { restored ->
            restored.getString(STATE_PROCESS_SESSION_TOKEN) == importCoordinator.processSessionToken
        }
        restoredManagerNotifications?.let { restored ->
            lastNotifiedImportOperationId = restored.getLong(STATE_LAST_NOTIFIED_IMPORT_OPERATION_ID)
            lastNotifiedDeletionOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_DELETION_OPERATION_ID)
            lastNotifiedRenameOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_RENAME_OPERATION_ID)
            lastNotifiedStorageCleanupOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_STORAGE_CLEANUP_OPERATION_ID)
            lastNotifiedHealthCheckOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_HEALTH_CHECK_OPERATION_ID)
        }
        if (restoredManagerNotifications == null) {
            lastNotifiedImportOperationId = when (
                val state = importCoordinator.managerState().importState
            ) {
                is ModelImportState.Succeeded -> state.operationId
                is ModelImportState.Cancelled -> state.operationId
                is ModelImportState.Failed -> state.operationId
                ModelImportState.Preparing,
                ModelImportState.Unavailable,
                is ModelImportState.Ready,
                is ModelImportState.Running,
                is ModelImportState.Cancelling,
                -> 0L
            }
        }
        savedInstanceState?.takeIf { restored ->
            restored.getString(STATE_DOWNLOAD_PROCESS_SESSION_TOKEN) ==
                downloadCoordinator.processSessionToken
        }?.let { restored ->
            lastNotifiedDownloadOperationId =
                restored.getLong(STATE_LAST_NOTIFIED_DOWNLOAD_OPERATION_ID)
        }
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
            REQUEST_CHOOSE_LITERT_MODEL -> if (resultCode == RESULT_OK) {
                val model = data?.getStringExtra(LiteRtModelCatalogActivity.EXTRA_MODEL_ID)
                    ?.let(RecommendedModelCatalog::find)
                if (model != null) {
                    data?.data?.let { uri -> downloadModel(model, uri, data.flags) }
                }
            }
        }
    }

    override fun onTargetsChanged() {
        renderedManagerView?.let(::renderModelCards)
    }

    private fun showLocalSectionMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_IMPORT_FILE, 0, R.string.button_import_model).isEnabled = importRow.view.isEnabled
            menu.add(0, MENU_CLEANUP_STORAGE, 1, R.string.button_cleanup_storage).isEnabled = cleanupEnabled
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_IMPORT_FILE -> openModelPicker()
                    MENU_CLEANUP_STORAGE -> cleanUnreferencedStorage()
                }
                true
            }
            show()
        }
    }

    private fun paragraph(text: CharSequence? = null): TextView = TextView(this).apply {
        this.text = text
        textSize = Ui.TEXT_SECONDARY
        setTextColor(appPalette.secondaryText)
        setLineSpacing(0f, Ui.LINE_SPACING_BODY)
        setPaddingRelative(uiDp(Ui.SCREEN_MARGIN), 0, uiDp(Ui.SCREEN_MARGIN), uiDp(Ui.SPACE_XS))
    }

    private fun blockParams(
        topDp: Int = 0,
        bottomDp: Int = 0,
        horizontalDp: Int = Ui.SCREEN_MARGIN,
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        marginStart = uiDp(horizontalDp)
        marginEnd = uiDp(horizontalDp)
        topMargin = uiDp(topDp)
        bottomMargin = uiDp(bottomDp)
    }

    private fun chooseRecommendedModel() {
        if (!localRuntimeAvailable) return
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, LiteRtModelCatalogActivity::class.java),
            REQUEST_CHOOSE_LITERT_MODEL,
        )
    }

    private fun downloadModel(model: RecommendedModel, destination: Uri, grantedFlags: Int) {
        if (!localRuntimeAvailable) return
        val accepted = downloadCoordinator.beginDownload(model, destination, grantedFlags)
        renderDownloadState(downloadCoordinator.state())
        if (!accepted) {
            showSnackbar(screenRoot, getString(R.string.download_already_running), Snackbar.LENGTH_LONG)
        }
    }

    private fun cancelDownload() {
        val operationId = cancellableDownloadOperationId ?: return
        downloadPanel.setCancelEnabled(false)
        if (downloadCoordinator.cancelDownload(operationId)) return
        renderDownloadState(downloadCoordinator.state())
    }

    private fun importDownloadedModel() {
        val completed = downloadedDestination ?: return
        val preflight = refreshImportStoragePreflight()
        if (completed.model.expectedSizeBytes > preflight.maximumAdditionalModelBytes) {
            showSnackbar(
                screenRoot,
                getString(
                    R.string.download_import_insufficient_storage,
                    Formatter.formatFileSize(this, completed.model.expectedSizeBytes),
                    Formatter.formatFileSize(this, preflight.maximumAdditionalModelBytes),
                ),
                Snackbar.LENGTH_LONG,
            )
            return
        }
        val accepted = importCoordinator.beginImport(completed.destination, completed.grantedFlags)
        renderManagerState(importCoordinator.managerState())
        if (!accepted) {
            showSnackbar(screenRoot, getString(R.string.download_import_unavailable), Snackbar.LENGTH_LONG)
        }
    }

    private fun renderDownloadState(state: ModelDownloadState<Uri>) {
        when (state) {
            ModelDownloadState.Idle -> {
                setDownloadUi(inProgress = false, startEnabled = true)
                // The download row already explains downloads; the status line only reports results.
                downloadStatus.text = null
                downloadStatus.visibility = View.GONE
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
                showDownloadProgress(
                    state.model,
                    state.progress,
                    labelOverride = getString(R.string.download_cancelling, state.model.displayName),
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
                    Snackbar.LENGTH_SHORT,
                )
            }
            is ModelDownloadState.Cancelled -> {
                setDownloadUi(inProgress = false, startEnabled = true)
                val message = terminalDownloadMessage(
                    getString(R.string.download_cancelled),
                    state.cleanup,
                )
                downloadStatus.text = message
                notifyDownloadOnce(state.operationId, message, Snackbar.LENGTH_LONG)
            }
            is ModelDownloadState.Failed -> {
                setDownloadUi(inProgress = false, startEnabled = true)
                val message = terminalDownloadMessage(
                    getString(downloadFailureMessage(state.reason)),
                    state.cleanup,
                )
                downloadStatus.text = message
                notifyDownloadOnce(state.operationId, message, Snackbar.LENGTH_LONG)
            }
        }
    }

    private fun showDownloadProgress(
        model: RecommendedModel,
        value: ModelDownloadProgress,
        labelOverride: CharSequence? = null,
    ) {
        if (value.processedBytes == 0L) {
            downloadPanel.showIndeterminate(
                labelOverride ?: getString(R.string.download_connecting, model.displayName),
            )
            return
        }
        val progress = if (value.processedBytes >= value.totalBytes) {
            PROGRESS_MAX
        } else {
            (value.processedBytes * PROGRESS_MAX / value.totalBytes).toInt()
        }
        downloadPanel.showProgress(
            labelOverride ?: model.displayName,
            progress,
            transferMeta(value.processedBytes, value.totalBytes, progress / (PROGRESS_MAX / 100)),
        )
    }

    private fun transferMeta(processedBytes: Long, totalBytes: Long, percent: Int): String =
        "${Formatter.formatFileSize(this, processedBytes)} / " +
            "${Formatter.formatFileSize(this, totalBytes)} ($percent%)"

    private fun setDownloadUi(
        inProgress: Boolean,
        startEnabled: Boolean,
        cancelOperationId: Long? = null,
        completed: ModelDownloadState.Succeeded<Uri>? = null,
    ) {
        cancellableDownloadOperationId = cancelOperationId
        downloadedDestination = completed
        if (!inProgress) downloadPanel.hide()
        downloadPanel.setCancelEnabled(cancelOperationId != null)
        downloadStatus.visibility = if (inProgress) View.GONE else View.VISIBLE
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
        if (!localRuntimeAvailable) return
        val preflight = refreshImportStoragePreflight()
        if (!preflight.canOpenPicker) {
            showSnackbar(
                screenRoot,
                getString(
                    R.string.import_picker_blocked_insufficient_storage,
                    Formatter.formatFileSize(this, preflight.reservedFreeBytes),
                ),
                Snackbar.LENGTH_LONG,
            )
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
        if (!localRuntimeAvailable) return
        importCoordinator.beginImport(uri, grantedFlags)
        renderManagerState(importCoordinator.managerState())
    }

    private fun renderImportState(state: ModelImportState<ImportedModel>) {
        when (state) {
            ModelImportState.Preparing -> {
                setImportUi(inProgress = true, importEnabled = false)
                importPanel.showIndeterminate(getString(R.string.import_in_progress))
            }
            ModelImportState.Unavailable -> {
                setImportUi(inProgress = false, importEnabled = false)
                showImportMessage(getString(R.string.import_failed))
            }
            is ModelImportState.Ready -> {
                setImportUi(inProgress = false, importEnabled = true)
                showImportMessage(null)
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
                showImportProgress(
                    state.progress,
                    labelOverride = getString(R.string.import_cancelling),
                )
            }
            is ModelImportState.Succeeded -> {
                setImportUi(inProgress = false, importEnabled = true)
                showImportMessage(null)
                notifyImportOnce(state.operationId, R.string.import_succeeded, Snackbar.LENGTH_SHORT)
            }
            is ModelImportState.Cancelled -> {
                setImportUi(inProgress = false, importEnabled = true)
                showImportMessage(null)
                notifyImportOnce(state.operationId, R.string.import_cancelled, Snackbar.LENGTH_SHORT)
            }
            is ModelImportState.Failed -> {
                setImportUi(inProgress = false, importEnabled = state.retryAllowed)
                val message = importFailureMessage(state.reason)
                showImportMessage(getString(message))
                notifyImportOnce(state.operationId, message, Snackbar.LENGTH_LONG)
            }
        }
    }

    /** The import status line only reports failures; the new model shows up as a card. */
    private fun showImportMessage(message: CharSequence?) {
        status.text = message
        status.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderManagerState(state: ModelManagerState) {
        renderImportState(state.importState)
        renderManagerCatalog(state)
        when (val deletion = state.deletion) {
            is ModelDeletionState.Succeeded -> notifyDeletionOnce(
                deletion.operationId,
                R.string.model_deletion_succeeded,
                Snackbar.LENGTH_SHORT,
            )
            is ModelDeletionState.Failed -> notifyDeletionOnce(
                deletion.operationId,
                R.string.model_deletion_failed,
                Snackbar.LENGTH_LONG,
            )
            ModelDeletionState.Idle,
            is ModelDeletionState.Deleting,
            -> Unit
        }
        when (val rename = state.rename) {
            is ModelRenameState.Succeeded -> notifyRenameOnce(
                rename.operationId,
                R.string.model_rename_succeeded,
                Snackbar.LENGTH_SHORT,
            )
            is ModelRenameState.Failed -> notifyRenameOnce(
                rename.operationId,
                R.string.model_rename_failed,
                Snackbar.LENGTH_LONG,
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
                duration = Snackbar.LENGTH_SHORT,
            )
            is ModelStorageCleanupState.Failed -> notifyStorageCleanupOnce(
                operationId = cleanup.operationId,
                message = getString(R.string.model_cleanup_failed),
                duration = Snackbar.LENGTH_LONG,
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
                    Snackbar.LENGTH_SHORT
                } else {
                    Snackbar.LENGTH_LONG
                },
            )
            is ModelHealthCheckState.Failed -> notifyHealthCheckOnce(
                operationId = healthCheck.operationId,
                message = R.string.model_health_check_failed,
                duration = Snackbar.LENGTH_LONG,
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
        cleanupEnabled = view.cleanupEnabled
        updateImportButtonEnabled()
        updateDownloadButtonEnabled()
        val storage = refreshImportStoragePreflight()
        catalogSummary.text = when (view.availability) {
            ModelCatalogAvailability.LOADING -> getString(R.string.model_catalog_loading)
            ModelCatalogAvailability.UNAVAILABLE -> getString(R.string.model_catalog_unavailable)
            ModelCatalogAvailability.READY -> getString(
                R.string.model_catalog_summary_with_available,
                view.rows.size,
                Formatter.formatFileSize(this, view.totalSizeBytes),
                Formatter.formatFileSize(this, storage.usableBytes),
            )
        }
        if (view == renderedManagerView) return
        renderedManagerView = view
        if (view.rows != catalogRowsForTargets) {
            // Imports, deletions, renames and health checks change the local targets and can move the
            // default target; refreshTargets redraws the cards through onTargetsChanged.
            catalogRowsForTargets = view.rows
            refreshTargets()
        } else {
            renderModelCards(view)
        }
    }

    private fun renderModelCards(view: ModelManagerViewState) {
        catalogRows.removeAllViews()
        view.rows.forEach { row -> catalogRows.addView(modelCard(row)) }
    }

    private fun modelCard(row: ModelManagerRow): View {
        val targetId = AiTargetIds.local(row.modelId)
        val healthText = getString(
            if (row.healthCheckInProgress) {
                R.string.model_health_checking
            } else {
                healthStatusText(row.healthStatus)
            },
        )
        val badges = buildList {
            if (isDefaultTarget(targetId)) {
                add(CardBadge(getString(R.string.models_badge_default), BadgeTone.ACCENT))
            }
            add(
                if (isLocalModelUsable(row)) {
                    CardBadge(getString(R.string.models_badge_ready), BadgeTone.NEUTRAL)
                } else {
                    CardBadge(getString(R.string.models_badge_unavailable), BadgeTone.WARNING)
                },
            )
        }
        return itemCard(
            title = row.displayName,
            subtitle = "${Formatter.formatFileSize(this, row.sizeBytes)} | $healthText",
            badges = badges,
            actionsDescription = R.string.model_item_actions,
            onActions = { anchor -> showModelActionsMenu(anchor, row) },
            onClick = { showModelDetails(row) },
        )
    }

    /** The catalog's verdict once it is loaded, otherwise the runtime and the last health check. */
    private fun isLocalModelUsable(row: ModelManagerRow): Boolean =
        targetFor(AiTargetIds.local(row.modelId))?.let { TargetPickerTexts.isUsable(it) }
            ?: (localRuntimeAvailable && row.healthStatus != ModelHealthStatus.INCOMPATIBLE)

    /** The same fixed order as the service cards (D98), without the online-only entries. */
    private fun showModelActionsMenu(anchor: View, row: ModelManagerRow) {
        val targetId = AiTargetIds.local(row.modelId)
        PopupMenu(this, anchor).apply {
            menu.add(0, ACTION_SET_DEFAULT, 0, R.string.online_ai_action_set_default).isEnabled =
                targetFor(targetId)?.let { TargetPickerTexts.isUsable(it) } == true && !isDefaultTarget(targetId)
            menu.add(0, ACTION_CHECK, 1, R.string.button_check_model).isEnabled =
                localRuntimeAvailable && row.healthCheckEnabled && !row.healthCheckInProgress
            menu.add(0, ACTION_RENAME, 2, R.string.button_rename_model).isEnabled = row.renameEnabled
            menu.add(0, ACTION_DELETE, 3, R.string.button_delete_model).isEnabled = row.deletionEnabled
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    ACTION_SET_DEFAULT -> makeDefault(targetId)
                    ACTION_CHECK -> checkModel(row.modelId)
                    ACTION_RENAME -> confirmModelRename(row)
                    ACTION_DELETE -> confirmModelDeletion(row)
                }
                true
            }
            show()
        }
    }

    private fun checkModel(modelId: String) {
        if (!localRuntimeAvailable) return
        val accepted = importCoordinator.beginHealthCheck(modelId)
        renderManagerState(importCoordinator.managerState())
        if (!accepted) {
            showSnackbar(screenRoot, getString(R.string.model_health_check_failed), Snackbar.LENGTH_LONG)
        }
    }

    private fun confirmModelRename(row: ModelManagerRow) {
        inputDialog(
            title = getString(R.string.model_rename_title),
            initialValue = row.displayName,
            message = getString(R.string.model_rename_message),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            maxLength = ModelDisplayNamePolicy.MAXIMUM_UTF8_BYTES,
            positiveResource = R.string.button_rename_model,
            validate = { value ->
                runCatching { ModelDisplayNamePolicy.normalizeUserInput(value) }
                    .fold(onSuccess = { null }, onFailure = { getString(R.string.model_rename_invalid) })
            },
        ) { value -> renameModel(row.modelId, value) }
    }

    private fun renameModel(modelId: String, requestedName: String) {
        val displayName = runCatching {
            ModelDisplayNamePolicy.normalizeUserInput(requestedName)
        }.getOrElse {
            showSnackbar(screenRoot, getString(R.string.model_rename_invalid), Snackbar.LENGTH_LONG)
            return
        }
        val accepted = importCoordinator.beginRename(modelId, displayName)
        renderManagerState(importCoordinator.managerState())
        if (!accepted) {
            showSnackbar(screenRoot, getString(R.string.model_rename_failed), Snackbar.LENGTH_LONG)
        }
    }

    private fun confirmModelDeletion(row: ModelManagerRow) {
        confirmDialog(
            title = getString(R.string.model_delete_confirm_title),
            message = getString(R.string.model_delete_confirm_message, row.displayName),
            positiveResource = R.string.button_delete_model,
            destructive = true,
        ) { deleteModel(row.modelId) }
    }

    private fun deleteModel(modelId: String) {
        val accepted = importCoordinator.beginDeletion(modelId)
        val after = importCoordinator.managerState()
        renderManagerState(after)
        if (!accepted) {
            showSnackbar(screenRoot, getString(R.string.model_deletion_failed), Snackbar.LENGTH_LONG)
        }
    }

    private fun cleanUnreferencedStorage() {
        importCoordinator.beginStorageCleanup()
        renderManagerState(importCoordinator.managerState())
    }

    /** Local model details (D104): what the model store keeps, with the model ID ready to copy. */
    private fun showModelDetails(row: ModelManagerRow) {
        val model = importCoordinator.managerState().snapshot?.models
            ?.firstOrNull { it.modelId == row.modelId } ?: return
        val imported = DateUtils.formatDateTime(
            this,
            model.importedAtMillis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME,
        )
        val details = getString(
            R.string.model_status_format,
            model.displayName,
            Formatter.formatFileSize(this, model.sizeBytes),
            model.sha256,
            model.modelId,
            getString(healthStatusText(model.healthStatus)),
        ) + "\n" + getString(R.string.models_detail_imported, imported)
        materialDialog()
            .setTitle(model.displayName)
            .setMessage(details)
            .setNeutralButton(R.string.button_copy_model_id) { _, _ -> copyModelId(model.modelId) }
            .setPositiveButton(android.R.string.ok, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun healthStatusText(status: ModelHealthStatus): Int = when (status) {
        ModelHealthStatus.NOT_CHECKED -> R.string.model_health_not_checked
        ModelHealthStatus.AVAILABLE -> R.string.model_health_available
        ModelHealthStatus.INCOMPATIBLE -> R.string.model_health_incompatible
    }

    private fun copyModelId(modelId: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(getString(R.string.model_id_clipboard_label), modelId),
        )
        showSnackbar(screenRoot, getString(R.string.model_id_copied))
    }

    private fun cancelImport() {
        val operationId = cancellableOperationId ?: return
        importPanel.setCancelEnabled(false)
        if (importCoordinator.cancelImport(operationId)) return
        val latest = importCoordinator.managerState()
        renderManagerState(latest)
        val importState = latest.importState
        if (importState is ModelImportState.Running && importState.operationId == operationId) {
            cancellableOperationId = null
            importPanel.setCancelEnabled(false)
        }
    }

    private fun showImportProgress(
        value: ModelImportProgress,
        labelOverride: CharSequence? = null,
    ) {
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
            importPanel.showIndeterminate(
                labelOverride ?: stage,
                Formatter.formatFileSize(this, value.processedBytes),
            )
            return
        }
        val progress = if (value.processedBytes >= totalBytes) {
            PROGRESS_MAX
        } else {
            (value.processedBytes * PROGRESS_MAX / totalBytes).toInt()
        }
        importPanel.showProgress(
            labelOverride ?: stage,
            progress,
            transferMeta(value.processedBytes, totalBytes, progress / (PROGRESS_MAX / 100)),
        )
    }

    private fun setImportUi(
        inProgress: Boolean,
        importEnabled: Boolean,
        cancelOperationId: Long? = null,
    ) {
        cancellableOperationId = cancelOperationId
        if (!inProgress) importPanel.hide()
        importPanel.setCancelEnabled(cancelOperationId != null)
        if (inProgress) status.visibility = View.GONE
        importStateAllowsPicker = importEnabled
        updateImportButtonEnabled()
        updateDownloadButtonEnabled()
    }

    @SuppressLint("UsableSpace") // The budget is deliberately conservative and excludes reclaimable caches.
    private fun refreshImportStoragePreflight(): ModelImportStoragePreflight {
        val usableBytes = runCatching { filesDir.usableSpace }.getOrDefault(0L)
        val preflight = ModelImportPolicy.storagePreflight(usableBytes)
        storageAllowsPicker = preflight.canOpenPicker
        updateImportButtonEnabled()
        return preflight
    }

    private fun updateImportButtonEnabled() {
        if (!::importRow.isInitialized) return
        val enabled = localRuntimeAvailable && importStateAllowsPicker &&
            !catalogMutationBlocksPicker && storageAllowsPicker && !downloadBlocksImport
        importRow.setRowEnabled(enabled)
        importDownloadedButton.isEnabled = enabled && downloadedDestination != null
    }

    private fun updateDownloadButtonEnabled() {
        if (!::downloadRow.isInitialized) return
        downloadRow.setRowEnabled(
            localRuntimeAvailable && downloadStateAllowsStart &&
                importStateAllowsPicker && !catalogMutationBlocksPicker,
        )
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
        showSnackbar(screenRoot, getString(message), duration)
    }

    private fun notifyDownloadOnce(operationId: Long, message: CharSequence, duration: Int) {
        if (operationId <= lastNotifiedDownloadOperationId) return
        lastNotifiedDownloadOperationId = operationId
        showSnackbar(screenRoot, message, duration)
    }

    private fun notifyDeletionOnce(operationId: Long, message: Int, duration: Int) {
        if (operationId <= lastNotifiedDeletionOperationId) return
        lastNotifiedDeletionOperationId = operationId
        showSnackbar(screenRoot, getString(message), duration)
    }

    private fun notifyRenameOnce(operationId: Long, message: Int, duration: Int) {
        if (operationId <= lastNotifiedRenameOperationId) return
        lastNotifiedRenameOperationId = operationId
        showSnackbar(screenRoot, getString(message), duration)
    }

    private fun notifyStorageCleanupOnce(operationId: Long, message: CharSequence, duration: Int) {
        if (operationId <= lastNotifiedStorageCleanupOperationId) return
        lastNotifiedStorageCleanupOperationId = operationId
        showSnackbar(screenRoot, message, duration)
    }

    private fun notifyHealthCheckOnce(operationId: Long, message: Int, duration: Int) {
        if (operationId <= lastNotifiedHealthCheckOperationId) return
        lastNotifiedHealthCheckOperationId = operationId
        showSnackbar(screenRoot, getString(message), duration)
    }

    private companion object {
        const val REQUEST_OPEN_MODEL = 1001
        const val REQUEST_CHOOSE_LITERT_MODEL = 1003
        const val MENU_IMPORT_FILE = 2001
        const val MENU_CLEANUP_STORAGE = 2002
        const val ACTION_SET_DEFAULT = 3000
        const val ACTION_CHECK = 3001
        const val ACTION_RENAME = 3002
        const val ACTION_DELETE = 3003
        const val STATE_LAST_NOTIFIED_IMPORT_OPERATION_ID = "lastNotifiedImportOperationId"
        const val STATE_LAST_NOTIFIED_DOWNLOAD_OPERATION_ID = "lastNotifiedDownloadOperationId"
        const val STATE_LAST_NOTIFIED_DELETION_OPERATION_ID = "lastNotifiedDeletionOperationId"
        const val STATE_LAST_NOTIFIED_RENAME_OPERATION_ID = "lastNotifiedRenameOperationId"
        const val STATE_LAST_NOTIFIED_STORAGE_CLEANUP_OPERATION_ID =
            "lastNotifiedStorageCleanupOperationId"
        const val STATE_LAST_NOTIFIED_HEALTH_CHECK_OPERATION_ID =
            "lastNotifiedHealthCheckOperationId"
        const val STATE_PROCESS_SESSION_TOKEN = "processSessionToken"
        const val STATE_DOWNLOAD_PROCESS_SESSION_TOKEN = "downloadProcessSessionToken"
        const val PROGRESS_MAX = 10_000
    }
}
