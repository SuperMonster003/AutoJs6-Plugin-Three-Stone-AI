package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import io.github.supermonster003.autojs6.plugin.threestoneai.speech.VoicePhase.IDLE
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.VoicePhase.LISTENING
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.VoicePhase.SPEAKING
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.VoicePhase.THINKING
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceConversationMachineTest {
    private val calm = VoiceSituation(editing = false, generating = false)

    private fun next(phase: VoicePhase, event: VoiceEvent, situation: VoiceSituation = calm) =
        VoiceConversationMachine.next(phase, event, situation)

    @Test
    fun `a round listens sends reads and listens again`() {
        var phase = IDLE
        val effects = ArrayList<VoiceEffect>()
        for (event in listOf(
            VoiceEvent.Start,
            VoiceEvent.Recognized("What time is it?"),
            VoiceEvent.ReadingStarted,
            VoiceEvent.ReplyDone,
            VoiceEvent.Recognized("Thanks"),
            VoiceEvent.ReplyDone,
        )) {
            val transition = next(phase, event)
            phase = transition.phase
            effects += transition.effects
        }

        assertEquals(LISTENING, phase)
        assertEquals(
            listOf(
                VoiceEffect.StopReading,
                VoiceEffect.Listen,
                VoiceEffect.Send("What time is it?"),
                VoiceEffect.Listen,
                VoiceEffect.Send("Thanks"),
                VoiceEffect.Listen,
            ),
            effects,
        )
    }

    @Test
    fun `starting cancels editing and stops a running reply first`() {
        assertEquals(
            VoiceTransition(
                LISTENING,
                listOf(VoiceEffect.CancelEditing, VoiceEffect.StopGeneration, VoiceEffect.StopReading, VoiceEffect.Listen),
            ),
            next(IDLE, VoiceEvent.Start, VoiceSituation(editing = true, generating = true)),
        )
        for (phase in listOf(LISTENING, THINKING, SPEAKING)) {
            assertEquals(VoiceTransition(phase), next(phase, VoiceEvent.Start))
        }
    }

    @Test
    fun `the recognizer ends the conversation when canceled silent or failing`() {
        assertEquals(VoiceTransition(IDLE), next(LISTENING, VoiceEvent.RecognitionCanceled))
        assertEquals(VoiceTransition(IDLE), next(LISTENING, VoiceEvent.RecognitionNoMatch))
        assertEquals(
            VoiceTransition(IDLE, listOf(VoiceEffect.ReportFailure(DictationFailure.NETWORK))),
            next(LISTENING, VoiceEvent.RecognitionFailed(DictationFailure.NETWORK)),
        )
        assertEquals(VoiceTransition(IDLE), next(THINKING, VoiceEvent.SendRejected))
    }

    @Test
    fun `a failed or stopped reply ends the conversation and its reading`() {
        for (phase in listOf(THINKING, SPEAKING)) {
            for (event in listOf(VoiceEvent.GenerationFailed, VoiceEvent.GenerationStopped)) {
                assertEquals(VoiceTransition(IDLE, listOf(VoiceEffect.StopReading)), next(phase, event))
            }
            assertEquals(VoiceTransition(IDLE), next(phase, VoiceEvent.ReadingInterrupted))
        }
    }

    @Test
    fun `leaving the chat exit and other conversation changes end it from every phase`() {
        for (phase in listOf(LISTENING, THINKING, SPEAKING)) {
            for (event in listOf(VoiceEvent.LeftChat, VoiceEvent.Exit, VoiceEvent.ConversationChanged)) {
                assertEquals(VoiceTransition(IDLE, listOf(VoiceEffect.StopReading)), next(phase, event))
            }
        }
    }

    @Test
    fun `a tap interrupts only while speaking`() {
        assertEquals(
            VoiceTransition(LISTENING, listOf(VoiceEffect.StopGeneration, VoiceEffect.StopReading, VoiceEffect.Listen)),
            next(SPEAKING, VoiceEvent.Interrupt, VoiceSituation(editing = false, generating = true)),
        )
        assertEquals(
            VoiceTransition(LISTENING, listOf(VoiceEffect.StopReading, VoiceEffect.Listen)),
            next(SPEAKING, VoiceEvent.Interrupt),
        )
        assertEquals(VoiceTransition(THINKING), next(THINKING, VoiceEvent.Interrupt))
        assertEquals(VoiceTransition(LISTENING), next(LISTENING, VoiceEvent.Interrupt))
    }

    // P6-3 (D209, D220): a round on 3-Macaw Speech's session ends in one of the recognizer events above.
    private fun round(end: LiveDictationEnd, text: String? = null) = LiveVoiceRoundPolicy.outcome(end, text)

    private fun ended(event: VoiceEvent, keepText: Boolean = true) = LiveVoiceRound.Ended(event, keepText)

    @Test
    fun `a session round sends its final however the session stops`() {
        val sent = ended(VoiceEvent.Recognized("What time is it?"), keepText = false)

        assertEquals(sent, round(LiveDictationEnd.Stopped, "What time is it?"))
        assertEquals(sent, round(LiveDictationEnd.Stopped, "  What time is it? "))
        // A timeout or the five minutes racing the automatic finish.
        assertEquals(sent, round(LiveDictationEnd.Timeout(heard = true), "What time is it?"))
        assertEquals(sent, round(LiveDictationEnd.MaxDuration, "What time is it?"))
        // A whole sentence before a failure is still sent.
        assertEquals(sent, round(LiveDictationEnd.Failed(SpeechErrorCodes.ENGINE_FAILED, heard = true), "What time is it?"))
    }

    @Test
    fun `a session round without speech ends the conversation as the sheet did`() {
        // 8 s without speech is RESULT_NO_MATCH of P5-4, and so is a finish before anything was said.
        assertEquals(ended(VoiceEvent.RecognitionNoMatch), round(LiveDictationEnd.Timeout(heard = false)))
        assertEquals(ended(VoiceEvent.RecognitionNoMatch), round(LiveDictationEnd.Stopped))
        assertEquals(ended(VoiceEvent.RecognitionNoMatch), round(LiveDictationEnd.Stopped, " "))
        // A partial without its final stays in the input box.
        assertEquals(ended(VoiceEvent.RecognitionNoMatch), round(LiveDictationEnd.Timeout(heard = true)))
        assertEquals(ended(VoiceEvent.RecognitionNoMatch), round(LiveDictationEnd.MaxDuration))
        // Leaving the chat, the exit button or a typed message cancel the round; the shown text stays (D215).
        assertEquals(ended(VoiceEvent.RecognitionCanceled), round(LiveDictationEnd.Cancelled))
        assertEquals(ended(VoiceEvent.RecognitionCanceled), round(LiveDictationEnd.Cancelled, "half a sentence"))

        assertEquals(VoiceTransition(IDLE), next(LISTENING, VoiceEvent.RecognitionNoMatch))
        assertEquals(VoiceTransition(IDLE), next(LISTENING, VoiceEvent.RecognitionCanceled))
    }

    @Test
    fun `a busy microphone or a missing model fall back to the sheet for the round`() {
        for (code in listOf(
            SpeechErrorCodes.SESSION_BUSY,
            SpeechErrorCodes.MODEL_MISSING,
            SpeechErrorCodes.SESSION_CLOSED,
            SpeechErrorCodes.PLUGIN_UNAVAILABLE,
            SpeechErrorCodes.FEATURE_UNAVAILABLE,
        )) {
            assertEquals(code, LiveVoiceRound.Sheet, round(LiveDictationEnd.Failed(code, heard = false)))
        }
        assertEquals(
            LiveVoiceRound.MicrophonePermission,
            round(LiveDictationEnd.Failed(SpeechErrorCodes.PERMISSION_DENIED, heard = false)),
        )
        // The sheet answers the round with the recognizer events of P5-4, so the machine still listens meanwhile.
        assertEquals(VoiceTransition(LISTENING), next(LISTENING, VoiceEvent.ReplyDone))
        assertEquals(
            VoiceTransition(THINKING, listOf(VoiceEffect.Send("from the sheet"))),
            next(LISTENING, VoiceEvent.Recognized("from the sheet")),
        )
    }

    @Test
    fun `other session errors are reported and end the conversation`() {
        val reported = ended(VoiceEvent.RecognitionFailed(DictationFailure.OTHER))

        assertEquals(reported, round(LiveDictationEnd.Failed(SpeechErrorCodes.ENGINE_FAILED, heard = false)))
        assertEquals(reported, round(LiveDictationEnd.Failed(SpeechErrorCodes.INTERNAL, heard = false)))
        // Something was heard but not finished: the text stays and the failure is reported, not the sheet.
        assertEquals(reported, round(LiveDictationEnd.Failed(SpeechErrorCodes.SESSION_BUSY, heard = true)))
        assertEquals(
            VoiceTransition(IDLE, listOf(VoiceEffect.ReportFailure(DictationFailure.OTHER))),
            next(LISTENING, VoiceEvent.RecognitionFailed(DictationFailure.OTHER)),
        )
    }

    @Test
    fun `stale events change nothing`() {
        val events = listOf(
            VoiceEvent.Recognized("late"),
            VoiceEvent.RecognitionCanceled,
            VoiceEvent.RecognitionNoMatch,
            VoiceEvent.RecognitionFailed(DictationFailure.OTHER),
            VoiceEvent.SendRejected,
            VoiceEvent.ReadingStarted,
            VoiceEvent.ReplyDone,
            VoiceEvent.GenerationFailed,
            VoiceEvent.GenerationStopped,
            VoiceEvent.ReadingInterrupted,
            VoiceEvent.Interrupt,
            VoiceEvent.Exit,
            VoiceEvent.LeftChat,
            VoiceEvent.ConversationChanged,
        )
        events.forEach { event -> assertEquals(VoiceTransition(IDLE), next(IDLE, event)) }
        assertEquals(VoiceTransition(SPEAKING), next(SPEAKING, VoiceEvent.ReadingStarted))
        assertEquals(VoiceTransition(SPEAKING), next(SPEAKING, VoiceEvent.Recognized("late")))
        assertEquals(VoiceTransition(LISTENING), next(LISTENING, VoiceEvent.ReplyDone))
        assertEquals(VoiceTransition(LISTENING), next(LISTENING, VoiceEvent.GenerationFailed))
    }
}
