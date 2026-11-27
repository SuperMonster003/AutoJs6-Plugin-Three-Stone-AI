package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderProblem
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderSecretsUpdate
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderValueKind
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiReservedHeaderOwner
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.SettingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.applyThemedSelectableBackground
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formLabel
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.formTextField
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.setRowEnabled
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.settingRow
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import java.net.URI
import java.util.Locale

/** The folded "Common presets" of the add-header dialog (roadmap 1.8.0 D166, D169). */
internal enum class ServiceHeaderPreset(
    @param:StringRes val titleResource: Int,
    @param:StringRes val summaryResource: Int,
    val headers: List<OnlineAiCustomHeader>,
) {
    OPENCODE_GO(
        R.string.service_headers_preset_opencode,
        R.string.service_headers_preset_opencode_summary,
        listOf(OnlineAiCustomHeader(ServiceHeaderPolicy.SESSION_HEADER_NAME, OnlineAiHeaderValueKind.CONVERSATION)),
    ),
    OPENROUTER(
        R.string.service_headers_preset_openrouter,
        R.string.service_headers_preset_openrouter_summary,
        listOf(
            OnlineAiCustomHeader("HTTP-Referer", OnlineAiHeaderValueKind.FIXED, ServiceHeaderPolicy.OPENROUTER_REFERER),
            OnlineAiCustomHeader("X-OpenRouter-Title", OnlineAiHeaderValueKind.FIXED, ServiceHeaderPolicy.OPENROUTER_TITLE),
        ),
    ),
    HELICONE(
        R.string.service_headers_preset_helicone,
        R.string.service_headers_preset_helicone_summary,
        listOf(OnlineAiCustomHeader("Helicone-Auth", OnlineAiHeaderValueKind.SECRET)),
    ),
    CLOUDFLARE_AI_GATEWAY(
        R.string.service_headers_preset_cloudflare,
        R.string.service_headers_preset_cloudflare_summary,
        listOf(OnlineAiCustomHeader("cf-aig-authorization", OnlineAiHeaderValueKind.SECRET)),
    ),
    ;

    /** A preset with a secret entry fills the form instead, so the user types the value. */
    val needsValue: Boolean
        get() = headers.any { header -> header.kind == OnlineAiHeaderValueKind.SECRET }
}

internal sealed interface ServiceHeaderAddResult {
    data class Added(
        val headers: List<OnlineAiCustomHeader>,
        val added: List<OnlineAiCustomHeader>,
    ) : ServiceHeaderAddResult

    /** Every entry to add already has a header of the same name. */
    data object AlreadyPresent : ServiceHeaderAddResult

    data class TooMany(val maximum: Int) : ServiceHeaderAddResult
}

/** What the editor knows about the value of a secret header; the value itself is never shown. */
internal enum class ServiceHeaderSecretState {
    /** Typed in this editor and not saved yet. */
    TYPED,

    /** Stored for the saved service and still usable with the draft's provider and host. */
    SET,

    /** Missing: imported, copied, renamed, or dropped because the provider or host changed. */
    NOT_SET,
}

/** Rules of the request header editor that need no Android views (P12.1, P12.2). */
internal object ServiceHeaderPolicy {
    const val SESSION_HEADER_NAME = "x-opencode-session"
    const val OPENROUTER_REFERER = "https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI"
    const val OPENROUTER_TITLE = "3-Stone AI"
    private const val OPENCODE_HOST = "opencode.ai"
    private const val MISSING_SESSION_ERROR = "MissingSessionID"

    /** True for opencode.ai and its subdomains; an unparsable address is not OpenCode. */
    fun isOpenCodeBaseUrl(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl.trim()).host }.getOrNull()
            ?.lowercase(Locale.ROOT)
            ?.trimEnd('.')
            ?: return false
        return host == OPENCODE_HOST || host.endsWith(".$OPENCODE_HOST")
    }

    fun hasSessionHeader(headers: List<OnlineAiCustomHeader>): Boolean =
        headers.any { header -> header.key == SESSION_HEADER_NAME }

    /** D169: the details page offers to add the session header for OpenCode Go addresses. */
    fun needsSessionHeader(baseUrl: String, headers: List<OnlineAiCustomHeader>): Boolean =
        isOpenCodeBaseUrl(baseUrl) && !hasSessionHeader(headers)

    /** OpenCode Go answers HTTP 400 "MissingSessionID" without the session header (2026-11-15). */
    fun isMissingSessionError(text: CharSequence?): Boolean =
        text?.contains(MISSING_SESSION_ERROR, ignoreCase = true) == true

    /** Appends the entries whose names are not used yet, keeping the 16 entry limit. */
    fun add(
        headers: List<OnlineAiCustomHeader>,
        additions: List<OnlineAiCustomHeader>,
    ): ServiceHeaderAddResult {
        val keys = headers.mapTo(HashSet(), OnlineAiCustomHeader::key)
        val fresh = additions.filter { header -> keys.add(header.key) }
        return when {
            fresh.isEmpty() -> ServiceHeaderAddResult.AlreadyPresent
            headers.size + fresh.size > OnlineAiHeaderPolicy.MAXIMUM_HEADERS ->
                ServiceHeaderAddResult.TooMany(OnlineAiHeaderPolicy.MAXIMUM_HEADERS)
            else -> ServiceHeaderAddResult.Added(headers + fresh, fresh)
        }
    }

    fun addSessionHeader(headers: List<OnlineAiCustomHeader>): ServiceHeaderAddResult =
        add(headers, ServiceHeaderPreset.OPENCODE_GO.headers)

    fun kindOf(conversation: Boolean, secret: Boolean): OnlineAiHeaderValueKind = when {
        conversation -> OnlineAiHeaderValueKind.CONVERSATION
        secret -> OnlineAiHeaderValueKind.SECRET
        else -> OnlineAiHeaderValueKind.FIXED
    }

    /**
     * The problem the registry would report for a typed secret value (same trimming and checks as
     * OnlineAiHeaderSecretValues.encode), without making a String copy of it.
     */
    fun secretValueProblem(index: Int, value: CharSequence): OnlineAiHeaderProblem? {
        val (start, end) = trimmedBounds(value)
        return when {
            start == end -> OnlineAiHeaderProblem.ValueEmpty(index)
            (start until end).any { position -> !OnlineAiHeaderPolicy.isValueCharacter(value[position]) } ->
                OnlineAiHeaderProblem.ValueInvalidCharacter(index)
            end - start > OnlineAiHeaderPolicy.MAXIMUM_VALUE_BYTES ->
                OnlineAiHeaderProblem.ValueTooLong(index, OnlineAiHeaderPolicy.MAXIMUM_VALUE_BYTES)
            else -> null
        }
    }

    /** The trimmed characters of [value] in a new array; the caller owns and later clears it. */
    fun trimmedCharacters(value: CharSequence): CharArray {
        val (start, end) = trimmedBounds(value)
        return CharArray(end - start) { position -> value[start + position] }
    }

    /** The list position a problem refers to; null for problems of the whole list. */
    fun problemIndex(problem: OnlineAiHeaderProblem): Int? = when (problem) {
        is OnlineAiHeaderProblem.TooManyHeaders, is OnlineAiHeaderProblem.TotalTooLarge -> null
        is OnlineAiHeaderProblem.NameEmpty -> problem.index
        is OnlineAiHeaderProblem.NameTooLong -> problem.index
        is OnlineAiHeaderProblem.NameInvalidCharacter -> problem.index
        is OnlineAiHeaderProblem.NameDuplicate -> problem.index
        is OnlineAiHeaderProblem.NameReserved -> problem.index
        is OnlineAiHeaderProblem.ValueEmpty -> problem.index
        is OnlineAiHeaderProblem.ValueTooLong -> problem.index
        is OnlineAiHeaderProblem.ValueInvalidCharacter -> problem.index
        is OnlineAiHeaderProblem.ValueNotAllowed -> problem.index
    }

    /** The problems of the entry at [index] plus those of the whole list. */
    fun problemsOf(problems: List<OnlineAiHeaderProblem>, index: Int): List<OnlineAiHeaderProblem> =
        problems.filter { problem -> problemIndex(problem).let { it == null || it == index } }

    fun isNameProblem(problem: OnlineAiHeaderProblem): Boolean =
        problem is OnlineAiHeaderProblem.NameEmpty ||
            problem is OnlineAiHeaderProblem.NameTooLong ||
            problem is OnlineAiHeaderProblem.NameInvalidCharacter ||
            problem is OnlineAiHeaderProblem.NameDuplicate ||
            problem is OnlineAiHeaderProblem.NameReserved

    fun isValueProblem(problem: OnlineAiHeaderProblem): Boolean =
        problem is OnlineAiHeaderProblem.ValueEmpty ||
            problem is OnlineAiHeaderProblem.ValueTooLong ||
            problem is OnlineAiHeaderProblem.ValueInvalidCharacter ||
            problem is OnlineAiHeaderProblem.ValueNotAllowed

    fun secretState(key: String, typed: Set<String>, usableStored: Set<String>): ServiceHeaderSecretState = when (key) {
        in typed -> ServiceHeaderSecretState.TYPED
        in usableStored -> ServiceHeaderSecretState.SET
        else -> ServiceHeaderSecretState.NOT_SET
    }

    /** Secret entries that have neither a typed nor a usable stored value. */
    fun missingSecrets(
        headers: List<OnlineAiCustomHeader>,
        typed: Set<String>,
        usableStored: Set<String>,
    ): List<OnlineAiCustomHeader> = headers.filter { header ->
        header.kind == OnlineAiHeaderValueKind.SECRET &&
            secretState(header.key, typed, usableStored) == ServiceHeaderSecretState.NOT_SET
    }

    private fun trimmedBounds(value: CharSequence): Pair<Int, Int> {
        var start = 0
        var end = value.length
        while (start < end && value[start].isWhitespace()) start++
        while (end > start && value[end - 1].isWhitespace()) end--
        return start to end
    }
}

/** Texts of the request header editor, also used by the details page and the services page. */
internal class ServiceHeaderTexts(private val context: Context) {
    fun kind(kind: OnlineAiHeaderValueKind): String = context.getString(
        when (kind) {
            OnlineAiHeaderValueKind.FIXED -> R.string.service_headers_kind_fixed
            OnlineAiHeaderValueKind.SECRET -> R.string.service_headers_kind_secret
            OnlineAiHeaderValueKind.CONVERSATION -> R.string.service_headers_kind_conversation
        },
    )

    fun problem(problem: OnlineAiHeaderProblem): String = when (problem) {
        is OnlineAiHeaderProblem.TooManyHeaders ->
            context.getString(R.string.service_headers_problem_too_many, problem.maximum)
        is OnlineAiHeaderProblem.NameEmpty -> context.getString(R.string.service_headers_problem_name_empty)
        is OnlineAiHeaderProblem.NameTooLong ->
            context.getString(R.string.service_headers_problem_name_too_long, problem.maximumBytes)
        is OnlineAiHeaderProblem.NameInvalidCharacter ->
            context.getString(R.string.service_headers_problem_name_invalid)
        is OnlineAiHeaderProblem.NameDuplicate ->
            context.getString(R.string.service_headers_problem_name_duplicate)
        is OnlineAiHeaderProblem.NameReserved -> context.getString(
            when (problem.owner) {
                OnlineAiReservedHeaderOwner.PLUGIN -> R.string.service_headers_problem_name_reserved_plugin
                OnlineAiReservedHeaderOwner.TRANSPORT -> R.string.service_headers_problem_name_reserved_transport
            },
        )
        is OnlineAiHeaderProblem.ValueEmpty -> context.getString(R.string.service_headers_problem_value_empty)
        is OnlineAiHeaderProblem.ValueTooLong ->
            context.getString(R.string.service_headers_problem_value_too_long, problem.maximumBytes)
        is OnlineAiHeaderProblem.ValueInvalidCharacter ->
            context.getString(R.string.service_headers_problem_value_invalid)
        is OnlineAiHeaderProblem.ValueNotAllowed ->
            context.getString(R.string.service_headers_problem_value_not_allowed)
        is OnlineAiHeaderProblem.TotalTooLarge ->
            context.getString(R.string.service_headers_problem_total_too_large, problem.maximumBytes)
    }

    /** A save failure: the problem, prefixed with the header's name when it is about one entry. */
    fun saveProblem(problem: OnlineAiHeaderProblem, headers: List<OnlineAiCustomHeader>): String {
        val header = ServiceHeaderPolicy.problemIndex(problem)?.let(headers::getOrNull)
        val text = problem(problem)
        return if (header == null || header.name.isBlank()) {
            context.getString(R.string.service_headers_save_invalid_list, text)
        } else {
            context.getString(R.string.service_headers_save_invalid, header.name.trim(), text)
        }
    }

    /** Summary of the "Request headers" row: count, names and how each value is produced (D166). */
    fun summary(headers: List<OnlineAiCustomHeader>): String {
        if (headers.isEmpty()) return context.getString(R.string.service_headers_none)
        val entries = headers.joinToString(", ") { header ->
            context.getString(R.string.service_headers_entry_format, header.name, kind(header.kind))
        }
        return context.resources.getQuantityString(R.plurals.service_headers_summary, headers.size, headers.size, entries)
    }

    fun missingSecrets(headers: List<OnlineAiCustomHeader>): String =
        context.getString(R.string.service_headers_summary_secret_missing, headers.joinToString(", ") { it.name })

    /** Second line of an entry: the value of a fixed header, the state of a secret one. */
    fun entry(header: OnlineAiCustomHeader, state: ServiceHeaderSecretState): String = when (header.kind) {
        OnlineAiHeaderValueKind.FIXED ->
            context.getString(R.string.service_headers_entry_value, kind(header.kind), header.value.orEmpty())
        OnlineAiHeaderValueKind.SECRET -> context.getString(
            R.string.service_headers_entry_value,
            kind(header.kind),
            context.getString(
                when (state) {
                    ServiceHeaderSecretState.TYPED -> R.string.service_headers_value_pending
                    ServiceHeaderSecretState.SET -> R.string.service_headers_value_set
                    ServiceHeaderSecretState.NOT_SET -> R.string.service_headers_value_not_set
                },
            ),
        )
        OnlineAiHeaderValueKind.CONVERSATION -> kind(header.kind)
    }

    /** Appended to a failure explanation that names OpenCode Go's MissingSessionID error (D169). */
    fun withMissingSessionHint(failureText: String): String =
        if (ServiceHeaderPolicy.isMissingSessionError(failureText)) {
            failureText + "\n\n" + context.getString(R.string.service_headers_missing_session_hint)
        } else {
            failureText
        }
}

/**
 * The "Request headers" page of the service details page and its add / edit dialog (roadmap 1.8.0
 * P12.1, D166). Edits change the draft only; the details page's "Save" stores them. Typed secret
 * values are kept here, keyed by lower-case name, until they are saved or the page closes; they
 * are never shown again, never put into a String and cleared as soon as they are no longer needed.
 */
internal class ServiceHeaderEditor(
    private val activity: ConfiguredActivity,
    initialHeaders: List<OnlineAiCustomHeader>,
    typedSecrets: Map<String, CharArray>,
    private val host: Host,
) {
    interface Host {
        val feedbackRoot: View

        /** Secret header names whose stored values the draft keeps (none after a destination change). */
        fun usableStoredSecrets(): Set<String>

        /** True while a changed provider or host drops the stored secret values on save. */
        fun storedSecretsDropped(): Boolean

        fun onHeadersChanged()
    }

    private val texts = ServiceHeaderTexts(activity)
    private val typed = LinkedHashMap<String, CharArray>()

    var headers: List<OnlineAiCustomHeader> = initialHeaders
        private set

    @VisibleForTesting
    internal var dialog: AlertDialog? = null
        private set

    val page: LinearLayout
    private val destinationNote: TextView
    private val emptyNote: TextView
    private val list: LinearLayout
    private val addRow: SettingRow

    init {
        val secretKeys = OnlineAiHeaderPolicy.secretKeys(initialHeaders).toSet()
        typedSecrets.forEach { (key, value) ->
            if (key in secretKeys && value.isNotEmpty()) typed[key] = value else value.fill('\u0000')
        }
        destinationNote = note(R.string.service_headers_destination_changed).apply {
            setTextColor(activity.getColor(R.color.validation_error))
        }
        emptyNote = note(R.string.service_headers_empty)
        list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        addRow = activity.settingRow(
            title = activity.getString(R.string.service_headers_add),
            summary = activity.getString(R.string.service_headers_add_summary, OnlineAiHeaderPolicy.MAXIMUM_HEADERS),
            iconResource = R.drawable.ic_add_24,
            onClick = { showDialog(null) },
        )
        page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(note(R.string.service_headers_help))
            addView(destinationNote)
            addView(emptyNote)
            addView(list)
            addView(addRow.view)
        }
        refresh()
    }

    val typedSecretCount: Int
        get() = typed.size

    val typedSecretKeys: Set<String>
        get() = typed.keys.toSet()

    /** Copies for the saved-instance-state Bundle; the Bundle's owner clears them after restoring. */
    fun copyTypedSecrets(): Map<String, CharArray> =
        typed.mapValuesTo(LinkedHashMap()) { (_, value) -> value.copyOf() }

    /**
     * The secret value update for one registry save. Build it right at the call: the registry
     * takes ownership of the copies and clears them; the editor keeps its own until [onSaved].
     */
    fun secretsUpdate(): OnlineAiHeaderSecretsUpdate {
        val secretKeys = OnlineAiHeaderPolicy.secretKeys(headers).toSet()
        val values = LinkedHashMap<String, CharArray>()
        typed.forEach { (key, value) -> if (key in secretKeys) values[key] = value.copyOf() }
        return if (values.isEmpty()) {
            OnlineAiHeaderSecretsUpdate.Keep
        } else {
            OnlineAiHeaderSecretsUpdate.Put.takingOwnership(values)
        }
    }

    /** After a successful save: typed values are stored, so they are cleared here. */
    fun onSaved(storedHeaders: List<OnlineAiCustomHeader>) {
        clearTyped()
        headers = storedHeaders
        refresh()
    }

    fun missingSecrets(): List<OnlineAiCustomHeader> =
        ServiceHeaderPolicy.missingSecrets(headers, typed.keys, host.usableStoredSecrets())

    /** The OpenCode Go hint and the MissingSessionID entry points (D169); true when it was added. */
    fun addSessionHeader(): Boolean {
        val result = ServiceHeaderPolicy.addSessionHeader(headers)
        val message = when (result) {
            is ServiceHeaderAddResult.Added -> {
                headers = result.headers
                changed()
                activity.getString(R.string.service_headers_session_added, ServiceHeaderPolicy.SESSION_HEADER_NAME)
            }
            ServiceHeaderAddResult.AlreadyPresent ->
                activity.getString(R.string.service_headers_session_exists, ServiceHeaderPolicy.SESSION_HEADER_NAME)
            is ServiceHeaderAddResult.TooMany ->
                activity.getString(R.string.service_headers_limit_reached, result.maximum)
        }
        activity.showSnackbar(host.feedbackRoot, message)
        return result is ServiceHeaderAddResult.Added
    }

    /** Redraws the page; the details page calls it when the provider or host changes. */
    fun refresh() {
        val usableStored = host.usableStoredSecrets()
        list.removeAllViews()
        headers.forEachIndexed { index, header -> list.addView(row(index, header, usableStored)) }
        emptyNote.visibility = if (headers.isEmpty()) View.VISIBLE else View.GONE
        val dropped = host.storedSecretsDropped() &&
            headers.any { header -> header.kind == OnlineAiHeaderValueKind.SECRET }
        destinationNote.visibility = if (dropped) View.VISIBLE else View.GONE
        val full = headers.size >= OnlineAiHeaderPolicy.MAXIMUM_HEADERS
        addRow.setRowEnabled(!full)
        addRow.summaryView.text = if (full) {
            activity.getString(R.string.service_headers_limit_reached, OnlineAiHeaderPolicy.MAXIMUM_HEADERS)
        } else {
            activity.getString(R.string.service_headers_add_summary, OnlineAiHeaderPolicy.MAXIMUM_HEADERS)
        }
        activity.applyThemeToControls(list)
    }

    /** Closes the dialog and clears every typed value; call when the page goes away. */
    fun close() {
        dialog?.dismiss()
        dialog = null
        clearTyped()
    }

    // ---- Rows -------------------------------------------------------------------------------

    private fun row(index: Int, header: OnlineAiCustomHeader, usableStored: Set<String>): View {
        val state = ServiceHeaderPolicy.secretState(header.key, typed.keys, usableStored)
        val row = activity.settingRow(
            title = header.name,
            summary = texts.entry(header, state),
            iconResource = when (header.kind) {
                OnlineAiHeaderValueKind.FIXED -> R.drawable.ic_code_24
                OnlineAiHeaderValueKind.SECRET -> R.drawable.ic_key_24
                OnlineAiHeaderValueKind.CONVERSATION -> R.drawable.ic_history_24
            },
            showChevron = false,
            onClick = { showDialog(index) },
        )
        row.view.tag = header.key
        row.titleView.maxLines = 2
        row.titleView.ellipsize = TextUtils.TruncateAt.MIDDLE
        row.summaryView.maxLines = 3
        row.summaryView.ellipsize = TextUtils.TruncateAt.END
        if (header.kind == OnlineAiHeaderValueKind.SECRET && state == ServiceHeaderSecretState.NOT_SET) {
            row.summaryView.setTextColor(activity.getColor(R.color.validation_error))
        }
        val more = ImageButton(activity).apply {
            setImageDrawable(activity.tintedDrawable(R.drawable.ic_more_vert_24, activity.appPalette.secondaryText))
            background = null
            applyThemedSelectableBackground(borderless = true)
            contentDescription = activity.getString(R.string.service_headers_row_actions, header.name)
            setOnClickListener { anchor -> showRowActions(anchor, index) }
        }
        row.view.addView(
            more,
            LinearLayout.LayoutParams(activity.uiDp(Ui.ICON_BUTTON_SIZE), activity.uiDp(Ui.ICON_BUTTON_SIZE)).apply {
                marginStart = activity.uiDp(Ui.SPACE_SM)
            },
        )
        return row.view
    }

    private fun showRowActions(anchor: View, index: Int) {
        PopupMenu(activity, anchor).apply {
            menu.add(0, ACTION_EDIT, 0, R.string.service_headers_action_edit)
            menu.add(0, ACTION_DELETE, 1, R.string.service_headers_action_delete)
            setOnMenuItemClickListener { item ->
                applyRowAction(index, item.itemId)
                true
            }
            show()
        }
    }

    /** One entry of a row's menu; the device test calls it in place of the popup. */
    @VisibleForTesting
    internal fun applyRowAction(index: Int, action: Int) {
        val header = headers.getOrNull(index) ?: return
        when (action) {
            ACTION_EDIT -> showDialog(index)
            ACTION_DELETE -> {
                typed.remove(header.key)?.fill('\u0000')
                headers = headers.filterIndexed { position, _ -> position != index }
                changed()
                activity.showSnackbar(
                    host.feedbackRoot,
                    activity.getString(R.string.service_headers_deleted, header.name),
                )
            }
        }
    }

    // ---- Dialog -----------------------------------------------------------------------------

    /** The add (null) or edit dialog: centered, fixed Cancel / OK, validation keeps it open. */
    @VisibleForTesting
    internal fun showDialog(index: Int?) {
        if (index == null && headers.size >= OnlineAiHeaderPolicy.MAXIMUM_HEADERS) {
            activity.showSnackbar(
                host.feedbackRoot,
                activity.getString(R.string.service_headers_limit_reached, OnlineAiHeaderPolicy.MAXIMUM_HEADERS),
            )
            return
        }
        dialog?.dismiss()
        val form = HeaderForm(index)
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(form.root)
        }
        val created = activity.materialDialog()
            .setTitle(if (index == null) R.string.service_headers_dialog_add_title else R.string.service_headers_dialog_edit_title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        created.setOnShowListener {
            activity.tintDialogButtons(created)
            created.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (form.submit()) created.dismiss()
            }
            val width = minOf(activity.uiDp(560), activity.resources.displayMetrics.widthPixels - activity.uiDp(48))
            created.window?.setBackgroundDrawable(activity.roundedFill(activity.appPalette.surface, Ui.RADIUS_SHEET))
            created.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            created.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
                textSize = 20f
                setTextColor(activity.appPalette.primaryText)
            }
        }
        created.setOnDismissListener {
            form.clearValue()
            if (dialog === created) dialog = null
        }
        // The value field may hold a secret; dialogs have their own window, so mark it too.
        created.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        @Suppress("DEPRECATION") // adjustResize keeps the fields above the keyboard on every API level.
        created.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        activity.applyThemeToControls(scroll)
        dialog = created
        created.show()
    }

    /** The views and rules of one add / edit dialog. */
    private inner class HeaderForm(private val index: Int?) {
        private val existing = index?.let(headers::getOrNull)
        private val oldKey = existing?.key
        private var secretTouched = existing != null
        private var updatingSecret = false

        private val nameField: TextInputLayout
        private val nameInput: TextInputEditText
        private val valueField: TextInputLayout
        private val valueInput: TextInputEditText
        private val fixedMode: MaterialRadioButton
        private val conversationMode: MaterialRadioButton
        private val secretBox: MaterialCheckBox
        private val secretHelp: TextView
        private val conversationHelp: TextView
        private val listError: TextView
        private var presets: View? = null
        private var presetList: View? = null
        val root: LinearLayout

        init {
            val (nameLayout, name) = activity.formTextField(
                initialValue = existing?.name,
                hint = activity.getString(R.string.service_headers_name),
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                maxLength = OnlineAiHeaderPolicy.MAXIMUM_NAME_BYTES * 2,
            )
            nameField = nameLayout
            nameInput = name
            nameField.placeholderText = activity.getString(R.string.service_headers_name_hint)
            nameField.tag = TAG_NAME

            fixedMode = radio(R.string.service_headers_mode_value).apply { id = View.generateViewId() }
            conversationMode = radio(R.string.service_headers_kind_conversation).apply { id = View.generateViewId() }
            val modes = RadioGroup(activity).apply {
                orientation = RadioGroup.VERTICAL
                addView(fixedMode)
                addView(conversationMode)
            }

            val (valueLayout, value) = activity.formTextField(
                initialValue = existing?.takeIf { it.kind == OnlineAiHeaderValueKind.FIXED }?.value,
                hint = activity.getString(R.string.service_headers_value),
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                maxLength = OnlineAiHeaderPolicy.MAXIMUM_VALUE_BYTES * 2,
            )
            valueField = valueLayout
            valueInput = value
            valueField.tag = TAG_VALUE
            valueInput.isSaveEnabled = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                valueInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                valueInput.setAutofillHints(null)
            }

            secretBox = MaterialCheckBox(activity).apply {
                text = activity.getString(R.string.service_headers_secret)
                textSize = 14f
                minimumHeight = activity.uiDp(Ui.TOUCH_TARGET)
                setTextColor(activity.appPalette.primaryText)
                tag = TAG_SECRET
                isChecked = existing?.kind == OnlineAiHeaderValueKind.SECRET
            }
            secretHelp = help(R.string.service_headers_secret_help)
            conversationHelp = help(R.string.service_headers_conversation_help)
            listError = help(R.string.service_headers_name).apply {
                text = null
                tag = TAG_ERROR
                setTextColor(activity.getColor(R.color.validation_error))
                visibility = View.GONE
            }

            root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(activity.uiDp(Ui.SPACE_XXL), activity.uiDp(Ui.SPACE_SM), activity.uiDp(Ui.SPACE_XXL), 0)
            }
            if (existing == null) root.addView(presetSection())
            root.addView(fullWidth(nameField, if (existing == null) Ui.SPACE_LG else 0))
            root.addView(activity.formLabel(R.string.service_headers_mode))
            root.addView(modes)
            root.addView(fullWidth(valueField, Ui.SPACE_SM))
            root.addView(secretBox)
            root.addView(secretHelp)
            root.addView(conversationHelp)
            root.addView(listError)

            if (existing?.kind == OnlineAiHeaderValueKind.CONVERSATION) conversationMode.isChecked = true else fixedMode.isChecked = true
            modes.setOnCheckedChangeListener { _, _ -> update() }
            secretBox.setOnCheckedChangeListener { _, _ ->
                if (!updatingSecret) secretTouched = true
                update()
            }
            nameInput.addTextChangedListener(afterChange {
                nameField.error = null
                listError.visibility = View.GONE
                if (!secretTouched && fixedMode.isChecked) {
                    updatingSecret = true
                    secretBox.isChecked = OnlineAiHeaderPolicy.suggestsSecret(nameInput.text?.toString().orEmpty())
                    updatingSecret = false
                }
                update()
            })
            valueInput.addTextChangedListener(afterChange {
                valueField.error = null
                listError.visibility = View.GONE
            })
            update()
        }

        /** Shows the controls of the chosen mode and the value field's state. */
        private fun update() {
            val conversation = conversationMode.isChecked
            val secret = !conversation && secretBox.isChecked
            valueField.visibility = if (conversation) View.GONE else View.VISIBLE
            secretBox.visibility = valueField.visibility
            secretHelp.visibility = if (secret) View.VISIBLE else View.GONE
            conversationHelp.visibility = if (conversation) View.VISIBLE else View.GONE
            if (secret) {
                if (valueField.endIconMode != TextInputLayout.END_ICON_PASSWORD_TOGGLE) {
                    valueField.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                    valueInput.transformationMethod = PasswordTransformationMethod.getInstance()
                }
            } else if (valueField.endIconMode != TextInputLayout.END_ICON_NONE) {
                valueField.endIconMode = TextInputLayout.END_ICON_NONE
                valueInput.transformationMethod = null
            }
            valueField.helperText = when {
                !secret || valueInput.text?.isNotEmpty() == true -> null
                typedValueKey() != null -> activity.getString(R.string.service_headers_value_pending_keep)
                storedValueKept() -> activity.getString(R.string.service_headers_value_secret_keep)
                else -> null
            }
        }

        /** The key of a typed value this entry keeps when the field stays empty. */
        private fun typedValueKey(): String? =
            oldKey?.takeIf { existing?.kind == OnlineAiHeaderValueKind.SECRET && typed.containsKey(it) }

        /** A stored value is kept only while the entry stays secret under the same name. */
        private fun storedValueKept(): Boolean {
            val key = oldKey ?: return false
            return existing?.kind == OnlineAiHeaderValueKind.SECRET &&
                key == OnlineAiHeaderPolicy.key(nameInput.text?.toString().orEmpty()) &&
                key in host.usableStoredSecrets()
        }

        /** Validates and applies the entry; false keeps the dialog open with the reasons shown. */
        fun submit(): Boolean {
            nameField.error = null
            valueField.error = null
            listError.visibility = View.GONE
            val conversation = conversationMode.isChecked
            val kind = ServiceHeaderPolicy.kindOf(conversation, secretBox.isChecked)
            val name = nameInput.text?.toString().orEmpty()
            val newKey = OnlineAiHeaderPolicy.key(name)
            val valueText = valueInput.text ?: return false
            val entry = OnlineAiCustomHeader(
                name = name,
                kind = kind,
                value = if (kind == OnlineAiHeaderValueKind.FIXED) valueText.toString() else null,
            )
            val target = index ?: headers.size
            val candidate = headers.toMutableList().apply { if (index == null) add(entry) else set(index, entry) }
            val secretBytes = HashMap<String, Int>()
            typed.forEach { (key, value) -> if (key != oldKey) secretBytes[key] = value.size }
            if (kind == OnlineAiHeaderValueKind.SECRET) {
                val ownBytes = if (valueText.isNotEmpty()) ServiceHeaderPolicy.trimmedCharacters(valueText).let { chars ->
                    chars.size.also { chars.fill('\u0000') }
                } else {
                    typedValueKey()?.let { typed[it]?.size } ?: 0
                }
                secretBytes[newKey] = ownBytes
            }
            val problems = ServiceHeaderPolicy.problemsOf(OnlineAiHeaderPolicy.problems(candidate, secretBytes), target)
            problems.firstOrNull(ServiceHeaderPolicy::isNameProblem)?.let { nameField.error = texts.problem(it) }
            problems.firstOrNull(ServiceHeaderPolicy::isValueProblem)?.let { valueField.error = texts.problem(it) }
            problems.firstOrNull { ServiceHeaderPolicy.problemIndex(it) == null }?.let { problem ->
                listError.text = texts.problem(problem)
                listError.visibility = View.VISIBLE
            }
            var valid = problems.isEmpty()
            if (kind == OnlineAiHeaderValueKind.SECRET && valueField.error == null) {
                val secretProblem = when {
                    valueText.isNotEmpty() -> ServiceHeaderPolicy.secretValueProblem(target, valueText)
                        ?.let(texts::problem)
                    typedValueKey() != null || storedValueKept() -> null
                    existing?.kind == OnlineAiHeaderValueKind.SECRET && oldKey != newKey &&
                        oldKey in host.usableStoredSecrets() ->
                        activity.getString(R.string.service_headers_problem_secret_renamed)
                    else -> texts.problem(OnlineAiHeaderProblem.ValueEmpty(target))
                }
                if (secretProblem != null) {
                    valueField.error = secretProblem
                    valid = false
                }
            }
            if (!valid) return false

            when {
                kind == OnlineAiHeaderValueKind.SECRET && valueText.isNotEmpty() -> {
                    val characters = ServiceHeaderPolicy.trimmedCharacters(valueText)
                    oldKey?.let { typed.remove(it)?.fill('\u0000') }
                    typed.remove(newKey)?.fill('\u0000')
                    typed[newKey] = characters
                }
                kind == OnlineAiHeaderValueKind.SECRET -> {
                    val moved = typedValueKey()?.takeIf { it != newKey }?.let(typed::remove)
                    if (moved != null) typed[newKey] = moved
                }
                else -> oldKey?.let { typed.remove(it)?.fill('\u0000') }
            }
            headers = OnlineAiHeaderPolicy.normalize(candidate)
            clearValue()
            changed()
            return true
        }

        fun clearValue() {
            valueInput.text?.clear()
        }

        private fun presetSection(): View {
            var expanded = false
            val entries = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            ServiceHeaderPreset.entries.forEach { preset ->
                entries.addView(
                    activity.settingRow(
                        title = activity.getString(preset.titleResource),
                        summary = activity.getString(preset.summaryResource),
                        showChevron = false,
                        onClick = { applyPreset(preset) },
                    ).view.apply {
                        tag = preset.name
                        setPaddingRelative(activity.uiDp(Ui.SPACE_SM), paddingTop, activity.uiDp(Ui.SPACE_SM), paddingBottom)
                    },
                )
            }
            val header = activity.settingRow(
                title = activity.getString(R.string.service_headers_presets),
                iconResource = R.drawable.ic_expand_more_24,
                showChevron = false,
                onClick = {},
            )
            header.view.tag = TAG_PRESETS
            header.view.setPaddingRelative(0, header.view.paddingTop, 0, header.view.paddingBottom)
            ViewCompat.setAccessibilityHeading(header.view, true)
            val icon = header.view.getChildAt(0)
            val updateExpansion = {
                entries.visibility = if (expanded) View.VISIBLE else View.GONE
                icon?.rotation = if (expanded) 180f else 0f
                ViewCompat.setStateDescription(
                    header.view,
                    activity.getString(if (expanded) R.string.online_ai_catalog_expanded else R.string.online_ai_catalog_collapsed),
                )
            }
            header.view.setOnClickListener {
                expanded = !expanded
                updateExpansion()
            }
            updateExpansion()
            presetList = entries
            return LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(header.view)
                addView(entries)
                presets = this
            }
        }

        /** Ready presets are added at once; a preset that needs a secret value fills the form. */
        private fun applyPreset(preset: ServiceHeaderPreset) {
            if (!preset.needsValue) {
                when (val result = ServiceHeaderPolicy.add(headers, preset.headers)) {
                    is ServiceHeaderAddResult.Added -> {
                        headers = result.headers
                        dialog?.dismiss()
                        changed()
                        activity.showSnackbar(
                            host.feedbackRoot,
                            activity.getString(
                                R.string.service_headers_preset_added,
                                result.added.joinToString(", ") { it.name },
                            ),
                        )
                    }
                    ServiceHeaderAddResult.AlreadyPresent -> showListError(activity.getString(R.string.service_headers_preset_exists))
                    is ServiceHeaderAddResult.TooMany ->
                        showListError(activity.getString(R.string.service_headers_limit_reached, result.maximum))
                }
                return
            }
            val header = preset.headers.first()
            nameInput.setText(header.name)
            fixedMode.isChecked = true
            secretTouched = true
            secretBox.isChecked = true
            valueField.placeholderText = activity.getString(R.string.service_headers_bearer_placeholder)
            presetList?.visibility = View.GONE
            presets?.findViewWithTag<View>(TAG_PRESETS)?.let { row -> (row as? ViewGroup)?.getChildAt(0)?.rotation = 0f }
            valueInput.requestFocus()
            update()
        }

        private fun showListError(text: String) {
            listError.text = text
            listError.visibility = View.VISIBLE
        }

        private fun radio(@StringRes textResource: Int) = MaterialRadioButton(activity).apply {
            text = activity.getString(textResource)
            textSize = 14f
            minimumHeight = activity.uiDp(Ui.TOUCH_TARGET)
            setTextColor(activity.appPalette.primaryText)
        }

        private fun help(@StringRes textResource: Int) = TextView(activity).apply {
            text = activity.getString(textResource)
            textSize = Ui.TEXT_SECTION
            setTextColor(activity.appPalette.secondaryText)
            setLineSpacing(0f, Ui.LINE_SPACING_BODY)
            setPaddingRelative(0, activity.uiDp(Ui.SPACE_XS), 0, activity.uiDp(Ui.SPACE_SM))
        }

        private fun fullWidth(field: TextInputLayout, topMarginDp: Int): TextInputLayout = field.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = activity.uiDp(topMarginDp) }
        }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private fun changed() {
        refresh()
        host.onHeadersChanged()
    }

    private fun clearTyped() {
        typed.values.forEach { value -> value.fill('\u0000') }
        typed.clear()
    }

    private fun note(@StringRes textResource: Int) = TextView(activity).apply {
        text = activity.getString(textResource)
        textSize = 14f
        setTextColor(activity.appPalette.secondaryText)
        setLineSpacing(0f, 1.1f)
        setPaddingRelative(activity.uiDp(Ui.SPACE_XXL), activity.uiDp(Ui.SPACE_SM), activity.uiDp(Ui.SPACE_XXL), activity.uiDp(Ui.SPACE_SM))
    }

    private fun afterChange(action: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = action()
    }

    companion object {
        const val ACTION_EDIT = 2601
        const val ACTION_DELETE = 2602
        const val TAG_NAME = "serviceHeaderName"
        const val TAG_VALUE = "serviceHeaderValue"
        const val TAG_SECRET = "serviceHeaderSecret"
        const val TAG_ERROR = "serviceHeaderError"
        const val TAG_PRESETS = "serviceHeaderPresets"
    }
}
