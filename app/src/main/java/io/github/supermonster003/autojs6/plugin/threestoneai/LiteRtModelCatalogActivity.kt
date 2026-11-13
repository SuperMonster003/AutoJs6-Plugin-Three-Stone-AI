package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.download.AvailableLiteRtModel
import io.github.supermonster003.autojs6.plugin.threestoneai.download.AvailableLiteRtModelCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.download.LiteRtModelCapability
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.download.ModelDownloadState
import io.github.supermonster003.autojs6.plugin.threestoneai.download.RecommendedModel
import io.github.supermonster003.autojs6.plugin.threestoneai.download.cardModel
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ImportedModel
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelManagerState
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.ContentPadding
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.buildScaffold
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.cardContainer
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.cardListParams
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.emptyStateView
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.roundedFill
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.textButton
import java.text.NumberFormat

class LiteRtModelCatalogActivity : ConfiguredActivity() {
    private lateinit var importCoordinator: ModelImportCoordinator
    private lateinit var modelRows: LinearLayout
    private lateinit var screenRoot: View
    private var importedModels: List<ImportedModel> = emptyList()
    private var currentQuery = ""
    private val managerObserver = ModelImportCoordinator.ManagerObserver(::renderManagerState)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importCoordinator = ModelImportCoordinator.get(applicationContext)
        setContentView(createContentView())
        renderModels(currentQuery)
    }

    override fun onStart() {
        super.onStart()
        renderManagerState(importCoordinator.attachManager(managerObserver))
    }

    override fun onStop() {
        importCoordinator.detachManager(managerObserver)
        super.onStop()
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
                    ) {
                        currentQuery = value?.toString().orEmpty()
                        renderModels(currentQuery)
                    }

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
        val imported = importedModels.any { importedModel ->
            AvailableLiteRtModelCatalog.matchesImportedModel(
                model = model,
                importedSha256 = importedModel.sha256,
                importedDisplayName = importedModel.displayName,
            )
        }

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = model.displayName
                textSize = Ui.TEXT_ITEM
                typeface = Ui.mediumTypeface
                setTextColor(appPalette.primaryText)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(statusBadge(verified = model.verifiedDownload != null, imported = imported))
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
            model.verifiedDownload?.let { download ->
                addView(
                    textButton(R.string.litert_catalog_download) {
                        confirmDownload(download, model.minimumMemoryGb)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = uiDp(Ui.SPACE_SM) },
                )
            }
        })
    }

    private fun statusBadge(verified: Boolean, imported: Boolean) = TextView(this).apply {
        text = getString(
            when {
                imported -> R.string.litert_catalog_imported
                verified -> R.string.litert_catalog_verified_download
                else -> R.string.litert_catalog_source_only
            },
        )
        textSize = 11f
        typeface = Ui.mediumTypeface
        setTextColor(
            when {
                imported -> appPalette.onPrimary
                verified -> appPalette.accent
                else -> appPalette.secondaryText
            },
        )
        background = roundedFill(
            if (imported) appPalette.primary else appPalette.windowBackground,
            Ui.RADIUS_SHEET,
            if (imported) null else if (verified) appPalette.accent else appPalette.divider,
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

    private fun renderManagerState(state: ModelManagerState) {
        val latest = state.snapshot?.models.orEmpty()
        if (latest == importedModels) return
        importedModels = latest
        renderModels(currentQuery)
    }

    /**
     * Confirms a download into private storage (D99). The memory line warns when the device has
     * less memory than the model needs but still allows the download (D107).
     */
    private fun confirmDownload(model: RecommendedModel, minimumMemoryGb: Int) {
        val message = StringBuilder(
            getString(
                R.string.download_confirm_message,
                Formatter.formatFileSize(this, model.expectedSizeBytes),
                model.expectedSha256,
                model.license,
                minimumMemoryGb,
            ),
        )
        val totalMemory = deviceTotalMemoryBytes()
        if (ModelDownloadPolicy.isBelowMemoryRequirement(totalMemory, minimumMemoryGb)) {
            message.append("\n\n").append(
                getString(
                    R.string.download_confirm_memory_warning,
                    Formatter.formatFileSize(this, totalMemory),
                    minimumMemoryGb,
                ),
            )
        }
        val unfinished = ModelDownloadCoordinator.get(applicationContext).state()
        val unfinishedModel = unfinished.cardModel
        if (
            unfinishedModel != null && unfinishedModel != model &&
            (unfinished is ModelDownloadState.Paused || unfinished is ModelDownloadState.Failed)
        ) {
            message.append("\n\n").append(
                getString(R.string.download_confirm_discards_paused, unfinishedModel.displayName),
            )
        }
        materialDialog()
            .setTitle(model.displayName)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.button_view_model_source) { _, _ -> openSource(model.sourceUrl) }
            .setPositiveButton(R.string.litert_catalog_download) { _, _ ->
                setResult(RESULT_OK, Intent().putExtra(EXTRA_MODEL_ID, model.id))
                finish()
            }
            .show()
            .also(::tintDialogButtons)
    }

    private fun deviceTotalMemoryBytes(): Long = runCatching {
        val manager = getSystemService(ActivityManager::class.java)
        ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
    }.getOrDefault(0L)

    private fun openSource(model: AvailableLiteRtModel) {
        openSource(model.sourceUrl)
    }

    private fun openSource(sourceUrl: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))) }
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
