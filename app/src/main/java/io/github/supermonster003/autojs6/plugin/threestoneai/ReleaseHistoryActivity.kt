package io.github.supermonster003.autojs6.plugin.threestoneai

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale

class ReleaseHistoryActivity : ConfiguredActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(appPalette.windowBackground)
        addView(createAppToolbar(R.string.release_history_title, showBack = true))
        addView(ScrollView(context).apply {
            isFillViewport = true
            addView(MarkdownMessageView(context, appPalette).apply {
                setPaddingRelative(dp(20), dp(18), dp(20), dp(28))
                val document = loadReleaseHistory()
                if (document == null) {
                    showPlainText(getString(R.string.release_history_load_failed), 14.5f)
                } else {
                    showDocument(document, 14.5f)
                }
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        applySystemBarInsets(this)
    }

    private fun loadReleaseHistory(): MarkdownDocument? {
        val assetName = ReleaseHistoryAssetPolicy.assetFor(
            resources.configuration.locales.get(0) ?: Locale.getDefault(),
        )
        val source = runCatching {
            assets.open("doc/$assetName").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return null
        return StreamingMarkdownParser.parse(source)
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
