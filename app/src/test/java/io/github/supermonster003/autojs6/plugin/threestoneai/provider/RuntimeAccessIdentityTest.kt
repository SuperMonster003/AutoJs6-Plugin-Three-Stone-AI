package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import org.junit.Assert.*
import org.junit.Test

class RuntimeAccessIdentityTest {
    private val approved = RuntimeAccessIdentity("example.app", 12345, 1700000000L, listOf("signer"))
    @Test fun sameInstallationRetainsConsent() { assertTrue(approved.accepts(approved.copy())) }
    @Test fun reinstallUidReuseAndSignerChangesNeverInheritConsent() {
        assertFalse(approved.accepts(approved.copy(packageName = "example.other")))
        assertFalse(approved.accepts(approved.copy(uid = 12346)))
        assertFalse(approved.accepts(approved.copy(firstInstallTime = 1700000001L)))
        assertFalse(approved.accepts(approved.copy(signers = listOf("other"))))
        assertFalse(approved.accepts(null))
    }
    @Test fun incompleteIdentityCannotAuthorizeEvenItself() {
        val missingSigner = approved.copy(signers = emptyList())
        assertFalse(missingSigner.accepts(missingSigner))
    }
}
