package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureReason

internal enum class ChatGenerationFailureKind {
    TARGET_UNAVAILABLE,
    LOCAL_EXECUTION,
    CREDENTIAL,
    PROFILE_CHANGED,
    AUTHENTICATION,
    RATE_LIMIT,
    PROVIDER,
    NETWORK,
    METERED_NETWORK,
    TIMEOUT,
    TLS,
    RESPONSE,
    UNKNOWN,
}

internal data class ChatGenerationFailure(
    val locality: AiTargetLocality,
    val kind: ChatGenerationFailureKind,
    /** "HTTP 400: type: message" for online failures that carried a status or provider text, else null. */
    val detail: String? = null,
)

/**
 * Converts backend failures into a bounded presentation category. Raw exception text is never
 * retained or shown because it may contain a local path or transport detail; the only passthrough is
 * the provider's own bounded, credential-free explanation of an online failure.
 */
internal object ChatGenerationFailurePolicy {
    fun classify(
        target: ConversationTargetSnapshot,
        error: Throwable,
    ): ChatGenerationFailure = ChatGenerationFailure(
        locality = target.locality,
        kind = when {
            error is AiTargetUnavailableException -> ChatGenerationFailureKind.TARGET_UNAVAILABLE
            target.locality == AiTargetLocality.LOCAL ->
                ChatGenerationFailureKind.LOCAL_EXECUTION
            error !is OnlineAiFailureException -> ChatGenerationFailureKind.UNKNOWN
            else -> error.reason.toChatFailureKind()
        },
        detail = (error as? OnlineAiFailureException)
            ?.takeIf { target.locality == AiTargetLocality.REMOTE }
            ?.let(::providerDetail),
    )

    fun providerDetail(failure: OnlineAiFailureException): String? = listOfNotNull(
        failure.statusCode?.let { code -> "HTTP $code" },
        failure.providerDetail,
    ).joinToString(": ").ifEmpty { null }

    private fun OnlineAiFailureReason.toChatFailureKind(): ChatGenerationFailureKind = when (this) {
        OnlineAiFailureReason.CREDENTIAL_UNAVAILABLE -> ChatGenerationFailureKind.CREDENTIAL
        OnlineAiFailureReason.PROFILE_CHANGED -> ChatGenerationFailureKind.PROFILE_CHANGED
        OnlineAiFailureReason.AUTHENTICATION_FAILED,
        OnlineAiFailureReason.PERMISSION_DENIED,
        -> ChatGenerationFailureKind.AUTHENTICATION
        OnlineAiFailureReason.RATE_LIMITED -> ChatGenerationFailureKind.RATE_LIMIT
        OnlineAiFailureReason.REQUEST_REJECTED,
        OnlineAiFailureReason.REDIRECT_REFUSED,
        OnlineAiFailureReason.SERVICE_UNAVAILABLE,
        OnlineAiFailureReason.PROVIDER_ERROR,
        -> ChatGenerationFailureKind.PROVIDER
        OnlineAiFailureReason.NETWORK_UNAVAILABLE -> ChatGenerationFailureKind.NETWORK
        OnlineAiFailureReason.METERED_NETWORK_DISALLOWED ->
            ChatGenerationFailureKind.METERED_NETWORK
        OnlineAiFailureReason.TIMED_OUT -> ChatGenerationFailureKind.TIMEOUT
        OnlineAiFailureReason.TLS_FAILED -> ChatGenerationFailureKind.TLS
        OnlineAiFailureReason.INVALID_REQUEST,
        OnlineAiFailureReason.RESPONSE_TOO_LARGE,
        OnlineAiFailureReason.INVALID_RESPONSE,
        OnlineAiFailureReason.EXECUTION_CLOSED,
        -> ChatGenerationFailureKind.RESPONSE
    }
}
