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
    fun masksEverySecretOfTheRequestInEachFormTheDetailMayCarryIt() {
        val key = "placeholder-key".toByteArray(Charsets.US_ASCII)
        val token = "placeholder-key-token".toByteArray(Charsets.US_ASCII)
        assertEquals(
            "a [redacted] b [redacted] c",
            OnlineAiProviderDetail.withoutSecrets("a placeholder-key-token b placeholder-key c", listOf(key, token)),
        )

        // sanitize collapses the spaces inside a secret value before the secret is removed.
        val wide = "placeholder   wide".toByteArray(Charsets.US_ASCII)
        val collapsed = checkNotNull(OnlineAiProviderDetail.sanitize("value placeholder   wide end"))
        assertEquals("value [redacted] end", OnlineAiProviderDetail.withoutSecrets(collapsed, listOf(wide)))

        // A plain-text body may echo the value as part of a JSON string literal.
        val quote = '"'
        val backslash = 92.toChar()
        val quoted = "place${quote}holder".toByteArray(Charsets.US_ASCII)
        assertEquals(
            "sent {${quote}v${quote}:${quote}[redacted]${quote}}",
            OnlineAiProviderDetail.withoutSecrets(
                "sent {${quote}v${quote}:${quote}place$backslash${quote}holder${quote}}",
                listOf(quoted),
            ),
        )

        val untouched = "nothing to mask"
        assertTrue(untouched === OnlineAiProviderDetail.withoutSecrets(untouched, listOf(key, token)))
        assertTrue(untouched === OnlineAiProviderDetail.withoutSecrets(untouched, listOf(ByteArray(0))))

        val failure = OnlineAiFailureException(
            OnlineAiFailureReason.INVALID_REQUEST,
            400,
            "bad placeholder-key-token for placeholder-key",
        ).withoutSecrets(listOf(key, token))
        assertEquals("bad [redacted] for [redacted]", failure.providerDetail)
        assertEquals(400, failure.statusCode)
    }

    @Test
    fun masksASecretThatTruncationCutOffAndKeepsTheLengthLimit() {
        val secret = "placeholder-secret-value".toByteArray(Charsets.US_ASCII)
        val cut = checkNotNull(OnlineAiProviderDetail.sanitize("x".repeat(280) + " placeholder-secret-value tail"))
        assertTrue(cut.endsWith(" placeholder-secr..."))
        assertEquals("x".repeat(280) + " [redacted]...", OnlineAiProviderDetail.withoutSecrets(cut, listOf(secret)))

        // Fewer than PARTIAL_SECRET_MINIMUM leading bytes are indistinguishable from ordinary text.
        val shortCut = checkNotNull(OnlineAiProviderDetail.sanitize("x".repeat(293) + " placeholder-secret-value"))
        assertTrue(shortCut.endsWith(" pla..."))
        assertTrue(shortCut === OnlineAiProviderDetail.withoutSecrets(shortCut, listOf(secret)))

        // Replacing many short secrets grows the text; the result is truncated again.
        val short = "ab12".toByteArray(Charsets.US_ASCII)
        val repeated = checkNotNull(OnlineAiProviderDetail.sanitize("ab12 ".repeat(100)))
        val masked = OnlineAiProviderDetail.withoutSecrets(repeated, listOf(short))
        assertTrue(masked.length <= OnlineAiProviderDetail.MAXIMUM_LENGTH)
        assertTrue(masked.endsWith("..."))
        assertFalse(masked.contains("ab12"))
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
