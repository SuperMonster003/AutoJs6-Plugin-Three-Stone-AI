package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderProblem
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderValueKind
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiReservedHeaderOwner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap 1.8.0 P12.1 - P12.3: request header editor rules that need no Android views. */
class ServiceHeaderPolicyTest {
    private fun fixed(name: String, value: String = "v") =
        OnlineAiCustomHeader(name, OnlineAiHeaderValueKind.FIXED, value)

    private fun secret(name: String) = OnlineAiCustomHeader(name, OnlineAiHeaderValueKind.SECRET)

    @Test
    fun openCodeAddressesAreRecognizedByHostOnly() {
        listOf(
            "https://opencode.ai/inference/go/openai/v1",
            "https://opencode.ai/inference/go/anthropic/v1",
            "https://opencode.ai/inference/go/google/v1beta",
            "  https://OpenCode.AI./zen/v1  ",
            "https://api.opencode.ai/v1",
        ).forEach { address -> assertTrue(address, ServiceHeaderPolicy.isOpenCodeBaseUrl(address)) }
        listOf(
            "https://notopencode.ai/v1",
            "https://opencode.ai.example.test/v1",
            "https://example.test/opencode.ai/v1",
            "not a url",
            "",
        ).forEach { address -> assertFalse(address, ServiceHeaderPolicy.isOpenCodeBaseUrl(address)) }
    }

    @Test
    fun theSessionHintAppearsOnlyForOpenCodeWithoutTheSessionHeader() {
        val address = "https://opencode.ai/inference/go/openai/v1"
        assertTrue(ServiceHeaderPolicy.needsSessionHeader(address, emptyList()))
        assertTrue(ServiceHeaderPolicy.needsSessionHeader(address, listOf(fixed("X-Title"))))
        val session = OnlineAiCustomHeader("X-OpenCode-Session", OnlineAiHeaderValueKind.CONVERSATION)
        assertFalse(ServiceHeaderPolicy.needsSessionHeader(address, listOf(session)))
        // A fixed value under the same name also counts: the user chose it on purpose.
        assertFalse(ServiceHeaderPolicy.needsSessionHeader(address, listOf(fixed("x-opencode-session"))))
        assertFalse(ServiceHeaderPolicy.needsSessionHeader("https://api.example.test/v1", emptyList()))
    }

    @Test
    fun missingSessionErrorsAreRecognizedInFailureTexts() {
        assertTrue(ServiceHeaderPolicy.isMissingSessionError("HTTP 400 MissingSessionID: session header required"))
        assertTrue(ServiceHeaderPolicy.isMissingSessionError("error missingsessionid"))
        assertFalse(ServiceHeaderPolicy.isMissingSessionError("HTTP 401 Unauthorized"))
        assertFalse(ServiceHeaderPolicy.isMissingSessionError(null))
    }

    @Test
    fun addingAppendsOnlyNamesThatAreNotUsedYet() {
        val existing = listOf(fixed("http-referer", "https://example.test"))
        val result = ServiceHeaderPolicy.add(existing, ServiceHeaderPreset.OPENROUTER.headers)
        result as ServiceHeaderAddResult.Added
        assertEquals(listOf("X-OpenRouter-Title"), result.added.map(OnlineAiCustomHeader::name))
        assertEquals(listOf("http-referer", "X-OpenRouter-Title"), result.headers.map(OnlineAiCustomHeader::name))
        // The existing entry keeps its own value.
        assertEquals("https://example.test", result.headers.first().value)

        val again = ServiceHeaderPolicy.add(result.headers, ServiceHeaderPreset.OPENROUTER.headers)
        assertEquals(ServiceHeaderAddResult.AlreadyPresent, again)
    }

    @Test
    fun addingKeepsTheSixteenEntryLimit() {
        val fifteen = (1..15).map { fixed("X-Custom-$it") }
        val one = ServiceHeaderPolicy.addSessionHeader(fifteen)
        one as ServiceHeaderAddResult.Added
        assertEquals(OnlineAiHeaderPolicy.MAXIMUM_HEADERS, one.headers.size)

        val full = ServiceHeaderPolicy.add(one.headers, listOf(fixed("X-One-More")))
        assertEquals(ServiceHeaderAddResult.TooMany(OnlineAiHeaderPolicy.MAXIMUM_HEADERS), full)

        // Two fresh entries do not fit into one free place, so nothing is added.
        val two = ServiceHeaderPolicy.add(fifteen, ServiceHeaderPreset.OPENROUTER.headers)
        assertEquals(ServiceHeaderAddResult.TooMany(OnlineAiHeaderPolicy.MAXIMUM_HEADERS), two)
    }

    @Test
    fun theSessionHeaderIsGeneratedPerConversation() {
        val result = ServiceHeaderPolicy.addSessionHeader(emptyList()) as ServiceHeaderAddResult.Added
        val header = result.added.single()
        assertEquals(ServiceHeaderPolicy.SESSION_HEADER_NAME, header.name)
        assertEquals(OnlineAiHeaderValueKind.CONVERSATION, header.kind)
        assertNull(header.value)
    }

    @Test
    fun everyPresetIsValidAndOnlySecretPresetsAskForAValue() {
        ServiceHeaderPreset.entries.forEach { preset ->
            assertEquals(preset.name, emptyList<OnlineAiHeaderProblem>(), OnlineAiHeaderPolicy.problems(preset.headers))
        }
        assertFalse(ServiceHeaderPreset.OPENCODE_GO.needsValue)
        assertFalse(ServiceHeaderPreset.OPENROUTER.needsValue)
        assertTrue(ServiceHeaderPreset.HELICONE.needsValue)
        assertTrue(ServiceHeaderPreset.CLOUDFLARE_AI_GATEWAY.needsValue)
        ServiceHeaderPreset.entries.flatMap(ServiceHeaderPreset::headers).forEach { header ->
            // The secret presets follow the default "Secret" rule of D162.
            assertEquals(header.name, header.kind == OnlineAiHeaderValueKind.SECRET, OnlineAiHeaderPolicy.suggestsSecret(header.name))
        }
    }

    @Test
    fun kindFollowsTheValueSourceAndTheSecretCheckbox() {
        assertEquals(OnlineAiHeaderValueKind.CONVERSATION, ServiceHeaderPolicy.kindOf(conversation = true, secret = true))
        assertEquals(OnlineAiHeaderValueKind.SECRET, ServiceHeaderPolicy.kindOf(conversation = false, secret = true))
        assertEquals(OnlineAiHeaderValueKind.FIXED, ServiceHeaderPolicy.kindOf(conversation = false, secret = false))
    }

    @Test
    fun typedSecretValuesAreCheckedLikeTheRegistryDoes() {
        assertEquals(OnlineAiHeaderProblem.ValueEmpty(3), ServiceHeaderPolicy.secretValueProblem(3, "   "))
        assertEquals(OnlineAiHeaderProblem.ValueInvalidCharacter(0), ServiceHeaderPolicy.secretValueProblem(0, "caf" + 'é'))
        assertEquals(
            OnlineAiHeaderProblem.ValueTooLong(1, OnlineAiHeaderPolicy.MAXIMUM_VALUE_BYTES),
            ServiceHeaderPolicy.secretValueProblem(1, "a".repeat(OnlineAiHeaderPolicy.MAXIMUM_VALUE_BYTES + 1)),
        )
        assertNull(ServiceHeaderPolicy.secretValueProblem(0, " " + "a".repeat(OnlineAiHeaderPolicy.MAXIMUM_VALUE_BYTES) + " "))
        assertNull(ServiceHeaderPolicy.secretValueProblem(0, "Bearer placeholder-value"))
    }

    @Test
    fun trimmedCharactersCopyOnlyTheValue() {
        assertArrayEquals("a b".toCharArray(), ServiceHeaderPolicy.trimmedCharacters("  a b  "))
        assertEquals(0, ServiceHeaderPolicy.trimmedCharacters("   ").size)
    }

    @Test
    fun problemsAreGroupedByEntryWithListProblemsShownEverywhere() {
        val tooMany = OnlineAiHeaderProblem.TooManyHeaders(17, 16)
        val total = OnlineAiHeaderProblem.TotalTooLarge(9000, 8192)
        val problems = listOf(
            OnlineAiHeaderProblem.NameEmpty(0),
            OnlineAiHeaderProblem.ValueEmpty(1),
            tooMany,
            OnlineAiHeaderProblem.NameReserved(2, OnlineAiReservedHeaderOwner.PLUGIN),
            total,
        )
        assertEquals(listOf(OnlineAiHeaderProblem.ValueEmpty(1), tooMany, total), ServiceHeaderPolicy.problemsOf(problems, 1))
        assertEquals(listOf(tooMany, total), ServiceHeaderPolicy.problemsOf(problems, 5))
        assertNull(ServiceHeaderPolicy.problemIndex(tooMany))
        assertEquals(2, ServiceHeaderPolicy.problemIndex(problems[3]))
    }

    @Test
    fun everyEntryProblemBelongsToExactlyOneField() {
        val entryProblems = listOf(
            OnlineAiHeaderProblem.NameEmpty(0),
            OnlineAiHeaderProblem.NameTooLong(0, 64),
            OnlineAiHeaderProblem.NameInvalidCharacter(0),
            OnlineAiHeaderProblem.NameDuplicate(1, 0),
            OnlineAiHeaderProblem.NameReserved(0, OnlineAiReservedHeaderOwner.TRANSPORT),
            OnlineAiHeaderProblem.ValueEmpty(0),
            OnlineAiHeaderProblem.ValueTooLong(0, 1024),
            OnlineAiHeaderProblem.ValueInvalidCharacter(0),
            OnlineAiHeaderProblem.ValueNotAllowed(0),
        )
        entryProblems.forEach { problem ->
            val fields = listOf(ServiceHeaderPolicy.isNameProblem(problem), ServiceHeaderPolicy.isValueProblem(problem))
            assertEquals(problem.toString(), 1, fields.count { it })
            assertTrue(problem.toString(), ServiceHeaderPolicy.problemIndex(problem) != null)
        }
        listOf(OnlineAiHeaderProblem.TooManyHeaders(17, 16), OnlineAiHeaderProblem.TotalTooLarge(9000, 8192)).forEach { problem ->
            assertFalse(ServiceHeaderPolicy.isNameProblem(problem))
            assertFalse(ServiceHeaderPolicy.isValueProblem(problem))
        }
    }

    @Test
    fun secretStatesNeverDependOnTheValue() {
        val typed = setOf("helicone-auth")
        val stored = setOf("cf-aig-authorization", "helicone-auth")
        assertEquals(ServiceHeaderSecretState.TYPED, ServiceHeaderPolicy.secretState("helicone-auth", typed, stored))
        assertEquals(ServiceHeaderSecretState.SET, ServiceHeaderPolicy.secretState("cf-aig-authorization", typed, stored))
        assertEquals(ServiceHeaderSecretState.NOT_SET, ServiceHeaderPolicy.secretState("x-api-key", typed, stored))
    }

    @Test
    fun importedSecretsWithoutStoredValuesAreReportedAsMissing() {
        val headers = listOf(secret("Helicone-Auth"), fixed("X-Title"), secret("cf-aig-authorization"), secret("X-Api-Key"))
        // After an import nothing is stored, so every secret entry is missing.
        assertEquals(
            listOf("Helicone-Auth", "cf-aig-authorization", "X-Api-Key"),
            ServiceHeaderPolicy.missingSecrets(headers, emptySet(), emptySet()).map(OnlineAiCustomHeader::name),
        )
        assertEquals(
            listOf("X-Api-Key"),
            ServiceHeaderPolicy.missingSecrets(headers, setOf("helicone-auth"), setOf("cf-aig-authorization"))
                .map(OnlineAiCustomHeader::name),
        )
    }

    @Test
    fun typedHeaderSecretsAndHeaderEditsCountAsUnsavedChanges() {
        val initial = OnlineAiProfileEditorValues(
            provider = OnlineAiProvider.OPENAI,
            name = "OpenCode Go",
            baseUrl = "https://opencode.ai/inference/go/openai/v1",
            modelIds = listOf("model-a"),
            defaultModelId = "model-a",
            visionModelIds = emptyList(),
            effort = GenerationEffort.DEFAULT,
            fastMode = false,
            headers = listOf(secret("Helicone-Auth")),
        )
        assertFalse(OnlineAiProfileEditorPolicy.hasUnsavedChanges(initial, initial.copy(), 0, typedHeaderSecrets = 0))
        assertTrue(OnlineAiProfileEditorPolicy.hasUnsavedChanges(initial, initial.copy(), 0, typedHeaderSecrets = 1))
        val added = initial.copy(headers = initial.headers + ServiceHeaderPreset.OPENCODE_GO.headers)
        assertTrue(OnlineAiProfileEditorPolicy.hasUnsavedChanges(initial, added, 0))
    }
}
