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
            302 to OpenAiCompatibleFailureReason.REDIRECT_REFUSED,
            401 to OpenAiCompatibleFailureReason.AUTHENTICATION_FAILED,
            403 to OpenAiCompatibleFailureReason.PERMISSION_DENIED,
            408 to OpenAiCompatibleFailureReason.TIMED_OUT,
            422 to OpenAiCompatibleFailureReason.REQUEST_REJECTED,
            429 to OpenAiCompatibleFailureReason.RATE_LIMITED,
            500 to OpenAiCompatibleFailureReason.SERVICE_UNAVAILABLE,
            504 to OpenAiCompatibleFailureReason.TIMED_OUT,
        ).forEach { (status, reason) ->
            val failure = failureForHttpStatus(status)
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
        assertTrue(text.choiceSeen)
        assertNull(text.usage)
        assertEquals(OpenAiCompatibleUsage(12L, 3L), usage.usage)
        assertFalse(usage.choiceSeen)
        assertFalse(usage.done)
        assertTrue(
            OpenAiCompatibleResponseParser.parseEvent(
                OpenAiCompatibleSseEvent(null, " [DONE] ", isDone = true),
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
        assertEquals(OpenAiCompatibleUsage(4L, 2L), response.usage)
    }

    @Test
    fun providerErrorsAndMalformedResponsesExposeOnlyFixedMessages() {
        val secret = "credential-should-not-escape"
        val providerFailure = assertThrows(OpenAiCompatibleFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseEvent(
                event("{\"error\":{\"message\":\"$secret\"}}"),
            )
        }
        assertEquals(OpenAiCompatibleFailureReason.PROVIDER_ERROR, providerFailure.reason)
        assertFalse(providerFailure.toString().contains(secret))

        val typedProviderFailure = assertThrows(OpenAiCompatibleFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseEvent(
                event("{\"type\":\"error\",\"message\":\"$secret\"}"),
            )
        }
        assertEquals(
            OpenAiCompatibleFailureReason.PROVIDER_ERROR,
            typedProviderFailure.reason,
        )
        assertFalse(typedProviderFailure.toString().contains(secret))

        val malformedFailure = assertThrows(OpenAiCompatibleFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseEvent(event("not-json-$secret"))
        }
        assertEquals(OpenAiCompatibleFailureReason.INVALID_RESPONSE, malformedFailure.reason)
        assertFalse(malformedFailure.toString().contains(secret))

        val missingChoice = assertThrows(OpenAiCompatibleFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseJson(
                "{\"choices\":[]}".toResponseBody("application/json".toMediaType()),
            )
        }
        assertEquals(OpenAiCompatibleFailureReason.INVALID_RESPONSE, missingChoice.reason)
    }

    @Test
    fun fractionalNegativeAndInconsistentUsageAreRejected() {
        listOf(
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":1.5,\"completion_tokens\":2}}",
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":-1,\"completion_tokens\":2}}",
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":4}}",
        ).forEach { payload ->
            val failure = assertThrows(OpenAiCompatibleFailureException::class.java) {
                OpenAiCompatibleResponseParser.parseEvent(event(payload))
            }
            assertEquals(OpenAiCompatibleFailureReason.INVALID_RESPONSE, failure.reason)
        }
    }

    @Test
    fun jsonResponseLimitIsEnforcedBeforeParsing() {
        val oversized = "x".repeat(
            OpenAiCompatibleTransportLimits.MAXIMUM_JSON_RESPONSE_BYTES.toInt() + 1,
        )
        val failure = assertThrows(OpenAiCompatibleFailureException::class.java) {
            OpenAiCompatibleResponseParser.parseJson(
                oversized.toResponseBody("application/json".toMediaType()),
            )
        }
        assertEquals(OpenAiCompatibleFailureReason.RESPONSE_TOO_LARGE, failure.reason)
    }

    private fun event(data: String) = OpenAiCompatibleSseEvent(
        event = null,
        data = data,
        isDone = false,
    )
}
