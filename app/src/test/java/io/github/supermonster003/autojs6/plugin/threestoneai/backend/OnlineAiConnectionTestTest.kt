package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class OnlineAiConnectionTestTest {
    @Test
    fun successfulTestUsesOneSmallPromptAndClosesTheSession() {
        val session = RecordingSession(target()) { _, listener ->
            listener.onTextDelta("OK")
            listener.onCompleted(null)
        }
        val backend = RecordingBackend(session)

        OnlineAiConnectionTest(backend, PROFILE_ID).use { it.execute() }

        assertEquals(AiTargetIds.profile(PROFILE_ID), backend.request?.targetId)
        val request = requireNotNull(session.request)
        assertTrue(request.history.isEmpty())
        assertEquals(GenerationRole.USER, request.prompt.role)
        assertEquals(listOf("Reply with OK."), request.prompt.textParts)
        assertEquals(8, request.maximumOutputTokens)
        assertFalse(request.reportUsage)
        assertTrue(session.closed)
    }

    @Test
    fun failedTerminalIsPropagatedWithoutWrappingOrRetainingProviderData() {
        val expected = OnlineAiFailureException(OnlineAiFailureReason.AUTHENTICATION_FAILED, 401)
        val session = RecordingSession(target()) { _, listener -> listener.onFailed(expected, null) }

        val actual = assertThrows(OnlineAiFailureException::class.java) {
            OnlineAiConnectionTest(RecordingBackend(session), PROFILE_ID).execute()
        }

        assertSame(expected, actual)
        assertTrue(session.closed)
    }

    @Test
    fun cancellationBeforeExecutionDoesNotCreateASession() {
        val backend = RecordingBackend(RecordingSession(target()) { _, _ -> Unit })
        val test = OnlineAiConnectionTest(backend, PROFILE_ID)
        test.cancel()

        assertThrows(CancellationException::class.java) { test.execute() }
        assertEquals(null, backend.request)
    }

    @Test
    fun oneConnectionTestOperationCannotBeReused() {
        val session = RecordingSession(target()) { _, listener -> listener.onCompleted(null) }
        val test = OnlineAiConnectionTest(RecordingBackend(session), PROFILE_ID)
        test.execute()

        assertThrows(IllegalStateException::class.java) { test.execute() }
    }

    private class RecordingBackend(
        private val session: AiBackendSession,
    ) : AiBackend {
        override val backendId = OnlineAiBackend.BACKEND_ID
        var request: AiBackendSessionRequest? = null

        override fun ownsTarget(targetId: String) = true

        override fun catalog() = AiTargetCatalog("test", null, listOf(session.target))

        override fun createSession(request: AiBackendSessionRequest): AiBackendSession {
            this.request = request
            return session
        }
    }

    private class RecordingSession(
        override val target: AiTarget,
        private val action: (GenerationRequest, GenerationListener) -> Unit,
    ) : AiBackendSession {
        var request: GenerationRequest? = null
        var closed = false

        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            this.request = request
            action(request, listener)
        }

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }
    }

    private fun target() = AiTarget(
        targetId = AiTargetIds.profile(PROFILE_ID),
        backendId = OnlineAiBackend.BACKEND_ID,
        providerId = "openai",
        profileId = PROFILE_ID,
        modelId = "model-a",
        displayName = "Remote",
        locality = AiTargetLocality.REMOTE,
        credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf("https://api.example.com"),
        configured = true,
        available = true,
        capabilities = AiTargetCapabilities(true, true, true, true, false, false),
        limits = AiTargetLimits(null, null),
        executionProfiles = emptyList(),
    )

    private companion object {
        const val PROFILE_ID = "550e8400-e29b-41d4-a716-446655440000"
    }
}
