package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.os.SystemClock
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import java.util.Collections

/**
 * A ready local-looking target whose replies are scripted, so chat UI tests need no model,
 * network or credentials. Replies stream as the given chunks; [hold] keeps a turn open until
 * [release] or cancellation, [failNext] fails the next turn after its chunks.
 */
internal class ScriptedAiBackend : AiBackend {
    override val backendId: String = "speech-test"

    @Volatile var available: Boolean = true
    @Volatile var reply: List<String> = listOf("Done.")
    @Volatile var chunkDelayMillis: Long = 20L
    @Volatile var hold: Boolean = false
    @Volatile var failNext: Boolean = false
    val prompts: MutableList<String> = Collections.synchronizedList(ArrayList())

    @Volatile private var released = false

    fun release() {
        released = true
    }

    val target: AiTarget
        get() = AiTarget(
            targetId = TARGET_ID,
            backendId = backendId,
            providerId = ThreeStoneAiPlugin.PROVIDER_ID,
            profileId = null,
            modelId = MODEL_ID,
            displayName = "Speech test model",
            locality = AiTargetLocality.LOCAL,
            credentialMode = AiTargetCredentialMode.NONE,
            declaredHttpsOrigins = emptyList(),
            configured = available,
            available = available,
            capabilities = AiTargetCapabilities(
                streaming = true,
                persistentSession = false,
                structuredJson = false,
                usage = true,
                reasoning = false,
                tools = false,
            ),
            limits = AiTargetLimits(maximumContextBytes = null, maximumOutputBytes = null),
            executionProfiles = emptyList(),
        )

    override fun ownsTarget(targetId: String): Boolean = targetId == TARGET_ID

    override fun catalog(): AiTargetCatalog = AiTargetCatalog(
        generation = "speech-test-$available",
        defaultTargetId = TARGET_ID,
        targets = listOf(target),
    )

    override fun createSession(request: AiBackendSessionRequest): AiBackendSession {
        require(request.targetId == TARGET_ID)
        return Session(target)
    }

    private inner class Session(override val target: AiTarget) : AiBackendSession {
        @Volatile private var cancelled = false

        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            prompts += request.prompt.textParts.joinToString("")
            released = false
            val started = SystemClock.elapsedRealtime()
            for (chunk in reply) {
                if (cancelled) return
                listener.onTextDelta(chunk)
                SystemClock.sleep(chunkDelayMillis)
            }
            while (hold && !released && !cancelled) SystemClock.sleep(POLL_MILLIS)
            if (cancelled) return
            if (failNext) {
                failNext = false
                listener.onFailed(IllegalStateException("Scripted failure"), null)
                return
            }
            listener.onCompleted(
                GenerationStatistics(
                    inputTokens = 1L,
                    outputTokens = reply.size.toLong(),
                    durationMillis = SystemClock.elapsedRealtime() - started,
                ),
            )
        }

        override fun cancel() {
            cancelled = true
        }

        override fun close() {
            cancelled = true
        }
    }

    companion object {
        const val MODEL_ID = "speech-test-model"
        const val TARGET_ID = "local:$MODEL_ID"
        private const val POLL_MILLIS = 20L
    }
}
