package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.*
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.AndroidKeystoreCredentialCipher
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.EncryptedAiCredentialStore
import io.github.supermonster003.autojs6.plugin.threestoneai.credential.FileCredentialRecordStorage
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendUnavailableReason
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.zip.ZipFile

/** Runs on real Android ABIs, including Java-only APKs installed on a 64-bit device. */
class AbiCompatibilityAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun installedRuntimeExpected(): Boolean = ZipFile(context.applicationInfo.sourceDir).use { apk ->
        Process.is64Bit() && Build.SUPPORTED_64_BIT_ABIS.any { abi ->
            apk.getEntry("lib/$abi/liblitertlm_jni.so") != null
        }
    }

    @Test
    fun localProfilesMatchInstalledPayloadAndRejectUnsupportedInferenceBeforeJni() {
        val expected = installedRuntimeExpected()
        val detector = LiteRtLmBackendCompatibilityDetector()
        instrumentation.sendStatus(0, Bundle().apply {
            putString("abiRuntime", "process64=${Process.is64Bit()}, local=$expected, pagesize=${Os.sysconf(OsConstants._SC_PAGESIZE)}")
        })
        assertEquals(expected, detector.isRuntimeAvailable)
        if (expected) {
            assertEquals(AiProviderBackendAvailability.AVAILABLE, detector.profiles.first().availability)
        } else {
            detector.profiles.forEach { profile ->
                assertEquals(AiProviderBackendAvailability.UNAVAILABLE, profile.availability)
                assertEquals(AiProviderBackendUnavailableReason.ABI_UNSUPPORTED, profile.unavailableReason)
            }
            assertThrows(IllegalArgumentException::class.java) {
                LiteRtLmEngineFactory.create("unused.litertlm", context.cacheDir)
            }
        }
        assertFalse(File("/proc/self/maps").readText().contains("liblitertlm_jni.so"))
    }

    @Test
    fun onlineStreamingUsesKeystoreAndUnifiedBackendWithoutLoadingLiteRt() {
        // Isolated files/key and a synthetic HTTPS response: no saved profile or paid service is used.
        val identifier = UUID.randomUUID().toString()
        val directory = File(context.cacheDir, "abi-test-$identifier").apply { check(mkdirs()) }
        val keyAlias = "abi-test-$identifier"
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            assertEquals("https://example.com/v1/chat/completions", chain.request().url.toString())
            assertEquals("Bearer abi-test-key", chain.request().header("Authorization"))
            Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Online OK\"}}]}\n\n" +
                        "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}\n\n" +
                        "data: [DONE]\n\n"
                    ).toResponseBody("text/event-stream".toMediaType()))
                .build()
        }.build()
        try {
            val credentials = EncryptedAiCredentialStore(
                FileCredentialRecordStorage(directory), AndroidKeystoreCredentialCipher(keyAlias),
            )
            val registry = OnlineAiProfileRegistry(
                OnlineAiProfileRepository(FileOnlineAiProfileDocumentStorage(directory)), credentials,
            )
            val profile = OnlineAiProfile.create(
                "ABI test", OnlineAiProvider.OPENAI_COMPATIBLE, "https://example.com/v1", "test-model",
            )
            registry.save(profile, OnlineAiCredentialUpdate.Replace.takingOwnership("abi-test-key".toCharArray()))
            OnlineAiHttpExecution(client).use { execution ->
                val backend = CompositeAiBackend(
                    listOf((context.applicationContext as ThreeStoneAiApplication).localBackend,
                        OnlineAiBackend(registry, execution)),
                    OnlineAiBackend.BACKEND_ID,
                )
                val target = backend.catalog().requireTarget(AiTargetIds.profile(profile.profileId))
                assertTrue(target.available && target.configured)
                val output = StringBuilder()
                var completed: GenerationStatistics? = null
                var failure: Throwable? = null
                backend.createSession(AiBackendSessionRequest(target.targetId)).use { session ->
                    session.stream(
                        GenerationRequest(emptyList(), GenerationMessage(GenerationRole.USER, listOf("Test")),
                            maximumOutputTokens = 16, samplingOptions = null, reportUsage = true),
                        object : GenerationListener {
                            override fun onTextDelta(text: String) { output.append(text) }
                            override fun onCompleted(statistics: GenerationStatistics?) { completed = statistics }
                            override fun onFailed(error: Throwable, statistics: GenerationStatistics?) { failure = error }
                        },
                    )
                }
                assertNull(failure)
                assertEquals("Online OK", output.toString())
                assertEquals(5L, completed?.totalTokens)
                assertEquals(1, requests)
            }
            assertFalse(File("/proc/self/maps").readText().contains("liblitertlm_jni.so"))
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(keyAlias) }
            directory.deleteRecursively()
        }
    }

    @Test
    fun modelManagerExplainsUnavailableLocalRuntimeAndDisablesImportAndDownload() {
        val activity = instrumentation.startActivitySync(
            Intent(context, ModelManagerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val views = descendants(activity.findViewById(android.R.id.content)).filterIsInstance<TextView>()
                val notice = activity.getString(R.string.model_local_runtime_unavailable)
                assertEquals(!installedRuntimeExpected(), views.any { it.text.toString() == notice })
                if (!installedRuntimeExpected()) {
                    listOf(R.string.button_import_model, R.string.button_browse_litert_models).forEach { label ->
                        val button = views.single { it.text.toString() == activity.getString(label) }
                        assertFalse(button.isEnabled)
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()
}
