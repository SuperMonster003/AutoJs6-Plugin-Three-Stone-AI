package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleResponseTest {
    @Test
    fun httpStatusesMapToStableFailureReasons() {
        mapOf(
            302 to OnlineAiFailureReason.REDIRECT_REFUSED,
            401 to OnlineAiFailureReason.AUTHENTICATION_FAILED,
            403 to OnlineAiFailureReason.PERMISSION_DENIED,
            408 to OnlineAiFailureReason.TIMED_OUT,
            422 to OnlineAiFailureReason.REQUEST_REJECTED,
            429 to OnlineAiFailureReason.RATE_LIMITED,
            500 to OnlineAiFailureReason.SERVICE_UNAVAILABLE,
            504 to OnlineAiFailureReason.TIMED_OUT,
        ).forEach { (status, reason) ->
            val failure = failureForOnlineAiHttpStatus(status)
            assertEquals(reason, failure.reason)
            assertEquals(status, failure.statusCode)
            assertTrue(failure.message.orEmpty().endsWith("(HTTP $status)"))
            assertNull(failure.cause)
        }
    }

    @Test
    fun streamParserExtractsTextArraysAndExactUsage() {
        val text = OpenAiCompatibleResponseParser.parseEvent(
            event(
                """
                {
                  "choices": [{
                    "delta": {
                      "content": [
                        {"type":"text","text":"Hello"},
                        {"type":"refusal","refusal":" world"}
                      ]
                    }
                  }]
                }
                """.trimIndent(),
            ),
        )
        val usage = OpenAiCompatibleResponseParser.parseEvent(
            event(
                """
                {
                  "choices": [],
                  "usage": {
                    "prompt_tokens": 12,
                    "completion_tokens": 3,
                    "total_tokens": 15
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals("Hello world", text.text)
        assertTrue(text.contentSeen)
        assertNull(text.usage)
        assertEquals(OnlineAiUsageUpdate(12L, 3L, 15L), usage.usage)
        assertFalse(usage.contentSeen)
        assertFalse(usage.done)
        assertTrue(
            OpenAiCompatibleResponseParser.parseEvent(
                OnlineAiSseEvent(null, " [DONE] ", isDone = true),
            ).done,
        )
    }

    @Test
    fun jsonFallbackExtractsAssistantTextAndUsageAliases() {
        val response = OpenAiCompatibleResponseParser.parseJson(
            """
            {
              "choices": [{"message":{"role":"assistant","content":"complete"}}],
              "usage":{"input_tokens":4,"output_tokens":2,"total_tokens":6}
            }
            """.trimIndent().toResponseBody("application/json".toMediaType()),
        )

        assertEquals("complete", response.text)
        assertEquals(OnlineAiUsageUpdate(4L, 2L, 6L), response.usage)
    }

    @Test
    fun usageParsesPromptCacheReadAndWriteDetails() {
        val usage = OpenAiCompatibleResponseParser.parseEvent(
            event(
                """
                {
                  "choices":[],
                  "usage":{
                    "prompt_tokens":2000,
                    "completion_tokens":4,
                    "total_tokens":2004,
                    "prompt_tokens_details":{
                      "cached_tokens":1536,
                      "cache_write_tokens":256
                    }
                  }
                }
                """.trimIndent(),
            ),
        ).usage

        assertEquals(
            OnlineAiUsageUpdate(
                inputTokens = 2000L,
                outputTokens = 4L,
                totalTokens = 2004L,
                cachedInputTokens = 1536L,
                cacheWriteInputTokens = 256L,
                cacheEligibleInputTokens = 2000L,
            ),
            usage,
        )
    }

    @Test
    fun providerErrorsAndMalformedResponsesExposeOnlyFixedMessages() {
        val secret = "credential-should-not-escape"
        val providerFailure = assertThrows(OnlineAiFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseEvent(
                event("{\"error\":{\"message\":\"$secret\"}}"),
            )
        }
        assertEquals(OnlineAiFailureReason.PROVIDER_ERROR, providerFailure.reason)
        assertFalse(providerFailure.toString().contains(secret))

        val typedProviderFailure = assertThrows(OnlineAiFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseEvent(
                event("{\"type\":\"error\",\"message\":\"$secret\"}"),
            )
        }
        assertEquals(
            OnlineAiFailureReason.PROVIDER_ERROR,
            typedProviderFailure.reason,
        )
        assertFalse(typedProviderFailure.toString().contains(secret))

        val malformedFailure = assertThrows(OnlineAiFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseEvent(event("not-json-$secret"))
        }
        assertEquals(OnlineAiFailureReason.INVALID_RESPONSE, malformedFailure.reason)
        assertFalse(malformedFailure.toString().contains(secret))

        val missingChoice = assertThrows(OnlineAiFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseJson(
                "{\"choices\":[]}".toResponseBody("application/json".toMediaType()),
            )
        }
        assertEquals(OnlineAiFailureReason.INVALID_RESPONSE, missingChoice.reason)
    }

    @Test
    fun fractionalNegativeAndInconsistentUsageAreRejected() {
        listOf(
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":1.5,\"completion_tokens\":2}}",
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":-1,\"completion_tokens\":2}}",
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":4}}",
        ).forEach { payload ->
            val failure = assertThrows(OnlineAiFailureException::class.java) {
                OpenAiCompatibleResponseParser.parseEvent(event(payload))
            }
            assertEquals(OnlineAiFailureReason.INVALID_RESPONSE, failure.reason)
        }
    }

    @Test
    fun jsonResponseLimitIsEnforcedBeforeParsing() {
        val oversized = "x".repeat(
            OnlineAiTransportLimits.MAXIMUM_JSON_RESPONSE_BYTES.toInt() + 1,
        )
        val failure = assertThrows(OnlineAiFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseJson(
                oversized.toResponseBody("application/json".toMediaType()),
            )
        }
        assertEquals(OnlineAiFailureReason.RESPONSE_TOO_LARGE, failure.reason)
    }

    private fun event(data: String) = OnlineAiSseEvent(
        event = null,
        data = data,
        isDone = false,
    )
}
