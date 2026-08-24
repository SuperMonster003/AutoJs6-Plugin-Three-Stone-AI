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
    private val PROFILE_KEYS = setOf("profileId", "displayName", "providerId", "baseUrl", "modelId")

    fun encode(document: OnlineAiProfileDocument): ByteArray {
        val normalized = OnlineAiProfilePolicy.normalize(document)
        val profiles = normalized.profiles.joinToString(",", "[", "]") { profile ->
            "{" +
                "\"profileId\":${quote(profile.profileId)}," +
                "\"displayName\":${quote(profile.displayName)}," +
                "\"providerId\":${quote(profile.provider.providerId)}," +
                "\"baseUrl\":${quote(profile.baseUrl)}," +
                "\"modelId\":${quote(profile.modelId)}" +
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
        var profiles: List<OnlineAiProfile>? = null
        val keys = linkedSetOf<String>()
        reader.use {
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Online AI profile document must be an object" }
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                require(name in DOCUMENT_KEYS && keys.add(name)) { "Online AI profile document keys are invalid" }
                when (name) {
                    "schema" -> schema = reader.nextExactInt(
                        OnlineAiProfilePolicy.SCHEMA,
                        "Online AI profile schema",
                    )
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
        require(
            keys == DOCUMENT_KEYS &&
                schema == OnlineAiProfilePolicy.SCHEMA &&
                defaultProfileIdRead,
        ) {
            "Online AI profile document is incomplete"
        }
        return OnlineAiProfilePolicy.normalize(
            OnlineAiProfileDocument(
                revision = requireNotNull(revision),
                profiles = requireNotNull(profiles),
                defaultProfileId = defaultProfileId,
                allowMeteredNetwork = requireNotNull(allowMeteredNetwork),
            ),
        )
    }

    private fun JsonReader.readProfiles(): List<OnlineAiProfile> {
        require(peek() == JsonToken.BEGIN_ARRAY) { "Online AI profiles must be an array" }
        val result = ArrayList<OnlineAiProfile>()
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

    private fun JsonReader.readProfile(): OnlineAiProfile {
        require(peek() == JsonToken.BEGIN_OBJECT) { "Online AI profile must be an object" }
        var profileId: String? = null
        var displayName: String? = null
        var providerId: String? = null
        var baseUrl: String? = null
        var modelId: String? = null
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
            }
        }
        endObject()
        require(keys == PROFILE_KEYS) { "Online AI profile is incomplete" }
        return OnlineAiProfile(
            profileId = requireNotNull(profileId),
            displayName = requireNotNull(displayName),
            provider = OnlineAiProvider.fromProviderId(requireNotNull(providerId)),
            baseUrl = requireNotNull(baseUrl),
            modelId = requireNotNull(modelId),
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
