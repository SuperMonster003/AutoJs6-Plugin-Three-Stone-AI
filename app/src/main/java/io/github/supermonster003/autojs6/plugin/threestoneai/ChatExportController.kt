package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.uiDp
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

internal enum class ChatExportKind { MARKDOWN, IMAGE }

/** The installed version name, as shown on the About page (the build has no BuildConfig). */
internal fun Context.installedVersionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() }.getOrDefault("")

/**
 * Exports a stored conversation from the conversation menus (roadmap P7.1, D83, D149 - D156): as a
 * Markdown document the user saves through the system picker, or as a tall PNG shared through
 * [ShareFileProvider]. The caller refuses the conversation that is still generating and saves the
 * current conversation first, so both read what history holds.
 */
internal class ChatExportController(
    private val activity: ConfiguredActivity,
    private val historyStore: ConversationHistoryStore,
    private val attachmentStore: ChatAttachmentStore,
    private val uiSettings: () -> ChatUiSettings,
    private val snackbarAnchor: () -> View,
    private val markdownPicker: ActivityResultLauncher<String>,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ThreeStoneAiExport").apply { isDaemon = true }
    }
    private var pendingMarkdown: PendingMarkdown? = null
    private var imageJob: ImageJob? = null
    private var destroyed = false

    /** Instrumentation only: the open "include reasoning" dialog. */
    @VisibleForTesting
    internal var optionsDialog: AlertDialog? = null
        private set

    @VisibleForTesting
    internal var lastMarkdownExport: MarkdownExportResult? = null
        private set

    @VisibleForTesting
    internal var lastImageExport: ImageExportResult? = null
        private set

    val imageExportRunning: Boolean get() = imageJob != null

    fun start(conversationId: String, kind: ChatExportKind) {
        imageJob?.takeIf { kind == ChatExportKind.IMAGE }?.let { job ->
            // One image at a time; bring its progress and Cancel back if another message replaced them (D155).
            if (job.progress?.isShownOrQueued != true) showProgress(job)
            return
        }
        val conversation = historyStore.find(conversationId)
        if (conversation == null || conversation.messages.isEmpty()) {
            activity.showSnackbar(snackbarAnchor(), activity.getString(failureResource(kind)))
            return
        }
        if (!ConversationMarkdownExport.hasReasoning(conversation)) {
            proceed(conversation, kind, includeReasoning = false)
            return
        }
        // Reasoning can be long and personal, so it is left out unless chosen each time (D151).
        val checkBox = MaterialCheckBox(activity).apply {
            text = activity.getString(R.string.chat_export_include_reasoning)
            textSize = 14f
            minimumHeight = activity.uiDp(48)
            isChecked = false
            buttonTintList = activity.controlTintList()
            buttonIconTintList = ColorStateList.valueOf(activity.appPalette.onAccent)
            setTextColor(activity.appPalette.primaryText)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(activity.uiDp(Ui.SPACE_XXL - Ui.SPACE_XS), activity.uiDp(Ui.SPACE_SM), activity.uiDp(Ui.SPACE_XXL), 0)
            addView(checkBox)
        }
        optionsDialog = activity.materialDialog()
            .setTitle(if (kind == ChatExportKind.MARKDOWN) R.string.chat_export_markdown else R.string.chat_export_image)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> proceed(conversation, kind, checkBox.isChecked) }
            .setOnDismissListener { optionsDialog = null }
            .show()
            .also(activity::tintDialogButtons)
    }

    private fun proceed(conversation: StoredConversation, kind: ChatExportKind, includeReasoning: Boolean) {
        when (kind) {
            ChatExportKind.MARKDOWN -> {
                pendingMarkdown = PendingMarkdown(conversation.id, includeReasoning)
                val fallback = "3-stone-ai-conversation-" +
                    SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.US).format(Date())
                try {
                    markdownPicker.launch(ConversationMarkdownExport.fileName(conversation.title, fallback))
                } catch (_: ActivityNotFoundException) {
                    pendingMarkdown = null
                    activity.showSnackbar(snackbarAnchor(), activity.getString(R.string.chat_export_markdown_failed))
                }
            }
            ChatExportKind.IMAGE -> shareImage(conversation, includeReasoning)
        }
    }

    /** The document the user picked for [pendingMarkdown]; null when the picker was cancelled. */
    fun onMarkdownDocument(uri: Uri?) {
        val pending = pendingMarkdown ?: return
        pendingMarkdown = null
        if (uri == null) return
        val conversation = historyStore.find(pending.conversationId)
        val labels = markdownLabels()
        val timeFormat = SimpleDateFormat(MARKDOWN_TIME_PATTERN, Locale.US)
        val versionName = activity.installedVersionName()
        execute {
            val result = runCatching {
                val text = ConversationMarkdownExport.format(
                    conversation = requireNotNull(conversation) { "The conversation is no longer available" },
                    labels = labels,
                    includeReasoning = pending.includeReasoning,
                    formatTime = { millis -> timeFormat.format(Date(millis)) },
                    exportedAtMillis = System.currentTimeMillis(),
                    appVersion = versionName,
                )
                val bytes = text.toByteArray(Charsets.UTF_8)
                openTruncated(uri).use { output -> output.write(bytes) }
                bytes.size
            }.onFailure { error -> Log.w(TAG, "Markdown export failed", error) }
            mainHandler.post {
                lastMarkdownExport = MarkdownExportResult(uri, result.getOrNull())
                if (!destroyed) {
                    activity.showSnackbar(
                        snackbarAnchor(),
                        activity.getString(
                            if (result.isSuccess) R.string.chat_export_markdown_succeeded else R.string.chat_export_markdown_failed,
                        ),
                        Snackbar.LENGTH_LONG,
                    )
                }
            }
        }
    }

    private fun openTruncated(uri: Uri): OutputStream {
        val resolver = activity.contentResolver
        // "wt" truncates a document chosen to be replaced; some providers only accept "w".
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        }
        return stream ?: throw FileNotFoundException("The selected document could not be opened")
    }

    private fun markdownLabels() = MarkdownExportLabels(
        user = activity.getString(R.string.chat_history_role_user),
        assistant = activity.getString(R.string.chat_history_role_assistant),
        model = activity.getString(R.string.chat_export_md_model),
        created = activity.getString(R.string.chat_export_md_created),
        updated = activity.getString(R.string.chat_export_md_updated),
        exported = activity.getString(R.string.chat_export_md_exported),
        attachments = activity.getString(R.string.chat_export_md_attachments),
        reasoning = activity.getString(R.string.chat_reasoning_title),
        reasoningDuration = durationFormatter(activity.getString(R.string.chat_reasoning_duration)),
        reasoningTruncated = activity.getString(R.string.chat_reasoning_truncated),
        stopped = activity.getString(R.string.chat_generation_stopped),
        failed = activity.getString(R.string.chat_generation_failed),
        truncated = activity.getString(R.string.chat_generation_truncated),
        emptyResponse = activity.getString(R.string.chat_empty_response),
    )

    private fun shareImage(conversation: StoredConversation, includeReasoning: Boolean) {
        val job = ImageJob(conversation.id, SystemClock.elapsedRealtime())
        imageJob = job
        showProgress(job)
        val renderer = ChatTranscriptImageRenderer(activity, uiSettings())
        execute {
            val prepared = try {
                renderer.prepare(conversation, includeReasoning, attachmentStore) { job.cancelled }
            } catch (_: OutOfMemoryError) {
                null
            }
            job.preparedAt = SystemClock.elapsedRealtime()
            mainHandler.post {
                if (prepared == null) finishImage(job, R.string.chat_export_image_too_large) else render(job, renderer, prepared)
            }
        }
    }

    private fun showProgress(job: ImageJob) {
        val palette = activity.appPalette
        job.progress = Snackbar.make(snackbarAnchor(), activity.getString(R.string.chat_export_image_progress), Snackbar.LENGTH_INDEFINITE)
            .setBackgroundTint(palette.primaryText)
            .setTextColor(palette.windowBackground)
            .setActionTextColor(palette.windowBackground)
            .setAction(android.R.string.cancel) { cancelImage(job) }
            .also(Snackbar::show)
    }

    /** Main thread: lays the conversation out and draws it, halving the scale after an OutOfMemoryError (D156). */
    private fun render(job: ImageJob, renderer: ChatTranscriptImageRenderer, prepared: ChatTranscriptImageRenderer.Prepared) {
        if (job.cancelled || destroyed) {
            prepared.recycle()
            return
        }
        val bitmap: Bitmap
        val view: View
        try {
            view = renderer.layout(prepared, ChatTranscriptImageRenderer.layoutWidth(activity), System.currentTimeMillis())
            var scale = ChatImageExportPolicy.scale(view.width, view.height)
            var drawn: Bitmap? = null
            while (drawn == null) {
                drawn = try {
                    renderer.draw(view, scale)
                } catch (error: OutOfMemoryError) {
                    Log.w(TAG, "Not enough memory for a ${view.width} x ${view.height} image at scale $scale", error)
                    scale = ChatImageExportPolicy.retryScale(view.width, scale) ?: throw error
                    null
                }
            }
            bitmap = drawn
            job.drawnAt = SystemClock.elapsedRealtime()
        } catch (_: OutOfMemoryError) {
            finishImage(job, R.string.chat_export_image_too_large)
            return
        } catch (error: RuntimeException) {
            Log.w(TAG, "Conversation image could not be drawn", error)
            finishImage(job, R.string.chat_export_image_failed)
            return
        } finally {
            prepared.recycle()
        }
        val layoutWidth = view.width
        val layoutHeight = view.height
        val directory = File(activity.cacheDir, ChatImageExportPolicy.SHARE_DIRECTORY)
        execute {
            val file = runCatching {
                directory.mkdirs()
                deleteStaleShares(directory)
                File(directory, ChatImageExportPolicy.fileName(System.currentTimeMillis())).also { target ->
                    val written = FileOutputStream(target).use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
                    check(written) { "PNG encoding failed" }
                }
            }.also { job.encodedAt = SystemClock.elapsedRealtime() }.onFailure { error -> Log.w(TAG, "Conversation image could not be written", error) }.getOrNull()
            val width = bitmap.width
            val height = bitmap.height
            bitmap.recycle()
            mainHandler.post {
                if (file == null) {
                    finishImage(job, R.string.chat_export_image_failed)
                } else {
                    share(job, file, ImageExportResult(file, width, height, layoutWidth, layoutHeight))
                }
            }
        }
    }

    private fun deleteStaleShares(directory: File) {
        val files = directory.listFiles()?.filter(File::isFile).orEmpty()
        val stale = ChatImageExportPolicy.staleFiles(files.associate { it.name to it.lastModified() }, System.currentTimeMillis())
        files.filter { it.name in stale }.forEach(File::delete)
    }

    private fun share(job: ImageJob, file: File, result: ImageExportResult) {
        if (job.cancelled || destroyed) {
            file.delete()
            return
        }
        val uri = try {
            FileProvider.getUriForFile(activity, activity.packageName + ShareFileProvider.AUTHORITY_SUFFIX, file)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Conversation image is outside the shared directory", error)
            finishImage(job, R.string.chat_export_image_failed)
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME_PNG)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newUri(activity.contentResolver, file.name, uri)
        try {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.chat_export_share_title)))
        } catch (_: ActivityNotFoundException) {
            finishImage(job, R.string.chat_export_image_failed)
            return
        }
        val finished = result.copy(
            elapsedMillis = SystemClock.elapsedRealtime() - job.startedAt,
            prepareMillis = job.preparedAt - job.startedAt,
            drawMillis = job.drawnAt - job.preparedAt,
            encodeMillis = job.encodedAt - job.drawnAt,
        )
        Log.d(
            TAG,
            "Conversation image ${finished.width} x ${finished.height} from ${finished.layoutWidth} x ${finished.layoutHeight} " +
                "in ${finished.elapsedMillis} ms (prepare ${finished.prepareMillis}, draw ${finished.drawMillis}, encode ${finished.encodeMillis})",
        )
        lastImageExport = finished
        finishImage(job, null)
    }

    private fun cancelImage(job: ImageJob) {
        job.cancelled = true
        finishImage(job, null)
    }

    private fun finishImage(job: ImageJob, messageResource: Int?) {
        job.progress?.dismiss()
        job.progress = null
        if (imageJob === job) imageJob = null
        if (messageResource != null && !destroyed) activity.showSnackbar(snackbarAnchor(), activity.getString(messageResource), Snackbar.LENGTH_LONG)
    }

    private fun execute(task: () -> Unit) {
        try {
            worker.execute(task)
        } catch (_: RejectedExecutionException) {
            // The screen is closing.
        }
    }

    fun save(outState: Bundle) {
        pendingMarkdown?.let { pending ->
            outState.putString(STATE_PENDING_ID, pending.conversationId)
            outState.putBoolean(STATE_PENDING_REASONING, pending.includeReasoning)
        }
    }

    fun restore(savedInstanceState: Bundle?) {
        val id = savedInstanceState?.getString(STATE_PENDING_ID) ?: return
        pendingMarkdown = PendingMarkdown(id, savedInstanceState.getBoolean(STATE_PENDING_REASONING))
    }

    /** An image still being made is dropped with the screen; a Markdown document being written is finished. */
    fun shutdown() {
        destroyed = true
        imageJob?.let { job ->
            job.cancelled = true
            job.progress?.dismiss()
        }
        imageJob = null
        optionsDialog?.dismiss()
        worker.shutdown()
    }

    private class PendingMarkdown(val conversationId: String, val includeReasoning: Boolean)

    private class ImageJob(val conversationId: String, val startedAt: Long) {
        @Volatile
        var cancelled = false
        var progress: Snackbar? = null

        // Phase ends (elapsed realtime), handed between the worker and the main thread through the handler.
        var preparedAt = 0L
        var drawnAt = 0L
        var encodedAt = 0L
    }

    data class MarkdownExportResult(val uri: Uri, val bytes: Int?)

    /**
     * Output and layout sizes, the time from the request to the share sheet being started, and its
     * phases: thumbnails (worker), layout and drawing (main thread, including its queue) and PNG
     * encoding (worker).
     */
    data class ImageExportResult(
        val file: File,
        val width: Int,
        val height: Int,
        val layoutWidth: Int,
        val layoutHeight: Int,
        val elapsedMillis: Long = 0L,
        val prepareMillis: Long = 0L,
        val drawMillis: Long = 0L,
        val encodeMillis: Long = 0L,
    )

    companion object {
        private const val TAG = "ThreeStoneAiExport"
        const val MIME_MARKDOWN = "text/markdown"
        private const val MIME_PNG = "image/png"
        private const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss"
        private const val MARKDOWN_TIME_PATTERN = "yyyy-MM-dd HH:mm XXX"
        private const val STATE_PENDING_ID = "chat_export_pending_markdown_id"
        private const val STATE_PENDING_REASONING = "chat_export_pending_markdown_reasoning"

        private fun failureResource(kind: ChatExportKind) =
            if (kind == ChatExportKind.MARKDOWN) R.string.chat_export_markdown_failed else R.string.chat_export_image_failed

        /** "Thought for %1$d s" with the plural-free count the chat card uses. */
        private fun durationFormatter(pattern: String): (Long) -> String = { seconds -> String.format(Locale.ROOT, pattern, seconds) }
    }
}
