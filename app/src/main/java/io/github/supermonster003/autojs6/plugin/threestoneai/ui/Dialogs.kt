package io.github.supermonster003.autojs6.plugin.threestoneai.ui

import android.content.res.ColorStateList
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.supermonster003.autojs6.plugin.threestoneai.AppColorPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.ConfiguredActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.R

/** Base builder used by every dialog in the app. */
internal fun ConfiguredActivity.materialDialog(): MaterialAlertDialogBuilder =
    MaterialAlertDialogBuilder(this)

/** Confirmation dialog; destructive actions color the positive button with the error tone. */
internal fun ConfiguredActivity.confirmDialog(
    title: CharSequence,
    message: CharSequence?,
    @StringRes positiveResource: Int,
    destructive: Boolean = false,
    onPositive: () -> Unit,
): AlertDialog = materialDialog()
    .setTitle(title)
    .setMessage(message)
    .setNegativeButton(android.R.string.cancel, null)
    .setPositiveButton(positiveResource) { _, _ -> onPositive() }
    .show()
    .also { dialog ->
        tintDialogButtons(dialog)
        if (destructive) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(getColor(R.color.validation_error))
        }
    }

/** Single text field dialog built on TextInputLayout with inline validation. */
internal fun ConfiguredActivity.inputDialog(
    title: CharSequence,
    initialValue: CharSequence?,
    message: CharSequence? = null,
    hint: CharSequence? = null,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    maxLength: Int? = null,
    @StringRes positiveResource: Int = android.R.string.ok,
    validate: (String) -> CharSequence? = { null },
    onSubmit: (String) -> Unit,
): AlertDialog {
    val editText = TextInputEditText(this).apply {
        setText(initialValue)
        this.inputType = inputType
        minimumHeight = uiDp(56)
        setPaddingRelative(
            uiDp(Ui.SPACE_LG),
            uiDp(Ui.SPACE_MD),
            uiDp(Ui.SPACE_LG),
            uiDp(Ui.SPACE_MD),
        )
        maxLength?.let { filters = arrayOf(InputFilter.LengthFilter(it)) }
        setTextColor(appPalette.primaryText)
        tintEditText(this)
        backgroundTintList = null
        // Drop the framework underline so the layout installs its outline box instead.
        background = null
    }
    val inputLayout = TextInputLayout(this).apply {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        boxBackgroundColor = android.graphics.Color.TRANSPARENT
        // The programmatic default style is the filled box, whose stroke widths are zero.
        boxStrokeWidth = uiDp(1)
        boxStrokeWidthFocused = uiDp(2)
        val radius = uiDpF(Ui.RADIUS_CONTROL.toFloat())
        setBoxCornerRadii(radius, radius, radius, radius)
        setBoxStrokeColorStateList(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf(),
                ),
                intArrayOf(appPalette.accent, appPalette.outline),
            ),
        )
        boxStrokeErrorColor = ColorStateList.valueOf(getColor(R.color.validation_error))
        setErrorTextColor(ColorStateList.valueOf(getColor(R.color.validation_error)))
        defaultHintTextColor = ColorStateList.valueOf(appPalette.secondaryText)
        hintTextColor = ColorStateList.valueOf(appPalette.accent)
        this.hint = hint
        addView(editText)
    }
    // The box drawable becomes the field's background; a leftover tint would erase it.
    editText.backgroundTintList = null
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_XXL), 0)
        addView(
            inputLayout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
    val dialog = materialDialog()
        .setTitle(title)
        .setMessage(message)
        .setView(container)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(positiveResource, null)
        .create()
    dialog.setOnShowListener {
        tintDialogButtons(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val value = editText.text?.toString().orEmpty()
            val problem = validate(value)
            if (problem != null) {
                inputLayout.error = problem
            } else {
                dialog.dismiss()
                onSubmit(value)
            }
        }
        editText.requestFocus()
    }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()
    return dialog
}

/** Choice-list adapter that keeps radio marks and text legible under the runtime palette. */
private class PaletteChoiceItem(context: android.content.Context) : LinearLayout(context), android.widget.Checkable {
    val indicator = com.google.android.material.radiobutton.MaterialRadioButton(context).apply {
        isClickable = false; isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        minimumWidth = 0; minimumHeight = 0
    }
    val label = TextView(context).apply {
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        textSize = 16f
        isSingleLine = false; maxLines = Int.MAX_VALUE; ellipsize = null
        setLineSpacing(0f, 1.08f)
    }
    init {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(indicator)
        addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    override fun isChecked() = indicator.isChecked
    override fun setChecked(value: Boolean) { indicator.isChecked = value }
    override fun toggle() { isChecked = !isChecked }
    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.RadioButton::class.java.name
        info.isCheckable = true; info.isChecked = isChecked
    }
}

internal class PaletteChoiceAdapter(private val activity: ConfiguredActivity, labels: List<CharSequence>,
                                    private val enabledAt: (Int) -> Boolean = { true }) :
    ArrayAdapter<CharSequence>(activity, android.R.layout.simple_list_item_single_choice, labels) {
    override fun areAllItemsEnabled() = false
    override fun isEnabled(position: Int) = enabledAt(position)
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = (convertView as? PaletteChoiceItem) ?: PaletteChoiceItem(activity)
        item.layoutParams = android.widget.AbsListView.LayoutParams(-1, -2)
        item.minimumHeight = activity.uiDp(if (getItem(position)?.contains('\n') == true) 72 else 56)
        item.setPaddingRelative(activity.uiDp(24), activity.uiDp(12), activity.uiDp(24), activity.uiDp(12))
        item.indicator.layoutParams = LinearLayout.LayoutParams(activity.uiDp(32), activity.uiDp(32)).apply { marginEnd = activity.uiDp(8) }
        item.indicator.buttonTintList = activity.controlTintList()
        item.label.text = getItem(position); item.label.setTextColor(activity.appPalette.primaryText)
        item.contentDescription = item.label.text
        item.isEnabled = isEnabled(position); item.indicator.isEnabled = item.isEnabled
        item.alpha = if (item.isEnabled) 1f else Ui.DISABLED_ALPHA
        item.isChecked = (parent as? android.widget.ListView)?.isItemChecked(position) == true
        item.applyThemedSelectableBackground()
        return item
    }
}

/** Single-choice dialog with palette-consistent items. */
internal fun ConfiguredActivity.singleChoiceDialog(
    title: CharSequence,
    labels: List<CharSequence>,
    checkedIndex: Int,
    enabledAt: (Int) -> Boolean = { true },
    onSelect: (Int) -> Unit,
): AlertDialog = materialDialog()
    .setTitle(title)
    .setSingleChoiceItems(PaletteChoiceAdapter(this, labels, enabledAt), checkedIndex) { dialog, index ->
        dialog.dismiss()
        onSelect(index)
    }
    .setNegativeButton(android.R.string.cancel, null)
    .show()
    .also(::tintDialogButtons)

internal class SheetHandle(
    val dialog: BottomSheetDialog,
    val positiveButton: View?,
)

/**
 * Full-height bottom sheet used for the app's large forms. The content scrolls;
 * the title and the action row stay pinned.
 */
internal fun ConfiguredActivity.formBottomSheet(
    title: CharSequence,
    content: View,
    @StringRes positiveResource: Int? = null,
    onPositive: (() -> Boolean)? = null,
    onDismiss: (() -> Unit)? = null,
): SheetHandle {
    val dialog = BottomSheetDialog(this)
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(0, uiDp(Ui.SPACE_SM), 0, 0)
    }
    column.addView(
        TextView(this).apply {
            text = title
            textSize = Ui.TEXT_PAGE_TITLE
            typeface = Ui.mediumTypeface
            setTextColor(appPalette.primaryText)
            setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_MD))
        },
    )
    column.addView(
        NestedScrollView(this).apply {
            isFillViewport = false
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
    )
    var positive: View? = null
    if (positiveResource != null && onPositive != null) {
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPaddingRelative(uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_SM), uiDp(Ui.SPACE_XXL), uiDp(Ui.SPACE_LG))
            addView(textButton(android.R.string.cancel) { dialog.dismiss() })
            val confirm = filledButton(positiveResource) {
                if (onPositive()) dialog.dismiss()
            }
            addView(
                confirm,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = uiDp(Ui.SPACE_MD) },
            )
            positive = confirm
        }
        column.addView(actions)
    }
    dialog.setContentView(column)
    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    dialog.behavior.skipCollapsed = true
    dialog.window?.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
    )
    onDismiss?.let { callback -> dialog.setOnDismissListener { callback() } }
    dialog.show()
    // Give the sheet a comfortable minimum height for forms.
    (column.parent as? View)?.minimumHeight = (resources.displayMetrics.heightPixels * 0.5f).toInt()
    return SheetHandle(dialog, positive)
}

/** Themed form field label used inside sheets and dialog forms. */
internal fun ConfiguredActivity.formLabel(@StringRes textResource: Int): TextView =
    TextView(this).apply {
        text = getString(textResource)
        textSize = Ui.TEXT_SECTION
        typeface = Ui.mediumTypeface
        setTextColor(appPalette.secondaryText)
        setPaddingRelative(0, uiDp(Ui.SPACE_XL), 0, uiDp(Ui.SPACE_SM))
    }

/** Outlined text field pair for sheet forms. */
internal fun ConfiguredActivity.formTextField(
    initialValue: CharSequence?,
    hint: CharSequence? = null,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    maxLength: Int? = null,
    singleLine: Boolean = true,
): Pair<TextInputLayout, TextInputEditText> {
    val editText = TextInputEditText(this).apply {
        setText(initialValue)
        this.inputType = inputType
        isSingleLine = singleLine
        textSize = Ui.TEXT_BODY
        minimumHeight = uiDp(56)
        setPaddingRelative(
            uiDp(Ui.SPACE_LG),
            uiDp(Ui.SPACE_MD),
            uiDp(Ui.SPACE_LG),
            uiDp(Ui.SPACE_MD),
        )
        maxLength?.let { filters = arrayOf(InputFilter.LengthFilter(it)) }
        setTextColor(appPalette.primaryText)
        tintEditText(this)
        backgroundTintList = null
        // Drop the framework underline so the layout installs its outline box instead.
        background = null
    }
    val layout = TextInputLayout(this).apply {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        boxBackgroundColor = android.graphics.Color.TRANSPARENT
        // The programmatic default style is the filled box, whose stroke widths are zero.
        boxStrokeWidth = uiDp(1)
        boxStrokeWidthFocused = uiDp(2)
        val radius = uiDpF(Ui.RADIUS_CONTROL.toFloat())
        setBoxCornerRadii(radius, radius, radius, radius)
        setBoxStrokeColorStateList(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(appPalette.accent, appPalette.outline),
            ),
        )
        boxStrokeErrorColor = ColorStateList.valueOf(getColor(R.color.validation_error))
        setErrorTextColor(ColorStateList.valueOf(getColor(R.color.validation_error)))
        defaultHintTextColor = ColorStateList.valueOf(appPalette.secondaryText)
        hintTextColor = ColorStateList.valueOf(appPalette.accent)
        this.hint = hint
        addView(editText)
    }
    // The box drawable becomes the field's background; a leftover tint would erase it.
    editText.backgroundTintList = null
    return layout to editText
}

/** Appearance choice draft. Only the positive action calls [onConfirm]; all dismissal paths discard it. */
internal fun ConfiguredActivity.confirmedChoiceDialog(title: CharSequence, labels: List<CharSequence>, checkedIndex: Int,
                                          onConfirm: (Int) -> Unit): AlertDialog {
    var draft = checkedIndex
    return materialDialog().setTitle(title)
        .setSingleChoiceItems(PaletteChoiceAdapter(this, labels), checkedIndex) { _, index -> draft = index }
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm(draft) }
        .show().also {
            tintDialogButtons(it)
            val width = minOf(uiDp(560), this.resources.displayMetrics.widthPixels - uiDp(48))
            it.window?.setBackgroundDrawable(roundedFill(appPalette.surface, 24))
            it.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.window?.decorView?.post {
                val maximum = (this.resources.displayMetrics.heightPixels * 0.85f).toInt()
                if ((it.window?.decorView?.height ?: 0) > maximum) it.window?.setLayout(width, maximum)
            }
            it.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
                textSize = 20f
                setTextColor(appPalette.primaryText)
            }
            it.listView.post { it.listView.setSelection(0) }
        }
}
