package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatUiSettingsStore
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiApplication
import io.github.supermonster003.autojs6.plugin.threestoneai.VoiceRoundTiming
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.mic
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * P5-4 against the device's own model: ten voice rounds with a real target and the preferred real
 * engine. The recognizer is the scripted fake (the questions are fixed), so the listen time here is
 * not a speech recognition time; real speech is a separate check by the maintainer. Plays sound.
 * The online variant sends ten short questions to the configured provider and is run only when
 * the maintainer asks for it.
 */
class SpeechConversationRealDeviceTest {
    @Test
    fun localTargetTenRounds() = tenRounds(AiTargetLocality.LOCAL, "local")

    @Test
    fun onlineTargetTenRounds() = tenRounds(AiTargetLocality.REMOTE, "online")

    private fun tenRounds(locality: AiTargetLocality, label: String) {
        ChatTestEnvironment(scripted = false).use { environment ->
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
                return@use
            }
            val wanted = arguments.getString("targetModel")
            val target = ready.firstOrNull { target -> wanted == null || target.modelId == wanted }
            assumeTrue("No ready $label target on this device", target != null)
            checkNotNull(target)
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
