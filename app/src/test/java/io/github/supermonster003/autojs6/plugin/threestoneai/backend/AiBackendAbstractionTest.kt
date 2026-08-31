package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.provider.StreamingOutputBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBackendAbstractionTest {
    @Test
    fun fakeSessionExercisesStreamingWithoutLoadingNativeModel() {
        val output = StreamingOutputBuffer(streaming = true, maximumOutputBytes = 64)
        val expectedStatistics = GenerationStatistics(inputTokens = 8L, outputTokens = 2L, durationMillis = 125L)
        val session: AiBackendSession = FakeSession(target(), listOf("hello ", "world"), expectedStatistics)
        session.stream(
            GenerationRequest(
                history = emptyList(),
                prompt = GenerationMessage(GenerationRole.USER, listOf("prompt")),
                maximumOutputTokens = null,
                samplingOptions = null,
                reportUsage = true,
            ),
            object : GenerationListener {
                override fun onTextDelta(text: String) {
                    assertFalse(output.append(text))
                }

                override fun onCompleted(statistics: GenerationStatistics?) {
                    assertEquals(expectedStatistics, statistics)
                    assertEquals(10L, statistics?.totalTokens)
                    output.markBackendDone()
                }

                override fun onFailed(error: Throwable, statistics: GenerationStatistics?) {
                    throw AssertionError(error)
                }
            },
        )
        output.grantCredits(2)
        assertEquals("hello ", output.takeCreditedChunk()?.text)
        assertEquals("world", output.takeCreditedChunk()?.text)
        assertTrue(output.isReadyForCompletion())
        assertEquals("hello world", output.snapshot().text)
        session.close()
        assertTrue((session as FakeSession).closed)
    }

    @Test
    fun generationStatisticsRejectInvalidCountersAndOverflow() {
        assertThrows(IllegalArgumentException::class.java) { GenerationStatistics(-1L, 0L, 0L) }
        assertThrows(IllegalArgumentException::class.java) { GenerationStatistics(0L, -1L, 0L) }
        assertThrows(IllegalArgumentException::class.java) { GenerationStatistics(0L, 0L, -1L) }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationStatistics(0L, 0L, 0L, contextTokensAfterTurn = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationStatistics(Long.MAX_VALUE, 1L, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationStatistics(
                inputTokens = 10L,
                outputTokens = 1L,
                durationMillis = 1L,
                cachedInputTokens = 11L,
                cacheEligibleInputTokens = 10L,
            )
        }

        val cached = GenerationStatistics(
            inputTokens = 100L,
            outputTokens = 1L,
            durationMillis = 1L,
            cachedInputTokens = 75L,
            cacheWriteInputTokens = 20L,
            cacheEligibleInputTokens = 100L,
        )
        assertEquals(0.75, cached.promptCacheHitRate!!, 0.0)
    }

    @Test
    fun persistentSessionReceivesOnlyTheNewPromptOnLaterTurns() {
        val session = RecordingPersistentSession(target())
        val listener = object : GenerationListener {
            override fun onTextDelta(text: String) = Unit
            override fun onCompleted(statistics: GenerationStatistics?) = Unit
            override fun onFailed(error: Throwable, statistics: GenerationStatistics?) =
                throw AssertionError(error)
        }
        val initial = GenerationRequest(
            history = listOf(GenerationMessage(GenerationRole.SYSTEM, listOf("Be concise"))),
            prompt = GenerationMessage(GenerationRole.USER, listOf("First")),
            maximumOutputTokens = 64,
            samplingOptions = GenerationSamplingOptions(0.5, 8, 0.9),
            reportUsage = true,
        )
        val next = initial.copy(
            history = emptyList(),
            prompt = GenerationMessage(GenerationRole.USER, listOf("Second only")),
        )

        session.stream(initial, listener)
        session.streamNext(next, listener)

        assertEquals(initial, session.initial)
        assertEquals(next, session.next)
        assertTrue(requireNotNull(session.next).history.isEmpty())
        assertEquals(listOf("Second only"), requireNotNull(session.next).prompt.textParts)
    }

    @Test
    fun cancelAndCloseAlwaysReleasesSessionWhenCancellationFails() {
        val cancellationFailure = IllegalStateException("cancel marker")
        val session = FailingCancellationSession(target(), cancellationFailure)

        assertEquals(
            cancellationFailure,
            assertThrows(IllegalStateException::class.java, session::cancelAndClose),
        )
        assertEquals(1, session.cancelCount)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun backendCatalogOwnsCapabilitiesAndSessionCreation() {
        val target = target()
        val session = RecordingPersistentSession(target)
        val backend = object : AiBackend {
            override val backendId = target.backendId
            override fun ownsTarget(targetId: String) = targetId == target.targetId
            override fun catalog() = AiTargetCatalog("generation-1", target.targetId, listOf(target))
            override fun createSession(request: AiBackendSessionRequest): AiBackendSession {
                assertEquals(target.targetId, request.targetId)
                return session
            }
        }

        assertEquals(target.capabilities, backend.capabilities(target.targetId))
        assertEquals(
            session,
            backend.createSession(AiBackendSessionRequest(target.targetId, "cpu")),
        )
        assertThrows(AiTargetUnavailableException::class.java) {
            backend.capabilities(AiTargetIds.local("litertlm." + "ff".repeat(16)))
        }
    }

    @Test
    fun targetCatalogRejectsDuplicateAndMissingDefaultTargets() {
        val target = target()
        assertThrows(IllegalArgumentException::class.java) {
            AiTargetCatalog("generation-1", null, listOf(target, target))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiTargetCatalog("generation-1", "local:missing", listOf(target))
        }
    }

    @Test
    fun targetLocalityMustMatchIdentityCredentialModeAndOrigins() {
        val local = target()
        assertThrows(IllegalArgumentException::class.java) {
            local.copy(profileId = "remote")
        }
        assertThrows(IllegalArgumentException::class.java) {
            local.copy(declaredHttpsOrigins = listOf("https://api.example.com"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            local.copy(targetId = "local:different")
        }
        assertThrows(IllegalArgumentException::class.java) {
            local.copy(
                targetId = "profile:remote",
                profileId = "remote",
                locality = AiTargetLocality.REMOTE,
                credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
                declaredHttpsOrigins = listOf("http://api.example.com"),
            )
        }
    }

    private class FakeSession(
        override val target: AiTarget,
        private val deltas: List<String>,
        private val statistics: GenerationStatistics,
    ) : AiBackendSession {
        var closed = false
            private set

        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            deltas.forEach(listener::onTextDelta)
            listener.onCompleted(statistics)
        }

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }
    }

    private class RecordingPersistentSession(
        override val target: AiTarget,
    ) : AiBackendSession {
        var initial: GenerationRequest? = null
        var next: GenerationRequest? = null

        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            initial = request
            listener.onCompleted(null)
        }

        override fun streamNext(request: GenerationRequest, listener: GenerationListener) {
            next = request
            listener.onCompleted(null)
        }

        override fun cancel() = Unit
        override fun close() = Unit
    }

    private class FailingCancellationSession(
        override val target: AiTarget,
        private val cancellationFailure: RuntimeException,
    ) : AiBackendSession {
        var cancelCount = 0
            private set
        var closeCount = 0
            private set

        override fun stream(request: GenerationRequest, listener: GenerationListener) = Unit

        override fun cancel() {
            cancelCount += 1
            throw cancellationFailure
        }

        override fun close() {
            closeCount += 1
        }
    }

    private fun target() = AiTarget(
        targetId = AiTargetIds.local("litertlm." + "11".repeat(16)),
        backendId = LiteRtLocalBackend.BACKEND_ID,
        providerId = "autojs6.three-stone-ai",
        profileId = null,
        modelId = "litertlm." + "11".repeat(16),
        displayName = "Local model",
        locality = AiTargetLocality.LOCAL,
        credentialMode = AiTargetCredentialMode.NONE,
        declaredHttpsOrigins = emptyList(),
        configured = true,
        available = true,
        capabilities = AiTargetCapabilities(
            streaming = true,
            persistentSession = true,
            structuredJson = true,
            usage = true,
            reasoning = false,
            tools = false,
        ),
        limits = AiTargetLimits(256L * 1024L, 64L * 1024L),
        executionProfiles = listOf(AiExecutionProfile("cpu", available = true)),
    )
}
