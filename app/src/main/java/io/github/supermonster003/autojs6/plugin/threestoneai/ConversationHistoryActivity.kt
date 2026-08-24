package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import java.text.DateFormat
import java.util.Date

class ConversationHistoryActivity : ConfiguredActivity() {
    private lateinit var historyStore: ConversationHistoryStore
    private lateinit var historyColumn: LinearLayout
    private lateinit var toolbar: Toolbar
    private var conversations: List<StoredConversation> = emptyList()
    private val selectedIds = linkedSetOf<String>()
    private var selectionMode = false
    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = leaveSelectionMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyStore = ConversationHistoryStore(applicationContext)
        selectedIds += savedInstanceState?.getStringArrayList(STATE_SELECTED_IDS).orEmpty()
        selectionMode = savedInstanceState?.getBoolean(STATE_SELECTION_MODE) == true
        onBackPressedDispatcher.addCallback(this, selectionBackCallback)
        title = getString(R.string.chat_history_title)
        setContentView(createContentView())
    }

    override fun onResume() {
        super.onResume()
        renderHistory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_SELECTED_IDS, ArrayList(selectedIds))
        outState.putBoolean(STATE_SELECTION_MODE, selectionMode)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_SELECT, 0, R.string.chat_history_select).apply {
            icon = tintedDrawable(R.drawable.ic_select_all_24, appPalette.onPrimary)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, MENU_SELECT_ALL, 1, R.string.chat_history_select_all).apply {
            icon = tintedDrawable(R.drawable.ic_select_all_24, appPalette.onPrimary)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, MENU_DELETE, 2, R.string.chat_history_delete_selected).apply {
            icon = tintedDrawable(R.drawable.ic_delete_24, appPalette.onPrimary)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_CLEAR, 3, R.string.chat_history_clear_all)
        toolbar.post { tintToolbarIcons(toolbar) }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val hasHistory = conversations.isNotEmpty()
        menu.findItem(MENU_SELECT)?.apply {
            isVisible = !selectionMode
            isEnabled = hasHistory
        }
        menu.findItem(MENU_SELECT_ALL)?.apply {
            isVisible = selectionMode
            isEnabled = selectedIds.size < conversations.size
        }
        menu.findItem(MENU_DELETE)?.apply {
            isVisible = selectionMode
            isEnabled = selectedIds.isNotEmpty()
        }
        menu.findItem(MENU_CLEAR)?.apply {
            isVisible = !selectionMode
            isEnabled = hasHistory
        }
        toolbar.title = if (selectionMode) {
            getString(R.string.chat_history_selected_count, selectedIds.size)
        } else {
            getString(R.string.chat_history_title)
        }
        toolbar.post { tintToolbarIcons(toolbar) }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_SELECT -> {
            enterSelectionMode()
            true
        }
        MENU_SELECT_ALL -> {
            selectedIds.clear()
            selectedIds += conversations.map(StoredConversation::id)
            renderHistory()
            true
        }
        MENU_DELETE -> {
            confirmDeleteSelected()
            true
        }
        MENU_CLEAR -> {
            confirmClearAll()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(appPalette.windowBackground)
        }
        toolbar = createAppToolbar(R.string.chat_history_title, showBack = true)
        toolbar.setNavigationOnClickListener {
            if (selectionMode) leaveSelectionMode() else finish()
        }
        root.addView(toolbar)
        root.addView(
            View(this).apply { setBackgroundColor(appPalette.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)),
        )
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

    private fun renderHistory() {
        conversations = historyStore.loadAll()
        selectedIds.retainAll(conversations.map(StoredConversation::id).toSet())
        if (conversations.isEmpty()) selectionMode = false
        selectionBackCallback.isEnabled = selectionMode
        historyColumn.removeAllViews()
        if (conversations.isEmpty()) {
            historyColumn.gravity = Gravity.CENTER
            historyColumn.addView(TextView(this).apply {
                text = getString(R.string.chat_history_empty_title)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(appPalette.primaryText)
                setPaddingRelative(dp(24), dp(80), dp(24), dp(7))
            })
            historyColumn.addView(TextView(this).apply {
                text = getString(R.string.chat_history_empty_description)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(appPalette.secondaryText)
                setPaddingRelative(dp(24), 0, dp(24), dp(24))
            })
        } else {
            historyColumn.gravity = Gravity.NO_GRAVITY
            conversations.forEach { conversation ->
                historyColumn.addView(conversationRow(conversation))
            }
        }
        invalidateOptionsMenu()
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

        val checked = conversation.id in selectedIds
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            isActivated = checked
            contentDescription = conversation.title
            setPaddingRelative(if (selectionMode) dp(6) else dp(16), dp(12), dp(16), dp(12))
            background = roundedRipple(checked)
            addView(CheckBox(context).apply {
                visibility = if (selectionMode) View.VISIBLE else View.GONE
                isChecked = checked
                isClickable = false
                buttonTintList = controlTintList()
                contentDescription = conversation.title
            }, LinearLayout.LayoutParams(dp(46), dp(46)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = conversation.title
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(appPalette.primaryText)
                })
                if (preview.isNotEmpty()) {
                    addView(TextView(context).apply {
                        text = preview
                        textSize = 13.5f
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(appPalette.secondaryText)
                        setPaddingRelative(0, dp(5), 0, 0)
                    })
                }
                addView(TextView(context).apply {
                    text = metadata
                    textSize = 11.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(appPalette.secondaryText)
                    setPaddingRelative(0, dp(7), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener {
                if (selectionMode) toggleSelection(conversation.id)
                else openConversation(conversation.id)
            }
            setOnLongClickListener {
                if (!selectionMode) enterSelectionMode(conversation.id)
                else toggleSelection(conversation.id)
                true
            }
        }.also { row ->
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun enterSelectionMode(initialId: String? = null) {
        selectionMode = true
        initialId?.let(selectedIds::add)
        renderHistory()
    }

    private fun leaveSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        renderHistory()
    }

    private fun toggleSelection(conversationId: String) {
        if (!selectedIds.add(conversationId)) selectedIds.remove(conversationId)
        renderHistory()
    }

    private fun confirmDeleteSelected() {
        if (selectedIds.isEmpty()) return
        showThemedDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.chat_history_delete_title)
                .setMessage(getString(R.string.chat_history_delete_message, selectedIds.size))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.chat_history_delete_selected) { _, _ ->
                    historyStore.delete(selectedIds)
                    leaveSelectionMode()
                }
                .create(),
        )
    }

    private fun confirmClearAll() {
        if (conversations.isEmpty()) return
        showThemedDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.chat_history_clear_title)
                .setMessage(R.string.chat_history_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.chat_history_clear_all) { _, _ ->
                    historyStore.clear()
                    leaveSelectionMode()
                }
                .create(),
        )
    }

    private fun showThemedDialog(dialog: AlertDialog) {
        dialog.show()
        tintDialogButtons(dialog)
    }

    private fun openConversation(conversationId: String) {
        startActivity(
            Intent(this, ChatActivity::class.java)
                .putExtra(ConversationNavigation.EXTRA_CONVERSATION_ID, conversationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun roundedRipple(selected: Boolean) = RippleDrawable(
        ColorStateList.valueOf(AppColorPolicy.withAlpha(appPalette.accent, 0x24)),
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (selected) {
                AppColorPolicy.withAlpha(appPalette.accent, if (appPalette.isDark) 0x38 else 0x24)
            } else {
                appPalette.assistantSurface
            })
            cornerRadius = dp(15).toFloat()
            setStroke(dp(1), appPalette.chatBorder)
        },
        null,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MENU_SELECT = 1
        const val MENU_SELECT_ALL = 2
        const val MENU_DELETE = 3
        const val MENU_CLEAR = 4
        const val STATE_SELECTED_IDS = "selectedConversationIds"
        const val STATE_SELECTION_MODE = "conversationSelectionMode"
        val WHITESPACE = Regex("\\s+")
    }
}
