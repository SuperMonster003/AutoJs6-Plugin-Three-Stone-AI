package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiConnectionTest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureReason
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCredentialUpdate
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Device acceptance of the OpenCode Go session header (roadmap 1.8.0 P12.7, D170). Skipped unless
 * the run passes `-e openCodeGoAcceptance true`; the normal suite never sends these requests.
 *
 * For each of the three OpenCode Go endpoints (OpenAI compatible, Anthropic, Google) the test saves
 * a service with an obviously invalid placeholder API key and runs the connection test the "Test
 * connection" action runs. Without the session header OpenCode Go rejects the request with HTTP 400
 * MissingSessionID before it looks at the key. The failure explanation on the service details page
 * then offers "Add session header"; after adding and saving it the same test reaches the key check
 * and fails with HTTP 401, which shows that the header was sent. No real key is used, nothing is
 * billed, and the provider's answer must not contain the placeholder key.
 *
 * The test service is deleted afterwards; the default target and the base URL history are put back
 * and checked. Nothing about the existing services (keys included) is read or logged.
 */
class OpenCodeGoSessionHeaderAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val application get() = context.applicationContext as ThreeStoneAiApplication
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun openAiCompatibleEndpointNeedsTheSessionHeader() =
        verify("openai", OnlineAiProvider.OPENAI_COMPATIBLE, "https://opencode.ai/inference/go/openai/v1")

    @Test
    fun anthropicEndpointNeedsTheSessionHeader() =
        verify("anthropic", OnlineAiProvider.ANTHROPIC, "https://opencode.ai/inference/go/anthropic/v1")

    @Test
    fun googleEndpointNeedsTheSessionHeader() =
        verify("google", OnlineAiProvider.GEMINI, "https://opencode.ai/inference/go/google/v1beta")

    private fun verify(label: String, provider: OnlineAiProvider, baseUrl: String) {
        assumeTrue("Opt-in: -e openCodeGoAcceptance true", arguments.getString("openCodeGoAcceptance") == "true")
        // The catalog migrates the default target first, so reading it never stores anything.
        application.aiBackend.catalog()
        val defaultTarget = storedDefault()
        val history = historyPreferences().all.toMap()
        val profilesBefore = profileIds()
        val profile = OnlineAiProfile.create(
            displayName = "$NAME ($label)",
            provider = provider,
            baseUrl = baseUrl,
            modelId = arguments.getString("openCodeModel") ?: MODEL_ID,
        )
        try {
            application.onlineProfileRegistry.save(
                profile,
                OnlineAiCredentialUpdate.Replace.takingOwnership(PLACEHOLDER_KEY.toCharArray()),
            )
            val before = connectionFailure(profile.profileId)
            log("$label without the session header: ${describe(before)}")
            assertEquals("$label: HTTP status without the session header", 400, before.statusCode)
            assertTrue(
                "$label: MissingSessionID without the session header",
                ServiceHeaderPolicy.isMissingSessionError(before.providerDetail),
            )

            ActivityScenario.launch<ServiceDetailActivity>(ServiceDetailActivity.intent(context, profile.profileId)).use { scenario ->
                instrumentation.waitForIdleSync()
                assertEquals("$label: the OpenCode hint is shown", View.VISIBLE, scenario.call(::openCodeHintVisibility))
                val dialog = scenario.call { it.tester.showFailure(profile.profileId, before) }
                instrumentation.waitForIdleSync()
                val add = scenario.call { dialog.getButton(AlertDialog.BUTTON_NEUTRAL) }
                assertEquals("$label: the explanation offers to add the header", View.VISIBLE, scenario.call { add.visibility })
                scenario.call { add.performClick() }
                instrumentation.waitForIdleSync()
                assertEquals(
                    "$label: the session header is in the draft",
                    ServiceHeaderPreset.OPENCODE_GO.headers,
                    scenario.call { it.headerEditor.headers },
                )
                assertEquals("$label: the OpenCode hint is gone", View.GONE, scenario.call(::openCodeHintVisibility))
                assertTrue("$label: saving succeeds", scenario.call { it.save() })
            }
            assertEquals(
                "$label: the saved service has the session header",
                ServiceHeaderPreset.OPENCODE_GO.headers,
                application.onlineProfileRegistry.snapshot().profiles.single { it.profile.profileId == profile.profileId }.profile.headers,
            )

            val after = connectionFailure(profile.profileId)
            log("$label with the session header: ${describe(after)}")
            assertEquals("$label: HTTP status with the session header", 401, after.statusCode)
            assertEquals("$label: failure category", OnlineAiFailureReason.AUTHENTICATION_FAILED, after.reason)
            assertFalse(
                "$label: the session header error is gone",
                ServiceHeaderPolicy.isMissingSessionError(after.providerDetail),
            )
            listOf(before, after).forEach { failure ->
                assertFalse("$label: the placeholder key is not echoed", failure.providerDetail.orEmpty().contains(PLACEHOLDER_KEY))
            }
        } finally {
            runCatching { application.onlineProfileRegistry.delete(profile.profileId) }
            application.defaultTargetStore.write(defaultTarget)
            // Written synchronously: the process may end right after the last test.
            historyPreferences().edit().clear().apply {
                history.forEach { (key, value) -> if (value is String) putString(key, value) }
            }.commit()
        }
        assertEquals("$label: the test service is deleted", profilesBefore, profileIds())
        assertEquals("$label: the default target is back", defaultTarget, storedDefault())
    }

    /** Runs the same connection test as "Test connection" and returns its online failure. */
    private fun connectionFailure(profileId: String): OnlineAiFailureException {
        val error = runCatching {
            OnlineAiConnectionTest(application.onlineBackend, profileId).use { it.execute() }
        }.exceptionOrNull()
        return error as? OnlineAiFailureException
            ?: throw AssertionError("expected an online failure, got ${error?.javaClass?.simpleName ?: "success"}")
    }

    /** Status, category and the error type the provider named; never the whole answer. */
    private fun describe(failure: OnlineAiFailureException): String {
        val detail = failure.providerDetail.orEmpty()
        val type = when {
            ServiceHeaderPolicy.isMissingSessionError(detail) -> "MissingSessionID"
            detail.contains("authentication_error") -> "authentication_error"
            detail.contains("UNAUTHENTICATED") -> "UNAUTHENTICATED"
            detail.isEmpty() -> "no detail"
            else -> "other detail"
        }
        return "HTTP ${failure.statusCode} ${failure.reason} ($type)"
    }

    private fun openCodeHintVisibility(activity: ServiceDetailActivity): Int =
        activity.window.decorView.findViewWithTag<View>(ServiceDetailActivity.TAG_OPENCODE_HINT).visibility

    private fun historyPreferences() = context.getSharedPreferences("online-ai-base-url-history", Context.MODE_PRIVATE)

    private fun storedDefault(): String? =
        application.defaultTargetStore.readOrMigrate { error("the default target was not migrated") }

    private fun profileIds(): List<String> =
        application.onlineProfileRegistry.snapshot().profiles.map { it.profile.profileId }.sorted()

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private fun <T> ActivityScenario<ServiceDetailActivity>.call(block: (ServiceDetailActivity) -> T): T {
        var result: Result<T>? = null
        onActivity { activity -> result = runCatching { block(activity) } }
        return checkNotNull(result).getOrThrow()
    }

    private companion object {
        const val TAG = "P127OpenCodeGo"
        const val NAME = "P12.7 OpenCode Go"

        /** OpenCode Go checks the session header and the key before the model (2026-11-27). */
        const val MODEL_ID = "kimi-k2.5"

        /** Not a key: OpenCode Go answers 401 for it, so no request is billed. */
        const val PLACEHOLDER_KEY = "placeholder-not-a-key-0000"
    }
}
