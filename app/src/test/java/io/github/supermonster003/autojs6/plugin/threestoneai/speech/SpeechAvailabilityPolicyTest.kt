package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAvailabilityPolicyTest {
    private val macaw = SpeechAvailabilityPolicy.MACAW_PACKAGE

    @Test
    fun `macaw is pinned for both directions while preferred`() {
        val availability = SpeechAvailabilityPolicy.resolve(everything, preferMacaw = true)

        assertEquals(DictationProvider.MACAW, availability.dictation)
        assertEquals(ReadAloudProvider.MACAW, availability.readAloud)
        assertEquals(macaw, availability.dictationPackage)
        assertEquals(macaw, availability.ttsEnginePackage)
        assertTrue(availability.showPreferMacawSetting)
        assertFalse(availability.showMacawInstallHint)
    }

    @Test
    fun `turning the preference off lets the system choose`() {
        val availability = SpeechAvailabilityPolicy.resolve(everything, preferMacaw = false)

        assertEquals(DictationProvider.SYSTEM, availability.dictation)
        assertEquals(ReadAloudProvider.SYSTEM, availability.readAloud)
        assertNull(availability.dictationPackage)
        assertNull(availability.ttsEnginePackage)
        // The row stays visible so the user can turn the preference back on.
        assertTrue(availability.showPreferMacawSetting)
    }

    @Test
    fun `without macaw other recognizers and engines are used`() {
        val state = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = false,
            anyTtsEngine = true,
        )

        for (prefer in listOf(true, false)) {
            val availability = SpeechAvailabilityPolicy.resolve(state, prefer)
            assertEquals(DictationProvider.SYSTEM, availability.dictation)
            assertEquals(ReadAloudProvider.SYSTEM, availability.readAloud)
            assertFalse(availability.showPreferMacawSetting)
            assertTrue(availability.showMacawInstallHint)
        }
    }

    @Test
    fun `nothing installed hides both entries`() {
        val availability = SpeechAvailabilityPolicy.resolve(SpeechDeviceState.NONE, preferMacaw = true)

        assertEquals(DictationProvider.NONE, availability.dictation)
        assertEquals(ReadAloudProvider.NONE, availability.readAloud)
        assertFalse(availability.dictationAvailable)
        assertFalse(availability.readAloudAvailable)
        assertFalse(availability.showPreferMacawSetting)
        assertTrue(availability.showMacawInstallHint)
    }

    @Test
    fun `a device without any engine keeps dictation but not reading`() {
        val state = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = false,
            anyTtsEngine = false,
        )

        val availability = SpeechAvailabilityPolicy.resolve(state, preferMacaw = true)

        assertEquals(DictationProvider.SYSTEM, availability.dictation)
        assertEquals(ReadAloudProvider.NONE, availability.readAloud)
        assertTrue(availability.dictationAvailable)
        assertFalse(availability.readAloudAvailable)
    }

    @Test
    fun `a disabled macaw sheet falls back to the system while its engine stays pinned`() {
        // 3-Macaw Speech can disable its RECOGNIZE_SPEECH sheet in its own settings (V4).
        val state = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = true,
            macawTtsEngine = true,
            anyTtsEngine = true,
        )

        val availability = SpeechAvailabilityPolicy.resolve(state, preferMacaw = true)

        assertEquals(DictationProvider.SYSTEM, availability.dictation)
        assertEquals(ReadAloudProvider.MACAW, availability.readAloud)
        assertTrue(availability.showPreferMacawSetting)
        assertFalse(availability.showMacawInstallHint)
    }

    @Test
    fun `macaw alone still counts as an installed recognizer and engine`() {
        val state = SpeechDeviceState(
            macawRecognizer = true,
            anyRecognizer = false,
            macawTtsEngine = true,
            anyTtsEngine = false,
        )

        val off = SpeechAvailabilityPolicy.resolve(state, preferMacaw = false)

        assertEquals(DictationProvider.SYSTEM, off.dictation)
        assertEquals(ReadAloudProvider.SYSTEM, off.readAloud)
    }

    @Test
    fun `a usable session dictates live while preferred and keeps the sheet as the fallback`() {
        val live = MacawSpeechAvailability(MacawSpeechStatus.AVAILABLE, contractVersion = 3, resident = true)

        val on = SpeechAvailabilityPolicy.resolve(everything, preferMacaw = true, macaw = live)
        assertTrue(on.liveDictation)
        assertTrue(on.dictationAvailable)
        assertTrue(on.sheetDictationAvailable)
        assertEquals(DictationProvider.MACAW, on.dictation)
        assertEquals(live, on.macaw)

        // Turned off, the system chooses a sheet as in 1.7.2 (P6-2).
        assertFalse(SpeechAvailabilityPolicy.resolve(everything, preferMacaw = false, macaw = live).liveDictation)
        // Without the microphone permission the session still opens and asks for it (D209).
        val noMicrophone = live.copy(status = MacawSpeechStatus.NO_MICROPHONE_PERMISSION)
        assertTrue(SpeechAvailabilityPolicy.resolve(everything, preferMacaw = true, macaw = noMicrophone).liveDictation)
        // Probing, or a state without a session: the sheet.
        listOf(
            null,
            MacawSpeechAvailability.NOT_INSTALLED,
            live.copy(status = MacawSpeechStatus.NOT_BINDABLE),
            live.copy(status = MacawSpeechStatus.INCOMPATIBLE),
            live.copy(status = MacawSpeechStatus.NO_RECOGNITION_MODEL),
        ).forEach { macaw ->
            val availability = SpeechAvailabilityPolicy.resolve(everything, preferMacaw = true, macaw = macaw)
            assertFalse(macaw.toString(), availability.liveDictation)
            assertEquals(DictationProvider.MACAW, availability.dictation)
        }
    }

    @Test
    fun `live dictation alone shows the microphone and the preference`() {
        val live = MacawSpeechAvailability(MacawSpeechStatus.AVAILABLE, contractVersion = 1, resident = false)

        val availability = SpeechAvailabilityPolicy.resolve(SpeechDeviceState.NONE, preferMacaw = true, macaw = live)

        assertEquals(DictationProvider.NONE, availability.dictation)
        assertTrue(availability.dictationAvailable)
        assertFalse("No sheet to fall back to", availability.sheetDictationAvailable)
        assertTrue(availability.showPreferMacawSetting)
        assertFalse(availability.showMacawInstallHint)
    }

    private val everything = SpeechDeviceState(
        macawRecognizer = true,
        anyRecognizer = true,
        macawTtsEngine = true,
        anyTtsEngine = true,
    )
}
