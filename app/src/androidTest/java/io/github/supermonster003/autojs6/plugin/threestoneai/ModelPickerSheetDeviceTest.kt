package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiExecutionProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model switcher sheet as rendered (roadmap 1.7.0 P5.6, D101): every target is listed once,
 * favorites only in the top group, a search shows one list without group headers, and service rows
 * carry the service name. Targets and favorites are synthetic and held in memory; nothing is stored.
 */
class ModelPickerSheetDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun eachTargetIsListedOnceAndFavoritesMoveBetweenGroups() {
        launch().use { scenario ->
            var favorites = setOf(fable.targetId)
            lateinit var dialog: BottomSheetDialog
            lateinit var favoritesHeader: String
            lateinit var groupHeaderA: String
            scenario.onActivity { activity ->
                val texts = TargetPickerTexts(activity)
                favoritesHeader = activity.getString(R.string.model_picker_favorites)
                groupHeaderA = texts.groupTitle(ModelPickerSection(ModelPickerSectionKind.PROFILE, "profile:$PROFILE_A", listOf(opus)))
                dialog = activity.showModelPickerSheet(state(favorites), actions(texts) { id ->
                    favorites = if (id in favorites) favorites - id else favorites + id
                    favorites
                })
            }
            scenario.onActivity {
                val shown = texts(dialog)
                assertEquals("Favorites header", 1, shown.count { it == favoritesHeader })
                listOf(opus.modelId, fable.modelId, gpt.modelId).forEach { title ->
                    assertEquals("$title is listed once", 1, shown.count { it == title })
                }
                assertTrue("Favorites come first", shown.indexOf(favoritesHeader) < shown.indexOf(fable.modelId))
                assertTrue("The favorite is not repeated under its service", shown.indexOf(fable.modelId) < shown.indexOf(groupHeaderA))
                assertEquals("Service rows show the service name (favorite included)", 2, shown.count { it == ANTHROPIC })
                val unavailable = context.getString(R.string.chat_target_unavailable_suffix).trimStart(' ', '|')
                assertTrue("Unavailable rows say so after the service", "$CODEX | $unavailable" in shown)

                unfavoriteButton(dialog).performClick()
                val after = texts(dialog)
                assertFalse("No favorites group once empty", favoritesHeader in after)
                assertTrue("Unfavorited target goes back to its group in catalog order", after.indexOf(opus.modelId) < after.indexOf(fable.modelId))
                assertTrue(after.indexOf(groupHeaderA) < after.indexOf(opus.modelId))
                assertEquals(1, after.count { it == fable.modelId })
            }
            scenario.onActivity {
                views(dialog.window!!.decorView).filterIsInstance<ImageView>()
                    .first { it.contentDescription == context.getString(R.string.model_picker_favorite) && rowTitle(it) == fable.modelId }
                    .performClick()
                assertTrue(favoritesHeader in texts(dialog))
                searchField(dialog).setText("claude")
                val found = texts(dialog)
                assertFalse("Search results have no favorites header", favoritesHeader in found)
                assertFalse("Search results have no service header", groupHeaderA in found)
                assertEquals(1, found.count { it == opus.modelId })
                assertEquals(1, found.count { it == fable.modelId })
                assertFalse(gpt.modelId in found)
                assertTrue("Search keeps catalog order", found.indexOf(opus.modelId) < found.indexOf(fable.modelId))
                dialog.dismiss()
            }
        }
    }

    private fun state(favorites: Set<String>) = ModelPickerState(
        targets = listOf(opus, fable, gpt, gemma),
        selectedTargetId = null,
        favorites = favorites,
        currentTarget = null,
        effort = null,
        fast = null,
    )

    private fun actions(texts: TargetPickerTexts, toggle: (String) -> Set<String>) = ModelPickerActions(
        rowTitle = texts::rowTitle,
        rowSubtitle = texts::rowSubtitle,
        groupTitle = texts::groupTitle,
        searchText = texts::searchText,
        isReady = TargetPickerTexts::isUsable,
        onSelect = {},
        onToggleFavorite = toggle,
        onEffort = {},
        onFast = {},
        onManage = null,
    )

    private fun launch() = ActivityScenario.launch<AppSettingsActivity>(Intent(context, AppSettingsActivity::class.java))

    private fun texts(dialog: BottomSheetDialog): List<String> =
        views(dialog.window!!.decorView).filter { it is TextView && it !is EditText }.map { (it as TextView).text.toString() }

    private fun searchField(dialog: BottomSheetDialog): EditText =
        views(dialog.window!!.decorView).filterIsInstance<EditText>().first()

    private fun unfavoriteButton(dialog: BottomSheetDialog): View =
        views(dialog.window!!.decorView).filterIsInstance<ImageView>()
            .single { it.contentDescription == context.getString(R.string.model_picker_unfavorite) }

    /** The title of the row a star button belongs to: the first text in the row's column. */
    private fun rowTitle(star: View): String? {
        val row = star.parent as? android.view.ViewGroup ?: return null
        return views(row).filterIsInstance<TextView>().firstOrNull()?.text?.toString()
    }

    private fun remoteTarget(profileId: String, firstModelId: String, modelId: String, name: String, available: Boolean = true) = AiTarget(
        targetId = AiTargetIds.profileModel(profileId, firstModelId, modelId),
        backendId = "online",
        providerId = "openai",
        profileId = profileId,
        modelId = modelId,
        displayName = name,
        locality = AiTargetLocality.REMOTE,
        credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf("https://example.com"),
        configured = true,
        available = available,
        capabilities = plain,
        limits = limits,
        executionProfiles = emptyList(),
    )

    private val opus = remoteTarget(PROFILE_A, "claude-opus-5-5", "claude-opus-5-5", ANTHROPIC)
    private val fable = remoteTarget(PROFILE_A, "claude-opus-5-5", "claude-fable-5-1", ANTHROPIC)
    private val gpt = remoteTarget(PROFILE_B, "gpt-6.1-sol", "gpt-6.1-sol", CODEX, available = false)
    private val gemma = AiTarget(
        targetId = "local:p56-picker-test",
        backendId = "local",
        providerId = "autojs6.three-stone-ai",
        profileId = null,
        modelId = "p56-picker-test",
        displayName = "Picker test model",
        locality = AiTargetLocality.LOCAL,
        credentialMode = AiTargetCredentialMode.NONE,
        declaredHttpsOrigins = emptyList(),
        configured = true,
        available = true,
        capabilities = plain,
        limits = limits,
        executionProfiles = listOf(AiExecutionProfile("cpu", available = true)),
    )

    private companion object {
        const val PROFILE_A = "00000000-0000-0000-0000-0000000005a6"
        const val PROFILE_B = "00000000-0000-0000-0000-0000000005b6"
        const val ANTHROPIC = "P5.6 Anthropic"
        const val CODEX = "P5.6 Codex"
        val plain = AiTargetCapabilities(
            streaming = true,
            persistentSession = true,
            structuredJson = false,
            usage = true,
            reasoning = false,
            tools = false,
        )
        val limits = AiTargetLimits(maximumContextBytes = null, maximumOutputBytes = null)
    }
}
