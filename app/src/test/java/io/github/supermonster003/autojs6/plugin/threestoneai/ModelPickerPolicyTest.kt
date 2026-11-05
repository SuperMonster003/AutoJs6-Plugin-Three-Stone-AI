package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiExecutionProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerPolicyTest {
    private val opus = remoteTarget(PROFILE_A, "claude-opus-5-5", "claude-opus-5-5", anthropic)
    private val fable = remoteTarget(PROFILE_A, "claude-opus-5-5", "claude-fable-5-1", anthropic)
    private val gpt = remoteTarget(PROFILE_B, "gpt-6.1-sol", "gpt-6.1-sol", openAi)
    private val gemma = localTarget()
    private val targets = listOf(opus, fable, gpt, gemma)

    @Test
    fun `sections group by profile in catalog order with local models last`() {
        val sections = ModelPickerPolicy.sections(targets, emptySet(), "", ::searchText)

        assertEquals(
            listOf(ModelPickerSectionKind.PROFILE, ModelPickerSectionKind.PROFILE, ModelPickerSectionKind.LOCAL),
            sections.map(ModelPickerSection::kind),
        )
        assertEquals(listOf("profile:$PROFILE_A", "profile:$PROFILE_B", ModelPickerPolicy.LOCAL_KEY), sections.map(ModelPickerSection::groupKey))
        assertEquals(listOf(opus, fable), sections[0].targets)
    }

    @Test
    fun `favorites are pinned on top and stay in their own group`() {
        val sections = ModelPickerPolicy.sections(targets, setOf(fable.targetId, gemma.targetId), "", ::searchText)

        assertEquals(ModelPickerSectionKind.FAVORITES, sections.first().kind)
        assertEquals(ModelPickerPolicy.FAVORITES_KEY, sections.first().groupKey)
        assertEquals(listOf(fable, gemma), sections.first().targets)
        assertEquals(listOf(opus, fable), sections[1].targets)
        assertEquals(4, sections.size)
    }

    @Test
    fun `every query word must match and empty sections are dropped`() {
        val fableOnly = ModelPickerPolicy.sections(targets, setOf(gpt.targetId), "  FABLE\tanthropic ", ::searchText)
        assertEquals(1, fableOnly.size)
        assertEquals(listOf(fable), fableOnly.single().targets)

        val none = ModelPickerPolicy.sections(targets, emptySet(), "fable openai", ::searchText)
        assertTrue(none.isEmpty())

        val local = ModelPickerPolicy.sections(targets, emptySet(), "gemma", ::searchText)
        assertEquals(listOf(ModelPickerSectionKind.LOCAL), local.map(ModelPickerSection::kind))
    }

    @Test
    fun `tags show image input reasoning and fast only when supported`() {
        assertEquals(
            listOf(ModelPickerTag.VISION, ModelPickerTag.REASONING, ModelPickerTag.FAST),
            ModelPickerPolicy.tags(opus),
        )
        assertEquals(listOf(ModelPickerTag.REASONING), ModelPickerPolicy.tags(gpt))
        assertEquals(emptyList<ModelPickerTag>(), ModelPickerPolicy.tags(gemma))
        assertEquals(
            listOf(ModelPickerTag.REASONING),
            ModelPickerPolicy.tags(gemma.copy(capabilities = plain.copy(reasoning = true))),
        )
    }

    @Test
    fun `effort choices start with default and are hidden without levels`() {
        assertEquals(
            listOf(GenerationEffort.DEFAULT) + anthropic.effortLevels,
            ModelPickerPolicy.effortChoices(opus),
        )
        assertEquals(emptyList<GenerationEffort>(), ModelPickerPolicy.effortChoices(gemma))
        assertEquals(emptyList<GenerationEffort>(), ModelPickerPolicy.effortChoices(null))
    }

    @Test
    fun `highlighted effort is the level the target really receives`() {
        assertEquals(GenerationEffort.XHIGH, ModelPickerPolicy.highlightedEffort(gpt, GenerationEffort.MAX))
        assertEquals(GenerationEffort.DEFAULT, ModelPickerPolicy.highlightedEffort(gpt, null))
        assertEquals(GenerationEffort.HIGH, ModelPickerPolicy.highlightedEffort(opus, GenerationEffort.HIGH))
    }

    private fun searchText(target: AiTarget): String =
        listOf(target.modelId, target.displayName, target.providerId).joinToString(" ")

    private fun remoteTarget(
        profileId: String,
        firstModelId: String,
        modelId: String,
        capabilities: AiTargetCapabilities,
    ) = AiTarget(
        targetId = AiTargetIds.profileModel(profileId, firstModelId, modelId),
        backendId = "online",
        providerId = if (capabilities.fastMode) "anthropic" else "openai",
        profileId = profileId,
        modelId = modelId,
        displayName = if (capabilities.fastMode) "Anthropic" else "Codex",
        locality = AiTargetLocality.REMOTE,
        credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf("https://example.com"),
        configured = true,
        available = true,
        capabilities = capabilities,
        limits = limits,
        executionProfiles = emptyList(),
    )

    private fun localTarget() = AiTarget(
        targetId = "local:gemma",
        backendId = "local",
        providerId = "autojs6.three-stone-ai",
        profileId = null,
        modelId = "gemma",
        displayName = "Gemma",
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
        const val PROFILE_A = "00000000-0000-0000-0000-00000000000a"
        const val PROFILE_B = "00000000-0000-0000-0000-00000000000b"
        val plain = AiTargetCapabilities(
            streaming = true,
            persistentSession = true,
            structuredJson = false,
            usage = true,
            reasoning = false,
            tools = false,
        )
        val anthropic = plain.copy(
            vision = true,
            effortLevels = listOf(
                GenerationEffort.LOW,
                GenerationEffort.MEDIUM,
                GenerationEffort.HIGH,
                GenerationEffort.XHIGH,
                GenerationEffort.MAX,
            ),
            fastMode = true,
        )
        val openAi = plain.copy(
            effortLevels = listOf(GenerationEffort.LOW, GenerationEffort.MEDIUM, GenerationEffort.HIGH, GenerationEffort.XHIGH),
        )
        val limits = AiTargetLimits(maximumContextBytes = null, maximumOutputBytes = null)
    }
}
