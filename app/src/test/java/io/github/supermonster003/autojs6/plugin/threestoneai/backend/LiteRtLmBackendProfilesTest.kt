package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderBackendUnavailableReason
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

        assertEquals(AiProviderBackendAvailability.AVAILABLE, profiles.getValue("cpu").availability)
        assertEquals(AiProviderBackendAvailability.AVAILABLE, profiles.getValue("gpu").availability)
        assertEquals(AiProviderBackendAvailability.UNAVAILABLE, profiles.getValue("npu").availability)
        assertEquals(
            AiProviderBackendUnavailableReason.NPU_RUNTIME_NOT_PACKAGED,
            profiles.getValue("npu").unavailableReason,
        )
    }

    @Test
    fun missingOpenClDisablesOnlyGpuOnASupportedAbi() {
        val profiles = LiteRtLmBackendCompatibilityPolicy.evaluate(
            supportedAbi = true,
            openClLibraryAvailable = false,
        ).associateBy { it.profileId }

        assertEquals(AiProviderBackendAvailability.AVAILABLE, profiles.getValue("cpu").availability)
        assertEquals(
            AiProviderBackendUnavailableReason.OPENCL_LIBRARY_UNAVAILABLE,
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
            assertEquals(AiProviderBackendAvailability.UNAVAILABLE, profile.availability)
            assertEquals(AiProviderBackendUnavailableReason.ABI_UNSUPPORTED, profile.unavailableReason)
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

        assertEquals(LiteRtLmBackendProfile.GPU, detector.requireAvailable(AiProviderBackendProfile.GPU))
        detector.profiles
        assertEquals(1, probeCount)
        assertThrows(IllegalArgumentException::class.java) {
            detector.requireAvailable(AiProviderBackendProfile.NPU)
        }
    }
}
