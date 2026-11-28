package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatUiSettingsStore
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatViewTags
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiApplication
import io.github.supermonster003.autojs6.plugin.threestoneai.VoiceRoundTiming
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.mic
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
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
