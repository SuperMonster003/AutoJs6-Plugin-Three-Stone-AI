package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.checkbox.MaterialCheckBox
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.accentRipple
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.accentTone
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.buildScaffold
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.cardListParams
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.confirmDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.emptyStateView
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedRippleFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationHistoryActivity : ConfiguredActivity() {
    private lateinit var historyStore: ConversationHistoryStore
    private lateinit var historyColumn: LinearLayout
    private lateinit var toolbar: Toolbar
    private lateinit var screenRoot: View
    private var conversations: List<StoredConversation> = emptyList()
    private val selectedIds = linkedSetOf<String>()
    private val pendingExportIds = linkedSetOf<String>()
    private var selectionMode = false
    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = leaveSelectionMode()
    }
    private val importPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::importConversations) }
    private val exportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_CONVERSATIONS),
    ) { uri ->
        if (uri != null) exportConversations(uri)
        pendingExportIds.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyStore = ConversationHistoryStore(applicationContext)
        selectedIds += savedInstanceState?.getStringArrayList(STATE_SELECTED_IDS).orEmpty()
        pendingExportIds += savedInstanceState?.getStringArrayList(STATE_PENDING_EXPORT_IDS).orEmpty()
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
        outState.putStringArrayList(STATE_PENDING_EXPORT_IDS, ArrayList(pendingExportIds))
        outState.putBoolean(STATE_SELECTION_MODE, selectionMode)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_IMPORT, 0, R.string.chat_history_import).apply {
            icon = tintedDrawable(R.drawable.ic_upload_24, appPalette.primaryText)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, MENU_SELECT, 1, R.string.chat_history_select).apply {
            icon = tintedDrawable(R.drawable.ic_check_circle_24, appPalette.primaryText)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, MENU_SELECT_ALL, 2, R.string.chat_history_select_all).apply {
            icon = tintedDrawable(R.drawable.ic_select_all_24, appPalette.primaryText)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, MENU_COPY, 3, R.string.chat_history_copy_all).apply {
            icon = tintedDrawable(R.drawable.ic_copy_24, appPalette.primaryText)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, MENU_EXPORT, 4, R.string.chat_history_export).apply {
            icon = tintedDrawable(R.drawable.ic_download_24, appPalette.primaryText)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, MENU_DELETE, 5, R.string.chat_history_delete_selected).apply {
            icon = tintedDrawable(R.drawable.ic_delete_24, appPalette.primaryText)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_CLEAR, 6, R.string.chat_history_clear_all)
        toolbar.post { tintToolbarIcons(toolbar) }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val hasHistory = conversations.isNotEmpty()
        menu.findItem(MENU_IMPORT)?.isVisible = !selectionMode
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
        menu.findItem(MENU_COPY)?.apply {
            isVisible = selectionMode
            isEnabled = selectedIds.isNotEmpty()
        }
        menu.findItem(MENU_EXPORT)?.apply {
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
        MENU_IMPORT -> {
            importPicker.launch(arrayOf(MIME_CONVERSATIONS, "application/octet-stream"))
            true
        }
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
        MENU_COPY -> {
            copySelectedConversations()
            true
        }
        MENU_EXPORT -> {
            beginExportSelected()
            true
        }
        MENU_CLEAR -> {
            confirmClearAll()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun createContentView(): View {
        val scaffold = buildScaffold(
            R.string.chat_history_title,
            contentPadding = io.github.supermonster003.autojs6.plugin.threestoneai.ui.ContentPadding.SCREEN,
        )
        toolbar = scaffold.toolbar
        toolbar.setNavigationOnClickListener {
            if (selectionMode) leaveSelectionMode() else finish()
        }
        historyColumn = scaffold.content
        screenRoot = scaffold.root
        return scaffold.root
    }

    private fun renderHistory() {
        conversations = historyStore.loadAll()
        selectedIds.retainAll(conversations.map(StoredConversation::id).toSet())
        if (conversations.isEmpty()) selectionMode = false
        selectionBackCallback.isEnabled = selectionMode
        historyColumn.removeAllViews()
        if (conversations.isEmpty()) {
            historyColumn.gravity = Gravity.CENTER
            historyColumn.addView(
                emptyStateView(
                    title = getString(R.string.chat_history_empty_title),
                    description = getString(R.string.chat_history_empty_description),
                    iconResource = R.drawable.ic_history_24,
                ),
            )
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
        val metadata = conversation.target?.displayName?.takeIf(String::isNotBlank)?.let { targetName ->
            getString(R.string.chat_history_metadata, countAndTime, targetName)
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
            setPaddingRelative(uiDp(Ui.SPACE_LG), uiDp(Ui.SPACE_MD), uiDp(Ui.SPACE_LG), uiDp(Ui.SPACE_MD))
            background = roundedRippleFill(
                fillColor = if (checked) accentTone(appPalette.accent) else appPalette.surface,
                rippleColor = accentRipple(appPalette.accent),
                radiusDp = Ui.RADIUS_CARD,
                strokeColor = if (checked) appPalette.accent else appPalette.outline,
            )
            if (selectionMode) {
                addView(
                    MaterialCheckBox(context).apply {
                        isChecked = checked
                        isClickable = false
                        buttonTintList = controlTintList()
                        contentDescription = conversation.title
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginEnd = uiDp(Ui.SPACE_SM) },
                )
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = conversation.title
                    textSize = Ui.TEXT_ITEM
                    typeface = Ui.mediumTypeface
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(appPalette.primaryText)
                })
                if (preview.isNotEmpty()) {
                    addView(TextView(context).apply {
                        text = preview
                        textSize = Ui.TEXT_SECONDARY
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        setLineSpacing(0f, 1.1f)
                        setTextColor(appPalette.secondaryText)
                        setPaddingRelative(0, uiDp(Ui.SPACE_XS), 0, 0)
                    })
                }
                addView(TextView(context).apply {
                    text = metadata
                    textSize = Ui.TEXT_CAPTION
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    alpha = 0.85f
                    setTextColor(appPalette.secondaryText)
                    setPaddingRelative(0, uiDp(Ui.SPACE_SM), 0, 0)
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
        }.also { row -> row.layoutParams = cardListParams() }
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
        confirmDialog(
            title = getString(R.string.chat_history_delete_title),
            message = getString(R.string.chat_history_delete_message, selectedIds.size),
            positiveResource = R.string.chat_history_delete_selected,
            destructive = true,
        ) {
            historyStore.delete(selectedIds)
            leaveSelectionMode()
        }
    }

    private fun confirmClearAll() {
        if (conversations.isEmpty()) return
        confirmDialog(
            title = getString(R.string.chat_history_clear_title),
            message = getString(R.string.chat_history_clear_message),
            positiveResource = R.string.chat_history_clear_all,
            destructive = true,
        ) {
            historyStore.clear()
            leaveSelectionMode()
        }
    }

    private fun selectedConversations(): List<StoredConversation> = conversations.filter { value ->
        value.id in selectedIds
    }

    private fun copySelectedConversations() {
        val selected = selectedConversations()
        if (selected.isEmpty()) return
        val transcript = ConversationTranscriptFormatter.format(
            conversations = selected,
            userLabel = getString(R.string.chat_history_role_user),
            assistantLabel = getString(R.string.chat_history_role_assistant),
            noticeLabel = getString(R.string.chat_history_role_notice),
        )
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(getString(R.string.chat_history_clipboard_label), transcript),
        )
        showSnackbar(
            screenRoot,
            getString(R.string.chat_history_copied, selected.size),
        )
    }

    private fun beginExportSelected() {
        val selected = selectedConversations()
        if (selected.isEmpty()) return
        pendingExportIds.clear()
        pendingExportIds += selected.map(StoredConversation::id)
        val timestamp = SimpleDateFormat(EXPORT_TIMESTAMP_PATTERN, Locale.US).format(Date())
        exportPicker.launch("3-stone-ai-conversations-$timestamp.3sac")
    }

    private fun exportConversations(uri: Uri) {
        val selected = historyStore.loadAll().filter { conversation ->
            conversation.id in pendingExportIds
        }
        val result = runCatching {
            require(selected.isNotEmpty()) { "No selected conversations remain" }
            val bytes = ConversationHistoryCodec.encode(selected)
            contentResolver.openOutputStream(uri, "w")?.use { output -> output.write(bytes) }
                ?: error("The selected export document could not be opened")
        }
        showSnackbar(
            screenRoot,
            getString(
                if (result.isSuccess) R.string.chat_history_export_succeeded
                else R.string.chat_history_export_failed,
            ),
            Snackbar.LENGTH_LONG,
        )
    }

    private fun importConversations(uri: Uri) {
        val result = runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use(ConversationHistoryCodec::readBounded)
                ?: error("The selected import document could not be opened")
            historyStore.importConversations(ConversationHistoryCodec.decode(bytes))
        }
        result.onSuccess { imported ->
            renderHistory()
            showSnackbar(
                screenRoot,
                getString(
                    R.string.chat_history_import_succeeded,
                    imported.imported,
                    imported.skipped,
                ),
                Snackbar.LENGTH_LONG,
            )
        }.onFailure {
            showSnackbar(
                screenRoot,
                getString(R.string.chat_history_import_failed),
                Snackbar.LENGTH_LONG,
            )
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

    private companion object {
        const val MENU_IMPORT = 1
        const val MENU_SELECT = 2
        const val MENU_SELECT_ALL = 3
        const val MENU_COPY = 4
        const val MENU_EXPORT = 5
        const val MENU_DELETE = 6
        const val MENU_CLEAR = 7
        const val STATE_SELECTED_IDS = "selectedConversationIds"
        const val STATE_PENDING_EXPORT_IDS = "pendingConversationExportIds"
        const val STATE_SELECTION_MODE = "conversationSelectionMode"
        const val MIME_CONVERSATIONS = "application/vnd.three-stone-ai.conversations"
        const val EXPORT_TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss"
        val WHITESPACE = Regex("\\s+")
    }
}
