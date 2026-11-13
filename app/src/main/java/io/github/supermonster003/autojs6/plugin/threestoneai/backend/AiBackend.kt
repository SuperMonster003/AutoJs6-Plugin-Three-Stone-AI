package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import java.io.Closeable
import java.security.MessageDigest

internal enum class AiTargetLocality {
    LOCAL,
    REMOTE,
}

internal enum class AiTargetCredentialMode {
    NONE,
    PLUGIN_MANAGED,
}

internal data class AiTargetCapabilities(
    val streaming: Boolean,
    val persistentSession: Boolean,
    val structuredJson: Boolean,
    val usage: Boolean,
    val reasoning: Boolean,
    val tools: Boolean,
    val vision: Boolean = false,
    /** Effort levels the target distinguishes, lowest first; empty hides effort controls (D73). */
    val effortLevels: List<GenerationEffort> = emptyList(),
    /** Whether the chat screen may offer the fast output mode for this target (D74). */
    val fastMode: Boolean = false,
    /** Chat attachments this target accepts; null takes none (roadmap P4, D70). */
    val attachments: AiAttachmentInput? = null,
)

internal data class AiTargetLimits(
    val maximumContextBytes: Long?,
    val maximumOutputBytes: Long?,
    val maximumOutputTokens: Int? = null,
    /** Plugin-internal model/provider capacity. Binder target pages intentionally omit this. */
    val maximumContextTokens: Int? = null,
) {
    init {
        require(maximumContextBytes == null || maximumContextBytes > 0L)
        require(maximumOutputBytes == null || maximumOutputBytes > 0L)
        require(maximumOutputTokens == null || maximumOutputTokens > 0)
        require(maximumContextTokens == null || maximumContextTokens > 0)
    }
}

internal data class AiExecutionProfile(
    val profileId: String,
    val available: Boolean,
    val unavailableReason: String? = null,
) {
    init {
        require(profileId.isNotBlank())
        require(available || !unavailableReason.isNullOrBlank())
        require(!available || unavailableReason == null)
    }
}

/** One selectable inference destination, independent of its local or remote implementation. */
internal data class AiTarget(
    val targetId: String,
    val backendId: String,
    val providerId: String,
    val profileId: String?,
    val modelId: String,
    val displayName: String,
    val locality: AiTargetLocality,
    val credentialMode: AiTargetCredentialMode,
    val declaredHttpsOrigins: List<String>,
    val configured: Boolean,
    val available: Boolean,
    val capabilities: AiTargetCapabilities,
    val limits: AiTargetLimits,
    val executionProfiles: List<AiExecutionProfile>,
    /** Profile default a new chat conversation starts from (D72); local targets keep DEFAULT. */
    val defaultEffort: GenerationEffort = GenerationEffort.DEFAULT,
    /** Profile fast-mode default a new chat conversation starts from (D72 / D74). */
    val defaultFast: Boolean = false,
) {
    init {
        require(targetId.isNotBlank())
        require(backendId.isNotBlank())
        require(providerId.isNotBlank())
        require(profileId == null || profileId.isNotBlank())
        require(modelId.isNotBlank())
        require(displayName.isNotBlank())
        require(declaredHttpsOrigins.size <= AiCommonLimits.MAX_ORIGINS) {
            "AI target declares too many origins"
        }
        require(declaredHttpsOrigins.distinct().size == declaredHttpsOrigins.size) {
            "AI target declared origins must be unique"
        }
        declaredHttpsOrigins.forEach(AiValidation::requireHttpsOrigin)
        when (locality) {
            AiTargetLocality.LOCAL -> {
                require(profileId == null)
                require(AiTargetIds.requireLocalModelId(targetId) == modelId)
                require(credentialMode == AiTargetCredentialMode.NONE)
                require(declaredHttpsOrigins.isEmpty())
            }
            AiTargetLocality.REMOTE -> {
                require(profileId != null)
                require(AiTargetIds.requireProfileId(targetId) == profileId)
                require(credentialMode == AiTargetCredentialMode.PLUGIN_MANAGED)
                require(declaredHttpsOrigins.isNotEmpty())
            }
        }
        require(configured || !available) { "An unconfigured AI target cannot be available" }
        require(executionProfiles.map(AiExecutionProfile::profileId).distinct().size == executionProfiles.size) {
            "AI target execution profiles must be unique"
        }
    }
}

internal data class AiTargetCatalog(
    val generation: String,
    val defaultTargetId: String?,
    val targets: List<AiTarget>,
) {
    init {
        require(generation.isNotBlank())
        require(targets.map(AiTarget::targetId).distinct().size == targets.size) {
            "AI target IDs must be unique"
        }
        require(defaultTargetId == null || targets.any { it.targetId == defaultTargetId }) {
            "The default AI target must exist in the catalog"
        }
    }

    fun requireTarget(targetId: String): AiTarget =
        targets.singleOrNull { it.targetId == targetId } ?: throw AiTargetUnavailableException(targetId)
}

internal data class AiBackendSessionRequest(
    val targetId: String,
    val executionProfileId: String? = null,
) {
    init {
        require(targetId.isNotBlank())
        require(executionProfileId == null || executionProfileId.isNotBlank())
    }
}

/** Plugin-owned backend boundary shared by launcher chat and Binder provider surfaces. */
internal interface AiBackend {
    val backendId: String

    fun ownsTarget(targetId: String): Boolean

    fun catalog(): AiTargetCatalog

    fun capabilities(targetId: String): AiTargetCapabilities = catalog().requireTarget(targetId).capabilities

    fun createSession(request: AiBackendSessionRequest): AiBackendSession
}

internal interface AiBackendSession : Closeable {
    val target: AiTarget

    /** True when retained native resources can rebuild persistent state without a new session. */
    val supportsInPlacePersistentRebuild: Boolean
        get() = false

    /** Streams the first turn and creates any implementation-specific persistent state. */
    fun stream(request: GenerationRequest, listener: GenerationListener)

    /** Streams a subsequent turn using state retained by the first [stream] call. */
    fun streamNext(request: GenerationRequest, listener: GenerationListener) {
        throw UnsupportedOperationException("Persistent generation is not supported")
    }

    /** Continues the active generation after the caller has executed every pending tool. */
    fun submitToolResults(results: List<GenerationToolResult>) {
        throw UnsupportedOperationException("Tool continuation is not supported")
    }

    /** Replaces persistent state while retaining expensive implementation-specific resources. */
    fun streamRebuilt(request: GenerationRequest, listener: GenerationListener) {
        throw UnsupportedOperationException("In-place persistent rebuild is not supported")
    }

    /** Requests cancellation before releasing every session resource. */
    fun cancelAndClose() {
        try {
            cancel()
        } finally {
            close()
        }
    }

    fun cancel()

    /** What the last [cancel] did to in-flight work, for cancellation logs (roadmap D77); null when unknown. */
    fun cancellationDiagnostics(): String? = null
}

internal class AiTargetUnavailableException(targetId: String) :
    IllegalArgumentException("AI target is unavailable: $targetId")

internal object AiTargetIds {
    private const val LOCAL_PREFIX = "local:"
    private const val PROFILE_PREFIX = "profile:"
    private const val PROFILE_MODEL_SEPARATOR = ".model."
    private val SEGMENT = AiValidation.STABLE_ID
    private val MODEL_KEY = Regex("^[0-9a-f]{24}$")

    fun local(modelId: String): String {
        require(SEGMENT.matches(modelId)) { "Local AI model ID is invalid" }
        return "$LOCAL_PREFIX$modelId"
    }

    fun requireLocalModelId(targetId: String): String {
        require(targetId.startsWith(LOCAL_PREFIX)) { "AI target is not local" }
        return targetId.removePrefix(LOCAL_PREFIX).also { modelId ->
            require(SEGMENT.matches(modelId)) { "Local AI target ID is invalid" }
        }
    }

    fun profile(profileId: String): String {
        require(SEGMENT.matches(profileId)) { "Online AI profile ID is invalid" }
        return "$PROFILE_PREFIX$profileId"
    }

    /** Keeps the profile's first model on the legacy ID and gives every alternative a stable ID. */
    fun profileModel(profileId: String, legacyModelId: String, modelId: String): String {
        val base = profile(profileId)
        return if (modelId == legacyModelId) base else {
            "$base$PROFILE_MODEL_SEPARATOR${modelKey(modelId)}"
        }
    }

    fun requireProfileId(targetId: String): String {
        require(targetId.startsWith(PROFILE_PREFIX)) { "AI target is not an online profile" }
        val payload = targetId.removePrefix(PROFILE_PREFIX)
        val parts = payload.split(PROFILE_MODEL_SEPARATOR, limit = 2)
        require(parts.size == 1 || MODEL_KEY.matches(parts[1])) {
            "Online AI model target ID is invalid"
        }
        return parts[0].also { profileId ->
            require(SEGMENT.matches(profileId)) { "Online AI target ID is invalid" }
        }
    }

    private fun modelKey(modelId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(modelId.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        .take(24)
}

internal enum class GenerationRole {
    SYSTEM,
    USER,
    ASSISTANT,
}

internal data class GenerationMessage(
    val role: GenerationRole,
    val textParts: List<String>,
    val nativeToolMessage: NativeToolMessage? = null,
    val images: List<GenerationImage> = emptyList(),
    /** Chat attachments of a user message (roadmap P4); scripts never send them. */
    val attachments: List<GenerationAttachment> = emptyList(),
)

internal data class GenerationSamplingOptions(
    val temperature: Double,
    val topK: Int,
    val topP: Double,
) {
    init {
        require(temperature.isFinite() && temperature >= 0.0)
        require(topK > 0)
        require(topP.isFinite() && topP in 0.0..1.0)
    }
}

internal data class GenerationRequest(
    val history: List<GenerationMessage>,
    val prompt: GenerationMessage,
    val maximumOutputTokens: Int?,
    val samplingOptions: GenerationSamplingOptions?,
    val reportUsage: Boolean,
    val responseJsonSchema: String? = null,
    val tools: List<GenerationToolDefinition> = emptyList(),
    val maximumToolRounds: Int = 0,
    /**
     * Chat-screen effort for this turn. Null (Binder requests, summaries, connection tests) and
     * [GenerationEffort.DEFAULT] both omit every effort field (D73).
     */
    val effort: GenerationEffort? = null,
    /** Chat-screen fast output mode. Null and false both omit the fast fields (D73 / D74). */
    val fast: Boolean? = null,
    /**
     * Chat-screen request for visible reasoning text (P3.1, D93): Anthropic thinking display
     * "summarized" and Gemini includeThoughts. Binder requests, summaries and connection tests keep
     * false so script output and request bodies stay unchanged.
     */
    val includeReasoning: Boolean = false,
)

/** Exact provider-side counters for one generation turn. */
internal data class GenerationStatistics(
    val inputTokens: Long,
    val outputTokens: Long,
    val durationMillis: Long,
    /** Full retained context after this turn when the backend can report it exactly. */
    val contextTokensAfterTurn: Long? = null,
    /** Provider-reported prompt tokens served from a reusable prefix cache. */
    val cachedInputTokens: Long? = null,
    /** Provider-reported prompt tokens written into a reusable prefix cache. */
    val cacheWriteInputTokens: Long? = null,
    /** Input-token denominator to use for cache-rate telemetry. */
    val cacheEligibleInputTokens: Long? = null,
) {
    val totalTokens: Long

    init {
        require(inputTokens >= 0L)
        require(outputTokens >= 0L)
        require(durationMillis >= 0L)
        require(contextTokensAfterTurn == null || contextTokensAfterTurn >= 0L)
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
        require(inputTokens <= Long.MAX_VALUE - outputTokens)
        totalTokens = inputTokens + outputTokens
    }

    val promptCacheHitRate: Double?
        get() {
            val cached = cachedInputTokens ?: return null
            val eligible = cacheEligibleInputTokens ?: inputTokens
            return if (eligible > 0L) cached.toDouble() / eligible.toDouble() else 0.0
        }
}

/**
 * Backend-neutral turn events. Text remains opaque here so every target reaches the same
 * surface-owned Markdown renderer; usage and errors are delivered through the same terminal path.
 */
internal interface GenerationListener {
    fun onTextDelta(text: String)
    /**
     * Visible reasoning text streamed before or between answer text (P3.1). It is never part of the
     * answer; listeners that do not show reasoning (Binder, summaries) ignore it.
     */
    fun onReasoningDelta(text: String) {}
    /** A pause, not a terminal event. Statistics cover this one model invocation. */
    fun onToolCalls(calls: List<GenerationToolCall>, statistics: GenerationStatistics?) {
        throw UnsupportedOperationException("This listener does not accept tool calls")
    }
    fun onCompleted(statistics: GenerationStatistics?)
    /**
     * Terminal event that also reports whether the provider stopped at its output-token limit (Anthropic
     * stop_reason max_tokens, OpenAI finish_reason length, Gemini MAX_TOKENS). The streamed text is then
     * valid but incomplete. Listeners without a truncation surface keep the plain [onCompleted].
     */
    fun onCompleted(statistics: GenerationStatistics?, truncated: Boolean) = onCompleted(statistics)
    fun onFailed(error: Throwable, statistics: GenerationStatistics?)
}
