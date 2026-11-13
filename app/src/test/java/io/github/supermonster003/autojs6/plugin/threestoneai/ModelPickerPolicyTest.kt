package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiExecutionProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiEffortSupport
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
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
    fun `favorites appear only in the favorites group (D101)`() {
        val sections = ModelPickerPolicy.sections(targets, setOf(fable.targetId, gemma.targetId), "", ::searchText)

        assertEquals(
            listOf(ModelPickerSectionKind.FAVORITES, ModelPickerSectionKind.PROFILE, ModelPickerSectionKind.PROFILE),
            sections.map(ModelPickerSection::kind),
        )
        assertEquals(ModelPickerPolicy.FAVORITES_KEY, sections.first().groupKey)
        assertEquals("Favorites keep catalog order", listOf(fable, gemma), sections.first().targets)
        assertEquals(listOf(opus), sections[1].targets)
        assertEquals(listOf(gpt), sections[2].targets)
        assertEquals("Each target is listed once", targets.size, sections.sumOf { it.targets.size })
    }

    @Test
    fun `unfavoriting puts a target back in its group in catalog order`() {
        val allOfA = ModelPickerPolicy.sections(targets, setOf(opus.targetId, fable.targetId), "", ::searchText)
        assertEquals(listOf("favorites", "profile:$PROFILE_B", ModelPickerPolicy.LOCAL_KEY), allOfA.map(ModelPickerSection::groupKey))

        val back = ModelPickerPolicy.sections(targets, setOf(opus.targetId), "", ::searchText)
        assertEquals(listOf(fable), back.single { it.groupKey == "profile:$PROFILE_A" }.targets)
        val none = ModelPickerPolicy.sections(targets, emptySet(), "", ::searchText)
        assertEquals(listOf(opus, fable), none.single { it.groupKey == "profile:$PROFILE_A" }.targets)
    }

    @Test
    fun `search lists every match once without groups and every query word must match`() {
        val fableOnly = ModelPickerPolicy.sections(targets, setOf(gpt.targetId), "  FABLE\tanthropic ", ::searchText)
        assertEquals(ModelPickerSectionKind.SEARCH, fableOnly.single().kind)
        assertEquals(ModelPickerPolicy.SEARCH_KEY, fableOnly.single().groupKey)
        assertEquals(listOf(fable), fableOnly.single().targets)

        val across = ModelPickerPolicy.sections(targets, setOf(gemma.targetId, fable.targetId), "e", ::searchText)
        assertEquals("No favorites group while searching", 1, across.size)
        assertEquals("Catalog order, favorites included", targets, across.single().targets)

        val none = ModelPickerPolicy.sections(targets, emptySet(), "fable openai", ::searchText)
        assertTrue(none.isEmpty())

        val blank = ModelPickerPolicy.sections(targets, emptySet(), " \t ", ::searchText)
        assertEquals("A blank query groups as usual", 3, blank.size)
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

    @Test
    fun `gpt-6 targets from the support table offer max (Q12)`() {
        val levels = OnlineAiEffortSupport.levels(OnlineAiProtocol.OPENAI_COMPATIBLE, "gpt-6.1-sol")
        val sol = remoteTarget(PROFILE_B, "gpt-6.1-sol", "gpt-6.1-sol", openAi.copy(effortLevels = levels))

        assertEquals(GenerationEffort.MAX, ModelPickerPolicy.effortChoices(sol).last())
        assertEquals(GenerationEffort.MAX, ModelPickerPolicy.highlightedEffort(sol, GenerationEffort.MAX))
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
