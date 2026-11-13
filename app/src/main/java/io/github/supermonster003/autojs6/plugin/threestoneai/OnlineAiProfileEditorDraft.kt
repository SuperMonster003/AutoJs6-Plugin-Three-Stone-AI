package io.github.supermonster003.autojs6.plugin.threestoneai

import android.os.Bundle
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider

/**
 * Non-secret values shown by the service details page (roadmap D105, P5.4). The API key is kept
 * out of this class so that it never appears in equality checks or in [toString].
 */
internal data class OnlineAiProfileEditorValues(
    val provider: OnlineAiProvider,
    val name: String,
    val baseUrl: String,
    val modelIds: List<String>,
    val defaultModelId: String,
    val visionModelIds: List<String>,
    val effort: GenerationEffort,
    val fastMode: Boolean,
)

internal object OnlineAiProfileEditorPolicy {
    /** A typed API key always counts as a change: the editor never shows the saved key. */
    fun hasUnsavedChanges(
        initial: OnlineAiProfileEditorValues,
        current: OnlineAiProfileEditorValues,
        credentialLength: Int,
    ): Boolean = credentialLength > 0 || initial != current

    fun provider(name: String?): OnlineAiProvider? =
        OnlineAiProvider.entries.firstOrNull { it.name == name }

    fun effort(name: String?): GenerationEffort? =
        GenerationEffort.entries.firstOrNull { it.name == name }
}

/**
 * Editor state kept across activity recreation and process death (D105). It lives only in memory
 * and in the saved-instance-state Bundle; nothing here is written to app storage. The Bundle holds
 * [credential] by reference until it is parceled, so only the restoring caller wipes it, once it is
 * back in the field.
 */
internal class OnlineAiProfileEditorDraft(
    val profileId: String?,
    val initial: OnlineAiProfileEditorValues,
    val current: OnlineAiProfileEditorValues,
    val lastSuggestedName: String?,
    val credential: CharArray,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_PROFILE_ID, profileId)
        putBundle(KEY_INITIAL, write(initial))
        putBundle(KEY_CURRENT, write(current))
        putString(KEY_LAST_SUGGESTED_NAME, lastSuggestedName)
        putCharArray(KEY_CREDENTIAL, credential)
    }

    companion object {
        private const val KEY_PROFILE_ID = "profileId"
        private const val KEY_INITIAL = "initial"
        private const val KEY_CURRENT = "current"
        private const val KEY_LAST_SUGGESTED_NAME = "lastSuggestedName"
        private const val KEY_CREDENTIAL = "credential"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_NAME = "name"
        private const val KEY_BASE_URL = "baseUrl"
        private const val KEY_MODEL_IDS = "modelIds"
        private const val KEY_DEFAULT_MODEL_ID = "defaultModelId"
        private const val KEY_VISION_MODEL_IDS = "visionModelIds"
        private const val KEY_EFFORT = "effort"
        private const val KEY_FAST_MODE = "fastMode"

        /** Returns null for a Bundle this version cannot read; the editor then stays closed. */
        fun fromBundle(bundle: Bundle): OnlineAiProfileEditorDraft? {
            val initial = bundle.getBundle(KEY_INITIAL)?.let(::read) ?: return null
            val current = bundle.getBundle(KEY_CURRENT)?.let(::read) ?: return null
            return OnlineAiProfileEditorDraft(
                profileId = bundle.getString(KEY_PROFILE_ID),
                initial = initial,
                current = current,
                lastSuggestedName = bundle.getString(KEY_LAST_SUGGESTED_NAME),
                credential = bundle.getCharArray(KEY_CREDENTIAL) ?: CharArray(0),
            )
        }

        private fun write(values: OnlineAiProfileEditorValues): Bundle = Bundle().apply {
            putString(KEY_PROVIDER, values.provider.name)
            putString(KEY_NAME, values.name)
            putString(KEY_BASE_URL, values.baseUrl)
            putStringArrayList(KEY_MODEL_IDS, ArrayList(values.modelIds))
            putString(KEY_DEFAULT_MODEL_ID, values.defaultModelId)
            putStringArrayList(KEY_VISION_MODEL_IDS, ArrayList(values.visionModelIds))
            putString(KEY_EFFORT, values.effort.name)
            putBoolean(KEY_FAST_MODE, values.fastMode)
        }

        private fun read(bundle: Bundle): OnlineAiProfileEditorValues? {
            val modelIds = bundle.getStringArrayList(KEY_MODEL_IDS)?.toList()
            if (modelIds.isNullOrEmpty()) return null
            return OnlineAiProfileEditorValues(
                provider = OnlineAiProfileEditorPolicy.provider(bundle.getString(KEY_PROVIDER)) ?: return null,
                name = bundle.getString(KEY_NAME) ?: return null,
                baseUrl = bundle.getString(KEY_BASE_URL) ?: return null,
                modelIds = modelIds,
                defaultModelId = bundle.getString(KEY_DEFAULT_MODEL_ID) ?: return null,
                visionModelIds = bundle.getStringArrayList(KEY_VISION_MODEL_IDS)?.toList().orEmpty(),
                effort = OnlineAiProfileEditorPolicy.effort(bundle.getString(KEY_EFFORT)) ?: return null,
                fastMode = bundle.getBoolean(KEY_FAST_MODE),
            )
        }
    }
}
