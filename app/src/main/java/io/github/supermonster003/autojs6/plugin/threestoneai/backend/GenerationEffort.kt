package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import java.util.Locale

/**
 * Requested reasoning depth for one remote generation turn (roadmap D72 / D73).
 *
 * [persistentId] is the stable lowercase ASCII form stored in profile JSON ("defaultEffort") and in
 * conversation history properties (key "effort"); it must never change once released.
 */
internal enum class GenerationEffort(val persistentId: String) {
    /** Omits every effort field so the model uses its own default (D73). */
    DEFAULT("default"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max"),
    ;

    companion object {
        fun fromPersistentId(value: String): GenerationEffort? = entries.singleOrNull { effort ->
            effort.persistentId == value
        }
    }
}

/**
 * Protocol and model support for effort and fast mode, checked against the provider documentation
 * on 2026-11-05 (roadmap D88 - D90). Unsupported levels fall to the closest supported level below
 * them, or to the lowest supported level when nothing lies below.
 */
internal object OnlineAiEffortSupport {
    /** Every Anthropic model except the Claude Opus 5.5 family needs this header for fast mode (D74). */
    const val ANTHROPIC_FAST_BETA = "fast-mode-2026-02-01"

    private val ALL_LEVELS = listOf(
        GenerationEffort.LOW,
        GenerationEffort.MEDIUM,
        GenerationEffort.HIGH,
        GenerationEffort.XHIGH,
        GenerationEffort.MAX,
    )
    private val ANTHROPIC_WITHOUT_EFFORT = listOf(
        "claude-3",
        "claude-opus-4-0",
        "claude-opus-4-1",
        "claude-opus-4-2025",
        "claude-sonnet-4-0",
        "claude-sonnet-4-2025",
        "claude-sonnet-4-5",
        "claude-haiku-4-5",
    )
    private val ANTHROPIC_WITHOUT_XHIGH = listOf(
        "claude-opus-4-6",
        "claude-sonnet-4-6",
        "claude-mythos-preview",
    )
    private const val ANTHROPIC_HIGH_CEILING = "claude-opus-4-5"
    private const val ANTHROPIC_FAST_GA = "claude-opus-5-5"
    private val OPENAI_LEVELS = listOf(
        GenerationEffort.LOW,
        GenerationEffort.MEDIUM,
        GenerationEffort.HIGH,
        GenerationEffort.XHIGH,
    )
    private val GEMINI_LEVELS = listOf(GenerationEffort.LOW, GenerationEffort.MEDIUM, GenerationEffort.HIGH)
    private val GEMINI_WITHOUT_LEVELS = listOf("gemini-1.", "gemini-2.0")

    /** Levels the target can distinguish, lowest first; empty when no effort field can be sent. */
    fun levels(protocol: OnlineAiProtocol, modelId: String): List<GenerationEffort> {
        val model = canonicalModel(modelId)
        return when (protocol) {
            OnlineAiProtocol.ANTHROPIC_MESSAGES -> when {
                ANTHROPIC_WITHOUT_EFFORT.any(model::startsWith) -> emptyList()
                model.startsWith(ANTHROPIC_HIGH_CEILING) -> ALL_LEVELS.take(3)
                ANTHROPIC_WITHOUT_XHIGH.any(model::startsWith) -> ALL_LEVELS - GenerationEffort.XHIGH
                else -> ALL_LEVELS
            }
            OnlineAiProtocol.OPENAI_COMPATIBLE -> OPENAI_LEVELS
            OnlineAiProtocol.GEMINI_GENERATE_CONTENT -> when {
                GEMINI_WITHOUT_LEVELS.any(model::startsWith) -> emptyList()
                model.startsWith("gemini-3-pro-preview") -> listOf(GenerationEffort.LOW, GenerationEffort.HIGH)
                model.startsWith("gemini-3.1-flash-lite-image") -> listOf(GenerationEffort.HIGH)
                else -> GEMINI_LEVELS
            }
        }
    }

    /** Whether the protocol has a fast output mode at all; the provider decides per model (D74). */
    fun supportsFast(protocol: OnlineAiProtocol): Boolean = protocol == OnlineAiProtocol.ANTHROPIC_MESSAGES

    /** Requested level as the target will receive it; [GenerationEffort.DEFAULT] means omitted. */
    fun effective(requested: GenerationEffort?, levels: List<GenerationEffort>): GenerationEffort {
        if (requested == null || requested == GenerationEffort.DEFAULT || levels.isEmpty()) {
            return GenerationEffort.DEFAULT
        }
        return levels.lastOrNull { level -> level <= requested } ?: levels.first()
    }

    /** Wire value for the protocol's effort field, or null to omit the field. */
    fun wireValue(protocol: OnlineAiProtocol, modelId: String, requested: GenerationEffort?): String? =
        effective(requested, levels(protocol, modelId))
            .takeUnless { effort -> effort == GenerationEffort.DEFAULT }
            ?.persistentId

    /** Anthropic models outside the GA fast family need the research-preview beta header (D74). */
    fun anthropicFastNeedsBeta(modelId: String): Boolean = !canonicalModel(modelId).startsWith(ANTHROPIC_FAST_GA)

    private fun canonicalModel(modelId: String): String =
        modelId.trim().substringAfterLast('/').lowercase(Locale.ROOT)
}
