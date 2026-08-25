package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreeStoneAiProviderDeclarationPolicyTest {
    @Test
    fun localCatalogDeclaresAnOnDeviceCredentialFreeProvider() {
        val declaration = ThreeStoneAiProviderDeclarationPolicy.from(catalog(localTarget()))

        assertEquals(AiDataLocality.ON_DEVICE, declaration.locality)
        assertEquals(AiCredentialMode.NONE, declaration.credentialMode)
        assertEquals(emptyList<String>(), declaration.declaredHttpsOrigins)
    }

    @Test
    fun anyRemoteTargetUpgradesTheAggregateToHybridPluginManagedOrigins() {
        val declaration = ThreeStoneAiProviderDeclarationPolicy.from(
            catalog(
                localTarget(),
                remoteTarget("profile:a", "https://z.example"),
                remoteTarget("profile:b", "https://a.example"),
                remoteTarget("profile:c", "https://z.example"),
            ),
        )

        assertEquals(AiDataLocality.HYBRID, declaration.locality)
        assertEquals(AiCredentialMode.PLUGIN_MANAGED, declaration.credentialMode)
        assertEquals(
            listOf("https://a.example", "https://z.example"),
            declaration.declaredHttpsOrigins,
        )
    }

    private fun catalog(vararg targets: AiTarget) = AiTargetCatalog(
        generation = "generation",
        defaultTargetId = targets.firstOrNull()?.targetId,
        targets = targets.toList(),
    )

    private fun localTarget() = AiTarget(
        targetId = "local:model",
        backendId = "local",
        providerId = ThreeStoneAiPlugin.PROVIDER_ID,
        profileId = null,
        modelId = "model",
        displayName = "Local",
        locality = AiTargetLocality.LOCAL,
        credentialMode = AiTargetCredentialMode.NONE,
        declaredHttpsOrigins = emptyList(),
        configured = true,
        available = true,
        capabilities = capabilities,
        limits = limits,
        executionProfiles = emptyList(),
    )

    private fun remoteTarget(targetId: String, origin: String) = AiTarget(
        targetId = targetId,
        backendId = "online",
        providerId = "openai-compatible",
        profileId = targetId.removePrefix("profile:"),
        modelId = "remote-model",
        displayName = "Remote",
        locality = AiTargetLocality.REMOTE,
        credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf(origin),
        configured = false,
        available = false,
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
