package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImageExportPolicyTest {
    @Test
    fun `layouts up to the height limit keep their size and taller ones are scaled down as a whole`() {
        assertEquals(1f, ChatImageExportPolicy.scale(1080, 5_000), 0f)
        assertEquals(1f, ChatImageExportPolicy.scale(1080, ChatImageExportPolicy.MAXIMUM_HEIGHT_PX), 0f)
        assertEquals(0.5f, ChatImageExportPolicy.scale(1080, 32_768), 0f)
        assertEquals(540 to 16_384, ChatImageExportPolicy.outputSize(1080, 32_768, 0.5f))
        assertEquals(1080 to 5_000, ChatImageExportPolicy.outputSize(1080, 5_000, 1f))
    }

    @Test
    fun `the scaled height never exceeds the limit`() {
        for (height in 16_385..60_000 step 997) {
            val scale = ChatImageExportPolicy.scale(1080, height)
            val (width, scaledHeight) = ChatImageExportPolicy.outputSize(1080, height, scale)
            assertTrue("$height -> $scaledHeight", scaledHeight in 16_000..ChatImageExportPolicy.MAXIMUM_HEIGHT_PX)
            assertTrue(width in 1..1080)
        }
    }

    @Test
    fun `a retry after running out of memory halves the scale until the text would be too small`() {
        assertEquals(0.5f, ChatImageExportPolicy.retryScale(1080, 1f)!!, 0f)
        assertEquals(0.25f, ChatImageExportPolicy.retryScale(1440, 0.5f)!!, 0f)
        assertNull(ChatImageExportPolicy.retryScale(1080, 0.5f))
        assertNull(ChatImageExportPolicy.retryScale(600, 1f))
    }

    @Test
    fun `old shared images are deleted and at most three are kept`() {
        val now = 10L * ChatImageExportPolicy.SHARE_RETENTION_MILLIS
        val hour = 60L * 60L * 1_000L

        assertEquals(emptySet<String>(), ChatImageExportPolicy.staleFiles(emptyMap(), now))
        assertEquals(emptySet<String>(), ChatImageExportPolicy.staleFiles(mapOf("a" to now - hour), now))
        assertEquals(
            setOf("c", "d"),
            ChatImageExportPolicy.staleFiles(mapOf("a" to now - hour, "b" to now - 2 * hour, "c" to now - 3 * hour, "d" to now - 4 * hour), now),
        )
        assertEquals(
            setOf("old"),
            ChatImageExportPolicy.staleFiles(mapOf("new" to now - hour, "old" to now - ChatImageExportPolicy.SHARE_RETENTION_MILLIS - 1), now),
        )
    }

    @Test
    fun `shared images are named by time`() {
        assertTrue(Regex("""3-Stone-AI-\d{8}-\d{6}\.png""").matches(ChatImageExportPolicy.fileName(1_700_000_000_000L)))
    }
}
