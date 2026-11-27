package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap P6-1 (D219): the chat binds 3-Macaw Speech with the flags AutoJs6 uses, so its recording is not silenced. */
class MacawSpeechBindFlagsTest {
    private val base = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT

    @Test
    fun capabilitiesFromApi29AndActivityStartsFromApi34() {
        assertEquals(base, MacawSpeech.bindFlags(28))
        assertEquals(base or Context.BIND_INCLUDE_CAPABILITIES, MacawSpeech.bindFlags(29))
        assertEquals(base or Context.BIND_INCLUDE_CAPABILITIES, MacawSpeech.bindFlags(33))
        assertEquals(
            base or Context.BIND_INCLUDE_CAPABILITIES or Context.BIND_ALLOW_ACTIVITY_STARTS,
            MacawSpeech.bindFlags(34),
        )
    }
}
