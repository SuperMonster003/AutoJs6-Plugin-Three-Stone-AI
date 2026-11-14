package io.github.supermonster003.autojs6.plugin.threestoneai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.text.TextUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedFill
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Serves only `cache/share`, the conversation images handed to other apps (roadmap P7.1, D153). */
internal class ShareFileProvider : FileProvider() {
    companion object {
        const val AUTHORITY_SUFFIX = ".share"
    }
}

/** Sizes, names and cleanup of the shared conversation image (roadmap P7.1, D83, D152 - D154). */
internal object ChatImageExportPolicy {
    /** Taller images are scaled down as a whole (D83); also within every renderer's texture limit. */
    const val MAXIMUM_HEIGHT_PX = 16_384

    /** Layout width cap, so a tablet renders a phone-like column instead of very long lines. */
    const val MAXIMUM_LAYOUT_WIDTH_DP = 480

    /** Below this output width the text is no longer legible, so a retry after OOM stops. */
    const val MINIMUM_OUTPUT_WIDTH_PX = 360

    /** `cache/share`, the only directory [ShareFileProvider] serves. */
    const val SHARE_DIRECTORY = "share"

    /** Older shared images are deleted when the next one is made (D154). */
    const val SHARE_RETENTION_MILLIS = 24L * 60L * 60L * 1_000L

    /** At most this many shared images are kept, the new one included (D154). */
    const val SHARE_KEPT_FILES = 3

    /** Image attachments beyond this are drawn as name chips, which bounds the decoding time. */
    const val MAXIMUM_THUMBNAILS = 120

    /** Scale for a [widthPx] x [heightPx] layout: 1, or less when it is taller than the limit. */
    fun scale(widthPx: Int, heightPx: Int): Float {
        require(widthPx > 0 && heightPx > 0)
        return min(1f, MAXIMUM_HEIGHT_PX.toFloat() / heightPx)
    }

    /** Bitmap size of a layout drawn at [scale]; the height never exceeds [MAXIMUM_HEIGHT_PX]. */
    fun outputSize(widthPx: Int, heightPx: Int, scale: Float): Pair<Int, Int> =
        (widthPx * scale).roundToInt().coerceAtLeast(1) to
            (heightPx * scale).toInt().coerceIn(1, MAXIMUM_HEIGHT_PX)

    /** The next scale after an OutOfMemoryError, or null when the image would be too small to read. */
    fun retryScale(widthPx: Int, scale: Float): Float? {
        val next = scale / 2f
        return next.takeIf { widthPx * it >= MINIMUM_OUTPUT_WIDTH_PX }
    }

    /**
     * Names of shared images to delete before a new one is written: those older than
     * [SHARE_RETENTION_MILLIS] and all but the newest `SHARE_KEPT_FILES - 1`.
     */
    fun staleFiles(files: Map<String, Long>, nowMillis: Long): Set<String> {
        val newestFirst = files.entries.sortedByDescending { it.value }
        return newestFirst.filterIndexed { index, (_, modified) ->
            index >= SHARE_KEPT_FILES - 1 || nowMillis - modified > SHARE_RETENTION_MILLIS
        }.map { it.key }.toSet()
    }

    fun fileName(nowMillis: Long): String =
        "3-Stone-AI-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMillis)) + ".png"
}

/**
 * Draws a conversation as one tall image the way the chat shows it (roadmap P7.1, D83, D152): the
 * same message views, bubble styles, font size and theme, laid out off screen. Version switches,
 * search highlights and usage lines are left out; code blocks and tables that scroll sideways in
 * the chat show their visible width.
 */
internal class ChatTranscriptImageRenderer(
    private val activity: ConfiguredActivity,
    private val settings: ChatUiSettings,
) {
    private val palette get() = activity.appPalette

    /** Parsed replies and decoded thumbnails, made off the main thread. */
    class Prepared(
        val conversation: StoredConversation,
        val includeReasoning: Boolean,
        val documents: Map<Long, MarkdownDocument>,
        val thumbnails: Map<String, Bitmap>,
    ) {
        fun recycle() = thumbnails.values.forEach(Bitmap::recycle)
    }

    /** Worker thread: everything that does not need views. */
    fun prepare(conversation: StoredConversation, includeReasoning: Boolean, store: ChatAttachmentStore, cancelled: () -> Boolean): Prepared {
        val documents = HashMap<Long, MarkdownDocument>()
        val thumbnails = HashMap<String, Bitmap>()
        val size = activity.uiDp(THUMBNAIL_DP)
        conversation.messages.forEach { message ->
            if (cancelled()) return@forEach
            if (message.role == ChatMessageRole.ASSISTANT && message.text.isNotEmpty()) {
                documents[message.id] = ConversationSearchPolicy.documentFor(message)
            }
            message.attachments.filter { it.kind == ChatAttachmentKind.IMAGE }.forEach { attachment ->
                if (thumbnails.size < ChatImageExportPolicy.MAXIMUM_THUMBNAILS && attachment.id !in thumbnails) {
                    squareThumbnail(store.file(conversation.id, attachment), size)?.let { thumbnails[attachment.id] = it }
                }
            }
        }
        return Prepared(conversation, includeReasoning, documents, thumbnails)
    }

    /** Main thread: the laid-out view of [prepared], [widthPx] wide. */
    fun layout(prepared: Prepared, widthPx: Int, exportedAtMillis: Long): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = activity.resources.configuration.layoutDirection
            setBackgroundColor(palette.windowBackground)
            setPaddingRelative(dp(16), dp(20), dp(16), dp(16))
        }
        val contentWidth = widthPx - root.paddingStart - root.paddingEnd
        addHeader(root, prepared.conversation, exportedAtMillis)
        prepared.conversation.messages.forEach { message ->
            if (message.role == ChatMessageRole.NOTICE) addNotice(root, message) else addMessage(root, prepared, message, contentWidth)
        }
        root.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.app_name) + " " + activity.installedVersionName()
                textSize = Ui.TEXT_LABEL
                gravity = Gravity.CENTER
                setTextColor(palette.secondaryText)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) },
        )
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    /** Main thread: [view] drawn at [scale]; throws OutOfMemoryError when the bitmap does not fit. */
    fun draw(view: View, scale: Float): Bitmap {
        val (width, height) = ChatImageExportPolicy.outputSize(view.width, view.height, scale)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(palette.windowBackground)
                scale(scale, scale)
                view.draw(this)
            }
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
        return bitmap
    }

    private fun addHeader(root: LinearLayout, conversation: StoredConversation, exportedAtMillis: Long) {
        root.addView(TextView(activity).apply {
            text = conversation.title
            textSize = Ui.TEXT_PAGE_TITLE
            typeface = Ui.mediumTypeface
            setTextColor(palette.primaryText)
        })
        val locale = activity.resources.configuration.locales[0]
        val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(Date(conversation.updatedAtMillis.takeIf { it > 0L } ?: exportedAtMillis))
        val subtitle = listOfNotNull(conversation.target?.displayName, time).joinToString(" | ")
        root.addView(
            TextView(activity).apply {
                text = subtitle
                textSize = Ui.TEXT_CAPTION
                setTextColor(palette.secondaryText)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply {
                    topMargin = dp(4)
                    bottomMargin = dp(12)
                },
        )
    }

    private fun addNotice(root: LinearLayout, message: ChatMessage) {
        val notice = MarkdownMessageView(activity, palette).apply {
            gravity = Gravity.CENTER
            setPaddingRelative(dp(14), dp(6), dp(14), dp(6))
            background = activity.roundedFill(palette.noticeSurface, Ui.RADIUS_BUBBLE)
            showPlainText(message.text, settings.fontSize.messageSp * NOTICE_TEXT_SCALE)
        }
        root.addView(
            notice,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(6)
                bottomMargin = dp(10)
            },
        )
    }

    private fun addMessage(root: LinearLayout, prepared: Prepared, message: ChatMessage, contentWidth: Int) {
        val user = message.role == ChatMessageRole.USER
        val style = if (user) settings.userBubbleStyle else settings.assistantBubbleStyle
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (user) Gravity.END else Gravity.START
        }
        val reasoning = message.reasoning?.takeIf { prepared.includeReasoning && !user && it.text.isNotEmpty() }
        if (reasoning != null) {
            val header = reasoning.durationMillis
                ?.let { activity.getString(R.string.chat_reasoning_duration, ChatReasoningCardPolicy.seconds(it)) }
                ?: activity.getString(R.string.chat_reasoning_title)
            val body = if (reasoning.truncated) {
                reasoning.text + "\n\n" + activity.getString(R.string.chat_reasoning_truncated)
            } else {
                reasoning.text
            }
            row.addView(
                ReasoningCardView(activity, palette).apply {
                    show(header, body, expanded = true, textSizeSp = settings.fontSize.messageSp * REASONING_TEXT_SCALE)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dp(6) },
            )
        }
        if (user && message.attachments.isNotEmpty()) addAttachments(row, prepared, message, contentWidth)
        if (!(user && message.text.isEmpty() && message.attachments.isNotEmpty())) {
            val body = MarkdownMessageView(activity, palette).apply { applyStyle(user, style, contentWidth) }
            val document = prepared.documents[message.id]
            when {
                document != null -> body.showDocument(document, settings.fontSize.messageSp)
                message.text.isNotEmpty() -> body.showPlainText(message.text, settings.fontSize.messageSp)
                else -> body.showPlainText(activity.getString(placeholder(message.status)), settings.fontSize.messageSp)
            }
            row.addView(
                body,
                LinearLayout.LayoutParams(
                    if (style == ChatBubbleStyle.NONE) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        status(message)?.let { value ->
            row.addView(TextView(activity).apply {
                text = activity.getString(value)
                textSize = 11f
                setTextColor(palette.secondaryText)
                setPaddingRelative(dp(4), dp(4), dp(4), 0)
            })
        }
        root.addView(
            row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = if (user) dp(8) else dp(4)
                bottomMargin = if (user) dp(4) else dp(14)
            },
        )
    }

    private fun MarkdownMessageView.applyStyle(user: Boolean, style: ChatBubbleStyle, contentWidth: Int) {
        if (style == ChatBubbleStyle.NONE) {
            setPaddingRelative(dp(2), dp(2), dp(2), dp(2))
            return
        }
        maximumWidth = contentWidth
        setPaddingRelative(dp(16), dp(10), dp(16), dp(10))
        background = when (style) {
            ChatBubbleStyle.NONE -> error("Handled above")
            ChatBubbleStyle.BACKGROUND ->
                activity.roundedFill(if (user) palette.userSurface else palette.assistantSurface, Ui.RADIUS_BUBBLE)
            ChatBubbleStyle.BORDER -> activity.roundedFill(
                android.graphics.Color.TRANSPARENT,
                Ui.RADIUS_BUBBLE,
                if (user) palette.accent else palette.chatBorder,
            )
        }
    }

    /** Image thumbnails in rows as wide as the column, then one chip per other file (or image not drawn). */
    private fun addAttachments(row: LinearLayout, prepared: Prepared, message: ChatMessage, contentWidth: Int) {
        val size = dp(THUMBNAIL_DP)
        val gap = dp(Ui.SPACE_SM)
        val drawn = message.attachments.filter { it.id in prepared.thumbnails }
        val perRow = max(1, (contentWidth + gap) / (size + gap))
        drawn.chunked(perRow).forEach { chunk ->
            val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEachIndexed { index, attachment ->
                line.addView(
                    ImageView(activity).apply {
                        setImageDrawable(
                            RoundedBitmapDrawableFactory.create(resources, prepared.thumbnails.getValue(attachment.id))
                                .apply { cornerRadius = dp(Ui.RADIUS_CONTROL).toFloat() },
                        )
                        scaleType = ImageView.ScaleType.FIT_XY
                    },
                    LinearLayout.LayoutParams(size, size).apply { if (index > 0) marginStart = gap },
                )
            }
            row.addView(line, wrapParams().apply { bottomMargin = dp(6) })
        }
        message.attachments.filter { it.id !in prepared.thumbnails }.forEach { attachment ->
            row.addView(chip(attachment, contentWidth), wrapParams().apply { bottomMargin = dp(6) })
        }
    }

    private fun chip(attachment: ChatAttachment, contentWidth: Int) = TextView(activity).apply {
        text = attachment.name + "  " + Formatter.formatShortFileSize(activity, attachment.sizeBytes)
        textSize = Ui.TEXT_SECONDARY
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.MIDDLE
        maxWidth = contentWidth
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(palette.primaryText)
        background = activity.roundedFill(palette.surface, Ui.RADIUS_CONTROL, palette.outline)
        setPaddingRelative(dp(10), dp(8), dp(12), dp(8))
        compoundDrawablePadding = dp(8)
        val icon = when (attachment.kind) {
            ChatAttachmentKind.IMAGE -> R.drawable.ic_image_24
            ChatAttachmentKind.PDF -> R.drawable.ic_description_24
            ChatAttachmentKind.TEXT -> R.drawable.ic_article_24
        }
        activity.tintedDrawable(icon, palette.accent)?.let { drawable ->
            drawable.setBounds(0, 0, dp(20), dp(20))
            setCompoundDrawablesRelative(drawable, null, null, null)
        }
    }

    private fun wrapParams() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun placeholder(status: ChatMessageStatus): Int = when (status) {
        ChatMessageStatus.COMPLETE -> R.string.chat_empty_response
        ChatMessageStatus.FAILED -> R.string.chat_generation_failed
        ChatMessageStatus.STOPPED, ChatMessageStatus.GENERATING -> R.string.chat_generation_stopped
    }

    /** The status line under a reply with text; an empty reply already shows it as its text. */
    private fun status(message: ChatMessage): Int? = when {
        message.role != ChatMessageRole.ASSISTANT || message.text.isEmpty() -> null
        message.status == ChatMessageStatus.STOPPED || message.status == ChatMessageStatus.GENERATING -> R.string.chat_generation_stopped
        message.status == ChatMessageStatus.FAILED -> R.string.chat_generation_failed
        message.truncated -> R.string.chat_generation_truncated
        else -> null
    }

    private fun dp(value: Int): Int = activity.uiDp(value)

    companion object {
        private const val THUMBNAIL_DP = 64
        private const val NOTICE_TEXT_SCALE = 0.8f
        private const val REASONING_TEXT_SCALE = 0.85f

        /** Layout width for the image: the portrait width of the screen, at most [ChatImageExportPolicy.MAXIMUM_LAYOUT_WIDTH_DP]. */
        fun layoutWidth(activity: ConfiguredActivity): Int {
            val metrics = activity.resources.displayMetrics
            return min(min(metrics.widthPixels, metrics.heightPixels), activity.uiDp(ChatImageExportPolicy.MAXIMUM_LAYOUT_WIDTH_DP))
        }

        /** A [sizePx] square, center-cropped thumbnail of an image file, or null when it cannot be read. */
        private fun squareThumbnail(file: File, sizePx: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val square = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val scale = sizePx.toFloat() / min(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate((sizePx - decoded.width * scale) / 2f, (sizePx - decoded.height * scale) / 2f)
            }
            Canvas(square).drawBitmap(decoded, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            decoded.recycle()
            square
        }.getOrNull()
    }
}
