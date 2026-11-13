package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiConnectionTest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiBaseUrlHistoryStore
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProviderCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.Ui
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.confirmDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.materialDialog
import io.github.supermonster003.autojs6.plugin.threestoneai.ui.showSnackbar
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Shared by the models and services page and the service details page (roadmap 1.7.0 P5.4). */
internal fun Context.onlineProviderLabel(provider: OnlineAiProvider): String =
    if (provider == OnlineAiProvider.OPENAI_COMPATIBLE) {
        getString(R.string.online_ai_provider_custom)
    } else {
        OnlineAiProviderCatalog.templateFor(provider).displayName
    }

internal object OnlineServiceText {
    const val NAME_MAXIMUM_CHARACTERS = 256
    const val BASE_URL_MAXIMUM_CHARACTERS = 4096
    const val CREDENTIAL_MAXIMUM_CHARACTERS = 8192

    fun isValidCredential(value: CharSequence): Boolean =
        value.length in 1..CREDENTIAL_MAXIMUM_CHARACTERS &&
            (0 until value.length).all { index -> value[index].code in 0x21..0x7E }

    fun copyCharacters(value: Editable): CharArray = CharArray(value.length) { index -> value[index] }

    /** [base], or "[base] 2", "[base] 3" ... when another service already uses the name (case-insensitive). */
    fun uniqueName(base: String, usedNames: Collection<String>): String {
        val names = usedNames.map { it.lowercase(Locale.ROOT) }.toSet()
        if (base.lowercase(Locale.ROOT) !in names) return base
        var suffix = 2
        while ("$base $suffix".lowercase(Locale.ROOT) in names) suffix += 1
        return "$base $suffix"
    }
}

internal fun ConfiguredActivity.showBaseUrlHistory(store: OnlineAiBaseUrlHistoryStore, target: EditText) {
    val values = store.load()
    if (values.isEmpty()) {
        materialDialog()
            .setTitle(R.string.online_ai_base_url_history)
            .setMessage(R.string.online_ai_base_url_history_empty)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also(::tintDialogButtons)
        return
    }
    materialDialog()
        .setTitle(R.string.online_ai_base_url_history)
        .setItems(values.toTypedArray()) { _, index ->
            target.setText(values[index])
            target.setSelection(target.text.length)
        }
        .setNeutralButton(R.string.online_ai_base_url_history_manage) { _, _ ->
            showBaseUrlHistoryManager(store, values)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
        .also(::tintDialogButtons)
}

private fun ConfiguredActivity.showBaseUrlHistoryManager(store: OnlineAiBaseUrlHistoryStore, values: List<String>) {
    val selected = BooleanArray(values.size)
    materialDialog()
        .setTitle(R.string.online_ai_base_url_history_manage)
        .setMultiChoiceItems(values.toTypedArray(), selected) { _, index, checked ->
            selected[index] = checked
        }
        .setNeutralButton(R.string.online_ai_base_url_history_clear) { _, _ -> store.clear() }
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.online_ai_base_url_history_delete) { _, _ ->
            store.remove(values.filterIndexed { index, _ -> selected[index] }.toSet())
        }
        .show()
        .also(::tintDialogButtons)
}

/**
 * The "Test connection" flow for a saved service: confirmation, a cancellable progress dialog, a
 * 120 s timeout and the failure explanation. Used from the service card menu and the details page.
 */
internal class OnlineServiceConnectionTester(
    private val activity: ConfiguredActivity,
    private val feedbackRoot: () -> View,
    private val onFinished: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { action ->
        Thread(action, "three-stone-ai-connection-test").apply { isDaemon = true }
    }
    private var nextToken = 1L
    private var active: ActiveTest? = null

    val activeProfileId: String? get() = active?.profileId

    fun confirm(profileId: String, displayName: String) {
        activity.confirmDialog(
            title = activity.getString(R.string.online_ai_test_title),
            message = activity.getString(R.string.online_ai_test_message, displayName),
            positiveResource = R.string.online_ai_test_start,
        ) { begin(profileId, displayName) }
    }

    fun cancel(showFeedback: Boolean) = cancel(active, showFeedback)

    /** Cancels any running test and stops the worker; call from onDestroy. */
    fun close() {
        cancel(active, showFeedback = false)
        executor.shutdownNow()
    }

    private fun begin(profileId: String, displayName: String) {
        cancel(active, showFeedback = false)
        val token = nextToken++
        val application = activity.application as ThreeStoneAiApplication
        val operation = OnlineAiConnectionTest(backend = application.onlineBackend, profileId = profileId)
        val progressView = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPaddingRelative(
                activity.uiDp(Ui.SPACE_XXL),
                activity.uiDp(Ui.SPACE_MD),
                activity.uiDp(Ui.SPACE_XXL),
                activity.uiDp(Ui.SPACE_SM),
            )
            addView(
                CircularProgressIndicator(context).apply {
                    isIndeterminate = true
                    indicatorSize = activity.uiDp(32)
                    setIndicatorColor(activity.appPalette.accent)
                },
            )
            addView(
                TextView(context).apply {
                    text = activity.getString(R.string.online_ai_test_progress, displayName)
                    textSize = 14f
                    setTextColor(activity.appPalette.secondaryText)
                    setPaddingRelative(activity.uiDp(16), 0, 0, 0)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        val dialog = activity.materialDialog()
            .setTitle(R.string.online_ai_action_test)
            .setView(progressView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        val timeout = Runnable { timeOut(token) }
        val test = ActiveTest(token, profileId, operation, dialog, timeout)
        active = test
        dialog.setOnShowListener {
            activity.tintDialogButtons(dialog)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { cancel(test, showFeedback = true) }
        }
        dialog.setOnCancelListener { cancel(test, showFeedback = true) }
        dialog.show()
        test.future = executor.submit {
            val failure = try {
                operation.execute()
                null
            } catch (error: Exception) {
                error
            }
            mainHandler.post { finish(test, failure) }
        }
        mainHandler.postDelayed(timeout, TIMEOUT_MILLIS)
    }

    private fun finish(test: ActiveTest, failure: Exception?) {
        if (active !== test) return
        active = null
        mainHandler.removeCallbacks(test.timeout)
        test.dialog.dismiss()
        when (failure) {
            null -> activity.showSnackbar(
                feedbackRoot(),
                activity.getString(R.string.online_ai_test_success),
                Snackbar.LENGTH_LONG,
            )
            is CancellationException ->
                activity.showSnackbar(feedbackRoot(), activity.getString(R.string.online_ai_test_cancelled))
            else -> activity.materialDialog()
                .setTitle(R.string.online_ai_test_failed)
                .setMessage(failureText(failure))
                .setPositiveButton(android.R.string.ok, null)
                .show()
                .also(activity::tintDialogButtons)
        }
        onFinished()
    }

    private fun timeOut(token: Long) {
        val test = active?.takeIf { it.token == token } ?: return
        active = null
        test.operation.cancel()
        test.future?.cancel(true)
        test.dialog.dismiss()
        activity.showSnackbar(
            feedbackRoot(),
            activity.getString(R.string.online_ai_test_timed_out),
            Snackbar.LENGTH_LONG,
        )
    }

    private fun cancel(test: ActiveTest?, showFeedback: Boolean) {
        if (test == null || active !== test) return
        active = null
        mainHandler.removeCallbacks(test.timeout)
        test.operation.cancel()
        test.future?.cancel(true)
        test.dialog.dismiss()
        if (showFeedback && !activity.isFinishing && !activity.isDestroyed) {
            activity.showSnackbar(feedbackRoot(), activity.getString(R.string.online_ai_test_cancelled))
        }
    }

    /** The fixed category plus, for online failures, the HTTP status and the provider's own explanation. */
    private fun failureText(error: Exception): String {
        val summary = activity.getString(failureMessage(error))
        val failure = error as? OnlineAiFailureException ?: return summary
        val detail = ChatGenerationFailurePolicy.providerDetail(failure) ?: return summary
        return summary + "\n\n" + activity.getString(R.string.chat_generation_failure_detail, detail)
    }

    private fun failureMessage(error: Exception): Int = when (error) {
        is OnlineAiFailureException -> when (error.reason) {
            OnlineAiFailureReason.CREDENTIAL_UNAVAILABLE -> R.string.online_ai_test_failure_credential
            OnlineAiFailureReason.PROFILE_CHANGED -> R.string.online_ai_test_failure_profile_changed
            OnlineAiFailureReason.AUTHENTICATION_FAILED,
            OnlineAiFailureReason.PERMISSION_DENIED,
            -> R.string.online_ai_test_failure_authentication
            OnlineAiFailureReason.RATE_LIMITED -> R.string.online_ai_test_failure_rate_limit
            OnlineAiFailureReason.REQUEST_REJECTED,
            OnlineAiFailureReason.REDIRECT_REFUSED,
            OnlineAiFailureReason.SERVICE_UNAVAILABLE,
            OnlineAiFailureReason.PROVIDER_ERROR,
            -> R.string.online_ai_test_failure_provider
            OnlineAiFailureReason.NETWORK_UNAVAILABLE -> R.string.online_ai_test_failure_network
            OnlineAiFailureReason.METERED_NETWORK_DISALLOWED -> R.string.online_ai_test_failure_metered
            OnlineAiFailureReason.TIMED_OUT -> R.string.online_ai_test_failure_timeout
            OnlineAiFailureReason.TLS_FAILED -> R.string.online_ai_test_failure_tls
            OnlineAiFailureReason.INVALID_REQUEST,
            OnlineAiFailureReason.INVALID_RESPONSE,
            OnlineAiFailureReason.RESPONSE_TOO_LARGE,
            OnlineAiFailureReason.EXECUTION_CLOSED,
            -> R.string.online_ai_test_failure_response
        }
        is AiTargetUnavailableException -> R.string.online_ai_test_failure_credential
        else -> R.string.online_ai_test_failed
    }

    private class ActiveTest(
        val token: Long,
        val profileId: String,
        val operation: OnlineAiConnectionTest,
        val dialog: AlertDialog,
        val timeout: Runnable,
        var future: Future<*>? = null,
    )

    private companion object {
        const val TIMEOUT_MILLIS = 120_000L
    }
}
