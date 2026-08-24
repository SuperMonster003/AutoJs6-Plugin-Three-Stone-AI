package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import android.os.Build
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import org.autojs.plugin.ai.provider.api.AiBackendProfileInfo
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderBackendUnavailableReason

internal enum class LiteRtLmBackendProfile(val protocolId: String) {
    CPU(AiProviderBackendProfile.CPU),
    GPU(AiProviderBackendProfile.GPU),
    NPU(AiProviderBackendProfile.NPU),
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
            !supportedAbi -> unavailable(profile, AiProviderBackendUnavailableReason.ABI_UNSUPPORTED)
            profile == LiteRtLmBackendProfile.CPU -> available(profile)
            profile == LiteRtLmBackendProfile.GPU && openClLibraryAvailable -> available(profile)
            profile == LiteRtLmBackendProfile.GPU -> unavailable(
                profile,
                AiProviderBackendUnavailableReason.OPENCL_LIBRARY_UNAVAILABLE,
            )
            else -> unavailable(
                profile,
                AiProviderBackendUnavailableReason.NPU_RUNTIME_NOT_PACKAGED,
            )
        }
    }

    private fun available(profile: LiteRtLmBackendProfile) = AiBackendProfileInfo(
        profileId = profile.protocolId,
        availability = AiProviderBackendAvailability.AVAILABLE,
    )

    private fun unavailable(profile: LiteRtLmBackendProfile, reason: String) = AiBackendProfileInfo(
        profileId = profile.protocolId,
        availability = AiProviderBackendAvailability.UNAVAILABLE,
        unavailableReason = reason,
    )
}

/** Process-cached loader probe verifies both system presence and linker-namespace visibility. */
internal class LiteRtLmBackendCompatibilityDetector(
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    private val loadOpenClLibrary: () -> Unit = { System.loadLibrary(OPENCL_LIBRARY_NAME) },
) {
    val profiles: List<AiBackendProfileInfo> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val supportedAbi = supportedAbis.any(ThreeStoneAiPlugin.SUPPORTED_ABIS::contains)
        LiteRtLmBackendCompatibilityPolicy.evaluate(
            supportedAbi = supportedAbi,
            openClLibraryAvailable = supportedAbi && canLoadOpenCl(),
        )
    }

    fun requireAvailable(profileId: String): LiteRtLmBackendProfile {
        val profile = LiteRtLmBackendProfile.fromProtocolId(profileId)
        require(
            profiles.single { it.profileId == profileId }.availability ==
                AiProviderBackendAvailability.AVAILABLE,
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
