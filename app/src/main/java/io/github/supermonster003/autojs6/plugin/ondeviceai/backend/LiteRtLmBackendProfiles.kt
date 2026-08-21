package io.github.supermonster003.autojs6.plugin.ondeviceai.backend

import android.os.Build
import io.github.supermonster003.autojs6.plugin.ondeviceai.OnDeviceAiPlugin
import org.autojs.plugin.ondeviceai.api.AiBackendProfileInfo
import org.autojs.plugin.ondeviceai.api.OnDeviceAiBackendAvailability
import org.autojs.plugin.ondeviceai.api.OnDeviceAiBackendProfile
import org.autojs.plugin.ondeviceai.api.OnDeviceAiBackendUnavailableReason

internal enum class LiteRtLmBackendProfile(val protocolId: String) {
    CPU(OnDeviceAiBackendProfile.CPU),
    GPU(OnDeviceAiBackendProfile.GPU),
    NPU(OnDeviceAiBackendProfile.NPU),
    ;

    companion object {
        fun fromProtocolId(profileId: String): LiteRtLmBackendProfile = entries.singleOrNull {
            it.protocolId == profileId
        } ?: throw IllegalArgumentException("Unsupported LiteRT-LM backend profile")
    }
}

/** Pure prerequisite policy; model initialization remains the definitive runtime compatibility test. */
internal object LiteRtLmBackendCompatibilityPolicy {
    fun evaluate(
        supportedAbi: Boolean,
        openClLibraryAvailable: Boolean,
    ): List<AiBackendProfileInfo> = LiteRtLmBackendProfile.entries.map { profile ->
        when {
            !supportedAbi -> unavailable(profile, OnDeviceAiBackendUnavailableReason.ABI_UNSUPPORTED)
            profile == LiteRtLmBackendProfile.CPU -> available(profile)
            profile == LiteRtLmBackendProfile.GPU && openClLibraryAvailable -> available(profile)
            profile == LiteRtLmBackendProfile.GPU -> unavailable(
                profile,
                OnDeviceAiBackendUnavailableReason.OPENCL_LIBRARY_UNAVAILABLE,
            )
            else -> unavailable(
                profile,
                OnDeviceAiBackendUnavailableReason.NPU_RUNTIME_NOT_PACKAGED,
            )
        }
    }

    private fun available(profile: LiteRtLmBackendProfile) = AiBackendProfileInfo(
        profileId = profile.protocolId,
        availability = OnDeviceAiBackendAvailability.AVAILABLE,
    )

    private fun unavailable(profile: LiteRtLmBackendProfile, reason: String) = AiBackendProfileInfo(
        profileId = profile.protocolId,
        availability = OnDeviceAiBackendAvailability.UNAVAILABLE,
        unavailableReason = reason,
    )
}

/** Process-cached loader probe verifies both system presence and linker-namespace visibility. */
internal class LiteRtLmBackendCompatibilityDetector(
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    private val loadOpenClLibrary: () -> Unit = { System.loadLibrary(OPENCL_LIBRARY_NAME) },
) {
    val profiles: List<AiBackendProfileInfo> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val supportedAbi = supportedAbis.any(OnDeviceAiPlugin.SUPPORTED_ABIS::contains)
        LiteRtLmBackendCompatibilityPolicy.evaluate(
            supportedAbi = supportedAbi,
            openClLibraryAvailable = supportedAbi && canLoadOpenCl(),
        )
    }

    fun requireAvailable(profileId: String): LiteRtLmBackendProfile {
        val profile = LiteRtLmBackendProfile.fromProtocolId(profileId)
        require(
            profiles.single { it.profileId == profileId }.availability ==
                OnDeviceAiBackendAvailability.AVAILABLE,
        ) { "The requested LiteRT-LM backend profile is unavailable on this device" }
        return profile
    }

    private fun canLoadOpenCl(): Boolean = try {
        loadOpenClLibrary()
        true
    } catch (_: LinkageError) {
        false
    } catch (_: SecurityException) {
        false
    }

    private companion object {
        const val OPENCL_LIBRARY_NAME = "OpenCL"
    }
}
