package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.Formatter
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Attachment views of the chat screen (roadmap P4.1, P4.3): the bar above the composer and the
 * strip above a user bubble. Thumbnails are decoded off the main thread and cached; tapping an
 * attachment opens it in another app through [FileProvider] with a read grant.
 */
internal class ChatAttachmentViews(
    private val activity: ConfiguredActivity,
    private val store: ChatAttachmentStore,
    private val snackbarAnchor: () -> View,
) {
    private val palette get() = activity.appPalette
    private val mainHandler = Handler(Looper.getMainLooper())
    private val decoder: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ThreeStoneAiAttachmentThumbnails").apply { isDaemon = true }
    }
    private val thumbnails = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun shutdown() {
        decoder.shutdownNow()
        thumbnails.evictAll()
    }

    /** The composer bar; [render] fills it. */
    fun createComposerBar(): HorizontalScrollView = HorizontalScrollView(activity).apply {
        tag = ChatViewTags.COMPOSER_ATTACHMENTS
        isHorizontalScrollBarEnabled = false
        visibility = View.GONE
        addView(LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL })
    }

    /**
     * Shows the pending attachments with a remove control each; [unsupported] ones are dimmed
     * because the current target cannot take them. [importing] adds a progress label.
     */
    fun render(
        bar: HorizontalScrollView,
        pending: List<ChatAttachment>,
        unsupported: Set<String>,
        importing: Boolean,
        onRemove: (ChatAttachment) -> Unit,
    ) {
        val row = bar.getChildAt(0) as LinearLayout
        row.removeAllViews()
        pending.forEach { attachment ->
            val file = store.pendingFile(attachment)
            val item = itemView(attachment, file, onRemove = { onRemove(attachment) }, onLongClick = null)
            item.alpha = if (attachment.id in unsupported) Ui.DISABLED_ALPHA else 1f
            row.addView(item, itemParams())
        }
        if (importing) {
            row.addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.chat_attachment_importing)
                    textSize = Ui.TEXT_CAPTION
                    setTextColor(palette.secondaryText)
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = activity.uiDp(THUMBNAIL_DP)
                },
                itemParams(),
            )
        }
        bar.visibility = if (pending.isEmpty() && !importing) View.GONE else View.VISIBLE
    }

    /**
     * Attachments shown above a sent user message; null when it has none. A long press opens the
     * message actions, as on the bubble, so a message without text keeps them.
     */
    fun createMessageStrip(conversationId: String, attachments: List<ChatAttachment>, onLongClick: () -> Unit): View? {
        if (attachments.isEmpty()) return null
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        attachments.forEach { attachment ->
            val file = store.file(conversationId, attachment)
            row.addView(itemView(attachment, file, onRemove = null, onLongClick = onLongClick), itemParams())
        }
        return HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun itemParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { marginEnd = activity.uiDp(Ui.SPACE_SM) }

    private fun itemView(
        attachment: ChatAttachment,
        file: File,
        onRemove: (() -> Unit)?,
        onLongClick: (() -> Unit)?,
    ): View {
        val content = if (attachment.kind == ChatAttachmentKind.IMAGE) thumbnailView(file) else chipView(attachment)
        content.contentDescription = attachment.name
        content.isClickable = true
        content.isFocusable = true
        content.setOnClickListener { open(file, attachment) }
        if (onLongClick != null) {
            content.setOnLongClickListener {
                onLongClick()
                true
            }
        }
        if (onRemove == null) return content
        return FrameLayout(activity).apply {
            addView(content)
            addView(
                ImageView(activity).apply {
                    setImageDrawable(activity.tintedDrawable(R.drawable.ic_close_24, palette.windowBackground))
                    background = activity.roundedFill(palette.secondaryText, REMOVE_SIZE_DP / 2)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val inset = activity.uiDp(2)
                    setPadding(inset, inset, inset, inset)
                    contentDescription = activity.getString(R.string.chat_attachment_remove, attachment.name)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onRemove() }
                },
                FrameLayout.LayoutParams(activity.uiDp(REMOVE_SIZE_DP), activity.uiDp(REMOVE_SIZE_DP), Gravity.TOP or Gravity.END),
            )
        }
    }

    private fun thumbnailView(file: File): View {
        val size = activity.uiDp(THUMBNAIL_DP)
        val key = file.path
        val image = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            tag = key
        }
        val cached = thumbnails.get(key)
        if (cached != null) {
            image.setImageBitmap(cached)
        } else {
            try {
                decoder.execute {
                    val bitmap = decodeThumbnail(file, size) ?: return@execute
                    mainHandler.post {
                        thumbnails.put(key, bitmap)
                        if (image.tag == key) image.setImageBitmap(bitmap)
                    }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                // The screen is closing.
            }
        }
        return FrameLayout(activity).apply {
            background = activity.roundedFill(palette.surfaceVariant, Ui.RADIUS_CONTROL, palette.outline)
            clipToOutline = true
            addView(image, FrameLayout.LayoutParams(size, size))
        }
    }

    private fun chipView(attachment: ChatAttachment): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = activity.uiDp(THUMBNAIL_DP)
        background = activity.roundedFill(palette.surface, Ui.RADIUS_CONTROL, palette.outline)
        setPaddingRelative(activity.uiDp(10), activity.uiDp(6), activity.uiDp(12), activity.uiDp(6))
        addView(
            ImageView(activity).apply {
                setImageDrawable(
                    activity.tintedDrawable(
                        if (attachment.kind == ChatAttachmentKind.PDF) R.drawable.ic_description_24 else R.drawable.ic_article_24,
                        palette.accent,
                    ),
                )
            },
            LinearLayout.LayoutParams(activity.uiDp(22), activity.uiDp(22)).apply { marginEnd = activity.uiDp(8) },
        )
        addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(activity).apply {
                    text = attachment.name
                    textSize = Ui.TEXT_SECONDARY
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    maxWidth = activity.uiDp(CHIP_TEXT_MAXIMUM_DP)
                    setTextColor(palette.primaryText)
                })
                addView(TextView(activity).apply {
                    text = Formatter.formatShortFileSize(activity, attachment.sizeBytes)
                    textSize = Ui.TEXT_LABEL
                    setTextColor(palette.secondaryText)
                })
            },
        )
    }

    private fun open(file: File, attachment: ChatAttachment) {
        if (!file.isFile) {
            activity.showSnackbar(snackbarAnchor(), activity.getString(R.string.chat_attachment_missing))
            return
        }
        val uri = try {
            FileProvider.getUriForFile(activity, authority(), file, attachment.name)
        } catch (_: IllegalArgumentException) {
            activity.showSnackbar(snackbarAnchor(), activity.getString(R.string.chat_attachment_open_failed))
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, attachment.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            activity.showSnackbar(snackbarAnchor(), activity.getString(R.string.chat_attachment_open_failed))
        } catch (_: SecurityException) {
            activity.showSnackbar(snackbarAnchor(), activity.getString(R.string.chat_attachment_open_failed))
        }
    }

    private fun authority() = activity.packageName + AUTHORITY_SUFFIX

    companion object {
        const val AUTHORITY_SUFFIX = ".attachments"
        private const val THUMBNAIL_DP = 64
        private const val REMOVE_SIZE_DP = 20
        private const val CHIP_TEXT_MAXIMUM_DP = 180
        private const val THUMBNAIL_CACHE_BYTES = 8 * 1024 * 1024

        private fun decodeThumbnail(file: File, sizePx: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
