package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.text.Editable
import android.text.format.Formatter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiEffortSupport
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.accentRipple
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.applyThemedSelectableBackground
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formBottomSheet
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formTextField
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.hairline
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedRippleFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.switchRow
import java.util.Locale

/*
 * Model switcher bottom sheet (roadmap P2.4, D78 item 1). The layout follows the ideas of RikkaHub's
 * model list and reasoning picker (search, favorites on top, groups, inline capability tags, a
 * per-conversation effort row); no RikkaHub code is used (AGPL-3.0).
 */

/** [SEARCH] is the single ungrouped list of search results; it has no header. */
internal enum class ModelPickerSectionKind { FAVORITES, PROFILE, LOCAL, SEARCH }

internal enum class ModelPickerTag { VISION, REASONING, FAST }

/** One group of the switcher; [groupKey] is the profile (or local) key, or [ModelPickerPolicy.FAVORITES_KEY]. */
internal data class ModelPickerSection(
    val kind: ModelPickerSectionKind,
    val groupKey: String,
    val targets: List<AiTarget>,
)

internal object ModelPickerPolicy {
    const val FAVORITES_KEY = "favorites"
    const val LOCAL_KEY = "local"
    const val SEARCH_KEY = "search"

    /**
     * Every target appears once (D101). Without a query, favorites form the top group and are left
     * out of their own groups, followed by one group per online service and one for local models,
     * each in catalog order; empty groups are dropped. With a query the matches form one ungrouped
     * list in catalog order: every word of [query] must appear in the target's [searchText]
     * (case-insensitive).
     */
    fun sections(
        targets: List<AiTarget>,
        favorites: Set<String>,
        query: String,
        searchText: (AiTarget) -> String,
    ): List<ModelPickerSection> {
        val words = words(query)
        if (words.isNotEmpty()) {
            val matching = targets.filter { target ->
                val text = searchText(target).lowercase(Locale.ROOT)
                words.all(text::contains)
            }
            return if (matching.isEmpty()) {
                emptyList()
            } else {
                listOf(ModelPickerSection(ModelPickerSectionKind.SEARCH, SEARCH_KEY, matching))
            }
        }
        val sections = ArrayList<ModelPickerSection>()
        val (favoriteTargets, others) = targets.partition { target -> target.targetId in favorites }
        if (favoriteTargets.isNotEmpty()) {
            sections += ModelPickerSection(ModelPickerSectionKind.FAVORITES, FAVORITES_KEY, favoriteTargets)
        }
        others.groupBy(::groupKey).forEach { (key, group) ->
            val kind = if (key == LOCAL_KEY) ModelPickerSectionKind.LOCAL else ModelPickerSectionKind.PROFILE
            sections += ModelPickerSection(kind, key, group)
        }
        return sections
    }

    fun groupKey(target: AiTarget): String = when (target.locality) {
        AiTargetLocality.LOCAL -> LOCAL_KEY
        AiTargetLocality.REMOTE -> "profile:" + (target.profileId ?: target.providerId)
    }

    /** Inline tags: image input, reasoning (effort can be chosen) and fast output. */
    fun tags(target: AiTarget): List<ModelPickerTag> = buildList {
        val capabilities = target.capabilities
        if (capabilities.vision) add(ModelPickerTag.VISION)
        if (capabilities.reasoning || capabilities.effortLevels.isNotEmpty()) add(ModelPickerTag.REASONING)
        if (capabilities.fastMode) add(ModelPickerTag.FAST)
    }

    /** Effort choices for the current conversation; empty hides the row (local targets, no effort). */
    fun effortChoices(target: AiTarget?): List<GenerationEffort> {
        val levels = target?.capabilities?.effortLevels.orEmpty()
        return if (levels.isEmpty()) emptyList() else listOf(GenerationEffort.DEFAULT) + levels
    }

    /** The choice to highlight: what the target will actually receive for [requested]. */
    fun highlightedEffort(target: AiTarget, requested: GenerationEffort?): GenerationEffort =
        OnlineAiEffortSupport.effective(requested, target.capabilities.effortLevels)

    private fun words(query: String): List<String> {
        val normalized = String(
            query.lowercase(Locale.ROOT).map { character -> if (character.isWhitespace()) ' ' else character }
                .toCharArray(),
        )
        return normalized.split(' ').filter(String::isNotEmpty)
    }
}

/** Favorite targets of the model switcher, kept on this device only (allowBackup is off). */
internal class ModelPickerFavoritesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): Set<String> = preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()

    fun toggle(targetId: String): Set<String> {
        val updated = load().toMutableSet()
        if (!updated.add(targetId)) updated.remove(targetId)
        preferences.edit().putStringSet(KEY_FAVORITES, updated).apply()
        return updated
    }

    /** Follows a service's reordered models (D122): favorites keep pointing at the same model. */
    fun replace(mapping: Map<String, String>) {
        if (mapping.isEmpty()) return
        val current = load()
        val updated = current.mapTo(mutableSetOf()) { targetId -> mapping[targetId] ?: targetId }
        if (updated != current) preferences.edit().putStringSet(KEY_FAVORITES, updated).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "model_picker"
        const val KEY_FAVORITES = "favorite_target_ids"
    }
}

/** What the sheet shows; [effort] and [fast] are null when the current target does not support them. */
internal class ModelPickerState(
    val targets: List<AiTarget>,
    val selectedTargetId: String?,
    val favorites: Set<String>,
    val currentTarget: AiTarget?,
    val effort: GenerationEffort?,
    val fast: Boolean?,
)

internal class ModelPickerActions(
    val rowTitle: (AiTarget) -> String,
    val rowSubtitle: (AiTarget) -> String?,
    val groupTitle: (ModelPickerSection) -> String,
    val searchText: (AiTarget) -> String,
    val isReady: (AiTarget) -> Boolean,
    val onSelect: (AiTarget) -> Unit,
    val onToggleFavorite: (String) -> Set<String>,
    val onEffort: (GenerationEffort) -> Unit,
    val onFast: (Boolean) -> Unit,
    /** Opens the models and services page; null hides the link (the page itself picks targets too). */
    val onManage: (() -> Unit)?,
)

/**
 * Row, group and search texts for targets, shared by the chat switcher and the default target picker
 * on the models and services page (roadmap 1.7.0 P5.2), so both lists read the same.
 */
internal class TargetPickerTexts(private val context: Context) {
    private val text = ChatGenerationText(context)

    fun name(target: AiTarget): String = text.displayName(ConversationTargetSnapshot.from(target))

    /** Local models by name, service models by model ID (their service is the group). */
    fun rowTitle(target: AiTarget): String =
        if (target.locality == AiTargetLocality.LOCAL) name(target) else target.modelId

    /**
     * Sizes of imported local models by model ID, read once from the catalog; a model missing here
     * (or an unreadable catalog) simply shows no size.
     */
    private val localSizes: Map<String, Long> by lazy {
        runCatching {
            ModelRepository(context).catalogSnapshot().entries.associate { entry -> entry.modelId to entry.sizeBytes }
        }.getOrDefault(emptyMap())
    }

    /**
     * Service models show their service and local models their size (D101), so a row reads the
     * same in the favorites group and in search results; unavailable targets say so.
     */
    fun rowSubtitle(target: AiTarget): String? {
        val parts = buildList {
            when (target.locality) {
                AiTargetLocality.REMOTE -> add(name(target))
                AiTargetLocality.LOCAL -> localSizes[target.modelId]?.takeIf { it > 0L }?.let { bytes ->
                    add(Formatter.formatFileSize(context, bytes))
                }
            }
            if (!isUsable(target)) add(context.getString(R.string.chat_target_unavailable_suffix).trimStart(' ', '|'))
        }
        return parts.joinToString(" | ").ifEmpty { null }
    }

    fun groupTitle(section: ModelPickerSection): String = when (section.kind) {
        ModelPickerSectionKind.FAVORITES -> context.getString(R.string.model_picker_favorites)
        ModelPickerSectionKind.LOCAL -> context.getString(R.string.model_picker_local)
        ModelPickerSectionKind.SEARCH -> ""
        ModelPickerSectionKind.PROFILE -> section.targets.first().let { target ->
            context.getString(
                R.string.chat_model_switch_summary,
                name(target),
                text.providerLabel(target.providerId, target.locality),
            )
        }
    }

    fun searchText(target: AiTarget): String = listOf(
        target.modelId,
        name(target),
        text.providerLabel(target.providerId, target.locality),
        text.localityLabel(target.locality),
    ).joinToString(" ")

    /** One line for the default target row: "model | service" or "name | Local models". */
    fun summary(target: AiTarget): String = context.getString(
        R.string.models_default_target_format,
        rowTitle(target),
        if (target.locality == AiTargetLocality.LOCAL) {
            context.getString(R.string.model_picker_local)
        } else {
            name(target)
        },
    )

    companion object {
        fun isUsable(target: AiTarget): Boolean =
            target.configured && target.available && target.capabilities.streaming
    }
}

internal fun ConfiguredActivity.showModelPickerSheet(
    state: ModelPickerState,
    actions: ModelPickerActions,
    title: String = getString(R.string.chat_model_switch_title),
): BottomSheetDialog {
    var favorites = state.favorites
    var query = ""
    lateinit var dialog: BottomSheetDialog
    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val (searchLayout, searchInput) = formTextField(null, hint = getString(R.string.model_picker_search))
    content.addView(
        searchLayout,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = uiDp(Ui.SPACE_XXL)
            marginEnd = uiDp(Ui.SPACE_XXL)
        },
    )
    addConversationControls(content, state, actions)
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(list)

    fun renderList() {
        list.removeAllViews()
        val sections = ModelPickerPolicy.sections(state.targets, favorites, query, actions.searchText)
        if (sections.isEmpty()) {
            list.addView(
                TextView(this).apply {
                    text = getString(R.string.model_picker_empty)
                    textSize = Ui.TEXT_BODY
                    setTextColor(appPalette.secondaryText)
                    gravity = Gravity.CENTER
                    setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_XXL))
                },
            )
            return
        }
        sections.forEach { section ->
            if (section.kind != ModelPickerSectionKind.SEARCH) {
                list.addView(pickerSectionHeader(actions.groupTitle(section)))
            }
            section.targets.forEach { target ->
                list.addView(
                    pickerRow(
                        target = target,
                        selected = target.targetId == state.selectedTargetId,
                        favorite = target.targetId in favorites,
                        actions = actions,
                        onPick = {
                            dialog.dismiss()
                            actions.onSelect(target)
                        },
                        onFavorite = {
                            favorites = actions.onToggleFavorite(target.targetId)
                            renderList()
                        },
                    ),
                )
            }
        }
    }

    searchInput.addTextChangedListener(
        object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(text: Editable?) {
                query = text?.toString().orEmpty()
                renderList()
            }
        },
    )
    renderList()
    val onManage = actions.onManage
    if (onManage != null) addManageLink(content) {
        dialog.dismiss()
        onManage()
    }
    dialog = formBottomSheet(title, content).dialog
    return dialog
}

private fun ConfiguredActivity.addManageLink(content: LinearLayout, onClick: () -> Unit) {
    content.addView(hairline(insetStartDp = 0))
    content.addView(
        TextView(this).apply {
            text = getString(R.string.model_picker_manage)
            textSize = Ui.TEXT_ITEM
            typeface = Ui.mediumTypeface
            setTextColor(AppColorPolicy.readableAccent(appPalette.accent, appPalette.surface))
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = uiDp(56)
            setPaddingRelative(uiDp(Ui.SPACE_XXL), 0, uiDp(Ui.SPACE_XXL), 0)
            isClickable = true
            isFocusable = true
            applyThemedSelectableBackground()
            setOnClickListener { onClick() }
        },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
    )
}

/** Effort row and fast switch for the conversation on screen, shown only when the target supports them. */
private fun ConfiguredActivity.addConversationControls(
    content: LinearLayout,
    state: ModelPickerState,
    actions: ModelPickerActions,
) {
    val target = state.currentTarget ?: return
    val choices = ModelPickerPolicy.effortChoices(target).takeIf { state.effort != null }.orEmpty()
    val showFast = state.fast != null && target.capabilities.fastMode
    if (choices.isEmpty() && !showFast) return
    if (choices.isNotEmpty()) {
        content.addView(pickerSectionHeader(getString(R.string.model_picker_effort)))
        var highlighted = ModelPickerPolicy.highlightedEffort(target, state.effort)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPaddingRelative(uiDp(Ui.SPACE_XXL), 0, uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
        }
        val pills = choices.map { effort -> effort to TextView(this) }

        fun renderPills() {
            pills.forEach { (effort, pill) ->
                val selected = effort == highlighted
                pill.isSelected = selected
                pill.setTextColor(if (selected) appPalette.onAccent else appPalette.primaryText)
                pill.background = if (selected) {
                    roundedFill(appPalette.accent, Ui.RADIUS_BUBBLE)
                } else {
                    roundedRippleFill(appPalette.surfaceVariant, accentRipple(appPalette.accent), Ui.RADIUS_BUBBLE)
                }
            }
        }

        pills.forEachIndexed { index, (effort, pill) ->
            pill.apply {
                text = effortLabel(effort)
                contentDescription = getString(R.string.model_picker_effort) + ": " + text
                textSize = Ui.TEXT_SECONDARY
                typeface = Ui.mediumTypeface
                gravity = Gravity.CENTER
                minimumHeight = uiDp(36)
                minWidth = uiDp(48)
                setPaddingRelative(uiDp(Ui.SPACE_MD), 0, uiDp(Ui.SPACE_MD), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (highlighted == effort) return@setOnClickListener
                    highlighted = effort
                    renderPills()
                    actions.onEffort(effort)
                }
            }
            row.addView(
                pill,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { if (index > 0) marginStart = uiDp(Ui.SPACE_SM) },
            )
        }
        renderPills()
        content.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }
    if (showFast) {
        content.addView(
            switchRow(
                title = getString(R.string.model_picker_fast),
                checked = state.fast == true,
                onToggle = actions.onFast,
            ).view,
        )
    }
    content.addView(hairline(insetStartDp = 0))
}

private fun ConfiguredActivity.pickerSectionHeader(title: String): TextView = TextView(this).apply {
    text = title
    textSize = Ui.TEXT_SECTION
    typeface = Ui.mediumTypeface
    setTextColor(appPalette.secondaryText)
    setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_LG), uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM))
}

private fun ConfiguredActivity.pickerRow(
    target: AiTarget,
    selected: Boolean,
    favorite: Boolean,
    actions: ModelPickerActions,
    onPick: () -> Unit,
    onFavorite: () -> Unit,
): View {
    val ready = actions.isReady(target)
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = uiDp(56)
        setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_SM))
        isClickable = true
        isFocusable = true
        isSelected = selected
        applyThemedSelectableBackground()
        setOnClickListener { onPick() }
    }
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        alpha = if (ready) 1f else Ui.DISABLED_ALPHA
    }
    column.addView(
        TextView(this).apply {
            text = actions.rowTitle(target)
            textSize = Ui.TEXT_ITEM
            setTextColor(appPalette.primaryText)
            if (selected) typeface = Ui.mediumTypeface
        },
    )
    actions.rowSubtitle(target)?.takeIf(String::isNotEmpty)?.let { subtitle ->
        column.addView(
            TextView(this).apply {
                text = subtitle
                textSize = Ui.TEXT_SECONDARY
                setTextColor(appPalette.secondaryText)
            },
        )
    }
    val tags = ModelPickerPolicy.tags(target)
    if (tags.isNotEmpty()) {
        val tagRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPaddingRelative(0, uiDp(Ui.SPACE_XS), 0, 0)
        }
        tags.forEachIndexed { index, tag ->
            tagRow.addView(
                TextView(this).apply {
                    text = getString(
                        when (tag) {
                            ModelPickerTag.VISION -> R.string.model_picker_tag_vision
                            ModelPickerTag.REASONING -> R.string.model_picker_tag_reasoning
                            ModelPickerTag.FAST -> R.string.model_picker_tag_fast
                        },
                    )
                    textSize = 10.5f
                    setTextColor(appPalette.secondaryText)
                    background = roundedFill(android.graphics.Color.TRANSPARENT, Ui.RADIUS_BUBBLE, appPalette.outline)
                    setPaddingRelative(uiDp(6), uiDp(1), uiDp(6), uiDp(1))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { if (index > 0) marginStart = uiDp(Ui.SPACE_XS) },
            )
        }
        column.addView(tagRow)
    }
    row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    if (selected) {
        row.addView(
            ImageView(this).apply {
                setImageDrawable(tintedDrawable(R.drawable.ic_check_circle_24, appPalette.accent))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(uiDp(20), uiDp(20)).apply { marginStart = uiDp(Ui.SPACE_SM) },
        )
    }
    row.addView(
        ImageView(this).apply {
            setImageDrawable(
                tintedDrawable(
                    if (favorite) R.drawable.ic_star_24 else R.drawable.ic_star_outline_24,
                    if (favorite) appPalette.accent else appPalette.secondaryText,
                ),
            )
            contentDescription = getString(if (favorite) R.string.model_picker_unfavorite else R.string.model_picker_favorite)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            applyThemedSelectableBackground(borderless = true)
            setOnClickListener { onFavorite() }
        },
        LinearLayout.LayoutParams(uiDp(Ui.ICON_BUTTON_SIZE), uiDp(Ui.ICON_BUTTON_SIZE)),
    )
    return row
}
