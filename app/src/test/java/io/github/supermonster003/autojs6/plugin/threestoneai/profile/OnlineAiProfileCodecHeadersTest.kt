package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeadersTest.Companion.conversation
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeadersTest.Companion.fixed
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeadersTest.Companion.secret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiProfileCodecHeadersTest {
    @Test
    fun documentsWithoutHeadersStayByteIdenticalToVersion172() {
        val encoded = OnlineAiProfileCodec.encode(sampleDocument()).toString(Charsets.UTF_8)

        assertEquals(VERSION_1_7_2_SAMPLE, encoded)
        assertEquals(sampleDocument().normalized(), OnlineAiProfileCodec.decode(VERSION_1_7_2_SAMPLE.toByteArray()))
        val exported = OnlineAiProfileTransferCodec.encode(
            OnlineAiProfileRegistrySnapshot(
                revision = 7L,
                defaultProfileId = GEMINI_ID,
                allowMeteredNetwork = true,
                profiles = sampleDocument().normalized().profiles.map { ConfiguredOnlineAiProfile(it, configured = true) },
            ),
        ).toString(Charsets.UTF_8)
        assertEquals(VERSION_1_7_2_SAMPLE.replace("\"revision\":7", "\"revision\":1"), exported)
    }

    @Test
    fun headersRoundTripInSchemaSixOnlyOnProfilesThatHaveThem() {
        val headers = listOf(
            fixed("X-Title", "AutoJs6 \"test\" \\ value"),
            secret("X-Api-Token"),
            conversation("x-session-affinity"),
        )
        val document = sampleDocument().let { sample ->
            sample.copy(profiles = sample.profiles.map { if (it.profileId == ANTHROPIC_ID) it.copy(headers = headers) else it })
        }

        val encoded = OnlineAiProfileCodec.encode(document).toString(Charsets.UTF_8)

        assertTrue(encoded.startsWith("{\"schema\":6,"))
        assertTrue(
            encoded.contains(
                "\"fastMode\":true,\"headers\":[" +
                    "{\"name\":\"X-Title\",\"kind\":\"fixed\",\"value\":\"AutoJs6 \\\"test\\\" \\\\ value\"}," +
                    "{\"name\":\"X-Api-Token\",\"kind\":\"secret\"}," +
                    "{\"name\":\"x-session-affinity\",\"kind\":\"conversation\"}" +
                    "]}",
            ),
        )
        assertEquals(1, Regex("\"headers\"").findAll(encoded).count())
        assertTrue(encoded.endsWith("\"defaultEffort\":\"default\",\"fastMode\":false}]}"))
        val decoded = OnlineAiProfileCodec.decode(encoded.toByteArray())
        assertEquals(document.normalized(), decoded)
        assertEquals(headers, decoded.profiles.single { it.profileId == ANTHROPIC_ID }.headers)
        assertEquals(emptyList<OnlineAiCustomHeader>(), decoded.profiles.single { it.profileId == GEMINI_ID }.headers)

        val withoutHeaders = decoded.copy(profiles = decoded.profiles.map { it.copy(headers = emptyList()) })
        assertEquals(VERSION_1_7_2_SAMPLE, OnlineAiProfileCodec.encode(withoutHeaders).toString(Charsets.UTF_8))
    }

    @Test
    fun schemaSixWithoutAnyHeadersStillDecodesAndIsRewrittenAsSchemaFive() {
        val schema6 = VERSION_1_7_2_SAMPLE.replace("\"schema\":5", "\"schema\":6")

        val decoded = OnlineAiProfileCodec.decode(schema6.toByteArray())

        assertEquals(sampleDocument().normalized(), decoded)
        assertEquals(VERSION_1_7_2_SAMPLE, OnlineAiProfileCodec.encode(decoded).toString(Charsets.UTF_8))
    }

    @Test
    fun unknownKeysAndMalformedHeadersAreRejected() {
        val valid = OnlineAiProfileCodec.encode(
            OnlineAiProfileDocument(1L, listOf(profile().copy(headers = listOf(fixed("X-Title", "AutoJs6"))))),
        ).toString(Charsets.UTF_8)
        val headersField = ",\"headers\":[{\"name\":\"X-Title\",\"kind\":\"fixed\",\"value\":\"AutoJs6\"}]"
        assertTrue(valid.contains(headersField))
        OnlineAiProfileCodec.decode(valid.toByteArray())

        listOf(
            "schema 5 with headers" to valid.replace("\"schema\":6", "\"schema\":5"),
            "schema 4 with headers" to valid.replace("\"schema\":6", "\"schema\":4")
                .replace(",\"defaultEffort\":\"default\",\"fastMode\":false", ""),
            "schema 7" to valid.replace("\"schema\":6", "\"schema\":7"),
            "unknown profile key" to valid.replace("\"headers\":", "\"requestHeaders\":"),
            "unknown header key" to valid.replace("\"value\":\"AutoJs6\"", "\"value\":\"AutoJs6\",\"note\":\"x\""),
            "empty header array" to valid.replace(headersField, ",\"headers\":[]"),
            "headers not an array" to valid.replace(headersField, ",\"headers\":{}"),
            "duplicate header key" to valid.replace("\"kind\":\"fixed\"", "\"kind\":\"fixed\",\"kind\":\"fixed\""),
            "fixed without value" to valid.replace(",\"value\":\"AutoJs6\"", ""),
            "secret with value" to valid.replace("\"kind\":\"fixed\"", "\"kind\":\"secret\""),
            "conversation with value" to valid.replace("\"kind\":\"fixed\"", "\"kind\":\"conversation\""),
            "unknown kind" to valid.replace("\"kind\":\"fixed\"", "\"kind\":\"Fixed\""),
            "missing name" to valid.replace("\"name\":\"X-Title\",", ""),
            "numeric value" to valid.replace("\"value\":\"AutoJs6\"", "\"value\":6"),
            "reserved name" to valid.replace("\"name\":\"X-Title\"", "\"name\":\"Authorization\""),
            "reserved name in other case" to valid.replace("\"name\":\"X-Title\"", "\"name\":\"ANTHROPIC-VERSION\""),
            "invalid name" to valid.replace("\"name\":\"X-Title\"", "\"name\":\"X Title\""),
            "line break in value" to valid.replace("\"value\":\"AutoJs6\"", "\"value\":\"Auto\\nJs6\""),
        ).forEach { (label, document) ->
            assertThrows(label, IllegalArgumentException::class.java) {
                OnlineAiProfileCodec.decode(document.toByteArray())
            }
        }

        val seventeen = (1..17).joinToString(",", ",\"headers\":[", "]") { index ->
            "{\"name\":\"X-H$index\",\"kind\":\"fixed\",\"value\":\"v\"}"
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfileCodec.decode(valid.replace(headersField, seventeen).toByteArray())
        }
    }

    @Test
    fun exportsKeepFixedValuesAndOmitSecretValues() {
        val configured = profile().copy(
            headers = listOf(fixed("X-Title", "AutoJs6"), secret("X-Api-Token"), conversation("X-Session")),
        )
        val exported = OnlineAiProfileTransferCodec.encode(
            OnlineAiProfileRegistrySnapshot(
                revision = 3L,
                defaultProfileId = null,
                allowMeteredNetwork = false,
                profiles = listOf(ConfiguredOnlineAiProfile(configured, configured = true)),
            ),
        ).toString(Charsets.UTF_8)

        assertTrue(exported.contains("{\"name\":\"X-Title\",\"kind\":\"fixed\",\"value\":\"AutoJs6\"}"))
        assertTrue(exported.contains("{\"name\":\"X-Api-Token\",\"kind\":\"secret\"}"))
        assertFalse(exported.contains("\"value\":\"\""))
        assertEquals(configured, OnlineAiProfileTransferCodec.decode(exported.toByteArray()).profiles.single())
    }

    private fun OnlineAiProfileDocument.normalized() = OnlineAiProfilePolicy.normalize(this)

    private fun profile() = OnlineAiProfile(
        profileId = "550e8400-e29b-41d4-a716-446655440000",
        displayName = "Work",
        provider = OnlineAiProvider.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com/v1",
        modelId = "model-a",
    )

    private fun sampleDocument() = OnlineAiProfileDocument(
        revision = 7L,
        profiles = listOf(
            OnlineAiProfile(
                profileId = GEMINI_ID,
                displayName = "Gemini",
                provider = OnlineAiProvider.GEMINI,
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                modelId = "gemini-x",
            ),
            OnlineAiProfile(
                profileId = ANTHROPIC_ID,
                displayName = "Work \"A\" \\ one",
                provider = OnlineAiProvider.ANTHROPIC,
                baseUrl = "https://API.Anthropic.com:443/v1/",
                modelId = "claude-b",
                modelIds = listOf("claude-a", "claude-b"),
                visionModelIds = listOf("claude-b"),
                defaultEffort = GenerationEffort.HIGH,
                fastMode = true,
            ),
        ),
        defaultProfileId = GEMINI_ID,
        allowMeteredNetwork = true,
    )

    private companion object {
        const val ANTHROPIC_ID = "11111111-1111-4111-8111-111111111111"
        const val GEMINI_ID = "22222222-2222-4222-8222-222222222222"

        /** What OnlineAiProfileCodec of v1.7.2 writes for [sampleDocument] (fixed sample, T71). */
        const val VERSION_1_7_2_SAMPLE =
            "{\"schema\":5,\"revision\":7,\"defaultProfileId\":\"22222222-2222-4222-8222-222222222222\"," +
                "\"allowMeteredNetwork\":true,\"profiles\":[" +
                "{\"profileId\":\"11111111-1111-4111-8111-111111111111\",\"displayName\":\"Work \\\"A\\\" \\\\ one\"," +
                "\"providerId\":\"anthropic\",\"baseUrl\":\"https://api.anthropic.com/v1\",\"modelId\":\"claude-b\"," +
                "\"modelIds\":[\"claude-a\",\"claude-b\"],\"visionModelIds\":[\"claude-b\"]," +
                "\"defaultEffort\":\"high\",\"fastMode\":true}," +
                "{\"profileId\":\"22222222-2222-4222-8222-222222222222\",\"displayName\":\"Gemini\"," +
                "\"providerId\":\"gemini\",\"baseUrl\":\"https://generativelanguage.googleapis.com/v1beta\"," +
                "\"modelId\":\"gemini-x\",\"modelIds\":[\"gemini-x\"],\"visionModelIds\":[]," +
                "\"defaultEffort\":\"default\",\"fastMode\":false}]}"
    }
}
