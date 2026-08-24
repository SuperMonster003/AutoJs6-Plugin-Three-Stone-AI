package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineMemoryPressurePolicyTest {
    @Suppress("DEPRECATION")
    @Test
    fun evictsForExplicitPressureButNotOrdinaryVisibilityTransitions() {
        assertFalse(EngineMemoryPressurePolicy.shouldEvict(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertFalse(EngineMemoryPressurePolicy.shouldEvict(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))

        assertTrue(EngineMemoryPressurePolicy.shouldEvict(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
        assertTrue(EngineMemoryPressurePolicy.shouldEvict(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertTrue(EngineMemoryPressurePolicy.shouldEvict(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        assertTrue(EngineMemoryPressurePolicy.shouldEvict(ComponentCallbacks2.TRIM_MEMORY_MODERATE))
        assertTrue(EngineMemoryPressurePolicy.shouldEvict(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }

    @Test
    fun productionIdleTimeoutIsFiveMinutes() {
        assertEquals(300_000L, LiteRtLmEngineRuntime.ENGINE_IDLE_TIMEOUT_MILLIS)
    }
}
