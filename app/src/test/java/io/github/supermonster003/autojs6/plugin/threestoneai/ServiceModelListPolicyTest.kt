package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The model list of the service details page (roadmap 1.7.0 P5.4, D96, D122). */
class ServiceModelListPolicyTest {
    private val list = ServiceModelList(
        modelIds = listOf("gpt-a", "gpt-b", "gpt-c"),
        preferredModelId = "gpt-b",
        visionModelIds = listOf("gpt-a", "gpt-c"),
    )

    @Test
    fun moveKeepsImageInputInListOrder() {
        val moved = ServiceModelListPolicy.move(list, 2, 0)

        assertEquals(listOf("gpt-c", "gpt-a", "gpt-b"), moved.modelIds)
        assertEquals(listOf("gpt-c", "gpt-a"), moved.visionModelIds)
        assertEquals("gpt-b", moved.preferredModelId)
        assertSame(list, ServiceModelListPolicy.move(list, 0, 3))
        assertSame(list, ServiceModelListPolicy.move(list, 1, 1))
    }

    @Test
    fun reorderAcceptsOnlyPermutations() {
        val reordered = ServiceModelListPolicy.reorder(list, listOf("gpt-b", "gpt-c", "gpt-a"))

        assertEquals(listOf("gpt-b", "gpt-c", "gpt-a"), reordered.modelIds)
        assertEquals(listOf("gpt-c", "gpt-a"), reordered.visionModelIds)
        assertSame(list, ServiceModelListPolicy.reorder(list, listOf("gpt-a", "gpt-b")))
        assertSame(list, ServiceModelListPolicy.reorder(list, listOf("gpt-a", "gpt-b", "gpt-x")))
    }

    @Test
    fun preferredAndImageInputChangeOnlyListedModels() {
        assertEquals("gpt-c", ServiceModelListPolicy.setPreferred(list, "gpt-c").preferredModelId)
        assertSame(list, ServiceModelListPolicy.setPreferred(list, "gpt-x"))
        assertEquals(listOf("gpt-a", "gpt-b", "gpt-c"), ServiceModelListPolicy.setVision(list, "gpt-b", true).visionModelIds)
        assertEquals(listOf("gpt-c"), ServiceModelListPolicy.setVision(list, "gpt-a", false).visionModelIds)
        assertSame(list, ServiceModelListPolicy.setVision(list, "gpt-x", true))
    }

    @Test
    fun removingThePreferredModelPrefersTheFirstAndTheLastModelStays() {
        val removed = ServiceModelListPolicy.remove(list, "gpt-b")
        assertEquals(listOf("gpt-a", "gpt-c"), removed.modelIds)
        assertEquals("gpt-a", removed.preferredModelId)

        val first = ServiceModelListPolicy.remove(list, "gpt-a")
        assertEquals("gpt-b", first.preferredModelId)
        assertEquals(listOf("gpt-c"), first.visionModelIds)

        val single = ServiceModelList(listOf("only"), "only", emptyList())
        assertFalse(ServiceModelListPolicy.canRemove(single))
        assertSame(single, ServiceModelListPolicy.remove(single, "only"))
        assertTrue(ServiceModelListPolicy.canRemove(list))
    }

    @Test
    fun addAppendsNewTrimmedModelsAndReportsWhyNothingWasAdded() {
        val added = ServiceModelListPolicy.add(list, listOf(" gpt-d ", "gpt-a", "", "gpt-d", "gpt-e"))
        added as ServiceModelAddResult.Added
        assertEquals(listOf("gpt-a", "gpt-b", "gpt-c", "gpt-d", "gpt-e"), added.list.modelIds)
        assertEquals(2, added.addedCount)
        assertEquals("gpt-b", added.list.preferredModelId)

        assertEquals(ServiceModelAddResult.NothingNew, ServiceModelListPolicy.add(list, listOf("gpt-a", " gpt-b")))
        assertEquals(ServiceModelAddResult.Invalid, ServiceModelListPolicy.add(list, listOf(" ", "")))
        assertEquals(ServiceModelAddResult.Invalid, ServiceModelListPolicy.add(list, listOf("bad\nline", "x".repeat(300))))
    }

    @Test
    fun addStopsAtTheModelLimit() {
        val full = ServiceModelList((1..31).map { "m$it" }, "m1", emptyList())

        assertTrue(ServiceModelListPolicy.add(full, listOf("m32")) is ServiceModelAddResult.Added)
        assertEquals(
            ServiceModelAddResult.TooMany(ServiceModelListPolicy.MAXIMUM_MODELS),
            ServiceModelListPolicy.add(full, listOf("m32", "m33")),
        )
    }

    @Test
    fun targetIdsFollowTheSameModelWhenTheFirstModelChanges() {
        val old = listOf("gpt-a", "gpt-b", "gpt-c")
        val new = listOf("gpt-b", "gpt-a", "gpt-c")
        val mapping = ServiceModelListPolicy.targetIdRemap("p1", old, new)

        new.forEach { modelId ->
            val old = AiTargetIds.profileModel("p1", "gpt-a", modelId)
            assertEquals(AiTargetIds.profileModel("p1", "gpt-b", modelId), mapping[old] ?: old)
        }
        assertEquals("profile:p1", mapping[AiTargetIds.profileModel("p1", "gpt-a", "gpt-b")])
        assertTrue(AiTargetIds.profileModel("p1", "gpt-a", "gpt-c") !in mapping)
        assertTrue(ServiceModelListPolicy.targetIdRemap("p1", old, listOf("gpt-a", "gpt-c")).isEmpty())
    }

    @Test
    fun aRemovedFirstModelDoesNotHandItsTargetToTheNewFirstModel() {
        val mapping = ServiceModelListPolicy.targetIdRemap("p1", listOf("gpt-a", "gpt-b"), listOf("gpt-b"))
        val moved = mapping.getValue("profile:p1")

        assertTrue(moved != "profile:p1")
        assertEquals(AiTargetIds.profileModel("p1", "gpt-b", "gpt-a"), moved)
        assertEquals("profile:p1", mapping[AiTargetIds.profileModel("p1", "gpt-a", "gpt-b")])
    }
}
