package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import io.github.supermonster003.autojs6.plugin.threestoneai.speech.VoicePhase.IDLE
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.VoicePhase.LISTENING
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.VoicePhase.SPEAKING
import io.github.supermonster003.autojs6.plugin.threestoneai.speech.VoicePhase.THINKING
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
