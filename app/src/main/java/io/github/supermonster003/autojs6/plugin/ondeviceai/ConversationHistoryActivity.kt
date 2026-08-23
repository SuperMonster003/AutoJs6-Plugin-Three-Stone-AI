package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class ConversationHistoryActivity : Activity() {
    private lateinit var historyStore: ConversationHistoryStore
    private lateinit var historyColumn: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyStore = ConversationHistoryStore(applicationContext)
        title = getString(R.string.chat_history_title)
        setContentView(createContentView())
    }

    override fun onResume() {
        super.onResume()
        renderHistory()
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.window_background))
            addView(createToolbar())
            addView(
                View(context).apply { setBackgroundColor(getColor(R.color.divider)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)),
            )
        }
        historyColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(16), dp(12), dp(16), dp(28))
        }
        root.addView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(
                    historyColumn,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        applySystemBarInsets(root)
        return root
    }

    private fun createToolbar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(58)
        setPaddingRelative(dp(4), dp(5), dp(16), dp(4))
        addView(TextView(context).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            contentDescription = getString(R.string.navigation_back)
            setTextColor(getColor(R.color.text_color_primary))
            applySelectableBackground(this)
            setOnClickListener { finish() }
        })
        addView(TextView(context).apply {
            text = getString(R.string.chat_history_title)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.text_color_primary))
        })
    }

    private fun renderHistory() {
        historyColumn.removeAllViews()
        val conversations = historyStore.loadAll()
        if (conversations.isEmpty()) {
            historyColumn.gravity = Gravity.CENTER
            historyColumn.addView(TextView(this).apply {
                text = getString(R.string.chat_history_empty_title)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_color_primary))
                setPaddingRelative(dp(24), dp(80), dp(24), dp(7))
            })
            historyColumn.addView(TextView(this).apply {
                text = getString(R.string.chat_history_empty_description)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_color_secondary))
                setPaddingRelative(dp(24), 0, dp(24), dp(24))
            })
            return
        }
        historyColumn.gravity = Gravity.NO_GRAVITY
        conversations.forEach { conversation -> historyColumn.addView(conversationRow(conversation)) }
    }

    private fun conversationRow(conversation: StoredConversation): View {
        val messageCount = conversation.messages.count { message ->
            message.role != ChatMessageRole.NOTICE
        }
        val updated = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(conversation.updatedAtMillis))
        val countAndTime = getString(R.string.chat_history_count_and_time, messageCount, updated)
        val metadata = conversation.modelDisplayName?.takeIf(String::isNotBlank)?.let { modelName ->
            getString(R.string.chat_history_metadata, countAndTime, modelName)
        } ?: countAndTime
        val preview = conversation.messages.asReversed().firstOrNull { message ->
            message.text.isNotBlank() && message.role != ChatMessageRole.NOTICE
        }?.let(ConversationSearchPolicy::documentFor)?.text
            ?.replace(WHITESPACE, " ")
            ?.trim()
            .orEmpty()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = conversation.title
            setPaddingRelative(dp(16), dp(14), dp(16), dp(14))
            background = roundedRipple()
            setOnClickListener { openConversation(conversation.id) }
            addView(TextView(context).apply {
                text = conversation.title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(getColor(R.color.text_color_primary))
            })
            if (preview.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = preview
                    textSize = 13.5f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(getColor(R.color.text_color_secondary))
                    setPaddingRelative(0, dp(5), 0, 0)
                })
            }
            addView(TextView(context).apply {
                text = metadata
                textSize = 11.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(getColor(R.color.text_color_secondary))
                setPaddingRelative(0, dp(7), 0, 0)
            })
        }.also { row ->
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun openConversation(conversationId: String) {
        startActivity(
            Intent(this, ChatActivity::class.java)
                .putExtra(ConversationNavigation.EXTRA_CONVERSATION_ID, conversationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun roundedRipple() = RippleDrawable(
        ColorStateList.valueOf(getColor(R.color.chat_ripple)),
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(getColor(R.color.chat_assistant_surface))
            cornerRadius = dp(15).toFloat()
            setStroke(dp(1), getColor(R.color.chat_border))
        },
        null,
    )

    private fun applySelectableBackground(view: View) {
        val value = TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)) {
            view.setBackgroundResource(value.resourceId)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
