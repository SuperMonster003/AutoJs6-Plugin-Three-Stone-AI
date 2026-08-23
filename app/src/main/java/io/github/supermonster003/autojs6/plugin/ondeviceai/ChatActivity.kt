package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationBackend
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationRole
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationStatistics
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.LiteRtLmBackendProfile
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ImportedModel
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelHealthStatus
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelImportCoordinator
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelImportState
import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelManagerState
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/** Launcher surface for direct, private, multi-turn interaction with the selected local model. */
class ChatActivity : ConfiguredActivity() {
    private lateinit var importCoordinator: ModelImportCoordinator
    private lateinit var historyStore: ConversationHistoryStore
    private lateinit var uiSettingsStore: ChatUiSettingsStore
    private lateinit var toolbar: Toolbar
    private lateinit var modelStatus: TextView
    private lateinit var searchNavigationBar: LinearLayout
    private lateinit var searchView: SearchView
    private lateinit var searchResultCount: TextView
    private lateinit var searchPrevious: SearchActionView
    private lateinit var searchNext: SearchActionView
    private lateinit var messagesScroll: ScrollView
    private lateinit var messagesColumn: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var emptyActionButton: Button
    private lateinit var suggestionButtons: List<Button>
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var editingBar: LinearLayout
    private lateinit var editingLabel: TextView

    private val messages = ArrayList<ChatMessage>()
    private val messageViews = HashMap<Long, MessageViewHolder>()
    private val markdownCache = HashMap<Long, CachedMarkdown>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generationEpoch = AtomicLong(0L)
    private val backendLock = Any()
    private val deltaLock = Any()
    private val backendExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litertlm-chat").apply { isDaemon = true }
    }

    private var nextMessageId = 1L
    private var currentConversationId = newConversationId()
    private var conversationCreatedAtMillis = System.currentTimeMillis()
    private var conversationUpdatedAtMillis = conversationCreatedAtMillis
    private var conversationModelId: String? = null
    private var conversationModelDisplayName: String? = null
    private var uiSettings = ChatUiSettings()
    private var editingMessageId: Long? = null
    private var draftBeforeEditing: String? = null
    private var restoredComposerText: String? = null
    private var restoredSearchQuery: String? = null
    private var searchMenuItem: MenuItem? = null
    private var searchExpanded = false
    private var searchMatches: List<ConversationSearchMatch> = emptyList()
    private var currentSearchMatchIndex = -1
    private var persistenceScheduled = false
    private var allowDeletedConversationRevival = false
    private var catalogAvailability = CatalogAvailability.LOADING
    private var selectedModel: ImportedModel? = null
    private var resolvedCatalogObserved = false
    private var isGenerating = false
    private var activeGenerationId: Long? = null
    private var activeAssistantMessageId: Long? = null
    private var activeBackend: GenerationBackend? = null
    private var activeBackendModelId: String? = null
    private var completedTurnsOnBackend = 0
    private var managerAttached = false
    private var destroyed = false
    private var pendingDeltaGenerationId = 0L
    private var pendingDeltaAssistantId = 0L
    private val pendingDelta = StringBuilder()
    private var deltaFlushScheduled = false

    private val persistConversationRunnable = Runnable {
        persistenceScheduled = false
        persistConversationNow()
    }

    private val managerObserver = ModelImportCoordinator.ManagerObserver { state ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            renderManagerState(state)
        } else {
            mainHandler.post { renderManagerState(state) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importCoordinator = ModelImportCoordinator.get(applicationContext)
        historyStore = ConversationHistoryStore(applicationContext)
        uiSettingsStore = ChatUiSettingsStore(applicationContext)
        uiSettings = uiSettingsStore.load()
        if (!restoreTranscript(savedInstanceState)) {
            restoreStoredConversation(
                intent.getStringExtra(ConversationNavigation.EXTRA_CONVERSATION_ID)
                    ?: historyStore.lastConversationId(),
            )
        }
        title = getString(R.string.chat_screen_title)
        setContentView(createContentView())
        restoredComposerText?.let(input::setText)
        restoredSearchQuery?.takeIf(String::isNotBlank)?.let { query ->
            toolbar.post {
                showSearch()
                searchView.setQuery(query, false)
            }
        }
        renderEditingState()
        renderTranscript()
        renderModelUi()
        applyUiSettings()
        historyStore.rememberLastConversation(currentConversationId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val conversationId = intent.getStringExtra(ConversationNavigation.EXTRA_CONVERSATION_ID)
            ?: return
        if (conversationId != currentConversationId) switchConversation(conversationId)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_NEW_CONVERSATION, 0, R.string.chat_new_conversation).apply {
            icon = tintedDrawable(R.drawable.ic_add_24, appPalette.onPrimary)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_CONVERSATION_HISTORY, 1, R.string.chat_history_title).apply {
            icon = tintedDrawable(R.drawable.ic_history_24, appPalette.onPrimary)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        searchMenuItem = menu.add(0, MENU_SEARCH, 2, R.string.chat_search_menu).apply {
            icon = tintedDrawable(R.drawable.ic_search_24, appPalette.onPrimary)
            setShowAsAction(
                MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW,
            )
            actionView = createSearchView()
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    searchExpanded = true
                    searchNavigationBar.visibility = View.VISIBLE
                    toolbar.post { tintToolbarIcons(toolbar) }
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    clearSearchState()
                    return true
                }
            })
        }
        menu.add(0, MENU_CHAT_SETTINGS, 3, R.string.chat_settings_title)
        menu.add(0, MENU_APP_SETTINGS, 4, R.string.app_settings_title)
        menu.add(0, MENU_MODEL_SETTINGS, 5, R.string.chat_model_settings)
        toolbar.post { tintToolbarIcons(toolbar) }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_NEW_CONVERSATION)?.isEnabled = messages.isNotEmpty()
        menu.findItem(MENU_SEARCH)?.isEnabled = messages.any { message -> message.text.isNotBlank() }
        toolbar.post { tintToolbarIcons(toolbar) }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_NEW_CONVERSATION -> {
            startNewConversation()
            true
        }
        MENU_CONVERSATION_HISTORY -> {
            persistConversationNow()
            startActivity(Intent(this, ConversationHistoryActivity::class.java))
            true
        }
        MENU_SEARCH -> {
            showSearch()
            true
        }
        MENU_CHAT_SETTINGS -> {
            showChatSettings()
            true
        }
        MENU_APP_SETTINGS -> {
            startActivity(Intent(this, AppSettingsActivity::class.java))
            true
        }
        MENU_MODEL_SETTINGS -> {
            openModelManager()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        managerAttached = true
        renderManagerState(importCoordinator.attachManager(managerObserver))
    }

    override fun onStop() {
        persistConversationNow()
        if (managerAttached) {
            importCoordinator.detachManager(managerObserver)
            managerAttached = false
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persistConversationNow()
        val savedMessages = messagesForSavedState()
        outState.putLongArray(STATE_MESSAGE_IDS, savedMessages.map(ChatMessage::id).toLongArray())
        outState.putStringArrayList(
            STATE_MESSAGE_ROLES,
            ArrayList(savedMessages.map { message -> message.role.name }),
        )
        outState.putStringArrayList(
            STATE_MESSAGE_STATUSES,
            ArrayList(savedMessages.map { message -> message.status.name }),
        )
        outState.putStringArrayList(
            STATE_MESSAGE_TEXTS,
            ArrayList(savedMessages.map(ChatMessage::text)),
        )
        outState.putLongArray(
            STATE_MESSAGE_INPUT_TOKENS,
            savedMessages.map { message -> message.usage?.inputTokens ?: NO_USAGE }.toLongArray(),
        )
        outState.putLongArray(
            STATE_MESSAGE_OUTPUT_TOKENS,
            savedMessages.map { message -> message.usage?.outputTokens ?: NO_USAGE }.toLongArray(),
        )
        outState.putLongArray(
            STATE_MESSAGE_DURATION_MILLIS,
            savedMessages.map { message -> message.usage?.durationMillis ?: NO_USAGE }.toLongArray(),
        )
        outState.putLong(STATE_NEXT_MESSAGE_ID, nextMessageId)
        outState.putString(STATE_CONVERSATION_ID, currentConversationId)
        outState.putLong(STATE_CONVERSATION_CREATED_AT, conversationCreatedAtMillis)
        outState.putLong(STATE_CONVERSATION_UPDATED_AT, conversationUpdatedAtMillis)
        outState.putString(STATE_CONVERSATION_MODEL_ID, conversationModelId)
        outState.putString(STATE_CONVERSATION_MODEL_NAME, conversationModelDisplayName)
        outState.putString(STATE_COMPOSER_TEXT, input.text.toString())
        outState.putString(
            STATE_SEARCH_QUERY,
            if (::searchView.isInitialized) searchView.query.toString() else restoredSearchQuery,
        )
        editingMessageId?.let { id -> outState.putLong(STATE_EDITING_MESSAGE_ID, id) }
        outState.putString(STATE_DRAFT_BEFORE_EDITING, draftBeforeEditing)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        destroyed = true
        if (managerAttached) {
            importCoordinator.detachManager(managerObserver)
            managerAttached = false
        }
        generationEpoch.incrementAndGet()
        persistConversationNow()
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(deltaLock) {
            pendingDelta.clear()
            deltaFlushScheduled = false
        }
        val detached = detachBackend()
        if (detached != null) {
            try {
                backendExecutor.execute { runCatching(detached::close) }
            } catch (_: RejectedExecutionException) {
                closeOnFallbackThread(detached)
            }
        }
        backendExecutor.shutdown()
        super.onDestroy()
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.window_background))
        }

        toolbar = createAppToolbar(
            R.string.app_name,
            showBack = false,
        )
        root.addView(toolbar)
        root.addView(createModelBar())
        root.addView(createSearchNavigationBar())
        root.addView(View(this).apply { setBackgroundColor(getColor(R.color.divider)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        val conversationFrame = FrameLayout(this)
        messagesColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            setPaddingRelative(dp(16), dp(18), dp(16), dp(24))
        }
        messagesScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(
                messagesColumn,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        conversationFrame.addView(
            messagesScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        emptyState = createEmptyState()
        conversationFrame.addView(
            emptyState,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                marginStart = dp(28)
                marginEnd = dp(28)
            },
        )
        root.addView(
            conversationFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        root.addView(createComposer())
        applySystemBarInsets(root)
        return root
    }

    private fun createModelBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        contentDescription = getString(R.string.chat_open_model_settings)
        minimumHeight = dp(36)
        setPaddingRelative(dp(18), dp(3), dp(12), dp(3))
        background = roundedRipple(
            fillColor = R.color.window_background,
            rippleColor = R.color.chat_ripple,
            radiusDp = 0,
        )
        setOnClickListener { openModelManager() }

        addView(TextView(context).apply {
            text = "\u25cf"
            textSize = 10f
            setTextColor(appPalette.accent)
        }, LinearLayout.LayoutParams(dp(18), LinearLayout.LayoutParams.WRAP_CONTENT))
        modelStatus = TextView(context).apply {
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(getColor(R.color.text_color_secondary))
        }
        addView(modelStatus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "\u203a"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_color_secondary))
        })
    }

    private fun createSearchNavigationBar(): View = LinearLayout(this).apply {
        searchNavigationBar = this
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPaddingRelative(dp(12), dp(2), dp(8), dp(3))
        searchResultCount = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_color_secondary))
            setPaddingRelative(dp(7), 0, dp(5), 0)
        }
        addView(searchResultCount, LinearLayout.LayoutParams(dp(64), dp(38)))
        searchPrevious = searchAction(SearchActionIcon.PREVIOUS, R.string.chat_search_previous) {
            moveSearchResult(-1)
        }
        addView(searchPrevious, LinearLayout.LayoutParams(dp(40), dp(38)))
        searchNext = searchAction(SearchActionIcon.NEXT, R.string.chat_search_next) {
            moveSearchResult(1)
        }
        addView(searchNext, LinearLayout.LayoutParams(dp(40), dp(38)))
        updateSearchControls()
    }

    private fun searchAction(
        icon: SearchActionIcon,
        descriptionResource: Int,
        action: () -> Unit,
    ) = SearchActionView(this, icon, appPalette.accent).apply {
        contentDescription = getString(descriptionResource)
        applySelectableBackground(this)
        setOnClickListener { action() }
    }

    private fun createSearchView() = SearchView(this).apply {
        searchView = this
        queryHint = getString(R.string.chat_search_hint)
        maxWidth = Int.MAX_VALUE
        isSubmitButtonEnabled = false
        setIconifiedByDefault(false)
        setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                hideKeyboard(this@apply)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                refreshSearchResults(selectFirst = true, locate = true)
                return true
            }
        })
        findViewById<EditText?>(androidx.appcompat.R.id.search_src_text)?.apply {
            setTextColor(appPalette.onPrimary)
            setHintTextColor(AppColorPolicy.withAlpha(appPalette.onPrimary, 0xB3))
            tintEditText(this)
        }
        listOf(
            androidx.appcompat.R.id.search_close_btn,
            androidx.appcompat.R.id.search_mag_icon,
            androidx.appcompat.R.id.search_go_btn,
        ).forEach { identifier ->
            findViewById<ImageView?>(identifier)?.setColorFilter(appPalette.onPrimary)
        }
    }

    private fun createEmptyState(): LinearLayout {
        val firstSuggestion = suggestionButton(R.string.chat_suggestion_explain)
        val secondSuggestion = suggestionButton(R.string.chat_suggestion_code)
        val thirdSuggestion = suggestionButton(R.string.chat_suggestion_plan)
        suggestionButtons = listOf(firstSuggestion, secondSuggestion, thirdSuggestion)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPaddingRelative(dp(8), dp(24), dp(8), dp(24))

            addView(TextView(context).apply {
                text = "\u2726"
                textSize = 32f
                gravity = Gravity.CENTER
                setTextColor(appPalette.accent)
            })
            emptyTitle = TextView(context).apply {
                textSize = 22f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(getColor(R.color.text_color_primary))
                setPaddingRelative(0, dp(10), 0, dp(8))
            }
            addView(emptyTitle)
            emptyDescription = TextView(context).apply {
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_color_secondary))
                setLineSpacing(0f, 1.15f)
            }
            addView(
                emptyDescription,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            emptyActionButton = Button(context).apply {
                text = getString(R.string.chat_manage_models)
                isAllCaps = false
                setOnClickListener { openModelManager() }
            }
            addView(emptyActionButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) })
            suggestionButtons.forEach { suggestion ->
                addView(
                    suggestion,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(8) },
                )
            }
        }
    }

    private fun suggestionButton(textResource: Int) = Button(this).apply {
        text = getString(textResource)
        isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(getColor(R.color.text_color_primary))
        background = roundedRipple(
            fillColor = R.color.chat_assistant_surface,
            rippleColor = R.color.chat_ripple,
            radiusDp = 14,
            strokeColor = R.color.chat_border,
        )
        setPaddingRelative(dp(16), dp(4), dp(16), dp(4))
        setOnClickListener { sendSuggestedPrompt(text.toString()) }
    }

    private fun createComposer(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(getColor(R.color.window_background))
        addView(View(context).apply { setBackgroundColor(getColor(R.color.divider)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        editingBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPaddingRelative(dp(16), dp(7), dp(8), 0)
            editingLabel = TextView(context).apply {
                text = getString(R.string.chat_editing_message)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(appPalette.accent)
            }
            addView(
                editingLabel,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(TextView(context).apply {
                text = getString(R.string.chat_cancel_editing)
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(appPalette.accent)
                setPaddingRelative(dp(10), dp(5), dp(10), dp(5))
                applySelectableBackground(this)
                setOnClickListener { cancelEditing() }
            })
        }
        addView(
            editingBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPaddingRelative(dp(6), dp(4), dp(6), dp(4))
            background = roundedDrawableColor(
                fillColor = appPalette.inputSurface,
                radiusDp = 18,
                strokeColor = appPalette.chatBorder,
            )

            input = EditText(context).apply {
                textSize = uiSettings.fontSize.inputSp
                maxLines = 6
                minLines = 1
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions = editorImeOptions()
                filters = arrayOf(
                    InputFilter.LengthFilter(ChatConversationPolicy.MAXIMUM_INPUT_CHARACTERS),
                )
                setTextColor(getColor(R.color.text_color_primary))
                setHintTextColor(getColor(R.color.text_color_secondary))
                setPaddingRelative(dp(12), dp(9), dp(8), dp(9))
                background = null
                tintEditText(this)
                setOnEditorActionListener { _, actionId, _ ->
                    if (
                        uiSettings.enterKeyBehavior == EnterKeyBehavior.SEND &&
                        actionId == EditorInfo.IME_ACTION_SEND
                    ) {
                        sendCurrentMessage()
                        true
                    } else {
                        false
                    }
                }
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode != KeyEvent.KEYCODE_ENTER || event.action != KeyEvent.ACTION_DOWN) {
                        return@setOnKeyListener false
                    }
                    val shouldSend = when (uiSettings.enterKeyBehavior) {
                        EnterKeyBehavior.SEND -> !event.isShiftPressed
                        EnterKeyBehavior.NEW_LINE -> event.isCtrlPressed
                    }
                    if (shouldSend) {
                        sendCurrentMessage()
                        true
                    } else {
                        false
                    }
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) =
                        renderComposerState()
                    override fun afterTextChanged(value: Editable?) = Unit
                })
            }
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            sendButton = Button(context).apply {
                text = getString(R.string.chat_send)
                textSize = 14f
                isAllCaps = false
                minWidth = dp(72)
                minimumHeight = dp(46)
                setTextColor(appPalette.onPrimary)
                background = roundedRippleColor(
                    fillColor = appPalette.primary,
                    rippleColor = AppColorPolicy.withAlpha(appPalette.onPrimary, 0x40),
                    radiusDp = 14,
                )
                setOnClickListener {
                    if (isGenerating) stopGeneration() else sendCurrentMessage()
                }
            }
            addView(sendButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(46),
            ))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
            topMargin = dp(10)
        })

        setPaddingRelative(0, 0, 0, dp(8))
    }

    private fun renderManagerState(state: ModelManagerState) {
        if (destroyed) return
        val availability = when {
            state.snapshot != null -> CatalogAvailability.READY
            state.importState === ModelImportState.Preparing -> CatalogAvailability.LOADING
            else -> CatalogAvailability.UNAVAILABLE
        }
        val newlySelected = state.snapshot?.selectedModel
        val catalogResolved = availability != CatalogAvailability.LOADING
        if (catalogResolved && !resolvedCatalogObserved) {
            resolvedCatalogObserved = true
        } else if (
            catalogResolved && resolvedCatalogObserved &&
            newlySelected?.modelId != selectedModel?.modelId
        ) {
            handleSelectedModelChange(newlySelected)
        }
        selectedModel = newlySelected
        catalogAvailability = availability
        renderModelUi()
    }

    private fun handleSelectedModelChange(newModel: ImportedModel?) {
        if (isGenerating) stopGeneration(showToast = false)
        closeCurrentBackend()
        conversationModelId = newModel?.modelId
        conversationModelDisplayName = newModel?.displayName
        if (messages.isNotEmpty()) {
            val notice = newModel?.let { model ->
                getString(R.string.chat_model_changed, model.displayName)
            } ?: getString(R.string.chat_model_removed)
            appendMessage(
                ChatMessage(
                    id = allocateMessageId(),
                    role = ChatMessageRole.NOTICE,
                    text = notice,
                ),
            )
        }
    }

    private fun renderModelUi() {
        if (!::modelStatus.isInitialized) return
        val model = selectedModel
        modelStatus.text = when {
            catalogAvailability == CatalogAvailability.LOADING ->
                getString(R.string.chat_model_loading)
            catalogAvailability == CatalogAvailability.UNAVAILABLE ->
                getString(R.string.chat_model_unavailable)
            model == null -> getString(R.string.chat_no_model)
            model.healthStatus == ModelHealthStatus.AVAILABLE -> model.displayName
            else -> getString(
                R.string.chat_model_format,
                model.displayName,
                getString(
                    when (model.healthStatus) {
                        ModelHealthStatus.AVAILABLE -> error("Handled above")
                        ModelHealthStatus.INCOMPATIBLE -> R.string.model_health_incompatible
                        ModelHealthStatus.NOT_CHECKED -> R.string.model_health_not_checked
                    },
                ),
            )
        }
        renderEmptyState()
        renderComposerState()
    }

    private fun renderEmptyState() {
        if (!::emptyState.isInitialized) return
        emptyState.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
        if (messages.isNotEmpty()) return
        val modelReady = catalogAvailability == CatalogAvailability.READY && selectedModel != null
        when {
            catalogAvailability == CatalogAvailability.LOADING -> {
                emptyTitle.text = getString(R.string.chat_preparing_title)
                emptyDescription.text = getString(R.string.chat_preparing_description)
            }
            catalogAvailability == CatalogAvailability.UNAVAILABLE -> {
                emptyTitle.text = getString(R.string.chat_catalog_error_title)
                emptyDescription.text = getString(R.string.chat_catalog_error_description)
            }
            selectedModel == null -> {
                emptyTitle.text = getString(R.string.chat_no_model_title)
                emptyDescription.text = getString(R.string.chat_no_model_description)
            }
            else -> {
                emptyTitle.text = getString(R.string.chat_welcome_title)
                emptyDescription.text = getString(
                    R.string.chat_welcome_description,
                    selectedModel?.displayName.orEmpty(),
                )
            }
        }
        emptyActionButton.visibility = if (modelReady) View.GONE else View.VISIBLE
        suggestionButtons.forEach { suggestion ->
            suggestion.visibility = if (modelReady) View.VISIBLE else View.GONE
            suggestion.isEnabled = modelReady && !isGenerating
        }
    }

    private fun renderComposerState() {
        if (!::input.isInitialized || !::sendButton.isInitialized) return
        val modelReady = catalogAvailability == CatalogAvailability.READY && selectedModel != null
        input.isEnabled = modelReady && !isGenerating
        input.hint = if (modelReady) {
            getString(R.string.chat_input_hint)
        } else {
            getString(R.string.chat_input_no_model_hint)
        }
        sendButton.text = getString(
            when {
                isGenerating -> R.string.chat_stop
                editingMessageId != null -> R.string.chat_save_and_send
                else -> R.string.chat_send
            },
        )
        sendButton.isEnabled = isGenerating || (modelReady && input.text.toString().isNotBlank())
        sendButton.alpha = if (sendButton.isEnabled) 1f else DISABLED_ALPHA
        if (::suggestionButtons.isInitialized) {
            suggestionButtons.forEach { it.isEnabled = modelReady && !isGenerating }
        }
        refreshEditableMessageActions()
    }

    private fun renderTranscript() {
        messagesColumn.removeAllViews()
        messageViews.clear()
        markdownCache.keys.retainAll(messages.map(ChatMessage::id).toSet())
        messages.forEach(::addMessageView)
        invalidateOptionsMenu()
        renderEmptyState()
        if (messages.isNotEmpty()) scrollToBottom()
    }

    private fun appendMessage(message: ChatMessage) {
        messages += message
        addMessageView(message)
        markConversationChanged()
        renderEmptyState()
        refreshActiveSearchResults()
        invalidateOptionsMenu()
        scrollToBottom()
    }

    private fun replaceMessage(message: ChatMessage) {
        val index = messages.indexOfFirst { candidate -> candidate.id == message.id }
        if (index < 0) return
        messages[index] = message
        markdownCache.remove(message.id)
        updateMessageView(message)
        markConversationChanged(schedulePersistence = message.status != ChatMessageStatus.GENERATING)
        refreshActiveSearchResults()
    }

    private fun addMessageView(message: ChatMessage) {
        if (message.role == ChatMessageRole.NOTICE) {
            val notice = MarkdownMessageView(this, appPalette).apply {
                gravity = Gravity.CENTER
                setPaddingRelative(dp(12), dp(7), dp(12), dp(7))
                background = roundedDrawableColor(appPalette.noticeSurface, 12)
                showPlainText(message.text, 12f)
            }
            messagesColumn.addView(
                notice,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(6)
                    bottomMargin = dp(10)
                },
            )
            messageViews[message.id] = MessageViewHolder(notice, null)
            return
        }

        val userMessage = message.role == ChatMessageRole.USER
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (userMessage) Gravity.END else Gravity.START
        }
        val label = TextView(this).apply {
            text = getString(if (userMessage) R.string.chat_role_user else R.string.chat_role_assistant)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.text_color_secondary))
            setPaddingRelative(dp(4), 0, dp(4), dp(4))
        }
        row.addView(label)
        val body = MarkdownMessageView(this, appPalette).apply {
            maximumWidth = resources.displayMetrics.widthPixels - dp(56)
            setPaddingRelative(dp(14), dp(11), dp(14), dp(11))
            background = roundedDrawableColor(
                if (userMessage) appPalette.userSurface else appPalette.assistantSurface,
                16,
                appPalette.chatBorder,
            )
            setMessageLongClickListener { showMessageActions(message.id) }
        }
        row.addView(body)
        val meta = TextView(this).apply {
            textSize = 11f
            setTextColor(getColor(R.color.text_color_secondary))
            setPaddingRelative(dp(4), dp(4), dp(4), 0)
            if (userMessage) gravity = Gravity.END
        }
        row.addView(meta)
        messagesColumn.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(5)
                bottomMargin = dp(13)
            },
        )
        messageViews[message.id] = MessageViewHolder(body, meta)
        updateMessageView(message)
    }

    private fun updateMessageView(message: ChatMessage) {
        val holder = messageViews[message.id] ?: return
        if (message.role == ChatMessageRole.NOTICE) {
            holder.body.showPlainText(message.text, 12f)
            return
        }
        val visibleText = message.text.ifEmpty {
            getString(
                when (message.status) {
                    ChatMessageStatus.GENERATING -> R.string.chat_thinking
                    ChatMessageStatus.STOPPED -> R.string.chat_generation_stopped
                    ChatMessageStatus.FAILED -> R.string.chat_generation_failed
                    ChatMessageStatus.COMPLETE -> R.string.chat_empty_response
                },
            )
        }
        if (message.text.isNotEmpty()) {
            val document = markdownDocument(message)
            holder.body.showDocument(
                document = document,
                textSizeSp = uiSettings.fontSize.messageSp,
                highlights = searchMatches.mapIndexedNotNull { index, match ->
                    match.takeIf { candidate -> candidate.messageId == message.id }?.let {
                        MarkdownSearchHighlight(
                            start = it.start,
                            end = it.end,
                            current = index == currentSearchMatchIndex,
                        )
                    }
                },
            )
        } else {
            holder.body.showPlainText(visibleText, uiSettings.fontSize.messageSp)
        }
        holder.body.alpha = if (
            message.text.isEmpty() && message.status == ChatMessageStatus.GENERATING
        ) PLACEHOLDER_ALPHA else 1f
        holder.meta?.let { meta -> updateMessageMeta(message, meta) }
    }

    private fun markdownDocument(message: ChatMessage): MarkdownDocument {
        val cached = markdownCache[message.id]
        if (cached?.source == message.text) return cached.document
        return ConversationSearchPolicy.documentFor(message).also { document ->
            markdownCache[message.id] = CachedMarkdown(message.text, document)
        }
    }

    private fun refreshEditableMessageActions() {
        if (!::messagesColumn.isInitialized) return
        messages.filter { message -> message.role == ChatMessageRole.USER }
            .forEach { message ->
                messageViews[message.id]?.meta?.let { meta -> updateMessageMeta(message, meta) }
            }
    }

    private fun updateMessageMeta(message: ChatMessage, meta: TextView) {
        val value = if (message.role == ChatMessageRole.USER) {
            ""
        } else {
            when (message.status) {
                ChatMessageStatus.GENERATING -> getString(R.string.chat_streaming)
                ChatMessageStatus.STOPPED -> getString(R.string.chat_generation_stopped)
                ChatMessageStatus.FAILED -> getString(R.string.chat_generation_failed_short)
                ChatMessageStatus.COMPLETE -> if (uiSettings.showGenerationUsage) {
                    message.usage?.let(::formatUsage).orEmpty()
                } else {
                    ""
                }
            }
        }
        meta.text = value
        meta.isClickable = false
        meta.setTextColor(appPalette.secondaryText)
        meta.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun sendSuggestedPrompt(prompt: String) {
        if (selectedModel == null || isGenerating) return
        input.setText(prompt)
        input.setSelection(input.text.length)
        sendCurrentMessage()
    }

    private fun sendCurrentMessage() {
        if (isGenerating) return
        val model = selectedModel
        if (catalogAvailability != CatalogAvailability.READY || model == null) {
            openModelManager()
            return
        }
        val promptText = input.text.toString().trim()
        if (promptText.isEmpty()) return
        val messageBeingEdited = editingMessageId
        val retainedPrefix = messageBeingEdited?.let { messageId ->
            ConversationEditPolicy.prefixBefore(messages, messageId)
        }
        if (messageBeingEdited != null && retainedPrefix == null) {
            cancelEditing()
            return
        }
        allowDeletedConversationRevival = true
        input.text.clear()
        hideKeyboard()

        val userMessage = ChatMessage(
            id = messageBeingEdited ?: allocateMessageId(),
            role = ChatMessageRole.USER,
            text = promptText,
        )
        val assistantMessage = ChatMessage(
            id = allocateMessageId(),
            role = ChatMessageRole.ASSISTANT,
            text = "",
            status = ChatMessageStatus.GENERATING,
        )
        val generationId = generationEpoch.incrementAndGet()
        activeGenerationId = generationId
        activeAssistantMessageId = assistantMessage.id
        isGenerating = true
        conversationModelId = model.modelId
        conversationModelDisplayName = model.displayName
        if (retainedPrefix != null) {
            closeCurrentBackend()
            messages.clear()
            messages.addAll(retainedPrefix)
            messages += userMessage
            messages += assistantMessage
            markdownCache.clear()
            editingMessageId = null
            draftBeforeEditing = null
            renderEditingState()
            markConversationChanged()
            renderTranscript()
        } else {
            appendMessage(userMessage)
            appendMessage(assistantMessage)
        }
        synchronized(deltaLock) {
            pendingDeltaGenerationId = generationId
            pendingDeltaAssistantId = assistantMessage.id
            pendingDelta.clear()
            deltaFlushScheduled = false
        }
        renderComposerState()
        renderEmptyState()
        persistConversationNow()
        startBackendTurn(model, promptText, assistantMessage.id, generationId)
    }

    private fun startBackendTurn(
        model: ImportedModel,
        promptText: String,
        assistantMessageId: Long,
        generationId: Long,
    ) {
        val listener = generationListener(generationId, assistantMessageId)
        val prompt = GenerationMessage(GenerationRole.USER, listOf(promptText))
        var reusableBackend: GenerationBackend? = null
        var backendToReplace: GenerationBackend? = null
        synchronized(backendLock) {
            val reusable = activeBackend != null && activeBackendModelId == model.modelId &&
                !ChatConversationPolicy.shouldRotateBackend(completedTurnsOnBackend)
            if (reusable) {
                reusableBackend = activeBackend
            } else {
                backendToReplace = activeBackend
                activeBackend = null
                activeBackendModelId = null
                completedTurnsOnBackend = 0
            }
        }

        val continuation = reusableBackend
        if (continuation != null) {
            submitBackendWork(generationId, assistantMessageId) {
                if (!isGenerationCurrent(generationId)) return@submitBackendWork
                continuation.continueGeneration(
                    generationRequest(history = emptyList(), prompt = prompt),
                    listener,
                )
            }
            return
        }

        val history = ChatConversationPolicy.historyForFreshBackend(messages)
        submitBackendWork(generationId, assistantMessageId) {
            runCatching { backendToReplace?.close() }
            if (!isGenerationCurrent(generationId)) return@submitBackendWork
            val created = (application as OnDeviceAiApplication).engineRuntime.createBackend(
                modelSha256 = model.sha256,
                modelPath = model.file.absolutePath,
                backendProfile = LiteRtLmBackendProfile.CPU,
            )
            val installed = synchronized(backendLock) {
                if (!isGenerationCurrent(generationId) || activeBackend != null) {
                    false
                } else {
                    activeBackend = created
                    activeBackendModelId = model.modelId
                    completedTurnsOnBackend = history.size / MESSAGES_PER_TURN
                    true
                }
            }
            if (!installed) {
                runCatching(created::close)
                return@submitBackendWork
            }
            created.start(generationRequest(history, prompt), listener)
        }
    }

    private fun generationRequest(
        history: List<GenerationMessage>,
        prompt: GenerationMessage,
    ) = GenerationRequest(
        history = history,
        prompt = prompt,
        maximumOutputTokens = uiSettings.maximumOutputTokens,
        samplingOptions = uiSettings.samplingOptions(),
        reportUsage = true,
    )

    private fun generationListener(
        generationId: Long,
        assistantMessageId: Long,
    ) = object : GenerationListener {
        override fun onTextDelta(text: String) {
            queueTextDelta(generationId, assistantMessageId, text)
        }

        override fun onCompleted(statistics: GenerationStatistics?) {
            mainHandler.post {
                completeGeneration(generationId, assistantMessageId, statistics)
            }
        }

        override fun onFailed(error: Throwable, statistics: GenerationStatistics?) {
            Log.e(TAG, "Launcher chat generation failed", error)
            mainHandler.post { failGeneration(generationId, assistantMessageId) }
        }
    }

    private fun submitBackendWork(
        generationId: Long,
        assistantMessageId: Long,
        action: () -> Unit,
    ) {
        try {
            backendExecutor.execute {
                try {
                    action()
                } catch (error: Throwable) {
                    Log.e(TAG, "Launcher chat generation crashed", error)
                    mainHandler.post { failGeneration(generationId, assistantMessageId) }
                }
            }
        } catch (error: RejectedExecutionException) {
            Log.e(TAG, "Launcher chat worker rejected generation", error)
            mainHandler.post { failGeneration(generationId, assistantMessageId) }
        }
    }

    private fun queueTextDelta(generationId: Long, assistantMessageId: Long, text: String) {
        if (text.isEmpty() || !isGenerationCurrent(generationId)) return
        val schedule = synchronized(deltaLock) {
            if (
                pendingDeltaGenerationId != generationId ||
                pendingDeltaAssistantId != assistantMessageId
            ) {
                return
            }
            pendingDelta.append(text)
            if (deltaFlushScheduled) {
                false
            } else {
                deltaFlushScheduled = true
                true
            }
        }
        if (schedule) {
            mainHandler.postDelayed(
                { flushTextDelta(generationId, assistantMessageId) },
                DELTA_FLUSH_INTERVAL_MILLIS,
            )
        }
    }

    private fun flushTextDelta(generationId: Long, assistantMessageId: Long) {
        val delta = takePendingDelta(generationId, assistantMessageId)
        if (delta.isEmpty() || !isGenerationCurrent(generationId)) return
        val current = messages.singleOrNull { message -> message.id == assistantMessageId } ?: return
        if (current.status != ChatMessageStatus.GENERATING) return
        val followOutput = uiSettings.followStreamingOutput && !searchExpanded && isNearBottom()
        replaceMessage(current.copy(text = current.text + delta))
        if (followOutput) scrollToBottom()
    }

    private fun takePendingDelta(generationId: Long, assistantMessageId: Long): String =
        synchronized(deltaLock) {
            if (
                pendingDeltaGenerationId != generationId ||
                pendingDeltaAssistantId != assistantMessageId
            ) {
                return@synchronized ""
            }
            val value = pendingDelta.toString()
            pendingDelta.clear()
            deltaFlushScheduled = false
            value
        }

    private fun completeGeneration(
        generationId: Long,
        assistantMessageId: Long,
        statistics: GenerationStatistics?,
    ) {
        if (!isGenerationCurrent(generationId)) return
        flushTextDelta(generationId, assistantMessageId)
        val current = messages.singleOrNull { message -> message.id == assistantMessageId } ?: return
        replaceMessage(
            current.copy(
                status = ChatMessageStatus.COMPLETE,
                usage = statistics?.let { value ->
                    ChatMessageUsage(
                        inputTokens = value.inputTokens,
                        outputTokens = value.outputTokens,
                        durationMillis = value.durationMillis,
                    )
                },
            ),
        )
        synchronized(backendLock) {
            if (activeBackend != null) completedTurnsOnBackend++
        }
        finishGenerationUi(generationId)
        persistConversationNow()
    }

    private fun failGeneration(generationId: Long, assistantMessageId: Long) {
        if (!isGenerationCurrent(generationId)) return
        flushTextDelta(generationId, assistantMessageId)
        val current = messages.singleOrNull { message -> message.id == assistantMessageId }
        if (current != null) {
            replaceMessage(
                current.copy(
                    text = current.text.ifEmpty { getString(R.string.chat_generation_failed) },
                    status = ChatMessageStatus.FAILED,
                    usage = null,
                ),
            )
        }
        closeCurrentBackend()
        finishGenerationUi(generationId)
        persistConversationNow()
        Toast.makeText(this, R.string.chat_generation_failed_short, Toast.LENGTH_LONG).show()
    }

    private fun finishGenerationUi(generationId: Long) {
        if (activeGenerationId != generationId) return
        activeGenerationId = null
        activeAssistantMessageId = null
        isGenerating = false
        renderComposerState()
        input.requestFocus()
    }

    private fun stopGeneration(showToast: Boolean = true) {
        val generationId = activeGenerationId ?: return
        val followOutput = uiSettings.followStreamingOutput && !searchExpanded && isNearBottom()
        val assistantMessageId = activeAssistantMessageId
        if (assistantMessageId != null) {
            val delta = takePendingDelta(generationId, assistantMessageId)
            val current = messages.singleOrNull { message -> message.id == assistantMessageId }
            if (current != null) {
                replaceMessage(
                    current.copy(
                        text = current.text + delta,
                        status = ChatMessageStatus.STOPPED,
                        usage = null,
                    ),
                )
            }
        }
        generationEpoch.incrementAndGet()
        activeGenerationId = null
        activeAssistantMessageId = null
        isGenerating = false
        closeCurrentBackend()
        renderComposerState()
        if (followOutput) scrollToBottom()
        persistConversationNow()
        if (showToast) {
            Toast.makeText(this, R.string.chat_generation_stopped, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startNewConversation() {
        if (isGenerating) stopGeneration(showToast = false) else generationEpoch.incrementAndGet()
        persistConversationNow()
        closeCurrentBackend()
        messages.clear()
        messageViews.clear()
        markdownCache.clear()
        messagesColumn.removeAllViews()
        nextMessageId = 1L
        currentConversationId = newConversationId()
        conversationCreatedAtMillis = System.currentTimeMillis()
        conversationUpdatedAtMillis = conversationCreatedAtMillis
        conversationModelId = selectedModel?.modelId
        conversationModelDisplayName = selectedModel?.displayName
        allowDeletedConversationRevival = false
        cancelEditing(restoreDraft = false)
        closeSearch()
        input.text.clear()
        historyStore.rememberLastConversation(currentConversationId)
        renderEmptyState()
        renderComposerState()
    }

    private fun closeCurrentBackend() {
        val detached = detachBackend() ?: return
        try {
            backendExecutor.execute { runCatching(detached::close) }
        } catch (_: RejectedExecutionException) {
            closeOnFallbackThread(detached)
        }
    }

    private fun detachBackend(): GenerationBackend? = synchronized(backendLock) {
        activeBackend.also {
            activeBackend = null
            activeBackendModelId = null
            completedTurnsOnBackend = 0
        }
    }

    private fun closeOnFallbackThread(backend: GenerationBackend) {
        Thread({ runCatching(backend::close) }, "litertlm-chat-close").apply {
            isDaemon = true
            start()
        }
    }

    private fun isGenerationCurrent(generationId: Long): Boolean =
        !destroyed && generationEpoch.get() == generationId

    private fun requestEditMessage(messageId: Long) {
        if (isGenerating || editingMessageId != null) return
        val impact = ConversationEditPolicy.impact(messages, messageId) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_edit_warning_title)
            .setMessage(getString(R.string.chat_edit_warning_message, impact.laterMessageCount))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_edit_continue) { _, _ -> enterEditing(messageId) }
            .show()
            .also(::tintDialogButtons)
    }

    private fun enterEditing(messageId: Long) {
        val message = messages.singleOrNull { candidate -> candidate.id == messageId } ?: return
        if (message.role != ChatMessageRole.USER || isGenerating) return
        closeSearch()
        draftBeforeEditing = input.text.toString()
        editingMessageId = messageId
        input.setText(message.text)
        input.setSelection(input.text.length)
        renderEditingState()
        renderComposerState()
        input.requestFocus()
        scheduleKeyboard(input)
    }

    private fun cancelEditing(restoreDraft: Boolean = true) {
        val previousDraft = draftBeforeEditing
        editingMessageId = null
        draftBeforeEditing = null
        if (restoreDraft && previousDraft != null && ::input.isInitialized) {
            input.setText(previousDraft)
            input.setSelection(input.text.length)
        }
        renderEditingState()
        renderComposerState()
    }

    private fun renderEditingState() {
        if (!::editingBar.isInitialized) return
        editingBar.visibility = if (editingMessageId == null) View.GONE else View.VISIBLE
    }

    private fun showSearch() {
        val item = searchMenuItem ?: return
        if (!item.isActionViewExpanded) item.expandActionView()
        searchView.requestFocus()
        scheduleKeyboard(searchView.findViewById(androidx.appcompat.R.id.search_src_text))
    }

    private fun closeSearch() {
        val item = searchMenuItem
        if (item?.isActionViewExpanded == true) item.collapseActionView() else clearSearchState()
    }

    private fun clearSearchState() {
        if (!::searchView.isInitialized) return
        val ownedKeyboardFocus = searchView.hasFocus()
        searchExpanded = false
        searchNavigationBar.visibility = View.GONE
        searchView.setQuery("", false)
        searchView.clearFocus()
        searchMatches = emptyList()
        currentSearchMatchIndex = -1
        updateSearchControls()
        messages.forEach(::updateMessageView)
        if (ownedKeyboardFocus) hideKeyboard(searchView)
    }

    private fun refreshSearchResults(selectFirst: Boolean, locate: Boolean) {
        if (!::searchView.isInitialized || !::messagesColumn.isInitialized) return
        val previous = searchMatches.getOrNull(currentSearchMatchIndex)
        val refreshed = ConversationSearchPolicy.find(messages, searchView.query.toString())
        searchMatches = refreshed
        currentSearchMatchIndex = when {
            refreshed.isEmpty() -> -1
            selectFirst -> 0
            previous != null && previous in refreshed -> refreshed.indexOf(previous)
            else -> currentSearchMatchIndex.coerceIn(0, refreshed.lastIndex)
        }
        updateSearchControls()
        messages.forEach(::updateMessageView)
        if (locate && currentSearchMatchIndex >= 0) locateCurrentSearchResult()
    }

    private fun refreshActiveSearchResults() {
        if (
            ::searchView.isInitialized && searchExpanded && searchView.query.isNotBlank()
        ) {
            refreshSearchResults(selectFirst = false, locate = false)
        }
    }

    private fun updateSearchControls() {
        if (!::searchResultCount.isInitialized) return
        val displayedIndex = if (currentSearchMatchIndex >= 0) currentSearchMatchIndex + 1 else 0
        searchResultCount.text = getString(
            R.string.chat_search_result_count,
            displayedIndex,
            searchMatches.size,
        )
        val hasResults = searchMatches.isNotEmpty()
        searchPrevious.isEnabled = hasResults
        searchNext.isEnabled = hasResults
        searchPrevious.alpha = if (hasResults) 1f else DISABLED_ALPHA
        searchNext.alpha = if (hasResults) 1f else DISABLED_ALPHA
    }

    private fun moveSearchResult(offset: Int) {
        if (searchMatches.isEmpty()) return
        currentSearchMatchIndex = (
            currentSearchMatchIndex.coerceAtLeast(0) + offset + searchMatches.size
            ) % searchMatches.size
        updateSearchControls()
        messages.forEach(::updateMessageView)
        locateCurrentSearchResult()
    }

    private fun locateCurrentSearchResult() {
        val match = searchMatches.getOrNull(currentSearchMatchIndex) ?: return
        messageViews[match.messageId]?.body?.locate(match.start)
    }

    private fun showChatSettings() {
        val workingSettings = uiSettings
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(12), dp(4), dp(12), dp(12))
        }
        val displaySection = settingsSection(R.string.chat_settings_display_section)
        container.addView(displaySection)
        displaySection.addView(settingsFieldLabel(R.string.chat_font_size))
        val fontGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        ChatFontSize.entries.forEach { fontSize ->
            fontGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = fontSize
                text = getString(fontSize.labelResource())
                isChecked = fontSize == workingSettings.fontSize
                setTextColor(appPalette.primaryText)
                buttonTintList = controlTintList()
                minimumHeight = dp(52)
            })
        }
        displaySection.addView(fontGroup)
        val followOutput = CheckBox(this).apply {
            text = getString(R.string.chat_follow_streaming_output)
            isChecked = workingSettings.followStreamingOutput
            setTextColor(appPalette.primaryText)
            buttonTintList = controlTintList()
            minimumHeight = dp(52)
        }
        displaySection.addView(followOutput)
        val showUsage = CheckBox(this).apply {
            text = getString(R.string.chat_show_generation_usage)
            isChecked = workingSettings.showGenerationUsage
            setTextColor(appPalette.primaryText)
            buttonTintList = controlTintList()
            minimumHeight = dp(52)
        }
        displaySection.addView(showUsage)

        val inputSection = settingsSection(R.string.chat_settings_input_section)
        container.addView(inputSection)
        inputSection.addView(settingsFieldLabel(R.string.chat_enter_key_behavior))
        val enterKeyGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        EnterKeyBehavior.entries.forEach { behavior ->
            enterKeyGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = behavior
                text = getString(
                    when (behavior) {
                        EnterKeyBehavior.SEND -> R.string.chat_enter_key_send
                        EnterKeyBehavior.NEW_LINE -> R.string.chat_enter_key_new_line
                    },
                )
                isChecked = behavior == workingSettings.enterKeyBehavior
                setTextColor(appPalette.primaryText)
                buttonTintList = controlTintList()
                minimumHeight = dp(52)
            })
        }
        inputSection.addView(enterKeyGroup)

        val generationSection = settingsSection(R.string.chat_settings_generation_section)
        container.addView(generationSection)
        val unlimitedTokens = CheckBox(this).apply {
            text = getString(R.string.chat_maximum_output_tokens_unlimited)
            isChecked = workingSettings.maximumOutputTokens == null
            setTextColor(appPalette.primaryText)
            buttonTintList = controlTintList()
            minimumHeight = dp(52)
        }
        generationSection.addView(unlimitedTokens)
        generationSection.addView(settingsFieldLabel(R.string.chat_maximum_output_tokens))
        val maximumTokensInput = settingsEditText(
            workingSettings.maximumOutputTokensDraft.toString(),
            InputType.TYPE_CLASS_NUMBER,
        )
        generationSection.addView(maximumTokensInput)
        maximumTokensInput.isEnabled = !unlimitedTokens.isChecked
        maximumTokensInput.alpha = if (maximumTokensInput.isEnabled) 1f else DISABLED_ALPHA
        unlimitedTokens.setOnCheckedChangeListener { _, checked ->
            maximumTokensInput.isEnabled = !checked
            maximumTokensInput.alpha = if (checked) DISABLED_ALPHA else 1f
        }

        val useModelSamplingDefaults = CheckBox(this).apply {
            text = getString(R.string.chat_use_model_sampling_defaults)
            isChecked = workingSettings.useModelSamplingDefaults
            setTextColor(appPalette.primaryText)
            buttonTintList = controlTintList()
            minimumHeight = dp(52)
            setPaddingRelative(0, dp(8), 0, 0)
        }
        generationSection.addView(useModelSamplingDefaults)
        generationSection.addView(settingsFieldLabel(R.string.chat_temperature))
        val temperatureInput = settingsEditText(
            workingSettings.temperature.toString(),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
        )
        generationSection.addView(temperatureInput)
        generationSection.addView(settingsFieldLabel(R.string.chat_top_k))
        val topKInput = settingsEditText(
            workingSettings.topK.toString(),
            InputType.TYPE_CLASS_NUMBER,
        )
        generationSection.addView(topKInput)
        generationSection.addView(settingsFieldLabel(R.string.chat_top_p))
        val topPInput = settingsEditText(
            workingSettings.topP.toString(),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
        )
        generationSection.addView(topPInput)
        generationSection.addView(TextView(this).apply {
            text = getString(R.string.chat_generation_settings_help)
            textSize = 12f
            setTextColor(appPalette.secondaryText)
            setPaddingRelative(0, dp(7), 0, dp(8))
            setLineSpacing(0f, 1.1f)
        })
        val samplingInputs = listOf(temperatureInput, topKInput, topPInput)
        fun updateSamplingInputState(useDefaults: Boolean) {
            samplingInputs.forEach { field ->
                field.isEnabled = !useDefaults
                field.alpha = if (useDefaults) DISABLED_ALPHA else 1f
            }
        }
        updateSamplingInputState(useModelSamplingDefaults.isChecked)
        useModelSamplingDefaults.setOnCheckedChangeListener { _, checked ->
            updateSamplingInputState(checked)
        }

        val settingsScroll = ScrollView(this).apply {
            setBackgroundColor(appPalette.windowBackground)
            addView(
                container,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                minOf((resources.displayMetrics.heightPixels * 0.68f).toInt(), dp(560)),
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.chat_settings_title)
            .setView(settingsScroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_settings_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                minOf((resources.displayMetrics.widthPixels * 0.94f).toInt(), dp(720)),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(appPalette.accent)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(appPalette.accent)
            applyThemeToControls(container)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedFont = fontGroup.findViewById<RadioButton>(fontGroup.checkedRadioButtonId)
                    ?.tag as? ChatFontSize ?: workingSettings.fontSize
                val selectedEnterBehavior = enterKeyGroup.findViewById<RadioButton>(
                    enterKeyGroup.checkedRadioButtonId,
                )?.tag as? EnterKeyBehavior ?: workingSettings.enterKeyBehavior
                val maximumTokens = if (unlimitedTokens.isChecked) {
                    null
                } else {
                    maximumTokensInput.text.toString().toIntOrNull()?.takeIf { it > 0 }
                        ?: return@setOnClickListener showSettingError(
                            maximumTokensInput,
                            R.string.chat_positive_integer_error,
                        )
                }
                val maximumTokensDraft = maximumTokens
                    ?: maximumTokensInput.text.toString().toIntOrNull()?.takeIf { it > 0 }
                    ?: workingSettings.maximumOutputTokensDraft
                val useDefaults = useModelSamplingDefaults.isChecked
                val temperature = if (useDefaults) {
                    workingSettings.temperature
                } else {
                    temperatureInput.text.toString().toDoubleOrNull()
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                        ?: return@setOnClickListener showSettingError(
                            temperatureInput,
                            R.string.chat_nonnegative_number_error,
                        )
                }
                val topK = if (useDefaults) {
                    workingSettings.topK
                } else {
                    topKInput.text.toString().toIntOrNull()?.takeIf { it > 0 }
                        ?: return@setOnClickListener showSettingError(
                            topKInput,
                            R.string.chat_positive_integer_error,
                        )
                }
                val topP = if (useDefaults) {
                    workingSettings.topP
                } else {
                    topPInput.text.toString().toDoubleOrNull()
                        ?.takeIf { it.isFinite() && it in 0.0..1.0 }
                        ?: return@setOnClickListener showSettingError(
                            topPInput,
                            R.string.chat_probability_error,
                        )
                }
                uiSettings = ChatUiSettings(
                    fontSize = selectedFont,
                    followStreamingOutput = followOutput.isChecked,
                    showGenerationUsage = showUsage.isChecked,
                    enterKeyBehavior = selectedEnterBehavior,
                    maximumOutputTokens = maximumTokens,
                    maximumOutputTokensDraft = maximumTokensDraft,
                    useModelSamplingDefaults = useDefaults,
                    temperature = temperature,
                    topK = topK,
                    topP = topP,
                )
                uiSettingsStore.save(uiSettings)
                applyUiSettings()
                dialog.dismiss()
            }
        }
        dialog.show()
        tintDialogButtons(dialog)
    }

    private fun settingsSection(textResource: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedDrawableColor(
            fillColor = appPalette.assistantSurface,
            radiusDp = 14,
            strokeColor = appPalette.chatBorder,
        )
        setPaddingRelative(dp(16), dp(6), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            setMargins(0, dp(8), 0, dp(4))
        }
        addView(TextView(this@ChatActivity).apply {
            text = getString(textResource)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(appPalette.accent)
            setPaddingRelative(0, dp(6), 0, dp(7))
        })
    }

    private fun settingsFieldLabel(textResource: Int) = TextView(this).apply {
        text = getString(textResource)
        textSize = 12.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(appPalette.secondaryText)
        setPaddingRelative(0, dp(9), 0, dp(3))
    }

    private fun settingsEditText(value: String, fieldInputType: Int) = EditText(this).apply {
        setText(value)
        setSelection(text.length)
        inputType = fieldInputType
        maxLines = 1
        setSingleLine(true)
        setTextColor(appPalette.primaryText)
        setHintTextColor(appPalette.secondaryText)
        backgroundTintList = ColorStateList.valueOf(appPalette.accent)
        setPaddingRelative(dp(4), dp(5), dp(4), dp(5))
    }

    private fun showSettingError(field: EditText, messageResource: Int) {
        field.error = getString(messageResource)
        field.requestFocus()
    }

    private fun ChatFontSize.labelResource(): Int = when (this) {
        ChatFontSize.SMALL -> R.string.chat_font_size_small
        ChatFontSize.DEFAULT -> R.string.chat_font_size_default
        ChatFontSize.LARGE -> R.string.chat_font_size_large
        ChatFontSize.EXTRA_LARGE -> R.string.chat_font_size_extra_large
    }

    private fun applyUiSettings() {
        if (::input.isInitialized) {
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSettings.fontSize.inputSp)
            input.imeOptions = editorImeOptions()
            getSystemService(InputMethodManager::class.java).restartInput(input)
        }
        messageViews.values.forEach { holder ->
            holder.body.setMessageTextSize(uiSettings.fontSize.messageSp)
        }
        messages.forEach(::updateMessageView)
        if (currentSearchMatchIndex >= 0) locateCurrentSearchResult()
    }

    private fun editorImeOptions(): Int = when (uiSettings.enterKeyBehavior) {
        EnterKeyBehavior.SEND -> EditorInfo.IME_ACTION_SEND
        EnterKeyBehavior.NEW_LINE -> EditorInfo.IME_ACTION_NONE
    }

    private fun markConversationChanged(schedulePersistence: Boolean = true) {
        conversationUpdatedAtMillis = System.currentTimeMillis().coerceAtLeast(
            conversationCreatedAtMillis,
        )
        if (schedulePersistence && !persistenceScheduled) {
            persistenceScheduled = true
            mainHandler.postDelayed(persistConversationRunnable, PERSISTENCE_DELAY_MILLIS)
        }
    }

    private fun persistConversationNow() {
        mainHandler.removeCallbacks(persistConversationRunnable)
        persistenceScheduled = false
        if (messages.isEmpty()) return
        val conversation = StoredConversation(
            id = currentConversationId,
            title = ConversationHistoryPolicy.titleFor(
                messages,
                getString(R.string.chat_history_untitled),
            ),
            createdAtMillis = conversationCreatedAtMillis,
            updatedAtMillis = conversationUpdatedAtMillis,
            modelId = conversationModelId,
            modelDisplayName = conversationModelDisplayName,
            messages = messages.toList(),
        )
        val persisted = runCatching {
            historyStore.upsert(
                conversation,
                reviveDeleted = allowDeletedConversationRevival,
            )
        }
            .onFailure { error -> Log.e(TAG, "Unable to persist launcher conversation", error) }
            .getOrDefault(false)
        if (persisted) allowDeletedConversationRevival = false
    }

    private fun restoreStoredConversation(conversationId: String?): Boolean {
        val stored = historyStore.find(conversationId) ?: return false
        applyStoredConversation(stored)
        return true
    }

    private fun applyStoredConversation(conversation: StoredConversation) {
        messages.clear()
        messages += ChatConversationPolicy.restore(conversation.messages)
        currentConversationId = conversation.id
        conversationCreatedAtMillis = conversation.createdAtMillis
        conversationUpdatedAtMillis = conversation.updatedAtMillis
        conversationModelId = conversation.modelId
        conversationModelDisplayName = conversation.modelDisplayName
        nextMessageId = (messages.maxOfOrNull(ChatMessage::id) ?: 0L) + 1L
        markdownCache.clear()
        allowDeletedConversationRevival = false
        historyStore.rememberLastConversation(currentConversationId)
    }

    private fun switchConversation(conversationId: String) {
        val target = historyStore.find(conversationId)
        if (target == null) {
            Toast.makeText(this, R.string.chat_history_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        if (isGenerating) stopGeneration(showToast = false) else persistConversationNow()
        closeCurrentBackend()
        cancelEditing(restoreDraft = false)
        closeSearch()
        input.text.clear()
        applyStoredConversation(target)
        renderTranscript()
        renderModelUi()
        applyUiSettings()
    }

    private fun showKeyboard(target: View) {
        getSystemService(InputMethodManager::class.java).showSoftInput(
            target,
            InputMethodManager.SHOW_IMPLICIT,
        )
    }

    private fun scheduleKeyboard(target: View) {
        mainHandler.postDelayed(
            {
                if (!destroyed && target.isAttachedToWindow && target.hasFocus()) {
                    showKeyboard(target)
                }
            },
            KEYBOARD_REQUEST_DELAY_MILLIS,
        )
    }

    private fun openModelManager() {
        startActivity(Intent(this, ModelManagerActivity::class.java))
    }

    private fun copyMessage(messageId: Long): Boolean {
        val message = messages.singleOrNull { candidate -> candidate.id == messageId } ?: return false
        if (message.text.isEmpty()) return false
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(getString(R.string.chat_clipboard_label), message.text),
        )
        Toast.makeText(this, R.string.chat_message_copied, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun showMessageActions(messageId: Long) {
        val message = messages.singleOrNull { candidate -> candidate.id == messageId } ?: return
        if (message.text.isEmpty()) return
        val actions = when (message.role) {
            ChatMessageRole.USER -> intArrayOf(
                R.string.chat_message_copy,
                R.string.chat_message_edit,
            )
            ChatMessageRole.ASSISTANT -> intArrayOf(
                R.string.chat_message_copy,
                R.string.chat_message_regenerate,
            )
            ChatMessageRole.NOTICE -> return
        }
        AlertDialog.Builder(this)
            .setItems(actions.map(::getString).toTypedArray()) { _, index ->
                when (index) {
                    0 -> copyMessage(messageId)
                    1 -> when (message.role) {
                        ChatMessageRole.USER -> requestEditMessage(messageId)
                        ChatMessageRole.ASSISTANT -> requestRegenerateMessage(messageId)
                        ChatMessageRole.NOTICE -> Unit
                    }
                }
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun requestRegenerateMessage(messageId: Long) {
        val impact = ConversationRegenerationPolicy.impact(messages, messageId) ?: return
        if (impact.laterMessageCount == 0) {
            regenerateMessage(impact.userMessageId)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.chat_regenerate_warning_title)
            .setMessage(
                getString(R.string.chat_regenerate_warning_message, impact.laterMessageCount),
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_message_regenerate) { _, _ ->
                regenerateMessage(impact.userMessageId)
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun regenerateMessage(userMessageId: Long) {
        val userMessage = messages.singleOrNull { message -> message.id == userMessageId }
            ?.takeIf { message -> message.role == ChatMessageRole.USER }
            ?: return
        if (isGenerating) stopGeneration(showToast = false)
        draftBeforeEditing = null
        editingMessageId = userMessage.id
        input.setText(userMessage.text)
        input.setSelection(input.text.length)
        sendCurrentMessage()
    }

    private fun formatUsage(usage: ChatMessageUsage): String {
        val duration = if (usage.durationMillis < MILLIS_PER_SECOND) {
            getString(R.string.chat_duration_milliseconds, usage.durationMillis)
        } else {
            getString(
                R.string.chat_duration_seconds,
                String.format(Locale.getDefault(), "%.1f", usage.durationMillis / 1_000.0),
            )
        }
        return getString(
            R.string.chat_usage_format,
            usage.inputTokens,
            usage.outputTokens,
            duration,
        )
    }

    private fun restoreTranscript(state: Bundle?): Boolean {
        if (state == null) return false
        restoredComposerText = state.getString(STATE_COMPOSER_TEXT)
        restoredSearchQuery = state.getString(STATE_SEARCH_QUERY)
        draftBeforeEditing = state.getString(STATE_DRAFT_BEFORE_EDITING)
        editingMessageId = if (state.containsKey(STATE_EDITING_MESSAGE_ID)) {
            state.getLong(STATE_EDITING_MESSAGE_ID)
        } else {
            null
        }
        val storedId = state.getString(STATE_CONVERSATION_ID)
        val stored = historyStore.find(storedId)
        if (stored != null) {
            applyStoredConversation(stored)
            if (messages.none { message -> message.id == editingMessageId }) editingMessageId = null
            return true
        }

        val ids = state.getLongArray(STATE_MESSAGE_IDS) ?: return false
        val roles = state.getStringArrayList(STATE_MESSAGE_ROLES) ?: return false
        val statuses = state.getStringArrayList(STATE_MESSAGE_STATUSES) ?: return false
        val texts = state.getStringArrayList(STATE_MESSAGE_TEXTS) ?: return false
        val inputTokens = state.getLongArray(STATE_MESSAGE_INPUT_TOKENS) ?: return false
        val outputTokens = state.getLongArray(STATE_MESSAGE_OUTPUT_TOKENS) ?: return false
        val durations = state.getLongArray(STATE_MESSAGE_DURATION_MILLIS) ?: return false
        if (
            listOf(roles.size, statuses.size, texts.size, inputTokens.size, outputTokens.size, durations.size)
                .any { size -> size != ids.size }
        ) {
            return false
        }
        val restored = runCatching {
            ids.indices.map { index ->
                val usage = if (
                    inputTokens[index] == NO_USAGE || outputTokens[index] == NO_USAGE ||
                    durations[index] == NO_USAGE
                ) {
                    null
                } else {
                    ChatMessageUsage(inputTokens[index], outputTokens[index], durations[index])
                }
                ChatMessage(
                    id = ids[index],
                    role = ChatMessageRole.valueOf(roles[index]),
                    text = texts[index],
                    status = ChatMessageStatus.valueOf(statuses[index]),
                    usage = usage,
                )
            }.also { values ->
                require(values.map(ChatMessage::id).distinct().size == values.size)
            }
        }.getOrNull() ?: return false
        messages += ChatConversationPolicy.restore(restored)
        val minimumNextId = (messages.maxOfOrNull(ChatMessage::id) ?: 0L) + 1L
        nextMessageId = state.getLong(STATE_NEXT_MESSAGE_ID, minimumNextId).coerceAtLeast(minimumNextId)
        currentConversationId = storedId?.takeIf(String::isNotBlank) ?: currentConversationId
        conversationCreatedAtMillis = state.getLong(
            STATE_CONVERSATION_CREATED_AT,
            conversationCreatedAtMillis,
        ).coerceAtLeast(0L)
        conversationUpdatedAtMillis = state.getLong(
            STATE_CONVERSATION_UPDATED_AT,
            conversationCreatedAtMillis,
        ).coerceAtLeast(conversationCreatedAtMillis)
        conversationModelId = state.getString(STATE_CONVERSATION_MODEL_ID)
        conversationModelDisplayName = state.getString(STATE_CONVERSATION_MODEL_NAME)
        if (messages.none { message -> message.id == editingMessageId }) editingMessageId = null
        return true
    }

    private fun messagesForSavedState(): List<ChatMessage> {
        val retained = ArrayList<ChatMessage>()
        var characters = 0
        for (message in messages.asReversed()) {
            if (
                retained.size >= MAXIMUM_SAVED_MESSAGES ||
                characters + message.text.length > MAXIMUM_SAVED_CHARACTERS
            ) {
                break
            }
            retained += message
            characters += message.text.length
        }
        return retained.asReversed()
    }

    private fun allocateMessageId(): Long {
        check(nextMessageId > 0L) { "Chat message IDs are exhausted" }
        return nextMessageId.also { current ->
            nextMessageId = if (current == Long.MAX_VALUE) 0L else current + 1L
        }
    }

    private fun isNearBottom(): Boolean {
        val content = messagesScroll.getChildAt(0) ?: return true
        val remaining = content.height - messagesScroll.height - messagesScroll.scrollY
        return remaining <= dp(NEAR_BOTTOM_DP)
    }

    private fun scrollToBottom() {
        messagesScroll.post {
            val content = messagesScroll.getChildAt(0) ?: return@post
            messagesScroll.smoothScrollTo(
                0,
                (content.height - messagesScroll.height).coerceAtLeast(0),
            )
        }
    }

    private fun hideKeyboard(target: View = input) {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(
            target.windowToken,
            0,
        )
        target.clearFocus()
    }

    private fun roundedDrawable(
        fillColor: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(getColor(fillColor))
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { color -> setStroke(dp(1), getColor(color)) }
    }

    private fun roundedDrawableColor(
        fillColor: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fillColor)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { color -> setStroke(dp(1), color) }
    }

    private fun roundedRipple(
        fillColor: Int,
        rippleColor: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
    ) = RippleDrawable(
        ColorStateList.valueOf(getColor(rippleColor)),
        roundedDrawable(fillColor, radiusDp, strokeColor),
        null,
    )

    private fun roundedRippleColor(
        fillColor: Int,
        rippleColor: Int,
        radiusDp: Int,
    ) = RippleDrawable(
        ColorStateList.valueOf(rippleColor),
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dp(radiusDp).toFloat()
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

    private data class MessageViewHolder(
        val body: MarkdownMessageView,
        val meta: TextView?,
    )

    private data class CachedMarkdown(
        val source: String,
        val document: MarkdownDocument,
    )

    private enum class CatalogAvailability {
        LOADING,
        UNAVAILABLE,
        READY,
    }

    private companion object {
        const val TAG = "OnDeviceAiChat"
        const val MENU_NEW_CONVERSATION = 1
        const val MENU_MODEL_SETTINGS = 2
        const val MENU_CONVERSATION_HISTORY = 3
        const val MENU_SEARCH = 4
        const val MENU_CHAT_SETTINGS = 5
        const val MENU_APP_SETTINGS = 6
        const val MESSAGES_PER_TURN = 2
        const val MILLIS_PER_SECOND = 1_000L
        const val DELTA_FLUSH_INTERVAL_MILLIS = 32L
        const val PERSISTENCE_DELAY_MILLIS = 750L
        const val KEYBOARD_REQUEST_DELAY_MILLIS = 180L
        const val NEAR_BOTTOM_DP = 96
        const val DISABLED_ALPHA = 0.42f
        const val PLACEHOLDER_ALPHA = 0.65f
        const val NO_USAGE = -1L
        const val MAXIMUM_SAVED_MESSAGES = 48
        const val MAXIMUM_SAVED_CHARACTERS = 128 * 1_024
        const val STATE_MESSAGE_IDS = "chatMessageIds"
        const val STATE_MESSAGE_ROLES = "chatMessageRoles"
        const val STATE_MESSAGE_STATUSES = "chatMessageStatuses"
        const val STATE_MESSAGE_TEXTS = "chatMessageTexts"
        const val STATE_MESSAGE_INPUT_TOKENS = "chatMessageInputTokens"
        const val STATE_MESSAGE_OUTPUT_TOKENS = "chatMessageOutputTokens"
        const val STATE_MESSAGE_DURATION_MILLIS = "chatMessageDurationMillis"
        const val STATE_NEXT_MESSAGE_ID = "chatNextMessageId"
        const val STATE_CONVERSATION_ID = "chatConversationId"
        const val STATE_CONVERSATION_CREATED_AT = "chatConversationCreatedAt"
        const val STATE_CONVERSATION_UPDATED_AT = "chatConversationUpdatedAt"
        const val STATE_CONVERSATION_MODEL_ID = "chatConversationModelId"
        const val STATE_CONVERSATION_MODEL_NAME = "chatConversationModelName"
        const val STATE_COMPOSER_TEXT = "chatComposerText"
        const val STATE_SEARCH_QUERY = "chatSearchQuery"
        const val STATE_EDITING_MESSAGE_ID = "chatEditingMessageId"
        const val STATE_DRAFT_BEFORE_EDITING = "chatDraftBeforeEditing"

        fun newConversationId(): String = UUID.randomUUID().toString()
    }
}
