package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap D105 / P8.0: when closing the online profile editor has to ask first. */
class OnlineAiProfileEditorPolicyTest {
    private val initial = OnlineAiProfileEditorValues(
        provider = OnlineAiProvider.OPENAI,
        name = "OpenAI",
        baseUrl = "https://api.example.test/v1",
        modelIds = listOf("model-a", "model-b"),
        defaultModelId = "model-a",
        visionModelIds = listOf("model-b"),
        effort = GenerationEffort.DEFAULT,
        fastMode = false,
    )

    @Test
    fun anUntouchedFormHasNoChanges() {
        assertFalse(OnlineAiProfileEditorPolicy.hasUnsavedChanges(initial, initial.copy(), credentialLength = 0))
    }

    @Test
    fun aTypedApiKeyAlwaysCountsAsAChange() {
        assertTrue(OnlineAiProfileEditorPolicy.hasUnsavedChanges(initial, initial, credentialLength = 1))
    }

    @Test
    fun everyEditableValueCountsAsAChange() {
        val other = GenerationEffort.entries.first { it != initial.effort }
        val changes = listOf(
            initial.copy(provider = OnlineAiProvider.entries.first { it != initial.provider }),
            initial.copy(name = "OpenAI 2"),
            initial.copy(baseUrl = "https://api.example.test/v2"),
            initial.copy(modelIds = listOf("model-a")),
            initial.copy(defaultModelId = "model-b"),
            initial.copy(visionModelIds = emptyList()),
            initial.copy(effort = other),
            initial.copy(fastMode = true),
        )
        changes.forEach { current ->
            assertTrue(current.toString(), OnlineAiProfileEditorPolicy.hasUnsavedChanges(initial, current, 0))
        }
    }

    @Test
    fun undoingAnEditLeavesNoChanges() {
        val edited = initial.copy(name = "Temporary")
        assertTrue(OnlineAiProfileEditorPolicy.hasUnsavedChanges(initial, edited, 0))
        assertFalse(OnlineAiProfileEditorPolicy.hasUnsavedChanges(initial, edited.copy(name = initial.name), 0))
    }

    @Test
    fun savedEnumNamesAreReadBackAndUnknownNamesAreRejected() {
        OnlineAiProvider.entries.forEach { assertEquals(it, OnlineAiProfileEditorPolicy.provider(it.name)) }
        GenerationEffort.entries.forEach { assertEquals(it, OnlineAiProfileEditorPolicy.effort(it.name)) }
        assertNull(OnlineAiProfileEditorPolicy.provider("NOT_A_PROVIDER"))
        assertNull(OnlineAiProfileEditorPolicy.provider(null))
        assertNull(OnlineAiProfileEditorPolicy.effort("NOT_AN_EFFORT"))
    }

    @Test
    fun theApiKeyIsNotPartOfTheComparedValues() {
        // The values class has no credential field, so neither equals nor toString can expose it.
        val fields = OnlineAiProfileEditorValues::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.toString(), fields.none { "credential" in it || "key" in it || "secret" in it })
    }
}
