package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.autojs.plugin.speech.api.SpeechContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap P6-1 (D203, D208): the states of 3-Macaw Speech and the "may reside" verdict per agreed version. */
class MacawSpeechAvailabilityPolicyTest {
    private val v1Features = setOf(
        SpeechContract.FEATURE_ASR,
        SpeechContract.FEATURE_ASR_STREAMING,
        SpeechContract.FEATURE_VAD,
        SpeechContract.FEATURE_KWS,
        SpeechContract.FEATURE_TTS,
        SpeechContract.FEATURE_MODELS,
    )
    private val v3Features = v1Features + setOf(
        SpeechContract.FEATURE_PREPARE,
        SpeechContract.FEATURE_RESIDENT,
        SpeechContract.FEATURE_BACKGROUND_WAKE,
    )

    /** 3-Macaw Speech 1.6.x as probed on a phone with every model and the microphone. */
    private val ready = MacawSpeechProbe(
        installed = true,
        bindFailure = null,
        baseContractVersion = 1,
        maxContractVersion = 3,
        features = v3Features,
        microphoneGranted = true,
        recognitionModels = 2,
        wakeModels = 1,
    )

    @Test
    fun `not installed`() {
        val availability = MacawSpeechAvailabilityPolicy.resolve(MacawSpeechProbe.NOT_INSTALLED)

        assertEquals(MacawSpeechStatus.NOT_INSTALLED, availability.status)
        assertFalse(availability.liveDictation)
        assertFalse(availability.resident)
        assertEquals(0, availability.contractVersion)
    }

    @Test
    fun `every binding failure is not bindable`() {
        MacawBindFailure.entries.forEach { failure ->
            val availability = MacawSpeechAvailabilityPolicy.resolve(ready.copy(bindFailure = failure))

            assertEquals(failure.name, MacawSpeechStatus.NOT_BINDABLE, availability.status)
            assertFalse(availability.liveDictation)
            assertFalse(availability.resident)
        }
        // Installed without a successful read defaults to a refused binding.
        assertEquals(
            MacawSpeechStatus.NOT_BINDABLE,
            MacawSpeechAvailabilityPolicy.resolve(MacawSpeechProbe(installed = true)).status,
        )
    }

    @Test
    fun `an unknown base version or no streaming recognition is incompatible`() {
        listOf(
            ready.copy(baseContractVersion = null),
            ready.copy(baseContractVersion = 0),
            ready.copy(baseContractVersion = 9, maxContractVersion = 9),
            ready.copy(features = v3Features - SpeechContract.FEATURE_ASR_STREAMING),
        ).forEach { probe ->
            val availability = MacawSpeechAvailabilityPolicy.resolve(probe)

            assertEquals(probe.toString(), MacawSpeechStatus.INCOMPATIBLE, availability.status)
            assertFalse(availability.liveDictation)
            assertFalse(availability.resident)
        }
    }

    @Test
    fun `a missing recognition model comes before the microphone permission`() {
        val noModel = MacawSpeechAvailabilityPolicy.resolve(ready.copy(recognitionModels = 0, microphoneGranted = false))
        val noMicrophone = MacawSpeechAvailabilityPolicy.resolve(ready.copy(microphoneGranted = false))

        assertEquals(MacawSpeechStatus.NO_RECOGNITION_MODEL, noModel.status)
        // The sheet guides the download, so live dictation is not tried.
        assertFalse(noModel.liveDictation)
        assertEquals(MacawSpeechStatus.NO_MICROPHONE_PERMISSION, noMicrophone.status)
        // The session asks for the permission (PERMISSION_DENIED leads to 3-Macaw Speech's dialog, D209).
        assertTrue(noMicrophone.liveDictation)
    }

    @Test
    fun `available`() {
        val availability = MacawSpeechAvailabilityPolicy.resolve(ready)

        assertEquals(MacawSpeechStatus.AVAILABLE, availability.status)
        assertEquals(3, availability.contractVersion)
        assertTrue(availability.liveDictation)
        assertTrue(availability.resident)
    }

    @Test
    fun `residing needs version 3 with kws, background wake and a wake model`() {
        // 3-Macaw Speech 1.0.0 - 1.2.x: version 1 only. Live dictation works, residing does not.
        val v1 = MacawSpeechAvailabilityPolicy.resolve(ready.copy(maxContractVersion = null, features = v1Features))
        assertEquals(1, v1.contractVersion)
        assertTrue(v1.liveDictation)
        assertFalse(v1.resident)

        // Version 2 (1.3.0 - 1.5.x); a version 3 feature it lists does not count below version 3.
        val v2 = MacawSpeechAvailabilityPolicy.resolve(ready.copy(maxContractVersion = 2))
        assertEquals(2, v2.contractVersion)
        assertTrue(v2.liveDictation)
        assertFalse(v2.resident)

        // Version 3 without the features (the P11.1 builds of 1.6.0).
        val v3Bare = MacawSpeechAvailabilityPolicy.resolve(ready.copy(features = v1Features))
        assertEquals(3, v3Bare.contractVersion)
        assertFalse(v3Bare.resident)

        assertFalse(MacawSpeechAvailabilityPolicy.resolve(ready.copy(wakeModels = 0)).resident)
        assertFalse(MacawSpeechAvailabilityPolicy.resolve(ready.copy(features = v3Features - SpeechContract.FEATURE_KWS)).resident)
        assertFalse(
            MacawSpeechAvailabilityPolicy.resolve(ready.copy(features = v3Features - SpeechContract.FEATURE_BACKGROUND_WAKE)).resident,
        )
        // Residing does not depend on the recognition model or the microphone permission of dictation.
        assertTrue(MacawSpeechAvailabilityPolicy.resolve(ready.copy(microphoneGranted = false)).resident)
    }

    @Test
    fun `the agreed version is the smaller highest version`() {
        assertEquals(1, MacawSpeechAvailabilityPolicy.negotiate(1, null))
        assertEquals(2, MacawSpeechAvailabilityPolicy.negotiate(1, 2))
        assertEquals(3, MacawSpeechAvailabilityPolicy.negotiate(1, 3))
        // A later plugin is met at this app's version 3.
        assertEquals(3, MacawSpeechAvailabilityPolicy.negotiate(1, 7))
        assertEquals(0, MacawSpeechAvailabilityPolicy.negotiate(4, 4))
    }
}
