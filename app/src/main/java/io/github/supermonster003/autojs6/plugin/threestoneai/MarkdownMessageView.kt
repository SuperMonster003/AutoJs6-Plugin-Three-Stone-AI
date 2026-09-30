package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.ceil
import kotlin.math.min

internal data class MarkdownSearchHighlight(
    val start: Int,
    val end: Int,
    val current: Boolean,
)

/** Native block renderer for streaming Markdown, including scrollable tables and fenced code. */
internal class MarkdownMessageView(
    context: Context,
    private val palette: AppThemePalette,
) : LinearLayout(context) {
    private val renderer = MarkdownTextRenderer(context, palette.accent)
    private val rangedTextViews = ArrayList<RangedTextView>()
    private var renderedDocument = MarkdownDocument("", emptyList())
    private var renderedHighlights: List<MarkdownSearchHighlight> = emptyList()
    private var messageTextSizeSp = ChatFontSize.DEFAULT.messageSp
    private var messageLongClick: (() -> Unit)? = null

    var maximumWidth: Int = Int.MAX_VALUE

    init {
        orientation = VERTICAL
    }

    fun setMessageLongClickListener(listener: (() -> Unit)?) {
        messageLongClick = listener
        isLongClickable = listener != null
        setOnLongClickListener {
            listener?.invoke()
            listener != null
        }
        applyLongClickToChildren(this)
    }

    fun showDocument(
        document: MarkdownDocument,
        textSizeSp: Float,
        highlights: List<MarkdownSearchHighlight> = emptyList(),
    ) {
        renderedDocument = document
        renderedHighlights = highlights
        messageTextSizeSp = textSizeSp
        rebuild()
    }

    fun showPlainText(text: CharSequence, textSizeSp: Float) {
        showDocument(MarkdownDocument(text.toString(), emptyList()), textSizeSp)
    }

    fun setMessageTextSize(textSizeSp: Float) {
        if (messageTextSizeSp == textSizeSp) return
        messageTextSizeSp = textSizeSp
        rebuild()
    }

    fun locate(globalOffset: Int): Boolean {
        val target = rangedTextViews.firstOrNull { ranged ->
            globalOffset in ranged.start until ranged.end
        } ?: return false
        target.view.post {
            val layout = target.view.layout ?: return@post
            val localOffset = (globalOffset - target.start).coerceIn(0, target.view.text.length)
            val line = layout.getLineForOffset(localOffset)
            val left = layout.getPrimaryHorizontal(localOffset).toInt() + target.view.paddingLeft
            val rectangle = Rect(
                left,
                target.view.paddingTop + layout.getLineTop(line),
                left + dp(24),
                target.view.paddingTop + layout.getLineBottom(line),
            )
            target.view.requestRectangleOnScreen(rectangle, true)
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val bounded = min(available, maximumWidth)
        val adjusted = when (widthMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(bounded, MeasureSpec.EXACTLY)
            else -> MeasureSpec.makeMeasureSpec(bounded, MeasureSpec.AT_MOST)
        }
        super.onMeasure(adjusted, heightMeasureSpec)
    }

    private fun rebuild() {
        removeAllViews()
        rangedTextViews.clear()
        val structural = renderedDocument.spans
            .filter { span -> span.kind in STRUCTURAL_KINDS }
            .sortedBy(MarkdownSpan::start)
        var cursor = 0
        structural.forEach { span ->
            if (span.start > cursor) addNormalText(cursor, span.start)
            when (span.kind) {
                MarkdownSpanKind.CODE_BLOCK -> addCodeBlock(span)
                MarkdownSpanKind.TABLE -> addTable(span)
                MarkdownSpanKind.HORIZONTAL_RULE -> addHorizontalRule()
                else -> Unit
            }
            cursor = maxOf(cursor, span.end)
        }
        if (cursor < renderedDocument.text.length) {
            addNormalText(cursor, renderedDocument.text.length)
        }
        if (childCount == 0 && renderedDocument.text.isNotEmpty()) {
            addNormalText(0, renderedDocument.text.length)
        }
        applyLongClickToChildren(this)
    }

    private fun addNormalText(start: Int, end: Int) {
        if (end <= start) return
        val value = renderRange(start, end)
        if (value.isEmpty()) return
        addView(createTextView(value, start, end))
    }

    private fun addCodeBlock(span: MarkdownSpan) {
        val code = renderedDocument.text.substring(span.start, span.end)
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            background = roundedDrawable(context.getColor(R.color.chat_markdown_code_surface), 10)
        }
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(12), dp(3), dp(4), dp(3))
            setBackgroundColor(AppColorPolicy.withAlpha(palette.primary, if (palette.isDark) 0x24 else 0x18))
        }
        header.addView(TextView(context).apply {
            text = span.metadata?.takeIf(String::isNotBlank) ?: context.getString(R.string.chat_code_block)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.secondaryText)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(ImageButton(context).apply {
            setImageDrawable(tintedDrawable(R.drawable.ic_copy_24, palette.accent))
            imageTintList = ColorStateList.valueOf(palette.accent)
            contentDescription = context.getString(R.string.chat_code_copy)
            background = selectableBorderlessBackground()
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { copyCode(code) }
        }, LayoutParams(dp(40), dp(40)))
        container.addView(header)

        val rendered = renderRange(span.start, span.end, excludeStructural = true)
        val codeText = createTextView(rendered, span.start, span.end).apply {
            typeface = Typeface.MONOSPACE
            setHorizontallyScrolling(true)
            setPaddingRelative(dp(12), dp(10), dp(12), dp(12))
        }
        container.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(codeText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(container, blockLayoutParams())
    }

    private fun addTable(span: MarkdownSpan) {
        val rows = tableRows(span)
        if (rows.isEmpty()) return
        val columnCount = rows.maxOf(List<CellRange>::size)
        val columnWidths = IntArray(columnCount) { dp(72) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                messageTextSizeSp,
                resources.displayMetrics,
            )
        }
        rows.forEach { row ->
            row.forEachIndexed { column, cell ->
                val visible = renderedDocument.text.substring(cell.start, cell.end)
                val longestLine = visible.lineSequence().maxByOrNull(String::length).orEmpty()
                val measured = ceil(paint.measureText(longestLine).toDouble()).toInt() + dp(24)
                columnWidths[column] = maxOf(columnWidths[column], measured.coerceAtMost(dp(220)))
            }
        }

        val table = TableLayout(context).apply {
            isShrinkAllColumns = false
            isStretchAllColumns = false
            rows.forEachIndexed { rowIndex, row ->
                addView(TableRow(context).apply {
                    row.forEachIndexed { column, cell ->
                        val value = renderRange(cell.start, cell.end, excludeStructural = true)
                        addView(createTextView(value, cell.start, cell.end).apply {
                            gravity = tableCellGravity(span.metadata, column)
                            if (rowIndex == 0) typeface = Typeface.DEFAULT_BOLD
                            setPaddingRelative(dp(12), dp(9), dp(12), dp(9))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                breakStrategy = android.graphics.text.LineBreaker
                                    .BREAK_STRATEGY_HIGH_QUALITY
                                hyphenationFrequency = android.text.Layout
                                    .HYPHENATION_FREQUENCY_NORMAL
                            }
                            background = GradientDrawable().apply {
                                setColor(
                                    if (rowIndex == 0) {
                                        AppColorPolicy.withAlpha(palette.primary, if (palette.isDark) 0x30 else 0x1F)
                                    } else {
                                        context.getColor(R.color.chat_markdown_code_surface)
                                    },
                                )
                                setStroke(dp(1), palette.chatBorder)
                            }
                        }, TableRow.LayoutParams(columnWidths[column], TableRow.LayoutParams.WRAP_CONTENT))
                    }
                })
            }
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(table, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }, blockLayoutParams())
    }

    private fun addHorizontalRule() {
        addView(View(context).apply {
            setBackgroundColor(AppColorPolicy.withAlpha(palette.secondaryText, 0x66))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
        })
    }

    private fun createTextView(value: CharSequence, start: Int, end: Int) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, messageTextSizeSp)
        setTextColor(palette.primaryText)
        setLinkTextColor(palette.accent)
        highlightColor = AppColorPolicy.withAlpha(palette.accent, 0x44)
        setLineSpacing(0f, 1.12f)
        setTextIsSelectable(messageLongClick == null)
        linksClickable = true
        if ((value as? Spannable)?.getSpans(0, value.length, ClickableSpan::class.java)?.isNotEmpty() == true) {
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = AppColorPolicy.withAlpha(palette.accent, 0x44)
        }
        rangedTextViews += RangedTextView(start, end, this)
    }

    private fun renderRange(
        start: Int,
        end: Int,
        excludeStructural: Boolean = true,
    ): SpannableStringBuilder {
        val sliced = MarkdownDocument(
            text = renderedDocument.text.substring(start, end),
            spans = renderedDocument.spans.mapNotNull { span ->
                if (excludeStructural && span.kind in STRUCTURAL_KINDS) return@mapNotNull null
                val clippedStart = maxOf(start, span.start)
                val clippedEnd = minOf(end, span.end)
                if (clippedEnd <= clippedStart) null else span.copy(
                    start = clippedStart - start,
                    end = clippedEnd - start,
                )
            },
        )
        return renderer.render(sliced).apply {
            renderedHighlights.forEach { highlight ->
                val clippedStart = maxOf(start, highlight.start)
                val clippedEnd = minOf(end, highlight.end)
                if (clippedEnd > clippedStart) {
                    setSpan(
                        BackgroundColorSpan(
                            context.getColor(
                                if (highlight.current) {
                                    R.color.chat_search_match_current
                                } else {
                                    R.color.chat_search_match
                                },
                            ),
                        ),
                        clippedStart - start,
                        clippedEnd - start,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
    }

    private fun tableRows(span: MarkdownSpan): List<List<CellRange>> {
        val text = renderedDocument.text
        val result = ArrayList<List<CellRange>>()
        var lineStart = span.start
        while (lineStart <= span.end) {
            val lineEnd = text.indexOf('\n', lineStart).let { found ->
                if (found < 0 || found > span.end) span.end else found
            }
            val cells = ArrayList<CellRange>()
            var cellStart = lineStart
            while (cellStart <= lineEnd) {
                val tab = text.indexOf('\t', cellStart).let { found ->
                    if (found < 0 || found > lineEnd) lineEnd else found
                }
                cells += CellRange(cellStart, tab)
                if (tab == lineEnd) break
                cellStart = tab + 1
            }
            result += cells
            if (lineEnd == span.end) break
            lineStart = lineEnd + 1
        }
        return result
    }

    private fun tableCellGravity(metadata: String?, column: Int): Int = when (metadata?.getOrNull(column)) {
        'C' -> Gravity.CENTER_HORIZONTAL
        'E' -> Gravity.END
        else -> Gravity.START
    } or Gravity.CENTER_VERTICAL

    private fun copyCode(code: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.chat_code_clipboard_label), code),
        )
        Toast.makeText(context, R.string.chat_code_copied, Toast.LENGTH_SHORT).show()
    }

    private fun applyLongClickToChildren(view: View) {
        if (view !== this && view !is ImageButton) {
            view.setOnLongClickListener {
                messageLongClick?.invoke()
                messageLongClick != null
            }
        }
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) applyLongClickToChildren(view.getChildAt(index))
        }
    }

    private fun blockLayoutParams() = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = dp(6)
        bottomMargin = dp(6)
    }

    private fun roundedDrawable(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun selectableBorderlessBackground(): android.graphics.drawable.Drawable? {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                value,
                true,
            )
        ) {
            AppCompatResources.getDrawable(context, value.resourceId)
        } else {
            null
        }
    }

    private fun tintedDrawable(resource: Int, color: Int) =
        AppCompatResources.getDrawable(context, resource)?.let { drawable ->
            DrawableCompat.wrap(drawable.mutate()).also { wrapped ->
                DrawableCompat.setTint(wrapped, color)
            }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class RangedTextView(
        val start: Int,
        val end: Int,
        val view: TextView,
    )

    private data class CellRange(
        val start: Int,
        val end: Int,
    )

    private companion object {
        val STRUCTURAL_KINDS = setOf(
            MarkdownSpanKind.CODE_BLOCK,
            MarkdownSpanKind.TABLE,
            MarkdownSpanKind.HORIZONTAL_RULE,
        )
    }
}
