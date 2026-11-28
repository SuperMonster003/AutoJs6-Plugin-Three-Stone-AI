package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.speech.RecognizerIntent
import io.github.supermonster003.autojs6.plugin.threestoneai.R

/**
 * P5-4 with the fake recognizer sheet, a scripted target and the silent engine: ten rounds in a row and every exit
 * path of roadmap D13. 3-Macaw Speech is unusable here ([ChatTestEnvironment]'s default), which is also P6-3's
 * fallback: without its session the voice conversation keeps listening through the sheet.
 */
class SpeechConversationDeviceTest : SpeechConversationCases() {
    override val live = false
    override val label = "conversation"
    override val failureMessage = R.string.speech_dictation_failed_network

    override fun createEnvironment() = ChatTestEnvironment().also {
        SpeechDeviceProbe.stateOverride = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = false,
            anyTtsEngine = true,
        )
    }

    override fun hear(vararg steps: Heard) {
        environment.script(
            *steps.map { step ->
                when (step) {
                    is Heard.Said -> Activity.RESULT_OK to step.text
                    Heard.NoSpeech -> RecognizerIntent.RESULT_NO_MATCH to ""
                    Heard.Canceled -> Activity.RESULT_CANCELED to ""
                    Heard.Failure -> RecognizerIntent.RESULT_NETWORK_ERROR to ""
                }
            }.toTypedArray(),
        )
    }
}
