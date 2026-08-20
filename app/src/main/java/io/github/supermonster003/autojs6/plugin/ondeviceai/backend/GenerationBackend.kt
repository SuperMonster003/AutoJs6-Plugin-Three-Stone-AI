package io.github.supermonster003.autojs6.plugin.ondeviceai.backend

import java.io.Closeable

internal enum class GenerationRole {
    SYSTEM,
    USER,
    ASSISTANT,
}

internal data class GenerationMessage(
    val role: GenerationRole,
    val textParts: List<String>,
)

internal data class GenerationRequest(
    val history: List<GenerationMessage>,
    val prompt: GenerationMessage,
    val maximumOutputTokens: Int?,
)

internal interface GenerationListener {
    fun onTextDelta(text: String)
    fun onCompleted()
    fun onFailed(error: Throwable)
}

internal interface GenerationBackend : Closeable {
    fun start(request: GenerationRequest, listener: GenerationListener)
    fun cancel()
}

internal fun interface GenerationBackendFactory {
    fun create(modelPath: String): GenerationBackend
}
