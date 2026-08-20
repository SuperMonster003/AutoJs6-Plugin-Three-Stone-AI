package io.github.supermonster003.autojs6.plugin.ondeviceai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    private val digestA = "11".repeat(32)
    private val digestB = "22".repeat(32)
    private val entryA = entry(digestA, "A")
    private val entryB = entry(digestB, "B")

    @Test
    fun schemaTwoRoundTripsStrictlyAndLegacySchemaOneDecodesOnlyAsMigrationSource() {
        val document = ModelCatalogDocument(7L, entryA.modelId, listOf(entryB, entryA))
        val decoded = ModelCatalogCodec.decode(ModelCatalogCodec.encode(document))
        assertEquals(listOf(entryA.modelId, entryB.modelId), decoded.entries.map { it.modelId })
        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogCodec.decode(ModelCatalogCodec.encode(document).toString(Charsets.UTF_8)
                .replace("\"schema\":2", "\"schema\":1").toByteArray())
        }
        assertEquals(entryA, ModelCatalogCodec.decodeLegacyCurrent(legacy(entryA)))
    }

    @Test
    fun strictCodecRejectsUnknownDuplicateMalformedAndOversizedDocuments() {
        val valid = ModelCatalogCodec.encode(ModelCatalogDocument(1L, null, listOf(entryA)))
            .toString(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogCodec.decode(valid.dropLast(1).plus(",\"extra\":1}").toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogCodec.decode(valid.replace("\"revision\":1", "\"revision\":1,\"revision\":1").toByteArray())
        }
        assertThrows(Exception::class.java) {
            ModelCatalogCodec.decode(byteArrayOf(0xC3.toByte(), 0x28))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogCodec.decode(ByteArray(ModelCatalogCodec.MAXIMUM_CATALOG_BYTES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogPolicy.normalize(
                ModelCatalogDocument(
                    revision = 1L,
                    selectedModelId = null,
                    entries = List(ModelCatalogPolicy.MAXIMUM_ENTRIES + 1) { index ->
                        entry(index.toString(16).padStart(2, '0') + "00".repeat(31), "model-$index")
                    },
                ),
            )
        }
    }

    @Test
    fun duplicateDigestSelectsExistingWithoutChangingMetadataOrListingGeneration() {
        val original = ModelCatalogDocument(3L, null, listOf(entryA))
        val generation = listingGeneration(original)
        val duplicate = entryA.copy(displayName = "replacement", importedAtMillis = 999L)
        val update = ModelCatalogPolicy.integrateImport(original, duplicate)
        assertTrue(update.changed)
        assertEquals(entryA, update.model)
        assertEquals(listOf(entryA), update.document.entries)
        assertEquals(4L, update.document.revision)
        assertEquals(generation, listingGeneration(update.document))

        val noOp = ModelCatalogPolicy.integrateImport(update.document, duplicate)
        assertFalse(noOp.changed)
        assertEquals(4L, noOp.document.revision)
    }

    @Test
    fun selectionAndRevisionDoNotAffectGenerationButPublicFieldsDo() {
        val first = ModelCatalogDocument(1L, entryA.modelId, listOf(entryA, entryB))
        val selectionOnly = first.copy(revision = 9L, selectedModelId = entryB.modelId)
        assertEquals(listingGeneration(first), listingGeneration(selectionOnly))
        assertNotEquals(
            listingGeneration(first),
            listingGeneration(first.copy(entries = listOf(entryA.copy(displayName = "renamed"), entryB))),
        )
        assertNotEquals(listingGeneration(first), listingGeneration(first, listOf("streaming", "reasoning")))
        assertNotEquals(listingGeneration(first), listingGeneration(first, maximumContextBytes = 1L))
        assertNotEquals(listingGeneration(first), listingGeneration(first, maximumOutputBytes = 1L))
    }

    @Test
    fun selectingExistingModelChangesOnlyPointerAndRevisionWhileReselectIsANoOp() {
        val original = ModelCatalogPolicy.normalize(
            ModelCatalogDocument(7L, entryA.modelId, listOf(entryB, entryA)),
        )
        val originalGeneration = listingGeneration(original)

        val selected = ModelCatalogPolicy.select(original, entryB.modelId)

        assertTrue(selected.changed)
        assertEquals(entryB, selected.model)
        assertEquals(entryB.modelId, selected.document.selectedModelId)
        assertEquals(8L, selected.document.revision)
        assertEquals(original.entries, selected.document.entries)
        assertEquals(originalGeneration, listingGeneration(selected.document))

        val repeated = ModelCatalogPolicy.select(selected.document, entryB.modelId)
        assertFalse(repeated.changed)
        assertEquals(entryB, repeated.model)
        assertEquals(selected.document, repeated.document)
    }

    @Test
    fun selectingUnknownModelFailsWithoutChangingTheInputCatalog() {
        val original = ModelCatalogPolicy.normalize(
            ModelCatalogDocument(7L, entryA.modelId, listOf(entryA, entryB)),
        )
        val originalBytes = ModelCatalogCodec.encode(original)

        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogPolicy.select(original, "litertlm.${"ff".repeat(16)}")
        }

        assertTrue(originalBytes.contentEquals(ModelCatalogCodec.encode(original)))
        assertEquals(entryA.modelId, original.selectedModelId)
        assertEquals(7L, original.revision)
    }

    @Test
    fun deletingUnselectedModelRemovesOnlyThatGenerationAndAdvancesRevision() {
        val original = ModelCatalogPolicy.normalize(
            ModelCatalogDocument(7L, entryA.modelId, listOf(entryA, entryB)),
        )
        val originalGeneration = listingGeneration(original)

        val deletion = ModelCatalogPolicy.deleteUnselected(original, entryB.modelId)

        assertEquals(entryB, deletion.model)
        assertEquals(entryA.modelId, deletion.document.selectedModelId)
        assertEquals(listOf(entryA), deletion.document.entries)
        assertEquals(8L, deletion.document.revision)
        assertNotEquals(originalGeneration, listingGeneration(deletion.document))
        assertEquals(listOf(entryA, entryB), original.entries)
        assertEquals(7L, original.revision)
    }

    @Test
    fun deletingSelectedOrUnknownModelFailsWithoutChangingTheCatalog() {
        val original = ModelCatalogPolicy.normalize(
            ModelCatalogDocument(7L, entryA.modelId, listOf(entryA, entryB)),
        )
        val originalBytes = ModelCatalogCodec.encode(original)

        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogPolicy.deleteUnselected(original, entryA.modelId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogPolicy.deleteUnselected(original, "litertlm.${"ff".repeat(16)}")
        }

        assertTrue(originalBytes.contentEquals(ModelCatalogCodec.encode(original)))
        assertEquals(entryA.modelId, original.selectedModelId)
        assertEquals(7L, original.revision)
    }

    @Test
    fun catalogWithoutASelectionCanDeleteItsLastModel() {
        val original = ModelCatalogDocument(3L, null, listOf(entryA))

        val deletion = ModelCatalogPolicy.deleteUnselected(original, entryA.modelId)

        assertEquals(entryA, deletion.model)
        assertEquals(null, deletion.document.selectedModelId)
        assertTrue(deletion.document.entries.isEmpty())
        assertEquals(4L, deletion.document.revision)
    }

    @Test
    fun prefixCollisionFailsClosed() {
        val collisionDigest = digestA.take(32) + "33".repeat(16)
        assertThrows(IllegalArgumentException::class.java) {
            ModelCatalogPolicy.integrateImport(
                ModelCatalogDocument(1L, entryA.modelId, listOf(entryA)),
                entry(collisionDigest, "collision"),
            )
        }
    }

    private fun entry(digest: String, name: String) = ModelCatalogEntry(
        modelId = ModelImportPolicy.stableModelId(digest),
        displayName = name,
        fileName = "model-$digest.litertlm",
        sizeBytes = 8L,
        sha256 = digest,
        importedAtMillis = 1L,
    )

    private fun legacy(entry: ModelCatalogEntry): ByteArray =
        """{"schema":1,"modelId":"${entry.modelId}","displayName":"${entry.displayName}","fileName":"${entry.fileName}","sizeBytes":${entry.sizeBytes},"sha256":"${entry.sha256}","importedAtMillis":${entry.importedAtMillis}}"""
            .toByteArray(Charsets.UTF_8)

    private fun listingGeneration(
        document: ModelCatalogDocument,
        capabilityIds: List<String> = listOf("streaming"),
        maximumContextBytes: Long = 256L * 1024L,
        maximumOutputBytes: Long = 64L * 1024L,
    ) = ModelCatalogPolicy.listingGeneration(
        document,
        capabilityIds,
        maximumContextBytes,
        maximumOutputBytes,
    )
}
