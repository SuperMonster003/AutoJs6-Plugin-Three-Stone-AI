package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Strict schema for the published, credential-free model suggestions. */
internal object OnlineAiModelCatalogCodec {
    const val MAXIMUM_DOCUMENT_BYTES = 256 * 1024
    const val MAXIMUM_MODELS_PER_PROVIDER = 128
    private val DOCUMENT_KEYS = setOf("schemaVersion", "revision", "updatedAtEpochMillis", "providers")
    private val PROVIDER_KEYS = setOf("defaultModelId", "models")
    private val MODEL_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}")
    private val POSITIVE_INTEGER = Regex("[1-9][0-9]*")

    fun decode(bytes: ByteArray): OnlineAiModelCatalog {
        require(bytes.size in 1..MAXIMUM_DOCUMENT_BYTES) { "Model catalog size is invalid" }
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        require(!text.startsWith('\uFEFF')) { "Model catalog must not contain a byte-order mark" }
        var schema: Long? = null
        var revision: Long? = null
        var updatedAt: Long? = null
        var providers: Map<String, OnlineAiModelCatalogProvider>? = null
        JsonReader(StringReader(text)).also { it.strictness = Strictness.STRICT }.use { reader ->
            reader.requireToken(JsonToken.BEGIN_OBJECT)
            reader.beginObject()
            val keys = HashSet<String>()
            while (reader.hasNext()) {
                val key = reader.nextName()
                require(key in DOCUMENT_KEYS && keys.add(key)) { "Model catalog keys are invalid" }
                when (key) {
                    "schemaVersion" -> schema = reader.positiveLong()
                    "revision" -> revision = reader.positiveLong()
                    "updatedAtEpochMillis" -> updatedAt = reader.positiveLong()
                    "providers" -> providers = reader.providers()
                }
            }
            reader.endObject()
            reader.requireToken(JsonToken.END_DOCUMENT)
            require(keys == DOCUMENT_KEYS && schema == 1L) { "Model catalog schema is unsupported or incomplete" }
        }
        return OnlineAiModelCatalog(requireNotNull(revision), requireNotNull(updatedAt), requireNotNull(providers))
    }

    /** Canonical encoding makes harmless JSON whitespace/key ordering irrelevant to revision checks. */
    fun encode(catalog: OnlineAiModelCatalog): ByteArray {
        val providers = OnlineAiModelCatalog.PROVIDER_IDS.joinToString(",") { id ->
            val provider = catalog.providers.getValue(id)
            "\"$id\":{\"defaultModelId\":\"${provider.defaultModelId}\",\"models\":" +
                provider.models.joinToString(",", "[", "]") { "\"$it\"" } + "}"
        }
        return ("{\"schemaVersion\":1,\"revision\":${catalog.revision}," +
            "\"updatedAtEpochMillis\":${catalog.updatedAtEpochMillis},\"providers\":{$providers}}")
            .toByteArray(Charsets.UTF_8)
            .also { decode(it) }
    }

    private fun JsonReader.providers(): Map<String, OnlineAiModelCatalogProvider> {
        requireToken(JsonToken.BEGIN_OBJECT)
        beginObject()
        val result = LinkedHashMap<String, OnlineAiModelCatalogProvider>()
        while (hasNext()) {
            val id = nextName()
            require(id in OnlineAiModelCatalog.PROVIDER_IDS && id !in result) { "Model catalog providers are invalid" }
            result[id] = provider()
        }
        endObject()
        require(result.keys == OnlineAiModelCatalog.PROVIDER_IDS.toSet()) { "Model catalog providers are incomplete" }
        return result
    }

    private fun JsonReader.provider(): OnlineAiModelCatalogProvider {
        requireToken(JsonToken.BEGIN_OBJECT)
        beginObject()
        val keys = HashSet<String>()
        var defaultModelId: String? = null
        var models: List<String>? = null
        while (hasNext()) {
            val key = nextName()
            require(key in PROVIDER_KEYS && keys.add(key)) { "Model catalog provider keys are invalid" }
            when (key) {
                "defaultModelId" -> defaultModelId = modelId()
                "models" -> {
                    requireToken(JsonToken.BEGIN_ARRAY)
                    beginArray()
                    val values = LinkedHashSet<String>()
                    while (hasNext()) {
                        require(values.size < MAXIMUM_MODELS_PER_PROVIDER) { "Too many model catalog entries" }
                        require(values.add(modelId())) { "Duplicate model catalog ID" }
                    }
                    endArray()
                    require(values.isNotEmpty()) { "Model catalog provider is empty" }
                    models = values.toList()
                }
            }
        }
        endObject()
        require(keys == PROVIDER_KEYS && defaultModelId in models.orEmpty()) { "Model catalog default is invalid" }
        return OnlineAiModelCatalogProvider(requireNotNull(defaultModelId), requireNotNull(models))
    }

    private fun JsonReader.modelId(): String {
        requireToken(JsonToken.STRING)
        return nextString().also { require(MODEL_ID.matches(it)) { "Model catalog ID is invalid" } }
    }

    private fun JsonReader.positiveLong(): Long {
        requireToken(JsonToken.NUMBER)
        val raw = nextString()
        require(POSITIVE_INTEGER.matches(raw)) { "Model catalog integer is invalid" }
        return raw.toLongOrNull() ?: throw IllegalArgumentException("Model catalog integer is out of range")
    }

    private fun JsonReader.requireToken(expected: JsonToken) {
        require(peek() == expected) { "Model catalog value has an invalid type or trailing data" }
    }
}
