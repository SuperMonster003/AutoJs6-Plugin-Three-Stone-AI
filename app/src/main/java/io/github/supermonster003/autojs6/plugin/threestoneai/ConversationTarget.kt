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

    /** Stable execution identity; a presentation-only rename does not create a different target. */
    fun matchesExecutionIdentity(target: AiTarget): Boolean =
        targetId == target.targetId &&
            providerId == target.providerId &&
            modelId == target.modelId &&
            locality == target.locality

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

    /** Resolves only when the historical provider, model and locality still identify the same target. */
    fun resolveExact(
        snapshot: ConversationTargetSnapshot?,
        catalog: AiTargetCatalog?,
    ): AiTarget? = resolve(snapshot, catalog)?.takeIf { target ->
        snapshot?.matchesExecutionIdentity(target) == true
    }

    /**
     * Target of a conversation after the catalog was reloaded (roadmap D92, Q10). A conversation with
     * messages keeps its target. An empty conversation without a target takes the default; one that
     * still holds the previous catalog's default follows the new default, so changing a profile's
     * default model also applies to the new conversation already on screen. A target the user picked
     * in the empty conversation differs from the previous default and stays.
     */
    fun afterCatalogRefresh(
        current: ConversationTargetSnapshot?,
        previousCatalog: AiTargetCatalog?,
        catalog: AiTargetCatalog?,
        hasMessages: Boolean,
    ): ConversationTargetSnapshot? {
        if (hasMessages) return current
        val latestDefault = catalog?.let(::defaultSnapshot) ?: return current
        if (current == null) return latestDefault
        val previousDefaultId = previousCatalog?.defaultTargetId ?: return current
        return if (current.targetId == previousDefaultId) latestDefault else current
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
