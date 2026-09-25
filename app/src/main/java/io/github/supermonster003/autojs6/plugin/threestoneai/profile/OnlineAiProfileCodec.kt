package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Strict, bounded JSON containing profile metadata only. Authentication fields are never valid. */
internal object OnlineAiProfileCodec {
    const val MAXIMUM_DOCUMENT_BYTES = 512 * 1024
    private val DOCUMENT_KEYS = setOf(
        "schema",
        "revision",
        "defaultProfileId",
        "allowMeteredNetwork",
        "profiles",
    )
    private val LEGACY_PROFILE_KEYS = setOf(
        "profileId",
        "displayName",
        "providerId",
        "baseUrl",
        "modelId",
    )
    private val MULTI_MODEL_KEYS = LEGACY_PROFILE_KEYS + "modelIds"
    private val PROFILE_KEYS = MULTI_MODEL_KEYS + "visionModelIds"

    fun encode(document: OnlineAiProfileDocument): ByteArray {
        val normalized = OnlineAiProfilePolicy.normalize(document)
        val profiles = normalized.profiles.joinToString(",", "[", "]") { profile ->
            "{" +
                "\"profileId\":${quote(profile.profileId)}," +
                "\"displayName\":${quote(profile.displayName)}," +
                "\"providerId\":${quote(profile.provider.providerId)}," +
                "\"baseUrl\":${quote(profile.baseUrl)}," +
                "\"modelId\":${quote(profile.modelId)}," +
                "\"modelIds\":${profile.modelIds.joinToString(",", "[", "]", transform = ::quote)}," +
                "\"visionModelIds\":${profile.visionModelIds.joinToString(",", "[", "]", transform = ::quote)}" +
                "}"
        }
        return ("{" +
            "\"schema\":${OnlineAiProfilePolicy.SCHEMA}," +
            "\"revision\":${normalized.revision}," +
            "\"defaultProfileId\":${normalized.defaultProfileId?.let(::quote) ?: "null"}," +
            "\"allowMeteredNetwork\":${normalized.allowMeteredNetwork}," +
            "\"profiles\":$profiles" +
            "}").toByteArray(Charsets.UTF_8).also { bytes ->
            require(bytes.size in 1..MAXIMUM_DOCUMENT_BYTES) { "Online AI profile document is too large" }
        }
    }

    fun decode(bytes: ByteArray): OnlineAiProfileDocument {
        require(bytes.size in 1..MAXIMUM_DOCUMENT_BYTES) { "Online AI profile document size is invalid" }
        val reader = strictReader(bytes)
        var schema: Int? = null
        var revision: Long? = null
        var defaultProfileId: String? = null
        var defaultProfileIdRead = false
        var allowMeteredNetwork: Boolean? = null
        var profiles: List<DecodedProfile>? = null
        val keys = linkedSetOf<String>()
        reader.use {
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Online AI profile document must be an object" }
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(name in DOCUMENT_KEYS && keys.add(name)) { "Online AI profile document keys are invalid" }
                when (name) {
                    "schema" -> schema = reader.nextSupportedSchema()
                    "revision" -> revision = reader.nextStrictLong("Online AI profile revision")
                    "defaultProfileId" -> {
                        defaultProfileIdRead = true
                        defaultProfileId = reader.nextNullableStrictString(
                            "Default online AI profile ID",
                        )
                    }
                    "allowMeteredNetwork" -> allowMeteredNetwork =
                        reader.nextStrictBoolean("Online AI metered-network setting")
                    "profiles" -> profiles = reader.readProfiles()
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Online AI profile document has trailing data" }
        }
        val decodedProfiles = requireNotNull(profiles)
        require(
            keys == DOCUMENT_KEYS &&
                schema in setOf(OnlineAiProfilePolicy.LEGACY_SCHEMA, OnlineAiProfilePolicy.MULTI_MODEL_SCHEMA, OnlineAiProfilePolicy.SCHEMA) &&
                defaultProfileIdRead,
        ) {
            "Online AI profile document is incomplete"
        }
        if (schema != OnlineAiProfilePolicy.LEGACY_SCHEMA) {
            require(decodedProfiles.all(DecodedProfile::hasModelIds)) {
                "Online AI profile model IDs are incomplete"
            }
        }
        require(decodedProfiles.all { it.hasVisionModelIds == (schema == OnlineAiProfilePolicy.SCHEMA) }) {
            "Online AI image capabilities do not match the document schema"
        }
        return OnlineAiProfilePolicy.normalize(
            OnlineAiProfileDocument(
                revision = requireNotNull(revision),
                profiles = decodedProfiles.map(DecodedProfile::profile),
                defaultProfileId = defaultProfileId,
                allowMeteredNetwork = requireNotNull(allowMeteredNetwork),
            ),
        )
    }

    private fun JsonReader.readProfiles(): List<DecodedProfile> {
        require(peek() == JsonToken.BEGIN_ARRAY) { "Online AI profiles must be an array" }
        val result = ArrayList<DecodedProfile>()
        beginArray()
        while (hasNext()) {
            require(result.size < OnlineAiProfilePolicy.MAXIMUM_PROFILES) {
                "There are too many online AI profiles"
            }
            result += readProfile()
        }
        endArray()
        return result
    }

    private fun JsonReader.readProfile(): DecodedProfile {
        require(peek() == JsonToken.BEGIN_OBJECT) { "Online AI profile must be an object" }
        var profileId: String? = null
        var displayName: String? = null
        var providerId: String? = null
        var baseUrl: String? = null
        var modelId: String? = null
        var modelIds: List<String>? = null
        var visionModelIds: List<String>? = null
        val keys = linkedSetOf<String>()
        beginObject()
        while (hasNext()) {
            val name = nextName()
            require(name in PROFILE_KEYS && keys.add(name)) { "Online AI profile keys are invalid" }
            when (name) {
                "profileId" -> profileId = nextStrictString("Online AI profile ID")
                "displayName" -> displayName = nextStrictString("Online AI profile display name")
                "providerId" -> providerId = nextStrictString("Online AI provider ID")
                "baseUrl" -> baseUrl = nextStrictString("Online AI base URL")
                "modelId" -> modelId = nextStrictString("Online AI model ID")
                "modelIds" -> modelIds = readModelIds()
                "visionModelIds" -> visionModelIds = readModelIds()
            }
        }
        endObject()
        require(keys == LEGACY_PROFILE_KEYS || keys == MULTI_MODEL_KEYS || keys == PROFILE_KEYS) {
            "Online AI profile is incomplete"
        }
        val requiredModelId = requireNotNull(modelId)
        return DecodedProfile(
            profile = OnlineAiProfile(
                profileId = requireNotNull(profileId),
                displayName = requireNotNull(displayName),
                provider = OnlineAiProvider.fromProviderId(requireNotNull(providerId)),
                baseUrl = requireNotNull(baseUrl),
                modelId = requiredModelId,
                modelIds = modelIds ?: listOf(requiredModelId),
                visionModelIds = visionModelIds ?: emptyList(),
            ),
            hasModelIds = modelIds != null,
            hasVisionModelIds = visionModelIds != null,
        )
    }

    private fun JsonReader.readModelIds(): List<String> {
        require(peek() == JsonToken.BEGIN_ARRAY) { "Online AI model IDs must be an array" }
        val result = ArrayList<String>()
        beginArray()
        while (hasNext()) {
            require(result.size < OnlineAiProfilePolicy.MAXIMUM_MODELS_PER_PROFILE) {
                "An online AI profile has too many model IDs"
            }
            result += nextStrictString("Online AI model ID")
        }
        endArray()
        return result
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

    private fun JsonReader.nextNullableStrictString(label: String): String? = when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            null
        }
        JsonToken.STRING -> nextString()
        else -> throw IllegalArgumentException("$label is invalid")
    }

    private fun JsonReader.nextStrictBoolean(label: String): Boolean {
        require(peek() == JsonToken.BOOLEAN) { "$label is invalid" }
        return nextBoolean()
    }

    private fun JsonReader.nextSupportedSchema(): Int {
        val schema = nextStrictLong("Online AI profile schema")
        require(
            schema == OnlineAiProfilePolicy.LEGACY_SCHEMA.toLong() ||
                schema == OnlineAiProfilePolicy.SCHEMA.toLong() ||
                schema == OnlineAiProfilePolicy.MULTI_MODEL_SCHEMA.toLong(),
        ) { "Online AI profile schema is unsupported" }
        return schema.toInt()
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

    private data class DecodedProfile(
        val profile: OnlineAiProfile,
        val hasModelIds: Boolean,
        val hasVisionModelIds: Boolean,
    )
}
