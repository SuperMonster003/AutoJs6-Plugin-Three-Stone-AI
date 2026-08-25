package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiExecutionProfile
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationTargetTest {
    @Test
    fun `new conversation captures the catalog default as an immutable snapshot`() {
        val original = remoteTarget(displayName = "PoloAPI", modelId = "claude-sonnet")
        val catalog = catalog(defaultTargetId = original.targetId, targets = listOf(original))

        val snapshot = ConversationTargetPolicy.defaultSnapshot(catalog)

        assertEquals(ConversationTargetSnapshot.from(original), snapshot)
        assertEquals(
            "PoloAPI",
            snapshot?.displayName,
        )
    }

    @Test
    fun `restored snapshot resolves only by its fixed target id`() {
        val snapshot = ConversationTargetSnapshot.from(
            remoteTarget(displayName = "Original name", modelId = "original-model"),
        )
        val editedTarget = remoteTarget(displayName = "Edited name", modelId = "edited-model")

        assertEquals(
            editedTarget,
            ConversationTargetPolicy.resolve(snapshot, catalog(editedTarget.targetId, listOf(editedTarget))),
        )
        assertEquals("Original name", snapshot.displayName)
        assertNull(ConversationTargetPolicy.resolve(snapshot, catalog(null, emptyList())))
    }

    @Test
    fun `exact resolution permits a rename but rejects provider or model drift`() {
        val snapshot = ConversationTargetSnapshot.from(
            remoteTarget(displayName = "Original name", modelId = "original-model"),
        )
        val renamed = remoteTarget(displayName = "Renamed", modelId = "original-model")
        val changedModel = remoteTarget(displayName = "Renamed", modelId = "new-model")
        val changedProvider = renamed.copy(providerId = "openai")

        assertEquals(
            renamed,
            ConversationTargetPolicy.resolveExact(snapshot, catalog(renamed.targetId, listOf(renamed))),
        )
        assertNull(
            ConversationTargetPolicy.resolveExact(
                snapshot,
                catalog(changedModel.targetId, listOf(changedModel)),
            ),
        )
        assertNull(
            ConversationTargetPolicy.resolveExact(
                snapshot,
                catalog(changedProvider.targetId, listOf(changedProvider)),
            ),
        )
    }

    @Test
    fun `empty conversation switches directly while populated conversation requires confirmation`() {
        val current = ConversationTargetSnapshot.from(localTarget())
        val candidate = remoteTarget()

        assertEquals(
            ConversationTargetSelectionDisposition.UNCHANGED,
            ConversationTargetPolicy.selectionDisposition(current, localTarget(), hasMessages = true),
        )
        assertEquals(
            ConversationTargetSelectionDisposition.APPLY,
            ConversationTargetPolicy.selectionDisposition(current, candidate, hasMessages = false),
        )
        assertEquals(
            ConversationTargetSelectionDisposition.CONFIRM_EXISTING_CONVERSATION,
            ConversationTargetPolicy.selectionDisposition(current, candidate, hasMessages = true),
        )
    }

    private fun catalog(defaultTargetId: String?, targets: List<AiTarget>) = AiTargetCatalog(
        generation = "test-catalog",
        defaultTargetId = defaultTargetId,
        targets = targets,
    )

    private fun localTarget() = AiTarget(
        targetId = "local:gemma",
        backendId = "local",
        providerId = "autojs6.three-stone-ai",
        profileId = null,
        modelId = "gemma",
        displayName = "Gemma",
        locality = AiTargetLocality.LOCAL,
        credentialMode = AiTargetCredentialMode.NONE,
        declaredHttpsOrigins = emptyList(),
        configured = true,
        available = true,
        capabilities = capabilities,
        limits = limits,
        executionProfiles = listOf(AiExecutionProfile("cpu", available = true)),
    )

    private fun remoteTarget(
        displayName: String = "PoloAPI",
        modelId: String = "claude-sonnet",
    ) = AiTarget(
        targetId = "profile:00000000-0000-0000-0000-000000000001",
        backendId = "online",
        providerId = "openai-compatible",
        profileId = "00000000-0000-0000-0000-000000000001",
        modelId = modelId,
        displayName = displayName,
        locality = AiTargetLocality.REMOTE,
        credentialMode = AiTargetCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf("https://example.com"),
        configured = true,
        available = true,
        capabilities = capabilities,
        limits = limits,
        executionProfiles = emptyList(),
    )

    private companion object {
        val capabilities = AiTargetCapabilities(
            streaming = true,
            persistentSession = true,
            structuredJson = false,
            usage = true,
            reasoning = false,
            tools = false,
        )
        val limits = AiTargetLimits(
            maximumContextBytes = null,
            maximumOutputBytes = null,
        )
    }
}
