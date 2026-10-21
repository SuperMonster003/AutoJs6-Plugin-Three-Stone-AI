package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/** Only the caller's own authorization bit is public; account/model data is never returned. */
class RuntimeAiAccessProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == "hasAccess" && extras == null)
        val app = requireNotNull(context)
        app.enforceCallingOrSelfPermission(RuntimeCallerVerifier.PERMISSION, "AI runtime permission is required")
        val uid = Binder.getCallingUid()
        val packageName = requireNotNull(arg)
        require(packageName in app.packageManager.getPackagesForUid(uid).orEmpty()) { "AI access query belongs to another UID" }
        return Bundle().apply { putBoolean("authorized", RuntimeAccessStore(app).isGranted(packageName, uid)) }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
