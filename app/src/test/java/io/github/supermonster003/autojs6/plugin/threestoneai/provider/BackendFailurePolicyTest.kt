package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureReason
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.common.api.AiRetryDisposition
import org.junit.Assert.*
import org.junit.Test

class BackendFailurePolicyTest {
    @Test
    fun everyKnownOnlineFailureRetainsOnlyItsClosedCategory() {
        for (reason in OnlineAiFailureReason.entries) {
            val error = BackendFailurePolicy.error(OnlineAiFailureException(reason, 503))
            assertEquals(AiErrorCode.PROVIDER_FAILED, error.code)
            assertEquals("AI generation failed", error.message)
            assertEquals(AiRetryDisposition.NEVER, error.retryDisposition)
            assertEquals("ONLINE_$reason", error.providerCode)
            assertEquals(error, AiCommonCodec.decodeError(AiCommonCodec.encodeError(error)))
            assertFalse(error.providerCode!!.contains("503"))
        }
    }

    @Test
    fun unknownFailuresNeverReadOrExposeThrowableTextOrCauses() {
        val secret = "https://private.example/secret?api_key=private-key"
        val errors = listOf(
            IllegalStateException(secret, OnlineAiFailureException(OnlineAiFailureReason.NETWORK_UNAVAILABLE)),
            object : Throwable() {
                override val message: String get() = error("Throwable text must not be inspected")
                override fun toString(): String = error("Throwable text must not be inspected")
            },
        )
        for (failure in errors) {
            val error = BackendFailurePolicy.error(failure)
            assertEquals(AiErrorCode.PROVIDER_FAILED, error.code)
            assertEquals(AiRetryDisposition.NEVER, error.retryDisposition)
            assertEquals("AI generation failed", error.message)
            assertNull(error.providerCode)
            val encoded = AiCommonCodec.encodeError(error)
            assertFalse(String(encoded, Charsets.UTF_8).contains(secret))
            assertEquals(error, AiCommonCodec.decodeError(encoded))
        }
    }
}
