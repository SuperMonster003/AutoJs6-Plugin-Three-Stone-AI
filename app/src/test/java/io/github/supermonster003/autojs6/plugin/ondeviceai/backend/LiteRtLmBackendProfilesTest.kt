package io.github.supermonster003.autojs6.plugin.ondeviceai.backend

import org.autojs.plugin.ondeviceai.api.OnDeviceAiBackendAvailability
import org.autojs.plugin.ondeviceai.api.OnDeviceAiBackendProfile
import org.autojs.plugin.ondeviceai.api.OnDeviceAiBackendUnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LiteRtLmBackendProfilesTest {
    @Test
    fun supportedAbiAndOpenClExposeCpuAndGpuButKeepNpuUnavailable() {
        val profiles = LiteRtLmBackendCompatibilityPolicy.evaluate(
            supportedAbi = true,
            openClLibraryAvailable = true,
        ).associateBy { it.profileId }

        assertEquals(OnDeviceAiBackendAvailability.AVAILABLE, profiles.getValue("cpu").availability)
        assertEquals(OnDeviceAiBackendAvailability.AVAILABLE, profiles.getValue("gpu").availability)
        assertEquals(OnDeviceAiBackendAvailability.UNAVAILABLE, profiles.getValue("npu").availability)
        assertEquals(
            OnDeviceAiBackendUnavailableReason.NPU_RUNTIME_NOT_PACKAGED,
            profiles.getValue("npu").unavailableReason,
        )
    }

    @Test
    fun missingOpenClDisablesOnlyGpuOnASupportedAbi() {
        val profiles = LiteRtLmBackendCompatibilityPolicy.evaluate(
            supportedAbi = true,
            openClLibraryAvailable = false,
        ).associateBy { it.profileId }

        assertEquals(OnDeviceAiBackendAvailability.AVAILABLE, profiles.getValue("cpu").availability)
        assertEquals(
            OnDeviceAiBackendUnavailableReason.OPENCL_LIBRARY_UNAVAILABLE,
            profiles.getValue("gpu").unavailableReason,
        )
    }

    @Test
    fun unsupportedAbiFailsEveryProfileBeforeAnyOpenClProbe() {
        var probeCount = 0
        val detector = LiteRtLmBackendCompatibilityDetector(
            supportedAbis = listOf("armeabi-v7a"),
            loadOpenClLibrary = { probeCount += 1 },
        )

        detector.profiles.forEach { profile ->
            assertEquals(OnDeviceAiBackendAvailability.UNAVAILABLE, profile.availability)
            assertEquals(OnDeviceAiBackendUnavailableReason.ABI_UNSUPPORTED, profile.unavailableReason)
        }
        assertEquals(0, probeCount)
    }

    @Test
    fun detectorCachesTheNativeProbeAndRejectsUnavailableProfiles() {
        var probeCount = 0
        val detector = LiteRtLmBackendCompatibilityDetector(
            supportedAbis = listOf("arm64-v8a"),
            loadOpenClLibrary = { probeCount += 1 },
        )

        assertEquals(LiteRtLmBackendProfile.GPU, detector.requireAvailable(OnDeviceAiBackendProfile.GPU))
        detector.profiles
        assertEquals(1, probeCount)
        assertThrows(IllegalArgumentException::class.java) {
            detector.requireAvailable(OnDeviceAiBackendProfile.NPU)
        }
    }
}
