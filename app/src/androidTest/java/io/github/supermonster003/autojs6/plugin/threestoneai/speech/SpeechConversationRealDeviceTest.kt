package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatGenerationCoordinator
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatMessageRole
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatMessageStatus
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatUiSettingsStore
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags
import io.github.supermonster003.autojs6.plugin.threestoneai.ContextPolicy
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiApplication
import io.github.supermonster003.autojs6.plugin.threestoneai.VoiceRoundTiming
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.LocalEngineHandover
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.mic
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * P5-4 against the device's own model: ten voice rounds with a real target and the preferred real
 * engine. The recognizer is the scripted fake (the questions are fixed), so the listen time here is
 * not a speech recognition time; real speech is a separate check by the maintainer. Plays sound.
 * The online variant sends ten short questions to the configured provider and is run only when
 * the maintainer asks for it.
 *
 * P6-3: [localTargetTenRoundsThroughTheSession] listens through the real 3-Macaw Speech session instead: each round
 * the phone plays the first sentence of `en.wav` through its own speaker as soon as the session is ready (media volume
 * raised to `minVolume` percent only while it plays, D214), and the eleventh round hears no question, so 8 s of
 * silence end the conversation. A real room is not silent: when the session hears something after the ten questions
 * (a voice, a noise taken for a word), that round is sent like any other and counted in `ambientRounds`, and the
 * exit button ends the conversation as soon as the following round is ready. Run only through
 * `device_speech_check.py --only conversation-local-live` after the maintainer agreed to the sound; no recognized or
 * spoken text is stored, only timings, lengths and the CER.
 */
class SpeechConversationRealDeviceTest {
    @Test
    fun localTargetTenRounds() = tenRounds(AiTargetLocality.LOCAL, "local")

    @Test
    fun localTargetTenRoundsThroughTheSession() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("acoustic cases run only on request", arguments.getString("acoustic") == "true")
        ChatTestEnvironment(scripted = false, macaw = null).use { environment ->
            val target = readyTarget(environment, AiTargetLocality.LOCAL, "local-live") ?: return@use
            val store = ChatUiSettingsStore(environment.context)
            store.save(store.load().copy(preferMacawSpeech = true, maximumOutputTokens = 64, maximumOutputTokensDraft = 64))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val sentence = VoiceTestAudio.sentence(instrumentation.context.assets, environment.context.cacheDir)
            val asked = CopyOnWriteArrayList<Long>()
            val finals = CopyOnWriteArrayList<Pair<String, Long>>()
            val readies = CopyOnWriteArrayList<Long>()
            VoiceRoundSpeaker(environment.context, arguments.getString("minVolume")?.toIntOrNull() ?: 70).use { speaker ->
                ActivityScenario.launch<ChatActivity>(Intent(environment.context, ChatActivity::class.java)).use { scenario ->
                    waitUntil("the local target is selected", timeoutMillis = 30_000L) {
                        var selected = false
                        scenario.onActivity { activity -> selected = activity.useTargetForTest(target.targetId) }
                        selected
                    }
                    waitUntil("the chat probed 3-Macaw Speech", 8_000L) {
                        var probed = false
                        scenario.onActivity { activity -> probed = activity.macawSpeechForTest != null }
                        probed
                    }
                    var live = false
                    scenario.onActivity { activity -> live = activity.liveDictationAvailableForTest }
                    assumeTrue("3-Macaw Speech does not dictate live here", live)
                    scenario.onActivity { activity ->
                        activity.liveDictationObserverForTest = object : LiveDictationListener {
                            override fun onReady() {
                                readies += SystemClock.elapsedRealtime()
                                // Ten questions; the eleventh round should hear nothing and time out.
                                if (asked.size < QUESTIONS.size) {
                                    asked += speaker.play(sentence)
                                } else if (readies.size > QUESTIONS.size + 1) {
                                    // The room was heard after the questions; leave instead of waiting for silence.
                                    activity.window.decorView.post {
                                        activity.window.decorView.findViewWithTag<View>(ChatViewTags.VOICE_STATUS_ACTION)
                                            ?.performClick()
                                    }
                                }
                            }

                            override fun onPartial(text: String) = Unit

                            override fun onFinal(text: String) {
                                if (text.isNotBlank()) finals += text to SystemClock.elapsedRealtime()
                            }

                            override fun onEnd(end: LiveDictationEnd) = Unit
                        }
                        assertTrue(mic(activity).performLongClick())
                    }
                    waitUntil("ten rounds finish", timeoutMillis = 20 * 60_000L) {
                        var ended = false
                        scenario.onActivity { activity ->
                            ended = activity.currentVoicePhase == VoicePhase.IDLE && !activity.dictationPending &&
                                !activity.liveDictationActive
                        }
                        ended
                    }
                    var rounds = emptyList<VoiceRoundTiming>()
                    var exit: String? = null
                    var fallbacks = 0
                    scenario.onActivity { activity ->
                        rounds = activity.voiceRounds.toList()
                        exit = activity.lastVoiceExit
                        fallbacks = activity.voiceSheetFallbackCount
                    }
                    val availability = SpeechDeviceProbe.availability(environment.context, preferMacaw = true)
                    val values = linkedMapOf<String, Any?>(
                        "target" to target.locality.name,
                        "model" to target.modelId,
                        "engine" to availability.readAloud.name,
                        "volume" to speaker.described,
                        "rounds" to rounds.size,
                        "liveRounds" to rounds.count { it.live },
                        "sheetFallbacks" to fallbacks,
                        "ambientRounds" to rounds.size - QUESTIONS.size,
                        "exit" to exit,
                    )
                    // From the end of each question's audio to the final that followed it.
                    val afterSpeech = asked.map { start ->
                        val spoken = start + VoiceTestAudio.SENTENCE_MILLIS
                        finals.firstOrNull { it.second > spoken }?.let { it.second - spoken }
                    }
                    // What each round's session recognized: the finals between its ready and the next round's ready.
                    val heardByRound = readies.mapIndexed { index, ready ->
                        val next = readies.getOrNull(index + 1) ?: Long.MAX_VALUE
                        finals.filter { it.second in ready until next }.joinToString(" ") { it.first }
                    }
                    val cers = asked.indices.map { index ->
                        VoiceTestAudio.cer(VoiceTestAudio.EN_REFERENCE, heardByRound.getOrElse(index) { "" })
                    }
                    rounds.forEachIndexed { index, round ->
                        values["round${index + 1}"] = "listen=${round.listenMillis} afterSpeech=${afterSpeech.getOrNull(index)} " +
                            "firstSound=${round.firstSoundMillis} round=${round.roundMillis} chars=${round.replyCharacters} " +
                            "heard=${heardByRound.getOrElse(index) { "" }.length} " +
                            (cers.getOrNull(index)?.let { "cer=${"%.3f".format(it)}" } ?: "ambient")
                    }
                    values["readyToAskMaxMillis"] = readies.zip(asked).maxOfOrNull { (ready, start) -> start - ready }
                    values["meanCer"] = "%.3f".format(cers.average())
                    SpeechDeviceReport.receipt("conversation-local-live", values)
                    assertTrue("Ten questions were sent", rounds.size >= QUESTIONS.size)
                    assertTrue("Every round listened through the session", rounds.all { it.live })
                    assertEquals(if (rounds.size == QUESTIONS.size) "RecognitionNoMatch" else "Exit", exit)
                }
            }
        }
    }


    /**
     * A1 (3-stone-voice-1c, D222 / D223): voice rounds with the device's own model across session rebuilds and a
     * context summary, all in one conversation. A small context budget makes the chat rebuild the session every few
     * rounds, where the replaced session used to keep the engine and the next round failed (F2). The rounds come in
     * segments of three; after each one the chat stays idle long enough for a local summary to take the engine, and
     * once one has finished the segments go on until a rebuild has also replaced an open session. The recognizer is
     * the scripted fake and a silent fake engine reads the replies, so the case makes no sound. Only counts and
     * timings are stored.
     */
    @Test
    fun localTargetRoundsAcrossASummary() {
        FakeSpeechEngine.install()
        FakeSpeechEngine.utteranceMillis = 40L
        try {
            ChatTestEnvironment(scripted = false).use { environment ->
                val target = readyTarget(environment, AiTargetLocality.LOCAL, "local-summary") ?: return@use
                val store = ChatUiSettingsStore(environment.context)
                // The hand test's settings (defaults): output tokens unlimited, so a local session is rebuilt every
                // round (Q95) and a summary may start after any idle pause; the questions ask for short answers.
                store.save(
                    store.load().copy(
                        maximumOutputTokens = null,
                        contextTokenBudget = ContextPolicy.DEFAULT_INPUT_TOKEN_BUDGET,
                    ),
                )
                val counts = ChatGenerationCoordinator.get(environment.context).engineHandoffCountsForTest
                val replacedBefore = counts.replacedSessions.get()
                val startedBefore = counts.summariesStarted.get()
                val completedBefore = counts.summariesCompleted.get()
                val failedBefore = counts.summariesFailed.get()
                val yieldedBefore = counts.summariesYielded.get()
                ActivityScenario.launch<ChatActivity>(Intent(environment.context, ChatActivity::class.java)).use { scenario ->
                    waitUntil("the local target is selected", timeoutMillis = 30_000L) {
                        var selected = false
                        scenario.onActivity { activity -> selected = activity.useTargetForTest(target.targetId) }
                        selected
                    }
                    // A recreation for the AutoJs6 appearance would reset the round counters.
                    poll(15_000L) {
                        var settled = false
                        scenario.onActivity { activity -> settled = activity.appearanceSettledForTest }
                        settled
                    }
                    var asked = 0
                    var summaryAfterRounds: Int? = null
                    var summaryMillis: Long? = null
                    var idleWaits = 0
                    while (true) {
                        val questions = (0 until SEGMENT_ROUNDS).map { QUESTIONS[(asked + it) % QUESTIONS.size] }
                        environment.script(*(questions.map { Activity.RESULT_OK to it } + (Activity.RESULT_CANCELED to "")).toTypedArray())
                        scenario.onActivity { activity -> assertTrue(mic(activity).performLongClick()) }
                        waitUntil("rounds ${asked + 1} - ${asked + SEGMENT_ROUNDS} finish", timeoutMillis = 10 * 60_000L) {
                            var ended = false
                            scenario.onActivity { activity ->
                                ended = activity.currentVoicePhase == VoicePhase.IDLE && !activity.dictationPending
                            }
                            ended
                        }
                        var exit: String? = null
                        scenario.onActivity { activity -> exit = activity.lastVoiceExit }
                        assertEquals("Rounds ${asked + 1} - ${asked + SEGMENT_ROUNDS} end at the canceled sheet", "RecognitionCanceled", exit)
                        asked += SEGMENT_ROUNDS
                        if (summaryAfterRounds != null && counts.replacedSessions.get() > replacedBefore) break
                        if (asked >= MAXIMUM_SUMMARY_ROUNDS) break
                        if (summaryAfterRounds != null) continue
                        // Idle now: a due local summary takes the engine after LOCAL_SUMMARY_IDLE_MILLIS.
                        idleWaits++
                        val started = poll(ChatGenerationCoordinator.LOCAL_SUMMARY_IDLE_MILLIS + 10_000L) {
                            counts.summariesStarted.get() > startedBefore
                        }
                        if (!started) continue
                        val startedAt = SystemClock.elapsedRealtime()
                        waitUntil("the local summary finishes", timeoutMillis = 8 * 60_000L) {
                            counts.summariesCompleted.get() > completedBefore || counts.summariesFailed.get() > failedBefore
                        }
                        summaryAfterRounds = asked
                        summaryMillis = SystemClock.elapsedRealtime() - startedAt
                    }
                    var rounds = emptyList<VoiceRoundTiming>()
                    var replies = emptyList<ChatMessage>()
                    scenario.onActivity { activity ->
                        rounds = activity.voiceRounds.toList()
                        replies = activity.messagesForTest.filter { it.role == ChatMessageRole.ASSISTANT }
                    }
                    val values = linkedMapOf<String, Any?>(
                        "target" to target.locality.name,
                        "model" to target.modelId,
                        "contextTokenBudget" to ContextPolicy.DEFAULT_INPUT_TOKEN_BUDGET,
                        "maximumOutputTokens" to "unlimited",
                        "rounds" to rounds.size,
                        "completeReplies" to replies.count { it.status == ChatMessageStatus.COMPLETE && it.text.isNotBlank() },
                        "replacedSessions" to counts.replacedSessions.get() - replacedBefore,
                        "summariesStarted" to counts.summariesStarted.get() - startedBefore,
                        "summariesCompleted" to counts.summariesCompleted.get() - completedBefore,
                        "summariesFailed" to counts.summariesFailed.get() - failedBefore,
                        "summariesYielded" to counts.summariesYielded.get() - yieldedBefore,
                        "summaryAfterRounds" to summaryAfterRounds,
                        "summaryMillis" to summaryMillis,
                        "idleWaits" to idleWaits,
                    )
                    rounds.forEachIndexed { index, round ->
                        values["round${index + 1}"] = "round=${round.roundMillis} chars=${round.replyCharacters}"
                    }
                    SpeechDeviceReport.receipt("conversation-local-summary", values)
                    assertTrue("A summary within $MAXIMUM_SUMMARY_ROUNDS rounds", summaryAfterRounds != null)
                    assertTrue("A rebuild replaced an open session", counts.replacedSessions.get() > replacedBefore)
                    assertEquals(asked, rounds.size)
                    assertEquals(asked, replies.size)
                    assertTrue("Every round got a complete reply", replies.all { it.status == ChatMessageStatus.COMPLETE && it.text.isNotBlank() })
                    assertTrue("A local summary finished", counts.summariesCompleted.get() > completedBefore)
                    assertEquals("No local summary failed", failedBefore, counts.summariesFailed.get())
                }
            }
        } finally {
            FakeSpeechEngine.uninstall()
        }
    }

    /**
     * D227 (A2 (b)), driven by `.python/device_engine_handover.py`: after a local voice round this process (the chat)
     * holds the engine; the script AutoJs6 runs meanwhile loads one in `:provider`, which first asks this process to
     * give its idle copy back. A second round then loads the engine here again and asks `:provider` for its copy.
     */
    @Test
    fun localEngineGivenBackToAScript() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Run by device_engine_handover.py", arguments.getString("engineHandover") == "true")
        FakeSpeechEngine.install()
        FakeSpeechEngine.utteranceMillis = 40L
        try {
            ChatTestEnvironment(scripted = false).use { environment ->
                val target = readyTarget(environment, AiTargetLocality.LOCAL, "engine-handover") ?: return@use
                val store = ChatUiSettingsStore(environment.context)
                store.save(
                    store.load().copy(maximumOutputTokens = null, contextTokenBudget = ContextPolicy.DEFAULT_INPUT_TOKEN_BUDGET),
                )
                val counts = ChatGenerationCoordinator.get(environment.context).engineHandoffCountsForTest
                val releasedBefore = counts.releasedForAnotherProcess.get()
                val directory = File(checkNotNull(environment.context.getExternalFilesDir(null)), "speech-check")
                ActivityScenario.launch<ChatActivity>(Intent(environment.context, ChatActivity::class.java)).use { scenario ->
                    waitUntil("the local target is selected", timeoutMillis = 30_000L) {
                        var selected = false
                        scenario.onActivity { activity -> selected = activity.useTargetForTest(target.targetId) }
                        selected
                    }
                    poll(15_000L) {
                        var settled = false
                        scenario.onActivity { activity -> settled = activity.appearanceSettledForTest }
                        settled
                    }
                    voiceRound(environment, scenario, QUESTIONS[1])
                    val heldPss = pssMb()
                    SpeechDeviceReport.receipt("engine-handover-ready", mapOf("mainPssMb" to heldPss))

                    val released = poll(HANDOVER_WAIT_MILLIS) { counts.releasedForAnotherProcess.get() > releasedBefore }
                    // The session returns the engine on the chat's cancel thread; the engine closes right after.
                    poll(RELEASE_SETTLE_MILLIS) { heldPss - pssMb() >= MINIMUM_RELEASED_MB }
                    val releasedPss = pssMb()
                    SpeechDeviceReport.receipt(
                        "engine-handover-released",
                        mapOf("released" to released, "mainPssMb" to releasedPss, "answer" to LocalEngineHandover.lastAnswerForTest),
                    )

                    // The script has finished once this file appears; :provider keeps its engine cached.
                    poll(HANDOVER_WAIT_MILLIS) { File(directory, "engine-handover-continue").exists() }
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    voiceRound(environment, scenario, QUESTIONS[4])
                    val reloadedPss = pssMb()
                    val request = LocalEngineHandover.lastRequestForTest
                    SpeechDeviceReport.receipt("engine-handover-reloaded", mapOf("mainPssMb" to reloadedPss, "request" to request))
                    // Lets the script sample :provider before the instrumentation ends.
                    poll(SAMPLE_WAIT_MILLIS) { File(directory, "engine-handover-sampled").exists() }

                    var replies = emptyList<ChatMessage>()
                    scenario.onActivity { activity ->
                        replies = activity.messagesForTest.filter { it.role == ChatMessageRole.ASSISTANT }
                    }
                    SpeechDeviceReport.receipt(
                        "engine-handover",
                        linkedMapOf(
                            "model" to target.modelId,
                            "heldMainPssMb" to heldPss,
                            "releasedMainPssMb" to releasedPss,
                            "reloadedMainPssMb" to reloadedPss,
                            "released" to released,
                            "answer" to LocalEngineHandover.lastAnswerForTest,
                            "request" to request,
                            "replies" to replies.size,
                            "completeReplies" to replies.count { it.status == ChatMessageStatus.COMPLETE && it.text.isNotBlank() },
                        ),
                    )
                    assertTrue("The idle chat session gave its engine to the script", released)
                    assertTrue(
                        "The chat process freed the engine ($heldPss -> $releasedPss MB)",
                        heldPss - releasedPss >= MINIMUM_RELEASED_MB,
                    )
                    assertTrue(
                        "The second round asked :provider and got an answer: $request",
                        request?.startsWith("answered=true") == true,
                    )
                    assertEquals(2, replies.size)
                    assertTrue(
                        "Both rounds got a complete reply",
                        replies.all { it.status == ChatMessageStatus.COMPLETE && it.text.isNotBlank() },
                    )
                }
            }
        } finally {
            FakeSpeechEngine.uninstall()
        }
    }

    /** One voice round with [question] recognized, ended by a canceled sheet. */
    private fun voiceRound(environment: ChatTestEnvironment, scenario: ActivityScenario<ChatActivity>, question: String) {
        environment.script(Activity.RESULT_OK to question, Activity.RESULT_CANCELED to "")
        waitUntil("the chat is in front", timeoutMillis = 30_000L) { scenario.state == Lifecycle.State.RESUMED }
        scenario.onActivity { activity -> assertTrue(mic(activity).performLongClick()) }
        waitUntil("the round finishes", timeoutMillis = 10 * 60_000L) {
            var ended = false
            scenario.onActivity { activity ->
                ended = activity.currentVoicePhase == VoicePhase.IDLE && !activity.dictationPending
            }
            ended
        }
    }

    /** This (the chat) process's proportional set size in MB. */
    private fun pssMb(): Long = Debug.getPss() / 1024L

    /** Polls [condition] for up to [timeoutMillis]; false when it never held. */
    private fun poll(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return true
            SystemClock.sleep(200L)
        }
        return condition()
    }

    @Test
    fun onlineTargetTenRounds() = tenRounds(AiTargetLocality.REMOTE, "online")

    private fun tenRounds(locality: AiTargetLocality, label: String) {
        ChatTestEnvironment(scripted = false).use { environment ->
            val target = readyTarget(environment, locality, label) ?: return@use
            val store = ChatUiSettingsStore(environment.context)
            // Short answers keep ten rounds within minutes; the setting is restored afterwards.
            store.save(store.load().copy(preferMacawSpeech = true, maximumOutputTokens = 64, maximumOutputTokensDraft = 64))
            environment.script(*(QUESTIONS.map { Activity.RESULT_OK to it } + (Activity.RESULT_CANCELED to "")).toTypedArray())
            ActivityScenario.launch<ChatActivity>(Intent(environment.context, ChatActivity::class.java)).use { scenario ->
                waitUntil("the $label target is selected", timeoutMillis = 30_000L) {
                    var selected = false
                    scenario.onActivity { activity -> selected = activity.useTargetForTest(target.targetId) }
                    selected
                }
                scenario.onActivity { activity -> assertTrue(mic(activity).performLongClick()) }
                waitUntil("ten rounds finish", timeoutMillis = 15 * 60_000L) {
                    var ended = false
                    scenario.onActivity { activity ->
                        ended = activity.currentVoicePhase == VoicePhase.IDLE && !activity.dictationPending
                    }
                    ended
                }
                var rounds = emptyList<VoiceRoundTiming>()
                var exit: String? = null
                scenario.onActivity { activity ->
                    rounds = activity.voiceRounds.toList()
                    exit = activity.lastVoiceExit
                }
                val availability = SpeechDeviceProbe.availability(environment.context, preferMacaw = true)
                val values = linkedMapOf<String, Any?>(
                    "target" to target.locality.name,
                    "model" to target.modelId,
                    "engine" to availability.readAloud.name,
                    "rounds" to rounds.size,
                    "exit" to exit,
                )
                rounds.forEachIndexed { index, round ->
                    values["round${index + 1}"] =
                        "firstSound=${round.firstSoundMillis} round=${round.roundMillis} chars=${round.replyCharacters}"
                }
                SpeechDeviceReport.receipt("conversation-$label", values)
                assertEquals(QUESTIONS.size, rounds.size)
                assertEquals("RecognitionCanceled", exit)
            }
        }
    }

    /** The ready target to use, or null after listing them (`listTargets`); skips the test without one. */
    private fun readyTarget(environment: ChatTestEnvironment, locality: AiTargetLocality, label: String): AiTarget? {
        val application = environment.context.applicationContext as ThreeStoneAiApplication
        val ready = application.aiBackend.catalog().targets.filter { target ->
            target.locality == locality && target.configured && target.available && target.capabilities.streaming
        }
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("listTargets") == "true") {
            // Model and provider names only; profiles and credentials stay private.
            SpeechDeviceReport.receipt(
                "conversation-$label-targets",
                ready.mapIndexed { index, target -> "target$index" to "${target.providerId} ${target.modelId}" }.toMap(),
            )
            return null
        }
        val wanted = arguments.getString("targetModel")
        val target = ready.firstOrNull { target -> wanted == null || target.modelId == wanted }
        assumeTrue("No ready $label target on this device", target != null)
        return checkNotNull(target)
    }

    private companion object {
        const val SEGMENT_ROUNDS = 3
        const val MAXIMUM_SUMMARY_ROUNDS = 30
        const val HANDOVER_WAIT_MILLIS = 5 * 60_000L
        const val RELEASE_SETTLE_MILLIS = 15_000L
        const val SAMPLE_WAIT_MILLIS = 60_000L

        /** Far below the about 1.5 GB of Gemma 4 E2B, far above what a closed conversation leaves. */
        const val MINIMUM_RELEASED_MB = 500L

        val QUESTIONS = listOf(
            "用一句话介绍你自己.",
            "一加一等于几?",
            "用一句话解释彩虹.",
            "说一个水果的名字.",
            "一周有几天?",
            "推荐一项室内运动.",
            "用一句话说说春天.",
            "水的沸点是多少度?",
            "用一句话鼓励我.",
            "说再见.",
        )
    }
}
