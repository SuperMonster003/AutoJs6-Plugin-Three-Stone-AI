package io.github.supermonster003.autojs6.plugin.ai.text.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextSessionStateTest {
    @Test
    fun acceptsExactlyOneTerminalTransition() {
        val state = AiTextSessionState()
        state.start()
        assertTrue(state.complete())
        assertFalse(state.cancel())
        assertEquals(AiTextSessionState.Terminal.COMPLETED, state.terminal)
        assertTrue(state.close())
        assertFalse(state.close())
    }
}
