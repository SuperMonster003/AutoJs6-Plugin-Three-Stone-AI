package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * The session ID a script sees in onStarted, created once per Binder session. A persistent session
 * also uses it as the conversation key of every backend it builds (T73), so a rebuild after
 * compaction keeps the same "per conversation" header value. A one-off request passes no key and
 * gets a random one for its single backend session.
 */
internal class ScriptSessionIdentity(
    private val persistent: Boolean,
    private val nonce: () -> String = { UUID.randomUUID().toString() },
) {
    private val sessionId = AtomicReference<String?>()

    fun sessionId(requestId: String): String {
        sessionId.get()?.let { return it }
        val created = SessionIds.create(requestId, nonce())
        return if (sessionId.compareAndSet(null, created)) created else checkNotNull(sessionId.get())
    }

    fun backendRequest(
        targetId: String,
        backendProfile: String?,
        requestId: String,
    ): AiBackendSessionRequest = AiBackendSessionRequest(
        targetId = targetId,
        executionProfileId = backendProfile,
        conversationKey = if (persistent) sessionId(requestId) else null,
    )
}
