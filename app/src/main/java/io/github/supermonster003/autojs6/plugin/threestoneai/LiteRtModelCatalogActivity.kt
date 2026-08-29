package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.supermonster003.autojs6.plugin.threestoneai.download.AvailableLiteRtModel
import io.github.supermonster003.autojs6.plugin.threestoneai.download.AvailableLiteRtModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.download.LiteRtModelCapability
import java.text.NumberFormat

class LiteRtModelCatalogActivity : ConfiguredActivity() {
    private lateinit var modelRows: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        renderModels("")
    }

    private fun createContentView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)
        val statusBarBackground = createStatusBarBackground()
        addView(
            statusBarBackground,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0),
        )
        addView(createAppToolbar(R.string.litert_catalog_title, showBack = true))
        addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPaddingRelative(dp(18), dp(16), dp(18), dp(30))
                    addView(TextView(context).apply {
                        text = getString(R.string.litert_catalog_summary)
                        textSize = 14f
                        setTextColor(appPalette.secondaryText)
                        setLineSpacing(0f, 1.14f)
                        setPaddingRelative(dp(2), 0, dp(2), dp(10))
                    })
                    addView(EditText(context).apply {
                        hint = getString(R.string.litert_catalog_search_hint)
                        textSize = 14f
                        isSingleLine = true
                        setTextColor(appPalette.primaryText)
                        setHintTextColor(appPalette.secondaryText)
                        backgroundTintList = controlTintList()
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
                    }, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { setMargins(0, 0, 0, dp(10)) })
                    modelRows = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    addView(modelRows)
                })
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        applySystemBarInsets(this, statusBarBackground)
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
            modelRows.addView(TextView(this).apply {
                text = getString(R.string.litert_catalog_no_results)
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(appPalette.secondaryText)
                setPaddingRelative(0, dp(34), 0, dp(34))
            })
        } else {
            visible.forEach { model -> modelRows.addView(modelCard(model)) }
        }
        applyThemeToControls(modelRows)
    }

    private fun modelCard(model: AvailableLiteRtModel) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(appPalette.assistantSurface)
            setStroke(dp(1), appPalette.chatBorder)
        }
        setPaddingRelative(dp(16), dp(14), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, dp(6), 0, dp(6)) }

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = model.displayName
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(appPalette.primaryText)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(statusBadge(model.verifiedDownload != null))
        })
        addView(TextView(context).apply {
            text = getString(modelDescriptionResource(model.id))
            textSize = 13.5f
            setTextColor(appPalette.secondaryText)
            setLineSpacing(0f, 1.12f)
            setPaddingRelative(0, dp(7), 0, dp(9))
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
            textSize = 11.5f
            setTextColor(appPalette.secondaryText)
            setTextIsSelectable(true)
            setPaddingRelative(0, dp(7), 0, dp(6))
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(actionText(R.string.button_view_model_source) { openSource(model) })
            model.verifiedDownload?.let {
                addView(actionText(R.string.litert_catalog_download) { chooseModel(model) })
            }
        })
    }

    private fun statusBadge(verified: Boolean) = TextView(this).apply {
        text = getString(
            if (verified) R.string.litert_catalog_verified_download else R.string.litert_catalog_source_only,
        )
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (verified) appPalette.accent else appPalette.secondaryText)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            setColor(appPalette.windowBackground)
            setStroke(dp(1), if (verified) appPalette.accent else appPalette.divider)
        }
        setPaddingRelative(dp(9), dp(4), dp(9), dp(4))
    }

    private fun metadataText(value: String) = TextView(this).apply {
        text = value
        textSize = 12.5f
        setTextColor(appPalette.primaryText)
        setLineSpacing(0f, 1.1f)
        setPaddingRelative(0, dp(2), 0, dp(2))
    }

    private fun actionText(textResource: Int, action: () -> Unit) = TextView(this).apply {
        text = getString(textResource)
        textSize = 13.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(appPalette.accent)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumHeight = dp(44)
        setPaddingRelative(dp(12), dp(8), dp(12), dp(8))
        applySelectableBackground(this)
        setOnClickListener { action() }
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
                Toast.makeText(this, R.string.download_source_unavailable, Toast.LENGTH_LONG).show()
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

    private fun applySelectableBackground(view: View) {
        val value = TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)) {
            view.setBackgroundResource(value.resourceId)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_MODEL_ID = "liteRtCatalogModelId"
    }
}
