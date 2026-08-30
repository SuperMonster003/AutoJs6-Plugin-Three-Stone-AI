package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.text.format.Formatter
import com.google.android.material.snackbar.Snackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.download.AvailableLiteRtModel
import io.github.supermonster003.autojs6.plugin.threestoneai.download.AvailableLiteRtModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.download.LiteRtModelCapability
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.ContentPadding
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.buildScaffold
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.cardContainer
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.cardListParams
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.emptyStateView
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.textButton
import java.text.NumberFormat

class LiteRtModelCatalogActivity : ConfiguredActivity() {
    private lateinit var modelRows: LinearLayout
    private lateinit var screenRoot: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        renderModels("")
    }

    private fun createContentView(): View {
        val scaffold = buildScaffold(
            R.string.litert_catalog_title,
            contentPadding = ContentPadding.SCREEN,
        )
        val content = scaffold.content
        content.addView(TextView(this).apply {
            text = getString(R.string.litert_catalog_summary)
            textSize = Ui.TEXT_SECONDARY
            setTextColor(appPalette.secondaryText)
            setLineSpacing(0f, Ui.LINE_SPACING_BODY)
            setPaddingRelative(0, 0, 0, uiDp(Ui.SPACE_MD))
        })
        content.addView(createSearchField())
        modelRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(modelRows)
        screenRoot = scaffold.root
        return scaffold.root
    }

    private fun createSearchField(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = uiDp(Ui.TOUCH_TARGET)
        background = roundedFill(appPalette.surface, Ui.RADIUS_SHEET, appPalette.outline)
        setPaddingRelative(uiDp(Ui.SPACE_LG), 0, uiDp(Ui.SPACE_LG), 0)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = uiDp(Ui.SPACE_LG) }
        addView(
            ImageView(context).apply {
                setImageDrawable(tintedDrawable(R.drawable.ic_search_24, appPalette.secondaryText))
            },
            LinearLayout.LayoutParams(uiDp(20), uiDp(20)).apply { marginEnd = uiDp(Ui.SPACE_MD) },
        )
        addView(
            EditText(context).apply {
                hint = getString(R.string.litert_catalog_search_hint)
                textSize = Ui.TEXT_BODY
                isSingleLine = true
                background = null
                setTextColor(appPalette.primaryText)
                setHintTextColor(appPalette.secondaryText)
                tintEditText(this)
                backgroundTintList = null
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
                    ) = renderModels(value?.toString().orEmpty())

                    override fun afterTextChanged(value: Editable?) = Unit
                })
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    private fun renderModels(rawQuery: String) {
        if (!::modelRows.isInitialized) return
        val query = rawQuery.trim()
        val visible = AvailableLiteRtModelCatalog.models.filter { model ->
            query.isEmpty() || listOf(
                model.displayName,
                model.repositoryId,
                model.packaging,
                model.license,
                capabilitySummary(model),
            ).any { value -> value.contains(query, ignoreCase = true) }
        }
        modelRows.removeAllViews()
        if (visible.isEmpty()) {
            modelRows.addView(
                emptyStateView(
                    title = getString(R.string.litert_catalog_no_results),
                    description = null,
                    iconResource = R.drawable.ic_search_24,
                ),
            )
        } else {
            visible.forEach { model -> modelRows.addView(modelCard(model)) }
        }
        applyThemeToControls(modelRows)
    }

    private fun modelCard(model: AvailableLiteRtModel): View = cardContainer().apply {
        layoutParams = cardListParams()

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = model.displayName
                textSize = Ui.TEXT_ITEM
                typeface = Ui.mediumTypeface
                setTextColor(appPalette.primaryText)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(statusBadge(model.verifiedDownload != null))
        })
        addView(TextView(context).apply {
            text = getString(modelDescriptionResource(model.id))
            textSize = Ui.TEXT_SECONDARY
            setTextColor(appPalette.secondaryText)
            setLineSpacing(0f, 1.12f)
            setPaddingRelative(0, uiDp(Ui.SPACE_SM), 0, uiDp(Ui.SPACE_SM))
        })
        addView(metadataText(
            getString(
                R.string.litert_catalog_size_memory,
                Formatter.formatFileSize(this@LiteRtModelCatalogActivity, model.sizeBytes),
                model.minimumMemoryGb,
            ),
        ))
        addView(metadataText(
            getString(
                R.string.litert_catalog_runtime,
                model.contextTokens?.let(::formatCount)
                    ?: getString(R.string.litert_catalog_not_specified),
                formatCount(model.maximumOutputTokens),
                model.accelerators.joinToString(", "),
            ),
        ))
        addView(metadataText(
            getString(
                R.string.litert_catalog_format,
                model.packaging,
                capabilitySummary(model),
                model.license,
            ),
        ))
        addView(TextView(context).apply {
            text = getString(
                R.string.litert_catalog_source_metadata,
                model.repositoryId,
                model.commitHash.take(12),
            )
            textSize = Ui.TEXT_CAPTION
            alpha = 0.85f
            setTextColor(appPalette.secondaryText)
            setTextIsSelectable(true)
            setPaddingRelative(0, uiDp(Ui.SPACE_SM), 0, uiDp(Ui.SPACE_XS))
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(textButton(R.string.button_view_model_source) { openSource(model) })
            model.verifiedDownload?.let {
                addView(
                    textButton(R.string.litert_catalog_download) { chooseModel(model) },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = uiDp(Ui.SPACE_SM) },
                )
            }
        })
    }

    private fun statusBadge(verified: Boolean) = TextView(this).apply {
        text = getString(
            if (verified) R.string.litert_catalog_verified_download else R.string.litert_catalog_source_only,
        )
        textSize = 11f
        typeface = Ui.mediumTypeface
        setTextColor(if (verified) appPalette.accent else appPalette.secondaryText)
        background = roundedFill(
            appPalette.windowBackground,
            Ui.RADIUS_SHEET,
            if (verified) appPalette.accent else appPalette.divider,
        )
        setPaddingRelative(uiDp(10), uiDp(Ui.SPACE_XS), uiDp(10), uiDp(Ui.SPACE_XS))
    }

    private fun metadataText(value: String) = TextView(this).apply {
        text = value
        textSize = Ui.TEXT_SECTION
        setTextColor(appPalette.primaryText)
        setLineSpacing(0f, 1.1f)
        setPaddingRelative(0, uiDp(2), 0, uiDp(2))
    }

    private fun chooseModel(model: AvailableLiteRtModel) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_MODEL_ID, model.id),
        )
        finish()
    }

    private fun openSource(model: AvailableLiteRtModel) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.sourceUrl))) }
            .onFailure {
                showSnackbar(
                    screenRoot,
                    getString(R.string.download_source_unavailable),
                    Snackbar.LENGTH_LONG,
                )
            }
    }

    private fun capabilitySummary(model: AvailableLiteRtModel): String = model.capabilities
        .map { capability ->
            getString(
                when (capability) {
                    LiteRtModelCapability.TEXT -> R.string.litert_capability_text
                    LiteRtModelCapability.IMAGE -> R.string.litert_capability_image
                    LiteRtModelCapability.AUDIO -> R.string.litert_capability_audio
                    LiteRtModelCapability.THINKING -> R.string.litert_capability_thinking
                    LiteRtModelCapability.SPECIALIZED -> R.string.litert_capability_specialized
                },
            )
        }
        .joinToString(", ")

    private fun modelDescriptionResource(id: String): Int = when (id) {
        "gemma-4-e2b-it" -> R.string.litert_model_gemma4_e2b_description
        "gemma-4-e4b-it" -> R.string.litert_model_gemma4_e4b_description
        "gemma-3n-e2b-it" -> R.string.litert_model_gemma3n_e2b_description
        "gemma-3n-e4b-it" -> R.string.litert_model_gemma3n_e4b_description
        "gemma3-1b-it" -> R.string.litert_model_gemma3_1b_description
        "qwen2-5-1-5b-instruct" -> R.string.litert_model_qwen_description
        "deepseek-r1-distill-qwen-1-5b" -> R.string.litert_model_deepseek_description
        "tiny-garden-270m" -> R.string.litert_model_tiny_garden_description
        "mobile-actions-270m" -> R.string.litert_model_mobile_actions_description
        else -> R.string.litert_model_generic_description
    }

    private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)

    companion object {
        const val EXTRA_MODEL_ID = "liteRtCatalogModelId"
    }
}
