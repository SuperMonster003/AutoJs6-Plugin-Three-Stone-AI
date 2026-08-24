package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import org.autojs.plugin.ai.common.api.AiCommonLimits

internal object BinderInputPolicy {
    fun requireEnvelopeSize(byteCount: Int) {
        require(byteCount in 1..AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES) {
            "local AI Binder envelope size is invalid"
        }
    }

    fun requireRequestDescriptorCount(descriptorCount: Int, providerMaximum: Int) {
        require(providerMaximum in 0..AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST)
        require(descriptorCount in 0..minOf(providerMaximum, AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST)) {
            "local AI request descriptor count exceeds the protocol limit"
        }
    }

    fun requireSessionDescriptorCount(descriptorCount: Int, providerMaximum: Int) {
        require(providerMaximum in 0..AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION)
        require(descriptorCount in 0..minOf(providerMaximum, AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION)) {
            "local AI session descriptor count exceeds the protocol limit"
        }
    }
}
