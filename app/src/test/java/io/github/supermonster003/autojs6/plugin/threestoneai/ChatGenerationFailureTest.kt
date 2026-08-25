package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetUnavailableException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureException
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.OnlineAiFailureReason
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGenerationFailureTest {
    @Test
    fun `local targets always remain on the local failure boundary`() {
        val failure = ChatGenerationFailurePolicy.classify(
            localTarget,
            OnlineAiFailureException(OnlineAiFailureReason.AUTHENTICATION_FAILED, 401),
        )

        assertEquals(
            ChatGenerationFailure(AiTargetLocality.LOCAL, ChatGenerationFailureKind.LOCAL_EXECUTION),
            failure,
        )
    }

    @Test
    fun `online stable errors map to bounded user-facing categories`() {
        val expected = mapOf(
            OnlineAiFailureReason.CREDENTIAL_UNAVAILABLE to ChatGenerationFailureKind.CREDENTIAL,
            OnlineAiFailureReason.PROFILE_CHANGED to ChatGenerationFailureKind.PROFILE_CHANGED,
            OnlineAiFailureReason.AUTHENTICATION_FAILED to
                ChatGenerationFailureKind.AUTHENTICATION,
            OnlineAiFailureReason.PERMISSION_DENIED to ChatGenerationFailureKind.AUTHENTICATION,
            OnlineAiFailureReason.RATE_LIMITED to ChatGenerationFailureKind.RATE_LIMIT,
            OnlineAiFailureReason.REQUEST_REJECTED to ChatGenerationFailureKind.PROVIDER,
            OnlineAiFailureReason.REDIRECT_REFUSED to ChatGenerationFailureKind.PROVIDER,
            OnlineAiFailureReason.SERVICE_UNAVAILABLE to ChatGenerationFailureKind.PROVIDER,
            OnlineAiFailureReason.PROVIDER_ERROR to ChatGenerationFailureKind.PROVIDER,
            OnlineAiFailureReason.NETWORK_UNAVAILABLE to ChatGenerationFailureKind.NETWORK,
            OnlineAiFailureReason.METERED_NETWORK_DISALLOWED to
                ChatGenerationFailureKind.METERED_NETWORK,
            OnlineAiFailureReason.TIMED_OUT to ChatGenerationFailureKind.TIMEOUT,
            OnlineAiFailureReason.TLS_FAILED to ChatGenerationFailureKind.TLS,
            OnlineAiFailureReason.INVALID_REQUEST to ChatGenerationFailureKind.RESPONSE,
            OnlineAiFailureReason.RESPONSE_TOO_LARGE to ChatGenerationFailureKind.RESPONSE,
            OnlineAiFailureReason.INVALID_RESPONSE to ChatGenerationFailureKind.RESPONSE,
            OnlineAiFailureReason.EXECUTION_CLOSED to ChatGenerationFailureKind.RESPONSE,
        )

        expected.forEach { (reason, kind) ->
            assertEquals(
                ChatGenerationFailure(AiTargetLocality.REMOTE, kind),
                ChatGenerationFailurePolicy.classify(
                    cloudTarget,
                    OnlineAiFailureException(reason, statusCode = null),
                ),
            )
        }
    }

    @Test
    fun `target loss and unknown transport errors stay bounded without raw details`() {
        assertEquals(
            ChatGenerationFailure(
                AiTargetLocality.REMOTE,
                ChatGenerationFailureKind.TARGET_UNAVAILABLE,
            ),
            ChatGenerationFailurePolicy.classify(
                cloudTarget,
                AiTargetUnavailableException(cloudTarget.targetId),
            ),
        )
        assertEquals(
            ChatGenerationFailure(AiTargetLocality.REMOTE, ChatGenerationFailureKind.UNKNOWN),
            ChatGenerationFailurePolicy.classify(
                cloudTarget,
                IOException("secret transport detail"),
            ),
        )
    }

    private companion object {
        val localTarget = ConversationTargetSnapshot(
            targetId = "local:model-a",
            providerId = "autojs6.three-stone-ai",
            modelId = "model-a",
            displayName = "Local model",
            locality = AiTargetLocality.LOCAL,
        )
        val cloudTarget = ConversationTargetSnapshot(
            targetId = "profile:00000000-0000-0000-0000-000000000001",
            providerId = "openai-compatible",
            modelId = "model-b",
            displayName = "Cloud profile",
            locality = AiTargetLocality.REMOTE,
        )
    }
}
