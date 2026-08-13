package io.github.supermonster003.autojs6.plugin.ai.text.provider

import io.github.supermonster003.autojs6.plugin.ai.text.AiTextPlugin
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelCatalogDocument
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelCatalogEntry
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelImportPolicy
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.text.api.AiModelListRequest
import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPagerTest {
    private val entryA = entry("11", "A")
    private val entryB = entry("22", "B")
    private val entryC = entry("33", "C")

    @Test
    fun allPublicModelsAreReturnedAcrossDeterministicBoundedPages() {
        val pager = pager(ModelCatalogDocument(1L, entryB.modelId, listOf(entryC, entryA, entryB)))

        val first = pager.page(request(pageSize = 2))
        val second = pager.page(request(pageSize = 2, pageToken = first.nextPageToken))

        assertEquals(listOf(entryA.modelId, entryB.modelId), first.models.map { it.modelId })
        assertEquals(listOf(entryC.modelId), second.models.map { it.modelId })
        assertEquals(first.listingGeneration, second.listingGeneration)
        assertTrue(requireNotNull(first.nextPageToken).toByteArray().size < 128)
        assertNull(second.nextPageToken)
        (first.models + second.models).forEach { model ->
            assertEquals(listOf(AiTextCapabilityId.STREAMING), model.capabilityIds)
            assertEquals(AiTextPlugin.MAXIMUM_CONTEXT_BYTES, model.maximumContextBytes)
            assertEquals(AiTextPlugin.MAXIMUM_OUTPUT_BYTES, model.maximumOutputBytes)
        }
    }

    @Test
    fun freshListingsUseUniqueSingleUseOpaqueTokens() {
        val tokens = ArrayDeque(listOf("aa".repeat(24), "bb".repeat(24)))
        val pager = ModelPager(
            catalogSnapshot = { catalog(entryA, entryB) },
            tokenSource = tokens::removeFirst,
        )

        val firstRun = pager.page(request(pageSize = 1))
        val secondRun = pager.page(request(pageSize = 1))

        assertEquals(firstRun.listingGeneration, secondRun.listingGeneration)
        assertNotEquals(firstRun.nextPageToken, secondRun.nextPageToken)
        assertEquals(entryB.modelId, pager.page(request(1, firstRun.nextPageToken)).models.single().modelId)
        assertEquals(entryB.modelId, pager.page(request(1, secondRun.nextPageToken)).models.single().modelId)
        assertThrows(InvalidModelListRequestException::class.java) {
            pager.page(request(1, firstRun.nextPageToken))
        }
    }

    @Test
    fun continuationBindsGenerationPageSizeAndAlignedBoundedOffset() {
        var snapshot = catalog(entryA, entryB, entryC)
        val pager = ModelPager(
            catalogSnapshot = { snapshot },
            tokenSource = { "aa".repeat(24) },
        )
        val first = pager.page(request(pageSize = 1))
        val token = requireNotNull(first.nextPageToken)

        assertThrows(IllegalArgumentException::class.java) { pager.page(request(2, token)) }
        assertThrows(InvalidModelListRequestException::class.java) { pager.page(request(1, "malformed")) }

        val fresh = pager.page(request(pageSize = 1))
        snapshot = catalog(entryA.copy(displayName = "renamed"), entryB, entryC)
        assertThrows(IllegalArgumentException::class.java) { pager.page(request(1, fresh.nextPageToken)) }
        assertNotEquals(first.listingGeneration, pager.page(request(1)).listingGeneration)
    }

    @Test
    fun oneCoherentCatalogSnapshotIsReadPerPageAndFailuresStayDistinctFromBadTokens() {
        var reads = 0
        val pager = ModelPager(
            catalogSnapshot = {
                reads += 1
                catalog(entryA, entryB)
            },
            tokenSource = { "aa".repeat(24) },
        )
        val first = pager.page(request(1))
        assertEquals(1, reads)
        pager.page(request(1, first.nextPageToken))
        assertEquals(2, reads)

        val unavailable = ModelPager(catalogSnapshot = { error("corrupt catalog") })
        assertThrows(ModelListingUnavailableException::class.java) { unavailable.page(request(1)) }
    }

    @Test
    fun emptyCatalogHasAStableNonEmptyGenerationAndNoToken() {
        val page = pager(ModelCatalogDocument(1L, null, emptyList())).page(request(100))

        assertTrue(page.listingGeneration.isNotEmpty())
        assertTrue(page.models.isEmpty())
        assertNull(page.nextPageToken)
    }

    @Test
    fun issuedTokenLedgerEvictsTheOldestEntryAndCloseClearsTheRemainder() {
        val tokens = ArrayDeque(listOf("aa".repeat(24), "bb".repeat(24), "cc".repeat(24)))
        val pager = ModelPager(
            catalogSnapshot = { catalog(entryA, entryB) },
            tokenSource = tokens::removeFirst,
            maximumIssuedTokens = 2,
        )
        val oldest = pager.page(request(1)).nextPageToken
        val retained = pager.page(request(1)).nextPageToken
        val newest = pager.page(request(1)).nextPageToken

        assertThrows(InvalidModelListRequestException::class.java) { pager.page(request(1, oldest)) }
        assertEquals(entryB.modelId, pager.page(request(1, retained)).models.single().modelId)
        pager.close()
        assertThrows(ModelListingUnavailableException::class.java) { pager.page(request(1, newest)) }
    }

    private fun pager(document: ModelCatalogDocument) = ModelPager(
        catalogSnapshot = { document },
        tokenSource = { "aa".repeat(24) },
    )

    private fun request(pageSize: Int, pageToken: String? = null) = AiModelListRequest(
        protocolVersion = AiProtocolVersion(1, 0),
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
}
