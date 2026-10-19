package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.app.Activity
import android.speech.RecognizerIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationOutcomePolicyTest {
    @Test
    fun `codes match the platform constants`() {
        assertEquals(Activity.RESULT_OK, DictationOutcomePolicy.RESULT_OK)
        assertEquals(Activity.RESULT_CANCELED, DictationOutcomePolicy.RESULT_CANCELED)
        assertEquals(RecognizerIntent.RESULT_NO_MATCH, DictationOutcomePolicy.RESULT_NO_MATCH)
        assertEquals(RecognizerIntent.RESULT_CLIENT_ERROR, DictationOutcomePolicy.RESULT_CLIENT_ERROR)
        assertEquals(RecognizerIntent.RESULT_SERVER_ERROR, DictationOutcomePolicy.RESULT_SERVER_ERROR)
        assertEquals(RecognizerIntent.RESULT_NETWORK_ERROR, DictationOutcomePolicy.RESULT_NETWORK_ERROR)
        assertEquals(RecognizerIntent.RESULT_AUDIO_ERROR, DictationOutcomePolicy.RESULT_AUDIO_ERROR)
    }

    @Test
    fun `only the first result is used`() {
        assertEquals(
            DictationOutcome.Recognized("first"),
            DictationOutcomePolicy.classify(Activity.RESULT_OK, listOf(" first ", "second")),
        )
    }

    @Test
    fun `success without text counts as no speech`() {
        assertEquals(DictationOutcome.NoMatch, DictationOutcomePolicy.classify(Activity.RESULT_OK, null))
        assertEquals(DictationOutcome.NoMatch, DictationOutcomePolicy.classify(Activity.RESULT_OK, emptyList()))
        assertEquals(DictationOutcome.NoMatch, DictationOutcomePolicy.classify(Activity.RESULT_OK, listOf(" ")))
        assertEquals(DictationOutcome.NoMatch, DictationOutcomePolicy.classify(Activity.RESULT_OK, listOf(null)))
    }

    @Test
    fun `cancel and no match stay quiet while failures are reported`() {
        val canceled = DictationOutcomePolicy.classify(Activity.RESULT_CANCELED, null)
        val noMatch = DictationOutcomePolicy.classify(RecognizerIntent.RESULT_NO_MATCH, null)

        assertEquals(DictationOutcome.Canceled, canceled)
        assertEquals(DictationOutcome.NoMatch, noMatch)
        assertFalse(DictationOutcomePolicy.reportsSingleDictation(canceled))
        assertFalse(DictationOutcomePolicy.reportsSingleDictation(noMatch))
        mapOf(
            RecognizerIntent.RESULT_NETWORK_ERROR to DictationFailure.NETWORK,
            RecognizerIntent.RESULT_AUDIO_ERROR to DictationFailure.AUDIO,
            RecognizerIntent.RESULT_CLIENT_ERROR to DictationFailure.OTHER,
            RecognizerIntent.RESULT_SERVER_ERROR to DictationFailure.OTHER,
            42 to DictationFailure.OTHER,
        ).forEach { (code, kind) ->
            val outcome = DictationOutcomePolicy.classify(code, null)
            assertEquals(DictationOutcome.Failed(kind), outcome)
            assertTrue(DictationOutcomePolicy.reportsSingleDictation(outcome))
        }
    }
}
