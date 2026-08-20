package io.github.supermonster003.autojs6.plugin.ondeviceai.model

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object ModelCatalogCodec {
    const val MAXIMUM_CATALOG_BYTES = 256 * 1024
    const val MAXIMUM_LEGACY_BYTES = 64 * 1024
    private val CATALOG_KEYS = setOf("schema", "revision", "selectedModelId", "entries")
    private val ENTRY_KEYS = setOf(
        "modelId", "displayName", "fileName", "sizeBytes", "sha256", "importedAtMillis",
    )
    private val LEGACY_KEYS = setOf(
        "schema", "modelId", "displayName", "fileName", "sizeBytes", "sha256", "importedAtMillis",
    )

    fun encode(document: ModelCatalogDocument): ByteArray {
        val normalized = ModelCatalogPolicy.normalize(document)
        val selected = normalized.selectedModelId?.let(::quote) ?: "null"
        val entries = normalized.entries.joinToString(separator = ",", prefix = "[", postfix = "]") { entry ->
            "{" +
                "\"modelId\":${quote(entry.modelId)}," +
                "\"displayName\":${quote(entry.displayName)}," +
                "\"fileName\":${quote(entry.fileName)}," +
                "\"sizeBytes\":${entry.sizeBytes}," +
                "\"sha256\":${quote(entry.sha256)}," +
                "\"importedAtMillis\":${entry.importedAtMillis}" +
                "}"
        }
        return ("{" +
            "\"schema\":${ModelCatalogPolicy.SCHEMA}," +
            "\"revision\":${normalized.revision}," +
            "\"selectedModelId\":$selected," +
            "\"entries\":$entries" +
            "}").toByteArray(Charsets.UTF_8).also {
            require(it.size in 1..MAXIMUM_CATALOG_BYTES) { "Model catalog is too large" }
        }
    }

    fun decode(bytes: ByteArray): ModelCatalogDocument {
        require(bytes.size in 1..MAXIMUM_CATALOG_BYTES) { "Model catalog size is invalid" }
        val reader = strictReader(bytes)
        var schema: Int? = null
        var revision: Long? = null
        var selectedModelId: String? = null
        var selectedSeen = false
        var entries: List<ModelCatalogEntry>? = null
        val keys = linkedSetOf<String>()
        reader.use {
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Model catalog must be an object" }
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(name in CATALOG_KEYS && keys.add(name)) { "Model catalog keys are invalid" }
                when (name) {
                    "schema" -> schema = reader.nextExactInt(ModelCatalogPolicy.SCHEMA, "Model catalog schema")
                    "revision" -> revision = reader.nextStrictLong("Model catalog revision")
                    "selectedModelId" -> {
                        selectedSeen = true
                        selectedModelId = if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                            null
                        } else {
                            reader.nextStrictString("Selected model ID")
                        }
                    }
                    "entries" -> entries = reader.readEntries()
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Model catalog has trailing data" }
        }
        require(keys == CATALOG_KEYS && schema == ModelCatalogPolicy.SCHEMA && selectedSeen) {
            "Model catalog is incomplete"
        }
        return ModelCatalogPolicy.normalize(
            ModelCatalogDocument(
                revision = requireNotNull(revision),
                selectedModelId = selectedModelId,
                entries = requireNotNull(entries),
            ),
        )
    }

    fun decodeLegacyCurrent(bytes: ByteArray): ModelCatalogEntry {
        require(bytes.size in 1..MAXIMUM_LEGACY_BYTES) { "Legacy model metadata size is invalid" }
        val reader = strictReader(bytes)
        var schema: Int? = null
        var modelId: String? = null
        var displayName: String? = null
        var fileName: String? = null
        var sizeBytes: Long? = null
        var sha256: String? = null
        var importedAtMillis: Long? = null
        val keys = linkedSetOf<String>()
        reader.use {
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Legacy model metadata must be an object" }
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(name in LEGACY_KEYS && keys.add(name)) { "Legacy model metadata keys are invalid" }
                when (name) {
                    "schema" -> schema = reader.nextExactInt(1, "Legacy model metadata schema")
                    "modelId" -> modelId = reader.nextStrictString("Legacy model ID")
                    "displayName" -> displayName = reader.nextStrictString("Legacy model display name")
                    "fileName" -> fileName = reader.nextStrictString("Legacy model file name")
                    "sizeBytes" -> sizeBytes = reader.nextStrictLong("Legacy model size")
                    "sha256" -> sha256 = reader.nextStrictString("Legacy model digest")
                    "importedAtMillis" -> importedAtMillis = reader.nextStrictLong("Legacy model import time")
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Legacy model metadata has trailing data" }
        }
        require(keys == LEGACY_KEYS && schema == 1) { "Legacy model metadata is incomplete" }
        return ModelCatalogEntry(
            modelId = requireNotNull(modelId),
            displayName = requireNotNull(displayName),
            fileName = requireNotNull(fileName),
            sizeBytes = requireNotNull(sizeBytes),
            sha256 = requireNotNull(sha256),
            importedAtMillis = requireNotNull(importedAtMillis),
        ).also(ModelCatalogPolicy::requireValidEntry)
    }

    private fun JsonReader.readEntries(): List<ModelCatalogEntry> {
        require(peek() == JsonToken.BEGIN_ARRAY) { "Model catalog entries must be an array" }
        val result = ArrayList<ModelCatalogEntry>()
        beginArray()
        while (hasNext()) {
            require(result.size < ModelCatalogPolicy.MAXIMUM_ENTRIES) { "Model catalog contains too many entries" }
            result += readEntry()
        }
        endArray()
        return result
    }

    private fun JsonReader.readEntry(): ModelCatalogEntry {
        require(peek() == JsonToken.BEGIN_OBJECT) { "Model catalog entry must be an object" }
        var modelId: String? = null
        var displayName: String? = null
        var fileName: String? = null
        var sizeBytes: Long? = null
        var sha256: String? = null
        var importedAtMillis: Long? = null
        val keys = linkedSetOf<String>()
        beginObject()
        while (hasNext()) {
            val name = nextName()
            require(name in ENTRY_KEYS && keys.add(name)) { "Model catalog entry keys are invalid" }
            when (name) {
                "modelId" -> modelId = nextStrictString("Model catalog ID")
                "displayName" -> displayName = nextStrictString("Model catalog display name")
                "fileName" -> fileName = nextStrictString("Model catalog file name")
                "sizeBytes" -> sizeBytes = nextStrictLong("Model catalog size")
                "sha256" -> sha256 = nextStrictString("Model catalog digest")
                "importedAtMillis" -> importedAtMillis = nextStrictLong("Model catalog import time")
            }
        }
        endObject()
        require(keys == ENTRY_KEYS) { "Model catalog entry is incomplete" }
        return ModelCatalogEntry(
            modelId = requireNotNull(modelId),
            displayName = requireNotNull(displayName),
            fileName = requireNotNull(fileName),
            sizeBytes = requireNotNull(sizeBytes),
            sha256 = requireNotNull(sha256),
            importedAtMillis = requireNotNull(importedAtMillis),
        )
    }

    private fun strictReader(bytes: ByteArray): JsonReader {
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        return JsonReader(StringReader(text)).also { it.strictness = Strictness.STRICT }
    }

    private fun JsonReader.nextStrictString(label: String): String {
        require(peek() == JsonToken.STRING) { "$label is invalid" }
        return nextString()
    }

    private fun JsonReader.nextStrictLong(label: String): Long {
        require(peek() == JsonToken.NUMBER) { "$label is invalid" }
        val raw = nextString()
        require(raw.matches(Regex("^(?:0|[1-9][0-9]*)$"))) { "$label is invalid" }
        return raw.toLongOrNull() ?: throw IllegalArgumentException("$label is out of range")
    }

    private fun JsonReader.nextExactInt(expected: Int, label: String): Int {
        val value = nextStrictLong(label)
        require(value == expected.toLong()) { "$label is unsupported" }
        return expected
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }
}
