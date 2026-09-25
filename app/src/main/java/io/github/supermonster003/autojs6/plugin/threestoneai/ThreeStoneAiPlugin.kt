package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.os.Build
import android.os.Bundle
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.provider.api.AiProviderCapabilities
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo

internal object ThreeStoneAiPlugin {
    const val PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.threestoneai"
    const val PROVIDER_ID = "autojs6.three-stone-ai"
    const val ENGINE = "three-stone-ai"
    const val VARIANT = "default"
    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val REQUIRED_HOST_VERSION = 5276L
    const val MAXIMUM_CONTEXT_BYTES = 256L * 1024L
    const val MAXIMUM_OUTPUT_BYTES = 64L * 1024L
    const val MAXIMUM_MESSAGES = 64
    const val MAXIMUM_CONTENT_PARTS = 128
    const val MAXIMUM_REQUEST_DESCRIPTORS = 16
    const val MAXIMUM_SESSION_DESCRIPTORS = 16

    val SUPPORTED_ABIS = listOf("arm64-v8a", "x86_64")

    val capabilities = AiProviderCapabilities(
        supportsStreaming = true,
        supportsReasoning = false,
        supportsTools = true,
        supportsStructuredJson = true,
        supportsUsage = true,
        supportsPersistentSessions = true,
        maximumMessages = MAXIMUM_MESSAGES,
        maximumContentParts = MAXIMUM_CONTENT_PARTS,
        maximumToolDefinitions = AiProviderLimits.MAX_TOOL_DEFINITIONS,
        maximumContextBytes = MAXIMUM_CONTEXT_BYTES,
        maximumOutputBytes = MAXIMUM_OUTPUT_BYTES,
        maximumToolRounds = AiProviderLimits.MAX_TOOL_ROUNDS,
        maximumOutstandingToolCalls = AiProviderLimits.MAX_OUTSTANDING_TOOL_CALLS,
        maximumRequestDescriptors = MAXIMUM_REQUEST_DESCRIPTORS,
        maximumSessionDescriptors = MAXIMUM_SESSION_DESCRIPTORS,
        acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON),
        acceptedSchemaMimeTypes = listOf(AiProviderMimeType.JSON),
        vision = org.autojs.plugin.ai.provider.api.AiVisionCapabilities(),
    )
}

internal fun Context.aiProviderInfo(catalog: AiTargetCatalog): AiProviderInfo {
    val implementation = pluginImplementationVersion()
    val declaration = ThreeStoneAiProviderDeclarationPolicy.from(catalog)
    return AiProviderInfo(
        providerId = ThreeStoneAiPlugin.PROVIDER_ID,
        displayName = getString(R.string.app_name),
        implementationVersionName = implementation.name,
        implementationVersionCode = implementation.code,
        protocolRange = AiProviderProtocol.HOST_PROTOCOL_RANGE,
        locality = declaration.locality,
        credentialMode = declaration.credentialMode,
        declaredHttpsOrigins = declaration.declaredHttpsOrigins,
        supportedAbis = ThreeStoneAiPlugin.SUPPORTED_ABIS,
        minimumHostVersionCode = ThreeStoneAiPlugin.REQUIRED_HOST_VERSION,
    )
}

internal data class ThreeStoneAiProviderDeclaration(
    val locality: Int,
    val credentialMode: Int,
    val declaredHttpsOrigins: List<String>,
)

internal object ThreeStoneAiProviderDeclarationPolicy {
    fun from(catalog: AiTargetCatalog): ThreeStoneAiProviderDeclaration {
        val remoteTargets = catalog.targets.filter { target -> target.locality == AiTargetLocality.REMOTE }
        val hasRemoteTargets = remoteTargets.isNotEmpty()
        return ThreeStoneAiProviderDeclaration(
            locality = if (hasRemoteTargets) AiDataLocality.HYBRID else AiDataLocality.ON_DEVICE,
            credentialMode = if (hasRemoteTargets) AiCredentialMode.PLUGIN_MANAGED else AiCredentialMode.NONE,
            declaredHttpsOrigins = remoteTargets
                .flatMap { target -> target.declaredHttpsOrigins }
                .distinct()
                .sorted(),
        )
    }
}

private fun Context.pluginImplementationVersion(): PluginImplementationVersion {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    return PluginImplementationVersion(packageInfo.versionName.orEmpty(), versionCode)
}

internal fun Context.threeStoneAiPluginInfo(): PluginInfo {
    val implementation = pluginImplementationVersion()
    return PluginInfo().apply {
        name = getString(R.string.app_name)
        description = getString(R.string.plugin_description)
        instruction = resources.openRawResource(R.raw.plugin_instruction)
            .bufferedReader()
            .use { it.readText() }
        author = getString(R.string.plugin_author)
        collaborators = null
        versionName = implementation.name
        versionCode = implementation.code
        versionDate = getString(R.string.plugin_version_date)
        id = ThreeStoneAiPlugin.ENGINE
        engine = ThreeStoneAiPlugin.ENGINE
        variant = ThreeStoneAiPlugin.VARIANT
        supportedAbis = ThreeStoneAiPlugin.SUPPORTED_ABIS.toTypedArray()
        capabilities = Bundle().apply {
            putLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION, ThreeStoneAiPlugin.REQUIRED_HOST_VERSION)
        }
    }
}

private data class PluginImplementationVersion(val name: String, val code: Long)
