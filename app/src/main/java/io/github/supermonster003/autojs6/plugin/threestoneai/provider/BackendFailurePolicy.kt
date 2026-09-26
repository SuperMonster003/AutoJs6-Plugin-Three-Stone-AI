package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.common.api.AiRetryDisposition

/** Preserves only closed, non-sensitive online failure categories across the Provider boundary. */
internal object BackendFailurePolicy {
    fun error(error: Throwable): AiError = AiError(
        code = AiErrorCode.PROVIDER_FAILED,
        message = "AI generation failed",
        retryDisposition = AiRetryDisposition.NEVER,
        providerCode = (error as? OnlineAiFailureException)?.reason?.let { "ONLINE_" + it.name },
    )
}
