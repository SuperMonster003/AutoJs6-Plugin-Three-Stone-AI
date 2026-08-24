package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin

internal data class HostIdentityEvidence(
    val callingUid: Int,
    val installedHostUid: Int?,
    val packagesForCallingUid: Set<String>,
    val providerAndHostSignaturesMatch: Boolean,
)

internal enum class HostIdentityDecision {
    ALLOW,
    HOST_PACKAGE_NOT_OWNED,
    HOST_NOT_INSTALLED,
    HOST_UID_MISMATCH,
    SIGNATURE_MISMATCH,
}

internal object HostIdentityPolicy {
    fun evaluate(evidence: HostIdentityEvidence): HostIdentityDecision = when {
        ThreeStoneAiPlugin.HOST_PACKAGE_NAME !in evidence.packagesForCallingUid ->
            HostIdentityDecision.HOST_PACKAGE_NOT_OWNED
        evidence.installedHostUid == null -> HostIdentityDecision.HOST_NOT_INSTALLED
        evidence.callingUid != evidence.installedHostUid -> HostIdentityDecision.HOST_UID_MISMATCH
        !evidence.providerAndHostSignaturesMatch -> HostIdentityDecision.SIGNATURE_MISMATCH
        else -> HostIdentityDecision.ALLOW
    }

    fun requireAllowed(evidence: HostIdentityEvidence) {
        val decision = evaluate(evidence)
        if (decision != HostIdentityDecision.ALLOW) {
            throw SecurityException("local AI provider caller rejected: $decision")
        }
    }
}
