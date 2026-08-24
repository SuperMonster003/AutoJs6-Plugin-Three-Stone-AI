package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiProfileRegistryTest {
    @Test
    fun changingOriginRequiresExplicitCredentialDisposition() {
        val fixture = fixture()
        val original = profile(baseUrl = "https://first.example.com/v1")
        fixture.registry.save(original, replacement("old-secret"))
        fixture.events.clear()

        assertThrows(OnlineAiCredentialReentryRequiredException::class.java) {
            fixture.registry.save(
                original.copy(baseUrl = "https://second.example.com/v1"),
                OnlineAiCredentialUpdate.Keep,
            )
        }

        assertTrue(fixture.events.isEmpty())
        assertEquals(original.baseUrl, fixture.repository.find(original.profileId)?.baseUrl)
        assertEquals("old-secret", fixture.reveal(original.profileId))
    }

    @Test
    fun pathOnlyChangeCanKeepCredentialBecauseOriginIsStable() {
        val fixture = fixture()
        val original = profile(baseUrl = "https://api.example.com/v1")
        fixture.registry.save(original, replacement("path-secret"))
        fixture.events.clear()

        val saved = fixture.registry.save(
            original.copy(baseUrl = "https://api.example.com/team/v1"),
            OnlineAiCredentialUpdate.Keep,
        )

        assertTrue(saved.configured)
        assertEquals(listOf("profile-write"), fixture.events)
        assertEquals("path-secret", fixture.reveal(original.profileId))
    }

    @Test
    fun replacementOnNewOriginClearsBeforePublishingAndStoresAfterward() {
        val fixture = fixture()
        val original = profile(baseUrl = "https://first.example.com/v1")
        fixture.registry.save(original, replacement("old-secret"))
        fixture.events.clear()
        val newCharacters = "new-secret".toCharArray()

        val saved = fixture.registry.save(
            original.copy(baseUrl = "https://second.example.com/v1"),
            OnlineAiCredentialUpdate.Replace.takingOwnership(newCharacters),
        )

        assertEquals(listOf("credential-clear", "profile-write", "credential-put"), fixture.events)
        assertTrue(saved.configured)
        assertEquals("new-secret", fixture.reveal(original.profileId))
        assertTrue(newCharacters.all { it == '\u0000' })
    }

    @Test
    fun clearOnNewOriginPublishesOnlyAnUnconfiguredProfile() {
        val fixture = fixture()
        val original = profile(baseUrl = "https://first.example.com/v1")
        fixture.registry.save(original, replacement("old-secret"))
        fixture.events.clear()

        val saved = fixture.registry.save(
            original.copy(baseUrl = "https://second.example.com/v1"),
            OnlineAiCredentialUpdate.Clear,
        )

        assertEquals(listOf("credential-clear", "profile-write"), fixture.events)
        assertFalse(saved.configured)
        assertFalse(fixture.credentials.isConfigured(original.profileId))
    }

    @Test
    fun replacementInputIsClearedEvenWhenProfileValidationFails() {
        val fixture = fixture()
        val characters = "must-be-cleared".toCharArray()

        assertThrows(IllegalArgumentException::class.java) {
            fixture.registry.save(
                profile(baseUrl = "http://insecure.example.com/v1"),
                OnlineAiCredentialUpdate.Replace.takingOwnership(characters),
            )
        }

        assertTrue(characters.all { it == '\u0000' })
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun deletionClearsCredentialBeforeProfilePublication() {
        val fixture = fixture()
        val profile = profile()
        fixture.registry.save(profile, replacement("delete-secret"))
        fixture.events.clear()

        assertTrue(fixture.registry.delete(profile.profileId))

        assertEquals(listOf("credential-clear", "profile-write"), fixture.events)
        assertTrue(fixture.registry.snapshot().profiles.isEmpty())
    }

    @Test
    fun defaultSelectionRequiresACredentialAndDeletionClearsTheSelection() {
        val fixture = fixture()
        val profile = profile()
        fixture.registry.save(profile)

        assertThrows(IllegalArgumentException::class.java) {
            fixture.registry.setDefaultProfile(profile.profileId)
        }

        fixture.registry.save(profile, replacement("default-secret"))
        fixture.registry.setDefaultProfile(profile.profileId)
        fixture.registry.setAllowMeteredNetwork(true)
        val selected = fixture.registry.snapshot()
        assertEquals(profile.profileId, selected.defaultProfileId)
        assertTrue(selected.allowMeteredNetwork)

        fixture.registry.delete(profile.profileId)
        val deleted = fixture.registry.snapshot()
        assertEquals(null, deleted.defaultProfileId)
        assertTrue(deleted.allowMeteredNetwork)
    }

    @Test
    fun clearingTheCredentialAlsoClearsItsDefaultSelection() {
        val fixture = fixture()
        val profile = profile()
        fixture.registry.save(profile, replacement("default-secret"))
        fixture.registry.setDefaultProfile(profile.profileId)
        fixture.events.clear()

        val cleared = fixture.registry.save(profile, OnlineAiCredentialUpdate.Clear)

        assertFalse(cleared.configured)
        assertEquals(null, fixture.registry.snapshot().defaultProfileId)
        assertEquals(listOf("credential-clear", "profile-write"), fixture.events)
    }

    @Test
    fun registrySnapshotsExposeOnlyConfiguredStateAndCredentialCallbackIsEphemeral() {
        val fixture = fixture()
        val profile = profile()
        fixture.registry.save(profile, replacement("callback-secret"))

        val snapshot = fixture.registry.snapshot()
        assertEquals(listOf(profile.profileId), snapshot.profiles.map { it.profile.profileId })
        assertTrue(snapshot.profiles.single().configured)
        var callbackBytes: ByteArray? = null
        assertEquals(
            "callback-secret",
            fixture.registry.withCredential(profile.profileId) { _, bytes ->
                assertFalse(fixture.storage.accessActive)
                callbackBytes = bytes
                bytes.toString(Charsets.UTF_8)
            },
        )
        assertTrue(requireNotNull(callbackBytes).all { it == 0.toByte() })
        assertTrue(requireNotNull(fixture.credentials.lastCallbackBytes).all { it == 0.toByte() })
        val documentText = requireNotNull(fixture.storage.bytes()).toString(Charsets.UTF_8)
        assertFalse(documentText.contains("callback-secret"))
        assertFalse(documentText.contains("apiKey", ignoreCase = true))
    }

    private fun replacement(value: String): OnlineAiCredentialUpdate.Replace =
        OnlineAiCredentialUpdate.Replace.takingOwnership(value.toCharArray())

    private fun profile(
        baseUrl: String = "https://api.example.com/v1",
    ) = OnlineAiProfile(
        profileId = "550e8400-e29b-41d4-a716-446655440000",
        displayName = "Work",
        provider = OnlineAiProvider.OPENAI_COMPATIBLE,
        baseUrl = baseUrl,
        modelId = "model-a",
    )

    private fun fixture(): Fixture {
        val events = mutableListOf<String>()
        val storage = MemoryProfileStorage(events)
        val repository = OnlineAiProfileRepository(storage)
        val credentials = FakeCredentialStore(events)
        return Fixture(
            events = events,
            storage = storage,
            repository = repository,
            credentials = credentials,
            registry = OnlineAiProfileRegistry(repository, credentials),
        )
    }

    private data class Fixture(
        val events: MutableList<String>,
        val storage: MemoryProfileStorage,
        val repository: OnlineAiProfileRepository,
        val credentials: FakeCredentialStore,
        val registry: OnlineAiProfileRegistry,
    ) {
        fun reveal(profileId: String): String = registry.withCredential(profileId) { _, bytes ->
            bytes.toString(Charsets.UTF_8)
        }
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

    private class FakeCredentialStore(
        private val events: MutableList<String>,
    ) : AiCredentialStore {
        private val values = mutableMapOf<String, CharArray>()
        var lastCallbackBytes: ByteArray? = null
            private set

        override fun put(profileId: String, credential: CharArray) {
            try {
                events += "credential-put"
                values.put(profileId, credential.copyOf())?.fill('\u0000')
            } finally {
                credential.fill('\u0000')
            }
        }

        override fun isConfigured(profileId: String): Boolean = values[profileId]?.isNotEmpty() == true

        override fun <T> withCredential(profileId: String, action: (ByteArray) -> T): T {
            val bytes = requireNotNull(values[profileId]).concatToString().toByteArray(Charsets.UTF_8)
            lastCallbackBytes = bytes
            return try {
                action(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        override fun clear(profileId: String): Boolean {
            events += "credential-clear"
            return values.remove(profileId)?.let { removed ->
                removed.fill('\u0000')
                true
            } ?: false
        }
    }
}
