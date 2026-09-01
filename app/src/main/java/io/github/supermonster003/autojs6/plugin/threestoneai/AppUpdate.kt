package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class AppRelease(
    val tag: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
)

internal object UpdateVersionPolicy {
    fun isNewer(candidateTag: String, installedVersion: String): Boolean {
        val candidate = parse(candidateTag) ?: return false
        val installed = parse(installedVersion) ?: return false
        val width = maxOf(candidate.numbers.size, installed.numbers.size)
        repeat(width) { index ->
            val comparison = (candidate.numbers.getOrElse(index) { 0 })
                .compareTo(installed.numbers.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return when {
            candidate.preRelease == installed.preRelease -> false
            candidate.preRelease == null -> true
            installed.preRelease == null -> false
            else -> candidate.preRelease > installed.preRelease
        }
    }

    private fun parse(value: String): ParsedVersion? {
        val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
        return ParsedVersion(
            numbers = match.groupValues[1].split('.').map { part -> part.toIntOrNull() ?: return null },
            preRelease = match.groupValues[2].takeIf(String::isNotEmpty),
        )
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val preRelease: String?,
    )

    private val VERSION_PATTERN = Regex("^[vV]?([0-9]+(?:\\.[0-9]+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
}

internal class AppUpdateSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var automaticChecksEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOMATIC_CHECKS, true)
        set(value) {
            preferences.edit().putBoolean(KEY_AUTOMATIC_CHECKS, value).apply()
        }

    val ignoredTags: Set<String>
        get() = preferences.getStringSet(KEY_IGNORED_TAGS, emptySet()).orEmpty().toSet()

    fun ignore(tag: String) {
        preferences.edit().putStringSet(KEY_IGNORED_TAGS, ignoredTags + tag).apply()
    }

    fun stopIgnoring(tags: Collection<String>) {
        preferences.edit().putStringSet(KEY_IGNORED_TAGS, ignoredTags - tags.toSet()).apply()
    }

    fun shouldCheckAutomatically(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!automaticChecksEnabled) return false
        val lastAttempt = preferences.getLong(KEY_LAST_AUTOMATIC_ATTEMPT, 0L)
        return lastAttempt <= 0L || nowMillis - lastAttempt >= AUTOMATIC_CHECK_INTERVAL_MILLIS
    }

    fun recordAutomaticAttempt(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit().putLong(KEY_LAST_AUTOMATIC_ATTEMPT, nowMillis).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "app-update-settings"
        const val KEY_AUTOMATIC_CHECKS = "automatic-checks"
        const val KEY_IGNORED_TAGS = "ignored-tags"
        const val KEY_LAST_AUTOMATIC_ATTEMPT = "last-automatic-attempt"
        val AUTOMATIC_CHECK_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(12)
    }
}

internal class AppUpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    fun check(callback: (Result<AppRelease>) -> Unit): Call {
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "3-Stone-AI-update-checker")
            .build()
        return client.newCall(request).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    callback(runCatching { response.use(::decodeRelease) })
                }
            })
        }
    }

    private fun decodeRelease(response: Response): AppRelease {
        require(response.isSuccessful) { "Release service returned HTTP ${response.code}" }
        val body = requireNotNull(response.body) { "Release response is empty" }
        val declaredLength = body.contentLength()
        require(declaredLength < 0L || declaredLength <= MAXIMUM_RESPONSE_BYTES) {
            "Release response is too large"
        }
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                require(output.size() <= MAXIMUM_RESPONSE_BYTES) { "Release response is too large" }
            }
        }
        val json = JSONObject(output.toString(Charsets.UTF_8.name()))
        val tag = json.getString("tag_name").trim()
        require(TAG_PATTERN.matches(tag)) { "Release tag is invalid" }
        return AppRelease(
            tag = tag,
            name = json.optString("name").trim().ifEmpty { tag },
            notes = json.optString("body").trim().take(MAXIMUM_NOTES_CHARACTERS),
            pageUrl = "$RELEASES_PAGE/tag/$tag",
        )
    }

    private companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases/latest"
        const val RELEASES_PAGE =
            "https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"
        const val MAXIMUM_RESPONSE_BYTES = 256 * 1024
        const val MAXIMUM_NOTES_CHARACTERS = 6_000
        val TAG_PATTERN = Regex("^[0-9A-Za-z._-]{1,100}$")
    }
}

internal class AppUpdateController(
    private val activity: ConfiguredActivity,
    private val store: AppUpdateSettingsStore = AppUpdateSettingsStore(activity),
    private val checker: AppUpdateChecker = AppUpdateChecker(),
) {
    private var activeCall: Call? = null
    private var progressDialog: AlertDialog? = null

    fun checkManually() = check(manual = true)

    fun checkAutomaticallyIfDue() {
        if (!store.shouldCheckAutomatically() || activeCall != null) return
        store.recordAutomaticAttempt()
        check(manual = false)
    }

    fun cancel() {
        activeCall?.cancel()
        activeCall = null
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun check(manual: Boolean) {
        if (activeCall != null) return
        if (manual) {
            progressDialog = activity.materialDialog()
                .setTitle(R.string.app_update_checking)
                .setMessage(R.string.app_update_checking_summary)
                .setNegativeButton(android.R.string.cancel) { _, _ -> activeCall?.cancel() }
                .create()
                .also { dialog ->
                    dialog.setOnCancelListener { activeCall?.cancel() }
                    dialog.show()
                    activity.tintDialogButtons(dialog)
                }
        }
        activeCall = checker.check { result ->
            activity.runOnUiThread {
                val wasCancelled = activeCall?.isCanceled() == true
                activeCall = null
                progressDialog?.dismiss()
                progressDialog = null
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result.fold(
                    onSuccess = { release -> handleRelease(release, manual) },
                    onFailure = {
                        if (manual && !wasCancelled) {
                            activity.showSnackbar(
                                activity.findViewById(android.R.id.content),
                                activity.getString(R.string.app_update_check_failed),
                                Snackbar.LENGTH_LONG,
                            )
                        }
                    },
                )
            }
        }
    }

    private fun handleRelease(release: AppRelease, manual: Boolean) {
        val installedVersion = activity.packageManager
            .getPackageInfo(activity.packageName, 0)
            .versionName
            .orEmpty()
        if (!UpdateVersionPolicy.isNewer(release.tag, installedVersion)) {
            if (manual) {
                activity.materialDialog()
                    .setTitle(R.string.app_update_up_to_date)
                    .setMessage(activity.getString(R.string.app_update_current_version, installedVersion))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                    .also(activity::tintDialogButtons)
            }
            return
        }
        if (!manual && release.tag in store.ignoredTags) return
        val message = buildString {
            append(activity.getString(R.string.app_update_available_summary, release.tag))
            if (release.notes.isNotBlank()) {
                append("\n\n")
                append(release.notes)
            }
        }
        val builder = activity.materialDialog()
            .setTitle(release.name)
            .setMessage(message)
            .setNegativeButton(R.string.app_update_later, null)
            .setPositiveButton(R.string.app_update_view_release) { _, _ -> openRelease(release.pageUrl) }
        builder.setNeutralButton(R.string.release_history_title) { _, _ ->
            activity.startActivity(Intent(activity, ReleaseHistoryActivity::class.java))
        }
        builder.show().also(activity::tintDialogButtons)
    }

    private fun openRelease(url: String) {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            activity.showSnackbar(
                activity.findViewById(android.R.id.content),
                activity.getString(R.string.app_update_open_failed),
                Snackbar.LENGTH_LONG,
            )
        }
    }
}
