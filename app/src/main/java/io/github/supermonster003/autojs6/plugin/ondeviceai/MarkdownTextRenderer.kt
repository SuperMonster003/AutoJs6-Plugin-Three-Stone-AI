package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan

internal class MarkdownTextRenderer(context: Context) {
    private val codeSurface = context.getColor(R.color.chat_markdown_code_surface)
    private val quoteColor = context.getColor(R.color.chat_markdown_quote)
    private val linkColor = context.getColor(R.color.chat_markdown_link)
    private val secondaryText = context.getColor(R.color.text_color_secondary)

    fun render(document: MarkdownDocument): SpannableStringBuilder =
        SpannableStringBuilder(document.text).apply {
            document.spans.forEach { span ->
                when (span.kind) {
                    MarkdownSpanKind.BOLD -> add(span, StyleSpan(Typeface.BOLD))
                    MarkdownSpanKind.ITALIC -> add(span, StyleSpan(Typeface.ITALIC))
                    MarkdownSpanKind.STRIKETHROUGH -> add(span, StrikethroughSpan())
                    MarkdownSpanKind.INLINE_CODE -> {
                        add(span, TypefaceSpan("monospace"))
                        add(span, BackgroundColorSpan(codeSurface))
                    }
                    MarkdownSpanKind.CODE_BLOCK -> {
                        add(span, TypefaceSpan("monospace"))
                        add(span, BackgroundColorSpan(codeSurface))
                    }
                    MarkdownSpanKind.HEADING_1 -> heading(span, 1.42f)
                    MarkdownSpanKind.HEADING_2 -> heading(span, 1.32f)
                    MarkdownSpanKind.HEADING_3 -> heading(span, 1.24f)
                    MarkdownSpanKind.HEADING_4 -> heading(span, 1.17f)
                    MarkdownSpanKind.HEADING_5 -> heading(span, 1.11f)
                    MarkdownSpanKind.HEADING_6 -> heading(span, 1.06f)
                    MarkdownSpanKind.BLOCK_QUOTE -> {
                        add(span, QuoteSpan(quoteColor))
                        add(span, ForegroundColorSpan(secondaryText))
                    }
                    MarkdownSpanKind.LIST_ITEM -> Unit
                    MarkdownSpanKind.LINK -> {
                        add(span, ForegroundColorSpan(linkColor))
                        add(span, UnderlineSpan())
                    }
                    MarkdownSpanKind.HORIZONTAL_RULE -> add(
                        span,
                        ForegroundColorSpan(secondaryText),
                    )
                }
            }
        }

    private fun SpannableStringBuilder.heading(span: MarkdownSpan, relativeSize: Float) {
        add(span, StyleSpan(Typeface.BOLD))
        add(span, RelativeSizeSpan(relativeSize))
    }

    private fun SpannableStringBuilder.add(span: MarkdownSpan, value: Any) {
        if (span.end <= span.start || span.end > length) return
        setSpan(value, span.start, span.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
