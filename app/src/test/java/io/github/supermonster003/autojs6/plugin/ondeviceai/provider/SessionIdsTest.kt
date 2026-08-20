package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import org.autojs.plugin.ai.common.api.AiValidation
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionIdsTest {
    @Test
    fun maximumLengthRequestIdProducesBoundedStableSessionId() {
        val requestId = "r".repeat(128)
        val first = SessionIds.create(requestId, "nonce-1")
        val second = SessionIds.create(requestId, "nonce-2")
        assertTrue(AiValidation.STABLE_ID.matches(first))
        assertTrue(first.toByteArray(Charsets.UTF_8).size <= 128)
        assertNotEquals(first, second)
    }
}
