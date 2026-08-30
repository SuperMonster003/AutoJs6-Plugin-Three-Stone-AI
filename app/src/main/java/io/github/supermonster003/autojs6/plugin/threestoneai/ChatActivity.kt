package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
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
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AbsListView
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.accentRipple
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.accentTone
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.boundedRipple
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.filledButton
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedRippleFill
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import io.github.supermonster003.autojs6.plugin.threestoneai.download.AvailableLiteRtModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportState
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelManagerState
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProviderCatalog
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/** Launcher surface for explicit, multi-turn interaction with one conversation-bound AI target. */
class ChatActivity : ConfiguredActivity() {
    private lateinit var importCoordinator: ModelImportCoordinator
    private lateinit var historyStore: ConversationHistoryStore
    private lateinit var uiSettingsStore: ChatUiSettingsStore
    private lateinit var draftStore: ChatComposerDraftStore
    private lateinit var contextTokenCalibrationStore: ContextTokenCalibrationStore
    private lateinit var updateController: AppUpdateController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerHistoryColumn: LinearLayout
    private lateinit var toolbar: Toolbar
    private lateinit var targetLocalityStatus: TextView
    private lateinit var targetStatus: TextView
    private lateinit var searchInput: EditText
    private lateinit var messagesScroll: ScrollView
    private lateinit var messagesColumn: LinearLayout
    private lateinit var emptyStateScroll: ScrollView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var emptyActionButton: View
    private lateinit var suggestionButtons: List<Button>
    private lateinit var input: EditText
    private lateinit var sendButton: android.widget.ImageButton
    private lateinit var scrollToBottomButton: android.widget.ImageButton
    private lateinit var editingBar: LinearLayout
    private lateinit var editingLabel: TextView
    private var pendingSearchRefresh: Runnable? = null

    private val messages = ArrayList<ChatMessage>()
    private val messageViews = HashMap<Long, MessageViewHolder>()
    private val markdownCache = HashMap<Long, CachedMarkdown>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generationEpoch = AtomicLong(0L)
    private val backendLock = Any()
    private val deltaLock = Any()
    private val backendExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "three-stone-ai-chat").apply { isDaemon = true }
    }

    private var nextMessageId = 1L
    private var currentConversationId = newConversationId()
    private var conversationCreatedAtMillis = System.currentTimeMillis()
    private var conversationUpdatedAtMillis = conversationCreatedAtMillis
    private var conversationTarget: ConversationTargetSnapshot? = null
    private var uiSettings = ChatUiSettings()
    private var editingMessageId: Long? = null
    private var draftBeforeEditing: String? = null
    private var restoredComposerText: String? = null
    private var restoredSearchQuery: String? = null
    private var pendingDrawerExportId: String? = null
    private var searchMenuItem: MenuItem? = null
    private var searchExpanded = false
    private var searchMatches: List<ConversationSearchMatch> = emptyList()
    private var currentSearchMatchIndex = -1
    private var persistenceScheduled = false
    private var allowDeletedConversationRevival = false
    private var targetCatalogAvailability = TargetCatalogAvailability.LOADING
    private var targetCatalog: AiTargetCatalog? = null
    private var isGenerating = false
    private var activeGenerationId: Long? = null
    private var activeAssistantMessageId: Long? = null
    private var activeBackend: AiBackendSession? = null
    private var activeBackendTarget: ConversationTargetSnapshot? = null
    private var activeBackendContextTelemetry: BackendContextTelemetry? = null
    private var activeContextTurnObservation: ContextTurnObservation? = null
    private var backendContextEpoch = 0L
    private var completedTurnsOnBackend = 0
    private var managerAttached = false
    private var destroyed = false
    private var pendingDeltaGenerationId = 0L
    private var pendingDeltaAssistantId = 0L
    private val pendingDelta = StringBuilder()
    private var deltaFlushScheduled = false
    private val drawerExportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_CONVERSATIONS),
    ) { uri ->
        if (uri != null) exportDrawerConversation(uri)
        pendingDrawerExportId = null
    }

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
        draftStore = ChatComposerDraftStore(applicationContext)
        contextTokenCalibrationStore = ContextTokenCalibrationStore(applicationContext)
        uiSettings = uiSettingsStore.load()
        pendingDrawerExportId = savedInstanceState?.getString(STATE_PENDING_DRAWER_EXPORT_ID)
        updateController = AppUpdateController(this)
        if (!restoreTranscript(savedInstanceState)) {
            restoreStoredConversation(
                intent.getStringExtra(ConversationNavigation.EXTRA_CONVERSATION_ID)
                    ?: historyStore.lastConversationId(),
            )
        }
        if (savedInstanceState == null) restoredComposerText = draftStore.load()
        title = getString(R.string.chat_screen_title)
        setContentView(createContentView())
        installBackNavigation()
        restoredComposerText?.let(input::setText)
        restoredSearchQuery?.takeIf(String::isNotBlank)?.let { query ->
            toolbar.post {
                showSearch()
                searchInput.setText(query)
                searchInput.setSelection(searchInput.text.length)
            }
        }
        renderEditingState()
        renderTranscript()
        renderTargetUi()
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
            icon = tintedDrawable(R.drawable.ic_add_24, appPalette.primaryText)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_CONVERSATION_HISTORY, 1, R.string.chat_history_title).apply {
            icon = tintedDrawable(R.drawable.ic_history_24, appPalette.primaryText)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        searchMenuItem = menu.add(0, MENU_SEARCH, 2, R.string.chat_search_menu).apply {
            icon = tintedDrawable(R.drawable.ic_search_24, appPalette.primaryText)
            actionView = createSearchActionView()
            setShowAsAction(
                MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW,
            )
            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    searchExpanded = true
                    updateSearchControls()
                    toolbar.post {
                        tintToolbarIcons(toolbar)
                        focusSearchInput()
                    }
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    clearSearchState()
                    toolbar.post { tintToolbarIcons(toolbar) }
                    return true
                }
            })
        }
        menu.add(0, MENU_MODEL_SETTINGS, 3, R.string.model_manager_title)
        menu.add(0, MENU_APP_SETTINGS, 4, R.string.app_settings_title)
        toolbar.post { tintToolbarIcons(toolbar) }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        renderOptionsMenuState(menu)
        toolbar.post { tintToolbarIcons(toolbar) }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun renderOptionsMenuState(menu: Menu) {
        val hasSearchResults = searchMatches.isNotEmpty()
        menu.findItem(MENU_NEW_CONVERSATION)?.apply {
            title = getString(
                if (searchExpanded) R.string.chat_search_previous else R.string.chat_new_conversation,
            )
            icon = tintedDrawable(
                if (searchExpanded) R.drawable.ic_arrow_up_24 else R.drawable.ic_add_24,
                appPalette.primaryText,
            )
            isEnabled = if (searchExpanded) hasSearchResults else messages.isNotEmpty()
        }
        menu.findItem(MENU_CONVERSATION_HISTORY)?.apply {
            title = getString(
                if (searchExpanded) R.string.chat_search_next else R.string.chat_history_title,
            )
            icon = tintedDrawable(
                if (searchExpanded) R.drawable.ic_arrow_down_24 else R.drawable.ic_history_24,
                appPalette.primaryText,
            )
            isEnabled = !searchExpanded || hasSearchResults
        }
        menu.findItem(MENU_SEARCH)?.apply {
            isEnabled = searchExpanded || messages.any { message -> message.text.isNotBlank() }
            icon = tintedDrawable(R.drawable.ic_search_24, appPalette.primaryText)
        }
    }

    private fun refreshOptionsMenuState() {
        if (::toolbar.isInitialized) renderOptionsMenuState(toolbar.menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_NEW_CONVERSATION -> {
            if (searchExpanded) moveSearchResult(-1) else startNewConversation()
            true
        }
        MENU_CONVERSATION_HISTORY -> {
            if (searchExpanded) {
                moveSearchResult(1)
            } else {
                persistConversationNow()
                startActivity(Intent(this, ConversationHistoryActivity::class.java))
            }
            true
        }
        MENU_SEARCH -> {
            showSearch()
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

    override fun onResume() {
        super.onResume()
        val latestSettings = uiSettingsStore.load()
        if (latestSettings != uiSettings) {
            val bubbleStylesChanged =
                latestSettings.userBubbleStyle != uiSettings.userBubbleStyle ||
                    latestSettings.assistantBubbleStyle != uiSettings.assistantBubbleStyle
            uiSettings = latestSettings
            if (bubbleStylesChanged && ::messagesColumn.isInitialized) renderTranscript()
            applyUiSettings()
        }
        refreshTargetCatalog(importCoordinator.managerState())
        refreshDrawerHistory()
        updateController.checkAutomaticallyIfDue()
    }

    override fun onStop() {
        persistComposerDraft()
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
        outState.putStringArrayList(
            STATE_MESSAGE_TARGET_IDS,
            ArrayList(savedMessages.map { message -> message.target?.targetId.orEmpty() }),
        )
        outState.putStringArrayList(
            STATE_MESSAGE_TARGET_PROVIDER_IDS,
            ArrayList(savedMessages.map { message -> message.target?.providerId.orEmpty() }),
        )
        outState.putStringArrayList(
            STATE_MESSAGE_TARGET_MODEL_IDS,
            ArrayList(savedMessages.map { message -> message.target?.modelId.orEmpty() }),
        )
        outState.putStringArrayList(
            STATE_MESSAGE_TARGET_NAMES,
            ArrayList(savedMessages.map { message -> message.target?.displayName.orEmpty() }),
        )
        outState.putStringArrayList(
            STATE_MESSAGE_TARGET_LOCALITIES,
            ArrayList(savedMessages.map { message -> message.target?.locality?.name.orEmpty() }),
        )
        outState.putLong(STATE_NEXT_MESSAGE_ID, nextMessageId)
        outState.putString(STATE_CONVERSATION_ID, currentConversationId)
        outState.putLong(STATE_CONVERSATION_CREATED_AT, conversationCreatedAtMillis)
        outState.putLong(STATE_CONVERSATION_UPDATED_AT, conversationUpdatedAtMillis)
        saveConversationTarget(outState)
        outState.putString(STATE_COMPOSER_TEXT, input.text.toString())
        outState.putString(
            STATE_SEARCH_QUERY,
            if (::searchInput.isInitialized) searchInput.text.toString() else restoredSearchQuery,
        )
        editingMessageId?.let { id -> outState.putLong(STATE_EDITING_MESSAGE_ID, id) }
        outState.putString(STATE_DRAFT_BEFORE_EDITING, draftBeforeEditing)
        outState.putString(STATE_PENDING_DRAWER_EXPORT_ID, pendingDrawerExportId)
        persistComposerDraft()
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        persistComposerDraft()
        destroyed = true
        updateController.cancel()
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
                backendExecutor.execute { runCatching(detached::cancelAndClose) }
            } catch (_: RejectedExecutionException) {
                closeOnFallbackThread(detached)
            }
        }
        backendExecutor.shutdown()
        super.onDestroy()
    }

    private fun createContentView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(appPalette.windowBackground)
        }

        val statusBarBackground = createStatusBarBackground()
        content.addView(
            statusBarBackground,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0),
        )
        toolbar = createAppToolbar(
            R.string.app_name,
            showBack = false,
        )
        toolbar.navigationIcon = tintedDrawable(R.drawable.ic_menu_24, appPalette.primaryText)
        toolbar.setNavigationContentDescription(R.string.navigation_open_drawer)
        toolbar.setNavigationOnClickListener {
            persistConversationNow()
            refreshDrawerHistory()
            drawerLayout.openDrawer(GravityCompat.START)
        }
        content.addView(toolbar)
        content.addView(createTargetBar())
        content.addView(View(this).apply { setBackgroundColor(appPalette.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        val conversationFrame = FrameLayout(this)
        messagesColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            setPaddingRelative(dp(16), dp(18), dp(16), dp(24))
            // Fade new messages in; keep size changes instant so streaming stays calm.
            layoutTransition = android.animation.LayoutTransition().apply {
                disableTransitionType(android.animation.LayoutTransition.CHANGING)
                disableTransitionType(android.animation.LayoutTransition.CHANGE_APPEARING)
                disableTransitionType(android.animation.LayoutTransition.CHANGE_DISAPPEARING)
                disableTransitionType(android.animation.LayoutTransition.DISAPPEARING)
            }
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
        emptyStateScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            addView(
                emptyState,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        conversationFrame.addView(
            emptyStateScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        scrollToBottomButton = android.widget.ImageButton(this).apply {
            background = roundedRippleFill(
                appPalette.surface,
                accentRipple(appPalette.accent),
                Ui.RADIUS_SHEET,
                appPalette.outline,
            )
            setImageDrawable(tintedDrawable(R.drawable.ic_arrow_down_24, appPalette.primaryText))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.chat_scroll_to_bottom)
            visibility = View.GONE
            elevation = dp(2).toFloat()
            setOnClickListener { scrollToBottom() }
        }
        conversationFrame.addView(
            scrollToBottomButton,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = dp(16)
                bottomMargin = dp(16)
            },
        )
        messagesScroll.setOnScrollChangeListener { _, _, _, _, _ -> updateScrollToBottomButton() }
        messagesColumn.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) refreshBubbleWidths()
            updateScrollToBottomButton()
        }
        content.addView(
            conversationFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        val composer = createComposer()
        content.addView(composer)

        val navigationDrawer = createNavigationDrawer()

        drawerLayout = DrawerLayout(this).apply {
            setBackgroundColor(appPalette.windowBackground)
            addView(
                content,
                DrawerLayout.LayoutParams(
                    DrawerLayout.LayoutParams.MATCH_PARENT,
                    DrawerLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                navigationDrawer,
                DrawerLayout.LayoutParams(
                    navigationDrawerWidth(),
                    DrawerLayout.LayoutParams.MATCH_PARENT,
                ).apply { gravity = GravityCompat.START },
            )
        }
        applyChatSystemBarInsets(content, composer, navigationDrawer, statusBarBackground)
        return drawerLayout
    }

    private fun applyChatSystemBarInsets(
        content: View,
        composer: View,
        navigationDrawer: View,
        statusBarBackground: View,
    ) {
        val contentPadding = ViewPadding.from(content)
        val composerPadding = ViewPadding.from(composer)
        applySystemBarInsets(drawerLayout) { bars ->
            content.setPadding(
                contentPadding.left + bars.left,
                contentPadding.top,
                contentPadding.right + bars.right,
                contentPadding.bottom,
            )
            updateStatusBarBackgroundHeight(statusBarBackground, bars.top)
            composer.setPadding(
                composerPadding.left,
                composerPadding.top,
                composerPadding.right,
                composerPadding.bottom + bars.bottom,
            )
            applyDrawerSystemBarMargins(navigationDrawer, bars)
        }
    }

    private fun updateStatusBarBackgroundHeight(statusBarBackground: View, height: Int) {
        val params = statusBarBackground.layoutParams ?: return
        if (params.height == height) return
        params.height = height
        statusBarBackground.layoutParams = params
    }

    private fun applyDrawerSystemBarMargins(
        navigationDrawer: View,
        bars: SystemBarPadding,
    ) {
        val params = navigationDrawer.layoutParams as? DrawerLayout.LayoutParams ?: return
        if (
            params.leftMargin == bars.left &&
            params.topMargin == bars.top &&
            params.rightMargin == bars.right &&
            params.bottomMargin == bars.bottom
        ) {
            return
        }
        params.setMargins(bars.left, bars.top, bars.right, bars.bottom)
        navigationDrawer.layoutParams = params
    }

    private fun createTargetBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(dp(16), dp(2), dp(16), dp(8))

        val capsule = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.chat_open_model_switcher)
            minimumHeight = dp(36)
            background = roundedRippleFill(
                appPalette.surfaceVariant,
                accentRipple(appPalette.accent),
                Ui.RADIUS_BUBBLE,
            )
            setPaddingRelative(dp(12), dp(4), dp(10), dp(4))
            setOnClickListener { showTargetSelector() }

            // The explicit Local/Cloud badge is a product invariant; keep it prominent.
            targetLocalityStatus = TextView(context).apply {
                textSize = 10.5f
                typeface = Ui.mediumTypeface
                setTextColor(appPalette.accent)
                background = roundedFill(accentTone(appPalette.accent), Ui.RADIUS_BUBBLE)
                setPaddingRelative(dp(8), dp(2), dp(8), dp(2))
            }
            addView(
                targetLocalityStatus,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(8) },
            )
            targetStatus = TextView(context).apply {
                textSize = 12.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(appPalette.primaryText)
            }
            addView(
                targetStatus,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                ImageView(context).apply {
                    setImageDrawable(
                        tintedDrawable(R.drawable.ic_chevron_right_24, appPalette.secondaryText),
                    )
                    alpha = 0.7f
                },
                LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginStart = dp(6) },
            )
        }
        addView(
            capsule,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun createSearchActionView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumWidth = (resources.displayMetrics.widthPixels - dp(208))
            .coerceIn(dp(120), dp(240))
        layoutParams = Toolbar.LayoutParams(
            Toolbar.LayoutParams.MATCH_PARENT,
            Toolbar.LayoutParams.MATCH_PARENT,
        )
        setPaddingRelative(dp(4), 0, dp(4), 0)

        searchInput = EditText(context).apply {
            hint = getString(R.string.chat_search_hint)
            textSize = 18f
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            minimumHeight = 0
            setTextColor(appPalette.primaryText)
            setHintTextColor(appPalette.secondaryText)
            setPaddingRelative(0, 0, 0, 0)
            background = null
            tintEditText(this)
            backgroundTintList = null
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard(this)
                    true
                } else {
                    false
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    value: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    value: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    pendingSearchRefresh?.let(::removeCallbacks)
                    val refresh = Runnable {
                        if (searchExpanded) {
                            refreshSearchResults(selectFirst = true, locate = true)
                        }
                    }
                    pendingSearchRefresh = refresh
                    postDelayed(refresh, SEARCH_DEBOUNCE_MILLIS)
                }

                override fun afterTextChanged(value: Editable?) = Unit
            })
        }
        addView(
            searchInput,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f,
            ),
        )
    }

    private fun createNavigationDrawer(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        elevation = dp(16).toFloat()
        setBackgroundColor(appPalette.windowBackground)

        addView(TextView(context).apply {
            text = getString(R.string.chat_history_title)
            textSize = 16f
            typeface = Ui.mediumTypeface
            setTextColor(appPalette.primaryText)
            setPaddingRelative(dp(20), dp(20), dp(20), dp(12))
        })
        addView(
            ScrollView(context).apply {
                isFillViewport = true
                drawerHistoryColumn = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPaddingRelative(dp(10), 0, dp(10), dp(12))
                }
                addView(drawerHistoryColumn)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        addView(
            View(context).apply { setBackgroundColor(appPalette.divider) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)),
        )
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPaddingRelative(dp(4), dp(5), dp(4), dp(5))
            addView(
                drawerActionButton(R.drawable.ic_settings_24, R.string.app_settings_title) {
                    startActivity(Intent(this@ChatActivity, AppSettingsActivity::class.java))
                },
                LinearLayout.LayoutParams(0, dp(60), 1f),
            )
            addView(
                drawerActionButton(R.drawable.ic_model_24, R.string.model_manager_title) {
                    openModelManager()
                },
                LinearLayout.LayoutParams(0, dp(60), 1f),
            )
            addView(
                drawerActionButton(R.drawable.ic_restart_24, R.string.drawer_restart) {
                    restartApplication()
                },
                LinearLayout.LayoutParams(0, dp(60), 1f),
            )
            addView(
                drawerActionButton(R.drawable.ic_exit_24, R.string.drawer_exit) {
                    exitApplication()
                },
                LinearLayout.LayoutParams(0, dp(60), 1f),
            )
        })
        refreshDrawerHistory()
    }

    private fun drawerActionButton(
        iconResource: Int,
        titleResource: Int,
        action: () -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = getString(titleResource)
        background = boundedRipple(accentRipple(appPalette.accent), Ui.RADIUS_CONTROL)
        addView(ImageView(context).apply {
            setImageDrawable(tintedDrawable(iconResource, appPalette.secondaryText))
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(22), dp(22)))
        addView(TextView(context).apply {
            text = getString(titleResource)
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(appPalette.secondaryText)
            setPaddingRelative(0, dp(5), 0, 0)
        })
        setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            action()
        }
    }

    private fun navigationDrawerWidth(): Int {
        val screenWidthDp = resources.configuration.screenWidthDp.toFloat()
        val targetWidthDp = 300f.coerceAtMost(screenWidthDp * 0.85f)
        return dp(targetWidthDp.toInt())
    }

    private fun refreshDrawerHistory() {
        if (!::drawerHistoryColumn.isInitialized) return
        val conversations = historyStore.loadAll()
        drawerHistoryColumn.removeAllViews()
        if (conversations.isEmpty()) {
            drawerHistoryColumn.addView(TextView(this).apply {
                text = getString(R.string.chat_history_empty_title)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(appPalette.secondaryText)
                setPaddingRelative(dp(18), dp(42), dp(18), dp(28))
            })
            return
        }
        conversations.forEach { conversation ->
            drawerHistoryColumn.addView(drawerHistoryRow(conversation))
        }
    }

    private fun drawerHistoryRow(conversation: StoredConversation): View {
        val messageCount = conversation.messages.count { message ->
            message.role != ChatMessageRole.NOTICE
        }
        val updated = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(conversation.updatedAtMillis))
        val countAndTime = getString(R.string.chat_history_count_and_time, messageCount, updated)
        val metadata = conversation.target?.let { snapshot ->
            getString(R.string.chat_history_metadata, countAndTime, targetDisplayName(snapshot))
        } ?: countAndTime
        val current = conversation.id == currentConversationId
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = conversation.title
            setPaddingRelative(dp(12), dp(11), dp(12), dp(11))
            background = if (current) {
                roundedRippleFill(
                    accentTone(appPalette.accent),
                    accentRipple(appPalette.accent),
                    Ui.RADIUS_CONTROL,
                )
            } else {
                boundedRipple(accentRipple(appPalette.accent), Ui.RADIUS_CONTROL)
            }
            addView(TextView(context).apply {
                text = conversation.title
                textSize = 14.5f
                typeface = if (current) Ui.mediumTypeface else Typeface.DEFAULT
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(appPalette.primaryText)
            })
            addView(TextView(context).apply {
                text = metadata
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(appPalette.secondaryText)
                setPaddingRelative(0, dp(5), 0, 0)
            })
            setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                if (conversation.id != currentConversationId) switchConversation(conversation.id)
            }
            setOnLongClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                showDrawerHistoryActions(conversation)
                true
            }
        }.also { row ->
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(4) }
        }
    }

    private fun showDrawerHistoryActions(conversation: StoredConversation) {
        val actions = listOf(
            DrawerConversationAction.COPY_ALL,
            DrawerConversationAction.EXPORT,
            DrawerConversationAction.DELETE,
        )
        materialDialog()
            .setTitle(conversation.title)
            .setItems(actions.map { action -> getString(action.labelResource) }.toTypedArray()) { _, index ->
                when (actions[index]) {
                    DrawerConversationAction.COPY_ALL -> copyDrawerConversation(conversation)
                    DrawerConversationAction.EXPORT -> beginDrawerConversationExport(conversation)
                    DrawerConversationAction.DELETE -> confirmDrawerConversationDeletion(conversation)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun copyDrawerConversation(conversation: StoredConversation) {
        val transcript = ConversationTranscriptFormatter.format(
            conversations = listOf(conversation),
            userLabel = getString(R.string.chat_history_role_user),
            assistantLabel = getString(R.string.chat_history_role_assistant),
            noticeLabel = getString(R.string.chat_history_role_notice),
        )
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(getString(R.string.chat_history_clipboard_label), transcript),
        )
        showSnackbar(drawerLayout, getString(R.string.chat_history_copied, 1))
    }

    private fun beginDrawerConversationExport(conversation: StoredConversation) {
        pendingDrawerExportId = conversation.id
        val timestamp = SimpleDateFormat(EXPORT_TIMESTAMP_PATTERN, Locale.US).format(Date())
        drawerExportPicker.launch("3-stone-ai-conversation-$timestamp.3sac")
    }

    private fun exportDrawerConversation(uri: Uri) {
        val conversation = historyStore.find(pendingDrawerExportId)
        val result = runCatching {
            requireNotNull(conversation) { "The conversation is no longer available" }
            val bytes = ConversationHistoryCodec.encode(listOf(conversation))
            contentResolver.openOutputStream(uri, "w")?.use { output -> output.write(bytes) }
                ?: error("The selected export document could not be opened")
        }
        showSnackbar(
            drawerLayout,
            getString(
                if (result.isSuccess) R.string.chat_history_export_succeeded
                else R.string.chat_history_export_failed,
            ),
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
        )
    }

    private fun confirmDrawerConversationDeletion(conversation: StoredConversation) {
        materialDialog()
            .setTitle(R.string.chat_history_delete_single_title)
            .setMessage(getString(R.string.chat_history_delete_single_message, conversation.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_history_delete_selected) { _, _ ->
                val deletingCurrent = conversation.id == currentConversationId
                if (deletingCurrent) {
                    // Stop first because stopping a streamed response persists its final state.
                    // Clearing the in-memory branch after deletion then prevents the regular
                    // new-conversation path from reviving the just-deleted history entry.
                    if (isGenerating) stopGeneration(showToast = false) else persistConversationNow()
                    historyStore.delete(setOf(conversation.id))
                    messages.clear()
                    allowDeletedConversationRevival = false
                    startNewConversation()
                } else {
                    historyStore.delete(setOf(conversation.id))
                    refreshDrawerHistory()
                }
            }
            .show()
            .also { dialog ->
                tintDialogButtons(dialog)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(getColor(R.color.validation_error))
            }
    }

    private fun installBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        drawerLayout.closeDrawer(GravityCompat.START)
                    searchExpanded -> closeSearch()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun restartApplication() {
        persistConversationNow()
        val component = packageManager.getLaunchIntentForPackage(packageName)?.component
        if (component == null) {
            recreate()
            return
        }
        startActivity(Intent.makeRestartActivityTask(component))
    }

    private fun exitApplication() {
        persistConversationNow()
        finishAndRemoveTask()
    }

    private fun createEmptyState(): LinearLayout {
        val firstSuggestion = suggestionButton(R.string.chat_suggestion_explain)
        val secondSuggestion = suggestionButton(R.string.chat_suggestion_code)
        val thirdSuggestion = suggestionButton(R.string.chat_suggestion_plan)
        suggestionButtons = listOf(firstSuggestion, secondSuggestion, thirdSuggestion)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPaddingRelative(dp(36), dp(24), dp(36), dp(24))

            addView(
                ImageView(context).apply {
                    setImageResource(R.mipmap.ic_launcher_foreground)
                    contentDescription = null
                },
                LinearLayout.LayoutParams(dp(96), dp(96)),
            )
            emptyTitle = TextView(context).apply {
                textSize = Ui.TEXT_PAGE_TITLE
                gravity = Gravity.CENTER
                typeface = Ui.mediumTypeface
                setTextColor(appPalette.primaryText)
                setPaddingRelative(0, dp(4), 0, dp(8))
            }
            addView(emptyTitle)
            emptyDescription = TextView(context).apply {
                textSize = Ui.TEXT_BODY
                gravity = Gravity.CENTER
                setTextColor(appPalette.secondaryText)
                setLineSpacing(0f, Ui.LINE_SPACING_BODY)
            }
            addView(
                emptyDescription,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            emptyActionButton = this@ChatActivity.filledButton(R.string.chat_choose_target) {
                showTargetSelector()
            }
            addView(emptyActionButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) })
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

    private fun suggestionButton(textResource: Int): Button =
        MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = getString(textResource)
            isAllCaps = false
            textSize = Ui.TEXT_BODY
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            insetTop = 0
            insetBottom = 0
            minHeight = dp(52)
            minimumHeight = dp(52)
            setTextColor(appPalette.primaryText)
            rippleColor = ColorStateList.valueOf(accentRipple(appPalette.accent))
            background = roundedFill(appPalette.surface, Ui.RADIUS_CARD, appPalette.outline)
            backgroundTintList = null
            setPaddingRelative(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { sendSuggestedPrompt(text.toString()) }
        }

    private fun createComposer(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)

        editingBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = roundedFill(accentTone(appPalette.accent), Ui.RADIUS_CONTROL)
            setPaddingRelative(dp(12), dp(2), dp(4), dp(2))
            addView(
                ImageView(context).apply {
                    setImageDrawable(tintedDrawable(R.drawable.ic_edit_24, appPalette.accent))
                },
                LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(8) },
            )
            editingLabel = TextView(context).apply {
                text = getString(R.string.chat_editing_message)
                textSize = 12.5f
                typeface = Ui.mediumTypeface
                setTextColor(appPalette.accent)
            }
            addView(
                editingLabel,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                ImageView(context).apply {
                    setImageDrawable(tintedDrawable(R.drawable.ic_close_24, appPalette.accent))
                    isClickable = true
                    isFocusable = true
                    contentDescription = getString(R.string.chat_cancel_editing)
                    scaleType = ImageView.ScaleType.CENTER
                    applySelectableBackground(this)
                    setOnClickListener { cancelEditing() }
                },
                LinearLayout.LayoutParams(dp(36), dp(36)),
            )
        }
        addView(
            editingBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
                topMargin = dp(8)
            },
        )

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            isBaselineAligned = false

            input = EditText(context).apply {
                textSize = uiSettings.fontSize.inputSp
                maxLines = 6
                minLines = 1
                minimumHeight = dp(COMPOSER_CONTROL_MIN_HEIGHT_DP)
                minHeight = dp(COMPOSER_CONTROL_MIN_HEIGHT_DP)
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions = editorImeOptions()
                filters = arrayOf(
                    InputFilter.LengthFilter(ChatConversationPolicy.MAXIMUM_INPUT_CHARACTERS),
                )
                setTextColor(appPalette.primaryText)
                setHintTextColor(appPalette.secondaryText)
                setPaddingRelative(dp(16), dp(10), dp(16), dp(10))
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
                    override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                        renderComposerState()
                        persistComposerDraft()
                    }
                    override fun afterTextChanged(value: Editable?) = Unit
                })
            }
            addView(
                FrameLayout(context).apply {
                    minimumHeight = dp(COMPOSER_CONTROL_MIN_HEIGHT_DP)
                    background = roundedFill(
                        appPalette.surface,
                        COMPOSER_CORNER_RADIUS_DP,
                        appPalette.outline,
                    )
                    addView(
                        input,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    gravity = Gravity.BOTTOM
                },
            )

            val onAccent = AppColorPolicy.onThemeColor(appPalette.accent, appPalette.isDark)
            sendButton = android.widget.ImageButton(context).apply {
                scaleType = ImageView.ScaleType.CENTER
                background = roundedRippleFill(
                    android.graphics.Color.WHITE,
                    AppColorPolicy.withAlpha(onAccent, 0x33),
                    COMPOSER_CORNER_RADIUS_DP,
                )
                backgroundTintList = ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(appPalette.surfaceVariant, appPalette.accent),
                )
                imageTintList = ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(AppColorPolicy.withAlpha(appPalette.secondaryText, 0x99), onAccent),
                )
                setImageResource(R.drawable.ic_send_24)
                contentDescription = getString(R.string.chat_send)
                setOnClickListener {
                    if (isGenerating) stopGeneration() else sendCurrentMessage()
                }
            }
            addView(sendButton, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                gravity = Gravity.BOTTOM
                marginStart = dp(8)
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
            topMargin = dp(6)
        })

        setPaddingRelative(0, 0, 0, dp(10))
    }

    private fun syncComposerControlHeight() {
        if (!::input.isInitialized) return
        input.minimumHeight = dp(COMPOSER_CONTROL_MIN_HEIGHT_DP)
        input.minHeight = dp(COMPOSER_CONTROL_MIN_HEIGHT_DP)
    }

    private fun renderManagerState(state: ModelManagerState) {
        refreshTargetCatalog(state)
    }

    private fun refreshTargetCatalog(state: ModelManagerState) {
        if (destroyed) return
        val catalogResult = runCatching {
            (application as ThreeStoneAiApplication).aiBackend.catalog()
        }
        targetCatalog = catalogResult.getOrNull()
        targetCatalogAvailability = when {
            catalogResult.isSuccess -> TargetCatalogAvailability.READY
            state.importState === ModelImportState.Preparing -> TargetCatalogAvailability.LOADING
            else -> TargetCatalogAvailability.UNAVAILABLE
        }
        if (conversationTarget == null && messages.isEmpty()) {
            conversationTarget = targetCatalog?.let(ConversationTargetPolicy::defaultSnapshot)
        }
        val refreshedSnapshot = conversationTarget?.let { current ->
            targetCatalog?.targets
                ?.singleOrNull { target -> target.targetId == current.targetId }
                ?.let(ConversationTargetSnapshot::from)
        }
        val targetMetadataChanged = refreshedSnapshot != null && refreshedSnapshot != conversationTarget
        if (targetMetadataChanged) conversationTarget = refreshedSnapshot
        renderTargetUi()
        if (targetMetadataChanged && messages.isNotEmpty()) {
            persistConversationNow()
            refreshDrawerHistory()
        }
    }

    private fun renderTargetUi() {
        if (!::targetStatus.isInitialized) return
        val snapshot = conversationTarget
        val resolved = resolvedConversationTarget()
        targetLocalityStatus.visibility = if (snapshot == null) View.GONE else View.VISIBLE
        targetLocalityStatus.text = snapshot?.let { targetLocalityLabel(it.locality) }.orEmpty()
        targetStatus.text = when {
            targetCatalogAvailability == TargetCatalogAvailability.LOADING ->
                getString(R.string.chat_target_loading)
            targetCatalogAvailability == TargetCatalogAvailability.UNAVAILABLE ->
                getString(R.string.chat_target_catalog_unavailable)
            snapshot == null -> getString(R.string.chat_no_target)
            resolved == null || !resolved.configured || !resolved.available ||
                !resolved.capabilities.streaming -> getString(
                R.string.chat_target_unavailable_format,
                targetBarSummary(snapshot),
            )
            else -> targetBarSummary(snapshot)
        }
        renderEmptyState()
        renderComposerState()
    }

    private fun renderEmptyState() {
        if (!::emptyState.isInitialized) return
        val visible = messages.isEmpty()
        if (visible && emptyStateScroll.visibility != View.VISIBLE) {
            emptyState.alpha = 0f
            emptyStateScroll.visibility = View.VISIBLE
            emptyState.animate().alpha(1f).setDuration(180L).start()
        } else if (!visible) {
            emptyStateScroll.visibility = View.GONE
        }
        if (messages.isNotEmpty()) return
        val targetReady = isConversationTargetReady()
        when {
            targetCatalogAvailability == TargetCatalogAvailability.LOADING -> {
                emptyTitle.text = getString(R.string.chat_target_loading_title)
                emptyDescription.text = getString(R.string.chat_target_loading_description)
            }
            targetCatalogAvailability == TargetCatalogAvailability.UNAVAILABLE -> {
                emptyTitle.text = getString(R.string.chat_target_catalog_error_title)
                emptyDescription.text = getString(R.string.chat_target_catalog_error_description)
            }
            conversationTarget == null -> {
                emptyTitle.text = getString(R.string.chat_no_target_title)
                emptyDescription.text = getString(R.string.chat_no_target_description)
            }
            !targetReady -> {
                emptyTitle.text = getString(R.string.chat_target_unavailable_title)
                emptyDescription.text = getString(
                    R.string.chat_target_unavailable_description,
                    conversationTarget?.let(::targetDisplayName).orEmpty(),
                )
            }
            else -> {
                emptyTitle.text = getString(R.string.chat_welcome_title)
                emptyDescription.text = getString(
                    R.string.chat_welcome_description,
                    conversationTarget?.let(::targetDisplayName).orEmpty(),
                )
            }
        }
        emptyActionButton.visibility = if (targetReady) View.GONE else View.VISIBLE
        suggestionButtons.forEach { suggestion ->
            suggestion.visibility = if (targetReady) View.VISIBLE else View.GONE
            suggestion.isEnabled = targetReady && !isGenerating
        }
    }

    private fun renderComposerState() {
        if (!::input.isInitialized || !::sendButton.isInitialized) return
        val targetReady = isConversationTargetReady()
        // Typing stays available while a reply streams; only sending is deferred.
        input.isEnabled = targetReady
        input.hint = getString(
            if (targetReady) R.string.chat_input_hint else R.string.chat_input_no_target_hint,
        )
        sendButton.setImageResource(
            if (isGenerating) R.drawable.ic_stop_24 else R.drawable.ic_send_24,
        )
        sendButton.contentDescription = getString(
            when {
                isGenerating -> R.string.chat_stop
                editingMessageId != null -> R.string.chat_save_and_send
                else -> R.string.chat_send
            },
        )
        sendButton.isEnabled = isGenerating || (targetReady && input.text.toString().isNotBlank())
        if (::suggestionButtons.isInitialized) {
            suggestionButtons.forEach { suggestion ->
                suggestion.isEnabled = targetReady && !isGenerating
                suggestion.alpha = if (suggestion.isEnabled) 1f else Ui.DISABLED_ALPHA
            }
        }
        refreshEditableMessageActions()
    }

    private fun renderTranscript() {
        messagesColumn.removeAllViews()
        messageViews.clear()
        markdownCache.keys.retainAll(messages.map(ChatMessage::id).toSet())
        messages.forEach(::addMessageView)
        refreshOptionsMenuState()
        renderEmptyState()
        if (messages.isNotEmpty()) scrollToBottom()
    }

    private fun appendMessage(message: ChatMessage) {
        messages += message
        addMessageView(message)
        markConversationChanged()
        renderEmptyState()
        refreshActiveSearchResults()
        refreshOptionsMenuState()
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

    private fun recordAssistantTarget(
        generationId: Long,
        assistantMessageId: Long,
        actualTarget: ConversationTargetSnapshot,
    ) {
        if (!isGenerationCurrent(generationId)) return
        val current = messages.singleOrNull { message -> message.id == assistantMessageId }
            ?.takeIf { message -> message.role == ChatMessageRole.ASSISTANT }
            ?: return
        if (current.target != actualTarget) {
            replaceMessage(current.copy(target = actualTarget))
            persistConversationNow()
        }
    }

    private fun addMessageView(message: ChatMessage) {
        if (message.role == ChatMessageRole.NOTICE) {
            val notice = MarkdownMessageView(this, appPalette).apply {
                gravity = Gravity.CENTER
                setPaddingRelative(dp(14), dp(6), dp(14), dp(6))
                background = roundedFill(appPalette.noticeSurface, Ui.RADIUS_BUBBLE)
                showPlainText(message.text, noticeTextSizeSp())
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
        val bubbleStyle = bubbleStyleFor(message.role)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (userMessage) Gravity.END else Gravity.START
        }
        val body = MarkdownMessageView(this, appPalette).apply {
            applyBubbleStyle(message.role, bubbleStyle)
            setMessageLongClickListener { showMessageActions(message.id) }
        }
        row.addView(
            body,
            LinearLayout.LayoutParams(
                if (bubbleStyle == ChatBubbleStyle.NONE) {
                    LinearLayout.LayoutParams.MATCH_PARENT
                } else {
                    LinearLayout.LayoutParams.WRAP_CONTENT
                },
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        val meta = TextView(this).apply {
            textSize = 11f
            setTextColor(appPalette.secondaryText)
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
                topMargin = if (userMessage) dp(8) else dp(4)
                bottomMargin = if (userMessage) dp(4) else dp(14)
            },
        )
        messageViews[message.id] = MessageViewHolder(body, meta)
        updateMessageView(message)
    }

    private fun noticeTextSizeSp(): Float = uiSettings.fontSize.messageSp * 0.8f

    private fun bubbleMaxWidth(): Int {
        val contentWidth = messagesColumn.width -
            messagesColumn.paddingStart - messagesColumn.paddingEnd
        return if (contentWidth > 0) {
            (contentWidth * 0.78f).toInt()
        } else {
            (resources.displayMetrics.widthPixels * 0.7f).toInt()
        }
    }

    private fun bubbleStyleFor(role: ChatMessageRole): ChatBubbleStyle = when (role) {
        ChatMessageRole.USER -> uiSettings.userBubbleStyle
        ChatMessageRole.ASSISTANT -> uiSettings.assistantBubbleStyle
        ChatMessageRole.NOTICE -> ChatBubbleStyle.BACKGROUND
    }

    private fun MarkdownMessageView.applyBubbleStyle(
        role: ChatMessageRole,
        style: ChatBubbleStyle,
    ) {
        if (style == ChatBubbleStyle.NONE) {
            maximumWidth = Int.MAX_VALUE
            setPaddingRelative(dp(2), dp(2), dp(2), dp(2))
            background = boundedRipple(accentRipple(appPalette.accent), Ui.RADIUS_CONTROL)
            return
        }
        maximumWidth = bubbleMaxWidth()
        setPaddingRelative(dp(16), dp(10), dp(16), dp(10))
        background = when (style) {
            ChatBubbleStyle.NONE -> error("Handled above")
            ChatBubbleStyle.BACKGROUND -> roundedRippleFill(
                if (role == ChatMessageRole.USER) appPalette.userSurface
                else appPalette.assistantSurface,
                accentRipple(appPalette.accent),
                Ui.RADIUS_BUBBLE,
            )
            ChatBubbleStyle.BORDER -> roundedRippleFill(
                android.graphics.Color.TRANSPARENT,
                accentRipple(appPalette.accent),
                Ui.RADIUS_BUBBLE,
                if (role == ChatMessageRole.USER) appPalette.accent else appPalette.chatBorder,
            )
        }
    }

    private fun refreshBubbleWidths() {
        val width = bubbleMaxWidth()
        messages.forEach { message ->
            if (
                message.role != ChatMessageRole.NOTICE &&
                bubbleStyleFor(message.role) != ChatBubbleStyle.NONE
            ) {
                messageViews[message.id]?.body?.let { body ->
                    if (body.maximumWidth != width) {
                        body.maximumWidth = width
                        body.requestLayout()
                    }
                }
            }
        }
    }

    private fun updateScrollToBottomButton() {
        if (!::scrollToBottomButton.isInitialized) return
        val visible = messages.isNotEmpty() && !isNearBottom()
        if (visible == (scrollToBottomButton.visibility == View.VISIBLE)) return
        if (visible) {
            scrollToBottomButton.alpha = 0f
            scrollToBottomButton.visibility = View.VISIBLE
            scrollToBottomButton.animate().alpha(1f).setDuration(150L).start()
        } else {
            scrollToBottomButton.animate().alpha(0f).setDuration(150L)
                .withEndAction { scrollToBottomButton.visibility = View.GONE }
                .start()
        }
    }

    private fun updateMessageView(message: ChatMessage) {
        val holder = messageViews[message.id] ?: return
        if (message.role == ChatMessageRole.NOTICE) {
            holder.body.showPlainText(message.text, noticeTextSizeSp())
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
        val failed = message.role == ChatMessageRole.ASSISTANT &&
            message.status == ChatMessageStatus.FAILED
        val value = if (message.role == ChatMessageRole.USER) {
            ""
        } else {
            when (message.status) {
                ChatMessageStatus.GENERATING -> getString(R.string.chat_streaming)
                ChatMessageStatus.STOPPED -> getString(R.string.chat_generation_stopped)
                ChatMessageStatus.FAILED ->
                    getString(R.string.chat_generation_failure_choose_target)
                ChatMessageStatus.COMPLETE -> if (uiSettings.showGenerationUsage) {
                    message.usage?.let { usage -> formatUsage(message, usage) }.orEmpty()
                } else {
                    ""
                }
            }
        }
        meta.text = value
        meta.isClickable = failed
        meta.isFocusable = failed
        meta.minimumHeight = if (failed) dp(40) else 0
        meta.gravity = when {
            failed -> Gravity.CENTER_VERTICAL
            message.role == ChatMessageRole.USER -> Gravity.END
            else -> Gravity.NO_GRAVITY
        }
        meta.setOnClickListener(if (failed) View.OnClickListener { showTargetSelector() } else null)
        meta.contentDescription = value
        meta.background = null
        if (failed) {
            applySelectableBackground(meta)
            val errorColor = getColor(R.color.validation_error)
            meta.setTextColor(errorColor)
            val warning = tintedDrawable(R.drawable.ic_warning_24, errorColor)
                ?.apply { setBounds(0, 0, dp(14), dp(14)) }
            meta.setCompoundDrawablesRelative(warning, null, null, null)
            meta.compoundDrawablePadding = dp(5)
        } else {
            meta.setTextColor(appPalette.secondaryText)
            meta.setCompoundDrawablesRelative(null, null, null, null)
        }
        setMetaPulsing(meta, message.status == ChatMessageStatus.GENERATING)
        meta.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setMetaPulsing(meta: TextView, active: Boolean) {
        val running = meta.getTag(R.id.tag_meta_pulse_animator) as? android.animation.ValueAnimator
        if (active) {
            if (running != null) return
            val animator = android.animation.ValueAnimator.ofFloat(1f, 0.35f).apply {
                duration = 750L
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
                addUpdateListener { animation -> meta.alpha = animation.animatedValue as Float }
                start()
            }
            meta.setTag(R.id.tag_meta_pulse_animator, animator)
        } else if (running != null) {
            running.cancel()
            meta.setTag(R.id.tag_meta_pulse_animator, null)
            meta.alpha = 1f
        }
    }

    private fun sendSuggestedPrompt(prompt: String) {
        if (!isConversationTargetReady() || isGenerating) return
        input.setText(prompt)
        input.setSelection(input.text.length)
        sendCurrentMessage()
    }

    private fun sendCurrentMessage(targetOverride: ConversationTargetSnapshot? = null) {
        if (isGenerating) return
        val target = if (targetOverride == null) {
            resolvedConversationTarget()
        } else {
            ConversationTargetPolicy.resolveExact(targetOverride, targetCatalog)
        }
        if (!isTargetReady(target)) {
            if (targetOverride == null) {
                showTargetSelector()
            } else {
                showUnavailableRegenerationTargetDialog(targetOverride)
            }
            return
        }
        checkNotNull(target)
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
        draftStore.clear()
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
            target = ConversationTargetSnapshot.from(target),
        )
        val generationId = generationEpoch.incrementAndGet()
        activeGenerationId = generationId
        activeAssistantMessageId = assistantMessage.id
        isGenerating = true
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
        startBackendTurn(target, promptText, assistantMessage.id, generationId)
    }

    private fun startBackendTurn(
        target: AiTarget,
        promptText: String,
        assistantMessageId: Long,
        generationId: Long,
    ) {
        val prompt = GenerationMessage(GenerationRole.USER, listOf(promptText))
        val requestedTarget = ConversationTargetSnapshot.from(target)
        val budget = ContextBudgetCalculator.calculate(
            targetLimits = target.limits,
            applicationInputTokenBudget = uiSettings.contextTokenBudget,
            maximumOutputTokens = uiSettings.maximumOutputTokens,
        )
        var reusableBackend: AiBackendSession? = null
        var reusableTelemetry: BackendContextTelemetry? = null
        var backendToReplace: AiBackendSession? = null
        synchronized(backendLock) {
            val telemetry = activeBackendContextTelemetry
            val reusable = target.capabilities.persistentSession &&
                activeBackend != null && telemetry != null &&
                activeBackendTarget?.let { activeTarget ->
                    activeTarget.matchesExecutionIdentity(target)
                } == true &&
                !ContextAccountingPolicy.shouldRotateBackend(
                    accounting = telemetry.accounting,
                    budget = budget,
                    completedTurnsOnBackend = completedTurnsOnBackend,
                )
            if (reusable) {
                reusableBackend = activeBackend
                reusableTelemetry = telemetry
            } else {
                backendToReplace = activeBackend
                activeBackend = null
                activeBackendTarget = null
                activeBackendContextTelemetry = null
                activeContextTurnObservation = null
                completedTurnsOnBackend = 0
            }
        }

        val continuation = reusableBackend
        if (continuation != null) {
            val telemetry = checkNotNull(reusableTelemetry)
            val actualTarget = ConversationTargetSnapshot.from(continuation.target)
            observeContextTurnStart(
                generationId = generationId,
                backend = continuation,
                target = actualTarget,
                prompt = prompt,
                telemetry = telemetry,
                budget = budget,
                rebuilt = false,
            )
            recordAssistantTarget(generationId, assistantMessageId, actualTarget)
            val listener = generationListener(generationId, assistantMessageId, actualTarget)
            submitBackendWork(generationId, assistantMessageId) {
                if (!isGenerationCurrent(generationId)) return@submitBackendWork
                continuation.streamNext(
                    generationRequest(history = emptyList(), prompt = prompt),
                    listener,
                )
            }
            return
        }

        val calibration = contextTokenCalibrationStore.current(requestedTarget.targetId)
        val estimator = calibration.estimator()
        val compiled = ChatConversationPolicy.compileContext(
            transcript = messages,
            prompt = prompt,
            policy = ContextCompilationPolicy(
                estimator = estimator,
                maximumInputTokens = budget.compactionTargetTokens,
            ),
        )
        if (compiled.exceedsInputBudget) {
            Log.w(
                TAG,
                "Compiled context exceeds target after minimum-turn retention " +
                    "target=${requestedTarget.targetId} " +
                    "estimatedInputTokens=${compiled.estimatedInputTokens} " +
                    "compactionTargetTokens=${budget.compactionTargetTokens} " +
                    "coveredMessages=${compiled.coveredMessageIds.size}",
            )
        }
        val history = compiled.messages
        val historyEstimate = estimator.estimateMessages(history)
        Log.d(
            TAG,
            "Context compile target=${requestedTarget.targetId} " +
                "estimatedInputTokens=${compiled.estimatedInputTokens} " +
                "historyTokens=${historyEstimate.estimatedTokens} " +
                "historyBytes=${historyEstimate.utf8Bytes} " +
                "coveredMessages=${compiled.coveredMessageIds.size} " +
                "trimmed=${compiled.requiresSessionRebuild} " +
                "hardWatermarkTokens=${budget.hardWatermarkTokens} " +
                "compactionTargetTokens=${budget.compactionTargetTokens}",
        )
        submitBackendWork(generationId, assistantMessageId) {
            runCatching { backendToReplace?.close() }
            if (!isGenerationCurrent(generationId)) return@submitBackendWork
            val created = (application as ThreeStoneAiApplication).aiBackend.createSession(
                AiBackendSessionRequest(
                    targetId = requestedTarget.targetId,
                    executionProfileId = target.chatExecutionProfileId(),
                ),
            )
            val actualTarget = ConversationTargetSnapshot.from(created.target)
            val installedTelemetry = synchronized(backendLock) {
                if (!isGenerationCurrent(generationId) || activeBackend != null) {
                    null
                } else {
                    backendContextEpoch = if (backendContextEpoch == Long.MAX_VALUE) {
                        Long.MAX_VALUE
                    } else {
                        backendContextEpoch + 1L
                    }
                    val telemetry = BackendContextTelemetry(
                        epoch = backendContextEpoch,
                        committedMessages = history.map { message -> message.contextSnapshot() },
                        accounting = ContextAccounting.initial(historyEstimate.estimatedTokens),
                    )
                    activeBackend = created
                    activeBackendTarget = actualTarget
                    activeBackendContextTelemetry = telemetry
                    activeContextTurnObservation = null
                    completedTurnsOnBackend = 0
                    telemetry
                }
            }
            if (installedTelemetry == null) {
                runCatching(created::close)
                return@submitBackendWork
            }
            observeContextTurnStart(
                generationId = generationId,
                backend = created,
                target = actualTarget,
                prompt = prompt,
                telemetry = installedTelemetry,
                budget = budget,
                rebuilt = true,
            )
            mainHandler.post {
                recordAssistantTarget(generationId, assistantMessageId, actualTarget)
            }
            val listener = generationListener(generationId, assistantMessageId, actualTarget)
            created.stream(generationRequest(history, prompt), listener)
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

    private fun observeContextTurnStart(
        generationId: Long,
        backend: AiBackendSession,
        target: ConversationTargetSnapshot,
        prompt: GenerationMessage,
        telemetry: BackendContextTelemetry,
        budget: ContextBudget,
        rebuilt: Boolean,
    ) {
        val calibration = contextTokenCalibrationStore.current(target.targetId)
        val estimator = calibration.estimator()
        val promptSnapshot = prompt.contextSnapshot()
        val outbound = telemetry.committedMessages + promptSnapshot
        val inputEstimate = estimator.estimateMessages(outbound)
        // LiteRT reports only the newly processed prompt after its first turn. Online targets
        // report the complete replayed request on every turn.
        val calibrationInput = estimator.estimateMessages(
            ContextTokenObservationPolicy.calibrationInputMessages(
                locality = target.locality,
                rebuilt = rebuilt,
                committedMessages = telemetry.committedMessages,
                prompt = promptSnapshot,
            ),
        )
        val observation = ContextTurnObservation(
            generationId = generationId,
            backendEpoch = telemetry.epoch,
            prompt = promptSnapshot,
            rebuilt = rebuilt,
            inputEstimate = inputEstimate,
            calibrationInput = calibrationInput,
            coefficientBefore = calibration.tokensPerUtf8Byte,
            hardWatermarkTokens = budget.hardWatermarkTokens,
        )
        val installed = synchronized(backendLock) {
            if (
                activeBackend !== backend ||
                activeBackendContextTelemetry?.epoch != telemetry.epoch ||
                !isGenerationCurrent(generationId)
            ) {
                false
            } else {
                activeContextTurnObservation = observation
                true
            }
        }
        if (!installed) return
        Log.d(
            TAG,
            "Context start target=${target.targetId} " +
                "estimatedInputTokens=${inputEstimate.estimatedTokens} " +
                "inputBytes=${inputEstimate.utf8Bytes} actualInputTokens=pending " +
                "accountingTokens=${telemetry.accounting.tokens} " +
                "accountingSource=${telemetry.accounting.source} " +
                "hardWatermarkTokens=${budget.hardWatermarkTokens} " +
                "compactionTargetTokens=${budget.compactionTargetTokens} " +
                "backendEpoch=${telemetry.epoch} rebuilt=$rebuilt " +
                "coefficient=${calibration.tokensPerUtf8Byte}",
        )
    }

    private fun completeContextTurnObservation(
        generationId: Long,
        actualTarget: ConversationTargetSnapshot,
        assistantText: String,
        statistics: GenerationStatistics?,
        cumulativeInputTokens: Long,
    ) {
        var observation: ContextTurnObservation? = null
        var committedMessages: List<GenerationMessage>? = null
        var previousAccounting: ContextAccounting? = null
        synchronized(backendLock) {
            val candidate = activeContextTurnObservation
            val telemetry = activeBackendContextTelemetry
            if (
                candidate != null && candidate.generationId == generationId &&
                telemetry != null && telemetry.epoch == candidate.backendEpoch
            ) {
                val committed = telemetry.committedMessages +
                    candidate.prompt.contextSnapshot() +
                    GenerationMessage(
                        role = GenerationRole.ASSISTANT,
                        textParts = listOf(assistantText),
                    )
                activeBackendContextTelemetry = telemetry.copy(committedMessages = committed)
                observation = candidate
                committedMessages = committed
                previousAccounting = telemetry.accounting
            }
            activeContextTurnObservation = null
        }
        val completedObservation = observation ?: return
        val completedMessages = committedMessages ?: return
        val calibrationObservation = statistics?.let { usage ->
            ContextTokenCalibrationObservation(
                utf8Bytes = completedObservation.calibrationInput.utf8Bytes,
                messageCount = completedObservation.calibrationInput.messageCount,
                actualInputTokens = usage.inputTokens,
            )
        }
        val calibration = contextTokenCalibrationStore.record(
            actualTarget.targetId,
            calibrationObservation,
        )
        val estimatedAfterTurn = calibration.estimator()
            .estimateMessages(completedMessages)
            .estimatedTokens
        val assistant = completedMessages.last()
        val estimatedTurnTokens = calibration.estimator()
            .estimateMessages(listOf(completedObservation.prompt, assistant))
            .estimatedTokens
        val accounting = ContextAccountingPolicy.afterSuccessfulTurn(
            current = previousAccounting ?: ContextAccounting.initial(),
            statistics = statistics,
            estimatedContextTokensAfterTurn = estimatedAfterTurn,
            estimatedTurnTokens = estimatedTurnTokens,
        )
        synchronized(backendLock) {
            val telemetry = activeBackendContextTelemetry
            if (telemetry?.epoch == completedObservation.backendEpoch) {
                activeBackendContextTelemetry = telemetry.copy(accounting = accounting)
            }
        }
        Log.d(
            TAG,
            "Context complete target=${actualTarget.targetId} " +
                "estimatedInputTokens=${completedObservation.inputEstimate.estimatedTokens} " +
                "actualInputTokens=${statistics?.inputTokens} " +
                "actualOutputTokens=${statistics?.outputTokens} " +
                "accountingTokens=${accounting.tokens} accountingSource=${accounting.source} " +
                "hardWatermarkTokens=${completedObservation.hardWatermarkTokens} " +
                "cumulativeInputTokens=$cumulativeInputTokens " +
                "backendEpoch=${completedObservation.backendEpoch} " +
                "rebuilt=${completedObservation.rebuilt} " +
                "coefficientBefore=${completedObservation.coefficientBefore} " +
                "coefficientAfter=${calibration.tokensPerUtf8Byte}",
        )
    }

    private fun GenerationMessage.contextSnapshot(): GenerationMessage =
        copy(textParts = textParts.toList())

    private fun generationListener(
        generationId: Long,
        assistantMessageId: Long,
        actualTarget: ConversationTargetSnapshot,
    ) = object : GenerationListener {
        override fun onTextDelta(text: String) {
            queueTextDelta(generationId, assistantMessageId, text)
        }

        override fun onCompleted(statistics: GenerationStatistics?) {
            mainHandler.post {
                completeGeneration(generationId, assistantMessageId, actualTarget, statistics)
            }
        }

        override fun onFailed(error: Throwable, statistics: GenerationStatistics?) {
            Log.e(TAG, "Launcher chat generation failed", error)
            mainHandler.post {
                failGeneration(
                    generationId = generationId,
                    assistantMessageId = assistantMessageId,
                    error = error,
                    actualTarget = actualTarget,
                )
            }
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
                    mainHandler.post {
                        failGeneration(generationId, assistantMessageId, error)
                    }
                }
            }
        } catch (error: RejectedExecutionException) {
            Log.e(TAG, "Launcher chat worker rejected generation", error)
            mainHandler.post { failGeneration(generationId, assistantMessageId, error) }
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
        actualTarget: ConversationTargetSnapshot,
        statistics: GenerationStatistics?,
    ) {
        if (!isGenerationCurrent(generationId)) return
        flushTextDelta(generationId, assistantMessageId)
        val current = messages.singleOrNull { message -> message.id == assistantMessageId } ?: return
        replaceMessage(
            current.copy(
                status = ChatMessageStatus.COMPLETE,
                target = actualTarget,
                usage = statistics?.let { value ->
                    ChatMessageUsage(
                        inputTokens = value.inputTokens,
                        outputTokens = value.outputTokens,
                        durationMillis = value.durationMillis,
                    )
                },
            ),
        )
        val cumulativeInputTokens = ChatConversationPolicy.cumulativeInputTokensThrough(
            messages,
            assistantMessageId,
        )
        completeContextTurnObservation(
            generationId = generationId,
            actualTarget = actualTarget,
            assistantText = current.text,
            statistics = statistics,
            cumulativeInputTokens = cumulativeInputTokens,
        )
        synchronized(backendLock) {
            if (activeBackend != null) completedTurnsOnBackend++
        }
        finishGenerationUi(generationId)
        persistConversationNow()
    }

    private fun failGeneration(
        generationId: Long,
        assistantMessageId: Long,
        error: Throwable,
        actualTarget: ConversationTargetSnapshot? = null,
    ) {
        if (!isGenerationCurrent(generationId)) return
        flushTextDelta(generationId, assistantMessageId)
        val current = messages.singleOrNull { message -> message.id == assistantMessageId }
        if (current != null) {
            val failedTarget = actualTarget ?: checkNotNull(current.target)
            val failureText = generationFailureText(failedTarget, error)
            replaceMessage(
                current.copy(
                    text = current.text.withGenerationFailure(failureText),
                    status = ChatMessageStatus.FAILED,
                    usage = null,
                    target = failedTarget,
                ),
            )
        }
        closeCurrentBackend()
        finishGenerationUi(generationId)
        persistConversationNow()
        showSnackbar(drawerLayout, getString(R.string.chat_generation_failed_short), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
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
        val actualTarget = synchronized(backendLock) { activeBackendTarget }
        if (assistantMessageId != null) {
            val delta = takePendingDelta(generationId, assistantMessageId)
            val current = messages.singleOrNull { message -> message.id == assistantMessageId }
            if (current != null) {
                replaceMessage(
                    current.copy(
                        text = current.text + delta,
                        status = ChatMessageStatus.STOPPED,
                        usage = null,
                        target = actualTarget ?: current.target,
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
            showSnackbar(drawerLayout, getString(R.string.chat_generation_stopped))
        }
    }

    private fun startNewConversation(
        target: ConversationTargetSnapshot? = targetCatalog?.let(ConversationTargetPolicy::defaultSnapshot),
    ) {
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
        conversationTarget = target
        allowDeletedConversationRevival = false
        cancelEditing(restoreDraft = false)
        closeSearch()
        input.text.clear()
        draftStore.clear()
        historyStore.rememberLastConversation(currentConversationId)
        renderTargetUi()
    }

    private fun closeCurrentBackend() {
        val detached = detachBackend() ?: return
        try {
            backendExecutor.execute { runCatching(detached::cancelAndClose) }
        } catch (_: RejectedExecutionException) {
            closeOnFallbackThread(detached)
        }
    }

    private fun detachBackend(): AiBackendSession? = synchronized(backendLock) {
        activeBackend.also {
            activeBackend = null
            activeBackendTarget = null
            activeBackendContextTelemetry = null
            activeContextTurnObservation = null
            completedTurnsOnBackend = 0
        }
    }

    private fun closeOnFallbackThread(backend: AiBackendSession) {
        Thread({ runCatching(backend::cancelAndClose) }, "three-stone-ai-chat-close").apply {
            isDaemon = true
            start()
        }
    }

    private fun isGenerationCurrent(generationId: Long): Boolean =
        !destroyed && generationEpoch.get() == generationId

    private fun requestEditMessage(messageId: Long) {
        if (isGenerating || editingMessageId != null) return
        val impact = ConversationEditPolicy.impact(messages, messageId) ?: return
        materialDialog()
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
        draftStore.save(draftBeforeEditing)
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
        } else if (!restoreDraft) {
            draftStore.clear()
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
        if (!item.isActionViewExpanded) {
            if (!item.expandActionView()) return
        }
        toolbar.post { focusSearchInput() }
    }

    private fun focusSearchInput() {
        if (!::searchInput.isInitialized) return
        searchInput.requestFocus()
        searchInput.setSelection(searchInput.text.length)
        scheduleKeyboard(searchInput)
    }

    private fun closeSearch() {
        val item = searchMenuItem
        if (item?.isActionViewExpanded == true) {
            item.collapseActionView()
        } else {
            clearSearchState()
        }
    }

    private fun clearSearchState() {
        val shouldHideKeyboard = searchExpanded ||
            (::searchInput.isInitialized && searchInput.hasFocus())
        searchExpanded = false
        if (::searchInput.isInitialized) {
            searchInput.setText("")
            searchInput.clearFocus()
        }
        searchMatches = emptyList()
        currentSearchMatchIndex = -1
        updateSearchControls()
        messages.forEach(::updateMessageView)
        toolbar.subtitle = null
        if (shouldHideKeyboard && ::searchInput.isInitialized) hideKeyboard(searchInput)
    }

    private fun refreshSearchResults(selectFirst: Boolean, locate: Boolean) {
        if (!::searchInput.isInitialized || !::messagesColumn.isInitialized) return
        val previous = searchMatches.getOrNull(currentSearchMatchIndex)
        val refreshed = ConversationSearchPolicy.find(messages, searchInput.text.toString())
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
            ::searchInput.isInitialized && searchExpanded && searchInput.text.isNotBlank()
        ) {
            refreshSearchResults(selectFirst = false, locate = false)
        }
    }

    private fun updateSearchControls() {
        toolbar.subtitle = if (searchExpanded && searchMatches.isNotEmpty()) {
            getString(
                R.string.chat_search_match_count,
                currentSearchMatchIndex + 1,
                searchMatches.size,
            )
        } else {
            null
        }
        refreshOptionsMenuState()
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

    private fun applyUiSettings() {
        if (::input.isInitialized) {
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, uiSettings.fontSize.inputSp)
            input.imeOptions = editorImeOptions()
            syncComposerControlHeight()
            getSystemService(InputMethodManager::class.java).restartInput(input)
        }
        messageViews.values.forEach { holder ->
            holder.body.setMessageTextSize(uiSettings.fontSize.messageSp)
        }
        messages.forEach(::updateMessageView)
        if (currentSearchMatchIndex >= 0) locateCurrentSearchResult()
    }

    private fun persistComposerDraft() {
        if (!::draftStore.isInitialized || !::input.isInitialized) return
        val draft = if (editingMessageId == null) input.text else draftBeforeEditing
        draftStore.save(draft)
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
            target = conversationTarget,
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
        if (persisted) {
            allowDeletedConversationRevival = false
            if (
                ::drawerLayout.isInitialized &&
                drawerLayout.isDrawerOpen(GravityCompat.START)
            ) {
                refreshDrawerHistory()
            }
        }
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
        conversationTarget = conversation.target
        nextMessageId = (messages.maxOfOrNull(ChatMessage::id) ?: 0L) + 1L
        markdownCache.clear()
        allowDeletedConversationRevival = false
        historyStore.rememberLastConversation(currentConversationId)
    }

    private fun switchConversation(conversationId: String) {
        val target = historyStore.find(conversationId)
        if (target == null) {
            showSnackbar(drawerLayout, getString(R.string.chat_history_not_found))
            return
        }
        if (isGenerating) stopGeneration(showToast = false) else persistConversationNow()
        closeCurrentBackend()
        cancelEditing(restoreDraft = false)
        closeSearch()
        input.text.clear()
        draftStore.clear()
        applyStoredConversation(target)
        renderTranscript()
        renderTargetUi()
        applyUiSettings()
    }

    private fun resolvedConversationTarget(): AiTarget? =
        ConversationTargetPolicy.resolve(conversationTarget, targetCatalog)

    private fun isConversationTargetReady(): Boolean {
        return isTargetReady(resolvedConversationTarget())
    }

    private fun isTargetReady(target: AiTarget?): Boolean =
        targetCatalogAvailability == TargetCatalogAvailability.READY &&
            target?.configured == true && target.available && target.capabilities.streaming

    private fun targetSummary(snapshot: ConversationTargetSnapshot): String = getString(
        R.string.chat_target_status_format,
        targetLocalityLabel(snapshot.locality),
        targetProviderLabel(snapshot.providerId, snapshot.locality),
        targetDisplayName(snapshot),
        snapshot.modelId,
    )

    private fun targetBarSummary(snapshot: ConversationTargetSnapshot): String = getString(
        R.string.chat_model_switch_summary,
        targetDisplayName(snapshot),
        snapshot.modelId,
    )

    private fun targetDisplayName(snapshot: ConversationTargetSnapshot): String =
        if (snapshot.locality == AiTargetLocality.LOCAL) {
            AvailableLiteRtModelCatalog.displayNameForImportedModel(
                snapshot.displayName,
                snapshot.modelId,
            )
        } else {
            snapshot.displayName
        }

    private fun targetLocalityLabel(locality: AiTargetLocality): String = getString(
        when (locality) {
            AiTargetLocality.LOCAL -> R.string.chat_target_locality_local
            AiTargetLocality.REMOTE -> R.string.chat_target_locality_cloud
        },
    )

    private fun targetProviderLabel(providerId: String, locality: AiTargetLocality): String {
        if (locality == AiTargetLocality.LOCAL) return getString(R.string.app_name)
        val provider = runCatching { OnlineAiProvider.fromProviderId(providerId) }.getOrNull()
            ?: return providerId
        return if (provider == OnlineAiProvider.OPENAI_COMPATIBLE) {
            getString(R.string.online_ai_provider_custom)
        } else {
            OnlineAiProviderCatalog.templateFor(provider).displayName
        }
    }

    private fun showTargetSelector() {
        val catalog = targetCatalog
        if (
            targetCatalogAvailability != TargetCatalogAvailability.READY ||
            catalog == null || catalog.targets.isEmpty()
        ) {
            showTargetConfigurationDialog()
            return
        }
        val targets = catalog.targets
        val labels = targets.map(::targetSelectorLabel).toTypedArray()
        val adapter = TargetChoiceAdapter(labels)
        val selectedIndex = targets.indexOfFirst { target ->
            target.targetId == conversationTarget?.targetId
        }
        materialDialog()
            .setTitle(R.string.chat_model_switch_title)
            .setSingleChoiceItems(adapter, selectedIndex) { dialog, index ->
                val target = targets[index]
                dialog.dismiss()
                if (!target.configured || !target.available || !target.capabilities.streaming) {
                    showUnavailableTargetDialog(target)
                } else {
                    requestTargetSelection(target)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
    }

    private fun targetSelectorLabel(target: AiTarget): String {
        val snapshot = ConversationTargetSnapshot.from(target)
        val suffix = buildString {
            if (!target.configured || !target.available || !target.capabilities.streaming) {
                append(getString(R.string.chat_target_unavailable_suffix))
            }
        }
        return getString(
            R.string.chat_target_selector_item,
            targetLocalityLabel(target.locality),
            targetProviderLabel(target.providerId, target.locality),
            targetDisplayName(snapshot),
            snapshot.modelId,
            suffix,
        )
    }

    private fun requestTargetSelection(target: AiTarget) {
        when (
            ConversationTargetPolicy.selectionDisposition(
                current = conversationTarget,
                candidate = target,
                hasMessages = messages.isNotEmpty(),
            )
        ) {
            ConversationTargetSelectionDisposition.UNCHANGED -> Unit
            ConversationTargetSelectionDisposition.APPLY ->
                applyConversationTarget(target, announce = false)
            ConversationTargetSelectionDisposition.CONFIRM_EXISTING_CONVERSATION ->
                confirmTargetChange(target)
        }
    }

    private fun confirmTargetChange(target: AiTarget) {
        val destinationWarning = if (target.locality == AiTargetLocality.REMOTE) {
            getString(R.string.chat_target_change_cloud_warning)
        } else {
            getString(R.string.chat_target_change_local_warning)
        }
        materialDialog()
            .setTitle(R.string.chat_target_change_title)
            .setMessage(
                getString(
                    R.string.chat_target_change_message,
                    targetDisplayName(ConversationTargetSnapshot.from(target)),
                    destinationWarning,
                ),
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.chat_target_continue_current) { _, _ ->
                applyConversationTarget(target, announce = true)
            }
            .setPositiveButton(R.string.chat_target_start_new) { _, _ ->
                startNewConversation(ConversationTargetSnapshot.from(target))
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun applyConversationTarget(target: AiTarget, announce: Boolean) {
        if (isGenerating) stopGeneration(showToast = false)
        closeCurrentBackend()
        val snapshot = ConversationTargetSnapshot.from(target)
        conversationTarget = snapshot
        if (announce && messages.isNotEmpty()) {
            appendMessage(
                ChatMessage(
                    id = allocateMessageId(),
                    role = ChatMessageRole.NOTICE,
                    text = getString(R.string.chat_target_changed_notice, targetSummary(snapshot)),
                ),
            )
            persistConversationNow()
        } else {
            if (messages.isNotEmpty()) {
                markConversationChanged()
                persistConversationNow()
            }
            renderTargetUi()
        }
    }

    private fun showUnavailableTargetDialog(target: AiTarget) {
        val settingsLabel = when (target.locality) {
            AiTargetLocality.LOCAL -> R.string.model_manager_title
            AiTargetLocality.REMOTE -> R.string.online_ai_settings_title
        }
        materialDialog()
            .setTitle(R.string.chat_target_unavailable_title)
            .setMessage(
                getString(
                    R.string.chat_target_unavailable_picker_message,
                    targetDisplayName(ConversationTargetSnapshot.from(target)),
                ),
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(settingsLabel) { _, _ -> openTargetSettings(target.locality) }
            .show()
            .also(::tintDialogButtons)
    }

    private fun showUnavailableRegenerationTargetDialog(
        snapshot: ConversationTargetSnapshot,
    ) {
        val settingsLabel = when (snapshot.locality) {
            AiTargetLocality.LOCAL -> R.string.model_manager_title
            AiTargetLocality.REMOTE -> R.string.online_ai_settings_title
        }
        materialDialog()
            .setTitle(R.string.chat_regenerate_target_unavailable_title)
            .setMessage(
                getString(
                    R.string.chat_regenerate_target_unavailable_message,
                    targetSummary(snapshot),
                ),
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(settingsLabel) { _, _ -> openTargetSettings(snapshot.locality) }
            .show()
            .also(::tintDialogButtons)
    }

    private fun showTargetConfigurationDialog() {
        materialDialog()
            .setTitle(R.string.chat_target_selector_empty_title)
            .setMessage(R.string.chat_target_selector_empty_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.model_manager_title) { _, _ -> openModelManager() }
            .show()
            .also(::tintDialogButtons)
    }

    private fun openTargetSettings(locality: AiTargetLocality) = when (locality) {
        AiTargetLocality.LOCAL -> openModelManager()
        AiTargetLocality.REMOTE -> openOnlineAiSettings()
    }

    private fun AiTarget.chatExecutionProfileId(): String? = when (locality) {
        AiTargetLocality.REMOTE -> null
        AiTargetLocality.LOCAL -> executionProfiles
            .firstOrNull { profile ->
                profile.profileId == AiProviderBackendProfile.CPU && profile.available
            }
            ?.profileId
            ?: executionProfiles.firstOrNull { profile -> profile.available }?.profileId
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

    private fun openOnlineAiSettings() {
        startActivity(Intent(this, OnlineAiSettingsActivity::class.java))
    }

    private fun copyMessage(messageId: Long): Boolean {
        val message = messages.singleOrNull { candidate -> candidate.id == messageId } ?: return false
        if (message.text.isEmpty()) return false
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(getString(R.string.chat_clipboard_label), message.text),
        )
        showSnackbar(drawerLayout, getString(R.string.chat_message_copied))
        return true
    }

    private fun showMessageActions(messageId: Long) {
        val message = messages.singleOrNull { candidate -> candidate.id == messageId } ?: return
        if (message.text.isEmpty()) return
        val actions = when (message.role) {
            ChatMessageRole.USER -> listOf(
                MessageAction.COPY,
                MessageAction.EDIT,
                MessageAction.DELETE,
            )
            ChatMessageRole.ASSISTANT -> listOf(
                MessageAction.COPY,
                MessageAction.REGENERATE,
            )
            ChatMessageRole.NOTICE -> return
        }
        materialDialog()
            .setItems(actions.map { action -> getString(action.labelResource) }.toTypedArray()) { _, index ->
                when (actions[index]) {
                    MessageAction.COPY -> copyMessage(messageId)
                    MessageAction.EDIT -> requestEditMessage(messageId)
                    MessageAction.DELETE -> requestDeleteMessage(messageId)
                    MessageAction.REGENERATE -> requestRegenerateMessage(messageId)
                }
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun requestDeleteMessage(messageId: Long) {
        val impact = ConversationDeletionPolicy.impact(messages, messageId) ?: return
        materialDialog()
            .setTitle(R.string.chat_delete_warning_title)
            .setMessage(getString(R.string.chat_delete_warning_message, impact.laterMessageCount))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_message_delete) { _, _ ->
                deleteMessageAndFollowing(messageId)
            }
            .show()
            .also { dialog ->
                tintDialogButtons(dialog)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(getColor(R.color.validation_error))
            }
    }

    private fun deleteMessageAndFollowing(messageId: Long) {
        val retained = ConversationDeletionPolicy.prefixBefore(messages, messageId) ?: return
        if (isGenerating) stopGeneration(showToast = false)
        closeCurrentBackend()
        closeSearch()
        cancelEditing(restoreDraft = true)
        messages.clear()
        messages.addAll(retained)
        markdownCache.clear()
        markConversationChanged(schedulePersistence = false)
        if (messages.isEmpty()) {
            historyStore.delete(setOf(currentConversationId))
            allowDeletedConversationRevival = false
        } else {
            allowDeletedConversationRevival = true
            persistConversationNow()
        }
        renderTranscript()
        renderTargetUi()
        refreshDrawerHistory()
    }

    private fun requestRegenerateMessage(messageId: Long) {
        val impact = ConversationRegenerationPolicy.impact(messages, messageId) ?: return
        val originalTarget = ConversationTargetPolicy.resolveExact(
            impact.responseTarget,
            targetCatalog,
        )
        if (!isTargetReady(originalTarget)) {
            showUnavailableRegenerationTargetDialog(impact.responseTarget)
            return
        }
        checkNotNull(originalTarget)
        val warnings = buildList {
            if (impact.laterMessageCount > 0) {
                add(getString(R.string.chat_regenerate_warning_message, impact.laterMessageCount))
            }
            if (conversationTarget?.matchesExecutionIdentity(originalTarget) != true) {
                val destinationWarning = if (impact.responseTarget.locality == AiTargetLocality.REMOTE) {
                    getString(R.string.chat_target_change_cloud_warning)
                } else {
                    getString(R.string.chat_target_change_local_warning)
                }
                add(
                    getString(
                        R.string.chat_regenerate_original_target_message,
                        targetSummary(impact.responseTarget),
                        destinationWarning,
                    ),
                )
            }
        }
        if (warnings.isEmpty()) {
            regenerateMessage(impact.userMessageId, impact.responseTarget)
            return
        }
        materialDialog()
            .setTitle(R.string.chat_regenerate_warning_title)
            .setMessage(warnings.joinToString("\n\n"))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.chat_message_regenerate) { _, _ ->
                regenerateMessage(impact.userMessageId, impact.responseTarget)
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun regenerateMessage(
        userMessageId: Long,
        responseTarget: ConversationTargetSnapshot,
    ) {
        val userMessage = messages.singleOrNull { message -> message.id == userMessageId }
            ?.takeIf { message -> message.role == ChatMessageRole.USER }
            ?: return
        if (isGenerating) stopGeneration(showToast = false)
        draftBeforeEditing = null
        editingMessageId = userMessage.id
        input.setText(userMessage.text)
        input.setSelection(input.text.length)
        sendCurrentMessage(targetOverride = responseTarget)
    }

    private fun formatUsage(message: ChatMessage, usage: ChatMessageUsage): String {
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
            ChatConversationPolicy.cumulativeInputTokensThrough(messages, message.id),
        )
    }

    private fun generationFailureText(
        target: ConversationTargetSnapshot,
        error: Throwable,
    ): String {
        val failure = ChatGenerationFailurePolicy.classify(target, error)
        val template = when (failure.locality) {
            AiTargetLocality.LOCAL -> R.string.chat_generation_failure_local
            AiTargetLocality.REMOTE -> R.string.chat_generation_failure_cloud
        }
        return getString(
            template,
            targetSummary(target),
            getString(failure.kind.detailResource()),
        )
    }

    private fun ChatGenerationFailureKind.detailResource(): Int = when (this) {
        ChatGenerationFailureKind.TARGET_UNAVAILABLE ->
            R.string.chat_generation_failure_target_unavailable
        ChatGenerationFailureKind.LOCAL_EXECUTION ->
            R.string.chat_generation_failure_local_execution
        ChatGenerationFailureKind.CREDENTIAL -> R.string.online_ai_test_failure_credential
        ChatGenerationFailureKind.PROFILE_CHANGED ->
            R.string.online_ai_test_failure_profile_changed
        ChatGenerationFailureKind.AUTHENTICATION ->
            R.string.online_ai_test_failure_authentication
        ChatGenerationFailureKind.RATE_LIMIT -> R.string.online_ai_test_failure_rate_limit
        ChatGenerationFailureKind.PROVIDER -> R.string.online_ai_test_failure_provider
        ChatGenerationFailureKind.NETWORK -> R.string.online_ai_test_failure_network
        ChatGenerationFailureKind.METERED_NETWORK -> R.string.online_ai_test_failure_metered
        ChatGenerationFailureKind.TIMEOUT -> R.string.online_ai_test_failure_timeout
        ChatGenerationFailureKind.TLS -> R.string.online_ai_test_failure_tls
        ChatGenerationFailureKind.RESPONSE -> R.string.online_ai_test_failure_response
        ChatGenerationFailureKind.UNKNOWN -> R.string.chat_generation_failure_unknown
    }

    private fun String.withGenerationFailure(failureText: String): String =
        if (isBlank()) failureText else trimEnd() + GENERATION_FAILURE_SEPARATOR + failureText

    private fun saveConversationTarget(outState: Bundle) {
        val snapshot = conversationTarget ?: return
        outState.putString(STATE_CONVERSATION_TARGET_ID, snapshot.targetId)
        outState.putString(STATE_CONVERSATION_TARGET_PROVIDER_ID, snapshot.providerId)
        outState.putString(STATE_CONVERSATION_TARGET_MODEL_ID, snapshot.modelId)
        outState.putString(STATE_CONVERSATION_TARGET_NAME, snapshot.displayName)
        outState.putString(STATE_CONVERSATION_TARGET_LOCALITY, snapshot.locality.name)
    }

    private fun restoreConversationTarget(state: Bundle): ConversationTargetSnapshot? {
        val targetId = state.getString(STATE_CONVERSATION_TARGET_ID) ?: return null
        val providerId = state.getString(STATE_CONVERSATION_TARGET_PROVIDER_ID) ?: return null
        val modelId = state.getString(STATE_CONVERSATION_TARGET_MODEL_ID) ?: return null
        val displayName = state.getString(STATE_CONVERSATION_TARGET_NAME) ?: return null
        val locality = state.getString(STATE_CONVERSATION_TARGET_LOCALITY) ?: return null
        return runCatching {
            ConversationTargetSnapshot(
                targetId = targetId,
                providerId = providerId,
                modelId = modelId,
                displayName = displayName,
                locality = AiTargetLocality.valueOf(locality),
            )
        }.getOrNull()
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
        val targetIds = state.getStringArrayList(STATE_MESSAGE_TARGET_IDS) ?: return false
        val targetProviderIds =
            state.getStringArrayList(STATE_MESSAGE_TARGET_PROVIDER_IDS) ?: return false
        val targetModelIds =
            state.getStringArrayList(STATE_MESSAGE_TARGET_MODEL_IDS) ?: return false
        val targetNames = state.getStringArrayList(STATE_MESSAGE_TARGET_NAMES) ?: return false
        val targetLocalities =
            state.getStringArrayList(STATE_MESSAGE_TARGET_LOCALITIES) ?: return false
        if (
            listOf(
                roles.size,
                statuses.size,
                texts.size,
                inputTokens.size,
                outputTokens.size,
                durations.size,
                targetIds.size,
                targetProviderIds.size,
                targetModelIds.size,
                targetNames.size,
                targetLocalities.size,
            )
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
                val target = if (targetIds[index].isEmpty()) {
                    require(
                        listOf(
                            targetProviderIds[index],
                            targetModelIds[index],
                            targetNames[index],
                            targetLocalities[index],
                        ).all(String::isEmpty),
                    )
                    null
                } else {
                    ConversationTargetSnapshot(
                        targetId = targetIds[index],
                        providerId = targetProviderIds[index],
                        modelId = targetModelIds[index],
                        displayName = targetNames[index],
                        locality = AiTargetLocality.valueOf(targetLocalities[index]),
                    )
                }
                ChatMessage(
                    id = ids[index],
                    role = ChatMessageRole.valueOf(roles[index]),
                    text = texts[index],
                    status = ChatMessageStatus.valueOf(statuses[index]),
                    usage = usage,
                    target = target,
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
        conversationTarget = restoreConversationTarget(state)
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

    private data class BackendContextTelemetry(
        val epoch: Long,
        val committedMessages: List<GenerationMessage>,
        val accounting: ContextAccounting,
    )

    private data class ContextTurnObservation(
        val generationId: Long,
        val backendEpoch: Long,
        val prompt: GenerationMessage,
        val rebuilt: Boolean,
        val inputEstimate: ContextTokenEstimate,
        val calibrationInput: ContextTokenEstimate,
        val coefficientBefore: Double,
        val hardWatermarkTokens: Long,
    )

    private data class CachedMarkdown(
        val source: String,
        val document: MarkdownDocument,
    )

    private data class ViewPadding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        companion object {
            fun from(view: View) = ViewPadding(
                left = view.paddingLeft,
                top = view.paddingTop,
                right = view.paddingRight,
                bottom = view.paddingBottom,
            )
        }
    }

    private enum class TargetCatalogAvailability {
        LOADING,
        UNAVAILABLE,
        READY,
    }

    private enum class MessageAction(val labelResource: Int) {
        COPY(R.string.chat_message_copy),
        EDIT(R.string.chat_message_edit),
        DELETE(R.string.chat_message_delete),
        REGENERATE(R.string.chat_message_regenerate),
    }

    private enum class DrawerConversationAction(val labelResource: Int) {
        COPY_ALL(R.string.chat_history_copy_all),
        EXPORT(R.string.chat_history_export),
        DELETE(R.string.chat_history_delete_selected),
    }

    private inner class TargetChoiceAdapter(labels: Array<String>) : ArrayAdapter<String>(
        this,
        android.R.layout.simple_list_item_single_choice,
        labels,
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getView(position, convertView, parent).apply {
                (this as? CheckedTextView)?.apply {
                    layoutParams = AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT,
                    )
                    minHeight = dp(64)
                    isSingleLine = false
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                    setLineSpacing(0f, 1.08f)
                    setPaddingRelative(dp(16), dp(10), dp(16), dp(10))
                    setTextColor(appPalette.primaryText)
                    checkMarkTintList = controlTintList()
                }
            }
    }

    private companion object {
        const val TAG = "ThreeStoneAiChat"
        const val MENU_NEW_CONVERSATION = 1
        const val MENU_MODEL_SETTINGS = 2
        const val MENU_CONVERSATION_HISTORY = 3
        const val MENU_SEARCH = 4
        const val MENU_APP_SETTINGS = 6
        const val MILLIS_PER_SECOND = 1_000L
        const val DELTA_FLUSH_INTERVAL_MILLIS = 32L
        const val PERSISTENCE_DELAY_MILLIS = 750L
        const val KEYBOARD_REQUEST_DELAY_MILLIS = 180L
        const val NEAR_BOTTOM_DP = 96
        const val DISABLED_ALPHA = 0.42f
        const val PLACEHOLDER_ALPHA = 0.65f
        const val COMPOSER_CONTROL_MIN_HEIGHT_DP = 46
        const val COMPOSER_CORNER_RADIUS_DP = 22
        const val SEARCH_DEBOUNCE_MILLIS = 150L
        const val GENERATION_FAILURE_SEPARATOR = "\n\n---\n\n"
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
        const val STATE_MESSAGE_TARGET_IDS = "chatMessageTargetIds"
        const val STATE_MESSAGE_TARGET_PROVIDER_IDS = "chatMessageTargetProviderIds"
        const val STATE_MESSAGE_TARGET_MODEL_IDS = "chatMessageTargetModelIds"
        const val STATE_MESSAGE_TARGET_NAMES = "chatMessageTargetNames"
        const val STATE_MESSAGE_TARGET_LOCALITIES = "chatMessageTargetLocalities"
        const val STATE_NEXT_MESSAGE_ID = "chatNextMessageId"
        const val STATE_CONVERSATION_ID = "chatConversationId"
        const val STATE_CONVERSATION_CREATED_AT = "chatConversationCreatedAt"
        const val STATE_CONVERSATION_UPDATED_AT = "chatConversationUpdatedAt"
        const val STATE_CONVERSATION_TARGET_ID = "chatConversationTargetId"
        const val STATE_CONVERSATION_TARGET_PROVIDER_ID = "chatConversationTargetProviderId"
        const val STATE_CONVERSATION_TARGET_MODEL_ID = "chatConversationTargetModelId"
        const val STATE_CONVERSATION_TARGET_NAME = "chatConversationTargetName"
        const val STATE_CONVERSATION_TARGET_LOCALITY = "chatConversationTargetLocality"
        const val STATE_COMPOSER_TEXT = "chatComposerText"
        const val STATE_SEARCH_QUERY = "chatSearchQuery"
        const val STATE_EDITING_MESSAGE_ID = "chatEditingMessageId"
        const val STATE_DRAFT_BEFORE_EDITING = "chatDraftBeforeEditing"
        const val STATE_PENDING_DRAWER_EXPORT_ID = "pendingDrawerConversationExportId"
        const val MIME_CONVERSATIONS = "application/vnd.three-stone-ai.conversations"
        const val EXPORT_TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss"

        fun newConversationId(): String = UUID.randomUUID().toString()
    }
}
