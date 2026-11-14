package io.github.supermonster003.autojs6.plugin.threestoneai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.showsText
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ScriptedAiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.SpeechDeviceReport
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.UUID

/**
 * Roadmap P7.1 / P7.2 (D78 item 4, D83, D149 - D156): a conversation is exported as Markdown to the
 * document the system picker returns, or shared as a PNG through `cache/share`. The conversations
 * are written straight into history (no AI request is made) and deleted afterwards. The system
 * picker and the share sheet are never tapped: the picker's answer is handed back by an activity
 * monitor (a media store download the test owns), the share sheet is closed with Back. Only the
 * opt-in emulator test presses the real picker's Save button.
 */
@SdkSuppress(minSdkVersion = 29)
class ChatExportDeviceTest {
    private lateinit var environment: ChatTestEnvironment
    private lateinit var store: ConversationHistoryStore
    private lateinit var shareDirectory: File
    private lateinit var sharesBefore: Set<String>
    private val started = Collections.synchronizedList(ArrayList<Intent>())
    private var pickerAnswer: Uri? = null
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            started += Intent(intent)
            val answer = pickerAnswer ?: return null
            return if (intent.action == Intent.ACTION_CREATE_DOCUMENT) {
                Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(answer))
            } else {
                null
            }
        }
    }
    private val mediaRows = ArrayList<Uri>()

    /** 3 s (P7.2); a flow may pass a looser limit for a loaded emulator and must say so in its evidence. */
    private val timeLimitMillis = InstrumentationRegistry.getArguments().getString(ARGUMENT_TIME_LIMIT)?.toLong() ?: TIME_LIMIT_MILLIS

    @Before
    fun setUp() {
        environment = ChatTestEnvironment()
        store = ConversationHistoryStore(environment.context)
        shareDirectory = File(environment.context.cacheDir, ChatImageExportPolicy.SHARE_DIRECTORY)
        sharesBefore = shareDirectory.list().orEmpty().toSet()
        environment.instrumentation.addMonitor(monitor)
    }

    @After
    fun tearDown() {
        environment.instrumentation.removeMonitor(monitor)
        mediaRows.forEach { uri -> runCatching { environment.context.contentResolver.delete(uri, null, null) } }
        // Images this test made; files that were there before are the app's own.
        shareDirectory.listFiles()?.filter { it.name !in sharesBefore }?.forEach(File::delete)
        environment.backend.release()
        environment.close()
    }

    @Test
    fun markdownHoldsTheShownVersionAndReasoningOnlyWhenChosen() {
        val conversation = seed(markdownConversation())
        val (target, path) = mediaDownload("$TEST_FILE_PREFIX${UUID.randomUUID().toString().take(8)}.md")
        pickerAnswer = target
        environment.launchChat().use { scenario ->
            waitForMessages(scenario, conversation.messages.size)

            // The toolbar menu starts it for the open conversation; reasoning asks first, unchecked (D151).
            clickMenu(scenario, R.string.chat_export_markdown)
            val without = confirmOptions(scenario, includeReasoning = false)
            val request = started.last { it.action == Intent.ACTION_CREATE_DOCUMENT }
            assertEquals(ChatExportController.MIME_MARKDOWN, request.type)
            assertEquals(ConversationMarkdownExport.fileName(conversation.title, "unused"), request.getStringExtra(Intent.EXTRA_TITLE))
            assertEquals(target, without.uri)
            val text = readByShell(path)
            assertEquals(without.bytes, text.toByteArray(Charsets.UTF_8).size)
            checkShownVersion(scenario, conversation.title, text)
            assertFalse("reasoning is left out by default", text.contains(REASONING_WORD))

            // The drawer's conversation menu (D83, D149) exports the same conversation with reasoning.
            chooseFromDrawer(scenario, conversation, R.string.chat_export_markdown)
            val with = confirmOptions(scenario, includeReasoning = true)
            assertEquals(target, with.uri)
            val withReasoning = readByShell(path)
            checkShownVersion(scenario, conversation.title, withReasoning)
            assertTrue(withReasoning.contains("> ") && withReasoning.contains(REASONING_WORD))
            SpeechDeviceReport.file("chat-export-sample.md", text.toByteArray(Charsets.UTF_8))
            SpeechDeviceReport.file("chat-export-sample-reasoning.md", withReasoning.toByteArray(Charsets.UTF_8))
            SpeechDeviceReport.receipt(
                "chat-export-markdown",
                mapOf(
                    "bytesWithoutReasoning" to without.bytes,
                    "bytesWithReasoning" to with.bytes,
                    "mimeType" to mimeType(target),
                    "readByShell" to "pass",
                ),
            )
        }
    }

    @Test
    fun fiftyMessagesBecomeAnImageAndTheShareSheetOpensWithinThreeSeconds() {
        val conversation = seed(longConversation())
        assertEquals(50, conversation.messages.size)
        environment.launchChat().use { scenario ->
            waitForMessages(scenario, 50)
            val automation = environment.instrumentation.uiAutomation
            val ownPackage = environment.context.packageName
            // The share sheet's own window event carries the time it appeared (uptime clock), so the
            // polling below (idle syncs are slow on an emulator) does not add to the measured time.
            val windowEvents = Collections.synchronizedList(ArrayList<Pair<String, Long>>())
            automation.setOnAccessibilityEventListener { event ->
                val eventPackage = event.packageName?.toString()
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventPackage != null && eventPackage != ownPackage) {
                    windowEvents += eventPackage to event.eventTime
                }
            }
            var requestedAt = 0L
            var sheetPackage: String? = null
            val sheetShownAt = try {
                scenario.onActivity { activity ->
                    requestedAt = SystemClock.uptimeMillis()
                    activity.requestConversationExport(conversation.id, ChatExportKind.IMAGE)
                }
                waitUntil("the share sheet shows", timeoutMillis = 15_000L) {
                    sheetPackage = automation.rootInActiveWindow?.packageName?.toString()?.takeIf { it != ownPackage }
                    sheetPackage != null && synchronized(windowEvents) { windowEvents.any { it.first == sheetPackage } }
                }
                synchronized(windowEvents) { windowEvents.first { it.first == sheetPackage }.second }
            } finally {
                automation.setOnAccessibilityEventListener(null)
            }
            val shownMillis = sheetShownAt - requestedAt
            SystemClock.sleep(SHEET_SETTLE_MILLIS) // the sheet slides up; the time above is when it appeared
            SpeechDeviceReport.screenshot("chat-export-share-sheet")

            var result: ChatExportController.ImageExportResult? = null
            scenario.onActivity { activity -> result = activity.exportController.lastImageExport }
            val image = checkNotNull(result) { "No image was shared" }
            val chooser = started.last { it.action == Intent.ACTION_CHOOSER }
            @Suppress("DEPRECATION")
            val send = checkNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("image/png", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            @Suppress("DEPRECATION")
            val uri = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals(ownPackage + ShareFileProvider.AUTHORITY_SUFFIX, uri.authority)
            assertEquals("/share/${image.file.name}", uri.path)
            assertEquals(uri, send.clipData?.getItemAt(0)?.uri)

            // The PNG is what was reported, within the height limit, and only in cache/share (D153).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(image.file.path, bounds)
            assertEquals(image.width to image.height, bounds.outWidth to bounds.outHeight)
            assertTrue(image.height <= ChatImageExportPolicy.MAXIMUM_HEIGHT_PX)
            assertEquals(ChatImageExportPolicy.outputSize(image.layoutWidth, image.layoutHeight, ChatImageExportPolicy.scale(image.layoutWidth, image.layoutHeight)), image.width to image.height)
            assertEquals(shareDirectory.canonicalFile, image.file.canonicalFile.parentFile)

            // Another app reads it only with the grant the share intent carries.
            val denied = shellBytes("content read --uri $uri")
            assertFalse("unreadable without a grant", denied.size.toLong() == image.file.length())
            environment.context.grantUriPermission(SHELL_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val read = try {
                shellBytes("content read --uri $uri")
            } finally {
                environment.context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            assertEquals(image.file.length(), read.size.toLong())
            assertArrayEquals(PNG_SIGNATURE, read.copyOf(PNG_SIGNATURE.size))
            SpeechDeviceReport.file("chat-export-sample.png", read)

            automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            waitUntil("the chat shows again after Back") { automation.rootInActiveWindow?.packageName?.toString() == ownPackage }

            SpeechDeviceReport.receipt(
                "chat-export-image",
                mapOf(
                    "messages" to 50,
                    "imageMillis" to image.elapsedMillis,
                    "prepareMillis" to image.prepareMillis,
                    "drawMillis" to image.drawMillis,
                    "encodeMillis" to image.encodeMillis,
                    "shareSheetMillis" to shownMillis,
                    "shareSheetPackage" to sheetPackage,
                    "width" to image.width,
                    "height" to image.height,
                    "layoutWidth" to image.layoutWidth,
                    "layoutHeight" to image.layoutHeight,
                    "bytes" to image.file.length(),
                    "otherAppRead" to "pass",
                    "timeLimitMillis" to timeLimitMillis,
                ),
            )
            assertTrue("image in ${image.elapsedMillis} ms", image.elapsedMillis <= timeLimitMillis)
            assertTrue("share sheet in $shownMillis ms", shownMillis <= timeLimitMillis)
        }
    }

    @Test
    fun anImageCanBeCancelledAndASecondRequestKeepsTheFirst() {
        val conversation = seed(longConversation())
        environment.launchChat().use { scenario ->
            waitForMessages(scenario, 50)
            clickMenu(scenario, R.string.chat_export_image)
            // A second request while the first runs starts nothing new (D155).
            scenario.onActivity { activity ->
                assertTrue(activity.exportController.imageExportRunning)
                activity.requestConversationExport(conversation.id, ChatExportKind.IMAGE)
            }
            var cancel: View? = null
            waitUntil("the progress offers Cancel", timeoutMillis = 5_000L) {
                scenario.onActivity { activity ->
                    cancel = views(activity.window.decorView).firstOrNull { view ->
                        view.id == com.google.android.material.R.id.snackbar_action && view.isShown
                    }
                }
                cancel != null
            }
            scenario.onActivity { activity ->
                assertTrue(showsText(activity, activity.getString(R.string.chat_export_image_progress)))
                checkNotNull(cancel).performClick()
                assertFalse(activity.exportController.imageExportRunning)
            }
            SystemClock.sleep(SETTLE_MILLIS)
            environment.instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> assertNull(activity.exportController.lastImageExport) }
            assertTrue("no share sheet after Cancel", started.none { it.action == Intent.ACTION_CHOOSER })
            assertEquals("no image is left behind", sharesBefore, shareDirectory.list().orEmpty().toSet())
        }
        SpeechDeviceReport.receipt("chat-export-cancel", mapOf("cancel" to "pass", "second" to "pass"))
    }

    @Test
    fun aConversationStillGeneratingIsNotExported() {
        val backend = environment.backend
        backend.pauseBeforeReply = true
        backend.reply = listOf("Later.")
        environment.launchChat().use { scenario ->
            scenario.onActivity { activity ->
                composer(activity).setText("Hello")
                activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
            }
            waitUntil("the reply is generating") {
                var generating = false
                scenario.onActivity { activity -> generating = activity.generatingForTest }
                generating
            }
            scenario.onActivity { activity ->
                activity.requestConversationExport(activity.conversationIdForTest, ChatExportKind.MARKDOWN)
                activity.requestConversationExport(activity.conversationIdForTest, ChatExportKind.IMAGE)
            }
            waitUntil("the refusal shows") {
                var shown = false
                scenario.onActivity { activity -> shown = showsText(activity, activity.getString(R.string.chat_export_generating)) }
                shown
            }
            scenario.onActivity { activity ->
                assertFalse(activity.exportController.imageExportRunning)
                assertNull(activity.exportController.optionsDialog)
            }
            assertTrue(started.none { it.action == Intent.ACTION_CREATE_DOCUMENT || it.action == Intent.ACTION_CHOOSER })
            backend.release()
            waitUntil("the reply completes") {
                var done = false
                scenario.onActivity { activity -> done = !activity.generatingForTest }
                done
            }
        }
    }

    @Test
    fun theShareProviderServesOnlyItsOwnDirectory() {
        val context = environment.context
        val authority = context.packageName + ShareFileProvider.AUTHORITY_SUFFIX
        val inside = FileProvider.getUriForFile(context, authority, File(shareDirectory, "probe.png"))
        assertEquals("/share/probe.png", inside.path)
        listOf(File(context.cacheDir, "probe.png"), File(context.filesDir, "probe.png"), File(context.cacheDir, "share-other/probe.png"))
            .forEach { file ->
                val refused = runCatching { FileProvider.getUriForFile(context, authority, file) }
                assertTrue("$file must stay private", refused.exceptionOrNull() is IllegalArgumentException)
            }
    }

    /**
     * Opt-in, emulator only (`-e systemPickers true`): the real documents UI saves the export into
     * Download, another app (the shell) opens the file, then the test deletes it. Its Save button is
     * pressed through UiAutomation, which the device rules allow only on an emulator. The conversation
     * has no reasoning, so the picker opens at once (D151).
     */
    @Test
    fun theSystemPickerSavesAFileAnotherAppCanOpen() {
        assumeTrue("Opt-in: -e systemPickers true", InstrumentationRegistry.getArguments().getString(ARGUMENT_SYSTEM_PICKERS) == "true")
        assumeTrue("The documents UI checked here is the API 33 one", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        assumeTrue("Emulator only", Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val automation = environment.instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        val prompt = TEST_FILE_PREFIX + "picker-" + UUID.randomUUID().toString().take(8)
        val conversation = seed(conversation("p7-export-${UUID.randomUUID()}", listOf(user(1, prompt), reply(2, "Saved through the system picker."))))
        val name = ConversationMarkdownExport.fileName(conversation.title, "unused")
        val path = "/sdcard/Download/$name"
        var pickerPackage: String? = null
        try {
            environment.launchChat().use { scenario ->
                waitForMessages(scenario, 2)
                clickMenu(scenario, R.string.chat_export_markdown)
                val request = started.last { it.action == Intent.ACTION_CREATE_DOCUMENT }
                assertEquals(name, request.getStringExtra(Intent.EXTRA_TITLE))
                var save: AccessibilityNodeInfo? = null
                waitUntil("the documents UI offers Save", PICKER_TIMEOUT_MILLIS) {
                    val root = automation.rootInActiveWindow
                    save = root?.takeIf { it.packageName?.toString()?.endsWith("documentsui") == true }
                        ?.findAccessibilityNodeInfosByViewId("android:id/button1")?.firstOrNull { it.isEnabled }
                    pickerPackage = root?.packageName?.toString()
                    save != null
                }
                checkNotNull(save).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                var result: ChatExportController.MarkdownExportResult? = null
                waitUntil("the Markdown file is written", PICKER_TIMEOUT_MILLIS) {
                    scenario.onActivity { activity -> result = activity.exportController.lastMarkdownExport }
                    result != null
                }
                val saved = checkNotNull(result)
                val resolver = environment.context.contentResolver
                val bytes = checkNotNull(resolver.openInputStream(saved.uri)).use { it.readBytes() }
                assertEquals(saved.bytes, bytes.size)
                val text = bytes.toString(Charsets.UTF_8)
                assertTrue(text.startsWith("# " + ConversationMarkdownExport.inline(conversation.title) + "\n"))
                assertEquals("another app opens the same file", text, readByShell(path))
                val type = resolver.getType(saved.uri)
                // The grant from the picker lasts while the chat is open; delete through it.
                assertTrue("the saved file is deleted", DocumentsContract.deleteDocument(resolver, saved.uri))
                SpeechDeviceReport.receipt(
                    "chat-export-picker",
                    mapOf(
                        "picker" to pickerPackage,
                        "bytes" to saved.bytes,
                        "mimeType" to type,
                        "folder" to "Download",
                        "readByShell" to "pass",
                        "deleted" to "pass",
                    ),
                )
            }
        } finally {
            // Only when the test failed before deleting it through the picker's grant.
            if (environment.shell("ls $path").trim() == path) environment.shell("rm -f $path")
        }
    }

    private fun checkShownVersion(scenario: ActivityScenario<ChatActivity>, title: String, text: String) {
        var user = ""
        scenario.onActivity { activity -> user = activity.getString(R.string.chat_history_role_user) }
        assertTrue(text.startsWith("# " + ConversationMarkdownExport.inline(title) + "\n"))
        assertTrue(text.contains("\n## " + ConversationMarkdownExport.inline(user) + "\n"))
        listOf(IMAGE_NAME, TEXT_NAME).forEach { name -> assertTrue(name, text.contains("\n- " + ConversationMarkdownExport.inline(name) + "\n")) }
        assertTrue(text.contains("\n```kotlin\nfun main() = println(\"<3 & `ok`\")\n```\n"))
        assertTrue(text.contains(ConversationMarkdownExport.plain(SPECIAL_TEXT)))
        HIDDEN_TEXTS.forEach { hidden -> assertFalse(hidden, text.contains(hidden)) }
        assertEquals("code fences are closed", 0, text.lines().count { it.startsWith("```") } % 2)
        assertFalse(text.contains('\r'))
    }

    private fun confirmOptions(scenario: ActivityScenario<ChatActivity>, includeReasoning: Boolean): ChatExportController.MarkdownExportResult {
        var before: ChatExportController.MarkdownExportResult? = null
        waitUntil("the reasoning option shows") {
            var shown = false
            scenario.onActivity { activity -> shown = activity.exportController.optionsDialog?.isShowing == true }
            shown
        }
        scenario.onActivity { activity ->
            val controller = activity.exportController
            before = controller.lastMarkdownExport
            val dialog = checkNotNull(controller.optionsDialog)
            val box = views(dialog.window!!.decorView).filterIsInstance<CheckBox>().single()
            assertFalse("unchecked by default", box.isChecked)
            assertEquals(activity.getString(R.string.chat_export_include_reasoning), box.text.toString())
            box.isChecked = includeReasoning
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        var result: ChatExportController.MarkdownExportResult? = null
        waitUntil("the Markdown file is written") {
            scenario.onActivity { activity ->
                result = activity.exportController.lastMarkdownExport?.takeIf { it !== before }
            }
            result != null
        }
        scenario.onActivity { activity ->
            assertTrue(showsText(activity, activity.getString(R.string.chat_export_markdown_succeeded)))
        }
        return checkNotNull(result).also { assertNotNull("bytes are reported", it.bytes) }
    }

    private fun clickMenu(scenario: ActivityScenario<ChatActivity>, label: Int) {
        scenario.onActivity { activity ->
            activity.invalidateOptionsMenu()
        }
        environment.instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            val toolbar = views(activity.window.decorView).filterIsInstance<Toolbar>().first()
            val wanted = activity.getString(label)
            val item = (0 until toolbar.menu.size()).map(toolbar.menu::getItem).single { it.title.toString() == wanted }
            assertTrue("$wanted is visible", item.isVisible)
            assertTrue("$wanted is enabled", item.isEnabled)
            activity.onOptionsItemSelected(item)
        }
    }

    private fun chooseFromDrawer(scenario: ActivityScenario<ChatActivity>, conversation: StoredConversation, label: Int) {
        scenario.onActivity { activity ->
            views(activity.window.decorView).filterIsInstance<DrawerLayout>().first().openDrawer(GravityCompat.START, false)
        }
        waitUntil("the drawer lists the conversation") {
            var row: View? = null
            scenario.onActivity { activity ->
                row = views(activity.window.decorView).filterIsInstance<TextView>()
                    .firstOrNull { it.isShown && it.text.toString() == conversation.title }
                    ?.let { title -> generateSequence(title as View) { it.parent as? View }.firstOrNull(View::isLongClickable) }
                row?.performLongClick()
            }
            row != null
        }
        scenario.onActivity { activity ->
            val dialog = checkNotNull(activity.drawerActionsDialog)
            val adapter = dialog.listView.adapter
            val labels = (0 until adapter.count).map { adapter.getItem(it).toString() }
            assertTrue(labels.containsAll(listOf(activity.getString(R.string.chat_export_markdown), activity.getString(R.string.chat_export_image))))
            val index = labels.indexOf(activity.getString(label))
            dialog.listView.performItemClick(null, index, adapter.getItemId(index))
            dialog.dismiss()
            views(activity.window.decorView).filterIsInstance<DrawerLayout>().first().closeDrawer(GravityCompat.START, false)
        }
    }

    private fun waitForMessages(scenario: ActivityScenario<ChatActivity>, count: Int) {
        waitUntil("the seeded conversation opens") {
            var size = 0
            scenario.onActivity { activity -> size = activity.messageCountForTest }
            size == count
        }
    }

    /** Writes [conversation] into history as the one the chat opens; refuses to push out older conversations. */
    private fun seed(conversation: StoredConversation): StoredConversation {
        val existing = store.loadAll()
        val kept = ConversationHistoryPolicy.normalized(listOf(conversation) + existing).map(StoredConversation::id)
        check(kept.containsAll(existing.map(StoredConversation::id))) { "History is too full to add a test conversation" }
        check(store.upsert(conversation)) { "The test conversation was not saved" }
        store.rememberLastConversation(conversation.id)
        return checkNotNull(store.find(conversation.id))
    }

    private fun markdownConversation(): StoredConversation {
        val id = "p7-export-${UUID.randomUUID()}"
        val first = listOf(user(1, HIDDEN_TEXTS[0]), reply(2, HIDDEN_TEXTS[1]), user(3, HIDDEN_TEXTS[2]), reply(4, HIDDEN_TEXTS[3]))
        val image = ChatAttachment("c".repeat(32), ChatAttachmentKind.IMAGE, IMAGE_NAME, "image/png", AttachmentTestFiles.imageBytes().size.toLong(), AttachmentTestFiles.IMAGE_WIDTH, AttachmentTestFiles.IMAGE_HEIGHT)
        val text = ChatAttachment("d".repeat(32), ChatAttachmentKind.TEXT, TEXT_NAME, "text/plain", AttachmentTestFiles.TEXT.length.toLong())
        writeAttachment(id, image, AttachmentTestFiles.imageBytes())
        writeAttachment(id, text, AttachmentTestFiles.TEXT.toByteArray(Charsets.UTF_8))
        val shown = checkNotNull(ChatBranchPolicy.withNewVersion(first, 1L, user(5, SPECIAL_TEXT, listOf(image, text)))) + listOf(
            reply(6, "Here:\n\n```kotlin\nfun main() = println(\"<3 & `ok`\")\n```\n", ChatMessageReasoning("$REASONING_WORD about <tags> & more\nstep two", 3_200L)),
            ChatMessage(7, ChatMessageRole.NOTICE, "Notice line"),
            user(8, "Next: | table | ~strike~"),
            reply(9, "```py\nprint(1)", status = ChatMessageStatus.STOPPED),
        )
        return conversation(id, shown)
    }

    private fun longConversation(): StoredConversation {
        val id = "p7-export-${UUID.randomUUID()}"
        val image = ChatAttachment("e".repeat(32), ChatAttachmentKind.IMAGE, IMAGE_NAME, "image/png", AttachmentTestFiles.imageBytes().size.toLong(), AttachmentTestFiles.IMAGE_WIDTH, AttachmentTestFiles.IMAGE_HEIGHT)
        writeAttachment(id, image, AttachmentTestFiles.imageBytes())
        val messages = (1..50).map { index ->
            val round = (index + 1) / 2
            if (index % 2 == 1) {
                user(index.toLong(), "Question $round: how should step $round of the plan work?", if (round == 1) listOf(image) else emptyList())
            } else {
                reply(index.toLong(), longReply(round))
            }
        }
        return conversation(id, messages)
    }

    private fun longReply(round: Int): String = buildString {
        append("Step $round works like this. ")
        append("The first part checks the input, the second part keeps the state and the third part reports what changed. ".repeat(1 + round % 3))
        append("\n\n- Check the input\n- Keep the state\n- Report the result\n")
        if (round % 5 == 0) append("\n```kotlin\nfun step$round(input: String): Result = runCatching { check(input.isNotBlank()); input.trim() }\n```\n")
        if (round % 10 == 0) append("\n| Part | Time |\n| --- | --- |\n| Check | 1 s |\n| Report | 2 s |\n")
    }

    private fun conversation(id: String, messages: List<ChatMessage>): StoredConversation {
        val now = System.currentTimeMillis()
        // Titled as the chat page titles it on every save: after the first user message shown.
        val title = ConversationHistoryPolicy.titleFor(messages, "Untitled")
        return StoredConversation(id = id, title = title, createdAtMillis = now - 3_600_000L, updatedAtMillis = now, target = TARGET, messages = messages)
    }

    private fun writeAttachment(conversationId: String, attachment: ChatAttachment, bytes: ByteArray) {
        val file = ChatAttachmentStore(environment.context).file(conversationId, attachment)
        checkNotNull(file.parentFile).mkdirs()
        file.writeBytes(bytes)
    }

    private fun user(id: Long, text: String, attachments: List<ChatAttachment> = emptyList()) =
        ChatMessage(id, ChatMessageRole.USER, text, attachments = attachments)

    private fun reply(id: Long, text: String, reasoning: ChatMessageReasoning? = null, status: ChatMessageStatus = ChatMessageStatus.COMPLETE) =
        ChatMessage(id, ChatMessageRole.ASSISTANT, text, status = status, target = TARGET, reasoning = reasoning)

    /**
     * A download the picker "returns" and its path for reading it back as another app. It goes straight
     * into Download under a unique test name: a test folder removed from the shell would leave a stale
     * media store row behind.
     */
    private fun mediaDownload(name: String): Pair<Uri, String> {
        val resolver = environment.context.contentResolver
        val uri = checkNotNull(
            resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, ChatExportController.MIME_MARKDOWN)
                },
            ),
        ) { "Could not add $name to the downloads" }
        mediaRows += uri
        @Suppress("DEPRECATION")
        val path = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return uri to checkNotNull(path) { "The download has no path" }
    }

    private fun mimeType(uri: Uri): String? = environment.context.contentResolver
        .query(uri, arrayOf(MediaStore.MediaColumns.MIME_TYPE), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    /**
     * Reads [path] as the shell user: another app opening the exported file. The command is split at
     * spaces without quoting, so the test names its files without spaces.
     */
    private fun readByShell(path: String): String {
        check(path.none(Char::isWhitespace)) { "Unexpected space in $path" }
        return shellBytes("cat $path").toString(Charsets.UTF_8)
    }

    private fun shellBytes(command: String): ByteArray {
        val descriptor = environment.instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val IMAGE_NAME = "photo [1].png"
        const val TEXT_NAME = "notes_draft.txt"
        const val REASONING_WORD = "Pondering"
        const val SPECIAL_TEXT = "Plan *trip* <b>&amp;</b> 50% off_now\n# not a heading\n  indented"
        val HIDDEN_TEXTS = listOf("Hidden question v1", "Hidden answer v1", "Hidden follow-up v1", "Hidden reply v1")
        /** Every file the test leaves in Download starts with this; the flow script checks none is left. */
        const val TEST_FILE_PREFIX = "3-Stone-P7-test-"
        const val ARGUMENT_SYSTEM_PICKERS = "systemPickers"
        const val PICKER_TIMEOUT_MILLIS = 20_000L
        const val SHELL_PACKAGE = "com.android.shell"
        const val TIME_LIMIT_MILLIS = 3_000L
        const val ARGUMENT_TIME_LIMIT = "exportTimeLimitMillis"
        const val SETTLE_MILLIS = 2_000L
        const val SHEET_SETTLE_MILLIS = 1_000L
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val TARGET = ConversationTargetSnapshot(
            targetId = ScriptedAiBackend.TARGET_ID,
            providerId = ThreeStoneAiPlugin.PROVIDER_ID,
            modelId = ScriptedAiBackend.MODEL_ID,
            displayName = "Speech test model",
            locality = AiTargetLocality.LOCAL,
        )
    }
}
