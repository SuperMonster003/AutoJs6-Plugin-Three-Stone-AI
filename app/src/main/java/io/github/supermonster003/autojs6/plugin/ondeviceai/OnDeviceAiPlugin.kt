package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.content.Context
import android.os.Build
import android.os.Bundle
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo

internal object OnDeviceAiPlugin {
    const val PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.ondeviceai"
    const val PROVIDER_ID = "autojs6.on-device-ai"
    const val ENGINE = "on-device-ai"
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

    val capabilities = OnDeviceAiCapabilities(
        supportsStreaming = true,
        supportsReasoning = false,
        supportsTools = false,
        supportsStructuredJson = false,
        supportsUsage = true,
        maximumMessages = MAXIMUM_MESSAGES,
        maximumContentParts = MAXIMUM_CONTENT_PARTS,
        maximumToolDefinitions = 0,
        maximumContextBytes = MAXIMUM_CONTEXT_BYTES,
        maximumOutputBytes = MAXIMUM_OUTPUT_BYTES,
        maximumToolRounds = 0,
        maximumOutstandingToolCalls = 0,
        maximumRequestDescriptors = MAXIMUM_REQUEST_DESCRIPTORS,
        maximumSessionDescriptors = MAXIMUM_SESSION_DESCRIPTORS,
        acceptedTextMimeTypes = listOf(OnDeviceAiMimeType.PLAIN),
        acceptedSchemaMimeTypes = emptyList(),
    )
}

internal fun Context.aiProviderInfo(): AiProviderInfo {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    return AiProviderInfo(
        providerId = OnDeviceAiPlugin.PROVIDER_ID,
        displayName = getString(R.string.app_name),
        implementationVersionName = packageInfo.versionName.orEmpty(),
        implementationVersionCode = versionCode,
        protocolRange = OnDeviceAiProtocol.HOST_PROTOCOL_RANGE,
        locality = AiDataLocality.ON_DEVICE,
        credentialMode = AiCredentialMode.NONE,
        declaredHttpsOrigins = emptyList(),
        supportedAbis = OnDeviceAiPlugin.SUPPORTED_ABIS,
        minimumHostVersionCode = OnDeviceAiPlugin.REQUIRED_HOST_VERSION,
    )
}

internal fun Context.onDeviceAiPluginInfo(): PluginInfo {
    val provider = aiProviderInfo()
    return PluginInfo().apply {
        name = getString(R.string.app_name)
        description = getString(R.string.plugin_description)
        instruction = resources.openRawResource(R.raw.plugin_instruction)
            .bufferedReader()
            .use { it.readText() }
        author = getString(R.string.plugin_author)
        collaborators = null
        versionName = provider.implementationVersionName
        versionCode = provider.implementationVersionCode
        versionDate = getString(R.string.plugin_version_date)
        id = OnDeviceAiPlugin.ENGINE
        engine = OnDeviceAiPlugin.ENGINE
        variant = OnDeviceAiPlugin.VARIANT
        supportedAbis = OnDeviceAiPlugin.SUPPORTED_ABIS.toTypedArray()
        capabilities = Bundle().apply {
            putLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION, OnDeviceAiPlugin.REQUIRED_HOST_VERSION)
        }
    }
}
