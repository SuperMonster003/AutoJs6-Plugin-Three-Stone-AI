package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder

/** Separately signed applications require the user's Android runtime permission. */
class ThreeStoneAiRuntimeService : ThreeStoneAiProviderService() {
    override val runtimeEntry = true
}

internal class RuntimeCallerVerifier(private val context: Context) : ProviderCallerVerifier {
    override fun enforceAllowedCaller(): Int = Binder.getCallingUid().also { uid ->
        RuntimeCallerPolicy.requireAllowed(uid, uid, granted(uid), cleanup = false)
    }

    override fun enforceSessionOwner(expectedUid: Int) {
        val uid = Binder.getCallingUid()
        RuntimeCallerPolicy.requireAllowed(uid, expectedUid, granted(uid), cleanup = false)
    }

    override fun enforceSessionCleanup(expectedUid: Int) {
        // Revocation must not prevent the owner from cancelling already admitted work.
        RuntimeCallerPolicy.requireAllowed(Binder.getCallingUid(), expectedUid, granted = false, cleanup = true)
    }

    private fun granted(uid: Int) = context.checkPermission(PERMISSION, -1, uid) == PackageManager.PERMISSION_GRANTED

    companion object { const val PERMISSION = "org.autojs.permission.AI_RUNTIME" }
}

internal object RuntimeCallerPolicy {
    fun requireAllowed(uid: Int, expectedUid: Int, granted: Boolean, cleanup: Boolean) {
        if (uid != expectedUid || !cleanup && !granted) throw SecurityException("AI access is not authorized for this caller")
    }
}
