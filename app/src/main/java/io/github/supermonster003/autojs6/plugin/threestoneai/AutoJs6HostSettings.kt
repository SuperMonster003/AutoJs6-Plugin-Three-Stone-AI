package io.github.supermonster003.autojs6.plugin.threestoneai

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

/**
 * The last host snapshot stored for the next process (D226, ported from 3-Macaw Speech 510bd1d): a
 * cache, not a user choice, so neither settings export nor backup lists it. Pure JVM.
 */
internal object AutoJs6HostSettingsCopy {
    const val FILE = "host-appearance"
    private const val KEY_VERSION_CODE = "versionCode"
    private const val KEY_VERSION_NAME = "versionName"
    private const val KEY_PRIMARY = "primary"
    private const val KEY_PRIMARY_DARK = "primaryDark"
    private const val KEY_ACCENT = "accent"
    private const val KEY_DARK_MODE_POLICY = "darkModePolicy"
    private const val KEY_DARK = "dark"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_RESOLVED_LANGUAGE = "resolvedLanguage"

    /** The preference entries of [snapshot]: Long, Int, Boolean or String values. */
    fun entries(snapshot: AutoJs6HostSettingsSnapshot): Map<String, Any> = linkedMapOf(
        KEY_VERSION_CODE to snapshot.hostVersionCode,
        KEY_VERSION_NAME to snapshot.hostVersionName,
        KEY_PRIMARY to snapshot.themeColorPrimary,
        KEY_PRIMARY_DARK to snapshot.themeColorPrimaryDark,
        KEY_ACCENT to snapshot.themeColorAccent,
        KEY_DARK_MODE_POLICY to snapshot.darkModePolicy.name,
        KEY_DARK to snapshot.darkModeActive,
        KEY_LANGUAGE to snapshot.languageTag,
        KEY_RESOLVED_LANGUAGE to snapshot.resolvedLanguageTag,
    )

    /** The stored copy, or null when a part is missing or malformed. */
    fun fromStored(values: Map<String, *>): AutoJs6HostSettingsSnapshot? {
        val resolvedLanguage = (values[KEY_RESOLVED_LANGUAGE] as? String)?.takeIf(::validLanguageTag) ?: return null
        val policy = (values[KEY_DARK_MODE_POLICY] as? String)
            ?.let { name -> AutoJs6DarkModePolicy.entries.firstOrNull { it.name == name } } ?: return null
        return AutoJs6HostSettingsSnapshot(
            hostVersionCode = values[KEY_VERSION_CODE] as? Long ?: return null,
            hostVersionName = values[KEY_VERSION_NAME] as? String ?: return null,
            themeColorPrimary = values[KEY_PRIMARY] as? Int ?: return null,
            themeColorPrimaryDark = values[KEY_PRIMARY_DARK] as? Int ?: return null,
            themeColorAccent = values[KEY_ACCENT] as? Int ?: return null,
            darkModePolicy = policy,
            darkModeActive = values[KEY_DARK] as? Boolean ?: return null,
            languageTag = values[KEY_LANGUAGE] as? String ?: return null,
            resolvedLanguageTag = resolvedLanguage,
        )
    }

    fun validLanguageTag(tag: String): Boolean =
        tag.length in 2..80 && tag.matches(Regex("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*")) &&
            java.util.Locale.forLanguageTag(tag).language.isNotBlank()
}

/** Client for AutoJs6's versioned, read-only official-plugin settings contract. */
internal object AutoJs6HostSettingsClient {
    private val settingsUri = Uri.parse(Contract.CONTENT_URI)

    @Volatile private var cached: AutoJs6HostSettingsResult? = null
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Main-thread callers never perform Provider IO while attaching/inflating a screen. Before the
     * first refresh of a process they get the copy the previous process stored (D226), so a cold
     * start draws its first frame in the last AutoJs6 language, night mode and theme instead of the
     * system's and the refresh only recreates the screen when AutoJs6 changed meanwhile.
     */
    fun query(context: Context): AutoJs6HostSettingsResult =
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) cached(context) else runCatching { read(context.applicationContext) }.getOrDefault(AutoJs6HostSettingsResult(AutoJs6HostAvailability.CONTRACT_UNAVAILABLE))

    private fun cached(context: Context): AutoJs6HostSettingsResult = cached ?: run {
        val stored = runCatching { AutoJs6HostSettingsCopy.fromStored(preferences(context).all) }.getOrNull()
        val result = if (stored != null) {
            AutoJs6HostSettingsResult(AutoJs6HostAvailability.AVAILABLE, stored)
        } else {
            AutoJs6HostSettingsResult(AutoJs6HostAvailability.CONTRACT_UNAVAILABLE)
        }
        cached = result
        result
    }

    fun refresh(context: Context, result: (AutoJs6HostSettingsResult) -> Unit) {
        val app = context.applicationContext
        worker.execute { val next = runCatching { read(app) }.getOrDefault(AutoJs6HostSettingsResult(AutoJs6HostAvailability.CONTRACT_UNAVAILABLE)); main.post { result(next) } }
    }

    /** Replaces the value in this process only (instrumentation). */
    fun publish(result: AutoJs6HostSettingsResult) { cached = result }

    /**
     * A refresh the resumed screen accepted: kept for this process and stored for the next one; a
     * host that cannot be read clears the copy, so a missing host still falls back to the system.
     */
    fun accept(context: Context, result: AutoJs6HostSettingsResult) {
        val changed = cached != result
        cached = result
        if (!changed) return
        val editor = preferences(context).edit().clear()
        result.snapshot?.let { snapshot ->
            AutoJs6HostSettingsCopy.entries(snapshot).forEach { (key, value) ->
                when (value) {
                    is Long -> editor.putLong(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    else -> editor.putString(key, value.toString())
                }
            }
        }
        editor.apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(AutoJs6HostSettingsCopy.FILE, Context.MODE_PRIVATE)

    private fun read(context: Context): AutoJs6HostSettingsResult {
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
            require(!bundle.hasFileDescriptors())
            @Suppress("DEPRECATION")
            val typesValid = bundle.get(Contract.KEY_DARK_MODE_ACTIVE) is Boolean && bundle.get(Contract.KEY_THEME_COLOR_PRIMARY) is Int && bundle.get(Contract.KEY_THEME_COLOR_ACCENT) is Int
            require(typesValid)
            val resolvedTag = requireNotNull(bundle.getString(Contract.KEY_RESOLVED_LANGUAGE_TAG))
            require(AutoJs6HostSettingsCopy.validLanguageTag(resolvedTag))
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
