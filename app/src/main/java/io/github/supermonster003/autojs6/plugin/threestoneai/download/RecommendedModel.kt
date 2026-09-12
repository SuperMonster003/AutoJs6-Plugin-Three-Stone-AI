package io.github.supermonster003.autojs6.plugin.threestoneai.download

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

internal enum class LiteRtModelCapability {
    TEXT,
    IMAGE,
    AUDIO,
    THINKING,
    SPECIALIZED,
}

/** Pinned metadata for reviewed LiteRT-LM bundles published by their model repositories. */
internal data class AvailableLiteRtModel(
    val id: String,
    val displayName: String,
    val repositoryId: String,
    val fileName: String,
    val commitHash: String,
    val sizeBytes: Long,
    val minimumMemoryGb: Int,
    val contextTokens: Int?,
    val maximumOutputTokens: Int,
    val accelerators: List<String>,
    val packaging: String,
    val capabilities: Set<LiteRtModelCapability>,
    val license: String,
    val sha256: String? = null,
) {
    val sourceUrl: String
        get() = "https://huggingface.co/$repositoryId/blob/$commitHash/$fileName"

    val verifiedDownload: RecommendedModel?
        get() = RecommendedModelCatalog.find(id)
}

internal object RecommendedModelCatalog {
    val models: List<RecommendedModel> = listOf(
        RecommendedModel(
            id = "gemma-4-e2b-it",
            displayName = "Gemma 4 E2B IT",
            fileName = "gemma-4-E2B-it.litertlm",
            sourceUrl =
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
                    "blob/7fa1d78473894f7e736a21d920c3aa80f950c0db/" +
                    "gemma-4-E2B-it.litertlm",
            downloadUrl =
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
                    "resolve/7fa1d78473894f7e736a21d920c3aa80f950c0db/" +
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
                    "blob/9695417f248178c63a9f318c6e0c56cb917cb837/" +
                    "gemma-4-E4B-it.litertlm",
            downloadUrl =
                "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/" +
                    "resolve/9695417f248178c63a9f318c6e0c56cb917cb837/" +
                    "gemma-4-E4B-it.litertlm?download=true",
            expectedSizeBytes = 3_654_467_584L,
            expectedSha256 =
                "f335f2bfd1b758dc6476db16c0f41854bd6237e2658d604cbe566bcefd00a7bc",
            license = "Apache-2.0",
        ),
        RecommendedModel(
            id = "qwen2-5-1-5b-instruct",
            displayName = "Qwen2.5 1.5B Instruct",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            sourceUrl =
                "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/" +
                    "blob/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/" +
                    "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl =
                "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/" +
                    "resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/" +
                    "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            expectedSizeBytes = 1_597_931_520L,
            expectedSha256 =
                "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
            license = "Apache-2.0",
        ),
        RecommendedModel(
            id = "deepseek-r1-distill-qwen-1-5b",
            displayName = "DeepSeek R1 Distill Qwen 1.5B",
            fileName =
                "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            sourceUrl =
                "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/" +
                    "blob/e34bb88632342d1f9640bad579a45134eb1cf988/" +
                    "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl =
                "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/" +
                    "resolve/e34bb88632342d1f9640bad579a45134eb1cf988/" +
                    "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm" +
                    "?download=true",
            expectedSizeBytes = 1_833_451_520L,
            expectedSha256 =
                "69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295",
            license = "MIT",
        ),
        RecommendedModel(
            id = "qwen2-5-3b-instruct",
            displayName = "Qwen2.5 3B Instruct",
            fileName = "Qwen2.5-3B-Instruct-LiteRT.litertlm",
            sourceUrl =
                "https://huggingface.co/mlboydaisuke/Qwen2.5-3B-Instruct-LiteRT/" +
                    "blob/be6cf1d794dca07856d2d61988dcbdf49085849c/model.litertlm",
            downloadUrl =
                "https://huggingface.co/mlboydaisuke/Qwen2.5-3B-Instruct-LiteRT/" +
                    "resolve/be6cf1d794dca07856d2d61988dcbdf49085849c/" +
                    "model.litertlm?download=true",
            expectedSizeBytes = 1_751_794_176L,
            expectedSha256 =
                "2e49db88da7c26bcb7ea7abf12d21b4a0215ad0f37468d58b32db5de42078e09",
            license = "Qwen Research License",
        ),
        RecommendedModel(
            id = "qwen2-5-coder-3b-instruct",
            displayName = "Qwen2.5 Coder 3B Instruct",
            fileName = "Qwen2.5_Coder_3B_It.litertlm",
            sourceUrl =
                "https://huggingface.co/litert-community/Qwen2.5-Coder-3B-Instruct/" +
                    "blob/a32e9f082c3fee8adcbe71990eae1eaca3eb0eb9/" +
                    "Qwen2.5_Coder_3B_It.litertlm",
            downloadUrl =
                "https://huggingface.co/litert-community/Qwen2.5-Coder-3B-Instruct/" +
                    "resolve/a32e9f082c3fee8adcbe71990eae1eaca3eb0eb9/" +
                    "Qwen2.5_Coder_3B_It.litertlm?download=true",
            expectedSizeBytes = 3_433_083_824L,
            expectedSha256 =
                "78d23da074383f52f852b945b8090870e6c9dded02a842f535ee3ccb9e2874f3",
            license = "Apache-2.0",
        ),
        RecommendedModel(
            id = "qwen2-vl-2b-instruct",
            displayName = "Qwen2-VL 2B Instruct",
            fileName = "Qwen2-VL-2B.litertlm",
            sourceUrl =
                "https://huggingface.co/litert-community/Qwen2-VL-2B/" +
                    "blob/f9f241a2ed5a10ed3b759076fd9ffbd2e3d2fe4d/" +
                    "Qwen2-VL-2B.litertlm",
            downloadUrl =
                "https://huggingface.co/litert-community/Qwen2-VL-2B/" +
                    "resolve/f9f241a2ed5a10ed3b759076fd9ffbd2e3d2fe4d/" +
                    "Qwen2-VL-2B.litertlm?download=true",
            expectedSizeBytes = 1_784_096_288L,
            expectedSha256 =
                "62db3d9f6ce18a8df56a0b45638518306c4799f025dd7e1c54b35cce8f965d13",
            license = "Apache-2.0",
        ),
        RecommendedModel(
            id = "phi-4-mini-reasoning",
            displayName = "Phi-4 Mini Reasoning",
            fileName = "Phi-4-mini-reasoning.litertlm",
            sourceUrl =
                "https://huggingface.co/litert-community/Phi-4-mini-reasoning/" +
                    "blob/43118d31dfaeaa8d8df12c55983e00ba41cb633e/model.litertlm",
            downloadUrl =
                "https://huggingface.co/litert-community/Phi-4-mini-reasoning/" +
                    "resolve/43118d31dfaeaa8d8df12c55983e00ba41cb633e/" +
                    "model.litertlm?download=true",
            expectedSizeBytes = 2_783_974_384L,
            expectedSha256 =
                "d3938899f3b2d7ad3e86bb1f9c361cf4a9a61f3b79c609b4cd2b6981d00e40fa",
            license = "MIT",
        ),
    ).onEach(ModelDownloadPolicy::requireValidCatalogEntry)

    fun find(id: String): RecommendedModel? = models.singleOrNull { it.id == id }
}

internal object AvailableLiteRtModelCatalog {
    val models: List<AvailableLiteRtModel> = listOf(
        model(
            id = "gemma-4-e2b-it",
            name = "Gemma 4 E2B IT",
            repository = "litert-community/gemma-4-E2B-it-litert-lm",
            file = "gemma-4-E2B-it.litertlm",
            commit = "7fa1d78473894f7e736a21d920c3aa80f950c0db",
            size = 2_583_085_056L,
            memory = 8,
            context = 32_000,
            output = 4_000,
            accelerators = listOf("GPU", "CPU"),
            packaging = "LiteRT-LM",
            capabilities = setOf(
                LiteRtModelCapability.TEXT,
                LiteRtModelCapability.IMAGE,
                LiteRtModelCapability.AUDIO,
                LiteRtModelCapability.THINKING,
            ),
            license = "Apache-2.0",
        ),
        model(
            id = "gemma-4-e4b-it",
            name = "Gemma 4 E4B IT",
            repository = "litert-community/gemma-4-E4B-it-litert-lm",
            file = "gemma-4-E4B-it.litertlm",
            commit = "9695417f248178c63a9f318c6e0c56cb917cb837",
            size = 3_654_467_584L,
            memory = 12,
            context = 32_000,
            output = 4_000,
            accelerators = listOf("GPU", "CPU"),
            packaging = "LiteRT-LM",
            capabilities = setOf(
                LiteRtModelCapability.TEXT,
                LiteRtModelCapability.IMAGE,
                LiteRtModelCapability.AUDIO,
                LiteRtModelCapability.THINKING,
            ),
            license = "Apache-2.0",
        ),
        model(
            id = "gemma-3n-e2b-it",
            name = "Gemma 3n E2B IT",
            repository = "google/gemma-3n-E2B-it-litert-lm",
            file = "gemma-3n-E2B-it-int4.litertlm",
            commit = "ba9ca88da013b537b6ed38108be609b8db1c3a16",
            size = 3_655_827_456L,
            memory = 8,
            context = 4_096,
            output = 4_096,
            accelerators = listOf("CPU", "GPU"),
            packaging = "INT4",
            capabilities = setOf(
                LiteRtModelCapability.TEXT,
                LiteRtModelCapability.IMAGE,
                LiteRtModelCapability.AUDIO,
            ),
            license = "Gemma Terms",
        ),
        model(
            id = "gemma-3n-e4b-it",
            name = "Gemma 3n E4B IT",
            repository = "google/gemma-3n-E4B-it-litert-lm",
            file = "gemma-3n-E4B-it-int4.litertlm",
            commit = "297ed75955702dec3503e00c2c2ecbbf475300bc",
            size = 4_919_541_760L,
            memory = 12,
            context = 4_096,
            output = 4_096,
            accelerators = listOf("CPU", "GPU"),
            packaging = "INT4",
            capabilities = setOf(
                LiteRtModelCapability.TEXT,
                LiteRtModelCapability.IMAGE,
                LiteRtModelCapability.AUDIO,
            ),
            license = "Gemma Terms",
        ),
        model(
            id = "gemma3-1b-it",
            name = "Gemma 3 1B IT",
            repository = "litert-community/Gemma3-1B-IT",
            file = "gemma3-1b-it-int4.litertlm",
            commit = "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
            size = 584_417_280L,
            memory = 6,
            context = null,
            output = 1_024,
            accelerators = listOf("GPU", "CPU"),
            packaging = "INT4",
            capabilities = setOf(LiteRtModelCapability.TEXT),
            license = "Gemma Terms",
        ),
        model(
            id = "qwen2-5-1-5b-instruct",
            name = "Qwen2.5 1.5B Instruct",
            repository = "litert-community/Qwen2.5-1.5B-Instruct",
            file = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            commit = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
            size = 1_597_931_520L,
            memory = 6,
            context = null,
            output = 4_096,
            accelerators = listOf("GPU", "CPU"),
            packaging = "Q8 | EKV 4096",
            capabilities = setOf(LiteRtModelCapability.TEXT),
            license = "Apache-2.0",
        ),
        model(
            id = "deepseek-r1-distill-qwen-1-5b",
            name = "DeepSeek R1 Distill Qwen 1.5B",
            repository = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            file = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            commit = "e34bb88632342d1f9640bad579a45134eb1cf988",
            size = 1_833_451_520L,
            memory = 6,
            context = null,
            output = 4_096,
            accelerators = listOf("GPU", "CPU"),
            packaging = "Q8 | EKV 4096",
            capabilities = setOf(LiteRtModelCapability.TEXT, LiteRtModelCapability.THINKING),
            license = "MIT",
        ),
        model(
            id = "llama-3-2-3b",
            name = "Llama 3.2 3B",
            repository = "litert-community/Llama-3.2-3B",
            file = "llama3_2_3b_mixed_int4_gpu.litertlm",
            commit = "401735124eace78bbaee7bbf8f95581ab2ee63af",
            size = 2_207_940_608L,
            memory = 6,
            context = 4_096,
            output = 4_096,
            accelerators = listOf("GPU", "CPU"),
            packaging = "Mixed INT4",
            capabilities = setOf(LiteRtModelCapability.TEXT),
            license = "Llama 3.2 Community License",
            sha256 = "63419d689ef141cc77d6ab5739f27426d779e1165546a4904edefa9fc36fea51",
        ),
        model(
            id = "llama-3-2-1b",
            name = "Llama 3.2 1B",
            repository = "litert-community/Llama-3.2-1B",
            file = "llama3_2_1b_mixed_int4_gpu.litertlm",
            commit = "fd0e16d32f23ffc87473750e294fef8d8bf081e1",
            size = 963_903_488L,
            memory = 4,
            context = 4_096,
            output = 4_096,
            accelerators = listOf("GPU", "CPU"),
            packaging = "Mixed INT4",
            capabilities = setOf(LiteRtModelCapability.TEXT),
            license = "Llama 3.2 Community License",
            sha256 = "c6f21c5304c40f45b69e5bfb15bb45acbaad7938e941d25a0a391aef0ec6ac27",
        ),
        model(
            id = "qwen2-5-3b-instruct",
            name = "Qwen2.5 3B Instruct",
            repository = "mlboydaisuke/Qwen2.5-3B-Instruct-LiteRT",
            file = "model.litertlm",
            commit = "be6cf1d794dca07856d2d61988dcbdf49085849c",
            size = 1_751_794_176L,
            memory = 6,
            context = 4_096,
            output = 4_096,
            accelerators = listOf("GPU", "CPU"),
            packaging = "INT4 | block 128",
            capabilities = setOf(LiteRtModelCapability.TEXT),
            license = "Qwen Research License",
            sha256 = "2e49db88da7c26bcb7ea7abf12d21b4a0215ad0f37468d58b32db5de42078e09",
        ),
        model(
            id = "qwen2-5-coder-3b-instruct",
            name = "Qwen2.5 Coder 3B Instruct",
            repository = "litert-community/Qwen2.5-Coder-3B-Instruct",
            file = "Qwen2.5_Coder_3B_It.litertlm",
            commit = "a32e9f082c3fee8adcbe71990eae1eaca3eb0eb9",
            size = 3_433_083_824L,
            memory = 8,
            context = null,
            output = 4_096,
            accelerators = listOf("GPU", "CPU"),
            packaging = "Dynamic WI8 AFP32",
            capabilities = setOf(LiteRtModelCapability.TEXT),
            license = "Apache-2.0",
            sha256 = "78d23da074383f52f852b945b8090870e6c9dded02a842f535ee3ccb9e2874f3",
        ),
        model(
            id = "qwen2-vl-2b-instruct",
            name = "Qwen2-VL 2B Instruct",
            repository = "litert-community/Qwen2-VL-2B",
            file = "Qwen2-VL-2B.litertlm",
            commit = "f9f241a2ed5a10ed3b759076fd9ffbd2e3d2fe4d",
            size = 1_784_096_288L,
            memory = 6,
            context = 4_096,
            output = 4_096,
            accelerators = listOf("GPU", "CPU"),
            packaging = "INT4 text | INT8 vision",
            capabilities = setOf(
                LiteRtModelCapability.TEXT,
                LiteRtModelCapability.IMAGE,
            ),
            license = "Apache-2.0",
            sha256 = "62db3d9f6ce18a8df56a0b45638518306c4799f025dd7e1c54b35cce8f965d13",
        ),
        model(
            id = "phi-4-mini-reasoning",
            name = "Phi-4 Mini Reasoning",
            repository = "litert-community/Phi-4-mini-reasoning",
            file = "model.litertlm",
            commit = "43118d31dfaeaa8d8df12c55983e00ba41cb633e",
            size = 2_783_974_384L,
            memory = 8,
            context = 4_096,
            output = 2_048,
            accelerators = listOf("GPU", "CPU"),
            packaging = "INT4 | block 32",
            capabilities = setOf(
                LiteRtModelCapability.TEXT,
                LiteRtModelCapability.THINKING,
            ),
            license = "MIT",
            sha256 = "d3938899f3b2d7ad3e86bb1f9c361cf4a9a61f3b79c609b4cd2b6981d00e40fa",
        ),
        model(
            id = "tiny-garden-270m",
            name = "TinyGarden 270M",
            repository = "litert-community/functiongemma-270m-ft-tiny-garden",
            file = "tiny_garden_q8_ekv1024.litertlm",
            commit = "c205853ff82da86141a1105faa2344a8b176dfe7",
            size = 288_964_608L,
            memory = 6,
            context = null,
            output = 1_024,
            accelerators = listOf("CPU"),
            packaging = "Q8 | EKV 1024",
            capabilities = setOf(LiteRtModelCapability.SPECIALIZED),
            license = "Gemma Terms",
        ),
        model(
            id = "mobile-actions-270m",
            name = "MobileActions 270M",
            repository = "litert-community/functiongemma-270m-ft-mobile-actions",
            file = "mobile_actions_q8_ekv1024.litertlm",
            commit = "38942192c9b723af836d489074823ff33d4a3e7a",
            size = 288_964_608L,
            memory = 6,
            context = null,
            output = 1_024,
            accelerators = listOf("CPU"),
            packaging = "Q8 | EKV 1024",
            capabilities = setOf(LiteRtModelCapability.SPECIALIZED),
            license = "Gemma Terms",
        ),
    ).also { catalog ->
        require(catalog.map(AvailableLiteRtModel::id).distinct().size == catalog.size)
        require(catalog.all { model -> model.sourceUrl.startsWith("https://huggingface.co/") })
        require(catalog.all { model ->
            model.sha256 == null || model.sha256.matches(Regex("^[0-9a-f]{64}$"))
        })
    }

    fun find(id: String): AvailableLiteRtModel? = models.singleOrNull { it.id == id }

    /** Matches a managed import to a Gallery card using pinned identity where available. */
    fun matchesImportedModel(
        model: AvailableLiteRtModel,
        importedSha256: String,
        importedDisplayName: String,
    ): Boolean {
        val expectedSha256 = model.sha256 ?: model.verifiedDownload?.expectedSha256
        return if (expectedSha256 != null) {
            expectedSha256.equals(importedSha256, ignoreCase = true)
        } else {
            model.fileName.equals(importedDisplayName, ignoreCase = true)
        }
    }

    fun displayNameForImportedModel(sourceDisplayName: String, importedModelId: String): String {
        val digestPrefix = importedModelId.removePrefix("litertlm.")
        val digestMatch = digestPrefix.takeIf { it.length == 32 }?.let { prefix ->
            models.singleOrNull { model ->
                val digest = model.sha256 ?: model.verifiedDownload?.expectedSha256
                digest?.startsWith(prefix) == true
            }
        }
        if (digestMatch != null) return digestMatch.displayName
        return models
            .filter { model -> model.fileName.equals(sourceDisplayName, ignoreCase = true) }
            .singleOrNull()
            ?.displayName
            ?: sourceDisplayName
    }

    private fun model(
        id: String,
        name: String,
        repository: String,
        file: String,
        commit: String,
        size: Long,
        memory: Int,
        context: Int?,
        output: Int,
        accelerators: List<String>,
        packaging: String,
        capabilities: Set<LiteRtModelCapability>,
        license: String,
        sha256: String? = null,
    ) = AvailableLiteRtModel(
        id = id,
        displayName = name,
        repositoryId = repository,
        fileName = file,
        commitHash = commit,
        sizeBytes = size,
        minimumMemoryGb = memory,
        contextTokens = context,
        maximumOutputTokens = output,
        accelerators = accelerators,
        packaging = packaging,
        capabilities = capabilities,
        license = license,
        sha256 = sha256,
    )
}
