package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.profile.ConfiguredOnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileCredentialAccess
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfileRegistrySnapshot
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Optional HTTPS execution boundary shared by every supported online protocol. */
internal interface OnlineAiExecution {
    val available: Boolean

    fun supports(profile: OnlineAiProfile): Boolean

    fun capabilities(profile: OnlineAiProfile): AiTargetCapabilities

    fun limits(profile: OnlineAiProfile): AiTargetLimits

    fun createSession(
        target: AiTarget,
        profile: OnlineAiProfile,
        credentialAccess: OnlineAiProfileCredentialAccess,
    ): AiBackendSession
}

/** Maps non-secret online profiles into the unified target namespace. */
internal class OnlineAiBackend(
    private val registry: OnlineAiProfileRegistry,
    private val execution: OnlineAiExecution? = null,
) : AiBackend {
    override val backendId: String = BACKEND_ID

    override fun ownsTarget(targetId: String): Boolean =
        runCatching { AiTargetIds.requireProfileId(targetId) }.isSuccess

    override fun catalog(): AiTargetCatalog {
        val snapshot = registry.snapshot()
        val targets = snapshot.profiles
            .flatMap(::targets)
        return AiTargetCatalog(
            generation = generation(snapshot, targets),
            defaultTargetId = snapshot.defaultProfileId?.let { profileId ->
                snapshot.profiles.single { state -> state.profile.profileId == profileId }
                    .profile
                    .let { profile ->
                        AiTargetIds.profileModel(
                            profile.profileId,
                            profile.modelIds.first(),
                            profile.modelId,
                        )
                    }
            },
            targets = targets,
        )
    }

    override fun createSession(request: AiBackendSessionRequest): AiBackendSession {
        require(request.executionProfileId == null) {
            "Online AI targets do not use local execution profiles"
        }
        val profileId = AiTargetIds.requireProfileId(request.targetId)
        val configured = registry.snapshot().profiles.singleOrNull { state ->
            state.profile.profileId == profileId
        } ?: throw AiTargetUnavailableException(request.targetId)
        val selectedTarget = targets(configured).singleOrNull { target ->
            target.targetId == request.targetId
        } ?: throw AiTargetUnavailableException(request.targetId)
        val effectiveProfile = configured.profile.copy(modelId = selectedTarget.modelId)
        val executor = execution?.takeIf { candidate ->
            candidate.available && candidate.supports(effectiveProfile)
        }
            ?: throw AiTargetUnavailableException(request.targetId)
        if (!configured.configured) throw AiTargetUnavailableException(request.targetId)
        return executor.createSession(
            target = selectedTarget,
            profile = effectiveProfile,
            credentialAccess = registry.bindCredential(configured.profile),
        )
    }

    private fun targets(configured: ConfiguredOnlineAiProfile): List<AiTarget> =
        configured.profile.modelIds.map { modelId -> target(configured, modelId) }

    private fun target(configured: ConfiguredOnlineAiProfile, modelId: String): AiTarget {
        val effectiveProfile = configured.profile.copy(modelId = modelId)
        val executor = execution?.takeIf { candidate ->
            candidate.available && candidate.supports(effectiveProfile)
        }
        return AiTarget(
            targetId = AiTargetIds.profileModel(
                configured.profile.profileId,
                configured.profile.modelIds.first(),
                modelId,
            ),
            backendId = backendId,
            providerId = configured.profile.provider.providerId,
            profileId = configured.profile.profileId,
            modelId = modelId,
            displayName = configured.profile.displayName,
            locality = AiTargetLocality.REMOTE,
            credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
            declaredHttpsOrigins = listOf(configured.profile.declaredHttpsOrigin),
            configured = configured.configured,
            available = configured.configured && executor != null,
            capabilities = executor?.capabilities(effectiveProfile) ?: UNIMPLEMENTED_CAPABILITIES,
            limits = executor?.limits(effectiveProfile) ?: UNKNOWN_LIMITS,
            executionProfiles = emptyList(),
        )
    }

    private fun generation(
        snapshot: OnlineAiProfileRegistrySnapshot,
        targets: List<AiTarget>,
    ): String {
        val input = ByteArrayOutputStream()
        DataOutputStream(input).use { output ->
            output.writeInt(CATALOG_GENERATION_SCHEMA)
            output.writeLong(snapshot.revision)
            output.writeInt(targets.size)
            targets.forEach { target ->
                output.writeLengthPrefixed(target.targetId)
                output.writeBoolean(target.configured)
                output.writeBoolean(target.available)
                output.writeBoolean(target.capabilities.streaming)
                output.writeBoolean(target.capabilities.persistentSession)
                output.writeBoolean(target.capabilities.structuredJson)
                output.writeBoolean(target.capabilities.usage)
                output.writeBoolean(target.capabilities.reasoning)
                output.writeBoolean(target.capabilities.tools)
                output.writeNullableLong(target.limits.maximumContextBytes)
                output.writeNullableLong(target.limits.maximumOutputBytes)
                output.writeNullableInt(target.limits.maximumOutputTokens)
                output.writeNullableInt(target.limits.maximumContextTokens)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return "online.${digest.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }.take(32)}"
    }

    private fun DataOutputStream.writeLengthPrefixed(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        value?.let(::writeLong)
    }

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        value?.let(::writeInt)
    }

    companion object {
        const val BACKEND_ID = "online"
        private const val CATALOG_GENERATION_SCHEMA = 2

        private val UNIMPLEMENTED_CAPABILITIES = AiTargetCapabilities(
            streaming = false,
            persistentSession = false,
            structuredJson = false,
            usage = false,
            reasoning = false,
            tools = false,
        )
        private val UNKNOWN_LIMITS = AiTargetLimits(
            maximumContextBytes = null,
            maximumOutputBytes = null,
            maximumOutputTokens = null,
            maximumContextTokens = null,
        )
    }
}
