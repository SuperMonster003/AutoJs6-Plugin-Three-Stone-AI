package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeAiBackendTest {
    @Test
    fun catalogsMergeInBackendOrderAndKeepFirstDefault() {
        val local = target("local:model", "local", AiTargetLocality.LOCAL)
        val remote = target("profile:remote", "remote", AiTargetLocality.REMOTE)
        val backend = CompositeAiBackend(
            listOf(
                FakeBackend("local", AiTargetCatalog("local-1", local.targetId, listOf(local))),
                FakeBackend("remote", AiTargetCatalog("remote-1", null, listOf(remote))),
            ),
        )

        val catalog = backend.catalog()

        assertEquals(local.targetId, catalog.defaultTargetId)
        assertEquals(listOf(local, remote), catalog.targets)
        assertTrue(catalog.generation.startsWith("catalog."))
        assertTrue(backend.ownsTarget(local.targetId))
        assertTrue(backend.ownsTarget(remote.targetId))
        assertFalse(backend.ownsTarget("profile:missing"))
    }

    @Test
    fun generationChangesWithAChildGenerationAndIsOtherwiseStable() {
        val target = target("local:model", "local", AiTargetLocality.LOCAL)
        fun merged(childGeneration: String) = AiTargetCatalogs.merge(
            listOf("local" to AiTargetCatalog(childGeneration, target.targetId, listOf(target))),
        ).generation

        assertEquals(merged("one"), merged("one"))
        assertNotEquals(merged("one"), merged("two"))
    }

    @Test
    fun preferredBackendDefaultOverridesWithoutReorderingTargets() {
        val local = target("local:model", "local", AiTargetLocality.LOCAL)
        val remote = target("profile:remote", "remote", AiTargetLocality.REMOTE)
        val backend = CompositeAiBackend(
            backends = listOf(
                FakeBackend("local", AiTargetCatalog("local", local.targetId, listOf(local))),
                FakeBackend("remote", AiTargetCatalog("remote", remote.targetId, listOf(remote))),
            ),
            preferredDefaultBackendId = "remote",
        )

        val catalog = backend.catalog()

        assertEquals(remote.targetId, catalog.defaultTargetId)
        assertEquals(listOf(local, remote), catalog.targets)
    }

    @Test
    fun sessionDispatchUsesOwnershipWithoutReadingCatalogs() {
        val local = target("local:model", "local", AiTargetLocality.LOCAL)
        val expected = RecordingSession(local)
        val healthy = FakeBackend(
            backendId = "local",
            targetCatalog = AiTargetCatalog("local-1", local.targetId, listOf(local)),
            session = expected,
        )
        val brokenCatalog = object : AiBackend {
            override val backendId = "remote"
            override fun ownsTarget(targetId: String) = targetId.startsWith("profile:")
            override fun catalog(): AiTargetCatalog = error("corrupt remote profile storage")
            override fun createSession(request: AiBackendSessionRequest): AiBackendSession = error("unused")
        }
        val backend = CompositeAiBackend(listOf(healthy, brokenCatalog))

        assertEquals(expected, backend.createSession(AiBackendSessionRequest(local.targetId)))
        assertEquals(1, healthy.created)
    }

    @Test
    fun sessionFailureNeverFallsBackAcrossLocalAndRemoteBackends() {
        val local = target("local:model", "local", AiTargetLocality.LOCAL)
        val remote = target("profile:remote", "remote", AiTargetLocality.REMOTE)
        val localFailure = IllegalStateException("local marker")
        val remoteFailure = IllegalStateException("remote marker")
        val localBackend = FakeBackend(
            "local",
            AiTargetCatalog("local-1", local.targetId, listOf(local)),
            failure = localFailure,
        )
        val remoteBackend = FakeBackend(
            "remote",
            AiTargetCatalog("remote-1", remote.targetId, listOf(remote)),
            failure = remoteFailure,
        )
        val backend = CompositeAiBackend(listOf(localBackend, remoteBackend))

        assertSame(
            localFailure,
            assertThrows(IllegalStateException::class.java) {
                backend.createSession(AiBackendSessionRequest(local.targetId))
            },
        )
        assertEquals(1, localBackend.created)
        assertEquals(0, remoteBackend.created)

        assertSame(
            remoteFailure,
            assertThrows(IllegalStateException::class.java) {
                backend.createSession(AiBackendSessionRequest(remote.targetId))
            },
        )
        assertEquals(1, localBackend.created)
        assertEquals(1, remoteBackend.created)
    }

    @Test
    fun duplicateTargetsAndAmbiguousOwnershipAreRejected() {
        val target = target("profile:shared", "first", AiTargetLocality.REMOTE)
        val first = FakeBackend("first", AiTargetCatalog("one", null, listOf(target)))
        val second = FakeBackend("second", AiTargetCatalog("two", null, listOf(target.copy(backendId = "second"))))
        val backend = CompositeAiBackend(listOf(first, second))

        assertThrows(IllegalArgumentException::class.java) { backend.catalog() }
        assertThrows(IllegalStateException::class.java) {
            backend.createSession(AiBackendSessionRequest(target.targetId))
        }
    }

    private class FakeBackend(
        override val backendId: String,
        private val targetCatalog: AiTargetCatalog,
        private val session: AiBackendSession? = null,
        private val failure: RuntimeException? = null,
    ) : AiBackend {
        var created = 0
            private set

        override fun ownsTarget(targetId: String) = targetCatalog.targets.any { it.targetId == targetId }
        override fun catalog() = targetCatalog
        override fun createSession(request: AiBackendSessionRequest): AiBackendSession {
            created += 1
            failure?.let { throw it }
            return requireNotNull(session)
        }
    }

    private class RecordingSession(override val target: AiTarget) : AiBackendSession {
        override fun stream(request: GenerationRequest, listener: GenerationListener) = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }

    private fun target(targetId: String, backendId: String, locality: AiTargetLocality): AiTarget = AiTarget(
        targetId = targetId,
        backendId = backendId,
        providerId = if (locality == AiTargetLocality.LOCAL) "local-provider" else "remote-provider",
        profileId = if (locality == AiTargetLocality.LOCAL) null else targetId.removePrefix("profile:"),
        modelId = "model",
        displayName = targetId,
        locality = locality,
        credentialMode = if (locality == AiTargetLocality.LOCAL) {
            AiTargetCredentialMode.NONE
        } else {
            AiTargetCredentialMode.PLUGIN_MANAGED
        },
        declaredHttpsOrigins = if (locality == AiTargetLocality.LOCAL) emptyList() else listOf("https://api.example.com"),
        configured = locality == AiTargetLocality.LOCAL,
        available = locality == AiTargetLocality.LOCAL,
        capabilities = AiTargetCapabilities(false, false, false, false, false, false),
        limits = AiTargetLimits(null, null),
        executionProfiles = emptyList(),
    )
}
