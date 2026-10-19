package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.supermonster003.autojs6.plugin.threestoneai.ChatActivity
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.composer
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.ChatTestEnvironment.Companion.waitUntil
import io.github.supermonster003.autojs6.plugin.threestoneai.voice.VoiceCommandStore
import org.junit.Test

/**
 * Checks that need a person (roadmap 10.5, E4 of P5-2, P5-4 and P5-5). Each test opens the chat with
 * the scripted target and the device's real recognizer and engine, then waits for the maintainer,
 * who operates every system dialog. Only timings, kinds and lengths are recorded, never text.
 * Run them one at a time with `.python/device_speech_check.py --only manual-...`.
 */
class SpeechManualDeviceTest {
    /** Tap the microphone once and say a sentence (or cancel). */
    @Test
    fun dictateOnce() {
        ChatTestEnvironment().use { environment ->
            val availability = SpeechDeviceProbe.availability(environment.context, preferMacaw = preferMacaw(environment))
            environment.launchChat().use { scenario ->
                waitUntil("the maintainer dictates", timeoutMillis = WAIT_MILLIS) {
                    var done = false
                    scenario.onActivity { activity ->
                        done = activity.lastDictationResult != null && !activity.dictationPending
                    }
                    done
                }
                var values = emptyMap<String, Any?>()
                scenario.onActivity { activity ->
                    val result = checkNotNull(activity.lastDictationResult)
                    values = linkedMapOf(
                        "dictation" to availability.dictation.name,
                        "outcome" to result.outcome,
                        "requestToResultMillis" to result.millis,
                        "recognizedCharacters" to result.characters,
                        "composerCharacters" to composer(activity).text.length,
                    )
                }
                SpeechDeviceReport.receipt("manual-dictation", values)
            }
        }
    }

    /** Long press the microphone, talk for a few rounds, then cancel the recognizer to end. */
    @Test
    fun voiceConversation() {
        ChatTestEnvironment().use { environment ->
            environment.backend.reply = listOf("好的, 我听到了。", "这是第二句回答。")
            environment.backend.chunkDelayMillis = 150L
            val availability = SpeechDeviceProbe.availability(environment.context, preferMacaw = preferMacaw(environment))
            environment.launchChat().use { scenario ->
                waitUntil("the maintainer finishes a voice conversation", timeoutMillis = 3 * WAIT_MILLIS) {
                    var done = false
                    scenario.onActivity { activity ->
                        done = activity.voiceRounds.isNotEmpty() && activity.lastVoiceExit != null &&
                            activity.currentVoicePhase == VoicePhase.IDLE && !activity.dictationPending
                    }
                    done
                }
                val values = linkedMapOf<String, Any?>(
                    "dictation" to availability.dictation.name,
                    "readAloud" to availability.readAloud.name,
                )
                scenario.onActivity { activity ->
                    values["rounds"] = activity.voiceRounds.size
                    values["exit"] = activity.lastVoiceExit
                    activity.voiceRounds.forEachIndexed { index, round ->
                        values["round${index + 1}"] = "listen=${round.listenMillis} firstSound=${round.firstSoundMillis} " +
                            "round=${round.roundMillis}"
                    }
                }
                SpeechDeviceReport.receipt("manual-conversation", values)
            }
        }
    }

    /** Hold the power button, speak and tap "3-Stone AI" in the 3-Macaw Speech assistant. */
    @Test
    fun voiceCommandFromTheAssistant() {
        ChatTestEnvironment().use { environment ->
            VoiceCommandStore(environment.context).lastImmediateSendAtMillis = null
            waitUntil("the assistant hands a question to 3-Stone AI", timeoutMillis = WAIT_MILLIS) {
                environment.backend.prompts.isNotEmpty()
            }
            waitUntil("the answer is read", timeoutMillis = 30_000L) {
                chat(environment)?.firstUtteranceLatencyMillis != null
            }
            val activity = chat(environment)
            val values = linkedMapOf<String, Any?>(
                "questionCharacters" to environment.backend.prompts.first().length,
                "firstSoundMillis" to activity?.firstUtteranceLatencyMillis,
            )
            environment.instrumentation.runOnMainSync {
                values["messages"] = activity?.messageCountForTest
                activity?.finish()
            }
            SpeechDeviceReport.receipt("manual-assistant", values)
        }
    }

    private fun preferMacaw(environment: ChatTestEnvironment): Boolean =
        io.github.supermonster003.autojs6.plugin.threestoneai.ChatUiSettingsStore(environment.context)
            .load().preferMacawSpeech

    private fun chat(environment: ChatTestEnvironment): ChatActivity? {
        var chat: ChatActivity? = null
        environment.instrumentation.runOnMainSync {
            chat = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ChatActivity>().firstOrNull()
        }
        return chat
    }

    private companion object {
        const val WAIT_MILLIS = 120_000L
    }
}
