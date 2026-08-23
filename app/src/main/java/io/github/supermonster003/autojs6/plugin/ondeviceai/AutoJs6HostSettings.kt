package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.autojs.plugin.common.api.AutoJs6HostSettingsContract as Contract

internal enum class AutoJs6HostAvailability {
    AVAILABLE,
    NOT_INSTALLED,
    DISABLED,
    CONTRACT_UNAVAILABLE,
}

internal enum class AutoJs6DarkModePolicy {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

internal data class AutoJs6HostSettingsSnapshot(
    val hostVersionCode: Long,
    val hostVersionName: String,
    val themeColorPrimary: Int,
    val themeColorPrimaryDark: Int,
    val themeColorAccent: Int,
    val darkModePolicy: AutoJs6DarkModePolicy,
    val darkModeActive: Boolean,
    val languageTag: String,
    val resolvedLanguageTag: String,
)

internal data class AutoJs6HostSettingsResult(
    val availability: AutoJs6HostAvailability,
    val snapshot: AutoJs6HostSettingsSnapshot? = null,
) {
    init {
        require((availability == AutoJs6HostAvailability.AVAILABLE) == (snapshot != null))
    }

    val selectable: Boolean
        get() = availability == AutoJs6HostAvailability.AVAILABLE

    val definitiveAbsence: Boolean
        get() = availability == AutoJs6HostAvailability.NOT_INSTALLED ||
            availability == AutoJs6HostAvailability.DISABLED
}

/** Client for AutoJs6's versioned, read-only official-plugin settings contract. */
internal object AutoJs6HostSettingsClient {
    private val settingsUri = Uri.parse(Contract.CONTENT_URI)

    fun query(context: Context): AutoJs6HostSettingsResult {
        val installed = inspectHostPackage(context)
        if (installed != AutoJs6HostAvailability.AVAILABLE) {
            return AutoJs6HostSettingsResult(installed)
        }
        val bundle = runCatching {
            context.contentResolver.call(settingsUri, Contract.METHOD_GET_SETTINGS, null, null)
        }.getOrNull() ?: return AutoJs6HostSettingsResult(
            AutoJs6HostAvailability.CONTRACT_UNAVAILABLE,
        )
        val snapshot = runCatching {
            require(
                bundle.getInt(Contract.KEY_PROTOCOL_VERSION, 0) == Contract.PROTOCOL_VERSION,
            )
            require(
                bundle.getString(Contract.KEY_HOST_PACKAGE_NAME) == Contract.HOST_PACKAGE_NAME,
            )
            require(bundle.containsKey(Contract.KEY_THEME_COLOR_PRIMARY))
            require(bundle.containsKey(Contract.KEY_THEME_COLOR_PRIMARY_DARK))
            require(bundle.containsKey(Contract.KEY_THEME_COLOR_ACCENT))
            AutoJs6HostSettingsSnapshot(
                hostVersionCode = bundle.getLong(Contract.KEY_HOST_VERSION_CODE, 0L),
                hostVersionName = bundle.getString(Contract.KEY_HOST_VERSION_NAME).orEmpty(),
                themeColorPrimary = bundle.getInt(Contract.KEY_THEME_COLOR_PRIMARY),
                themeColorPrimaryDark = bundle.getInt(Contract.KEY_THEME_COLOR_PRIMARY_DARK),
                themeColorAccent = bundle.getInt(Contract.KEY_THEME_COLOR_ACCENT),
                darkModePolicy = AutoJs6DarkModePolicy.valueOf(
                    bundle.getString(Contract.KEY_DARK_MODE_POLICY).orEmpty(),
                ),
                darkModeActive = bundle.getBoolean(Contract.KEY_DARK_MODE_ACTIVE),
                languageTag = bundle.getString(Contract.KEY_LANGUAGE_TAG).orEmpty(),
                resolvedLanguageTag = bundle.getString(
                    Contract.KEY_RESOLVED_LANGUAGE_TAG,
                ).orEmpty(),
            )
        }.getOrNull() ?: return AutoJs6HostSettingsResult(
            AutoJs6HostAvailability.CONTRACT_UNAVAILABLE,
        )
        return AutoJs6HostSettingsResult(AutoJs6HostAvailability.AVAILABLE, snapshot)
    }

    private fun inspectHostPackage(context: Context): AutoJs6HostAvailability {
        val packageManager = context.packageManager
        val applicationInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    Contract.HOST_PACKAGE_NAME,
                    PackageManager.ApplicationInfoFlags.of(
                        PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(
                    Contract.HOST_PACKAGE_NAME,
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                )
            }
        }.getOrNull() ?: return AutoJs6HostAvailability.NOT_INSTALLED
        val enabled = when (
            packageManager.getApplicationEnabledSetting(Contract.HOST_PACKAGE_NAME)
        ) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> applicationInfo.enabled
        }
        return if (enabled) AutoJs6HostAvailability.AVAILABLE else AutoJs6HostAvailability.DISABLED
    }

}
