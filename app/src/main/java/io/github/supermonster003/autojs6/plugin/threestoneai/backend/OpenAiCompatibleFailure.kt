package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import java.io.IOException

internal enum class OpenAiCompatibleFailureReason {
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
    TIMED_OUT,
    TLS_FAILED,
    RESPONSE_TOO_LARGE,
    INVALID_RESPONSE,
    PROVIDER_ERROR,
    EXECUTION_CLOSED,
}

/**
 * Stable online-generation failure that never retains a provider body, request URL, credential,
 * or lower-level exception. Those values must remain absent even if callers log this Throwable.
 */
internal class OpenAiCompatibleFailureException(
    val reason: OpenAiCompatibleFailureReason,
    val statusCode: Int? = null,
) : IOException(message(reason, statusCode)) {
    init {
        require(statusCode == null || statusCode in 100..599)
    }

    companion object {
        private fun message(reason: OpenAiCompatibleFailureReason, statusCode: Int?): String {
            val detail = when (reason) {
                OpenAiCompatibleFailureReason.INVALID_REQUEST -> "Online AI request is invalid"
                OpenAiCompatibleFailureReason.CREDENTIAL_UNAVAILABLE ->
                    "Online AI credential is unavailable"
                OpenAiCompatibleFailureReason.PROFILE_CHANGED ->
                    "Online AI profile changed before the request completed"
                OpenAiCompatibleFailureReason.AUTHENTICATION_FAILED ->
                    "Online AI authentication failed"
                OpenAiCompatibleFailureReason.PERMISSION_DENIED ->
                    "Online AI request was not permitted"
                OpenAiCompatibleFailureReason.RATE_LIMITED ->
                    "Online AI request was rate limited"
                OpenAiCompatibleFailureReason.REQUEST_REJECTED ->
                    "Online AI request was rejected"
                OpenAiCompatibleFailureReason.REDIRECT_REFUSED ->
                    "Online AI redirect was refused"
                OpenAiCompatibleFailureReason.SERVICE_UNAVAILABLE ->
                    "Online AI service is unavailable"
                OpenAiCompatibleFailureReason.NETWORK_UNAVAILABLE ->
                    "Online AI network request failed"
                OpenAiCompatibleFailureReason.TIMED_OUT ->
                    "Online AI request timed out"
                OpenAiCompatibleFailureReason.TLS_FAILED ->
                    "Online AI TLS connection failed"
                OpenAiCompatibleFailureReason.RESPONSE_TOO_LARGE ->
                    "Online AI response exceeded its safety limit"
                OpenAiCompatibleFailureReason.INVALID_RESPONSE ->
                    "Online AI response is invalid"
                OpenAiCompatibleFailureReason.PROVIDER_ERROR ->
                    "Online AI provider returned an error"
                OpenAiCompatibleFailureReason.EXECUTION_CLOSED ->
                    "Online AI execution is closed"
            }
            return statusCode?.let { "$detail (HTTP $it)" } ?: detail
        }
    }
}

internal fun failureForHttpStatus(statusCode: Int): OpenAiCompatibleFailureException {
    val reason = when (statusCode) {
        in 300..399 -> OpenAiCompatibleFailureReason.REDIRECT_REFUSED
        401 -> OpenAiCompatibleFailureReason.AUTHENTICATION_FAILED
        403 -> OpenAiCompatibleFailureReason.PERMISSION_DENIED
        408, 504 -> OpenAiCompatibleFailureReason.TIMED_OUT
        429 -> OpenAiCompatibleFailureReason.RATE_LIMITED
        in 500..599 -> OpenAiCompatibleFailureReason.SERVICE_UNAVAILABLE
        else -> OpenAiCompatibleFailureReason.REQUEST_REJECTED
    }
    return OpenAiCompatibleFailureException(reason, statusCode)
}
