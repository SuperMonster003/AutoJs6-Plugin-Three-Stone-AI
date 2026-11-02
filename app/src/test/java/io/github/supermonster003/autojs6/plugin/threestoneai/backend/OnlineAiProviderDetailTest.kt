package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiProviderDetailTest {
    @Test
    fun readsAnthropicOpenAiAndGeminiErrorEnvelopes() {
        assertEquals(
            "invalid_request_error: Your credit balance is too low to access the Anthropic API.",
            detail(
                """
                {"type":"error","error":{"type":"invalid_request_error",
                 "message":"Your credit balance is too low to access the Anthropic API."}}
                """,
            ),
        )
        assertEquals(
            "insufficient_quota: You exceeded your current quota.",
            detail("""{"error":{"message":"You exceeded your current quota.","type":"insufficient_quota","code":"x"}}"""),
        )
        assertEquals(
            "INVALID_ARGUMENT: API key not valid.",
            detail("""{"error":{"code":400,"message":"API key not valid.","status":"INVALID_ARGUMENT"}}"""),
        )
        assertEquals("plain failure", detail("""{"error":"plain failure"}"""))
        assertEquals("Overloaded", detail("""{"type":"error","message":"Overloaded"}"""))
        assertNull(detail("""{"type":"error","error":{}}"""))
        assertNull(detail("""{"choices":[]}"""))
    }

    @Test
    fun sanitisesWhitespaceControlCharactersKeyShapedTokensAndLength() {
        assertEquals(
            "line one line two key [redacted] and [redacted] end",
            OnlineAiProviderDetail.sanitize(
                "line one\n\n  line two\u0007 key sk-ant-api03-abcdefghijkl and AIzaSyA1234567890abcdefghijklmnop end",
            ),
        )
        val long = OnlineAiProviderDetail.sanitize("x".repeat(1_000))!!
        assertEquals(OnlineAiProviderDetail.MAXIMUM_LENGTH, long.length)
        assertTrue(long.endsWith("..."))
        assertNull(OnlineAiProviderDetail.sanitize(" \n\t "))
        assertNull(OnlineAiProviderDetail.fromText("<html><body>Bad gateway</body></html>"))
        assertEquals("Bad gateway", OnlineAiProviderDetail.fromText("  Bad gateway \n"))
    }

    @Test
    fun masksTheRequestCredentialWithoutKeepingItAsText() {
        val secret = "private-key-123".toByteArray(Charsets.US_ASCII)
        assertEquals(
            "echoed [redacted] twice [redacted].",
            OnlineAiProviderDetail.withoutSecret("echoed private-key-123 twice private-key-123.", secret),
        )
        val untouched = "nothing to mask"
        assertTrue(untouched === OnlineAiProviderDetail.withoutSecret(untouched, secret))
        assertEquals("short", OnlineAiProviderDetail.withoutSecret("short", secret))

        val failure = OnlineAiFailureException(
            OnlineAiFailureReason.AUTHENTICATION_FAILED,
            401,
            "invalid key private-key-123",
        ).withoutSecret(secret)
        assertEquals("invalid key [redacted]", failure.providerDetail)
        assertEquals(401, failure.statusCode)
        assertFalse(failure.toString().contains("private-key"))
        assertFalse(failure.toString().contains("invalid key"))
    }

    @Test
    fun httpErrorBodiesAreReadOnceBoundedAndMappedWithTheStatus() {
        val json = OnlineAiResponseSupport.readErrorDetail(
            """{"error":{"type":"rate_limit_error","message":"Slow down"}}"""
                .toResponseBody("application/json".toMediaType()),
        )
        assertEquals("rate_limit_error: Slow down", json)
        assertEquals(
            "Service unavailable",
            OnlineAiResponseSupport.readErrorDetail("Service unavailable".toResponseBody("text/plain".toMediaType())),
        )
        assertNull(OnlineAiResponseSupport.readErrorDetail("".toResponseBody("text/plain".toMediaType())))
        assertNull(
            OnlineAiResponseSupport.readErrorDetail(
                "y".repeat((OnlineAiTransportLimits.MAXIMUM_ERROR_RESPONSE_BYTES + 1L).toInt())
                    .toResponseBody("text/plain".toMediaType()),
            ),
        )

        val failure = failureForOnlineAiHttpStatus(429, "rate_limit_error: Slow down")
        assertEquals(OnlineAiFailureReason.RATE_LIMITED, failure.reason)
        assertEquals("rate_limit_error: Slow down", failure.providerDetail)
        assertEquals("Online AI request was rate limited (HTTP 429)", failure.message)
    }

    private fun detail(json: String): String? =
        OnlineAiProviderDetail.fromJson(JsonParser.parseString(json.trimIndent()).asJsonObject)
}
