package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation key of script sessions (T73, D180): a persistent session uses the session ID the
 * script sees for every backend it builds, a one-off request leaves the key to the online session.
 */
class ScriptSessionIdentityTest {
    @Test
    fun aPersistentSessionUsesItsScriptSessionIdForEveryBackend() {
        val identity = ScriptSessionIdentity(persistent = true)

        val first = identity.backendRequest("online:profile", backendProfile = "fast", requestId = "request-1")
        // A rebuild after compaction comes from a later request of the same Binder session.
        val rebuilt = identity.backendRequest("online:profile", backendProfile = null, requestId = "request-2")

        assertEquals("online:profile", first.targetId)
        assertEquals("fast", first.executionProfileId)
        assertEquals(identity.sessionId("request-1"), first.conversationKey)
        assertEquals(first.conversationKey, rebuilt.conversationKey)
        assertEquals(identity.sessionId("request-3"), rebuilt.conversationKey)
        assertTrue(checkNotNull(first.conversationKey).startsWith("litertlm."))
    }

    @Test
    fun anotherSessionGetsAnotherKey() {
        val first = ScriptSessionIdentity(persistent = true).backendRequest("online:profile", null, "request-1")
        val second = ScriptSessionIdentity(persistent = true).backendRequest("online:profile", null, "request-1")

        assertNotEquals(first.conversationKey, second.conversationKey)
    }

    @Test
    fun aOneOffRequestPassesNoKeyButStillHasASessionId() {
        val identity = ScriptSessionIdentity(persistent = false)

        val request = identity.backendRequest("online:profile", null, "request-1")

        assertNull(request.conversationKey)
        assertEquals(identity.sessionId("request-1"), identity.sessionId("request-2"))
    }

    @Test
    fun theSessionIdIsCreatedOnceFromTheFirstRequest() {
        var nonces = 0
        val identity = ScriptSessionIdentity(persistent = true) { "nonce-${++nonces}" }

        val sessionId = identity.sessionId("request-1")
        identity.backendRequest("online:profile", null, "request-2")
        identity.sessionId("request-3")

        assertEquals(1, nonces)
        assertEquals(SessionIds.create("request-1", "nonce-1"), sessionId)
    }
}
