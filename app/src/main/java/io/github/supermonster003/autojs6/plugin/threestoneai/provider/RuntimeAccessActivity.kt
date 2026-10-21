package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ConfiguredActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.R
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.*

/** The framework supplies the result caller; Intent extras cannot select an authorization target. */
class RuntimeAccessActivity : ConfiguredActivity() {
    private var dialog: AlertDialog? = null
    override fun hasUnconfirmedDialog() = dialog?.isShowing == true
    override fun defersAppearanceRecreation() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val caller = callingPackage ?: return finish()
        if (intent.data != null || intent.clipData != null || intent.extras != null ||
            packageManager.checkPermission(RuntimeCallerVerifier.PERMISSION, caller) != PackageManager.PERMISSION_GRANTED) return finish()
        val store = RuntimeAccessStore(this)
        val identity = store.identity(caller) ?: return finish()
        val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(caller, 0)).toString()
        setContentView(buildScaffold(R.string.ai_runtime_permission_label).root)
        dialog = materialDialog().setTitle(R.string.ai_runtime_permission_label)
            .setMessage(getString(R.string.ai_runtime_request_app, label, caller) + "\n\n" + getString(R.string.ai_runtime_permission_description))
            .setNegativeButton(R.string.ai_runtime_deny) { _, _ -> finish() }
            .setPositiveButton(R.string.ai_runtime_allow) { _, _ ->
                if (store.grant(identity)) setResult(Activity.RESULT_OK)
                finish()
            }.setOnCancelListener { finish() }.show().also {
                tintDialogButtons(it)
                it.getButton(AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured = true
                it.getButton(AlertDialog.BUTTON_NEGATIVE).filterTouchesWhenObscured = true
            }
    }

    override fun onDestroy() {
        dialog?.setOnCancelListener(null)
        dialog?.dismiss(); dialog = null
        super.onDestroy()
    }
}

/** Signature-protected settings entry, also reachable from the plugin's own settings page. */
class RuntimeAccessManagementActivity : ConfiguredActivity() {
    private var dialog: AlertDialog? = null
    override fun hasUnconfirmedDialog() = dialog?.isShowing == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val store = RuntimeAccessStore(this)
        val scaffold = buildScaffold(R.string.ai_runtime_access_title)
        scaffold.root.id = R.id.ai_runtime_access_root
        scaffold.content.addView(sectionHeader(R.string.ai_runtime_access_summary))
        val entries = store.grantedPackages()
        if (entries.isEmpty()) scaffold.content.addView(TextView(this).apply {
            id = R.id.ai_runtime_access_empty
            text = getString(R.string.ai_runtime_access_empty)
            textSize = 16f
            setTextColor(appPalette.primaryText)
            setPaddingRelative(uiDp(24), uiDp(16), uiDp(24), uiDp(16))
        })
        entries.forEach { identity ->
            val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(identity.packageName, 0)).toString()
            scaffold.content.addView(settingRow(label, identity.packageName, R.drawable.ic_person_24, onClick = {
                dialog = confirmDialog(getString(R.string.ai_runtime_remove_title),
                    getString(R.string.ai_runtime_remove_message, label, identity.packageName), R.string.ai_runtime_remove,
                    destructive = true) { store.revoke(identity.packageName); dialog = null; render() }.also {
                        it.getButton(AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured = true
                    }
            }).view)
        }
        setContentView(scaffold.root)
        applyThemeToControls(scaffold.root)
    }

    override fun onDestroy() { dialog?.dismiss(); dialog = null; super.onDestroy() }
}
