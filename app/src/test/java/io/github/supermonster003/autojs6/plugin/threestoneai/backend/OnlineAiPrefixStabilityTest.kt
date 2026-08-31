package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiPrefixStabilityTest {
    @Test
    fun everyProtocolKeepsInvariantLayersAndAppendOnlyTurnsByteStable() {
        profiles().forEach { profile ->
            val shared = listOf(
                message(GenerationRole.SYSTEM, "L0-fixed-instruction"),
                message(GenerationRole.SYSTEM, "L1-working-memory"),
                message(GenerationRole.SYSTEM, "L2-summary-checkpoint"),
                message(GenerationRole.USER, "earlier-user"),
                message(GenerationRole.ASSISTANT, "earlier-assistant"),
                message(GenerationRole.USER, "shared-current-user"),
            )
            val extended = shared + listOf(
                message(GenerationRole.ASSISTANT, "new-assistant"),
                message(GenerationRole.USER, "new-current-user"),
            )

            val shorterBody = encode(profile, shared)
            val longerBody = encode(profile, extended)
            val shorterPrefix = shorterBody.through("shared-current-user")
            val longerPrefix = longerBody.through("shared-current-user")

            assertEquals("${profile.provider} changed its serialized prefix", shorterPrefix, longerPrefix)
            assertOrdered(
                body = shorterBody,
                values = listOf(
                    "L0-fixed-instruction",
                    "L1-working-memory",
                    "L2-summary-checkpoint",
                    "earlier-user",
                    "shared-current-user",
                ),
            )
        }
    }

    private fun encode(profile: OnlineAiProfile, messages: List<GenerationMessage>): String {
        val turn = GenerationRequest(
            history = messages.dropLast(1),
            prompt = messages.last(),
            maximumOutputTokens = 16,
            samplingOptions = null,
            reportUsage = true,
        )
        return OnlineAiProtocolAdapters.forProfile(profile).prepare(
            profile = profile,
            messages = messages,
            turn = turn,
            credential = "test-key".toByteArray(),
        ).use { prepared -> prepared.request.bodyUtf8() }
    }

    private fun profiles(): List<OnlineAiProfile> = listOf(
        profile(
            provider = OnlineAiProvider.OPENAI_COMPATIBLE,
            baseUrl = "https://example.com/v1",
            model = "openai-model",
        ),
        profile(
            provider = OnlineAiProvider.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            model = "claude-model",
        ),
        profile(
            provider = OnlineAiProvider.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            model = "gemini-model",
        ),
    )

    private fun profile(
        provider: OnlineAiProvider,
        baseUrl: String,
        model: String,
    ) = OnlineAiProfile(
        profileId = "11111111-1111-4111-8111-111111111111",
        displayName = provider.name,
        provider = provider,
        baseUrl = baseUrl,
        modelId = model,
    )

    private fun message(role: GenerationRole, text: String) =
        GenerationMessage(role, listOf(text))

    private fun Request.bodyUtf8(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun String.through(marker: String): String {
        val start = indexOf(marker)
        check(start >= 0) { "Serialized request omitted $marker" }
        return substring(0, start + marker.length)
    }

    private fun assertOrdered(body: String, values: List<String>) {
        var previous = -1
        values.forEach { value ->
            val current = body.indexOf(value)
            assertTrue("$value is missing or out of order", current > previous)
            previous = current
        }
    }
}
