package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetUnavailableException
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.provider.api.AiContentPart
import org.autojs.plugin.ai.provider.api.AiGenerationOptions
import org.autojs.plugin.ai.provider.api.AiMessage
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderQuotaSnapshot
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.junit.Assert.assertThrows
import org.junit.Test

class TargetRequestPolicyTest {
    @Test
    fun configuredRemoteTargetAcceptsARequestWithoutLocalBackendProfile() {
        TargetRequestPolicy.requireSupported(remoteTarget(), request(), quota())
    }

    @Test
    fun remoteTargetRejectsLocalBackendProfileAndUnsupportedCapability() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetRequestPolicy.requireSupported(
                remoteTarget(),
                request(backendProfile = "cpu"),
                quota(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetRequestPolicy.requireSupported(
                remoteTarget(capabilities = capabilities.copy(streaming = false)),
                request(stream = true),
                quota(),
            )
        }
    }

    @Test
    fun providerSpecificUnsupportedControlsFailInsteadOfBeingIgnored() {
        TargetRequestPolicy.requireSupported(
            remoteTarget(providerId = "openai-compatible"),
            request(topK = 40),
            quota(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            TargetRequestPolicy.requireSupported(
                remoteTarget(providerId = "openai"),
                request(topK = 40),
                quota(),
            )
        }
    }

    @Test
    fun unavailableTargetAndTargetSpecificLimitsFailClosed() {
        assertThrows(AiTargetUnavailableException::class.java) {
            TargetRequestPolicy.requireSupported(
                remoteTarget(configured = false, available = false),
                request(),
                quota(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetRequestPolicy.requireSupported(
                remoteTarget(limits = limits.copy(maximumContextBytes = 3)),
                request(),
                quota(inputBytes = 4),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TargetRequestPolicy.requireSupported(
                remoteTarget(limits = limits.copy(maximumOutputBytes = 3)),
                request(maximumOutputBytes = 4),
                quota(),
            )
        }
    }

    private fun request(
        stream: Boolean = false,
        maximumOutputBytes: Long = 4,
        backendProfile: String? = null,
        topK: Int? = null,
    ) = AiProviderRequest(
        requestId = "request-target-policy",
        protocolVersion = AiProviderProtocol.PROTOCOL_V2,
        providerId = ThreeStoneAiPlugin.PROVIDER_ID,
        targetId = "profile:profile-a",
        messages = listOf(
            AiMessage(
                role = AiMessageRole.USER,
                parts = listOf(
                    AiContentPart(
                        AiPayloadReference(
                            mimeType = AiProviderMimeType.PLAIN,
                            declaredLengthBytes = 4,
                            inlineBytes = "test".toByteArray(),
                            charset = "utf-8",
                        ),
                    ),
                ),
            ),
        ),
        options = AiGenerationOptions(
            stream = stream,
            includeReasoning = false,
            structuredJson = false,
            reportUsage = false,
            maximumOutputBytes = maximumOutputBytes,
            timeoutMillis = 30_000,
            backendProfile = backendProfile,
            topK = topK,
        ),
    )

    private fun quota(inputBytes: Long = 4) = AiProviderQuotaSnapshot(
        messageCount = 1,
        contentPartCount = 1,
        toolDefinitionCount = 0,
        inputBytes = inputBytes,
        toolSchemaBytes = 0,
        descriptorCount = 0,
        effectiveMaximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
        effectiveMaximumToolRounds = 0,
    )

    private fun remoteTarget(
        configured: Boolean = true,
        available: Boolean = true,
        providerId: String = "openai-compatible",
        capabilities: AiTargetCapabilities = Companion.capabilities,
        limits: AiTargetLimits = Companion.limits,
    ) = AiTarget(
        targetId = "profile:profile-a",
        backendId = "online",
        providerId = providerId,
        profileId = "profile-a",
        modelId = "remote-model",
        displayName = "Remote",
        locality = AiTargetLocality.REMOTE,
        credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf("https://api.example.com"),
        configured = configured,
        available = available,
        capabilities = capabilities,
        limits = limits,
        executionProfiles = emptyList(),
    )

    private companion object {
        val capabilities = AiTargetCapabilities(
            streaming = true,
            persistentSession = true,
            structuredJson = true,
            usage = true,
            reasoning = false,
            tools = false,
        )
        val limits = AiTargetLimits(
            maximumContextBytes = ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES,
            maximumOutputBytes = ThreeStoneAiPlugin.MAXIMUM_OUTPUT_BYTES,
        )
    }
}
