package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.autojs.plugin.speech.api.SpeechContract

/** Why a binding to the speech service of 3-Macaw Speech gave no capabilities (roadmap D208). */
internal enum class MacawBindFailure {
    /** `bindService` threw `SecurityException`: AutoJs6, which defines `org.autojs.permission.PLUGIN`, is not installed. */
    NO_PLUGIN_PERMISSION,

    /** The service was not found, answered a null binding or died while it was read. */
    REFUSED,

    /** The service did not connect, or did not answer its capabilities, within the probe time. */
    TIMEOUT,
}

/** What one probe of 3-Macaw Speech's `org.autojs.plugin.SPEECH` service found; plain values, no Android types. */
internal data class MacawSpeechProbe(
    val installed: Boolean,
    /** Null once the capabilities were read over the Binder. */
    val bindFailure: MacawBindFailure? = if (installed) MacawBindFailure.REFUSED else null,
    /** `speechContractVersion`, the base version; null when absent. */
    val baseContractVersion: Int? = null,
    /** `speechMaxContractVersion`, the highest version the build implements; null when absent. */
    val maxContractVersion: Int? = null,
    val features: Set<String> = emptySet(),
    /** `speechMicrophoneGranted`: 3-Macaw Speech holds `RECORD_AUDIO` right now. */
    val microphoneGranted: Boolean = false,
    /** Installed models of kind `asr`. */
    val recognitionModels: Int = 0,
    /** Installed models of kind `kws`. */
    val wakeModels: Int = 0,
) {
    val bound: Boolean get() = installed && bindFailure == null

    companion object {
        val NOT_INSTALLED = MacawSpeechProbe(installed = false)
    }
}

/** The state of 3-Macaw Speech as the chat and the voice settings see it, in the order it is checked (roadmap D208). */
internal enum class MacawSpeechStatus {
    NOT_INSTALLED,

    /** Installed, but its speech service cannot be bound (no AutoJs6) or did not answer. */
    NOT_BINDABLE,

    /** No contract version both sides implement, or no streaming recognition (`asrStreaming`). */
    INCOMPATIBLE,

    /** No speech recognition model is installed; its own sheet guides the download. */
    NO_RECOGNITION_MODEL,

    /** Everything is there except the microphone permission, which a session asks for (`PERMISSION_DENIED`). */
    NO_MICROPHONE_PERMISSION,

    AVAILABLE,
}

/** The resolved state of 3-Macaw Speech. */
internal data class MacawSpeechAvailability(
    val status: MacawSpeechStatus,
    /** The agreed contract version (`SpeechContract.negotiate`), 0 when there is none. */
    val contractVersion: Int,
    /** The resident listener (P6-4) can run: version 3 with `kws` and `speechBackgroundWake`, and a wake model. */
    val resident: Boolean,
) {
    /**
     * Live dictation in the input box (P6-2) opens a session. Without the microphone permission it still does: the
     * session fails with `PERMISSION_DENIED`, which leads to 3-Macaw Speech's permission screen (roadmap D209).
     */
    val liveDictation: Boolean
        get() = status == MacawSpeechStatus.AVAILABLE || status == MacawSpeechStatus.NO_MICROPHONE_PERMISSION

    companion object {
        val NOT_INSTALLED = MacawSpeechAvailability(MacawSpeechStatus.NOT_INSTALLED, contractVersion = 0, resident = false)
    }
}

/** Resolves a [MacawSpeechProbe] (roadmap D203, D208). Pure, for unit tests. */
internal object MacawSpeechAvailabilityPolicy {
    /** The features the resident listener needs beside an installed wake model (D210). */
    val RESIDENT_FEATURES = setOf(SpeechContract.FEATURE_KWS, SpeechContract.FEATURE_BACKGROUND_WAKE)

    fun resolve(probe: MacawSpeechProbe): MacawSpeechAvailability {
        if (!probe.installed) return MacawSpeechAvailability.NOT_INSTALLED
        if (!probe.bound) return MacawSpeechAvailability(MacawSpeechStatus.NOT_BINDABLE, 0, resident = false)
        val agreed = probe.baseContractVersion?.let { base -> negotiate(base, probe.maxContractVersion) } ?: 0
        // A feature of a later version counts only when that version was agreed (protocol "Contract version 3").
        val features = probe.features.filterTo(mutableSetOf()) { feature ->
            val version = SpeechContract.featureVersion(feature)
            version in 1..agreed
        }
        if (agreed == 0 || SpeechContract.FEATURE_ASR_STREAMING !in features) {
            return MacawSpeechAvailability(MacawSpeechStatus.INCOMPATIBLE, agreed, resident = false)
        }
        val resident = agreed >= SpeechContract.CALL_VERSION_3 && features.containsAll(RESIDENT_FEATURES) &&
            probe.wakeModels > 0
        val status = when {
            probe.recognitionModels <= 0 -> MacawSpeechStatus.NO_RECOGNITION_MODEL
            !probe.microphoneGranted -> MacawSpeechStatus.NO_MICROPHONE_PERMISSION
            else -> MacawSpeechStatus.AVAILABLE
        }
        return MacawSpeechAvailability(status, agreed, resident)
    }

    /** `SpeechContract.negotiate`, guarded: a malformed answer counts as incompatible. */
    fun negotiate(base: Int, max: Int?): Int = runCatching { SpeechContract.negotiate(base, max) }.getOrDefault(0)
}
