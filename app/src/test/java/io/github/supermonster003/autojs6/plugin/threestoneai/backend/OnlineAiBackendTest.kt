package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AiCredentialStore
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCredentialUpdate
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileChangedException
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileCredentialAccess
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileDocumentAccess
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileDocumentStorage
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRepository
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProviderCatalog
import okhttp3.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiBackendTest {
    @Test
    fun productionCatalogMapsConfiguredProfileWithoutClaimingUnimplementedExecution() {
        val fixture = fixture()
        fixture.registry.save(profile(), replacement("secret"))
        val backend = OnlineAiBackend(fixture.registry)

        val catalog = backend.catalog()
        val target = catalog.targets.single()

        assertNull(catalog.defaultTargetId)
        assertEquals("profile:${profile().profileId}", target.targetId)
        assertEquals(OnlineAiBackend::class.java.simpleName, backend.javaClass.simpleName)
        assertEquals(OnlineAiBackend.BACKEND_ID, target.backendId)
        assertEquals("openai-compatible", target.providerId)
        assertEquals(profile().profileId, target.profileId)
        assertEquals("model-a", target.modelId)
        assertEquals(AiTargetLocality.REMOTE, target.locality)
        assertEquals(AiTargetCredentialMode.PLUGIN_MANAGED, target.credentialMode)
        assertEquals(listOf("https://api.example.com"), target.declaredHttpsOrigins)
        assertTrue(target.configured)
        assertFalse(target.available)
        assertFalse(target.capabilities.streaming)
        assertTrue(target.executionProfiles.isEmpty())
        assertThrows(AiTargetUnavailableException::class.java) {
            backend.createSession(AiBackendSessionRequest(target.targetId))
        }
    }

    @Test
    fun configuredStatusChangesCatalogGenerationWithoutChangingMetadataRevision() {
        val fixture = fixture()
        fixture.registry.save(profile())
        val backend = OnlineAiBackend(fixture.registry)
        val before = backend.catalog()
        val revisionBeforeCredential = fixture.registry.snapshot().revision

        fixture.registry.save(profile(), replacement("secret"))
        val after = backend.catalog()

        assertNotEquals(before.generation, after.generation)
        assertEquals(revisionBeforeCredential, fixture.registry.snapshot().revision)
        assertFalse(before.targets.single().configured)
        assertTrue(after.targets.single().configured)
    }

    @Test
    fun selectedDefaultProfileIsPublishedByTheOnlineCatalog() {
        val fixture = fixture()
        fixture.registry.save(profile(), replacement("secret"))
        fixture.registry.setDefaultProfile(profile().profileId)

        val catalog = OnlineAiBackend(fixture.registry, RecordingExecution()).catalog()

        assertEquals(AiTargetIds.profile(profile().profileId), catalog.defaultTargetId)
    }

    @Test
    fun everySelectedModelIsPublishedAndAlternativeSelectionUsesItsExactModel() {
        val fixture = fixture()
        val multiModelProfile = profile().copy(
            modelId = "model-b",
            modelIds = listOf("model-a", "model-b", "model-c"),
        )
        fixture.registry.save(multiModelProfile, replacement("execution-secret"))
        fixture.registry.setDefaultProfile(multiModelProfile.profileId)
        val execution = RecordingExecution()
        val backend = OnlineAiBackend(fixture.registry, execution)

        val catalog = backend.catalog()
        assertEquals(3, catalog.targets.size)
        assertEquals(listOf("model-a", "model-b", "model-c"), catalog.targets.map { it.modelId })
        assertEquals(
            AiTargetIds.profileModel(
                multiModelProfile.profileId,
                multiModelProfile.modelIds.first(),
                multiModelProfile.modelId,
            ),
            catalog.defaultTargetId,
        )
        val alternative = catalog.targets.single { it.modelId == "model-c" }
        assertNotEquals(AiTargetIds.profile(multiModelProfile.profileId), alternative.targetId)
        assertEquals(multiModelProfile.profileId, AiTargetIds.requireProfileId(alternative.targetId))

        backend.createSession(AiBackendSessionRequest(alternative.targetId))

        assertEquals("model-c", execution.profile?.modelId)
        assertEquals(multiModelProfile.modelIds, execution.profile?.modelIds)
        assertEquals("execution-secret", execution.credential)

        val targetIdsBefore = catalog.targets.associate { target -> target.modelId to target.targetId }
        fixture.registry.save(multiModelProfile.copy(modelId = "model-c"))
        val changedDefaultCatalog = backend.catalog()
        assertEquals(
            targetIdsBefore,
            changedDefaultCatalog.targets.associate { target -> target.modelId to target.targetId },
        )
        assertEquals(targetIdsBefore.getValue("model-c"), changedDefaultCatalog.defaultTargetId)
    }

    @Test
    fun injectedExecutionControlsAvailabilityCapabilitiesAndSessionCreation() {
        val fixture = fixture()
        fixture.registry.save(profile(), replacement("execution-secret"))
        val execution = RecordingExecution()
        val backend = OnlineAiBackend(fixture.registry, execution)
        val target = backend.catalog().targets.single()

        assertTrue(target.available)
        assertTrue(target.capabilities.streaming)
        assertTrue(target.capabilities.usage)
        assertEquals(8192, target.limits.maximumOutputTokens)
        assertEquals(32_768, target.limits.maximumContextTokens)
        val session = backend.createSession(AiBackendSessionRequest(target.targetId))

        assertEquals(target, session.target)
        assertEquals("execution-secret", execution.credential)
        assertEquals(profile(), execution.profile)
    }

    @Test
    fun httpExecutionPublishesOnlyItsImplementedCapabilitiesAndSafetyLimits() {
        val fixture = fixture()
        fixture.registry.save(profile(), replacement("execution-secret"))
        val execution = OnlineAiHttpExecution(
            Call.Factory { throw AssertionError("No network call expected") },
        )
        val backend = OnlineAiBackend(fixture.registry, execution)

        val target = backend.catalog().targets.single()

        assertTrue(target.available)
        assertTrue(target.capabilities.streaming)
        assertTrue(target.capabilities.persistentSession)
        assertTrue(target.capabilities.structuredJson)
        assertTrue(target.capabilities.usage)
        assertFalse(target.capabilities.reasoning)
        assertTrue(target.capabilities.tools)
        assertEquals(
            OnlineAiTransportLimits.MAXIMUM_CONTEXT_BYTES,
            target.limits.maximumContextBytes,
        )
        assertEquals(
            OnlineAiTransportLimits.MAXIMUM_OUTPUT_BYTES,
            target.limits.maximumOutputBytes,
        )
        assertNull(target.limits.maximumContextTokens)
        backend.createSession(AiBackendSessionRequest(target.targetId)).close()

        execution.close()

        assertFalse(backend.catalog().targets.single().available)
        assertThrows(AiTargetUnavailableException::class.java) {
            backend.createSession(AiBackendSessionRequest(target.targetId))
        }
    }

    @Test
    fun httpExecutionPublishesEveryProviderWithProtocolSpecificCapabilities() {
        val fixture = fixture()
        OnlineAiProvider.entries.forEachIndexed { index, provider ->
            fixture.registry.save(providerProfile(provider, index), replacement("secret-$index"))
        }
        val execution = OnlineAiHttpExecution(
            Call.Factory { throw AssertionError("No network call expected") },
        )
        val targets = OnlineAiBackend(fixture.registry, execution).catalog().targets

        assertEquals(
            OnlineAiProvider.entries.map(OnlineAiProvider::providerId),
            targets.map(AiTarget::providerId),
        )
        assertTrue(targets.all(AiTarget::available))
        assertFalse(targets.single { it.providerId == "deepseek" }.capabilities.structuredJson)
        assertTrue(
            targets.filterNot { it.providerId == "deepseek" }
                .all { target -> target.capabilities.structuredJson },
        )

        execution.close()
    }

    @Test
    fun namespaceOwnershipDoesNotPretendMissingProfilesExist() {
        val backend = OnlineAiBackend(fixture().registry, RecordingExecution())
        val targetId = AiTargetIds.profile(profile().profileId)

        assertTrue(backend.ownsTarget(targetId))
        assertFalse(backend.ownsTarget("local:model"))
        assertEquals(profile().profileId, AiTargetIds.requireProfileId(targetId))
        assertThrows(AiTargetUnavailableException::class.java) {
            backend.createSession(AiBackendSessionRequest(targetId))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiTargetIds.profile("x".repeat(129))
        }
    }

    @Test
    fun boundCredentialAccessRejectsAProfileChangedAfterSessionCreation() {
        val fixture = fixture()
        val original = profile()
        fixture.registry.save(original, replacement("first-secret"))
        val execution = RecordingExecution(readCredentialDuringCreation = false)
        val backend = OnlineAiBackend(fixture.registry, execution)
        backend.createSession(AiBackendSessionRequest(AiTargetIds.profile(original.profileId)))

        fixture.registry.save(
            original.copy(baseUrl = "https://other.example.com/v1"),
            replacement("second-secret"),
        )

        var callbackEntered = false
        assertThrows(OnlineAiProfileChangedException::class.java) {
            requireNotNull(execution.credentialAccess).withCredential {
                callbackEntered = true
            }
        }
        assertFalse(callbackEntered)
    }

    private fun profile() = OnlineAiProfile(
        profileId = "550e8400-e29b-41d4-a716-446655440000",
        displayName = "Work",
        provider = OnlineAiProvider.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com/v1",
        modelId = "model-a",
    )

    private fun providerProfile(provider: OnlineAiProvider, index: Int): OnlineAiProfile {
        val template = OnlineAiProviderCatalog.templateFor(provider)
        return OnlineAiProfile(
            profileId = "00000000-0000-4000-8000-${(index + 1).toString().padStart(12, '0')}",
            displayName = template.displayName,
            provider = provider,
            baseUrl = template.defaultBaseUrl ?: "https://custom.example.com/v1",
            modelId = "model-${index + 1}",
        )
    }

    private fun replacement(value: String) =
        OnlineAiCredentialUpdate.Replace.takingOwnership(value.toCharArray())

    private fun fixture(): Fixture {
        val credentials = FakeCredentialStore()
        return Fixture(
            registry = OnlineAiProfileRegistry(
                repository = OnlineAiProfileRepository(MemoryProfileStorage()),
                credentialStore = credentials,
            ),
        )
    }

    private data class Fixture(val registry: OnlineAiProfileRegistry)

    private class RecordingExecution(
        private val readCredentialDuringCreation: Boolean = true,
    ) : OnlineAiExecution {
        private val targetCapabilities = AiTargetCapabilities(
            streaming = true,
            persistentSession = false,
            structuredJson = true,
            usage = true,
            reasoning = false,
            tools = false,
        )
        private val targetLimits = AiTargetLimits(
            maximumContextBytes = null,
            maximumOutputBytes = null,
            maximumOutputTokens = 8192,
            maximumContextTokens = 32_768,
        )
        override val available = true
        var credential: String? = null
        var profile: OnlineAiProfile? = null
        var credentialAccess: OnlineAiProfileCredentialAccess? = null

        override fun supports(profile: OnlineAiProfile): Boolean = true

        override fun capabilities(profile: OnlineAiProfile): AiTargetCapabilities = targetCapabilities

        override fun limits(profile: OnlineAiProfile): AiTargetLimits = targetLimits

        override fun createSession(
            target: AiTarget,
            profile: OnlineAiProfile,
            credentialAccess: OnlineAiProfileCredentialAccess,
        ): AiBackendSession {
            this.profile = profile
            this.credentialAccess = credentialAccess
            if (readCredentialDuringCreation) {
                credential = credentialAccess.withCredential { bytes -> bytes.toString(Charsets.UTF_8) }
            }
            return FakeSession(target)
        }
    }

    private class FakeSession(override val target: AiTarget) : AiBackendSession {
        override fun stream(request: GenerationRequest, listener: GenerationListener) = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class MemoryProfileStorage : OnlineAiProfileDocumentStorage {
        private var document: ByteArray? = null

        override fun <T> withExclusiveAccess(action: (OnlineAiProfileDocumentAccess) -> T): T = synchronized(this) {
            action(
                object : OnlineAiProfileDocumentAccess {
                    override fun read(): ByteArray? = document?.copyOf()
                    override fun write(encodedDocument: ByteArray) {
                        document = encodedDocument.copyOf()
                    }
                },
            )
        }
    }

    private class FakeCredentialStore : AiCredentialStore {
        private val credentials = mutableMapOf<String, CharArray>()

        override fun put(profileId: String, credential: CharArray) {
            try {
                credentials.put(profileId, credential.copyOf())?.fill('\u0000')
            } finally {
                credential.fill('\u0000')
            }
        }

        override fun isConfigured(profileId: String) = credentials[profileId]?.isNotEmpty() == true

        override fun <T> withCredential(profileId: String, action: (ByteArray) -> T): T {
            val bytes = requireNotNull(credentials[profileId]).concatToString().toByteArray()
            return try {
                action(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        override fun clear(profileId: String): Boolean = credentials.remove(profileId)?.let { removed ->
            removed.fill('\u0000')
            true
        } ?: false
    }
}
