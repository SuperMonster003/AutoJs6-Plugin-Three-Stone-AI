package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import java.io.Closeable

internal enum class AiTargetLocality {
    LOCAL,
    REMOTE,
}

internal data class AiTargetCapabilities(
    val streaming: Boolean,
    val persistentSession: Boolean,
    val structuredJson: Boolean,
    val usage: Boolean,
    val reasoning: Boolean,
    val tools: Boolean,
)

internal data class AiTargetLimits(
    val maximumContextBytes: Long?,
    val maximumOutputBytes: Long?,
    val maximumOutputTokens: Int? = null,
) {
    init {
        require(maximumContextBytes == null || maximumContextBytes > 0L)
        require(maximumOutputBytes == null || maximumOutputBytes > 0L)
        require(maximumOutputTokens == null || maximumOutputTokens > 0)
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
    val configured: Boolean,
    val available: Boolean,
    val capabilities: AiTargetCapabilities,
    val limits: AiTargetLimits,
    val executionProfiles: List<AiExecutionProfile>,
) {
    init {
        require(targetId.isNotBlank())
        require(backendId.isNotBlank())
        require(providerId.isNotBlank())
        require(profileId == null || profileId.isNotBlank())
        require(modelId.isNotBlank())
        require(displayName.isNotBlank())
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

    fun catalog(): AiTargetCatalog

    fun capabilities(targetId: String): AiTargetCapabilities = catalog().requireTarget(targetId).capabilities

    fun createSession(request: AiBackendSessionRequest): AiBackendSession
}

internal interface AiBackendSession : Closeable {
    val target: AiTarget

    /** Streams the first turn and creates any implementation-specific persistent state. */
    fun stream(request: GenerationRequest, listener: GenerationListener)

    /** Streams a subsequent turn using state retained by the first [stream] call. */
    fun streamNext(request: GenerationRequest, listener: GenerationListener) {
        throw UnsupportedOperationException("Persistent generation is not supported")
    }

    fun cancel()
}

internal class AiTargetUnavailableException(targetId: String) :
    IllegalArgumentException("AI target is unavailable: $targetId")

internal object AiTargetIds {
    private const val LOCAL_PREFIX = "local:"
    private val SEGMENT = Regex("^[a-z0-9][a-z0-9._-]{0,191}$")

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
}

internal enum class GenerationRole {
    SYSTEM,
    USER,
    ASSISTANT,
}

internal data class GenerationMessage(
    val role: GenerationRole,
    val textParts: List<String>,
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
)

/** Exact provider-side counters for one generation turn. */
internal data class GenerationStatistics(
    val inputTokens: Long,
    val outputTokens: Long,
    val durationMillis: Long,
) {
    val totalTokens: Long

    init {
        require(inputTokens >= 0L)
        require(outputTokens >= 0L)
        require(durationMillis >= 0L)
        require(inputTokens <= Long.MAX_VALUE - outputTokens)
        totalTokens = inputTokens + outputTokens
    }
}

internal interface GenerationListener {
    fun onTextDelta(text: String)
    fun onCompleted(statistics: GenerationStatistics?)
    fun onFailed(error: Throwable, statistics: GenerationStatistics?)
}
