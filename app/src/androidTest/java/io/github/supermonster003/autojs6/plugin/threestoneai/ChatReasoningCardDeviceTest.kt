package io.github.supermonster003.autojs6.plugin.threestoneai

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.views
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Roadmap D79 / P3.2: the thinking card streams while a reply is generated, folds to "Thought for
 * N s" when it ends, opens on a tap, and comes back from the history after the screen is reopened.
 */
class ChatReasoningCardDeviceTest {
    private lateinit var environment: ChatTestEnvironment

    @Before
    fun setUp() {
        environment = ChatTestEnvironment()
    }

    @After
    fun tearDown() {
        environment.backend.release()
        environment.close()
    }

    @Test
    fun theCardStreamsCollapsesExpandsAndSurvivesReopening() {
        val backend = environment.backend
        backend.reasoning = listOf("Weigh ", "the ", "options.")
        backend.reply = listOf("Answer ", "text")
        backend.chunkDelayMillis = 400L
        backend.hold = true
        var conversationId = ""
        var reasoning: ChatMessageReasoning? = null
        environment.launchChat().use { scenario ->
            send(scenario, "think first")
            assertEquals(listOf(true), backend.reasoningRequested)

            waitUntil("the card streams the reasoning") {
                val card = card(scenario)
                card != null && card.expanded && card.body.startsWith("Weigh") &&
                    card.header == string(scenario, R.string.chat_reasoning_thinking)
            }
            waitUntil("the reply text follows the reasoning") { lastReply(scenario)?.text == "Answer text" }
            assertTrue(checkNotNull(card(scenario)).expanded)

            backend.release()
            waitUntil("the reply completes") { lastReply(scenario)?.status == ChatMessageStatus.COMPLETE }
            val reply = checkNotNull(lastReply(scenario))
            val replyReasoning = checkNotNull(reply.reasoning)
            reasoning = replyReasoning
            val duration = checkNotNull(replyReasoning.durationMillis)
            assertEquals("Weigh the options.", replyReasoning.text)
            assertEquals("Answer text", reply.text)
            assertTrue("duration $duration", duration in 1_000L..10_000L)
            val folded = string(scenario, R.string.chat_reasoning_duration, ChatReasoningCardPolicy.seconds(duration))
            waitUntil("the card folds") { card(scenario)?.let { !it.expanded && it.header == folded } == true }

            tapHeader(scenario)
            waitUntil("a tap opens the card") {
                card(scenario)?.let { it.expanded && it.body == "Weigh the options." } == true
            }
            tapHeader(scenario)
            waitUntil("a second tap folds it again") { card(scenario)?.expanded == false }
            conversationId = conversationId(scenario)
        }

        val stored = ConversationHistoryStore(environment.context).find(conversationId)
        assertEquals(reasoning, stored?.messages?.lastOrNull()?.reasoning)

        environment.launchChat().use { scenario ->
            assertEquals(conversationId, conversationId(scenario))
            waitUntil("the reopened card is folded") { card(scenario)?.expanded == false }
            tapHeader(scenario)
            waitUntil("the reopened card still opens") {
                card(scenario)?.let { it.expanded && it.body == "Weigh the options." } == true
            }
        }
    }

    @Test
    fun theTwoSwitchesKeepTheCardClosedWhileGeneratingAndOpenAfterwards() {
        val settings = ChatUiSettingsStore(environment.context)
        settings.save(settings.load().copy(showReasoningWhileGenerating = false, collapseReasoningWhenDone = false))
        val backend = environment.backend
        backend.reasoning = listOf("Quiet ", "plan.")
        backend.reply = listOf("Reply")
        backend.hold = true
        environment.launchChat().use { scenario ->
            send(scenario, "switches")
            waitUntil("the reply streams") { lastReply(scenario)?.text == "Reply" }
            waitUntil("the card stays closed while generating") { card(scenario)?.expanded == false }

            backend.release()
            waitUntil("the reply completes") { lastReply(scenario)?.status == ChatMessageStatus.COMPLETE }
            waitUntil("the card stays open afterwards") {
                card(scenario)?.let { it.expanded && it.body == "Quiet plan." } == true
            }
        }
    }

    @Test
    fun aReplyWithoutReasoningShowsNoCard() {
        val backend = environment.backend
        backend.reply = listOf("Plain")
        environment.launchChat().use { scenario ->
            send(scenario, "no thinking")
            waitUntil("the reply completes") { lastReply(scenario)?.status == ChatMessageStatus.COMPLETE }
            assertEquals(null, lastReply(scenario)?.reasoning)
            assertEquals(null, card(scenario))
            assertFalse(backend.reasoningRequested.isEmpty())
        }
    }

    private data class CardState(val header: String, val body: String, val expanded: Boolean)

    private fun card(scenario: ActivityScenario<ChatActivity>): CardState? {
        var state: CardState? = null
        scenario.onActivity { activity ->
            val card = views(activity.window.decorView).filterIsInstance<ReasoningCardView>()
                .lastOrNull { view -> view.isShown } ?: return@onActivity
            val header = (card.getChildAt(0) as ViewGroup).getChildAt(0) as TextView
            val body = card.getChildAt(1) as TextView
            state = CardState(
                header = header.text.toString(),
                body = body.text.toString(),
                expanded = body.visibility == View.VISIBLE,
            )
        }
        return state
    }

    private fun tapHeader(scenario: ActivityScenario<ChatActivity>) {
        scenario.onActivity { activity ->
            val card = views(activity.window.decorView).filterIsInstance<ReasoningCardView>().last { it.isShown }
            card.getChildAt(0).performClick()
        }
    }

    private fun send(scenario: ActivityScenario<ChatActivity>, text: String) {
        val prompts = environment.backend.prompts.size
        scenario.onActivity { activity ->
            composer(activity).setText(text)
            activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
        }
        waitUntil("the message is sent") { environment.backend.prompts.size == prompts + 1 }
    }

    private fun lastReply(scenario: ActivityScenario<ChatActivity>): ChatMessage? {
        var reply: ChatMessage? = null
        scenario.onActivity { activity ->
            reply = activity.messagesForTest.lastOrNull { message -> message.role == ChatMessageRole.ASSISTANT }
        }
        return reply
    }

    private fun conversationId(scenario: ActivityScenario<ChatActivity>): String {
        var id = ""
        scenario.onActivity { activity -> id = activity.conversationIdForTest }
        return id
    }

    /** Strings as the screen shows them (the app language may differ from the system one). */
    private fun string(scenario: ActivityScenario<ChatActivity>, resource: Int, vararg arguments: Any): String {
        var text = ""
        scenario.onActivity { activity -> text = activity.getString(resource, *arguments) }
        return text
    }
}
