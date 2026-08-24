package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogDocument
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.provider.api.AiBackendProfileInfo
import org.autojs.plugin.ai.provider.api.AiModelInfo
import org.autojs.plugin.ai.provider.api.AiModelListRequest
import org.autojs.plugin.ai.provider.api.AiModelPage
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import java.security.SecureRandom

internal class ModelPager(
    private val catalogSnapshot: () -> ModelCatalogDocument,
    private val backendProfiles: () -> List<AiBackendProfileInfo> = ::defaultBackendProfiles,
    private val tokenSource: () -> String = ModelPageTokenSource::next,
    private val maximumIssuedTokens: Int = AiProviderLimits.MAX_MODEL_PAGE_TOKEN_LEDGER_ENTRIES,
) {
    private val issuedTokens = LinkedHashMap<String, ModelPageContinuation>()
    private var closed = false

    init {
        require(maximumIssuedTokens in 1..AiProviderLimits.MAX_MODEL_PAGE_TOKEN_LEDGER_ENTRIES) {
            "Invalid model page-token capacity"
        }
    }

    constructor(
        repository: ModelRepository,
        backendProfiles: () -> List<AiBackendProfileInfo> = ::defaultBackendProfiles,
    ) : this(repository::catalogSnapshot, backendProfiles)

    /** Tokens are consumed and the catalog snapshot is selected under one service-scoped lock. */
    @Synchronized
    fun page(request: AiModelListRequest): AiModelPage {
        if (closed) throw ModelListingUnavailableException(IllegalStateException("Model pager is closed"))
        val continuation = request.pageToken?.let(::consumeToken)
        requireSelectedProtocol(request.protocolVersion)
        val catalog = try {
            ModelCatalogPolicy.normalize(catalogSnapshot())
        } catch (error: Throwable) {
            throw ModelListingUnavailableException(error)
        }
        val models: List<AiModelInfo>
        val listingGeneration: String
        try {
            val detectedBackendProfiles = backendProfiles()
            models = catalog.entries.map { entry ->
                AiModelInfo(
                    modelId = entry.modelId,
                    displayName = entry.displayName,
                    capabilityIds = PUBLIC_CAPABILITY_IDS,
                    maximumContextBytes = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES,
                    maximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
                    backendProfiles = detectedBackendProfiles,
                )
            }
            listingGeneration = ModelCatalogPolicy.listingGeneration(
                document = catalog,
                capabilityIds = PUBLIC_CAPABILITY_IDS,
                maximumContextBytes = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES,
                maximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
                backendProfiles = detectedBackendProfiles,
            )
        } catch (error: Throwable) {
            throw ModelListingFailedException(error)
        }
        val offset = continuation?.let { token ->
            require(token.protocolVersion == request.protocolVersion) { "Model page-token protocol changed" }
            require(token.pageSize == request.pageSize) { "Model page size changed while continuing a listing" }
            require(token.listingGeneration == listingGeneration) {
                "Model catalog changed while continuing a listing"
            }
            require(token.offset in 1 until models.size && token.offset % token.pageSize == 0) {
                "Model page-token offset is out of range"
            }
            token.offset
        } ?: 0
        val end = minOf(offset + request.pageSize, models.size)
        val nextPageToken = if (end < models.size) {
            issueToken(
                ModelPageContinuation(
                    protocolVersion = request.protocolVersion,
                    listingGeneration = listingGeneration,
                    pageSize = request.pageSize,
                    offset = end,
                ),
            )
        } else {
            null
        }
        return try {
            AiModelPage(
                listingGeneration = listingGeneration,
                models = models.subList(offset, end),
                nextPageToken = nextPageToken,
            )
        } catch (error: Throwable) {
            nextPageToken?.let { token -> issuedTokens.remove(token) }
            throw ModelListingFailedException(error)
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
            throw UnsupportedModelListProtocolException(error)
        }
    }

    private fun consumeToken(token: String): ModelPageContinuation =
        issuedTokens.remove(token) ?: throw InvalidModelListRequestException()

    private fun issueToken(continuation: ModelPageContinuation): String {
        var generated: String? = null
        for (attempt in 0 until MAXIMUM_TOKEN_GENERATION_ATTEMPTS) {
            val token = try {
                tokenSource()
            } catch (error: Throwable) {
                throw ModelListingFailedException(error)
            }
            if (!OPAQUE_TOKEN.matches(token)) {
                throw ModelListingFailedException(IllegalArgumentException("Invalid generated model page token"))
            }
            if (token !in issuedTokens) {
                generated = token
                break
            }
        }
        val token = generated
            ?: throw ModelListingFailedException(IllegalStateException("Model page token collision limit reached"))
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
        val PUBLIC_CAPABILITY_IDS = listOf(
            AiProviderCapabilityId.STREAMING,
            AiProviderCapabilityId.STRUCTURED_JSON,
            AiProviderCapabilityId.USAGE,
            AiProviderCapabilityId.PERSISTENT_SESSION,
        )
    }
}

private fun defaultBackendProfiles() = listOf(
    AiBackendProfileInfo(
        profileId = AiProviderBackendProfile.CPU,
        availability = AiProviderBackendAvailability.AVAILABLE,
    ),
)

internal class InvalidModelListRequestException : IllegalArgumentException("Invalid model-list request")

internal class UnsupportedModelListProtocolException(cause: Throwable) :
    IllegalArgumentException("Unsupported model-list protocol", cause)

internal class ModelListingUnavailableException(cause: Throwable) :
    IllegalStateException("Model catalog is unavailable", cause)

internal class ModelListingFailedException(cause: Throwable) :
    IllegalStateException("Model listing failed", cause)

private data class ModelPageContinuation(
    val protocolVersion: AiProtocolVersion,
    val listingGeneration: String,
    val pageSize: Int,
    val offset: Int,
)

private object ModelPageTokenSource {
    private val random = SecureRandom()

    fun next(): String = ByteArray(24).also { bytes -> random.nextBytes(bytes) }.joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
