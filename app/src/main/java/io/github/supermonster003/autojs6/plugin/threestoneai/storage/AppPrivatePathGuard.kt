package io.github.supermonster003.autojs6.plugin.threestoneai.storage

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Validates an app-owned direct child without rejecting trusted aliases in the Android data root.
 *
 * Android 9 and some other releases expose [android.content.Context.getFilesDir] below
 * `/data/user/0`, while canonical paths resolve that system-managed alias to `/data/data`. Only the
 * child itself must be link-free; canonical containment is evaluated relative to the equally
 * canonicalized trusted parent.
 */
internal object AppPrivatePathGuard {
    fun requireDirectChild(
        child: File,
        trustedParent: File,
        label: String,
    ) {
        val absoluteChild = child.absoluteFile
        val absoluteParent = trustedParent.absoluteFile
        val canonicalChild = child.canonicalFile
        val canonicalParent = trustedParent.canonicalFile
        validateDirectChild(
            absoluteParentPath = absoluteChild.parentFile?.path,
            expectedAbsoluteParentPath = absoluteParent.path,
            canonicalParentPath = canonicalChild.parentFile?.path,
            expectedCanonicalParentPath = canonicalParent.path,
            symbolicLink = isSymbolicLink(child),
            label = label,
        )
    }

    internal fun validateDirectChild(
        absoluteParentPath: String?,
        expectedAbsoluteParentPath: String,
        canonicalParentPath: String?,
        expectedCanonicalParentPath: String,
        symbolicLink: Boolean,
        label: String,
    ) {
        require(absoluteParentPath == expectedAbsoluteParentPath) {
            "$label escaped its parent"
        }
        require(!symbolicLink) { "$label must not use a link" }
        require(canonicalParentPath == expectedCanonicalParentPath) {
            "$label escaped its parent through a link"
        }
    }

    private fun isSymbolicLink(file: File): Boolean = try {
        OsConstants.S_ISLNK(Os.lstat(file.absolutePath).st_mode)
    } catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) false else throw error
    }
}
