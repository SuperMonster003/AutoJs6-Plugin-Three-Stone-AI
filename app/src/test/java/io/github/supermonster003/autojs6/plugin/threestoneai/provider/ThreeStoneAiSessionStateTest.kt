package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeStoneAiSessionStateTest {
    @Test
    fun acceptsExactlyOneTerminalTransition() {
        val state = ThreeStoneAiSessionState()
        state.start()
        assertTrue(state.complete())
        assertFalse(state.cancel())
        assertEquals(ThreeStoneAiSessionState.Terminal.COMPLETED, state.terminal)
        assertTrue(state.close())
        assertFalse(state.close())
    }
}
