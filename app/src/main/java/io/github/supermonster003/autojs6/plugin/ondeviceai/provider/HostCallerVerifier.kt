package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import io.github.supermonster003.autojs6.plugin.ondeviceai.OnDeviceAiPlugin

internal class HostCallerVerifier(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val providerPackageName = context.applicationContext.packageName

    fun enforceAllowedCaller(): Int = Binder.getCallingUid().also(::enforceAllowedUid)

    fun enforceSessionOwner(expectedUid: Int) {
        val callingUid = Binder.getCallingUid()
        if (callingUid != expectedUid) throw SecurityException("on-device AI session UID does not match its owner")
        enforceAllowedUid(callingUid)
    }

    @Suppress("DEPRECATION")
    private fun enforceAllowedUid(uid: Int) {
        val installedHostUid = try {
            packageManager.getApplicationInfo(OnDeviceAiPlugin.HOST_PACKAGE_NAME, 0).uid
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        HostIdentityPolicy.requireAllowed(
            HostIdentityEvidence(
                callingUid = uid,
                installedHostUid = installedHostUid,
                packagesForCallingUid = packageManager.getPackagesForUid(uid)?.toSet().orEmpty(),
                providerAndHostSignaturesMatch = packageManager.checkSignatures(
                    providerPackageName,
                    OnDeviceAiPlugin.HOST_PACKAGE_NAME,
                ) == PackageManager.SIGNATURE_MATCH,
            ),
        )
    }
}
