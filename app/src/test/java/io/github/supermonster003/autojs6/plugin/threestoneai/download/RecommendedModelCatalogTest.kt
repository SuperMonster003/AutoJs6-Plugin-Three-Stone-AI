package io.github.supermonster003.autojs6.plugin.threestoneai.download

import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelImportPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedModelCatalogTest {
    @Test
    fun catalogEntriesAreUniquePinnedHttpsDownloadsWithinTheImportLimit() {
        val models = RecommendedModelCatalog.models

        assertEquals(8, models.size)
        assertEquals(models.size, models.map { it.id }.toSet().size)
        assertEquals(models.size, models.map { it.fileName.lowercase() }.toSet().size)
        models.forEach { model ->
            ModelDownloadPolicy.requireValidCatalogEntry(model)
            assertTrue(model.downloadUrl.startsWith("https://huggingface.co/"))
            assertTrue("/resolve/main/" !in model.downloadUrl)
            assertTrue(model.expectedSizeBytes <= ModelImportPolicy.MAXIMUM_MODEL_BYTES)
            assertNotNull(RecommendedModelCatalog.find(model.id))
        }
    }

    @Test
    fun pinnedMetadataMatchesTheReviewedOfficialFiles() {
        val e2b = checkNotNull(RecommendedModelCatalog.find("gemma-4-e2b-it"))
        assertEquals(2_583_085_056L, e2b.expectedSizeBytes)
        assertEquals(
            "ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42",
            e2b.expectedSha256,
        )

        val e4b = checkNotNull(RecommendedModelCatalog.find("gemma-4-e4b-it"))
        assertEquals(3_654_467_584L, e4b.expectedSizeBytes)
        assertEquals(
            "f335f2bfd1b758dc6476db16c0f41854bd6237e2658d604cbe566bcefd00a7bc",
            e4b.expectedSha256,
        )

        val qwen = checkNotNull(RecommendedModelCatalog.find("qwen2-5-1-5b-instruct"))
        assertEquals(1_597_931_520L, qwen.expectedSizeBytes)
        assertEquals(
            "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
            qwen.expectedSha256,
        )

        val deepSeek = checkNotNull(
            RecommendedModelCatalog.find("deepseek-r1-distill-qwen-1-5b"),
        )
        assertEquals(1_833_451_520L, deepSeek.expectedSizeBytes)
        assertEquals(
            "69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295",
            deepSeek.expectedSha256,
        )

        val qwen3b = checkNotNull(RecommendedModelCatalog.find("qwen2-5-3b-instruct"))
        assertEquals(1_751_794_176L, qwen3b.expectedSizeBytes)
        assertEquals(
            "2e49db88da7c26bcb7ea7abf12d21b4a0215ad0f37468d58b32db5de42078e09",
            qwen3b.expectedSha256,
        )

        val coder = checkNotNull(RecommendedModelCatalog.find("qwen2-5-coder-3b-instruct"))
        assertEquals(3_433_083_824L, coder.expectedSizeBytes)
        assertEquals(
            "78d23da074383f52f852b945b8090870e6c9dded02a842f535ee3ccb9e2874f3",
            coder.expectedSha256,
        )

        val vision = checkNotNull(RecommendedModelCatalog.find("qwen2-vl-2b-instruct"))
        assertEquals(1_784_096_288L, vision.expectedSizeBytes)
        assertEquals(
            "62db3d9f6ce18a8df56a0b45638518306c4799f025dd7e1c54b35cce8f965d13",
            vision.expectedSha256,
        )

        val phi = checkNotNull(RecommendedModelCatalog.find("phi-4-mini-reasoning"))
        assertEquals(2_783_974_384L, phi.expectedSizeBytes)
        assertEquals(
            "d3938899f3b2d7ad3e86bb1f9c361cf4a9a61f3b79c609b4cd2b6981d00e40fa",
            phi.expectedSha256,
        )
    }

    @Test
    fun availableCatalogIncludesEveryReviewedGalleryEntryAndMarksVerifiedDownloads() {
        val available = AvailableLiteRtModelCatalog.models

        assertEquals(15, available.size)
        assertEquals(available.size, available.map { it.id }.toSet().size)
        assertEquals(8, available.count { it.verifiedDownload != null })
        assertTrue(available.all { it.commitHash.matches(Regex("^[0-9a-f]{40}$")) })
        assertTrue(available.all { it.sourceUrl.startsWith("https://huggingface.co/") })
        RecommendedModelCatalog.models.forEach { downloadable ->
            assertNotNull(AvailableLiteRtModelCatalog.find(downloadable.id))
        }
    }

    @Test
    fun importedCatalogModelsResolveToFriendlyTitlesWithoutGuessingAmbiguousFileNames() {
        assertEquals(
            "Gemma 4 E2B IT",
            AvailableLiteRtModelCatalog.displayNameForImportedModel(
                "gemma-4-E2B-it.litertlm",
                "litertlm.ab7838cdfc8f77e54d8ca45eadceb204",
            ),
        )
        assertEquals(
            "Qwen2.5 3B Instruct",
            AvailableLiteRtModelCatalog.displayNameForImportedModel(
                "model.litertlm",
                "litertlm.2e49db88da7c26bcb7ea7abf12d21b4a",
            ),
        )
        assertEquals(
            "custom-name.litertlm",
            AvailableLiteRtModelCatalog.displayNameForImportedModel(
                "custom-name.litertlm",
                "litertlm.00000000000000000000000000000000",
            ),
        )
    }

    @Test
    fun mutableCleartextAndUnapprovedCatalogUrlsAreRejected() {
        val model = RecommendedModelCatalog.models.first()
        listOf(
            model.copy(downloadUrl = model.downloadUrl.replace("https://", "http://")),
            model.copy(
                downloadUrl = model.downloadUrl.replace(
                    Regex("/resolve/[0-9a-f]{40}/"),
                    "/resolve/main/",
                ),
            ),
            model.copy(downloadUrl = model.downloadUrl.replace("huggingface.co", "example.com")),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                ModelDownloadPolicy.requireValidCatalogEntry(invalid)
            }
        }
    }

    @Test
    fun redirectsStayOnApprovedHttpsHostsAndPorts() {
        listOf(
            "https://huggingface.co/file",
            "https://cdn-lfs.huggingface.co/file",
            "https://us.aws.cdn.hf.co/file?signature=value",
            "https://cas-bridge.xethub.hf.co/file",
        ).forEach { target ->
            ModelDownloadPolicy.requireAllowedRedirectTarget(java.net.URI(target))
        }
        listOf(
            "http://huggingface.co/file",
            "https://example.com/file",
            "https://huggingface.co:8443/file",
            "https://user@huggingface.co/file",
        ).forEach { target ->
            assertThrows(IllegalArgumentException::class.java) {
                ModelDownloadPolicy.requireAllowedRedirectTarget(java.net.URI(target))
            }
        }
    }
}
