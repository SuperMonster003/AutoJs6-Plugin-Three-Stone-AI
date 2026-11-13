package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetIds
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProfilePolicy

/**
 * The model list of the service details page (roadmap 1.7.0 P5.4, D96): the order is the
 * profile's modelIds order (the first model keeps the bare target ID, Q13), [preferredModelId] is
 * the profile's modelId and [visionModelIds] keep the list order.
 */
internal data class ServiceModelList(
    val modelIds: List<String>,
    val preferredModelId: String,
    val visionModelIds: List<String>,
) {
    init {
        require(modelIds.isNotEmpty()) { "A service needs at least one model" }
        require(preferredModelId in modelIds) { "The preferred model is not in the list" }
    }
}

internal sealed interface ServiceModelAddResult {
    data class Added(val list: ServiceModelList, val addedCount: Int) : ServiceModelAddResult
    data object NothingNew : ServiceModelAddResult
    data object Invalid : ServiceModelAddResult
    data class TooMany(val limit: Int) : ServiceModelAddResult
}

internal object ServiceModelListPolicy {
    const val MAXIMUM_MODELS = OnlineAiProfilePolicy.MAXIMUM_MODELS_PER_PROFILE

    fun move(list: ServiceModelList, from: Int, to: Int): ServiceModelList {
        if (from == to || from !in list.modelIds.indices || to !in list.modelIds.indices) return list
        val reordered = list.modelIds.toMutableList().apply { add(to, removeAt(from)) }
        return list.copy(modelIds = reordered, visionModelIds = reordered.filter(list.visionModelIds::contains))
    }

    /** Applies a dragged order; anything but a permutation of the current models is ignored. */
    fun reorder(list: ServiceModelList, order: List<String>): ServiceModelList {
        if (order.size != list.modelIds.size || order.toSet() != list.modelIds.toSet()) return list
        return list.copy(modelIds = order, visionModelIds = order.filter(list.visionModelIds::contains))
    }

    fun setPreferred(list: ServiceModelList, modelId: String): ServiceModelList =
        if (modelId in list.modelIds) list.copy(preferredModelId = modelId) else list

    fun setVision(list: ServiceModelList, modelId: String, enabled: Boolean): ServiceModelList {
        if (modelId !in list.modelIds) return list
        val selected = list.visionModelIds.toMutableSet().apply { if (enabled) add(modelId) else remove(modelId) }
        return list.copy(visionModelIds = list.modelIds.filter(selected::contains))
    }

    /** The last model cannot be removed; removing the preferred model makes the first one preferred. */
    fun canRemove(list: ServiceModelList): Boolean = list.modelIds.size > 1

    fun remove(list: ServiceModelList, modelId: String): ServiceModelList {
        if (!canRemove(list) || modelId !in list.modelIds) return list
        val remaining = list.modelIds - modelId
        return ServiceModelList(
            modelIds = remaining,
            preferredModelId = list.preferredModelId.takeIf { it != modelId } ?: remaining.first(),
            visionModelIds = list.visionModelIds - modelId,
        )
    }

    /**
     * Appends [candidates] (presets and typed lines, trimmed) after the current models in the given
     * order. Models already in the list are skipped; one invalid ID rejects the whole addition.
     */
    fun add(list: ServiceModelList, candidates: List<String>): ServiceModelAddResult {
        val trimmed = candidates.map(String::trim).filter(String::isNotEmpty)
        val fresh = trimmed.distinct().filterNot(list.modelIds::contains)
        if (fresh.isEmpty()) return if (trimmed.isEmpty()) ServiceModelAddResult.Invalid else ServiceModelAddResult.NothingNew
        if (list.modelIds.size + fresh.size > MAXIMUM_MODELS) return ServiceModelAddResult.TooMany(MAXIMUM_MODELS)
        val normalized = runCatching { OnlineAiProfilePolicy.normalizeModelIds(list.modelIds + fresh) }
            .getOrElse { return ServiceModelAddResult.Invalid }
        return ServiceModelAddResult.Added(list.copy(modelIds = normalized), fresh.size)
    }

    /**
     * Saving a new model order can change the first model and with it which model owns the bare
     * target ID `profile:<id>` (Q13). This maps every old target ID of [oldModelIds] to the ID the
     * same model has under [newModelIds], so a stored default target or favorite keeps pointing at
     * the same model (D122). A removed model maps to the alternative ID it would have now, which no
     * target uses, so the default falls back automatically (D114) instead of drifting to the new
     * first model. Unchanged IDs are left out.
     */
    fun targetIdRemap(profileId: String, oldModelIds: List<String>, newModelIds: List<String>): Map<String, String> {
        if (oldModelIds.isEmpty() || newModelIds.isEmpty()) return emptyMap()
        val oldFirst = oldModelIds.first()
        val newFirst = newModelIds.first()
        if (oldFirst == newFirst) return emptyMap()
        return oldModelIds.associate { modelId ->
            AiTargetIds.profileModel(profileId, oldFirst, modelId) to
                AiTargetIds.profileModel(profileId, newFirst, modelId)
        }.filter { (old, new) -> old != new }
    }
}
