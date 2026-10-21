package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeCallerPolicyTest {
    @Test fun runtimeAccessNeedsAUserGrantAndTheOriginalUid() {
        RuntimeCallerPolicy.requireAllowed(10001, 10001, granted = true, cleanup = false)
        assertThrows(SecurityException::class.java) { RuntimeCallerPolicy.requireAllowed(10001, 10001, false, false) }
        assertThrows(SecurityException::class.java) { RuntimeCallerPolicy.requireAllowed(10002, 10001, true, false) }
    }
    @Test fun revocationStillAllowsOwnerCleanupButNeverCrossUidCleanup() {
        RuntimeCallerPolicy.requireAllowed(10001, 10001, granted = false, cleanup = true)
        assertThrows(SecurityException::class.java) { RuntimeCallerPolicy.requireAllowed(10002, 10001, true, true) }
    }
}
