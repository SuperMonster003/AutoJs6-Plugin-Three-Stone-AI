package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionPolicyTest {
    @Test
    fun comparesReleaseTagsWithoutTreatingEqualOrOlderVersionsAsUpdates() {
        assertTrue(UpdateVersionPolicy.isNewer("v1.2.0", "1.1.9"))
        assertTrue(UpdateVersionPolicy.isNewer("2.0", "1.99.99"))
        assertFalse(UpdateVersionPolicy.isNewer("v1.1.0", "1.1.0"))
        assertFalse(UpdateVersionPolicy.isNewer("1.0.9", "1.1.0"))
        assertFalse(UpdateVersionPolicy.isNewer("not-a-version", "1.1.0"))
    }

    @Test
    fun stableReleasesSortAfterPrereleasesAtTheSameNumericVersion() {
        assertTrue(UpdateVersionPolicy.isNewer("1.2.0", "1.2.0-beta.2"))
        assertFalse(UpdateVersionPolicy.isNewer("1.2.0-beta.2", "1.2.0"))
        assertTrue(UpdateVersionPolicy.isNewer("1.2.0-rc.2", "1.2.0-beta.9"))
    }
}
