package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.view.View
import io.github.supermonster003.autojs6.plugin.threestoneai.AttachmentChatDriver.ActivityStub
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiAttachmentInput
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachmentKind
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.showsText
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Roadmap P4.4: chat attachments with the scripted backend. The "+" menu starts the pickers, whose
 * results are stubbed with generated files (ChatAttachmentPickerEmulatorTest drives the system
 * pickers themselves); the files reach the backend, move with the sent message, come back with the
 * history, open through the FileProvider and are deleted with the conversation.
 */
class ChatAttachmentDeviceTest {
    private lateinit var environment: ChatTestEnvironment
    private lateinit var files: AttachmentTestFiles
    private lateinit var store: ChatAttachmentStore
    private lateinit var pendingBefore: Set<String>
    private var stub: ActivityStub? = null

    @Before
    fun setUp() {
        assumeTrue("WindowInspector needs API 29", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        environment = ChatTestEnvironment()
        files = AttachmentTestFiles(environment.context)
        store = ChatAttachmentStore(environment.context)
        pendingBefore = pendingFileNames()
    }

    @After
    fun tearDown() {
        if (!::environment.isInitialized) return
        stub?.let(environment.instrumentation::removeMonitor)
        environment.backend.release()
        environment.close()
        // Files the test left in the composer, never those of the user's own draft.
        (pendingFileNames() - pendingBefore).forEach { name -> File(pendingDirectory(), name).delete() }
        files.close()
    }

    @Test
    fun pickedFilesAreSentStoredReplayedOpenedAndDeletedWithTheConversation() {
        val backend = environment.backend
        backend.attachments = FULL_INPUT
        backend.reply = listOf("Seen.")
        val names = listOf(AttachmentTestFiles.PDF_NAME, AttachmentTestFiles.TEXT_NAME, AttachmentTestFiles.IMAGE_NAME)
        var conversationId = ""
        var picked = emptyList<ChatAttachment>()
        environment.launchChat().use { scenario ->
            val chat = AttachmentChatDriver(scenario)
            assertEquals(1f, chat.onChat { activity -> chat.attachButton(activity).alpha })

            val documents = pick(chat, R.string.chat_attachment_pick_files, setOf(Intent.ACTION_OPEN_DOCUMENT), files.pdf, files.text)
            assertTrue("several documents", documents.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            assertEquals("*/*", documents.type)
            chat.waitForImports(2)
            pick(chat, R.string.chat_attachment_pick_images, AttachmentChatDriver.IMAGE_PICKER_ACTIONS, files.image)
            picked = chat.waitForImports(3)

            assertEquals(names, picked.map(ChatAttachment::name))
            assertEquals(listOf(ChatAttachmentKind.PDF, ChatAttachmentKind.TEXT, ChatAttachmentKind.IMAGE), picked.map(ChatAttachment::kind))
            assertEquals(1, picked[0].pageCount)
            // A PNG within the edge limit is kept as it is (D132).
            assertEquals(files.image.length(), picked[2].sizeBytes)
            assertEquals(AttachmentTestFiles.IMAGE_WIDTH, picked[2].width)
            assertEquals(AttachmentTestFiles.IMAGE_HEIGHT, picked[2].height)
            picked.forEach { attachment -> assertTrue(attachment.name, store.pendingFile(attachment).isFile) }
            assertEquals(names.map { it to 1f }, chat.barItems())
            assertTrue("the draft keeps the picked files", draftPreferences().getString("attachments", null)?.isNotEmpty() == true)

            // The picked files survive the screen being recreated (onSaveInstanceState, D140).
            scenario.recreate()
            waitUntil("the recreated composer is ready") { chat.onChat { activity -> composer(activity).isEnabled } }
            assertEquals(picked, chat.pending())
            assertEquals(names.map { it to 1f }, chat.barItems())

            chat.send("What is in these files?")
            chat.waitForReplies(1)
            val first = backend.attachmentTurns.single()
            assertEquals(names, first.prompt.map { it.name })
            assertTrue(first.history.isEmpty())
            assertEquals(
                listOf(GenerationAttachmentKind.PDF, GenerationAttachmentKind.TEXT, GenerationAttachmentKind.IMAGE),
                first.prompt.map { it.kind },
            )
            assertArrayEquals(files.pdf.readBytes(), first.prompt[0].bytes)
            assertArrayEquals(files.text.readBytes(), first.prompt[1].bytes)
            assertArrayEquals(files.image.readBytes(), first.prompt[2].bytes)
            assertEquals("text/plain", first.prompt[1].mimeType)
            assertEquals(1, first.prompt[0].pageCount)

            conversationId = chat.conversationId()
            assertTrue(chat.pending().isEmpty())
            assertTrue(chat.barItems().isEmpty())
            picked.forEach { attachment ->
                assertFalse(attachment.name, store.pendingFile(attachment).exists())
                assertTrue(attachment.name, store.file(conversationId, attachment).isFile)
            }
            assertEquals(picked, chat.messages().first { it.role == ChatMessageRole.USER }.attachments)
            assertEquals(names, chat.stripItems())
            assertTrue(draftPreferences().getString("attachments", null).isNullOrEmpty())

            // The next turn replays the files from the history; the budget is ample (D134).
            chat.send("And again?")
            chat.waitForReplies(2)
            val second = backend.attachmentTurns[1]
            assertTrue(second.prompt.isEmpty())
            assertEquals(names, second.history.map { it.name })
            assertTrue(second.history.all { it.bytes != null })

            // Tapping a thumbnail hands the file to a viewer through the FileProvider (D141).
            val viewer = register(ActivityStub(setOf(Intent.ACTION_VIEW), null))
            chat.tapStripItem(AttachmentTestFiles.IMAGE_NAME)
            val view = viewer.awaitIntent()
            unregister()
            assertEquals("content", view.data?.scheme)
            assertEquals(environment.context.packageName + ".attachments", view.data?.authority)
            assertEquals("image/png", view.type)
            assertTrue((view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        }

        val history = ConversationHistoryStore(environment.context)
        val stored = checkNotNull(history.find(conversationId))
        assertEquals(picked, stored.messages.first { it.role == ChatMessageRole.USER }.attachments)
        assertTrue(store.conversationDirectory(conversationId).isDirectory)
        assertEquals(1, history.delete(setOf(conversationId)))
        assertFalse("the conversation's files go with it", store.conversationDirectory(conversationId).exists())
    }

    @Test
    fun aTargetWithoutAttachmentsExplainsWhyAndRefusesToSendThem() {
        val backend = environment.backend
        backend.attachments = null
        environment.launchChat().use { scenario ->
            val chat = AttachmentChatDriver(scenario)
            assertTrue(chat.onChat { activity -> chat.attachButton(activity).alpha } < 1f)
            chat.tapAttach()
            val unavailable = chat.string(R.string.chat_attachment_unavailable_target)
            waitUntil("the reason is shown") { chat.onChat { activity -> showsText(activity, unavailable) } }
            assertNull(chat.menuItems())

            // A file picked earlier (a draft, another target) stays, dimmed, and is not sent.
            chat.onChat { activity -> activity.importAttachmentsForTest(listOf(files.uri(files.text))) }
            val pending = chat.waitForImports(1)
            val unsupportedNow = chat.string(R.string.chat_attachment_unsupported_now, AttachmentTestFiles.TEXT_NAME)
            waitUntil("the import warns") { chat.onChat { activity -> showsText(activity, unsupportedNow) } }
            assertTrue(chat.barItems().single().second < 1f)

            chat.send("hello")
            val refused = chat.string(R.string.chat_attachment_send_unsupported, AttachmentTestFiles.TEXT_NAME)
            waitUntil("sending is refused") { chat.onChat { activity -> showsText(activity, refused) } }
            assertTrue(backend.prompts.isEmpty())
            assertEquals(pending, chat.pending())

            chat.removeFromBar(AttachmentTestFiles.TEXT_NAME)
            waitUntil("the file leaves the bar") { chat.pending().isEmpty() && chat.barItems().isEmpty() }
            assertFalse(store.pendingFile(pending.single()).exists())
        }
    }

    @Test
    fun aTextOnlyTargetOffersFilesButNotPicturesAndHoldsBackThePdf() {
        val backend = environment.backend
        backend.attachments = AiAttachmentInput(
            images = false,
            pdf = false,
            maximumPdfBytes = 1L,
            maximumRequestBytes = ChatAttachmentLimits.MAXIMUM_MESSAGE_BYTES,
            maximumRequestImages = 0,
        )
        backend.reply = listOf("Read.")
        environment.launchChat().use { scenario ->
            val chat = AttachmentChatDriver(scenario)
            chat.tapAttach()
            waitUntil("the attach menu opens") { chat.menuItems() != null }
            assertEquals(
                listOf(
                    chat.string(R.string.chat_attachment_pick_images_unsupported) to false,
                    chat.string(R.string.chat_attachment_pick_files) to true,
                ),
                chat.menuItems(),
            )
            environment.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitUntil("the attach menu closes") { chat.menuItems() == null }

            pick(chat, R.string.chat_attachment_pick_files, setOf(Intent.ACTION_OPEN_DOCUMENT), files.text, files.pdf)
            chat.waitForImports(2)
            val unsupportedNow = chat.string(R.string.chat_attachment_unsupported_now, AttachmentTestFiles.PDF_NAME)
            waitUntil("the PDF import warns") { chat.onChat { activity -> showsText(activity, unsupportedNow) } }
            val bar = chat.barItems()
            assertEquals(listOf(AttachmentTestFiles.TEXT_NAME, AttachmentTestFiles.PDF_NAME), bar.map { it.first })
            assertEquals(1f, bar[0].second)
            assertTrue(bar[1].second < 1f)

            chat.send("Read the notes")
            val refused = chat.string(R.string.chat_attachment_send_unsupported, AttachmentTestFiles.PDF_NAME)
            waitUntil("sending is refused") { chat.onChat { activity -> showsText(activity, refused) } }
            assertTrue(backend.prompts.isEmpty())

            chat.removeFromBar(AttachmentTestFiles.PDF_NAME)
            waitUntil("only the notes remain") { chat.pending().map(ChatAttachment::name) == listOf(AttachmentTestFiles.TEXT_NAME) }
            chat.onChat { activity -> activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick() }
            chat.waitForReplies(1)
            assertEquals(listOf(AttachmentTestFiles.TEXT_NAME), backend.attachmentTurns.single().prompt.map { it.name })
        }
    }

    /** Picks [picked] through the "+" menu item [item]; returns the intent the menu started. */
    private fun pick(chat: AttachmentChatDriver, item: Int, actions: Set<String>, vararg picked: File): Intent {
        val picker = register(ActivityStub(actions, AttachmentChatDriver.pickerResult(picked.map(files::uri))))
        chat.chooseFromAttachMenu(item)
        return picker.awaitIntent().also { unregister() }
    }

    private fun register(monitor: ActivityStub): ActivityStub {
        unregister()
        environment.instrumentation.addMonitor(monitor)
        stub = monitor
        return monitor
    }

    private fun unregister() {
        stub?.let(environment.instrumentation::removeMonitor)
        stub = null
    }

    private fun draftPreferences() = environment.context.getSharedPreferences("chat-composer-draft", Context.MODE_PRIVATE)

    private fun pendingDirectory() = File(store.root, ".pending")

    private fun pendingFileNames(): Set<String> = pendingDirectory().list()?.toSet().orEmpty()

    private companion object {
        val FULL_INPUT = AiAttachmentInput(
            images = true,
            pdf = true,
            maximumPdfBytes = ChatAttachmentLimits.MAXIMUM_MESSAGE_BYTES,
            maximumRequestBytes = ChatAttachmentLimits.MAXIMUM_MESSAGE_BYTES,
            maximumRequestImages = ChatAttachmentLimits.MAXIMUM_PER_MESSAGE,
        )
    }
}
