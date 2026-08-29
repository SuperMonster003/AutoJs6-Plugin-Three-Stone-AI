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
    val credentialMode: AiTargetCredentialMode,
    val declaredHttpsOrigins: List<String>,
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

    /** Streams the first turn and creates any implementation-specific persistent state. */
    fun stream(request: GenerationRequest, listener: GenerationListener)

    /** Streams a subsequent turn using state retained by the first [stream] call. */
    fun streamNext(request: GenerationRequest, listener: GenerationListener) {
        throw UnsupportedOperationException("Persistent generation is not supported")
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

/**
 * Backend-neutral turn events. Text remains opaque here so every target reaches the same
 * surface-owned Markdown renderer; usage and errors are delivered through the same terminal path.
 */
internal interface GenerationListener {
    fun onTextDelta(text: String)
    fun onCompleted(statistics: GenerationStatistics?)
    fun onFailed(error: Throwable, statistics: GenerationStatistics?)
}
