package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import java.io.IOException

internal enum class OnlineAiFailureReason {
    INVALID_REQUEST,
    CREDENTIAL_UNAVAILABLE,
    PROFILE_CHANGED,
    AUTHENTICATION_FAILED,
    PERMISSION_DENIED,
    RATE_LIMITED,
    REQUEST_REJECTED,
    REDIRECT_REFUSED,
    SERVICE_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    METERED_NETWORK_DISALLOWED,
    TIMED_OUT,
    TLS_FAILED,
    RESPONSE_TOO_LARGE,
    INVALID_RESPONSE,
    PROVIDER_ERROR,
    EXECUTION_CLOSED,
}

/**
 * Stable online-generation failure. [message] never retains a provider body, request URL, credential or
 * lower-level exception, so logging this Throwable stays safe. [providerDetail] is the bounded,
 * sanitised explanation the provider sent ([OnlineAiProviderDetail]); it is meant for user-facing
 * surfaces only and is excluded from [message] and [toString].
 */
internal class OnlineAiFailureException(
    val reason: OnlineAiFailureReason,
    val statusCode: Int? = null,
    val providerDetail: String? = null,
) : IOException(message(reason, statusCode)) {
    init {
        require(statusCode == null || statusCode in 100..599)
        require(providerDetail == null || providerDetail.length <= OnlineAiProviderDetail.MAXIMUM_LENGTH)
    }

    /** The same failure with every occurrence of [secret] masked in [providerDetail]. */
    fun withoutSecret(secret: ByteArray): OnlineAiFailureException {
        val detail = providerDetail ?: return this
        val masked = OnlineAiProviderDetail.withoutSecret(detail, secret)
        return if (masked == detail) this else OnlineAiFailureException(reason, statusCode, masked)
    }

    companion object {
        private fun message(reason: OnlineAiFailureReason, statusCode: Int?): String {
            val detail = when (reason) {
                OnlineAiFailureReason.INVALID_REQUEST -> "Online AI request is invalid"
                OnlineAiFailureReason.CREDENTIAL_UNAVAILABLE ->
                    "Online AI credential is unavailable"
                OnlineAiFailureReason.PROFILE_CHANGED ->
                    "Online AI profile changed before the request completed"
                OnlineAiFailureReason.AUTHENTICATION_FAILED ->
                    "Online AI authentication failed"
                OnlineAiFailureReason.PERMISSION_DENIED ->
                    "Online AI request was not permitted"
                OnlineAiFailureReason.RATE_LIMITED ->
                    "Online AI request was rate limited"
                OnlineAiFailureReason.REQUEST_REJECTED ->
                    "Online AI request was rejected"
                OnlineAiFailureReason.REDIRECT_REFUSED ->
                    "Online AI redirect was refused"
                OnlineAiFailureReason.SERVICE_UNAVAILABLE ->
                    "Online AI service is unavailable"
                OnlineAiFailureReason.NETWORK_UNAVAILABLE ->
                    "Online AI network request failed"
                OnlineAiFailureReason.METERED_NETWORK_DISALLOWED ->
                    "Online AI access on a metered network is disabled"
                OnlineAiFailureReason.TIMED_OUT ->
                    "Online AI request timed out"
                OnlineAiFailureReason.TLS_FAILED ->
                    "Online AI TLS connection failed"
                OnlineAiFailureReason.RESPONSE_TOO_LARGE ->
                    "Online AI response exceeded its safety limit"
                OnlineAiFailureReason.INVALID_RESPONSE ->
                    "Online AI response is invalid"
                OnlineAiFailureReason.PROVIDER_ERROR ->
                    "Online AI provider returned an error"
                OnlineAiFailureReason.EXECUTION_CLOSED ->
                    "Online AI execution is closed"
            }
            return statusCode?.let { "$detail (HTTP $it)" } ?: detail
        }
    }
}

internal fun failureForOnlineAiHttpStatus(
    statusCode: Int,
    providerDetail: String? = null,
): OnlineAiFailureException {
    val reason = when (statusCode) {
        in 300..399 -> OnlineAiFailureReason.REDIRECT_REFUSED
        401 -> OnlineAiFailureReason.AUTHENTICATION_FAILED
        403 -> OnlineAiFailureReason.PERMISSION_DENIED
        408, 504 -> OnlineAiFailureReason.TIMED_OUT
        429 -> OnlineAiFailureReason.RATE_LIMITED
        in 500..599 -> OnlineAiFailureReason.SERVICE_UNAVAILABLE
        else -> OnlineAiFailureReason.REQUEST_REJECTED
    }
    return OnlineAiFailureException(reason, statusCode, providerDetail)
}
