package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.content.Context

internal enum class ChatFontSize(
    val messageSp: Float,
    val inputSp: Float,
) {
    SMALL(13.5f, 14.5f),
    DEFAULT(15.5f, 16f),
    LARGE(18f, 18f),
    EXTRA_LARGE(21f, 20f),
}

internal data class ChatUiSettings(
    val fontSize: ChatFontSize = ChatFontSize.DEFAULT,
    val followStreamingOutput: Boolean = true,
    val showGenerationUsage: Boolean = true,
)

internal class ChatUiSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): ChatUiSettings = ChatUiSettings(
        fontSize = runCatching {
            ChatFontSize.valueOf(
                preferences.getString(KEY_FONT_SIZE, ChatFontSize.DEFAULT.name).orEmpty(),
            )
        }.getOrDefault(ChatFontSize.DEFAULT),
        followStreamingOutput = preferences.getBoolean(KEY_FOLLOW_OUTPUT, true),
        showGenerationUsage = preferences.getBoolean(KEY_SHOW_USAGE, true),
    )

    fun save(settings: ChatUiSettings) {
        preferences.edit()
            .putString(KEY_FONT_SIZE, settings.fontSize.name)
            .putBoolean(KEY_FOLLOW_OUTPUT, settings.followStreamingOutput)
            .putBoolean(KEY_SHOW_USAGE, settings.showGenerationUsage)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "chat-ui-settings"
        const val KEY_FONT_SIZE = "font-size"
        const val KEY_FOLLOW_OUTPUT = "follow-streaming-output"
        const val KEY_SHOW_USAGE = "show-generation-usage"
    }
}
