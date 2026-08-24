package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ImportedModel
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogDocument
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogEntry
import org.autojs.plugin.ai.provider.api.AiBackendProfileInfo
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderBackendUnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiteRtLocalBackendTest {
    private val modelA = model("11", "A")
    private val modelB = model("22", "B")
    private val profiles = listOf(
        AiBackendProfileInfo(
            profileId = AiProviderBackendProfile.CPU,
            availability = AiProviderBackendAvailability.AVAILABLE,
        ),
        AiBackendProfileInfo(
            profileId = AiProviderBackendProfile.GPU,
            availability = AiProviderBackendAvailability.UNAVAILABLE,
            unavailableReason = AiProviderBackendUnavailableReason.OPENCL_LIBRARY_UNAVAILABLE,
        ),
    )

    @Test
    fun catalogMapsImportedModelsToStableLocalTargets() {
        val backend = backend()

        val catalog = backend.catalog()

        assertTrue(catalog.generation.isNotBlank())
        assertEquals(AiTargetIds.local(modelB.modelId), catalog.defaultTargetId)
        assertEquals(listOf(modelA.modelId, modelB.modelId), catalog.targets.map(AiTarget::modelId))
        catalog.targets.forEach { target ->
            assertEquals(LiteRtLocalBackend.BACKEND_ID, target.backendId)
            assertEquals(ThreeStoneAiPlugin.PROVIDER_ID, target.providerId)
            assertEquals(AiTargetLocality.LOCAL, target.locality)
            assertTrue(target.configured)
            assertTrue(target.available)
            assertTrue(target.capabilities.streaming)
            assertTrue(target.capabilities.persistentSession)
            assertTrue(target.capabilities.structuredJson)
            assertTrue(target.capabilities.usage)
            assertFalse(target.capabilities.reasoning)
            assertFalse(target.capabilities.tools)
            assertEquals(ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES, target.limits.maximumContextBytes)
            assertEquals(ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES, target.limits.maximumOutputBytes)
            assertEquals(listOf("cpu", "gpu"), target.executionProfiles.map(AiExecutionProfile::profileId))
            assertTrue(target.executionProfiles.first().available)
            assertFalse(target.executionProfiles.last().available)
        }
    }

    @Test
    fun sessionCreationResolvesTargetModelAndExecutionProfile() {
        var capturedModel: ImportedModel? = null
        var capturedProfile: LiteRtLmBackendProfile? = null
        val backend = backend { target, model, profile ->
            capturedModel = model
            capturedProfile = profile
            RecordingSession(target)
        }

        val session = backend.createSession(
            AiBackendSessionRequest(
                targetId = AiTargetIds.local(modelA.modelId),
            ),
        )

        assertEquals(modelA, capturedModel)
        assertEquals(LiteRtLmBackendProfile.CPU, capturedProfile)
        assertEquals(AiTargetIds.local(modelA.modelId), session.target.targetId)
        assertEquals(modelA.displayName, session.target.displayName)
        assertEquals(session.target.capabilities, backend.capabilities(session.target.targetId))
    }

    @Test
    fun unknownOrNonLocalTargetFailsBeforeSessionCreation() {
        val backend = backend()
        val unknown = AiTargetIds.local("litertlm." + "ff".repeat(16))

        assertThrows(AiTargetUnavailableException::class.java) {
            backend.createSession(AiBackendSessionRequest(unknown, AiProviderBackendProfile.CPU))
        }
        assertThrows(IllegalArgumentException::class.java) {
            backend.createSession(AiBackendSessionRequest("profile:remote", AiProviderBackendProfile.CPU))
        }
    }

    private fun backend(
        sessionFactory: (AiTarget, ImportedModel, LiteRtLmBackendProfile) -> AiBackendSession =
            { target, _, _ -> RecordingSession(target) },
    ) = LiteRtLocalBackend(
        catalogSnapshot = {
            ModelCatalogDocument(
                revision = 1L,
                selectedModelId = modelB.modelId,
                entries = listOf(modelB.toEntry(), modelA.toEntry()),
            )
        },
        findModelById = { modelId -> listOf(modelA, modelB).singleOrNull { it.modelId == modelId } },
        backendProfiles = { profiles },
        requireBackendProfile = LiteRtLmBackendProfile::fromProtocolId,
        sessionFactory = sessionFactory,
    )

    private fun model(byte: String, displayName: String): ImportedModel {
        val digest = byte.repeat(32)
        return ImportedModel(
            modelId = "litertlm.${digest.take(32)}",
            displayName = displayName,
            file = File("model-$digest.litertlm"),
            sizeBytes = 8L,
            sha256 = digest,
            importedAtMillis = 1L,
        )
    }

    private fun ImportedModel.toEntry() = ModelCatalogEntry(
        modelId = modelId,
        displayName = displayName,
        fileName = file.name,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        importedAtMillis = importedAtMillis,
        healthStatus = healthStatus,
    )

    private class RecordingSession(
        override val target: AiTarget,
    ) : AiBackendSession {
        override fun stream(request: GenerationRequest, listener: GenerationListener) = Unit
        override fun cancel() = Unit
        override fun close() = Unit
    }
}
