package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HostIdentityPolicyTest {
    @Test
    fun requiresOwnedPackageUidAndMatchingSignature() {
        val allowed = HostIdentityEvidence(
            callingUid = 10001,
            installedHostUid = 10001,
            packagesForCallingUid = setOf(ThreeStoneAiPlugin.HOST_PACKAGE_NAME),
            providerAndHostSignaturesMatch = true,
        )
        assertEquals(HostIdentityDecision.ALLOW, HostIdentityPolicy.evaluate(allowed))
        assertEquals(
            HostIdentityDecision.HOST_UID_MISMATCH,
            HostIdentityPolicy.evaluate(allowed.copy(installedHostUid = 10002)),
        )
        assertEquals(
            HostIdentityDecision.SIGNATURE_MISMATCH,
            HostIdentityPolicy.evaluate(allowed.copy(providerAndHostSignaturesMatch = false)),
        )
        assertThrows(SecurityException::class.java) {
            HostIdentityPolicy.requireAllowed(allowed.copy(packagesForCallingUid = emptySet()))
        }
    }
}
