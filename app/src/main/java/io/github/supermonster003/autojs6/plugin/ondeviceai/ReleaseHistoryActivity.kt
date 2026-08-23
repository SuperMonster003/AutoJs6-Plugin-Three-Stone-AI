package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class ReleaseHistoryActivity : ConfiguredActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)
        addView(createToolbar())
        addView(ScrollView(context).apply {
            isFillViewport = true
            addView(TextView(context).apply {
                textSize = 14.5f
                setTextColor(appPalette.primaryText)
                setTextIsSelectable(true)
                setLineSpacing(0f, 1.12f)
                setPaddingRelative(dp(20), dp(18), dp(20), dp(28))
                text = loadReleaseHistory()
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        applySystemBarInsets(this)
    }

    private fun createToolbar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(58)
        setPaddingRelative(dp(8), dp(4), dp(18), dp(4))
        setBackgroundColor(appPalette.primary)
        addView(TextView(context).apply {
            text = getString(R.string.navigation_back)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(appPalette.onPrimary)
            setPaddingRelative(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { finish() }
        })
        addView(TextView(context).apply {
            text = getString(R.string.release_history_title)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(appPalette.onPrimary)
            setPaddingRelative(dp(8), 0, 0, 0)
        })
    }

    private fun loadReleaseHistory(): CharSequence {
        val assetName = ReleaseHistoryAssetPolicy.assetFor(
            resources.configuration.locales.get(0) ?: Locale.getDefault(),
        )
        val source = runCatching {
            assets.open("doc/$assetName").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { return getString(R.string.release_history_load_failed) }
        return MarkdownTextRenderer(this, appPalette.accent).render(
            StreamingMarkdownParser.parse(source),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

internal object ReleaseHistoryAssetPolicy {
    fun assetFor(locale: Locale): String {
        val tag = locale.toLanguageTag()
        return when {
            tag.startsWith("zh-Hant-HK", ignoreCase = true) -> "CHANGELOG-zh-Hant-HK.md"
            tag.startsWith("zh-Hant-TW", ignoreCase = true) -> "CHANGELOG-zh-Hant-TW.md"
            tag.startsWith("zh", ignoreCase = true) -> "CHANGELOG-zh-Hans.md"
            locale.language == "ar" -> "CHANGELOG-ar.md"
            locale.language == "es" -> "CHANGELOG-es.md"
            locale.language == "fr" -> "CHANGELOG-fr.md"
            locale.language == "ja" -> "CHANGELOG-ja.md"
            locale.language == "ko" -> "CHANGELOG-ko.md"
            locale.language == "ru" -> "CHANGELOG-ru.md"
            else -> "CHANGELOG-en.md"
        }
    }
}
