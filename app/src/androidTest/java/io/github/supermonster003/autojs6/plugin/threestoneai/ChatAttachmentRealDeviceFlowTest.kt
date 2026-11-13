package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in attachment requests to real services (roadmap P4.4, D131). Skipped unless the run passes
 * `-e realAttachments true`; each run sends at most one request, chosen by `-e case`:
 *
 * - `list`: logs the online targets (service name, protocol, model, attachment input, effort
 *   levels, readiness) and sends nothing.
 * - `image`: the generated two-color PNG; the reply should name both colors.
 * - `pdf`: the generated one-page PDF plus the generated text file; the reply should give the PDF's
 *   code word and the text file's fruit.
 * - `effort`: no files, the conversation's effort set to `-e effort` (Q15: whether a Gemini 2.5
 *   model accepts a non-default thinking level); a refusal is logged, not failed.
 *
 * `-e service` (the service's display name) and `-e model` pick the target. The files are generated
 * by the test (nothing is read from the device's pictures or storage), sent with the target the
 * conversation is switched to, and deleted with the test conversation; the default target and the
 * picker, base URL history, calibration and navigation preferences are restored. Nothing about the
 * services (keys and addresses included) is read or logged, and a failed reply logs only its HTTP
 * status.
 */
class ChatAttachmentRealDeviceFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val application get() = context.applicationContext as ThreeStoneAiApplication
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun listsTheOnlineTargets() {
        requireCase(setOf(CASE_LIST))
        val catalog = application.aiBackend.catalog()
        catalog.targets.filter { target -> target.profileId != null }.forEach { target ->
            val input = target.capabilities.attachments
            log(
                "target: service=${serviceName(target)} protocol=${protocol(target)} model=${target.modelId} " +
                    "images=${input?.images ?: false} pdf=${input?.pdf ?: false} " +
                    "effort=${target.capabilities.effortLevels.joinToString("/") { it.persistentId }} " +
                    "ready=${TargetPickerTexts.isUsable(target)}",
            )
        }
    }

    @Test
    fun oneRequestToARealService() {
        val case = requireCase(setOf(CASE_IMAGE, CASE_PDF, CASE_EFFORT))
        val serviceName = checkNotNull(arguments.getString("service")) { "-e service is required" }
        val modelId = checkNotNull(arguments.getString("model")) { "-e model is required" }
        val effort = arguments.getString("effort")?.let { value ->
            checkNotNull(GenerationEffort.fromPersistentId(value)) { "Unknown effort $value" }
        }
        val target = targetMatching("a ready target $modelId of $serviceName") { target ->
            target.profileId != null && target.modelId == modelId && serviceName(target) == serviceName &&
                TargetPickerTexts.isUsable(target)
        }
        val input = target.capabilities.attachments
        when (case) {
            CASE_IMAGE -> assumeTrue("$modelId takes no pictures", input?.images == true)
            CASE_PDF -> assumeTrue("$modelId takes no PDF", input?.pdf == true)
            CASE_EFFORT -> assumeTrue("$modelId has no effort $effort", effort != null && effort in target.capabilities.effortLevels)
        }

        val backup = takeBackup()
        val store = ChatAttachmentStore(context)
        var conversationId = ""
        try {
            ChatTestEnvironment(scripted = false).use { environment ->
                AttachmentTestFiles(context).use { files ->
                    val picked = when (case) {
                        CASE_IMAGE -> listOf(files.image)
                        CASE_PDF -> listOf(files.pdf, files.text)
                        else -> emptyList()
                    }
                    environment.launchChat().use { scenario ->
                        val chat = AttachmentChatDriver(scenario)
                        chat.onChat { activity -> activity.useTargetForTest(target.targetId) }
                        effort?.let { chosen -> chat.onChat { activity -> activity.useEffortForTest(chosen) } }
                        if (picked.isNotEmpty()) {
                            chat.onChat { activity -> activity.importAttachmentsForTest(picked.map(files::uri)) }
                            assertEquals(picked.map(File::getName), chat.waitForImports(picked.size).map(ChatAttachment::name))
                        }
                        val started = SystemClock.uptimeMillis()
                        chat.send(PROMPTS.getValue(case))
                        val reply = chat.waitForReplies(1, REPLY_TIMEOUT_MILLIS)
                        conversationId = chat.conversationId()
                        val seconds = (SystemClock.uptimeMillis() - started) / 1_000L
                        val sent = chat.messages().first { message -> message.role == ChatMessageRole.USER }
                        assertEquals(picked.map(File::getName), sent.attachments.map(ChatAttachment::name))
                        sent.attachments.forEach { attachment -> assertTrue(attachment.name, store.file(conversationId, attachment).isFile) }
                        assertEquals("the reply came from the chosen target", target.targetId, reply.target?.targetId)
                        report(case, reply, seconds)
                    }
                }
            }
        } finally {
            backup.restore()
        }
        assertFalse("the test conversation is gone", ConversationHistoryStore(context).find(conversationId) != null)
        assertFalse("its files are gone", store.conversationDirectory(conversationId).exists())
        assertEquals("the default target is back", backup.defaultTarget, storedDefault())
    }

    /** Logs the outcome without the reply text: status, length and which expected words it has. */
    private fun report(case: String, reply: ChatMessage, seconds: Long) {
        val text = reply.text
        if (reply.status != ChatMessageStatus.COMPLETE) {
            val http = HTTP_STATUS.find(text)?.value ?: "none"
            log("$case: ${reply.status} after $seconds s, http=$http")
            if (case == CASE_EFFORT) return
            throw AssertionError("$case: the reply did not complete (${reply.status}, http=$http)")
        }
        val expected = EXPECTED.getValue(case)
        val found = expected.filter { word -> text.contains(word, ignoreCase = true) }
        log("$case: COMPLETE after $seconds s, characters ${text.length}, expected words ${found.size}/${expected.size} $found")
        assertTrue("$case: the reply has text", text.isNotBlank())
        assertEquals("$case: the reply names ${expected.joinToString()}", expected, found)
    }

    private fun serviceName(target: AiTarget): String? = profile(target)?.displayName

    private fun protocol(target: AiTarget): String? = profile(target)?.provider?.protocol?.name

    private fun profile(target: AiTarget) = application.onlineProfileRegistry.snapshot().profiles
        .firstOrNull { configured -> configured.profile.profileId == target.profileId }?.profile

    private fun targetMatching(what: String, predicate: (AiTarget) -> Boolean): AiTarget {
        var found: AiTarget? = null
        val deadline = SystemClock.uptimeMillis() + PAGE_TIMEOUT_MILLIS
        while (found == null && SystemClock.uptimeMillis() < deadline) {
            found = application.aiBackend.catalog().targets.firstOrNull(predicate)
            if (found == null) SystemClock.sleep(200L)
        }
        assumeTrue("No $what on this device", found != null)
        return checkNotNull(found)
    }

    private fun takeBackup(): Backup {
        // The catalog migrates the default target first, so reading it never stores anything.
        application.aiBackend.catalog()
        return Backup(
            defaultTarget = storedDefault(),
            preferences = PREFERENCE_FILES.associateWith { name -> preferences(name).all.toMap() },
        )
    }

    private inner class Backup(val defaultTarget: String?, private val preferences: Map<String, Map<String, *>>) {
        fun restore() {
            application.defaultTargetStore.write(defaultTarget)
            preferences.forEach { (name, values) ->
                preferences(name).edit().clear().apply {
                    values.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }.commit()
            }
        }
    }

    private fun preferences(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun storedDefault(): String? =
        application.defaultTargetStore.readOrMigrate { error("the default target was not migrated") }

    private fun requireCase(cases: Set<String>): String {
        assumeTrue("Opt-in: -e realAttachments true", arguments.getString("realAttachments") == "true")
        assumeTrue("WindowInspector needs API 29", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val case = arguments.getString("case").orEmpty()
        assumeTrue("Not this case: $case", case in cases)
        return case
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "P44Attachments"
        const val CASE_LIST = "list"
        const val CASE_IMAGE = "image"
        const val CASE_PDF = "pdf"
        const val CASE_EFFORT = "effort"
        const val PAGE_TIMEOUT_MILLIS = 30_000L
        const val REPLY_TIMEOUT_MILLIS = 180_000L
        val HTTP_STATUS = Regex("(?<![0-9])[45][0-9][0-9](?![0-9])")
        val PROMPTS = mapOf(
            CASE_IMAGE to "The attached picture has a left half and a right half. Name the color of each half in English, in at most ten words.",
            CASE_PDF to "Two files are attached. What is the code word in the PDF, and what is the secret fruit in the text file? Answer in at most ten words.",
            CASE_EFFORT to "Reply with the single word OK.",
        )
        val EXPECTED = mapOf(
            CASE_IMAGE to listOf("red", "blue"),
            CASE_PDF to listOf(AttachmentTestFiles.CODE_WORD, AttachmentTestFiles.FRUIT),
            CASE_EFFORT to listOf("OK"),
        )
        val PREFERENCE_FILES = listOf(
            "model_picker",
            "online-ai-base-url-history",
            "context-token-calibration",
            "conversation-navigation",
        )
    }
}
