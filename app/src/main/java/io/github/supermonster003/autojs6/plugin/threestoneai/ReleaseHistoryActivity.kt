package io.github.supermonster003.autojs6.plugin.threestoneai

import android.os.Bundle
import android.view.View
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.ContentPadding
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.buildScaffold
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.emptyStateView
import java.util.Locale

class ReleaseHistoryActivity : ConfiguredActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): View {
        val scaffold = buildScaffold(
            R.string.release_history_title,
            contentPadding = ContentPadding.SCREEN,
        )
        val document = loadReleaseHistory()
        if (document == null) {
            scaffold.content.addView(
                emptyStateView(
                    title = getString(R.string.release_history_load_failed),
                    description = null,
                    iconResource = R.drawable.ic_warning_24,
                ),
            )
        } else {
            scaffold.content.addView(
                MarkdownMessageView(this, appPalette).apply {
                    showDocument(document, 14.5f)
                },
            )
        }
        return scaffold.root
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
