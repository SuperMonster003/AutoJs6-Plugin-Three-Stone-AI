package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LiteRtLocalCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogDocument
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogEntry
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportPolicy
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.provider.api.AiBackendProfileInfo
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiTargetAvailability
import org.autojs.plugin.ai.provider.api.AiTargetControlId
import org.autojs.plugin.ai.provider.api.AiTargetListRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetPagerTest {
    private val entryA = entry("11", "A")
    private val entryB = entry("22", "B")
    private val entryC = entry("33", "C")

    @Test
    fun allTargetsAreReturnedAcrossDeterministicBoundedPages() {
        val pager = pager(ModelCatalogDocument(1L, entryB.modelId, listOf(entryC, entryA, entryB)))

        val first = pager.page(request(pageSize = 2))
        val second = pager.page(request(pageSize = 2, pageToken = first.nextPageToken))

        assertEquals(listOf(entryA.modelId, entryB.modelId), first.targets.map { it.modelId })
        assertEquals(listOf(entryC.modelId), second.targets.map { it.modelId })
        assertEquals(first.catalogGeneration, second.catalogGeneration)
        assertEquals(
            listOf(entryB.modelId),
            (first.targets + second.targets).filter { it.isDefault }.map { it.modelId },
        )
        assertTrue(requireNotNull(first.nextPageToken).toByteArray().size < 128)
        assertNull(second.nextPageToken)
        (first.targets + second.targets).forEach { target ->
            assertTrue(target.targetId.startsWith("local:"))
            assertEquals(ThreeStoneAiPlugin.PROVIDER_ID, target.providerId)
            assertEquals(AiDataLocality.ON_DEVICE, target.locality)
            assertEquals(AiCredentialMode.NONE, target.credentialMode)
            assertEquals(AiTargetAvailability.AVAILABLE, target.availability)
            assertTrue(target.configured)
            assertEquals(
                listOf(
                    AiProviderCapabilityId.STREAMING,
                    AiProviderCapabilityId.STRUCTURED_JSON,
                    AiProviderCapabilityId.USAGE,
                    AiProviderCapabilityId.PERSISTENT_SESSION,
                ),
                target.capabilityIds,
            )
            assertEquals(ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES, target.maximumContextBytes)
            assertEquals(ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES, target.maximumOutputBytes)
            assertEquals(listOf(AiProviderBackendProfile.CPU), target.backendProfiles.map { it.profileId })
            assertTrue(AiTargetControlId.BACKEND_PROFILE in target.supportedControls)
            assertTrue(AiTargetControlId.RESPONSE_JSON_SCHEMA in target.supportedControls)
            assertTrue(target.declaredHttpsOrigins.isEmpty())
        }
    }

    @Test
    fun remoteTargetPreservesProfileProviderOriginAvailabilityAndPluginCredentialBoundary() {
        val page = TargetPager(
            catalogSnapshot = {
                AiTargetCatalog(
                    generation = "unified-generation",
                    defaultTargetId = remoteTarget.targetId,
                    targets = listOf(remoteTarget),
                )
            },
        ).page(request(10))

        val target = page.targets.single()
        assertEquals("profile:profile-a", target.targetId)
        assertEquals("openai-compatible", target.providerId)
        assertEquals("profile-a", target.profileId)
        assertEquals("remote/model", target.modelId)
        assertEquals(AiDataLocality.REMOTE, target.locality)
        assertEquals(AiCredentialMode.PLUGIN_MANAGED, target.credentialMode)
        assertEquals(AiTargetAvailability.AVAILABLE, target.availability)
        assertTrue(target.configured)
        assertTrue(target.isDefault)
        assertEquals(listOf("https://api.example.com"), target.declaredHttpsOrigins)
        assertFalse(AiTargetControlId.BACKEND_PROFILE in target.supportedControls)
        assertTrue(AiTargetControlId.TOP_K in target.supportedControls)
        assertTrue(target.backendProfiles.isEmpty())
        assertFalse(target.javaClass.declaredFields.any { field ->
            field.name.contains("key", ignoreCase = true) ||
                field.name.contains("credentialBytes", ignoreCase = true) ||
                field.name.contains("authorization", ignoreCase = true)
        })
    }

    @Test
    fun nativeOpenAiTargetDoesNotDeclareAnIgnoredTopKControl() {
        val nativeOpenAi = remoteTarget.copy(providerId = "openai")
        val target = TargetPager(
            catalogSnapshot = {
                AiTargetCatalog(
                    generation = "openai-generation",
                    defaultTargetId = nativeOpenAi.targetId,
                    targets = listOf(nativeOpenAi),
                )
            },
        ).page(request(10)).targets.single()

        assertFalse(AiTargetControlId.TOP_K in target.supportedControls)
        assertTrue(AiTargetControlId.TEMPERATURE in target.supportedControls)
        assertTrue(AiTargetControlId.TOP_P in target.supportedControls)
    }

    @Test
    fun freshListingsUseUniqueSingleUseOpaqueTokens() {
        val tokens = ArrayDeque(listOf("aa".repeat(24), "bb".repeat(24)))
        val pager = TargetPager(
            catalogSnapshot = { localCatalog(catalog(entryA, entryB)) },
            tokenSource = tokens::removeFirst,
        )

        val firstRun = pager.page(request(pageSize = 1))
        val secondRun = pager.page(request(pageSize = 1))

        assertEquals(firstRun.catalogGeneration, secondRun.catalogGeneration)
        assertNotEquals(firstRun.nextPageToken, secondRun.nextPageToken)
        assertEquals(entryB.modelId, pager.page(request(1, firstRun.nextPageToken)).targets.single().modelId)
        assertEquals(entryB.modelId, pager.page(request(1, secondRun.nextPageToken)).targets.single().modelId)
        assertThrows(InvalidTargetListRequestException::class.java) {
            pager.page(request(1, firstRun.nextPageToken))
        }
    }

    @Test
    fun continuationBindsGenerationPageSizeAndAlignedBoundedOffset() {
        var snapshot = catalog(entryA, entryB, entryC)
        val pager = TargetPager(
            catalogSnapshot = { localCatalog(snapshot) },
            tokenSource = { "aa".repeat(24) },
        )
        val first = pager.page(request(pageSize = 1))
        val token = requireNotNull(first.nextPageToken)

        assertThrows(IllegalArgumentException::class.java) { pager.page(request(2, token)) }
        assertThrows(InvalidTargetListRequestException::class.java) { pager.page(request(1, "malformed")) }

        val fresh = pager.page(request(pageSize = 1))
        snapshot = catalog(entryA.copy(displayName = "renamed"), entryB, entryC)
        assertThrows(IllegalArgumentException::class.java) { pager.page(request(1, fresh.nextPageToken)) }
        val renamedPage = pager.page(request(1))
        assertNotEquals(first.catalogGeneration, renamedPage.catalogGeneration)
        assertEquals("renamed", renamedPage.targets.single().displayName)
    }

    @Test
    fun oneCoherentCatalogSnapshotIsReadPerPageAndFailuresStayDistinctFromBadTokens() {
        var reads = 0
        val pager = TargetPager(
            catalogSnapshot = {
                reads += 1
                localCatalog(catalog(entryA, entryB))
            },
            tokenSource = { "aa".repeat(24) },
        )
        val first = pager.page(request(1))
        assertEquals(1, reads)
        pager.page(request(1, first.nextPageToken))
        assertEquals(2, reads)

        val unavailable = TargetPager(catalogSnapshot = { error("corrupt catalog") })
        assertThrows(TargetCatalogUnavailableException::class.java) { unavailable.page(request(1)) }
    }

    @Test
    fun emptyCatalogHasAStableNonEmptyGenerationAndNoToken() {
        val page = pager(ModelCatalogDocument(1L, null, emptyList())).page(request(100))

        assertTrue(page.catalogGeneration.isNotEmpty())
        assertTrue(page.targets.isEmpty())
        assertNull(page.nextPageToken)
    }

    @Test
    fun issuedTokenLedgerEvictsTheOldestEntryAndCloseClearsTheRemainder() {
        val tokens = ArrayDeque(listOf("aa".repeat(24), "bb".repeat(24), "cc".repeat(24)))
        val pager = TargetPager(
            catalogSnapshot = { localCatalog(catalog(entryA, entryB)) },
            tokenSource = tokens::removeFirst,
            maximumIssuedTokens = 2,
        )
        val oldest = pager.page(request(1)).nextPageToken
        val retained = pager.page(request(1)).nextPageToken
        val newest = pager.page(request(1)).nextPageToken

        assertThrows(InvalidTargetListRequestException::class.java) { pager.page(request(1, oldest)) }
        assertEquals(entryB.modelId, pager.page(request(1, retained)).targets.single().modelId)
        pager.close()
        assertThrows(TargetCatalogUnavailableException::class.java) { pager.page(request(1, newest)) }
    }

    private fun pager(document: ModelCatalogDocument) = TargetPager(
        catalogSnapshot = { localCatalog(document) },
        tokenSource = { "aa".repeat(24) },
    )

    private fun localCatalog(document: ModelCatalogDocument) = LiteRtLocalCatalog.create(
        document = document,
        backendProfiles = listOf(
            AiBackendProfileInfo(
                profileId = AiProviderBackendProfile.CPU,
                availability = AiProviderBackendAvailability.AVAILABLE,
            ),
        ),
    )

    private fun request(pageSize: Int, pageToken: String? = null) = AiTargetListRequest(
        protocolVersion = AiProviderProtocol.PROTOCOL_V2,
        pageSize = pageSize,
        pageToken = pageToken,
    )

    private fun catalog(vararg entries: ModelCatalogEntry) =
        ModelCatalogDocument(1L, entries.firstOrNull()?.modelId, entries.toList())

    private fun entry(byte: String, name: String): ModelCatalogEntry {
        val digest = byte.repeat(32)
        return ModelCatalogEntry(
            modelId = ModelImportPolicy.stableModelId(digest),
            displayName = name,
            fileName = "model-$digest.litertlm",
            sizeBytes = 8L,
            sha256 = digest,
            importedAtMillis = 1L,
        )
    }

    private companion object {
        val remoteTarget = AiTarget(
            targetId = "profile:profile-a",
            backendId = "online",
            providerId = "openai-compatible",
            profileId = "profile-a",
            modelId = "remote/model",
            displayName = "Remote target",
            locality = AiTargetLocality.REMOTE,
            credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
            declaredHttpsOrigins = listOf("https://api.example.com"),
            configured = true,
            available = true,
            capabilities = AiTargetCapabilities(
                streaming = true,
                persistentSession = true,
                structuredJson = true,
                usage = true,
                reasoning = false,
                tools = false,
            ),
            limits = AiTargetLimits(
                maximumContextBytes = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES,
                maximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
            ),
            executionProfiles = emptyList(),
        )
    }
}
