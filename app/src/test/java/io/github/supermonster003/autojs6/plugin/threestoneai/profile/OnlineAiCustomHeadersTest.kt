package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OnlineAiCustomHeadersTest {
    @Test
    fun reservedListNamesEveryHeaderThePluginOrHttpStackSends() {
        assertEquals(
            mapOf(
                "accept" to OnlineAiReservedHeaderOwner.PLUGIN,
                "cache-control" to OnlineAiReservedHeaderOwner.PLUGIN,
                "content-type" to OnlineAiReservedHeaderOwner.PLUGIN,
                "authorization" to OnlineAiReservedHeaderOwner.PLUGIN,
                "x-api-key" to OnlineAiReservedHeaderOwner.PLUGIN,
                "x-goog-api-key" to OnlineAiReservedHeaderOwner.PLUGIN,
                "anthropic-version" to OnlineAiReservedHeaderOwner.PLUGIN,
                "host" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "content-length" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "transfer-encoding" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "connection" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "keep-alive" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "proxy-connection" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "te" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "trailer" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "upgrade" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "expect" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "accept-encoding" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "content-encoding" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "cookie" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "proxy-authorization" to OnlineAiReservedHeaderOwner.TRANSPORT,
                "http2-settings" to OnlineAiReservedHeaderOwner.TRANSPORT,
            ),
            OnlineAiBuiltInHeaders.RESERVED,
        )
        assertFalse(OnlineAiBuiltInHeaders.USER_AGENT in OnlineAiBuiltInHeaders.RESERVED)
        assertFalse(OnlineAiBuiltInHeaders.ANTHROPIC_BETA in OnlineAiBuiltInHeaders.RESERVED)
    }

    @Test
    fun everyReservedNameIsRejectedInAnyLetterCase() {
        OnlineAiBuiltInHeaders.RESERVED.forEach { (name, owner) ->
            listOf(name, name.uppercase(Locale.ROOT), titleCase(name), "  $name  ").forEach { spelling ->
                assertEquals(spelling, owner, OnlineAiBuiltInHeaders.ownerOf(spelling))
                assertEquals(
                    spelling,
                    listOf(OnlineAiHeaderProblem.NameReserved(0, owner)),
                    OnlineAiHeaderPolicy.problems(listOf(fixed(spelling, "placeholder"))),
                )
            }
        }
    }

    @Test
    fun userAgentAndAnthropicBetaAreCustomizable() {
        listOf("User-Agent", "user-agent", "ANTHROPIC-BETA", "anthropic-beta").forEach { name ->
            assertEquals(name, emptyList<OnlineAiHeaderProblem>(), OnlineAiHeaderPolicy.problems(listOf(fixed(name, "v1"))))
        }
    }

    @Test
    fun namesMustBeShortRfc9110Tokens() {
        val allSymbols = "!#$%&'*+-.^_`|~"
        assertTrue(OnlineAiHeaderPolicy.problems(listOf(fixed("X$allSymbols", "v"))).isEmpty())
        assertTrue(OnlineAiHeaderPolicy.problems(listOf(fixed("x".repeat(64), "v"))).isEmpty())
        assertEquals(
            listOf(OnlineAiHeaderProblem.NameTooLong(0, 64)),
            OnlineAiHeaderPolicy.problems(listOf(fixed("x".repeat(65), "v"))),
        )
        listOf("", "   ").forEach { blank ->
            assertEquals(listOf(OnlineAiHeaderProblem.NameEmpty(0)), OnlineAiHeaderPolicy.problems(listOf(fixed(blank, "v"))))
        }
        listOf("X Trace", "X:Trace", "X/Trace", "X(Trace)", "X\"Trace", "X-\u00e9", "X-\u4e2d", "X\tTrace", "X@Trace").forEach { name ->
            assertEquals(
                name,
                listOf(OnlineAiHeaderProblem.NameInvalidCharacter(0)),
                OnlineAiHeaderPolicy.problems(listOf(fixed(name, "v"))),
            )
        }
        assertTrue(OnlineAiHeaderPolicy.isValidName("X-Trace"))
        assertFalse(OnlineAiHeaderPolicy.isValidName(" X-Trace"))
    }

    @Test
    fun namesAreUniqueIgnoringLetterCase() {
        assertEquals(
            listOf(
                OnlineAiHeaderProblem.NameDuplicate(1, 0),
                OnlineAiHeaderProblem.NameDuplicate(3, 0),
            ),
            OnlineAiHeaderPolicy.problems(
                listOf(
                    fixed("X-Trace", "a"),
                    conversation("x-trace"),
                    secret("X-Other"),
                    fixed(" X-TRACE ", "b"),
                ),
            ),
        )
    }

    @Test
    fun fixedValuesAreTrimmedVisibleAsciiOfBoundedLength() {
        assertEquals(
            listOf(fixed("X-Trace", "a b")),
            OnlineAiHeaderPolicy.requireValid(listOf(fixed(" X-Trace ", "  a b \r\n"))),
        )
        assertTrue(OnlineAiHeaderPolicy.problems(listOf(fixed("X-Trace", "~".repeat(1024)))).isEmpty())
        assertEquals(
            listOf(OnlineAiHeaderProblem.ValueTooLong(0, 1024)),
            OnlineAiHeaderPolicy.problems(listOf(fixed("X-Trace", "v".repeat(1025)))),
        )
        listOf(null, "", "   ").forEach { value ->
            assertEquals(
                "$value",
                listOf(OnlineAiHeaderProblem.ValueEmpty(0)),
                OnlineAiHeaderPolicy.problems(listOf(OnlineAiCustomHeader("X-Trace", OnlineAiHeaderValueKind.FIXED, value))),
            )
        }
        listOf("a\rb", "a\nb", "a\u0000b", "a\tb", "a\u007fb", "\u00e9", "\u4e2d\u6587").forEach { value ->
            assertEquals(
                value,
                listOf(OnlineAiHeaderProblem.ValueInvalidCharacter(0)),
                OnlineAiHeaderPolicy.problems(listOf(fixed("X-Trace", value))),
            )
        }
        assertTrue(OnlineAiHeaderPolicy.isValidValue("a b"))
        assertFalse(OnlineAiHeaderPolicy.isValidValue(" a"))
        assertFalse(OnlineAiHeaderPolicy.isValidValue("a\nb"))
    }

    @Test
    fun secretAndConversationEntriesNeverStoreAPlainValue() {
        assertTrue(OnlineAiHeaderPolicy.problems(listOf(secret("X-Api-Token"), conversation("X-Session-Affinity"))).isEmpty())
        assertEquals(
            listOf(
                OnlineAiHeaderProblem.ValueNotAllowed(0),
                OnlineAiHeaderProblem.ValueNotAllowed(1),
            ),
            OnlineAiHeaderPolicy.problems(
                listOf(
                    OnlineAiCustomHeader("X-Api-Token", OnlineAiHeaderValueKind.SECRET, "placeholder"),
                    OnlineAiCustomHeader("X-Session-Affinity", OnlineAiHeaderValueKind.CONVERSATION, ""),
                ),
            ),
        )
    }

    @Test
    fun atMostSixteenHeadersAndEightKibibytesPerService() {
        val sixteen = (1..16).map { index -> fixed("X-H$index", "v") }
        assertTrue(OnlineAiHeaderPolicy.problems(sixteen).isEmpty())
        assertEquals(
            listOf(OnlineAiHeaderProblem.TooManyHeaders(17, 16)),
            OnlineAiHeaderPolicy.problems(sixteen + fixed("X-H17", "v")),
        )

        // 8 x (24-byte name + 1000-byte value) = 8192 bytes exactly.
        val full = (1..8).map { index -> fixed("X-Header-Number-${index.toString().padStart(8, '0')}", "v".repeat(1000)) }
        assertTrue(full.all { it.name.length == 24 })
        assertTrue(OnlineAiHeaderPolicy.problems(full).isEmpty())
        val oneByteMore = full.dropLast(1) + fixed(full.last().name, "v".repeat(1001))
        assertEquals(listOf(OnlineAiHeaderProblem.TotalTooLarge(8193, 8192)), OnlineAiHeaderPolicy.problems(oneByteMore))

        // A conversation value counts as its 36-byte length; a secret as the stored length given.
        val withDerived = full.dropLast(1) + conversation("X-S")
        assertTrue(OnlineAiHeaderPolicy.problems(withDerived).isEmpty())
        val withSecret = full.dropLast(1) + secret("X-Secret-Header-Name-000")
        assertTrue(OnlineAiHeaderPolicy.problems(withSecret, mapOf("x-secret-header-name-000" to 1000)).isEmpty())
        assertEquals(
            listOf(OnlineAiHeaderProblem.TotalTooLarge(8193, 8192)),
            OnlineAiHeaderPolicy.problems(withSecret, mapOf("x-secret-header-name-000" to 1001)),
        )
        assertEquals(36, OnlineAiHeaderPolicy.CONVERSATION_VALUE_BYTES)
    }

    @Test
    fun validationFailureListsProblemKindsWithoutValues() {
        val failure = assertThrows(OnlineAiHeaderValidationException::class.java) {
            OnlineAiHeaderPolicy.requireValid(
                listOf(
                    fixed("Authorization", "Bearer placeholder-not-a-key"),
                    fixed("X-Trace", "line\nbreak-placeholder"),
                ),
            )
        }

        assertEquals(
            listOf(
                OnlineAiHeaderProblem.NameReserved(0, OnlineAiReservedHeaderOwner.PLUGIN),
                OnlineAiHeaderProblem.ValueInvalidCharacter(1),
            ),
            failure.problems,
        )
        assertFalse(failure.message.orEmpty().contains("placeholder"))
        assertTrue(failure.message.orEmpty().contains("NameReserved"))
    }

    @Test
    fun profileNormalizationValidatesAndTrimsHeadersInOrder() {
        val normalized = OnlineAiProfilePolicy.normalizeProfile(
            profile().copy(
                headers = listOf(
                    fixed(" X-Title ", " AutoJs6 test "),
                    secret("X-Api-Token"),
                    conversation("x-session-affinity"),
                ),
            ),
        )

        assertEquals(
            listOf(fixed("X-Title", "AutoJs6 test"), secret("X-Api-Token"), conversation("x-session-affinity")),
            normalized.headers,
        )
        assertEquals("x-session-affinity", normalized.headers.last().key)
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfilePolicy.normalizeProfile(profile().copy(headers = listOf(fixed("Host", "example.com"))))
        }
        val created = OnlineAiProfile.create(
            displayName = "Gateway",
            provider = OnlineAiProvider.OPENAI_COMPATIBLE,
            baseUrl = "https://gateway.example.com/v1",
            modelId = "model-a",
            headers = listOf(fixed("X-Title", "AutoJs6")),
        )
        assertEquals(listOf(fixed("X-Title", "AutoJs6")), created.headers)
        assertEquals(emptyList<OnlineAiCustomHeader>(), profile().headers)
    }

    @Test
    fun secretSuggestionLooksForCredentialLikeWords() {
        listOf("X-Auth-Token", "X-API-KEY", "Api-Key", "X-Client-Secret", "x-access-token", "X-Authorization-Extra").forEach { name ->
            assertTrue(name, OnlineAiHeaderPolicy.suggestsSecret(name))
        }
        listOf("X-Title", "HTTP-Referer", "X-Session-Affinity", "User-Agent", "anthropic-beta").forEach { name ->
            assertFalse(name, OnlineAiHeaderPolicy.suggestsSecret(name))
        }
    }

    @Test
    fun valueKindsHaveStableWireIds() {
        assertEquals(listOf("fixed", "secret", "conversation"), OnlineAiHeaderValueKind.entries.map { it.wireId })
        OnlineAiHeaderValueKind.entries.forEach { kind ->
            assertEquals(kind, OnlineAiHeaderValueKind.fromWireId(kind.wireId))
        }
        assertThrows(IllegalArgumentException::class.java) { OnlineAiHeaderValueKind.fromWireId("FIXED") }
    }

    private fun titleCase(name: String): String = name.split('-').joinToString("-") { part ->
        part.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun profile() = OnlineAiProfile(
        profileId = "550e8400-e29b-41d4-a716-446655440000",
        displayName = "Work",
        provider = OnlineAiProvider.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com/v1",
        modelId = "model-a",
    )

    internal companion object {
        fun fixed(name: String, value: String) = OnlineAiCustomHeader(name, OnlineAiHeaderValueKind.FIXED, value)

        fun secret(name: String) = OnlineAiCustomHeader(name, OnlineAiHeaderValueKind.SECRET)

        fun conversation(name: String) = OnlineAiCustomHeader(name, OnlineAiHeaderValueKind.CONVERSATION)
    }
}
