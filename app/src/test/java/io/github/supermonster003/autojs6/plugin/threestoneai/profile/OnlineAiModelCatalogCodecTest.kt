package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiModelCatalogCodecTest {
    @Test
    fun preservesExplicitDefaultsAndMergesOnlyDirectProvidersInStableOrder() {
        val catalog = OnlineAiModelCatalogCodec.decode(modelCatalogPayload())
        assertEquals(1L, catalog.revision)
        assertEquals(1000L, catalog.updatedAtEpochMillis)
        assertEquals(listOf("shared", "openai-model"), catalog.forProvider(OnlineAiProvider.OPENAI))
        assertEquals("openai-model", catalog.defaultForProvider(OnlineAiProvider.OPENAI_COMPATIBLE))
        assertEquals(
            listOf("shared", "openai-model", "anthropic-model", "gemini-model", "deepseek-model"),
            catalog.forProvider(OnlineAiProvider.OPENAI_COMPATIBLE),
        )
        assertFalse(catalog.forProvider(OnlineAiProvider.OPENAI_COMPATIBLE).contains("openrouter-model"))
        assertArrayEquals(OnlineAiModelCatalogCodec.encode(catalog), OnlineAiModelCatalogCodec.encode(
            OnlineAiModelCatalogCodec.decode(OnlineAiModelCatalogCodec.encode(catalog)),
        ))
        assertThrows(UnsupportedOperationException::class.java) {
            (catalog.forProvider(OnlineAiProvider.OPENAI) as MutableList<String>).add("modified")
        }
    }

    @Test
    fun rejectsDuplicateKeysAtEveryObjectLevelAndDuplicateModels() {
        val original = modelCatalogPayload().toString(Charsets.UTF_8)
        listOf(
            original.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            original.replace("\"providers\":{", "\"providers\":{\"openai\":{\"defaultModelId\":\"x\",\"models\":[\"x\"]},"),
            original.replace("\"defaultModelId\":\"openai-model\"", "\"defaultModelId\":\"openai-model\",\"defaultModelId\":\"openai-model\""),
            original.replace("[\"shared\",\"openai-model\"]", "[\"shared\",\"shared\",\"openai-model\"]"),
        ).forEach(::reject)
    }

    @Test
    fun rejectsMissingUnknownAndWronglyTypedFields() {
        val original = modelCatalogPayload().toString(Charsets.UTF_8)
        listOf(
            original.replace("\"schemaVersion\":1,", ""),
            original.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            original.replace("\"revision\":1", "\"extra\":1,\"revision\":1"),
            original.replace("\"defaultModelId\":\"openai-model\"", "\"endpoint\":\"x\",\"defaultModelId\":\"openai-model\""),
            original.replace("\"openrouter\":", "\"unknown\":"),
            original.replace("\"openai\":", "\"OPENAI\":"),
            original.replace("\"defaultModelId\":\"openai-model\"", "\"defaultModelId\":null"),
            original.replace("[\"shared\",\"openai-model\"]", "[]"),
            original.replace("[\"shared\",\"openai-model\"]", "[1,\"openai-model\"]"),
            original.replace("[\"shared\",\"openai-model\"]", "\"openai-model\""),
            original.replace("\"defaultModelId\":\"openai-model\"", "\"defaultModelId\":\"missing\""),
        ).forEach(::reject)
        // Missing provider and missing nested property are checked independently of field order.
        reject(original.replace(
            ",\"openrouter\":{\"defaultModelId\":\"openrouter-model\",\"models\":[\"shared\",\"openrouter-model\"]}", "",
        ))
        reject(original.replace("\"defaultModelId\":\"openai-model\",", ""))
    }

    @Test
    fun acceptsOnlyPositiveIntegralLongsWithoutCoercion() {
        val original = modelCatalogPayload().toString(Charsets.UTF_8)
        for (number in listOf("0", "-1", "1.0", "1e0", "\"1\"", "true", "null", "9223372036854775808")) {
            reject(original.replace("\"revision\":1", "\"revision\":$number"))
            reject(original.replace("\"updatedAtEpochMillis\":1000", "\"updatedAtEpochMillis\":$number"))
        }
        assertEquals(Long.MAX_VALUE, OnlineAiModelCatalogCodec.decode(
            original.replace("\"revision\":1", "\"revision\":${Long.MAX_VALUE}").toByteArray(),
        ).revision)
    }

    @Test
    fun rejectsNonAsciiAndOutOfRangeIdsWithoutTrimmingOrNormalizing() {
        val original = modelCatalogPayload().toString(Charsets.UTF_8)
        for (id in listOf("", "/bad", " bad", "bad ", "a@b", "a?b", "a\\nb", "模型", "a".repeat(257))) {
            reject(original.replace("openai-model", id))
        }
        assertTrue(OnlineAiModelCatalogCodec.decode(original.replace("openai-model", "a".repeat(256)).toByteArray())
            .defaultForProvider(OnlineAiProvider.OPENAI).length == 256)
        OnlineAiModelCatalogCodec.decode(original.replace("openai-model", "a._:/-012Z").toByteArray())
    }

    @Test
    fun limitsEachProviderSeparatelyAndBoundsTheEntireUtf8Document() {
        val original = modelCatalogPayload().toString(Charsets.UTF_8)
        fun withCount(count: Int) = original.replace(
            "[\"shared\",\"openai-model\"]",
            (listOf("openai-model") + (1 until count).map { "model-$it" }).joinToString(",", "[", "]") { "\"$it\"" },
        )
        assertEquals(128, OnlineAiModelCatalogCodec.decode(withCount(128).toByteArray()).forProvider(OnlineAiProvider.OPENAI).size)
        reject(withCount(129))
        val maximum = OnlineAiModelCatalogCodec.MAXIMUM_DOCUMENT_BYTES
        OnlineAiModelCatalogCodec.decode(original.padEnd(maximum, ' ').toByteArray())
        reject(original.padEnd(maximum + 1, ' '))
        assertThrows(Exception::class.java) { OnlineAiModelCatalogCodec.decode(byteArrayOf()) }
    }

    @Test
    fun rejectsMalformedUtf8CommentsTrailingDataAndPermissiveJsonSyntax() {
        val original = modelCatalogPayload().toString(Charsets.UTF_8)
        listOf(original + "{}", original + " true", "\uFEFF$original", "/* catalog */$original", original.replace("\"revision\":1", "revision:1"),
            original.replace("\"schemaVersion\":1", "'schemaVersion':1"), original.dropLast(1) + ",}",
        ).forEach(::reject)
        assertThrows(Exception::class.java) {
            OnlineAiModelCatalogCodec.decode(modelCatalogPayload() + byteArrayOf(0xc3.toByte(), 0x28))
        }
    }

    private fun reject(json: String) {
        assertThrows("Expected invalid catalog: ${json.take(120)}", Exception::class.java) {
            OnlineAiModelCatalogCodec.decode(json.toByteArray(Charsets.UTF_8))
        }
    }
}

internal fun modelCatalogPayload(revision: Long = 1L, modelSuffix: String = "model"): ByteArray {
    val providers = listOf("openai", "anthropic", "gemini", "deepseek", "openrouter").joinToString(",") { id ->
        "\"$id\":{\"defaultModelId\":\"$id-$modelSuffix\",\"models\":[\"shared\",\"$id-$modelSuffix\"]}"
    }
    return "{\"schemaVersion\":1,\"revision\":$revision,\"updatedAtEpochMillis\":1000,\"providers\":{$providers}}".toByteArray()
}
