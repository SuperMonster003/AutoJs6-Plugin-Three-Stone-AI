package io.github.supermonster003.autojs6.plugin.threestoneai

import android.os.Bundle
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationEffort
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiCustomHeader
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiHeaderValueKind
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProvider

/**
 * Non-secret values shown by the service details page (roadmap D105, P5.4). The API key and typed
 * secret header values are kept out of this class so that they never appear in equality checks or
 * in [toString]; [headers] carries only names, kinds and fixed values (1.8.0 P12.1).
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
    val headers: List<OnlineAiCustomHeader> = emptyList(),
)

internal object OnlineAiProfileEditorPolicy {
    /**
     * A typed API key or secret header value always counts as a change: the editor never shows the
     * saved ones.
     */
    fun hasUnsavedChanges(
        initial: OnlineAiProfileEditorValues,
        current: OnlineAiProfileEditorValues,
        credentialLength: Int,
        typedHeaderSecrets: Int = 0,
    ): Boolean = credentialLength > 0 || typedHeaderSecrets > 0 || initial != current

    fun provider(name: String?): OnlineAiProvider? =
        OnlineAiProvider.entries.firstOrNull { it.name == name }

    fun effort(name: String?): GenerationEffort? =
        GenerationEffort.entries.firstOrNull { it.name == name }
}

/**
 * Editor state kept across activity recreation and process death (D105). It lives only in memory
 * and in the saved-instance-state Bundle; nothing here is written to app storage. The Bundle holds
 * [credential] and the [headerSecrets] arrays (typed, not yet saved secret header values keyed by
 * lower-case name) by reference until it is parceled, so only the restoring caller wipes them, once
 * they are back in the editor. [headersPageOpen] reopens the "Request headers" page.
 */
internal class OnlineAiProfileEditorDraft(
    val profileId: String?,
    val initial: OnlineAiProfileEditorValues,
    val current: OnlineAiProfileEditorValues,
    val lastSuggestedName: String?,
    val credential: CharArray,
    val headerSecrets: Map<String, CharArray> = emptyMap(),
    val headersPageOpen: Boolean = false,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_PROFILE_ID, profileId)
        putBundle(KEY_INITIAL, write(initial))
        putBundle(KEY_CURRENT, write(current))
        putString(KEY_LAST_SUGGESTED_NAME, lastSuggestedName)
        putCharArray(KEY_CREDENTIAL, credential)
        putBundle(
            KEY_HEADER_SECRETS,
            Bundle().apply { headerSecrets.forEach { (key, value) -> putCharArray(key, value) } },
        )
        putBoolean(KEY_HEADERS_PAGE_OPEN, headersPageOpen)
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
        private const val KEY_HEADER_NAMES = "headerNames"
        private const val KEY_HEADER_KINDS = "headerKinds"
        private const val KEY_HEADER_VALUES = "headerValues"
        private const val KEY_HEADER_SECRETS = "headerSecrets"
        private const val KEY_HEADERS_PAGE_OPEN = "headersPageOpen"

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
                headerSecrets = readHeaderSecrets(bundle.getBundle(KEY_HEADER_SECRETS)),
                headersPageOpen = bundle.getBoolean(KEY_HEADERS_PAGE_OPEN),
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
            putStringArrayList(KEY_HEADER_NAMES, ArrayList(values.headers.map(OnlineAiCustomHeader::name)))
            putStringArrayList(KEY_HEADER_KINDS, ArrayList(values.headers.map { it.kind.wireId }))
            putStringArrayList(KEY_HEADER_VALUES, ArrayList(values.headers.map(OnlineAiCustomHeader::value)))
        }

        private fun read(bundle: Bundle): OnlineAiProfileEditorValues? {
            val modelIds = bundle.getStringArrayList(KEY_MODEL_IDS)?.toList()
            if (modelIds.isNullOrEmpty()) return null
            val headers = readHeaders(bundle) ?: return null
            return OnlineAiProfileEditorValues(
                provider = OnlineAiProfileEditorPolicy.provider(bundle.getString(KEY_PROVIDER)) ?: return null,
                name = bundle.getString(KEY_NAME) ?: return null,
                baseUrl = bundle.getString(KEY_BASE_URL) ?: return null,
                modelIds = modelIds,
                defaultModelId = bundle.getString(KEY_DEFAULT_MODEL_ID) ?: return null,
                visionModelIds = bundle.getStringArrayList(KEY_VISION_MODEL_IDS)?.toList().orEmpty(),
                effort = OnlineAiProfileEditorPolicy.effort(bundle.getString(KEY_EFFORT)) ?: return null,
                fastMode = bundle.getBoolean(KEY_FAST_MODE),
                headers = headers,
            )
        }

        /** Headers of a Bundle written by this version, none from an older one, null if damaged. */
        private fun readHeaders(bundle: Bundle): List<OnlineAiCustomHeader>? {
            val names = bundle.getStringArrayList(KEY_HEADER_NAMES) ?: return emptyList()
            val kinds = bundle.getStringArrayList(KEY_HEADER_KINDS) ?: return null
            val values = bundle.getStringArrayList(KEY_HEADER_VALUES) ?: return null
            if (kinds.size != names.size || values.size != names.size) return null
            return names.indices.map { index ->
                val name = names[index] ?: return null
                val kind = OnlineAiHeaderValueKind.entries.firstOrNull { it.wireId == kinds[index] } ?: return null
                OnlineAiCustomHeader(name, kind, values[index])
            }
        }

        private fun readHeaderSecrets(bundle: Bundle?): Map<String, CharArray> {
            if (bundle == null) return emptyMap()
            val result = LinkedHashMap<String, CharArray>()
            bundle.keySet().sorted().forEach { key -> bundle.getCharArray(key)?.let { result[key] = it } }
            return result
        }
    }
}
