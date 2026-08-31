package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfilePolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody

internal object GeminiGenerateContentProtocolAdapter : OnlineAiProtocolAdapter {
    override fun prepare(
        profile: OnlineAiProfile,
        messages: List<GenerationMessage>,
        turn: GenerationRequest,
        credential: ByteArray,
    ): PreparedOnlineAiRequest {
        val normalized = OnlineAiProfilePolicy.normalizeProfile(profile)
        require(normalized.provider.protocol == OnlineAiProtocol.GEMINI_GENERATE_CONTENT) {
            "Online AI profile does not use the Gemini GenerateContent protocol"
        }
        val messageBytes = OnlineAiRequestSupport.requireConversation(messages)
        val body = JsonObject().apply {
            val systemMessages = messages.filter { message -> message.role == GenerationRole.SYSTEM }
            if (systemMessages.isNotEmpty()) {
                // Keep invariant L0/L1/L2 bytes ahead of the append-only raw content array.
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        systemMessages.forEach { message ->
                            add(JsonObject().apply {
                                addProperty("text", OnlineAiRequestSupport.text(message))
                            })
                        }
                    })
                })
            }
            add("contents", JsonArray().apply {
                messages.filterNot { message -> message.role == GenerationRole.SYSTEM }
                    .forEach { message ->
                        add(JsonObject().apply {
                            addProperty(
                                "role",
                                when (message.role) {
                                    GenerationRole.USER -> "user"
                                    GenerationRole.ASSISTANT -> "model"
                                    GenerationRole.SYSTEM -> error("System messages are top-level")
                                },
                            )
                            add("parts", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("text", OnlineAiRequestSupport.text(message))
                                })
                            })
                        })
                    }
            })
            val generationConfig = JsonObject()
            turn.maximumOutputTokens?.let { maximumOutputTokens ->
                require(maximumOutputTokens > 0) { "Online AI maximum output tokens must be positive" }
                generationConfig.addProperty("maxOutputTokens", maximumOutputTokens)
            }
            turn.samplingOptions?.let { sampling ->
                generationConfig.addProperty("temperature", sampling.temperature)
                generationConfig.addProperty("topK", sampling.topK)
                generationConfig.addProperty("topP", sampling.topP)
            }
            turn.responseJsonSchema?.let { schema ->
                generationConfig.addProperty("responseMimeType", "application/json")
                generationConfig.add(
                    "responseSchema",
                    OnlineAiRequestSupport.parseSchema(schema, messageBytes),
                )
            }
            if (generationConfig.size() > 0) add("generationConfig", generationConfig)
        }
        return OnlineAiRequestSupport.prepare(
            url = endpoint(normalized.baseUrl, normalized.modelId),
            json = body,
            credential = credential,
            credentialHeader = OnlineAiCredentialHeader.GEMINI_API_KEY,
        )
    }

    override fun parseEvent(event: OnlineAiSseEvent): OnlineAiStreamChunk {
        if (event.isDone) OnlineAiResponseSupport.invalidResponse()
        return parse(OnlineAiResponseSupport.parseObject(event.data), requireTerminal = false)
    }

    override fun parseJson(body: ResponseBody): OnlineAiJsonResponse {
        val chunk = parse(OnlineAiResponseSupport.readObject(body), requireTerminal = true)
        if (!chunk.contentSeen) OnlineAiResponseSupport.invalidResponse()
        return OnlineAiJsonResponse(text = chunk.text, usage = chunk.usage)
    }

    private fun parse(root: JsonObject, requireTerminal: Boolean): OnlineAiStreamChunk {
        if (OnlineAiResponseSupport.hasProviderError(root)) OnlineAiResponseSupport.providerError()
        OnlineAiResponseSupport.objectOrNull(root, "promptFeedback")?.let { feedback ->
            val blockReason = OnlineAiResponseSupport.stringOrNull(feedback, "blockReason")
            if (blockReason != null && blockReason != "BLOCK_REASON_UNSPECIFIED") {
                OnlineAiResponseSupport.providerError()
            }
        }
        val usage = usage(OnlineAiResponseSupport.objectOrNull(root, "usageMetadata"))
        val candidates = OnlineAiResponseSupport.arrayOrNull(root, "candidates")
        if (candidates == null || candidates.size() == 0) {
            if (requireTerminal) OnlineAiResponseSupport.invalidResponse()
            return OnlineAiStreamChunk(usage = usage)
        }
        val first = candidates.first()
        if (!first.isJsonObject) OnlineAiResponseSupport.invalidResponse()
        val candidate = first.asJsonObject
        val finishReason = OnlineAiResponseSupport.stringOrNull(candidate, "finishReason")
        val done = when (finishReason) {
            null, "FINISH_REASON_UNSPECIFIED" -> false
            "STOP", "MAX_TOKENS" -> true
            else -> OnlineAiResponseSupport.providerError()
        }
        if (requireTerminal && !done) OnlineAiResponseSupport.invalidResponse()

        val content = OnlineAiResponseSupport.objectOrNull(candidate, "content")
            ?: OnlineAiResponseSupport.invalidResponse()
        val parts = OnlineAiResponseSupport.arrayOrNull(content, "parts")
            ?: OnlineAiResponseSupport.invalidResponse()
        var textPartSeen = false
        val text = buildString {
            parts.forEach { element ->
                if (!element.isJsonObject) OnlineAiResponseSupport.invalidResponse()
                val part = element.asJsonObject
                if (OnlineAiResponseSupport.booleanOrNull(part, "thought") == true) return@forEach
                val partText = OnlineAiResponseSupport.stringOrNull(part, "text") ?: return@forEach
                textPartSeen = true
                append(partText)
            }
        }
        return OnlineAiStreamChunk(
            text = text,
            usage = usage,
            contentSeen = textPartSeen,
            done = done,
        )
    }

    private fun usage(value: JsonObject?): OnlineAiUsageUpdate? {
        value ?: return null
        val input = OnlineAiResponseSupport.countOrNull(value, "promptTokenCount")
        val output = OnlineAiResponseSupport.countOrNull(value, "candidatesTokenCount")
        val total = OnlineAiResponseSupport.countOrNull(value, "totalTokenCount")
        val cached = OnlineAiResponseSupport.countOrNull(value, "cachedContentTokenCount")
        if (input == null && output == null && total == null && cached == null) return null
        return OnlineAiUsageUpdate(
            inputTokens = input,
            outputTokens = output,
            totalTokens = total,
            cachedInputTokens = cached,
            cacheEligibleInputTokens = input.takeIf { cached != null },
        )
    }

    private fun endpoint(baseUrl: String, rawModelId: String): HttpUrl {
        val modelId = rawModelId.removePrefix("models/")
        require(modelId.isNotBlank() && '/' !in modelId && ':' !in modelId) {
            "Gemini model ID is invalid"
        }
        val base = baseUrl.toHttpUrl()
        val segments = base.pathSegments.filter(String::isNotEmpty)
        val endpointSegment = "$modelId:streamGenerateContent"
        return base.newBuilder().apply {
            val lastSegment = segments.lastOrNull()
            if (
                lastSegment?.contains(":generateContent") == true ||
                lastSegment?.contains(":streamGenerateContent") == true
            ) {
                val actualLastIndex = base.pathSegments.indexOfLast(String::isNotEmpty)
                setPathSegment(actualLastIndex, endpointSegment)
            } else {
                if (lastSegment != "models") addPathSegment("models")
                addPathSegment(endpointSegment)
            }
            setQueryParameter("alt", "sse")
        }.build()
    }
}
