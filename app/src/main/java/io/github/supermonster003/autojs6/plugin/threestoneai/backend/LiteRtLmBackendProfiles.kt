package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import android.os.Build
import android.os.Process
import dalvik.system.BaseDexClassLoader
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

/** Checks the installed payload without loading JNI; OpenCL is probed only for local profiles. */
internal class LiteRtLmBackendCompatibilityDetector(
    private val processAbis: List<String> = (
        if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
    ).toList(),
    private val findLiteRtLibrary: () -> String? = {
        (LiteRtLmBackendCompatibilityDetector::class.java.classLoader as? BaseDexClassLoader)
            ?.findLibrary("litertlm_jni")
    },
    private val loadOpenClLibrary: () -> Unit = { System.loadLibrary(OPENCL_LIBRARY_NAME) },
) {
    // A Java-only 32-bit APK can also be installed on a 64-bit device. Device ABIs alone
    // must not enable local inference when that installed APK has no LiteRT-LM library.
    val isRuntimeAvailable: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        processAbis.any(NATIVE_ABIS::contains) && findLiteRtLibrary() != null
    }

    val profiles: List<AiBackendProfileInfo> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LiteRtLmBackendCompatibilityPolicy.evaluate(
            supportedAbi = isRuntimeAvailable,
            openClLibraryAvailable = isRuntimeAvailable && canLoadOpenCl(),
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
        val NATIVE_ABIS = setOf("arm64-v8a", "x86_64")
        const val OPENCL_LIBRARY_NAME = "OpenCL"
    }
}
