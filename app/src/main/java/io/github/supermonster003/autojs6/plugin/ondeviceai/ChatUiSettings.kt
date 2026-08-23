package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.content.Context
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationSamplingOptions
import org.autojs.plugin.ondeviceai.api.OnDeviceAiSamplingDefaults

internal enum class ChatFontSize(
    val messageSp: Float,
    val inputSp: Float,
) {
    SMALL(13.5f, 14.5f),
    DEFAULT(15.5f, 16f),
    LARGE(18f, 18f),
    EXTRA_LARGE(21f, 20f),
}

internal enum class EnterKeyBehavior {
    SEND,
    NEW_LINE,
}

internal data class ChatUiSettings(
    val fontSize: ChatFontSize = ChatFontSize.DEFAULT,
    val followStreamingOutput: Boolean = true,
    val showGenerationUsage: Boolean = true,
    val enterKeyBehavior: EnterKeyBehavior = EnterKeyBehavior.SEND,
    val maximumOutputTokens: Int? = null,
    val maximumOutputTokensDraft: Int = DEFAULT_MAXIMUM_OUTPUT_TOKENS_DRAFT,
    val useModelSamplingDefaults: Boolean = true,
    val temperature: Double = OnDeviceAiSamplingDefaults.TEMPERATURE,
    val topK: Int = OnDeviceAiSamplingDefaults.TOP_K,
    val topP: Double = OnDeviceAiSamplingDefaults.TOP_P,
) {
    init {
        require(maximumOutputTokens == null || maximumOutputTokens > 0)
        require(maximumOutputTokensDraft > 0)
        require(temperature.isFinite() && temperature >= 0.0)
        require(topK > 0)
        require(topP.isFinite() && topP in 0.0..1.0)
    }

    fun samplingOptions(): GenerationSamplingOptions? = if (useModelSamplingDefaults) {
        null
    } else {
        GenerationSamplingOptions(temperature = temperature, topK = topK, topP = topP)
    }

    companion object {
        const val DEFAULT_MAXIMUM_OUTPUT_TOKENS_DRAFT = 1_024
    }
}

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
        enterKeyBehavior = runCatching {
            EnterKeyBehavior.valueOf(
                preferences.getString(KEY_ENTER_KEY_BEHAVIOR, EnterKeyBehavior.SEND.name).orEmpty(),
            )
        }.getOrDefault(EnterKeyBehavior.SEND),
        maximumOutputTokens = if (preferences.getBoolean(KEY_MAXIMUM_OUTPUT_TOKENS_LIMITED, false)) {
            preferences.getInt(
                KEY_MAXIMUM_OUTPUT_TOKENS,
                ChatUiSettings.DEFAULT_MAXIMUM_OUTPUT_TOKENS_DRAFT,
            ).takeIf { it > 0 }
        } else {
            null
        },
        maximumOutputTokensDraft = preferences.getInt(
            KEY_MAXIMUM_OUTPUT_TOKENS_DRAFT,
            ChatUiSettings.DEFAULT_MAXIMUM_OUTPUT_TOKENS_DRAFT,
        ).coerceAtLeast(1),
        useModelSamplingDefaults = preferences.getBoolean(KEY_USE_MODEL_SAMPLING_DEFAULTS, true),
        temperature = preferences.getString(KEY_TEMPERATURE, null)
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: OnDeviceAiSamplingDefaults.TEMPERATURE,
        topK = preferences.getInt(KEY_TOP_K, OnDeviceAiSamplingDefaults.TOP_K).coerceAtLeast(1),
        topP = preferences.getString(KEY_TOP_P, null)
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?: OnDeviceAiSamplingDefaults.TOP_P,
    )

    fun save(settings: ChatUiSettings) {
        preferences.edit()
            .putString(KEY_FONT_SIZE, settings.fontSize.name)
            .putBoolean(KEY_FOLLOW_OUTPUT, settings.followStreamingOutput)
            .putBoolean(KEY_SHOW_USAGE, settings.showGenerationUsage)
            .putString(KEY_ENTER_KEY_BEHAVIOR, settings.enterKeyBehavior.name)
            .putBoolean(KEY_MAXIMUM_OUTPUT_TOKENS_LIMITED, settings.maximumOutputTokens != null)
            .putInt(
                KEY_MAXIMUM_OUTPUT_TOKENS,
                settings.maximumOutputTokens ?: settings.maximumOutputTokensDraft,
            )
            .putInt(KEY_MAXIMUM_OUTPUT_TOKENS_DRAFT, settings.maximumOutputTokensDraft)
            .putBoolean(KEY_USE_MODEL_SAMPLING_DEFAULTS, settings.useModelSamplingDefaults)
            .putString(KEY_TEMPERATURE, settings.temperature.toString())
            .putInt(KEY_TOP_K, settings.topK)
            .putString(KEY_TOP_P, settings.topP.toString())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "chat-ui-settings"
        const val KEY_FONT_SIZE = "font-size"
        const val KEY_FOLLOW_OUTPUT = "follow-streaming-output"
        const val KEY_SHOW_USAGE = "show-generation-usage"
        const val KEY_ENTER_KEY_BEHAVIOR = "enter-key-behavior"
        const val KEY_MAXIMUM_OUTPUT_TOKENS_LIMITED = "maximum-output-tokens-limited"
        const val KEY_MAXIMUM_OUTPUT_TOKENS = "maximum-output-tokens"
        const val KEY_MAXIMUM_OUTPUT_TOKENS_DRAFT = "maximum-output-tokens-draft"
        const val KEY_USE_MODEL_SAMPLING_DEFAULTS = "use-model-sampling-defaults"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_TOP_K = "top-k"
        const val KEY_TOP_P = "top-p"
    }
}
