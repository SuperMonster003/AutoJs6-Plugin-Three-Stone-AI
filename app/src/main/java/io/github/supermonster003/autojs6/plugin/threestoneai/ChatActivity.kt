package io.github.supermonster003.autojs6.plugin.threestoneai

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
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AbsListView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
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
    private lateinit var updateController: AppUpdateController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerHistoryColumn: LinearLayout
    private lateinit var toolbar: Toolbar
    private lateinit var targetLocalityStatus: TextView
    private lateinit var targetStatus: TextView
    private lateinit var searchInput: EditText
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
        updateController = AppUpdateController(this)
        if (!restoreTranscript(savedInstanceState)) {
            restoreStoredConversation(
                intent.getStringExtra(ConversationNavigation.EXTRA_CONVERSATION_ID)
                    ?: historyStore.lastConversationId(),
            )
        }
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
            icon = tintedDrawable(R.drawable.ic_add_24, appPalette.onPrimary)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_CONVERSATION_HISTORY, 1, R.string.chat_history_title).apply {
            icon = tintedDrawable(R.drawable.ic_history_24, appPalette.onPrimary)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        searchMenuItem = menu.add(0, MENU_SEARCH, 2, R.string.chat_search_menu).apply {
            icon = tintedDrawable(R.drawable.ic_search_24, appPalette.onPrimary)
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
                appPalette.onPrimary,
            )
            isEnabled = if (searchExpanded) hasSearchResults else messages.isNotEmpty()
        }
        menu.findItem(MENU_CONVERSATION_HISTORY)?.apply {
            title = getString(
                if (searchExpanded) R.string.chat_search_next else R.string.chat_history_title,
            )
            icon = tintedDrawable(
                if (searchExpanded) R.drawable.ic_arrow_down_24 else R.drawable.ic_history_24,
                appPalette.onPrimary,
            )
            isEnabled = !searchExpanded || hasSearchResults
        }
        menu.findItem(MENU_SEARCH)?.apply {
            isEnabled = searchExpanded || messages.any { message -> message.text.isNotBlank() }
            icon = tintedDrawable(R.drawable.ic_search_24, appPalette.onPrimary)
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
            uiSettings = latestSettings
            applyUiSettings()
        }
        refreshTargetCatalog(importCoordinator.managerState())
        refreshDrawerHistory()
        updateController.checkAutomaticallyIfDue()
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
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
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
            setBackgroundColor(getColor(R.color.window_background))
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
        toolbar.navigationIcon = tintedDrawable(R.drawable.ic_menu_24, appPalette.onPrimary)
        toolbar.setNavigationContentDescription(R.string.navigation_open_drawer)
        toolbar.setNavigationOnClickListener {
            persistConversationNow()
            refreshDrawerHistory()
            drawerLayout.openDrawer(GravityCompat.START)
        }
        installToolbarTitleLongPress()
        content.addView(toolbar)
        content.addView(createTargetBar())
        content.addView(View(this).apply { setBackgroundColor(getColor(R.color.divider)) },
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
        isClickable = true
        isFocusable = true
        contentDescription = getString(R.string.chat_open_model_switcher)
        minimumHeight = dp(42)
        setPaddingRelative(dp(16), dp(4), dp(10), dp(4))
        background = roundedRipple(
            fillColor = R.color.window_background,
            rippleColor = R.color.chat_ripple,
            radiusDp = 0,
        )
        setOnClickListener { showTargetSelector() }

        targetLocalityStatus = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.text_color_secondary))
            setPaddingRelative(0, 0, dp(10), 0)
        }
        addView(targetLocalityStatus)
        targetStatus = TextView(context).apply {
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(getColor(R.color.text_color_secondary))
        }
        addView(targetStatus, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = "\u203a"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_color_secondary))
        })
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
            setTextColor(appPalette.onPrimary)
            setHintTextColor(AppColorPolicy.withAlpha(appPalette.onPrimary, 0xB3))
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
                    if (searchExpanded) {
                        refreshSearchResults(selectFirst = true, locate = true)
                    }
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
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
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
                LinearLayout.LayoutParams(0, dp(70), 1f),
            )
            addView(
                drawerActionButton(R.drawable.ic_model_24, R.string.model_manager_title) {
                    openModelManager()
                },
                LinearLayout.LayoutParams(0, dp(70), 1f),
            )
            addView(
                drawerActionButton(R.drawable.ic_restart_24, R.string.drawer_restart) {
                    restartApplication()
                },
                LinearLayout.LayoutParams(0, dp(70), 1f),
            )
            addView(
                drawerActionButton(R.drawable.ic_exit_24, R.string.drawer_exit) {
                    exitApplication()
                },
                LinearLayout.LayoutParams(0, dp(70), 1f),
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
        applySelectableBackground(this)
        addView(ImageView(context).apply {
            setImageDrawable(tintedDrawable(iconResource, appPalette.secondaryText))
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(22), dp(22)))
        addView(TextView(context).apply {
            text = getString(titleResource)
            textSize = 11.5f
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
        val targetWidthDp = 288f.coerceIn(screenWidthDp * 0.4f, screenWidthDp * 0.9f)
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
            background = roundedRippleColor(
                fillColor = if (current) appPalette.assistantSurface else appPalette.windowBackground,
                rippleColor = getColor(R.color.chat_ripple),
                radiusDp = 11,
            )
            addView(TextView(context).apply {
                text = conversation.title
                textSize = 14.5f
                typeface = if (current) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
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
        }.also { row ->
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(4) }
        }
    }

    private fun installToolbarTitleLongPress() {
        toolbar.post {
            val titleView = (0 until toolbar.childCount)
                .asSequence()
                .map(toolbar::getChildAt)
                .filterIsInstance<TextView>()
                .firstOrNull { candidate -> candidate.text?.toString() == toolbar.title?.toString() }
                ?: return@post
            titleView.isLongClickable = true
            titleView.setOnLongClickListener {
                startActivity(Intent(this, AppSettingsActivity::class.java))
                true
            }
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
                text = getString(R.string.chat_choose_target)
                isAllCaps = false
                setOnClickListener { showTargetSelector() }
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
            isBaselineAligned = false

            input = EditText(context).apply {
                textSize = uiSettings.fontSize.inputSp
                maxLines = 6
                minLines = 1
                minimumHeight = dp(COMPOSER_CONTROL_MIN_HEIGHT_DP)
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions = editorImeOptions()
                filters = arrayOf(
                    InputFilter.LengthFilter(ChatConversationPolicy.MAXIMUM_INPUT_CHARACTERS),
                )
                setTextColor(getColor(R.color.text_color_primary))
                setPaddingRelative(dp(12), dp(8), dp(12), dp(8))
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
            addView(
                FrameLayout(context).apply {
                    background = roundedDrawableColor(
                        fillColor = appPalette.inputSurface,
                        radiusDp = COMPOSER_CORNER_RADIUS_DP,
                        strokeColor = appPalette.chatBorder,
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

            sendButton = Button(context).apply {
                text = getString(R.string.chat_send)
                textSize = 13.5f
                isAllCaps = false
                minWidth = dp(COMPOSER_SEND_MIN_WIDTH_DP)
                minimumWidth = dp(COMPOSER_SEND_MIN_WIDTH_DP)
                minimumHeight = composerSingleLineHeight()
                stateListAnimator = null
                elevation = 0f
                translationZ = 0f
                setPaddingRelative(dp(12), 0, dp(12), 0)
                setTextColor(appPalette.onPrimary)
                background = roundedRippleColor(
                    fillColor = appPalette.primary,
                    rippleColor = AppColorPolicy.withAlpha(appPalette.onPrimary, 0x40),
                    radiusDp = COMPOSER_CORNER_RADIUS_DP,
                )
                setOnClickListener {
                    if (isGenerating) stopGeneration() else sendCurrentMessage()
                }
            }
            addView(sendButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                composerSingleLineHeight(),
            ).apply {
                gravity = Gravity.BOTTOM
                marginStart = dp(8)
            })
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

    private fun composerSingleLineHeight(): Int = (
        input.lineHeight + input.compoundPaddingTop + input.compoundPaddingBottom
        ).coerceAtLeast(dp(COMPOSER_CONTROL_MIN_HEIGHT_DP))

    private fun syncComposerControlHeight() {
        if (!::input.isInitialized || !::sendButton.isInitialized) return
        input.minimumHeight = dp(COMPOSER_CONTROL_MIN_HEIGHT_DP)
        val height = composerSingleLineHeight()
        sendButton.minimumHeight = height
        val params = sendButton.layoutParams ?: return
        if (params.height != height) {
            params.height = height
            sendButton.layoutParams = params
        }
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
        emptyState.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
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
        input.isEnabled = targetReady && !isGenerating
        input.hint = null
        sendButton.text = getString(
            when {
                isGenerating -> R.string.chat_stop
                editingMessageId != null -> R.string.chat_save_and_send
                else -> R.string.chat_send
            },
        )
        sendButton.isEnabled = isGenerating || (targetReady && input.text.toString().isNotBlank())
        sendButton.alpha = if (sendButton.isEnabled) 1f else DISABLED_ALPHA
        if (::suggestionButtons.isInitialized) {
            suggestionButtons.forEach { it.isEnabled = targetReady && !isGenerating }
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
                    message.usage?.let(::formatUsage).orEmpty()
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
        if (failed) applySelectableBackground(meta)
        meta.setTextColor(if (failed) appPalette.accent else appPalette.secondaryText)
        meta.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
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
        var reusableBackend: AiBackendSession? = null
        var backendToReplace: AiBackendSession? = null
        synchronized(backendLock) {
            val reusable = target.capabilities.persistentSession &&
                activeBackend != null && activeBackendTarget?.let { activeTarget ->
                    activeTarget.matchesExecutionIdentity(target)
                } == true &&
                !ChatConversationPolicy.shouldRotateBackend(completedTurnsOnBackend)
            if (reusable) {
                reusableBackend = activeBackend
            } else {
                backendToReplace = activeBackend
                activeBackend = null
                activeBackendTarget = null
                completedTurnsOnBackend = 0
            }
        }

        val continuation = reusableBackend
        if (continuation != null) {
            val actualTarget = ConversationTargetSnapshot.from(continuation.target)
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

        val history = ChatConversationPolicy.historyForFreshBackend(messages)
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
            val installed = synchronized(backendLock) {
                if (!isGenerationCurrent(generationId) || activeBackend != null) {
                    false
                } else {
                    activeBackend = created
                    activeBackendTarget = actualTarget
                    completedTurnsOnBackend = history.size / MESSAGES_PER_TURN
                    true
                }
            }
            if (!installed) {
                runCatching(created::close)
                return@submitBackendWork
            }
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
            Toast.makeText(this, R.string.chat_generation_stopped, Toast.LENGTH_SHORT).show()
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
        toolbar.subtitle = null
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
            syncComposerControlHeight()
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
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
        const val MESSAGES_PER_TURN = 2
        const val MILLIS_PER_SECOND = 1_000L
        const val DELTA_FLUSH_INTERVAL_MILLIS = 32L
        const val PERSISTENCE_DELAY_MILLIS = 750L
        const val KEYBOARD_REQUEST_DELAY_MILLIS = 180L
        const val NEAR_BOTTOM_DP = 96
        const val DISABLED_ALPHA = 0.42f
        const val PLACEHOLDER_ALPHA = 0.65f
        const val COMPOSER_CONTROL_MIN_HEIGHT_DP = 40
        const val COMPOSER_SEND_MIN_WIDTH_DP = 60
        const val COMPOSER_CORNER_RADIUS_DP = 12
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

        fun newConversationId(): String = UUID.randomUUID().toString()
    }
}
