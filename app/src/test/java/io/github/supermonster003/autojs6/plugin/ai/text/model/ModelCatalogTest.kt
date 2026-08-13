package io.github.supermonster003.autojs6.plugin.ai.text.model

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
        val generation = ModelCatalogPolicy.listingGeneration(original)
        val duplicate = entryA.copy(displayName = "replacement", importedAtMillis = 999L)
        val update = ModelCatalogPolicy.integrateImport(original, duplicate)
        assertTrue(update.changed)
        assertEquals(entryA, update.model)
        assertEquals(listOf(entryA), update.document.entries)
        assertEquals(4L, update.document.revision)
        assertEquals(generation, ModelCatalogPolicy.listingGeneration(update.document))

        val noOp = ModelCatalogPolicy.integrateImport(update.document, duplicate)
        assertFalse(noOp.changed)
        assertEquals(4L, noOp.document.revision)
    }

    @Test
    fun selectionAndRevisionDoNotAffectGenerationButPublicFieldsDo() {
        val first = ModelCatalogDocument(1L, entryA.modelId, listOf(entryA, entryB))
        val selectionOnly = first.copy(revision = 9L, selectedModelId = entryB.modelId)
        assertEquals(ModelCatalogPolicy.listingGeneration(first), ModelCatalogPolicy.listingGeneration(selectionOnly))
        assertNotEquals(
            ModelCatalogPolicy.listingGeneration(first),
            ModelCatalogPolicy.listingGeneration(first.copy(entries = listOf(entryA.copy(displayName = "renamed"), entryB))),
        )
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
}
