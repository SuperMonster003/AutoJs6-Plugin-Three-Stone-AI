package io.github.supermonster003.autojs6.plugin.threestoneai.storage

import org.junit.Assert.assertThrows
import org.junit.Test

class AppPrivatePathGuardTest {
    @Test
    fun trustedAndroidDataRootAliasDoesNotMakeItsChildUnsafe() {
        AppPrivatePathGuard.validateDirectChild(
            absoluteParentPath = "/data/user/0/example/files",
            expectedAbsoluteParentPath = "/data/user/0/example/files",
            canonicalParentPath = "/data/data/example/files",
            expectedCanonicalParentPath = "/data/data/example/files",
            symbolicLink = false,
            label = "Test path",
        )
    }

    @Test
    fun absoluteParentEscapeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AppPrivatePathGuard.validateDirectChild(
                absoluteParentPath = "/data/user/0/example/cache",
                expectedAbsoluteParentPath = "/data/user/0/example/files",
                canonicalParentPath = "/data/data/example/cache",
                expectedCanonicalParentPath = "/data/data/example/files",
                symbolicLink = false,
                label = "Test path",
            )
        }
    }

    @Test
    fun canonicalEscapeThroughAnAncestorLinkIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AppPrivatePathGuard.validateDirectChild(
                absoluteParentPath = "/data/user/0/example/files",
                expectedAbsoluteParentPath = "/data/user/0/example/files",
                canonicalParentPath = "/data/local/tmp",
                expectedCanonicalParentPath = "/data/data/example/files",
                symbolicLink = false,
                label = "Test path",
            )
        }
    }

    @Test
    fun directSymbolicLinkIsRejectedEvenWhenItTargetsASibling() {
        assertThrows(IllegalArgumentException::class.java) {
            AppPrivatePathGuard.validateDirectChild(
                absoluteParentPath = "/data/user/0/example/files",
                expectedAbsoluteParentPath = "/data/user/0/example/files",
                canonicalParentPath = "/data/data/example/files",
                expectedCanonicalParentPath = "/data/data/example/files",
                symbolicLink = true,
                label = "Test path",
            )
        }
    }
}
