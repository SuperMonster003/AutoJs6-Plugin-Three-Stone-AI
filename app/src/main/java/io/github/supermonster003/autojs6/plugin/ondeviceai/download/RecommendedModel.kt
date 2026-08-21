package io.github.supermonster003.autojs6.plugin.ondeviceai.download

internal data class RecommendedModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val expectedSizeBytes: Long,
    val expectedSha256: String,
    val license: String,
)

internal object RecommendedModelCatalog {
    val models: List<RecommendedModel> = listOf(
        RecommendedModel(
            id = "gemma-4-e2b-it",
            displayName = "Gemma 4 E2B IT",
            fileName = "gemma-4-E2B-it.litertlm",
            sourceUrl =
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
                    "blob/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/" +
                    "gemma-4-E2B-it.litertlm",
            downloadUrl =
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
                    "resolve/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/" +
                    "gemma-4-E2B-it.litertlm?download=true",
            expectedSizeBytes = 2_583_085_056L,
            expectedSha256 =
                "ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42",
            license = "Apache-2.0",
        ),
        RecommendedModel(
            id = "gemma-4-e4b-it",
            displayName = "Gemma 4 E4B IT",
            fileName = "gemma-4-E4B-it.litertlm",
            sourceUrl =
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/" +
                    "blob/2eee7ac325f20eb8c9ac1d0e972f7c84663062da/" +
                    "gemma-4-E4B-it.litertlm",
            downloadUrl =
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/" +
                    "resolve/2eee7ac325f20eb8c9ac1d0e972f7c84663062da/" +
                    "gemma-4-E4B-it.litertlm?download=true",
            expectedSizeBytes = 3_659_530_240L,
            expectedSha256 =
                "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            license = "Apache-2.0",
        ),
    ).onEach(ModelDownloadPolicy::requireValidCatalogEntry)

    fun find(id: String): RecommendedModel? = models.singleOrNull { it.id == id }
}
