package io.github.supermonster003.autojs6.plugin.ai.text.provider

import io.github.supermonster003.autojs6.plugin.ai.text.AiTextPlugin
import io.github.supermonster003.autojs6.plugin.ai.text.model.ModelRepository
import org.autojs.plugin.ai.text.api.AiModelInfo
import org.autojs.plugin.ai.text.api.AiModelListRequest
import org.autojs.plugin.ai.text.api.AiModelPage
import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import org.autojs.plugin.ai.text.api.AiTextProtocol
import org.autojs.plugin.ai.text.api.AiTextVersionPolicy

internal class ModelPager(private val repository: ModelRepository) {
    fun page(request: AiModelListRequest): AiModelPage {
        AiTextVersionPolicy.requireSelected(request.protocolVersion, AiTextProtocol.HOST_PROTOCOL_RANGE.maximum)
        require(request.pageToken == null) { "This provider exposes at most one model per listing" }
        val model = repository.current()
        return AiModelPage(
            listingGeneration = model?.listingGeneration ?: EMPTY_LISTING_GENERATION,
            models = model?.let {
                listOf(
                    AiModelInfo(
                        modelId = it.modelId,
                        displayName = it.displayName,
                        capabilityIds = listOf(AiTextCapabilityId.STREAMING),
                        maximumContextBytes = AiTextPlugin.MAXIMUM_CONTEXT_BYTES,
                        maximumOutputBytes = AiTextPlugin.MAXIMUM_OUTPUT_BYTES,
                    ),
                )
            }.orEmpty(),
            nextPageToken = null,
        )
    }

    private companion object {
        const val EMPTY_LISTING_GENERATION = "litertlm-empty-v1"
    }
}
