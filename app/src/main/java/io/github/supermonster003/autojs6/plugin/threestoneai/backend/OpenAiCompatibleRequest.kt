package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Strictness
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfilePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProviderCatalog
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.Closeable

internal object OnlineAiTransportLimits {
    const val MAXIMUM_CONTEXT_BYTES = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES
    const val MAXIMUM_OUTPUT_BYTES = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES
    const val MAXIMUM_REQUEST_BYTES = 512L * 1024L
    const val MAXIMUM_JSON_RESPONSE_BYTES = 8L * 1024L * 1024L
    const val MAXIMUM_ERROR_RESPONSE_BYTES = 64L * 1024L
    const val MAXIMUM_SSE_EVENT_BYTES = 1024L * 1024L
    const val MAXIMUM_SSE_TOTAL_BYTES = 32L * 1024L * 1024L
    const val MAXIMUM_CREDENTIAL_BYTES = 8 * 1024
}

/** Owns the request body bytes and erases them as soon as the synchronous Call finishes. */
internal class PreparedOnlineAiRequest internal constructor(
    val request: Request,
    private val body: ErasableOneShotJsonRequestBody,
) : Closeable {
    override fun close() = body.close()
}

internal enum class OnlineAiCredentialHeader {
    BEARER,
    ANTHROPIC_API_KEY,
    GEMINI_API_KEY,
}

internal object OnlineAiRequestSupport {
    private val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
    private val STRICT_GSON: Gson = GsonBuilder()
        .setStrictness(Strictness.STRICT)
        .create()

    fun prepare(
        url: HttpUrl,
        json: JsonObject,
        credential: ByteArray,
        credentialHeader: OnlineAiCredentialHeader,
        fixedHeaders: Map<String, String> = emptyMap(),
    ): PreparedOnlineAiRequest {
        requireCredential(credential)
        val content = json.toString().toByteArray(Charsets.UTF_8)
        if (content.size.toLong() > OnlineAiTransportLimits.MAXIMUM_REQUEST_BYTES) {
            content.fill(0)
            throw IllegalArgumentException("Online AI request body is too large")
        }
        val body = ErasableOneShotJsonRequestBody(content, JSON)
        return try {
            val credentialText = String(credential, Charsets.US_ASCII)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-store")
                .header("Content-Type", JSON.toString())
                .header("User-Agent", "AutoJs6-Three-Stone-AI/1")
                .apply {
                    when (credentialHeader) {
                        OnlineAiCredentialHeader.BEARER ->
                            header("Authorization", "Bearer $credentialText")
                        OnlineAiCredentialHeader.ANTHROPIC_API_KEY ->
                            header("x-api-key", credentialText)
                        OnlineAiCredentialHeader.GEMINI_API_KEY ->
                            header("x-goog-api-key", credentialText)
                    }
                    fixedHeaders.forEach { (name, value) -> header(name, value) }
                }
                .build()
            PreparedOnlineAiRequest(request, body)
        } catch (error: Throwable) {
            body.close()
            throw error
        }
    }

    fun requireConversation(messages: List<GenerationMessage>): Long {
        require(messages.isNotEmpty()) { "Online AI request must contain at least one message" }
        require(messages.last().role == GenerationRole.USER) {
            "The final online AI prompt must be a user message"
        }
        var totalBytes = 0L
        messages.forEach { message ->
            require(message.textParts.isNotEmpty()) { "Online AI messages must contain text" }
            message.textParts.forEach { part ->
                totalBytes = try {
                    Math.addExact(totalBytes, part.toByteArray(Charsets.UTF_8).size.toLong())
                } catch (_: ArithmeticException) {
                    throw IllegalArgumentException("Online AI context is too large")
                }
                require(totalBytes <= OnlineAiTransportLimits.MAXIMUM_CONTEXT_BYTES) {
                    "Online AI context is too large"
                }
            }
        }
        return totalBytes
    }

    fun parseSchema(schema: String, messageBytes: Long): JsonObject {
        val schemaBytes = schema.toByteArray(Charsets.UTF_8).size.toLong()
        val combinedBytes = try {
            Math.addExact(messageBytes, schemaBytes)
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Online AI context is too large")
        }
        require(combinedBytes <= OnlineAiTransportLimits.MAXIMUM_CONTEXT_BYTES) {
            "Online AI context is too large"
        }
        val parsed = try {
            STRICT_GSON.fromJson(schema.removePrefix("\uFEFF"), JsonElement::class.java)
        } catch (_: RuntimeException) {
            throw IllegalArgumentException("Online AI response schema must be strict JSON")
        }
        require(parsed != null && parsed.isJsonObject) {
            "Online AI response schema must be a JSON object"
        }
        return parsed.asJsonObject
    }

    fun text(message: GenerationMessage): String {
        require(message.textParts.isNotEmpty()) { "Online AI messages must contain text" }
        return message.textParts.joinToString(separator = "")
    }

    private fun requireCredential(credential: ByteArray) {
        if (
            credential.size !in 1..OnlineAiTransportLimits.MAXIMUM_CREDENTIAL_BYTES ||
            credential.any { byte -> (byte.toInt() and 0xff) !in 0x21..0x7e }
        ) {
            throw OnlineAiFailureException(
                OnlineAiFailureReason.CREDENTIAL_UNAVAILABLE,
            )
        }
    }
}

internal object OpenAiCompatibleRequestFactory {
    private val ENDPOINT_SEGMENTS = listOf("chat", "completions")

    fun prepare(
        profile: OnlineAiProfile,
        messages: List<GenerationMessage>,
        turn: GenerationRequest,
        credential: ByteArray,
    ): PreparedOnlineAiRequest {
        val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
        require(normalized.provider.protocol == OnlineAiProtocol.OPENAI_COMPATIBLE) {
            "Online AI profile does not use the OpenAI-compatible protocol"
        }
        val messageBytes = OnlineAiRequestSupport.requireConversation(messages)

        val json = JsonObject().apply {
            addProperty("model", normalized.modelId)
            add("messages", JsonArray().apply {
                messages.forEach { message -> add(message.toOpenAiJson()) }
            })
            addProperty("stream", true)
            turn.maximumOutputTokens?.let { maximumOutputTokens ->
                require(maximumOutputTokens > 0) { "Online AI maximum output tokens must be positive" }
                addProperty("max_tokens", maximumOutputTokens)
            }
            turn.samplingOptions?.let { sampling ->
                addProperty("temperature", sampling.temperature)
                addProperty("top_p", sampling.topP)
                if (
                    normalized.provider == OnlineAiProvider.OPENROUTER ||
                    normalized.provider == OnlineAiProvider.OPENAI_COMPATIBLE
                ) {
                    addProperty("top_k", sampling.topK)
                }
            }
            if (turn.reportUsage) {
                add("stream_options", JsonObject().apply {
                    addProperty("include_usage", true)
                })
            }
            turn.responseJsonSchema?.let { schema ->
                require(OnlineAiProviderCatalog.templateFor(normalized.provider).structuredJson) {
                    "Online AI provider does not support response schemas"
                }
                add(
                    "response_format",
                    responseFormat(OnlineAiRequestSupport.parseSchema(schema, messageBytes)),
                )
            }
        }
        return OnlineAiRequestSupport.prepare(
            url = endpoint(normalized.baseUrl),
            json = json,
            credential = credential,
            credentialHeader = OnlineAiCredentialHeader.BEARER,
        )
    }

    private fun responseFormat(schema: JsonObject): JsonObject {
        return JsonObject().apply {
            addProperty("type", "json_schema")
            add("json_schema", JsonObject().apply {
                addProperty("name", "response")
                addProperty("strict", true)
                add("schema", schema)
            })
        }
    }

    private fun GenerationMessage.toOpenAiJson(): JsonObject {
        return JsonObject().apply {
            addProperty(
                "role",
                when (role) {
                    GenerationRole.SYSTEM -> "system"
                    GenerationRole.USER -> "user"
                    GenerationRole.ASSISTANT -> "assistant"
                },
            )
            addProperty("content", OnlineAiRequestSupport.text(this@toOpenAiJson))
        }
    }

    private fun endpoint(baseUrl: String): HttpUrl {
        val base = baseUrl.toHttpUrl()
        require(base.isHttps) { "Online AI request URL must use HTTPS" }
        require(base.username.isEmpty() && base.password.isEmpty()) {
            "Online AI request URL must not contain user information"
        }
        require(base.query == null && base.fragment == null) {
            "Online AI request URL must not contain a query or fragment"
        }
        val existingSegments = base.pathSegments.filter(String::isNotEmpty)
        if (existingSegments.takeLast(ENDPOINT_SEGMENTS.size) == ENDPOINT_SEGMENTS) {
            return base
        }
        return base.newBuilder().apply {
            ENDPOINT_SEGMENTS.forEach(::addPathSegment)
        }.build()
    }
}

internal class ErasableOneShotJsonRequestBody(
    private val content: ByteArray,
    private val mediaType: MediaType,
) : RequestBody(), Closeable {
    @Volatile
    private var erased = false

    override fun contentType(): MediaType = mediaType

    override fun contentLength(): Long = content.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        check(!erased) { "Online AI request body is no longer available" }
        sink.write(content)
    }

    override fun isOneShot(): Boolean = true

    override fun close() {
        if (erased) return
        synchronized(this) {
            if (erased) return
            content.fill(0)
            erased = true
        }
    }
}
