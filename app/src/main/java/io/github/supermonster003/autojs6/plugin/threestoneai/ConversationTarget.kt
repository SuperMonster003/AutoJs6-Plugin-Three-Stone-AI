package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality

/** Immutable target identity and presentation metadata captured when a conversation binds to it. */
internal data class ConversationTargetSnapshot(
    val targetId: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val locality: AiTargetLocality,
) {
    init {
        require(targetId.isNotBlank())
        require(providerId.isNotBlank())
        require(modelId.isNotBlank())
        require(displayName.isNotBlank())
        when (locality) {
            AiTargetLocality.LOCAL -> require(AiTargetIds.requireLocalModelId(targetId) == modelId)
            AiTargetLocality.REMOTE -> AiTargetIds.requireProfileId(targetId)
        }
    }

    companion object {
        fun from(target: AiTarget) = ConversationTargetSnapshot(
            targetId = target.targetId,
            providerId = target.providerId,
            modelId = target.modelId,
            displayName = target.displayName,
            locality = target.locality,
        )
    }
}

internal enum class ConversationTargetSelectionDisposition {
    UNCHANGED,
    APPLY,
    CONFIRM_EXISTING_CONVERSATION,
}

/** Pure policy for binding a stable default target without silently following catalog changes. */
internal object ConversationTargetPolicy {
    fun defaultSnapshot(catalog: AiTargetCatalog): ConversationTargetSnapshot? =
        catalog.defaultTargetId
            ?.let { targetId -> catalog.targets.singleOrNull { target -> target.targetId == targetId } }
            ?.let(ConversationTargetSnapshot::from)

    fun resolve(
        snapshot: ConversationTargetSnapshot?,
        catalog: AiTargetCatalog?,
    ): AiTarget? = snapshot?.let { selected ->
        catalog?.targets?.singleOrNull { target -> target.targetId == selected.targetId }
    }

    fun selectionDisposition(
        current: ConversationTargetSnapshot?,
        candidate: AiTarget,
        hasMessages: Boolean,
    ): ConversationTargetSelectionDisposition = when {
        current?.targetId == candidate.targetId -> ConversationTargetSelectionDisposition.UNCHANGED
        hasMessages -> ConversationTargetSelectionDisposition.CONFIRM_EXISTING_CONVERSATION
        else -> ConversationTargetSelectionDisposition.APPLY
    }
}
