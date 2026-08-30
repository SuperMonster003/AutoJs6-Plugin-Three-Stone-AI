package io.github.supermonster003.autojs6.plugin.threestoneai.profile

import android.content.Context

internal object OnlineAiBaseUrlHistoryPolicy {
    const val HTTPS_PREFIX = "https://"
    const val MAXIMUM_ENTRIES = 20

    fun normalized(values: Iterable<String>): List<String> = buildList {
        values.forEach { value ->
            val normalized = runCatching { OnlineAiProfileUrls.normalize(value) }.getOrNull()
                ?: return@forEach
            if (normalized !in this) add(normalized)
            if (size >= MAXIMUM_ENTRIES) return@buildList
        }
    }
}

internal class OnlineAiBaseUrlHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): List<String> = OnlineAiBaseUrlHistoryPolicy.normalized(
        preferences.getString(KEY_VALUES, null)
            ?.lineSequence()
            .orEmpty()
            .toList(),
    )

    fun record(baseUrl: String) {
        save(OnlineAiBaseUrlHistoryPolicy.normalized(listOf(baseUrl) + load()))
    }

    fun remove(baseUrls: Set<String>) {
        if (baseUrls.isEmpty()) return
        save(load().filterNot(baseUrls::contains))
    }

    fun clear() = save(emptyList())

    private fun save(values: List<String>) {
        preferences.edit().apply {
            if (values.isEmpty()) remove(KEY_VALUES)
            else putString(KEY_VALUES, values.joinToString("\n"))
        }.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "online-ai-base-url-history"
        const val KEY_VALUES = "values"
    }
}
