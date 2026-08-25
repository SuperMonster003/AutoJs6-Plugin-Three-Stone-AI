package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiExecutionProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.toProviderCapabilityIds
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.provider.api.AiBackendProfileInfo
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiTargetAvailability
import org.autojs.plugin.ai.provider.api.AiTargetControlId
import org.autojs.plugin.ai.provider.api.AiTargetInfo
import org.autojs.plugin.ai.provider.api.AiTargetListRequest
import org.autojs.plugin.ai.provider.api.AiTargetPage
import java.security.SecureRandom

internal class TargetPager(
    private val catalogSnapshot: () -> AiTargetCatalog,
    private val tokenSource: () -> String = TargetPageTokenSource::next,
    private val maximumIssuedTokens: Int = AiProviderLimits.MAX_TARGET_PAGE_TOKEN_LEDGER_ENTRIES,
) {
    private val issuedTokens = LinkedHashMap<String, TargetPageContinuation>()
    private var closed = false

    init {
        require(maximumIssuedTokens in 1..AiProviderLimits.MAX_TARGET_PAGE_TOKEN_LEDGER_ENTRIES) {
            "Invalid target page-token capacity"
        }
    }

    /** Tokens are consumed and the catalog snapshot is selected under one service-scoped lock. */
    @Synchronized
    fun page(request: AiTargetListRequest): AiTargetPage {
        if (closed) throw TargetCatalogUnavailableException(IllegalStateException("Target pager is closed"))
        val continuation = request.pageToken?.let(::consumeToken)
        requireSelectedProtocol(request.protocolVersion)
        val catalog = try {
            catalogSnapshot()
        } catch (error: Throwable) {
            throw TargetCatalogUnavailableException(error)
        }
        val targets: List<AiTargetInfo>
        val catalogGeneration: String
        try {
            targets = catalog.targets.map(AiTarget::toProviderTarget)
            catalogGeneration = catalog.generation
        } catch (error: Throwable) {
            throw TargetListingFailedException(error)
        }
        val offset = continuation?.let { token ->
            require(token.protocolVersion == request.protocolVersion) { "Target page-token protocol changed" }
            require(token.pageSize == request.pageSize) { "Target page size changed while continuing a listing" }
            require(token.catalogGeneration == catalogGeneration) {
                "Target catalog changed while continuing a listing"
            }
            require(token.offset in 1 until targets.size && token.offset % token.pageSize == 0) {
                "Target page-token offset is out of range"
            }
            token.offset
        } ?: 0
        val end = minOf(offset + request.pageSize, targets.size)
        val nextPageToken = if (end < targets.size) {
            issueToken(
                TargetPageContinuation(
                    protocolVersion = request.protocolVersion,
                    catalogGeneration = catalogGeneration,
                    pageSize = request.pageSize,
                    offset = end,
                ),
            )
        } else {
            null
        }
        return try {
            AiTargetPage(
                catalogGeneration = catalogGeneration,
                targets = targets.subList(offset, end),
                nextPageToken = nextPageToken,
            )
        } catch (error: Throwable) {
            nextPageToken?.let(issuedTokens::remove)
            throw TargetListingFailedException(error)
        }
    }

    @Synchronized
    fun close() {
        closed = true
        issuedTokens.clear()
    }

    private fun requireSelectedProtocol(requested: AiProtocolVersion) {
        try {
            require(requested in AiProviderProtocol.HOST_PROTOCOL_RANGE)
        } catch (error: Throwable) {
            throw UnsupportedTargetListProtocolException(error)
        }
    }

    private fun consumeToken(token: String): TargetPageContinuation =
        issuedTokens.remove(token) ?: throw InvalidTargetListRequestException()

    private fun issueToken(continuation: TargetPageContinuation): String {
        var generated: String? = null
        for (attempt in 0 until MAXIMUM_TOKEN_GENERATION_ATTEMPTS) {
            val token = try {
                tokenSource()
            } catch (error: Throwable) {
                throw TargetListingFailedException(error)
            }
            if (!OPAQUE_TOKEN.matches(token)) {
                throw TargetListingFailedException(IllegalArgumentException("Invalid generated target page token"))
            }
            if (token !in issuedTokens) {
                generated = token
                break
            }
        }
        val token = generated
            ?: throw TargetListingFailedException(IllegalStateException("Target page token collision limit reached"))
        if (issuedTokens.size >= maximumIssuedTokens) {
            val oldest = issuedTokens.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        issuedTokens[token] = continuation
        return token
    }

    private companion object {
        const val MAXIMUM_TOKEN_GENERATION_ATTEMPTS = 8
        val OPAQUE_TOKEN = Regex("^[0-9a-f]{48}$")
    }
}

private fun AiTarget.toProviderTarget() = AiTargetInfo(
    targetId = targetId,
    providerId = providerId,
    profileId = profileId,
    modelId = modelId,
    displayName = displayName,
    capabilityIds = capabilities.toProviderCapabilityIds(),
    locality = when (locality) {
        AiTargetLocality.LOCAL -> AiDataLocality.ON_DEVICE
        AiTargetLocality.REMOTE -> AiDataLocality.REMOTE
    },
    credentialMode = when (credentialMode) {
        AiTargetCredentialMode.NONE -> AiCredentialMode.NONE
        AiTargetCredentialMode.PLUGIN_MANAGED -> AiCredentialMode.PLUGIN_MANAGED
    },
    availability = if (available) AiTargetAvailability.AVAILABLE else AiTargetAvailability.UNAVAILABLE,
    configured = configured,
    maximumContextBytes = limits.maximumContextBytes,
    maximumOutputBytes = limits.maximumOutputBytes,
    supportedControls = toProviderSupportedControls(),
    declaredHttpsOrigins = declaredHttpsOrigins,
    backendProfiles = executionProfiles.map(AiExecutionProfile::toProviderProfile),
)

internal fun AiTarget.toProviderSupportedControls(): List<String> = buildList {
    add(AiTargetControlId.MAXIMUM_OUTPUT_TOKENS)
    add(AiTargetControlId.TEMPERATURE)
    if (supportsTopKControl()) add(AiTargetControlId.TOP_K)
    add(AiTargetControlId.TOP_P)
    if (capabilities.structuredJson) add(AiTargetControlId.RESPONSE_JSON_SCHEMA)
    if (executionProfiles.isNotEmpty()) add(AiTargetControlId.BACKEND_PROFILE)
}

private fun AiTarget.supportsTopKControl(): Boolean {
    if (locality == AiTargetLocality.LOCAL) return true
    return OnlineAiProvider.fromProviderId(providerId) !in setOf(
        OnlineAiProvider.OPENAI,
        OnlineAiProvider.DEEPSEEK,
    )
}

private fun AiExecutionProfile.toProviderProfile() = AiBackendProfileInfo(
    profileId = profileId,
    availability = if (available) {
        AiProviderBackendAvailability.AVAILABLE
    } else {
        AiProviderBackendAvailability.UNAVAILABLE
    },
    unavailableReason = unavailableReason,
)

internal class InvalidTargetListRequestException : IllegalArgumentException("Invalid target-list request")

internal class UnsupportedTargetListProtocolException(cause: Throwable) :
    IllegalArgumentException("Unsupported target-list protocol", cause)

internal class TargetCatalogUnavailableException(cause: Throwable) :
    IllegalStateException("Target catalog is unavailable", cause)

internal class TargetListingFailedException(cause: Throwable) :
    IllegalStateException("Target listing failed", cause)

private data class TargetPageContinuation(
    val protocolVersion: AiProtocolVersion,
    val catalogGeneration: String,
    val pageSize: Int,
    val offset: Int,
)

private object TargetPageTokenSource {
    private val random = SecureRandom()

    fun next(): String = ByteArray(24).also { bytes -> random.nextBytes(bytes) }.joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
