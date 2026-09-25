package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiProfilePolicyTest {
    @Test
    fun profileNormalizationProducesCanonicalUuidTextAndHttpsUrl() {
        val normalized = OnlineAiProfilePolicy.normalizeProfile(
            profile(
                profileId = " 550E8400-E29B-41D4-A716-446655440000 ",
                displayName = "  Work  ",
                baseUrl = " HTTPS://API.Example.COM:443/v1/ ",
                modelId = " model-a ",
            ),
        )

        assertEquals("550e8400-e29b-41d4-a716-446655440000", normalized.profileId)
        assertEquals("Work", normalized.displayName)
        assertEquals("https://api.example.com/v1", normalized.baseUrl)
        assertEquals("https://api.example.com", normalized.declaredHttpsOrigin)
        assertEquals("model-a", normalized.modelId)
        assertEquals(listOf("model-a"), normalized.modelIds)
    }

    @Test
    fun urlsRejectNonHttpsCredentialsAndAmbiguousComponents() {
        listOf(
            "http://api.example.com/v1",
            "https://user:secret@api.example.com/v1",
            "https://api.example.com/v1?tenant=one",
            "https://api.example.com/v1#fragment",
            "https:///v1",
        ).forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) {
                OnlineAiProfileUrls.normalize(value)
            }
        }
    }

    @Test
    fun baseUrlHistoryKeepsRecentUniqueValidHttpsUrlsWithinItsLimit() {
        val values = buildList {
            add(" HTTPS://API.Example.COM:443/v1/ ")
            add("https://api.example.com/v1")
            add("http://insecure.example.com")
            repeat(OnlineAiBaseUrlHistoryPolicy.MAXIMUM_ENTRIES + 5) { index ->
                add("https://$index.example.com/v1/")
            }
        }

        val normalized = OnlineAiBaseUrlHistoryPolicy.normalized(values)

        assertEquals(OnlineAiBaseUrlHistoryPolicy.MAXIMUM_ENTRIES, normalized.size)
        assertEquals("https://api.example.com/v1", normalized.first())
        assertEquals("https://0.example.com/v1", normalized[1])
        assertFalse(normalized.any { it.startsWith("http://") })
    }

    @Test
    fun credentialDestinationDependsOnProviderAndOriginNotBasePath() {
        val first = OnlineAiProfilePolicy.normalizeProfile(
            profile(baseUrl = "https://api.example.com/v1"),
        )
        val sameOrigin = OnlineAiProfilePolicy.normalizeProfile(
            profile(baseUrl = "https://api.example.com/team/v1"),
        )
        val anotherOrigin = OnlineAiProfilePolicy.normalizeProfile(
            profile(baseUrl = "https://other.example.com/v1"),
        )
        val anotherProvider = OnlineAiProfilePolicy.normalizeProfile(
            profile(provider = OnlineAiProvider.OPENAI),
        )

        assertTrue(OnlineAiProfileUrls.sameCredentialDestination(first, sameOrigin))
        assertFalse(OnlineAiProfileUrls.sameCredentialDestination(first, anotherOrigin))
        assertFalse(OnlineAiProfileUrls.sameCredentialDestination(first, anotherProvider))
    }

    @Test
    fun documentsSortProfilesAndRejectDuplicateNamesIgnoringCase() {
        val later = profile(
            profileId = "650e8400-e29b-41d4-a716-446655440000",
            displayName = "Later",
        )
        val earlier = profile(displayName = "Earlier")
        val normalized = OnlineAiProfilePolicy.normalize(
            OnlineAiProfileDocument(revision = 1L, profiles = listOf(later, earlier)),
        )

        assertEquals(listOf(earlier.profileId, later.profileId), normalized.profiles.map { it.profileId })
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfilePolicy.normalize(
                OnlineAiProfileDocument(
                    1L,
                    listOf(
                        earlier.copy(displayName = "Shared"),
                        later.copy(displayName = "shared"),
                    ),
                ),
            )
        }
    }

    @Test
    fun strictCodecRoundTripsAndRejectsAnyAuthenticationField() {
        val configuredProfile = profile(displayName = "A \"profile\"")
        val document = OnlineAiProfileDocument(
            revision = 7L,
            profiles = listOf(configuredProfile),
            defaultProfileId = configuredProfile.profileId,
            allowMeteredNetwork = true,
        )
        val encoded = OnlineAiProfileCodec.encode(document)
        val text = encoded.toString(Charsets.UTF_8)

        assertEquals(OnlineAiProfilePolicy.normalize(document), OnlineAiProfileCodec.decode(encoded))
        assertFalse(text.contains("apiKey", ignoreCase = true))
        assertFalse(text.contains("authorization", ignoreCase = true))
        val withCredential = text.replace(
            "\"modelId\":\"model-a\"",
            "\"modelId\":\"model-a\",\"apiKey\":\"must-not-persist\"",
        ).toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfileCodec.decode(withCredential)
        }
        assertThrows(Exception::class.java) {
            OnlineAiProfileCodec.decode(encoded + " {}".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfileCodec.decode(
                text.replace("\"schema\":4", "\"schema\":1").toByteArray(),
            )
        }
    }

    @Test
    fun currentCodecRoundTripsMultipleModelsAndMigratesLegacyDocuments() {
        val configuredProfile = profile().copy(
            modelId = "model-b",
            modelIds = listOf("model-a", "model-b", "model-c"),
        )
        val encoded = OnlineAiProfileCodec.encode(
            OnlineAiProfileDocument(revision = 3L, profiles = listOf(configuredProfile)),
        )
        val decoded = OnlineAiProfileCodec.decode(encoded)

        assertEquals("model-b", decoded.profiles.single().modelId)
        assertEquals(listOf("model-a", "model-b", "model-c"), decoded.profiles.single().modelIds)

        val legacy = encoded.toString(Charsets.UTF_8)
            .replace("\"schema\":4", "\"schema\":2")
            .replace(",\"modelIds\":[\"model-a\",\"model-b\",\"model-c\"]", "")
            .replace(",\"visionModelIds\":[]", "")
            .toByteArray()
        val migrated = OnlineAiProfileCodec.decode(legacy).profiles.single()

        assertEquals("model-b", migrated.modelId)
        assertEquals(listOf("model-b"), migrated.modelIds)
    }

    @Test
    fun profilesRequireASelectedDefaultModelAndBoundedSchemaNumbers() {
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfilePolicy.normalizeProfile(profile().copy(modelIds = emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfilePolicy.normalizeProfile(
                profile().copy(modelId = "model-b", modelIds = listOf("model-a")),
            )
        }
        val encoded = OnlineAiProfileCodec.encode(
            OnlineAiProfileDocument(revision = 1L, profiles = listOf(profile())),
        ).toString(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfileCodec.decode(
                encoded.replace("\"schema\":4", "\"schema\":4294967299").toByteArray(),
            )
        }
    }

    @Test
    fun settingsRequireAnExistingDefaultAndDeleteClearsItAtomically() {
        val configuredProfile = profile()
        val selected = OnlineAiProfilePolicy.updateSettings(
            OnlineAiProfilePolicy.upsert(OnlineAiProfilePolicy.empty(), configuredProfile).document,
            OnlineAiServiceSettings(
                defaultProfileId = configuredProfile.profileId,
                allowMeteredNetwork = true,
            ),
        )

        assertEquals(configuredProfile.profileId, selected.defaultProfileId)
        assertTrue(selected.allowMeteredNetwork)
        val deleted = OnlineAiProfilePolicy.delete(selected, configuredProfile.profileId).document
        assertEquals(null, deleted.defaultProfileId)
        assertTrue(deleted.allowMeteredNetwork)
        assertThrows(IllegalArgumentException::class.java) {
            OnlineAiProfilePolicy.updateSettings(
                OnlineAiProfilePolicy.empty(),
                OnlineAiServiceSettings(
                    defaultProfileId = configuredProfile.profileId,
                    allowMeteredNetwork = false,
                ),
            )
        }
    }

    @Test
    fun repositoryPublishesOnlyChangedDocumentsWithMonotonicRevisions() {
        val storage = MemoryProfileStorage()
        val repository = OnlineAiProfileRepository(storage)
        val profile = profile()

        assertEquals(1L, repository.snapshot().revision)
        val first = repository.save(profile)
        assertTrue(first.changed)
        assertEquals(2L, first.document.revision)
        assertEquals(1, storage.writeCount)
        val unchanged = repository.save(profile)
        assertFalse(unchanged.changed)
        assertEquals(2L, unchanged.document.revision)
        assertEquals(1, storage.writeCount)
        val removed = repository.delete(profile.profileId)
        assertTrue(removed.changed)
        assertEquals(3L, removed.document.revision)
        assertEquals(2, storage.writeCount)
        assertFalse(repository.delete(profile.profileId).changed)
        assertEquals(2, storage.writeCount)
    }

    @Test
    fun encodedStorageReceivesAnOwnedCopy() {
        val storage = MemoryProfileStorage()
        val repository = OnlineAiProfileRepository(storage)
        repository.save(profile())
        val first = requireNotNull(storage.snapshotBytes())
        val second = requireNotNull(storage.snapshotBytes())

        first.fill(0)

        assertNotEquals(first.toList(), second.toList())
        assertArrayEquals(second, storage.snapshotBytes())
    }

    private fun profile(
        profileId: String = "550e8400-e29b-41d4-a716-446655440000",
        displayName: String = "Work",
        provider: OnlineAiProvider = OnlineAiProvider.OPENAI_COMPATIBLE,
        baseUrl: String = "https://api.example.com/v1",
        modelId: String = "model-a",
    ) = OnlineAiProfile(
        profileId = profileId,
        displayName = displayName,
        provider = provider,
        baseUrl = baseUrl,
        modelId = modelId,
    )

    private class MemoryProfileStorage : OnlineAiProfileDocumentStorage {
        private var bytes: ByteArray? = null
        var writeCount: Int = 0
            private set

        override fun <T> withExclusiveAccess(action: (OnlineAiProfileDocumentAccess) -> T): T = synchronized(this) {
            action(
                object : OnlineAiProfileDocumentAccess {
                    override fun read(): ByteArray? = bytes?.copyOf()
                    override fun write(encodedDocument: ByteArray) {
                        bytes = encodedDocument.copyOf()
                        writeCount += 1
                    }
                },
            )
        }

        fun snapshotBytes(): ByteArray? = synchronized(this) { bytes?.copyOf() }
    }
}
