package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAiSessionStateTest {
    @Test
    fun acceptsExactlyOneTerminalTransition() {
        val state = OnDeviceAiSessionState()
        state.start()
        assertTrue(state.complete())
        assertFalse(state.cancel())
        assertEquals(OnDeviceAiSessionState.Terminal.COMPLETED, state.terminal)
        assertTrue(state.close())
        assertFalse(state.close())
    }
}
