package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import okhttp3.ResponseBody

internal data class OnlineAiUsageUpdate(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val cacheWriteInputTokens: Long? = null,
    val cacheEligibleInputTokens: Long? = null,
) {
    init {
        require(
            inputTokens != null || outputTokens != null || totalTokens != null ||
                cachedInputTokens != null || cacheWriteInputTokens != null
        )
        require(inputTokens == null || inputTokens >= 0L)
        require(outputTokens == null || outputTokens >= 0L)
        require(totalTokens == null || totalTokens >= 0L)
        require(cachedInputTokens == null || cachedInputTokens >= 0L)
        require(cacheWriteInputTokens == null || cacheWriteInputTokens >= 0L)
        require(cacheEligibleInputTokens == null || cacheEligibleInputTokens >= 0L)
        require(
            cachedInputTokens == null || cacheEligibleInputTokens == null ||
                cachedInputTokens <= cacheEligibleInputTokens
        )
        require(
            cacheWriteInputTokens == null || cacheEligibleInputTokens == null ||
                cacheWriteInputTokens <= cacheEligibleInputTokens
        )
        if (inputTokens != null && outputTokens != null) {
            require(inputTokens <= Long.MAX_VALUE - outputTokens)
        }
    }
}

internal data class OnlineAiStreamChunk(
    val text: String = "",
    val usage: OnlineAiUsageUpdate? = null,
    val contentSeen: Boolean = false,
    val done: Boolean = false,
)

internal data class OnlineAiJsonResponse(
    val text: String,
    val usage: OnlineAiUsageUpdate?,
)

/** Converts the provider-neutral text session surface to one provider-native HTTP protocol. */
internal interface OnlineAiProtocolAdapter {
    fun prepare(
        profile: OnlineAiProfile,
        messages: List<GenerationMessage>,
        turn: GenerationRequest,
        credential: ByteArray,
    ): PreparedOnlineAiRequest

    fun parseEvent(event: OnlineAiSseEvent, tools: OnlineAiToolCollector? = null): OnlineAiStreamChunk

    fun parseJson(body: ResponseBody, tools: OnlineAiToolCollector? = null): OnlineAiJsonResponse
}

internal object OnlineAiProtocolAdapters {
    fun forProfile(profile: OnlineAiProfile): OnlineAiProtocolAdapter =
        when (profile.provider.protocol) {
            OnlineAiProtocol.OPENAI_COMPATIBLE -> OpenAiCompatibleProtocolAdapter
            OnlineAiProtocol.ANTHROPIC_MESSAGES -> AnthropicMessagesProtocolAdapter
            OnlineAiProtocol.GEMINI_GENERATE_CONTENT -> GeminiGenerateContentProtocolAdapter
        }
}

private object OpenAiCompatibleProtocolAdapter : OnlineAiProtocolAdapter {
    override fun prepare(
        profile: OnlineAiProfile,
        messages: List<GenerationMessage>,
        turn: GenerationRequest,
        credential: ByteArray,
    ): PreparedOnlineAiRequest = OpenAiCompatibleRequestFactory.prepare(
        profile = profile,
        messages = messages,
        turn = turn,
        credential = credential,
    )

    override fun parseEvent(event: OnlineAiSseEvent, tools: OnlineAiToolCollector?): OnlineAiStreamChunk =
        OpenAiCompatibleResponseParser.parseEvent(event, tools)

    override fun parseJson(body: ResponseBody, tools: OnlineAiToolCollector?): OnlineAiJsonResponse =
        OpenAiCompatibleResponseParser.parseJson(body, tools)
}
