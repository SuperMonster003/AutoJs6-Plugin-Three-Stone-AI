package io.github.supermonster003.autojs6.plugin.threestoneai

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ListView
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Drives the chat screen's attachment controls for the P4.4 instrumentation tests: the "+" menu,
 * the composer bar, the message strips and stubbed picker / viewer activities. Needs API 29
 * (WindowInspector) for the popup menu.
 */
internal class AttachmentChatDriver(private val scenario: ActivityScenario<ChatActivity>) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    fun <T> onChat(block: (ChatActivity) -> T): T {
        var result: Result<T>? = null
        scenario.onActivity { activity -> result = runCatching { block(activity) } }
        return checkNotNull(result).getOrThrow()
    }

    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    fun string(@StringRes resource: Int, vararg arguments: Any): String =
        onChat { activity -> activity.getString(resource, *arguments) }

    fun attachButton(activity: ChatActivity): View = activity.window.decorView.findViewWithTag(ChatViewTags.COMPOSER_ATTACH)

    fun tapAttach() = onChat { activity -> attachButton(activity).performClick() }

    /** The shown "+" menu as (title, enabled) pairs, or null while it is closed. */
    fun menuItems(): List<Pair<String, Boolean>>? = onMain {
        menuList()?.let { list ->
            (0 until list.adapter.count).map { position ->
                val item = list.adapter.getItem(position) as MenuItem
                item.title.toString() to item.isEnabled
            }
        }
    }

    /** Taps "+", waits for its menu and picks the item titled [title]. */
    fun chooseFromAttachMenu(@StringRes title: Int) {
        val label = string(title)
        tapAttach()
        waitUntil("the attach menu opens") { onMain { menuList()?.childCount ?: 0 } > 0 }
        onMain {
            val list = checkNotNull(menuList())
            val position = (0 until list.adapter.count).first { position ->
                (list.adapter.getItem(position) as MenuItem).title.toString() == label
            }
            check((list.adapter.getItem(position) as MenuItem).isEnabled) { "$label is disabled" }
            list.performItemClick(list.getChildAt(position - list.firstVisiblePosition), position, list.getItemIdAtPosition(position))
        }
        waitUntil("the attach menu closes") { onMain { menuList() } == null }
    }

    fun pending(): List<ChatAttachment> = onChat { activity -> activity.pendingAttachmentsForTest }

    /** Waits until no import runs and [count] files are pending; returns them. */
    fun waitForImports(count: Int, timeoutMillis: Long = IMPORT_TIMEOUT_MILLIS): List<ChatAttachment> {
        waitUntil("$count attachments are pending", timeoutMillis) {
            onChat { activity -> !activity.attachmentImportsRunningForTest && activity.pendingAttachmentsForTest.size == count }
        }
        return pending()
    }

    /** Names on the composer bar in order, with their alpha; empty while the bar is hidden. */
    fun barItems(): List<Pair<String, Float>> = onChat { activity ->
        val bar = activity.window.decorView.findViewWithTag<HorizontalScrollView>(ChatViewTags.COMPOSER_ATTACHMENTS)
        if (bar == null || !bar.isShown) return@onChat emptyList()
        val row = bar.getChildAt(0) as ViewGroup
        // Each item is the thumbnail or chip (named by its description) under its remove control.
        (0 until row.childCount).map(row::getChildAt).filterIsInstance<FrameLayout>().map { item ->
            item.getChildAt(0).contentDescription.toString() to item.alpha
        }
    }

    /** Names shown in the sent messages' attachment strips, top to bottom. */
    fun stripItems(): List<String> = onChat { activity -> stripViews(activity).map { view -> view.contentDescription.toString() } }

    fun tapStripItem(name: String) = onChat { activity ->
        stripViews(activity).last { view -> view.contentDescription.toString() == name }.performClick()
    }

    /** Taps the bar's remove control of [name]. */
    fun removeFromBar(name: String) {
        val description = string(R.string.chat_attachment_remove, name)
        onChat { activity ->
            val bar = activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_ATTACHMENTS)
            views(bar).first { view -> view.contentDescription?.toString() == description }.performClick()
        }
    }

    fun send(text: String) = onChat { activity ->
        composer(activity).setText(text)
        activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
    }

    fun conversationId(): String = onChat { activity -> activity.conversationIdForTest }

    fun messages(): List<ChatMessage> = onChat { activity -> activity.messagesForTest }

    /** Waits until no reply is generating and [replies] assistant messages have finished. */
    fun waitForReplies(replies: Int, timeoutMillis: Long = REPLY_TIMEOUT_MILLIS): ChatMessage {
        waitUntil("$replies replies finish", timeoutMillis) {
            onChat { activity ->
                val finished = activity.messagesForTest.filter { message ->
                    message.role == ChatMessageRole.ASSISTANT && message.status != ChatMessageStatus.GENERATING
                }
                !activity.generatingForTest && finished.size >= replies
            }
        }
        return messages().last { message -> message.role == ChatMessageRole.ASSISTANT }
    }

    private fun stripViews(activity: ChatActivity): List<View> {
        val bar = activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_ATTACHMENTS)
        val inBar = views(bar).toSet()
        return views(activity.window.decorView).filter { view ->
            view !in inBar && view.isShown && view.contentDescription != null && view.hasOnClickListeners() &&
                view.parent?.parent is HorizontalScrollView
        }
    }

    private fun menuList(): ListView? = WindowInspector.getGlobalWindowViews()
        .filter { root -> root.isShown }
        .flatMap(::views)
        .filterIsInstance<ListView>()
        .firstOrNull { list -> list.isShown && (list.adapter?.count ?: 0) > 0 && list.adapter.getItem(0) is MenuItem }

    /**
     * Stands in for the activity an intent with one of [actions] would start: records the intent and
     * answers with [result] (RESULT_OK) or RESULT_CANCELED when it is null. Only one may be registered
     * at a time, because a monitor that does not match ends Instrumentation's search.
     */
    class ActivityStub(private val actions: Set<String>, private val result: Intent?) : Instrumentation.ActivityMonitor() {
        private val started = LinkedBlockingQueue<Intent>()

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action !in actions) return null
            started += Intent(intent)
            return Instrumentation.ActivityResult(if (result == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, result)
        }

        fun awaitIntent(): Intent = checkNotNull(started.poll(STUB_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "No activity for $actions was started"
        }
    }

    companion object {
        const val IMPORT_TIMEOUT_MILLIS = 20_000L
        const val REPLY_TIMEOUT_MILLIS = 20_000L
        private const val STUB_TIMEOUT_SECONDS = 10L

        /** Actions the photo picker contract may use: system picker, Play services fallback, documents. */
        val IMAGE_PICKER_ACTIONS = setOf(
            "android.provider.action.PICK_IMAGES",
            "androidx.activity.result.contract.action.PICK_IMAGES",
            Intent.ACTION_OPEN_DOCUMENT,
        )

        /** A picker result carrying [uris] the way the system pickers return several files. */
        fun pickerResult(uris: List<android.net.Uri>): Intent = Intent().apply {
            val clip = android.content.ClipData.newRawUri(null, uris.first())
            uris.drop(1).forEach { uri -> clip.addItem(android.content.ClipData.Item(uri)) }
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
