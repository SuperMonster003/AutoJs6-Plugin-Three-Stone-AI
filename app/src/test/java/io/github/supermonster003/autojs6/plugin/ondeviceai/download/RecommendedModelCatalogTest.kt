package io.github.supermonster003.autojs6.plugin.ondeviceai.download

import io.github.supermonster003.autojs6.plugin.ondeviceai.model.ModelImportPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedModelCatalogTest {
    @Test
    fun catalogEntriesAreUniquePinnedHttpsDownloadsWithinTheImportLimit() {
        val models = RecommendedModelCatalog.models

        assertEquals(2, models.size)
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
        assertEquals(3_659_530_240L, e4b.expectedSizeBytes)
        assertEquals(
            "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            e4b.expectedSha256,
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
