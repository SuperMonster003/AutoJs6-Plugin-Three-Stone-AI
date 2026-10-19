package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** Who answers a dictation request (roadmap D8, D12, D22). */
internal enum class DictationProvider {
    /** The request is pinned to the 3-Macaw Speech sheet with `setPackage`. */
    MACAW,

    /** The system resolves the request, which may show its chooser. */
    SYSTEM,

    /** No speech recognition activity answers on this device. */
    NONE,
}

/** Which engine reads replies aloud (roadmap D8, D12, D22). */
internal enum class ReadAloudProvider {
    /** `TextToSpeech` is created with the 3-Macaw Speech package as its engine. */
    MACAW,

    /** `TextToSpeech` uses the system default engine. */
    SYSTEM,

    /** No text-to-speech engine is installed. */
    NONE,
}

/** What the device offers, independent of the user's preference. */
internal data class SpeechDeviceState(
    /** `RECOGNIZE_SPEECH` resolves with the 3-Macaw Speech package set (its sheet is enabled). */
    val macawRecognizer: Boolean,
    /** `RECOGNIZE_SPEECH` resolves without a package. */
    val anyRecognizer: Boolean,
    /** A `TTS_SERVICE` service exists in the 3-Macaw Speech package. */
    val macawTtsEngine: Boolean,
    /** Any `TTS_SERVICE` service exists, which is what `TextToSpeech.getEngines()` lists. */
    val anyTtsEngine: Boolean,
) {
    companion object {
        val NONE = SpeechDeviceState(
            macawRecognizer = false,
            anyRecognizer = false,
            macawTtsEngine = false,
            anyTtsEngine = false,
        )
    }
}

/** The resolved voice entry points for the chat and the settings screen. */
internal data class SpeechAvailability(
    val dictation: DictationProvider,
    val readAloud: ReadAloudProvider,
    /** The "Prefer 3-Macaw Speech" row is shown only when either Macaw entry resolves (D17). */
    val showPreferMacawSetting: Boolean,
    /** Neither Macaw entry resolves, so the settings explain how to get offline speech (D8, D17). */
    val showMacawInstallHint: Boolean,
) {
    /** The package that dictation is pinned to, or null to let the system choose. */
    val dictationPackage: String?
        get() = SpeechAvailabilityPolicy.MACAW_PACKAGE.takeIf { dictation == DictationProvider.MACAW }

    /** The engine package for `TextToSpeech`, or null for the system default engine. */
    val ttsEnginePackage: String?
        get() = SpeechAvailabilityPolicy.MACAW_PACKAGE.takeIf { readAloud == ReadAloudProvider.MACAW }

    val dictationAvailable: Boolean get() = dictation != DictationProvider.NONE
    val readAloudAvailable: Boolean get() = readAloud != ReadAloudProvider.NONE
}

/**
 * Resolution rules for the system speech interfaces (roadmap D8, D12, D22). With the preference on and
 * a 3-Macaw Speech entry resolvable, that entry is used directly so several recognizers no longer bring
 * up the system chooser; otherwise the system decides. A Macaw sheet without a downloaded model still
 * counts as resolvable: the sheet itself guides the model installation (Stove D59).
 */
internal object SpeechAvailabilityPolicy {
    const val MACAW_PACKAGE = "io.github.supermonster003.autojs6.plugin.three.macaw.speech"

    fun resolve(state: SpeechDeviceState, preferMacaw: Boolean): SpeechAvailability {
        val anyRecognizer = state.anyRecognizer || state.macawRecognizer
        val anyEngine = state.anyTtsEngine || state.macawTtsEngine
        val dictation = when {
            preferMacaw && state.macawRecognizer -> DictationProvider.MACAW
            anyRecognizer -> DictationProvider.SYSTEM
            else -> DictationProvider.NONE
        }
        val readAloud = when {
            preferMacaw && state.macawTtsEngine -> ReadAloudProvider.MACAW
            anyEngine -> ReadAloudProvider.SYSTEM
            else -> ReadAloudProvider.NONE
        }
        val macawResolvable = state.macawRecognizer || state.macawTtsEngine
        return SpeechAvailability(
            dictation = dictation,
            readAloud = readAloud,
            showPreferMacawSetting = macawResolvable,
            showMacawInstallHint = !macawResolvable,
        )
    }
}
