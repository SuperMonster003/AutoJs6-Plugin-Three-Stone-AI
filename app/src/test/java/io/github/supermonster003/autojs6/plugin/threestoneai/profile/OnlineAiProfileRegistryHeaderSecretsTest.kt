package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialStore
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeadersTest.Companion.conversation
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeadersTest.Companion.fixed
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeadersTest.Companion.secret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiProfileRegistryHeaderSecretsTest {
    @Test
    fun secretValuesLiveInTheirOwnEncryptedRecordAndNeverInTheProfileDocument() {
        val fixture = Fixture()
        val apiKey = API_KEY.toCharArray()
        val token = "  $TOKEN_VALUE  ".toCharArray()
        val organization = ORGANIZATION_VALUE.toCharArray()

        val saved = fixture.registry.save(
            profile(),
            OnlineAiCredentialUpdate.Replace.takingOwnership(apiKey),
            OnlineAiHeaderSecretsUpdate.Put.takingOwnership(
                mapOf("x-api-token" to token, "X-ORG-SECRET" to organization),
            ),
        )

        assertTrue(saved.configured)
        assertEquals(listOf("profile-write", "put:key", "put:headers"), fixture.events)
        assertEquals(
            "v1\nx-api-token:$TOKEN_VALUE\nx-org-secret:$ORGANIZATION_VALUE\n",
            fixture.credentials.text(HEADERS_RECORD),
        )
        listOf(apiKey, token, organization).forEach { input -> assertTrue(input.all { it == '\u0000' }) }
        val document = requireNotNull(fixture.storage.bytes()).toString(Charsets.UTF_8)
        assertTrue(document.contains("{\"name\":\"X-Title\",\"kind\":\"fixed\",\"value\":\"AutoJs6\"}"))
        assertTrue(document.contains("{\"name\":\"X-Api-Token\",\"kind\":\"secret\"}"))
        listOf(API_KEY, TOKEN_VALUE, ORGANIZATION_VALUE).forEach { value -> assertFalse(document.contains(value)) }
        assertEquals(setOf("x-api-token", "x-org-secret"), fixture.registry.configuredHeaderSecrets(PROFILE_ID))

        var captured: ByteArray? = null
        val seen = fixture.registry.bindCredential(saved.profile).withCredentials { credential, secrets ->
            assertFalse(fixture.storage.accessActive)
            captured = secrets.valueOf("X-API-TOKEN")
            assertFalse(secrets.toString().contains(TOKEN_VALUE))
            listOf(
                credential.toString(Charsets.UTF_8),
                secrets.names.joinToString(","),
                requireNotNull(secrets.valueOf("x-api-token")).toString(Charsets.US_ASCII),
                requireNotNull(secrets.valueOf("x-org-secret")).toString(Charsets.US_ASCII),
            )
        }
        assertEquals(listOf(API_KEY, "x-api-token,x-org-secret", TOKEN_VALUE, ORGANIZATION_VALUE), seen)
        assertTrue(requireNotNull(captured).all { it == 0.toByte() })
        assertTrue(fixture.credentials.callbackBytes.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    @Test
    fun keepRetainsValuesWhileTheHeaderStaysSecretUnderTheSameName() {
        val fixture = Fixture()
        fixture.saveWithSecrets()
        fixture.events.clear()

        fixture.registry.save(profile().copy(displayName = "Renamed"))

        assertEquals(listOf("profile-write"), fixture.events)
        assertEquals(setOf("x-api-token", "x-org-secret"), fixture.registry.configuredHeaderSecrets(PROFILE_ID))
        fixture.events.clear()

        fixture.registry.save(
            profile().copy(headers = listOf(fixed("X-Title", "AutoJs6"), secret("X-Api-Token"))),
        )

        assertEquals(listOf("profile-write", "put:headers"), fixture.events)
        assertEquals("v1\nx-api-token:$TOKEN_VALUE\n", fixture.credentials.text(HEADERS_RECORD))
        fixture.events.clear()

        fixture.registry.save(
            profile().copy(headers = listOf(secret("X-Api-Token-2"), fixed("X-Org-Secret", "plain"))),
        )

        assertEquals(listOf("clear:headers", "profile-write"), fixture.events)
        assertNull(fixture.credentials.text(HEADERS_RECORD))
        assertEquals(emptySet<String>(), fixture.registry.configuredHeaderSecrets(PROFILE_ID))
        fixture.events.clear()

        fixture.registry.save(profile().copy(headers = listOf(fixed("X-Title", "AutoJs6"))))

        // The stored profile still declared a secret header, so the (already empty) record is cleared again.
        assertEquals(listOf("clear:headers", "profile-write"), fixture.events)
        assertEquals(API_KEY, fixture.revealKey())
    }

    @Test
    fun removingEverySecretHeaderClearsTheRecord() {
        val fixture = Fixture()
        fixture.saveWithSecrets()
        fixture.events.clear()

        fixture.registry.save(profile().copy(headers = listOf(fixed("X-Title", "AutoJs6"))))

        assertEquals(listOf("clear:headers", "profile-write"), fixture.events)
        assertNull(fixture.credentials.text(HEADERS_RECORD))
        assertEquals(API_KEY, fixture.revealKey())
    }

    @Test
    fun clearingTheApiKeyLeavesHeaderSecretsAndClearingHeaderSecretsLeavesTheApiKey() {
        val fixture = Fixture()
        fixture.saveWithSecrets()
        val record = fixture.credentials.text(HEADERS_RECORD)
        fixture.events.clear()

        val cleared = fixture.registry.save(profile(), OnlineAiCredentialUpdate.Clear)

        assertFalse(cleared.configured)
        // The metadata did not change, so the profile document is not rewritten.
        assertEquals(listOf("clear:key"), fixture.events)
        assertEquals(record, fixture.credentials.text(HEADERS_RECORD))
        assertEquals(setOf("x-api-token", "x-org-secret"), fixture.registry.configuredHeaderSecrets(PROFILE_ID))

        fixture.registry.save(profile(), OnlineAiCredentialUpdate.Replace.takingOwnership(API_KEY.toCharArray()))
        fixture.events.clear()

        fixture.registry.save(profile(), headerSecretsUpdate = OnlineAiHeaderSecretsUpdate.Clear)

        assertEquals(listOf("clear:headers"), fixture.events)
        assertEquals(emptySet<String>(), fixture.registry.configuredHeaderSecrets(PROFILE_ID))
        assertEquals(API_KEY, fixture.revealKey())
    }

    @Test
    fun providerOrHostChangeClearsHeaderSecretsWithTheApiKey() {
        val fixture = Fixture()
        fixture.saveWithSecrets()
        fixture.events.clear()
        val moved = profile().copy(baseUrl = "https://second.example.com/v1")

        fixture.registry.save(moved, OnlineAiCredentialUpdate.Replace.takingOwnership(API_KEY.toCharArray()))

        assertEquals(listOf("clear:key", "clear:headers", "profile-write", "put:key"), fixture.events)
        assertEquals(emptySet<String>(), fixture.registry.configuredHeaderSecrets(PROFILE_ID))

        fixture.registry.save(
            moved,
            headerSecretsUpdate = OnlineAiHeaderSecretsUpdate.Put.takingOwnership(
                mapOf("x-api-token" to "placeholder-token-2".toCharArray()),
            ),
        )
        fixture.events.clear()
        val otherProvider = moved.copy(
            provider = OnlineAiProvider.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
        )

        fixture.registry.save(
            otherProvider,
            OnlineAiCredentialUpdate.Clear,
            OnlineAiHeaderSecretsUpdate.Put.takingOwnership(
                mapOf("x-org-secret" to "placeholder-org-2".toCharArray()),
            ),
        )

        assertEquals(listOf("clear:key", "clear:headers", "profile-write", "put:headers"), fixture.events)
        assertEquals("v1\nx-org-secret:placeholder-org-2\n", fixture.credentials.text(HEADERS_RECORD))
        assertEquals(setOf("x-org-secret"), fixture.registry.configuredHeaderSecrets(PROFILE_ID))
    }

    @Test
    fun deletionClearsBothRecordsBeforeTheProfileIsRemoved() {
        val fixture = Fixture()
        fixture.saveWithSecrets()
        fixture.events.clear()

        assertTrue(fixture.registry.delete(PROFILE_ID))

        assertEquals(listOf("clear:key", "clear:headers", "profile-write"), fixture.events)
        assertTrue(fixture.credentials.records.isEmpty())
        assertEquals(emptySet<String>(), fixture.registry.configuredHeaderSecrets(PROFILE_ID))
    }

    @Test
    fun copiesAndImportsStartWithSecretValuesNotSet() {
        val fixture = Fixture()
        val source = fixture.saveWithSecrets().profile
        val copy = recreate(source, "Copy")

        fixture.registry.save(copy, OnlineAiCredentialUpdate.Clear)

        assertEquals(source.headers, fixture.registry.snapshot().profiles.single { it.profile.profileId == copy.profileId }.profile.headers)
        assertEquals(emptySet<String>(), fixture.registry.configuredHeaderSecrets(copy.profileId))
        assertNull(fixture.credentials.text(OnlineAiHeaderSecretRecord.recordId(copy.profileId)))
        assertEquals(setOf("x-api-token", "x-org-secret"), fixture.registry.configuredHeaderSecrets(PROFILE_ID))

        val exported = OnlineAiProfileTransferCodec.encode(fixture.registry.snapshot()).toString(Charsets.UTF_8)
        assertTrue(exported.contains("\"value\":\"AutoJs6\""))
        listOf(API_KEY, TOKEN_VALUE, ORGANIZATION_VALUE).forEach { value -> assertFalse(exported.contains(value)) }
        val importedSource = OnlineAiProfileTransferCodec.decode(exported.toByteArray()).profiles
            .single { it.profileId == PROFILE_ID }
        val imported = recreate(importedSource, "Imported")

        fixture.registry.save(imported, OnlineAiCredentialUpdate.Clear)

        assertEquals(source.headers, importedSource.headers)
        assertEquals(emptySet<String>(), fixture.registry.configuredHeaderSecrets(imported.profileId))
    }

    @Test
    fun invalidSecretValuesAreRejectedBeforeAnyWriteAndInputIsCleared() {
        val headers = listOf(fixed("X-Title", "AutoJs6"), secret("X-Api-Token"))
        listOf(
            "" to OnlineAiHeaderProblem.ValueEmpty(1),
            "   " to OnlineAiHeaderProblem.ValueEmpty(1),
            "placeholder\r\nInjected: yes" to OnlineAiHeaderProblem.ValueInvalidCharacter(1),
            "placeholder\u0000value" to OnlineAiHeaderProblem.ValueInvalidCharacter(1),
            "placeholder\tvalue" to OnlineAiHeaderProblem.ValueInvalidCharacter(1),
            "placeholder-caf\u00e9" to OnlineAiHeaderProblem.ValueInvalidCharacter(1),
            "p".repeat(1025) to OnlineAiHeaderProblem.ValueTooLong(1, 1024),
        ).forEach { (value, expected) ->
            val fixture = Fixture()
            val input = value.toCharArray()

            val error = assertThrows(OnlineAiHeaderValidationException::class.java) {
                fixture.registry.save(
                    profile().copy(headers = headers),
                    headerSecretsUpdate = OnlineAiHeaderSecretsUpdate.Put.takingOwnership(mapOf("X-Api-Token" to input)),
                )
            }

            assertEquals(listOf(expected), error.problems)
            if (value.isNotBlank()) assertFalse(requireNotNull(error.message).contains(value.trim().take(11)))
            assertTrue(input.all { it == '\u0000' })
            assertTrue(fixture.events.isEmpty())
        }

        listOf(
            mapOf("X-Title" to "placeholder".toCharArray()),
            mapOf("X-Unknown" to "placeholder".toCharArray()),
            mapOf("x-api-token" to "placeholder".toCharArray(), "X-API-TOKEN" to "placeholder".toCharArray()),
        ).forEach { values ->
            val fixture = Fixture()
            assertThrows(IllegalArgumentException::class.java) {
                fixture.registry.save(
                    profile().copy(headers = headers),
                    headerSecretsUpdate = OnlineAiHeaderSecretsUpdate.Put.takingOwnership(values),
                )
            }
            assertTrue(values.values.all { input -> input.all { it == '\u0000' } })
            assertTrue(fixture.events.isEmpty())
        }
    }

    @Test
    fun storedSecretValuesCountTowardsTheEightKibibyteTotal() {
        val fixedHeaders = (1..7).map { index ->
            fixed("X-Header-Number-" + index.toString().padStart(8, '0'), "v".repeat(1000))
        }
        val headers = fixedHeaders + secret("X-Api-Token")

        val accepted = Fixture()
        accepted.registry.save(
            profile().copy(headers = headers),
            headerSecretsUpdate = OnlineAiHeaderSecretsUpdate.Put.takingOwnership(
                mapOf("X-Api-Token" to "s".repeat(1013).toCharArray()),
            ),
        )
        assertEquals(setOf("x-api-token"), accepted.registry.configuredHeaderSecrets(PROFILE_ID))

        val rejected = Fixture()
        val error = assertThrows(OnlineAiHeaderValidationException::class.java) {
            rejected.registry.save(
                profile().copy(headers = headers),
                headerSecretsUpdate = OnlineAiHeaderSecretsUpdate.Put.takingOwnership(
                    mapOf("X-Api-Token" to "s".repeat(1014).toCharArray()),
                ),
            )
        }
        assertEquals(listOf(OnlineAiHeaderProblem.TotalTooLarge(8193, 8192)), error.problems)
        assertTrue(rejected.events.isEmpty())

        accepted.events.clear()
        val grown = assertThrows(OnlineAiHeaderValidationException::class.java) {
            accepted.registry.save(
                profile().copy(headers = fixedHeaders + fixed("X-Extra", "v") + secret("X-Api-Token")),
            )
        }
        assertTrue(grown.problems.single() is OnlineAiHeaderProblem.TotalTooLarge)
        assertTrue(accepted.events.isEmpty())
    }

    @Test
    fun unreadableRecordCountsAsNotSetButFailsRequests() {
        val fixture = Fixture()
        val saved = fixture.saveWithSecrets().profile
        fixture.credentials.unreadable += HEADERS_RECORD
        fixture.events.clear()

        assertEquals(emptySet<String>(), fixture.registry.configuredHeaderSecrets(PROFILE_ID))
        val renamed = fixture.registry.save(saved.copy(displayName = "Renamed")).profile
        assertEquals(listOf("profile-write"), fixture.events)
        assertEquals(API_KEY, fixture.revealKey())
        val error = assertThrows(AiCredentialUnavailableException::class.java) {
            fixture.registry.bindCredential(renamed).withCredentials { _, _ -> Unit }
        }
        assertEquals(AiCredentialFailureReason.UNAVAILABLE, error.reason)

        fixture.registry.save(
            renamed,
            headerSecretsUpdate = OnlineAiHeaderSecretsUpdate.Put.takingOwnership(
                mapOf("x-api-token" to TOKEN_VALUE.toCharArray()),
            ),
        )

        assertEquals(setOf("x-api-token"), fixture.registry.configuredHeaderSecrets(PROFILE_ID))
    }

    @Test
    fun malformedRecordIsUnavailableAndRequestsOnlySeeHeadersThatAreSecretNow() {
        val fixture = Fixture()
        val saved = fixture.saveWithSecrets().profile
        fixture.credentials.put(HEADERS_RECORD, "v1\nx-api-token:$TOKEN_VALUE\nx-stale:placeholder-stale\n".toCharArray())

        val names = fixture.registry.bindCredential(saved).withCredentials { _, secrets -> secrets.names }

        assertEquals(setOf("x-api-token"), names)
        fixture.credentials.put(HEADERS_RECORD, "v1\nx-api-token:$TOKEN_VALUE".toCharArray())
        val error = assertThrows(AiCredentialUnavailableException::class.java) {
            fixture.registry.bindCredential(saved).withCredentials { _, _ -> Unit }
        }
        assertEquals(AiCredentialFailureReason.UNAVAILABLE, error.reason)
        assertFalse(requireNotNull(error.message).contains(TOKEN_VALUE))

        val plain = Fixture()
        val withoutSecrets = plain.registry.save(
            profile().copy(headers = listOf(fixed("X-Title", "AutoJs6"), conversation("X-Session"))),
            OnlineAiCredentialUpdate.Replace.takingOwnership(API_KEY.toCharArray()),
        ).profile
        plain.credentials.put(HEADERS_RECORD, "not a record".toCharArray())
        assertEquals(
            emptySet<String>(),
            plain.registry.bindCredential(withoutSecrets).withCredentials { _, secrets -> secrets.names },
        )
    }

    @Test
    fun recordFormatRoundTripsAndRejectsMalformedPlaintext() {
        val values = linkedMapOf(
            "x-a" to "placeholder value".toByteArray(),
            "x-b" to ":placeholder:colon:".toByteArray(),
        )
        val encoded = OnlineAiHeaderSecretRecord.encode(values)

        assertEquals("v1\nx-a:placeholder value\nx-b::placeholder:colon:\n", encoded.concatToString())
        val decoded = OnlineAiHeaderSecretRecord.decode(encoded.concatToString().toByteArray())
        assertEquals(values.keys, decoded.keys)
        assertEquals(values.values.map { it.toList() }, decoded.values.map { it.toList() })
        assertEquals(emptyMap<String, ByteArray>(), OnlineAiHeaderSecretRecord.decode("v1\n".toByteArray()))
        assertEquals("550e8400-e29b-41d4-a716-446655440000.headers", OnlineAiHeaderSecretRecord.recordId(PROFILE_ID))

        listOf(
            "",
            "v2\nx-a:placeholder\n",
            "v1\nX-A:placeholder\n",
            "v1\nx-a:placeholder",
            "v1\n:placeholder\n",
            "v1\nx-a:\n",
            "v1\nx-a: placeholder\n",
            "v1\nx-a:placeholder \n",
            "v1\nx-a:placeholder\nx-a:placeholder\n",
            "v1\nx a:placeholder\n",
            "v1\nx-a:placeholder\tvalue\n",
            "v1\nx-a:placeholder-caf\u00e9\n",
            "v1\nx-a:" + "p".repeat(1025) + "\n",
        ).forEach { plaintext ->
            val error = assertThrows(plaintext, IllegalArgumentException::class.java) {
                OnlineAiHeaderSecretRecord.decode(plaintext.toByteArray(Charsets.UTF_8))
            }
            assertFalse(requireNotNull(error.message).contains("placeholder"))
        }
    }

    @Test
    fun updatesAndSecretSetsNeverPrintValues() {
        val input = TOKEN_VALUE.toCharArray()
        val update = OnlineAiHeaderSecretsUpdate.Put.takingOwnership(mapOf("X-Api-Token" to input))

        assertFalse(update.toString().contains(TOKEN_VALUE))
        assertTrue(update.toString().contains("X-Api-Token"))
        val secrets = OnlineAiHeaderSecrets.takingOwnership(mapOf("x-api-token" to TOKEN_VALUE.toByteArray()))
        assertFalse(secrets.toString().contains(TOKEN_VALUE))
        val value = requireNotNull(secrets.valueOf("X-Api-Token"))
        secrets.close()
        assertTrue(value.all { it == 0.toByte() })
        assertTrue(OnlineAiHeaderSecrets.NONE.names.isEmpty())
    }

    private fun recreate(source: OnlineAiProfile, name: String) = OnlineAiProfile.create(
        displayName = name,
        provider = source.provider,
        baseUrl = source.baseUrl,
        modelId = source.modelId,
        modelIds = source.modelIds,
        visionModelIds = source.visionModelIds,
        defaultEffort = source.defaultEffort,
        fastMode = source.fastMode,
        headers = source.headers,
    )

    private class Fixture {
        val events = mutableListOf<String>()
        val storage = MemoryProfileStorage(events)
        val credentials = RecordingCredentialStore(events)
        val registry = OnlineAiProfileRegistry(OnlineAiProfileRepository(storage), credentials)

        fun saveWithSecrets(): ConfiguredOnlineAiProfile = registry.save(
            profile(),
            OnlineAiCredentialUpdate.Replace.takingOwnership(API_KEY.toCharArray()),
            OnlineAiHeaderSecretsUpdate.Put.takingOwnership(
                mapOf(
                    "X-Api-Token" to TOKEN_VALUE.toCharArray(),
                    "X-Org-Secret" to ORGANIZATION_VALUE.toCharArray(),
                ),
            ),
        )

        fun revealKey(): String = registry.withCredential(PROFILE_ID) { _, bytes -> bytes.toString(Charsets.UTF_8) }
    }

    private class MemoryProfileStorage(
        private val events: MutableList<String>,
    ) : OnlineAiProfileDocumentStorage {
        private var document: ByteArray? = null

        @Volatile
        var accessActive = false
            private set

        override fun <T> withExclusiveAccess(action: (OnlineAiProfileDocumentAccess) -> T): T = synchronized(this) {
            accessActive = true
            try {
                action(
                    object : OnlineAiProfileDocumentAccess {
                        override fun read(): ByteArray? = document?.copyOf()
                        override fun write(encodedDocument: ByteArray) {
                            events += "profile-write"
                            document = encodedDocument.copyOf()
                        }
                    },
                )
            } finally {
                accessActive = false
            }
        }

        fun bytes(): ByteArray? = synchronized(this) { document?.copyOf() }
    }

    /** Behaves like EncryptedAiCredentialStore for missing and unreadable records. */
    private class RecordingCredentialStore(
        private val events: MutableList<String>,
    ) : AiCredentialStore {
        val records = linkedMapOf<String, CharArray>()
        val unreadable = mutableSetOf<String>()
        val callbackBytes = mutableListOf<ByteArray>()

        override fun put(profileId: String, credential: CharArray) {
            try {
                events += "put:" + label(profileId)
                unreadable -= profileId
                records.put(profileId, credential.copyOf())?.fill('\u0000')
            } finally {
                credential.fill('\u0000')
            }
        }

        override fun isConfigured(profileId: String): Boolean =
            profileId !in unreadable && records[profileId]?.isNotEmpty() == true

        override fun <T> withCredential(profileId: String, action: (ByteArray) -> T): T {
            if (profileId in unreadable) throw AiCredentialUnavailableException(AiCredentialFailureReason.UNAVAILABLE)
            val characters = records[profileId]
                ?: throw AiCredentialUnavailableException(AiCredentialFailureReason.NOT_CONFIGURED)
            val bytes = characters.concatToString().toByteArray(Charsets.UTF_8)
            callbackBytes += bytes
            return try {
                action(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        override fun clear(profileId: String): Boolean {
            events += "clear:" + label(profileId)
            unreadable -= profileId
            return records.remove(profileId)?.let { removed ->
                removed.fill('\u0000')
                true
            } ?: false
        }

        fun text(profileId: String): String? = records[profileId]?.concatToString()

        private fun label(profileId: String): String = if (profileId.endsWith(".headers")) "headers" else "key"
    }

    private companion object {
        const val PROFILE_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val HEADERS_RECORD = "$PROFILE_ID.headers"
        const val API_KEY = "placeholder-api-key"
        const val TOKEN_VALUE = "placeholder-token-value"
        const val ORGANIZATION_VALUE = "placeholder-org-value"

        fun profile() = OnlineAiProfile(
            profileId = PROFILE_ID,
            displayName = "Work",
            provider = OnlineAiProvider.OPENAI_COMPATIBLE,
            baseUrl = "https://api.example.com/v1",
            modelId = "model-a",
            headers = listOf(
                fixed("X-Title", "AutoJs6"),
                secret("X-Api-Token"),
                secret("X-Org-Secret"),
                conversation("X-Session"),
            ),
        )
    }
}
