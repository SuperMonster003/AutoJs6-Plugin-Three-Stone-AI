package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.PatternMatcher
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.text.LinkAnnotation
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.compose.ReleaseHistoryScreen
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.compose.ReleaseHistoryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Q160 A of the plugin center roadmap: the markdown links of a release note show as link text and open the browser.
 * The bundled changelogs have no link yet, so a made up release stands in for them.
 */
@OptIn(ExperimentalTestApi::class)
class ReleaseHistoryLinkInstrumentationTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val entries = listOf(
        ReleaseHistoryEntry(
            "v9.9.9",
            "2026/11/20",
            listOf(
                ReleaseHistoryItem("Fixed", "Crash on Android 7.x _[`issue #575`](${ISSUE})_"),
                ReleaseHistoryItem("Improved", "See [the documentation](${DOCS}) and [the readme](${README}) for the details"),
                ReleaseHistoryItem("Improved", "A note without a link"),
            ),
        ),
        ReleaseHistoryEntry("v9.9.8", "2026/11/19", listOf(ReleaseHistoryItem("Fixed", "An older release"))),
    )

    private fun showPage() {
        compose.setContent {
            ReleaseHistoryTheme(palette = AppThemePalette.resolve(instrumentation.targetContext)) { ReleaseHistoryScreen(entries, onBack = {}) }
        }
    }

    @Test
    fun linksShowTheirTextAndOpenThroughTheUriHandler() {
        val opened = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        compose.setContent {
            ReleaseHistoryTheme(palette = AppThemePalette.resolve(instrumentation.targetContext)) {
                CompositionLocalProvider(LocalUriHandler provides handler) { ReleaseHistoryScreen(entries, onBack = {}) }
            }
        }
        val texts = compose.onNodeWithTag("stone.releaseHistory.body.v9.9.9").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().joinToString("\n") { it.text }
        listOf("Crash on Android 7.x issue #575", "See the documentation and the readme for the details", "A note without a link").forEach {
            assertTrue("<$it> in <$texts>", texts.contains(it))
        }
        listOf("](", "_[", "`", "https://").forEach { assertFalse("<$it> in <$texts>", texts.contains(it)) }

        compose.onNodeWithText("issue #575", substring = true, useUnmergedTree = true).performFirstLinkClick()
        compose.onNodeWithText("the readme", substring = true, useUnmergedTree = true)
            .performFirstLinkClick { (it.item as LinkAnnotation.Url).url == README }
        compose.waitForIdle()
        assertEquals(listOf(ISSUE, README), opened)

        saveShot()
    }

    @Test
    fun aLinkAsksTheSystemToViewItsUrl() {
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https")
            addDataAuthority("github.com", null)
            addDataPath("/SuperMonster003/AutoJs6/issues/575", PatternMatcher.PATTERN_LITERAL)
        }
        // Blocking: the intent is caught before it is resolved, so the test does not depend on a browser.
        val monitor = Instrumentation.ActivityMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        instrumentation.addMonitor(monitor)
        try {
            showPage()
            compose.onNodeWithText("issue #575", substring = true, useUnmergedTree = true).performFirstLinkClick()
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            assertEquals(1, monitor.hits)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** For the screenshots of the acceptance list: `Android/data/<package>/files/shots/release-history-links.png`. */
    private fun saveShot() {
        val directory = instrumentation.targetContext.getExternalFilesDir("shots") ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "release-history-links.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val ISSUE = "https://github.com/SuperMonster003/AutoJs6/issues/575"
        const val DOCS = "https://docs.autojs6.com/"
        const val README = "https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/README.md"
    }
}
