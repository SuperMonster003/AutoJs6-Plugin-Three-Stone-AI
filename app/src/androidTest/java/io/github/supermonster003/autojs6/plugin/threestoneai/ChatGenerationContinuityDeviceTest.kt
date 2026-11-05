package io.github.supermonster003.autojs6.plugin.threestoneai

import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Roadmap D77 / P1.4: a reply keeps streaming across a recreated chat screen (rotation, night
 * mode, language and font size all recreate it), a stop from the new screen still cancels it,
 * and a reply that finishes while the screen is stopped is shown when it returns.
 */
class ChatGenerationContinuityDeviceTest {
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
    fun recreatingTheScreenDuringStreamingContinuesTheReply() {
        val backend = environment.backend
        backend.reply = (0 until 6).map { index -> "part$index " }
        backend.chunkDelayMillis = 60L
        backend.hold = true
        environment.launchChat().use { scenario ->
            send(scenario, "keep going")
            waitUntil("part of the reply is shown") { lastReply(scenario)?.text?.contains("part2") == true }
            val coordinator = ChatGenerationCoordinator.get(environment.context)
            assertTrue(coordinator.isGenerating)

            scenario.recreate()

            waitUntil("the recreated screen shows the reply still generating") {
                val reply = lastReply(scenario)
                reply?.status == ChatMessageStatus.GENERATING && reply.text.contains("part5") && generating(scenario)
            }
            assertTrue(coordinator.isGenerating)
            assertTrue(keepsScreenOn(scenario))

            backend.release()
            waitUntil("the reply completes") { lastReply(scenario)?.status == ChatMessageStatus.COMPLETE }
            val reply = checkNotNull(lastReply(scenario))
            assertEquals(backend.reply.joinToString(""), reply.text)
            assertEquals(1L, reply.usage?.inputTokens)
            assertEquals(6L, reply.usage?.outputTokens)
            assertEquals(1, backend.prompts.size)
            assertEquals(1, backend.completions)
            assertFalse(generating(scenario))
            assertFalse(keepsScreenOn(scenario))
        }
    }

    @Test
    fun stoppingFromTheRecreatedScreenCancelsTheReply() {
        val backend = environment.backend
        backend.reply = listOf("first ", "second ")
        backend.hold = true
        environment.launchChat().use { scenario ->
            send(scenario, "stop me")
            waitUntil("the reply is streaming") { lastReply(scenario)?.text?.contains("second") == true }

            scenario.recreate()
            waitUntil("the recreated screen is generating") { generating(scenario) }
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>(ChatViewTags.COMPOSER_SEND).performClick()
            }

            waitUntil("the reply is stopped") { lastReply(scenario)?.status == ChatMessageStatus.STOPPED }
            assertEquals("first second ", lastReply(scenario)?.text)
            assertFalse(ChatGenerationCoordinator.get(environment.context).isGenerating)
            backend.release()
            SystemClock.sleep(SETTLE_MILLIS)
            // A cancelled scripted turn never reports completion.
            assertEquals(0, backend.completions)
            assertEquals(ChatMessageStatus.STOPPED, lastReply(scenario)?.status)
        }
    }

    @Test
    fun aReplyFinishingWhileTheScreenIsStoppedIsShownOnReturn() {
        val backend = environment.backend
        backend.reply = listOf("while ", "away")
        backend.hold = true
        environment.launchChat().use { scenario ->
            send(scenario, "finish in background")
            waitUntil("the reply is streaming") { lastReply(scenario)?.text?.contains("away") == true }

            scenario.moveToState(Lifecycle.State.CREATED)
            backend.release()
            waitUntil("the reply completes in the background") { backend.completions == 1 }
            SystemClock.sleep(SETTLE_MILLIS)
            assertFalse(ChatGenerationCoordinator.get(environment.context).isGenerating)
            scenario.moveToState(Lifecycle.State.RESUMED)

            waitUntil("the returned screen shows the completed reply") {
                lastReply(scenario)?.status == ChatMessageStatus.COMPLETE
            }
            val reply = checkNotNull(lastReply(scenario))
            assertEquals("while away", reply.text)
            assertEquals(2L, reply.usage?.outputTokens)
            assertFalse(generating(scenario))
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

    private fun generating(scenario: ActivityScenario<ChatActivity>): Boolean {
        var generating = false
        scenario.onActivity { activity -> generating = activity.generatingForTest }
        return generating
    }

    private fun keepsScreenOn(scenario: ActivityScenario<ChatActivity>): Boolean {
        var flag = false
        scenario.onActivity { activity ->
            flag = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        }
        return flag
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
    }
}
