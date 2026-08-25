package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.toProviderCapabilityIds
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderQuotaSnapshot
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiTargetControlId

internal object TargetRequestPolicy {
    fun requireSupported(
        target: AiTarget,
        request: AiProviderRequest,
        quota: AiProviderQuotaSnapshot,
    ) {
        require(target.targetId == request.targetId) { "AI request target identity changed" }
        if (!target.configured || !target.available) throw AiTargetUnavailableException(target.targetId)

        val targetCapabilities = target.capabilities.toProviderCapabilityIds().toSet()
        require(requiredCapabilities(request).all { capability -> capability in targetCapabilities }) {
            "AI target does not support every required capability"
        }
        val supportedControls = target.toProviderSupportedControls().toSet()
        request.options.maximumOutputTokens?.let {
            require(AiTargetControlId.MAXIMUM_OUTPUT_TOKENS in supportedControls) {
                "AI target does not support maximum output tokens"
            }
        }
        request.options.temperature?.let {
            require(AiTargetControlId.TEMPERATURE in supportedControls) {
                "AI target does not support temperature"
            }
        }
        request.options.topK?.let {
            require(AiTargetControlId.TOP_K in supportedControls) {
                "AI target does not support topK"
            }
        }
        request.options.topP?.let {
            require(AiTargetControlId.TOP_P in supportedControls) {
                "AI target does not support topP"
            }
        }
        request.options.responseSchema?.let {
            require(AiTargetControlId.RESPONSE_JSON_SCHEMA in supportedControls) {
                "AI target does not support a response JSON Schema"
            }
        }
        target.limits.maximumContextBytes?.let { maximum ->
            require(quota.inputBytes <= maximum) { "Input bytes exceed the AI target limit" }
        }
        target.limits.maximumOutputBytes?.let { maximum ->
            require(request.options.maximumOutputBytes <= maximum) {
                "Requested output bytes exceed the AI target limit"
            }
        }
        target.limits.maximumOutputTokens?.let { maximum ->
            request.options.maximumOutputTokens?.let { requested ->
                require(requested <= maximum.toLong()) {
                    "Requested output tokens exceed the AI target limit"
                }
            }
        }
        request.options.backendProfile?.let { requestedProfile ->
            val profile = target.executionProfiles.singleOrNull { it.profileId == requestedProfile }
                ?: throw IllegalArgumentException("AI target does not support the requested backend profile")
            if (!profile.available) throw AiTargetUnavailableException(target.targetId)
        }
        if (target.executionProfiles.isEmpty()) {
            require(request.options.backendProfile == null) {
                "AI target does not expose local backend profiles"
            }
        }
    }

    private fun requiredCapabilities(request: AiProviderRequest): Set<String> = buildSet {
        addAll(request.options.requiredCapabilityIds)
        if (request.options.stream) add(AiProviderCapabilityId.STREAMING)
        if (request.options.includeReasoning) add(AiProviderCapabilityId.REASONING)
        if (request.options.structuredJson) add(AiProviderCapabilityId.STRUCTURED_JSON)
        if (request.options.reportUsage) add(AiProviderCapabilityId.USAGE)
        if (request.options.persistentSession) add(AiProviderCapabilityId.PERSISTENT_SESSION)
        if (request.tools.isNotEmpty() || request.options.maximumToolRounds > 0) {
            add(AiProviderCapabilityId.TOOLS)
        }
    }
}
