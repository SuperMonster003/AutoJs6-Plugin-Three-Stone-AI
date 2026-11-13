package io.github.supermonster003.autojs6.plugin.threestoneai

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiAttachmentInput
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Roadmap P4.4: the "+" menu with the real system pickers (D131: covered on an emulator, never on
 * the maintainer's phone). Opt-in with `-e systemPickers true` on an API 33+ emulator. The test
 * puts its own generated PNG and PDF into the emulator's media store, picks them in the photo
 * picker and in the documents UI through UiAutomation, checks that the chat imported them and that
 * the scripted backend received the same bytes, then deletes the media rows.
 */
class ChatAttachmentPickerEmulatorTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private lateinit var environment: ChatTestEnvironment
    private lateinit var files: AttachmentTestFiles
    private val inserted = mutableListOf<Uri>()

    @Before
    fun setUp() {
        assumeTrue("Opt-in: -e systemPickers true", arguments.getString("systemPickers") == "true")
        assumeTrue("The system photo picker needs API 33", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        assumeTrue("Emulator only (D131)", Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        environment = ChatTestEnvironment()
        files = AttachmentTestFiles(environment.context)
        val automation = environment.instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    @After
    fun tearDown() {
        if (!::environment.isInitialized) return
        inserted.forEach { uri -> runCatching { environment.context.contentResolver.delete(uri, null, null) } }
        // The media store leaves the folders; rmdir removes them only while they are empty.
        TEST_FOLDERS.forEach { folder -> environment.shell("rmdir /sdcard/$folder") }
        environment.backend.release()
        environment.close()
        files.close()
    }

    @Test
    fun theSystemPickersHandBackAPictureAndADocument() {
        val backend = environment.backend
        backend.attachments = AiAttachmentInput(
            images = true,
            pdf = true,
            maximumPdfBytes = ChatAttachmentLimits.MAXIMUM_MESSAGE_BYTES,
            maximumRequestBytes = ChatAttachmentLimits.MAXIMUM_MESSAGE_BYTES,
            maximumRequestImages = ChatAttachmentLimits.MAXIMUM_PER_MESSAGE,
        )
        backend.reply = listOf("Seen.")
        val tag = UUID.randomUUID().toString().take(8)
        val pdfName = "p4-brief-$tag.pdf"
        insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "p4-colors-$tag.png", "image/png", TEST_FOLDERS[0], files.image)
        insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), pdfName, "application/pdf", TEST_FOLDERS[1], files.pdf)

        environment.launchChat().use { scenario ->
            val chat = AttachmentChatDriver(scenario)
            val ownPackage = environment.context.packageName

            // Photo picker: the newest picture comes first; select it, then "Add".
            chat.chooseFromAttachMenu(R.string.chat_attachment_pick_images)
            click("the newest picture") { root ->
                root.takeIf { it.packageName != ownPackage }?.let(::nodes)
                    ?.firstOrNull { node -> node.viewIdResourceName?.endsWith(":id/icon_thumbnail") == true }
            }
            click("the photo picker's Add button") { root ->
                nodes(root).firstOrNull { node -> node.viewIdResourceName?.endsWith(":id/button_add") == true && node.isEnabled }
            }
            val picture = chat.waitForImports(1).single()
            assertEquals(ChatAttachmentKind.IMAGE, picture.kind)
            // A PNG within the edge limit is kept as it is (D132).
            assertEquals(files.image.length(), picture.sizeBytes)
            assertEquals(AttachmentTestFiles.IMAGE_WIDTH to AttachmentTestFiles.IMAGE_HEIGHT, picture.width to picture.height)

            // Documents UI: the PDF is among the recent files; one tap picks it.
            chat.chooseFromAttachMenu(R.string.chat_attachment_pick_files)
            click("the PDF in the documents UI") { root ->
                root.takeIf { it.packageName != ownPackage }?.let(::nodes)
                    ?.firstOrNull { node -> node.text?.toString() == pdfName }
            }
            val picked = chat.waitForImports(2)
            assertEquals(pdfName, picked[1].name)
            assertEquals(ChatAttachmentKind.PDF, picked[1].kind)
            assertEquals(1, picked[1].pageCount)

            chat.send("What is in these files?")
            chat.waitForReplies(1)
            val turn = backend.attachmentTurns.single()
            assertEquals(picked.map(ChatAttachment::name), turn.prompt.map { it.name })
            assertArrayEquals(files.image.readBytes(), turn.prompt[0].bytes)
            assertArrayEquals(files.pdf.readBytes(), turn.prompt[1].bytes)
        }
    }

    private fun insert(collection: Uri, name: String, mimeType: String, relativePath: String, source: File) {
        val resolver = environment.context.contentResolver
        val uri = checkNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                },
            ),
        ) { "Could not add $name to the media store" }
        inserted += uri
        checkNotNull(resolver.openOutputStream(uri)).use { output -> source.inputStream().use { it.copyTo(output) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    /** Waits for a node [find] returns in the active window and clicks it or its clickable parent. */
    private fun click(what: String, find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?) {
        val automation = environment.instrumentation.uiAutomation
        waitUntil(what, PICKER_TIMEOUT_MILLIS) {
            var target = automation.rootInActiveWindow?.let(find)
            while (target != null && !target.isClickable) target = target.parent
            target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
    }

    private fun nodes(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(root)
        for (index in 0 until root.childCount) root.getChild(index)?.let { child -> yieldAll(nodes(child)) }
    }

    private companion object {
        const val PICKER_TIMEOUT_MILLIS = 20_000L
        val TEST_FOLDERS = listOf("Pictures/3-Stone-P4-test", "Download/3-Stone-P4-test")
    }
}
