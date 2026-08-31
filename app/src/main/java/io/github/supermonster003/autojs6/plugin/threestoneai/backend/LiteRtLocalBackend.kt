package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ImportedModel
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogDocument
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogEntry
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelCatalogPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.model.ModelRepository
import org.autojs.plugin.ai.provider.api.AiBackendProfileInfo
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId

/** Local backend adapter; native LiteRT-LM session mechanics remain isolated in [LiteRtLocalSession]. */
internal class LiteRtLocalBackend(
    private val catalogSnapshot: () -> ModelCatalogDocument,
    private val findModelById: (String) -> ImportedModel?,
    private val backendProfiles: () -> List<AiBackendProfileInfo>,
    private val requireBackendProfile: (String) -> LiteRtLmBackendProfile,
    private val sessionFactory: (AiTarget, ImportedModel, LiteRtLmBackendProfile) -> AiBackendSession,
) : AiBackend {

    constructor(
        repository: ModelRepository,
        compatibilityDetector: LiteRtLmBackendCompatibilityDetector,
        engineRuntime: LiteRtLmEngineRuntime,
    ) : this(
        catalogSnapshot = repository::catalogSnapshot,
        findModelById = repository::findByModelId,
        backendProfiles = { compatibilityDetector.profiles },
        requireBackendProfile = compatibilityDetector::requireAvailable,
        sessionFactory = { target, model, profile ->
            engineRuntime.createSession(
                target = target,
                modelSha256 = model.sha256,
                modelPath = model.file.absolutePath,
                backendProfile = profile,
            )
        },
    )

    override val backendId: String = BACKEND_ID

    override fun ownsTarget(targetId: String): Boolean =
        runCatching { AiTargetIds.requireLocalModelId(targetId) }.isSuccess

    override fun catalog(): AiTargetCatalog = LiteRtLocalCatalog.create(
        document = catalogSnapshot(),
        backendProfiles = backendProfiles(),
    )

    override fun createSession(request: AiBackendSessionRequest): AiBackendSession {
        val modelId = AiTargetIds.requireLocalModelId(request.targetId)
        val model = findModelById(modelId) ?: throw AiTargetUnavailableException(request.targetId)
        val profiles = backendProfiles()
        val profile = requireBackendProfile(
            request.executionProfileId ?: AiProviderBackendProfile.CPU,
        )
        val target = LiteRtLocalCatalog.target(model, profiles)
        return sessionFactory(target, model, profile)
    }

    companion object {
        const val BACKEND_ID = "litert-local"
    }
}

internal object LiteRtLocalCatalog {
    val capabilities = AiTargetCapabilities(
        streaming = true,
        persistentSession = true,
        structuredJson = true,
        usage = true,
        reasoning = false,
        tools = false,
    )

    fun create(
        document: ModelCatalogDocument,
        backendProfiles: List<AiBackendProfileInfo>,
    ): AiTargetCatalog {
        val normalized = ModelCatalogPolicy.normalize(document)
        return AiTargetCatalog(
            generation = ModelCatalogPolicy.listingGeneration(
                document = normalized,
                capabilityIds = capabilities.toProviderCapabilityIds(),
                maximumContextBytes = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES,
                maximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
                backendProfiles = backendProfiles,
            ),
            defaultTargetId = normalized.selectedModelId?.let(AiTargetIds::local),
            targets = normalized.entries.map { entry -> target(entry, backendProfiles) },
        )
    }

    fun target(
        model: ImportedModel,
        backendProfiles: List<AiBackendProfileInfo>,
    ): AiTarget = target(
        modelId = model.modelId,
        displayName = model.displayName,
        backendProfiles = backendProfiles,
    )

    private fun target(
        entry: ModelCatalogEntry,
        backendProfiles: List<AiBackendProfileInfo>,
    ): AiTarget = target(
        modelId = entry.modelId,
        displayName = entry.displayName,
        backendProfiles = backendProfiles,
    )

    private fun target(
        modelId: String,
        displayName: String,
        backendProfiles: List<AiBackendProfileInfo>,
    ): AiTarget {
        val executionProfiles = backendProfiles.map(AiBackendProfileInfo::toExecutionProfile)
        return AiTarget(
            targetId = AiTargetIds.local(modelId),
            backendId = LiteRtLocalBackend.BACKEND_ID,
            providerId = ThreeStoneAiPlugin.PROVIDER_ID,
            profileId = null,
            modelId = modelId,
            displayName = displayName,
            locality = AiTargetLocality.LOCAL,
            credentialMode = AiTargetCredentialMode.NONE,
            declaredHttpsOrigins = emptyList(),
            configured = true,
            available = executionProfiles.any(AiExecutionProfile::available),
            capabilities = capabilities,
            limits = AiTargetLimits(
                maximumContextBytes = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES,
                maximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
                maximumContextTokens = LiteRtLmEngineFactory.MAXIMUM_CONTEXT_TOKENS,
            ),
            executionProfiles = executionProfiles,
        )
    }
}

internal fun AiTargetCapabilities.toProviderCapabilityIds(): List<String> = buildList {
    if (streaming) add(AiProviderCapabilityId.STREAMING)
    if (structuredJson) add(AiProviderCapabilityId.STRUCTURED_JSON)
    if (usage) add(AiProviderCapabilityId.USAGE)
    if (persistentSession) add(AiProviderCapabilityId.PERSISTENT_SESSION)
    if (reasoning) add(AiProviderCapabilityId.REASONING)
    if (tools) add(AiProviderCapabilityId.TOOLS)
}

internal fun AiBackendProfileInfo.toExecutionProfile() = AiExecutionProfile(
    profileId = profileId,
    available = availability == AiProviderBackendAvailability.AVAILABLE,
    unavailableReason = unavailableReason,
)
