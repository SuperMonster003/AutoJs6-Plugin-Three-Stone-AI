package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Access UI, status provider and runtime service all live in :provider. */
internal class RuntimeAccessStore(context: Context) {
    private val manager = context.applicationContext.packageManager
    private val preferences = context.applicationContext.getSharedPreferences("ai_runtime_access", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    fun identity(packageName: String): RuntimeAccessIdentity? = runCatching {
        val info = manager.getPackageInfo(packageName, if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        RuntimeAccessIdentity(packageName, requireNotNull(info.applicationInfo).uid, info.firstInstallTime,
            requireNotNull(signers).map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            }.sorted())
    }.getOrNull()

    fun isGranted(packageName: String, uid: Int): Boolean {
        val current = identity(packageName)?.takeIf { it.uid == uid } ?: return false
        val saved = read(packageName) ?: return false
        return saved.accepts(current)
    }

    fun isGranted(uid: Int): Boolean = manager.getPackagesForUid(uid).orEmpty().any { isGranted(it, uid) }

    fun grant(expected: RuntimeAccessIdentity): Boolean {
        if (!expected.accepts(identity(expected.packageName))) return false
        return preferences.edit().putString(expected.packageName, JSONObject()
            .put("uid", expected.uid).put("firstInstallTime", expected.firstInstallTime)
            .put("signers", JSONArray(expected.signers)).toString()).commit()
    }

    fun revoke(packageName: String) {
        val saved = read(packageName)
        check(preferences.edit().remove(packageName).commit())
        saved?.let { RuntimeAccessSessions.revoke(it.uid) }
    }

    fun grantedPackages(): List<RuntimeAccessIdentity> = preferences.all.keys.mapNotNull { packageName ->
        val current = identity(packageName) ?: return@mapNotNull null
        current.takeIf { read(packageName)?.accepts(it) == true }
    }.sortedBy { it.packageName }

    private fun read(packageName: String): RuntimeAccessIdentity? = runCatching {
        val row = JSONObject(preferences.getString(packageName, null) ?: return null)
        val signers = row.getJSONArray("signers")
        RuntimeAccessIdentity(packageName, row.getInt("uid"), row.getLong("firstInstallTime"),
            (0 until signers.length()).map(signers::getString))
    }.getOrNull()
}

internal object RuntimeAccessSessions {
    private val sessions = ConcurrentHashMap<RemoteThreeStoneAiSession, Int>()
    fun register(session: RemoteThreeStoneAiSession, uid: Int) { sessions[session] = uid }
    fun remove(session: RemoteThreeStoneAiSession) { sessions.remove(session) }
    fun revoke(uid: Int) { sessions.forEach { (session, owner) -> if (owner == uid) session.accessRevoked() } }
}
