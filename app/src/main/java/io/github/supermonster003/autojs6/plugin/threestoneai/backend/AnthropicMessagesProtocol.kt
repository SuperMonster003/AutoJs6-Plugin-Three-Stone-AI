package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfilePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody

internal object AnthropicMessagesProtocolAdapter : OnlineAiProtocolAdapter {
    private val ENDPOINT_SEGMENTS = listOf("messages")

    override fun prepare(
        profile: OnlineAiProfile,
        messages: List<GenerationMessage>,
        turn: GenerationRequest,
        credential: ByteArray,
    ): PreparedOnlineAiRequest {
        val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
        require(normalized.provider.protocol == OnlineAiProtocol.ANTHROPIC_MESSAGES) {
            "Online AI profile does not use the Anthropic Messages protocol"
        }
        OnlineAiImages.requireSupported(normalized, messages)
        val messageBytes = OnlineAiTools.requireRequest(turn, OnlineAiRequestSupport.requireConversation(messages))
        val maximumOutputTokens = turn.maximumOutputTokens ?: DEFAULT_MAXIMUM_OUTPUT_TOKENS
        require(maximumOutputTokens > 0) { "Online AI maximum output tokens must be positive" }
        val body = JsonObject().apply {
            addProperty("model", normalized.modelId)
            addProperty("stream", true)
            addProperty("max_tokens", maximumOutputTokens)
            if (turn.tools.isNotEmpty()) add("tools", OnlineAiTools.definitions(turn.tools, normalized.provider.protocol))
            val systemMessages = messages.filter { message -> message.role == GenerationRole.SYSTEM }
            if (systemMessages.isNotEmpty()) {
                // Keep invariant L0/L1/L2 bytes ahead of the append-only raw message array.
                add("system", JsonArray().apply {
                    systemMessages.forEach { message ->
                        add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", OnlineAiRequestSupport.text(message))
                        })
                    }
                })
            }
            add("messages", JsonArray().apply {
                messages.filterNot { message -> message.role == GenerationRole.SYSTEM }
                    .forEach { message ->
                        add(JsonObject().apply {
                            addProperty(
                                "role",
                                when (message.role) {
                                    GenerationRole.USER -> "user"
                                    GenerationRole.ASSISTANT -> "assistant"
                                    GenerationRole.SYSTEM -> error("System messages are top-level")
                                },
                            )
                            if (message.nativeToolMessage != null) {
                                require(message.nativeToolMessage.protocol == normalized.provider.protocol)
                                add("content", OnlineAiImages.replay(message.nativeToolMessage))
                            } else if (message.images.isEmpty()) addProperty("content", OnlineAiRequestSupport.text(message))
                            else add("content", OnlineAiImages.content(OnlineAiRequestSupport.text(message), message.images, normalized.provider.protocol))
                        })
                    }
            })
            turn.samplingOptions?.let { sampling ->
                addProperty("temperature", sampling.temperature)
                addProperty("top_k", sampling.topK)
                addProperty("top_p", sampling.topP)
            }
            turn.responseJsonSchema?.let { schema ->
                add("output_config", JsonObject().apply {
                    add("format", JsonObject().apply {
                        addProperty("type", "json_schema")
                        add("schema", OnlineAiRequestSupport.parseSchema(schema, messageBytes))
                    })
                })
            }
        }
        return OnlineAiRequestSupport.prepare(
            url = endpoint(normalized.baseUrl),
            json = body,
            credential = credential,
            withImages = messages.any { it.images.isNotEmpty() || it.nativeToolMessage?.imageResults.orEmpty().isNotEmpty() },
            credentialHeader = OnlineAiCredentialHeader.ANTHROPIC_API_KEY,
            fixedHeaders = mapOf("anthropic-version" to ANTHROPIC_VERSION),
        )
    }

    override fun parseEvent(event: OnlineAiSseEvent, tools: OnlineAiToolCollector?): OnlineAiStreamChunk {
        if (event.isDone) OnlineAiResponseSupport.invalidResponse()
        val root = OnlineAiResponseSupport.parseObject(event.data)
        if (event.event == "error" || OnlineAiResponseSupport.hasProviderError(root)) {
            OnlineAiResponseSupport.providerError()
        }
        val type = OnlineAiResponseSupport.stringOrNull(root, "type")
            ?: event.event
            ?: OnlineAiResponseSupport.invalidResponse()
        if (event.event != null && event.event != type) OnlineAiResponseSupport.invalidResponse()
        if (type == "error") OnlineAiResponseSupport.providerError()
        tools?.event(root)
        return when (type) {
            "message_start" -> {
                val message = OnlineAiResponseSupport.objectOrNull(root, "message")
                    ?: OnlineAiResponseSupport.invalidResponse()
                OnlineAiStreamChunk(usage = usage(OnlineAiResponseSupport.objectOrNull(message, "usage")))
            }
            "content_block_start" -> {
                val block = OnlineAiResponseSupport.objectOrNull(root, "content_block")
                    ?: OnlineAiResponseSupport.invalidResponse()
                if (OnlineAiResponseSupport.stringOrNull(block, "type") == "text") {
                    OnlineAiStreamChunk(
                        text = OnlineAiResponseSupport.stringOrNull(block, "text")
                            ?: OnlineAiResponseSupport.invalidResponse(),
                        contentSeen = true,
                    )
                } else {
                    OnlineAiStreamChunk(contentSeen = OnlineAiResponseSupport.stringOrNull(block, "type") == "tool_use")
                }
            }
            "content_block_delta" -> {
                val delta = OnlineAiResponseSupport.objectOrNull(root, "delta")
                    ?: OnlineAiResponseSupport.invalidResponse()
                if (OnlineAiResponseSupport.stringOrNull(delta, "type") == "text_delta") {
                    OnlineAiStreamChunk(
                        text = OnlineAiResponseSupport.stringOrNull(delta, "text")
                            ?: OnlineAiResponseSupport.invalidResponse(),
                        contentSeen = true,
                    )
                } else {
                    OnlineAiStreamChunk()
                }
            }
            "message_delta" -> {
                val delta = OnlineAiResponseSupport.objectOrNull(root, "delta")
                    ?: OnlineAiResponseSupport.invalidResponse()
                if (OnlineAiResponseSupport.stringOrNull(delta, "stop_reason") == "refusal") {
                    OnlineAiResponseSupport.providerError()
                }
                OnlineAiStreamChunk(usage = usage(OnlineAiResponseSupport.objectOrNull(root, "usage")))
            }
            "message_stop" -> OnlineAiStreamChunk(done = true)
            "ping", "content_block_stop" -> OnlineAiStreamChunk()
            else -> OnlineAiStreamChunk()
        }
    }

    override fun parseJson(body: ResponseBody, tools: OnlineAiToolCollector?): OnlineAiJsonResponse {
        val root = OnlineAiResponseSupport.readObject(body)
        tools?.json(root)
        if (
            OnlineAiResponseSupport.stringOrNull(root, "type") == "error" ||
            OnlineAiResponseSupport.hasProviderError(root)
        ) {
            OnlineAiResponseSupport.providerError()
        }
        if (OnlineAiResponseSupport.stringOrNull(root, "stop_reason") == "refusal") {
            OnlineAiResponseSupport.providerError()
        }
        val content = OnlineAiResponseSupport.arrayOrNull(root, "content")
            ?: OnlineAiResponseSupport.invalidResponse()
        var textBlockSeen = false
        val text = buildString {
            content.forEach { element ->
                if (!element.isJsonObject) OnlineAiResponseSupport.invalidResponse()
                val block = element.asJsonObject
                if (OnlineAiResponseSupport.stringOrNull(block, "type") == "text") {
                    textBlockSeen = true
                    append(
                        OnlineAiResponseSupport.stringOrNull(block, "text")
                            ?: OnlineAiResponseSupport.invalidResponse(),
                    )
                }
            }
        }
        if (!textBlockSeen && content.none { it.asJsonObject.get("type")?.asString == "tool_use" }) {
            OnlineAiResponseSupport.invalidResponse()
        }
        return OnlineAiJsonResponse(
            text = text,
            usage = usage(OnlineAiResponseSupport.objectOrNull(root, "usage")),
        )
    }

    private fun usage(value: JsonObject?): OnlineAiUsageUpdate? {
        value ?: return null
        val uncachedInput = OnlineAiResponseSupport.countOrNull(value, "input_tokens")
        val output = OnlineAiResponseSupport.countOrNull(value, "output_tokens")
        val cacheRead = OnlineAiResponseSupport.countOrNull(value, "cache_read_input_tokens")
        val cacheWrite = OnlineAiResponseSupport.countOrNull(value, "cache_creation_input_tokens")
        if (uncachedInput == null && output == null && cacheRead == null && cacheWrite == null) return null
        val fullInput = if (uncachedInput != null || cacheRead != null || cacheWrite != null) {
            try {
                Math.addExact(
                    Math.addExact(uncachedInput ?: 0L, cacheRead ?: 0L),
                    cacheWrite ?: 0L,
                )
            } catch (_: ArithmeticException) {
                OnlineAiResponseSupport.invalidResponse()
            }
        } else {
            null
        }
        return OnlineAiUsageUpdate(
            inputTokens = fullInput,
            outputTokens = output,
            cachedInputTokens = cacheRead,
            cacheWriteInputTokens = cacheWrite,
            cacheEligibleInputTokens = fullInput.takeIf { cacheRead != null || cacheWrite != null },
        )
    }

    private fun endpoint(baseUrl: String): HttpUrl {
        val base = baseUrl.toHttpUrl()
        val existingSegments = base.pathSegments.filter(String::isNotEmpty)
        if (existingSegments.takeLast(ENDPOINT_SEGMENTS.size) == ENDPOINT_SEGMENTS) return base
        return base.newBuilder().apply { ENDPOINT_SEGMENTS.forEach(::addPathSegment) }.build()
    }

    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val DEFAULT_MAXIMUM_OUTPUT_TOKENS = 1024
}
