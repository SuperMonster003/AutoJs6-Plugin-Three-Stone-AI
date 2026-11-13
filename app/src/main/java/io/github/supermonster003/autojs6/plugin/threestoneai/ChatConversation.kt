package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachment
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachmentKind
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole

internal enum class ChatMessageRole {
    USER,
    ASSISTANT,
    NOTICE,
}

internal enum class ChatMessageStatus {
    COMPLETE,
    GENERATING,
    STOPPED,
    FAILED,
}

internal data class ChatMessageUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val durationMillis: Long,
) {
    init {
        require(inputTokens >= 0L)
        require(outputTokens >= 0L)
        require(durationMillis >= 0L)
    }
}

/**
 * Thinking text an assistant reply came with (roadmap P3, D94). It is shown in a card above the reply
 * but never sent back to a model, read aloud or searched.
 */
internal data class ChatMessageReasoning(
    val text: String,
    /** Thinking time; null while the model is still thinking. */
    val durationMillis: Long? = null,
    /** [text] was shortened to [MAXIMUM_BYTES] UTF-8 bytes. */
    val truncated: Boolean = false,
) {
    init {
        require(durationMillis == null || durationMillis >= 0L)
    }

    companion object {
        /** One history property value (D91 / D94). */
        const val MAXIMUM_BYTES = ConversationHistoryProperties.MAXIMUM_VALUE_BYTES

        /** [text] cut to at most [MAXIMUM_BYTES] UTF-8 bytes on a code point boundary. */
        fun bounded(text: String, durationMillis: Long?, truncated: Boolean = false): ChatMessageReasoning {
            if (text.length <= MAXIMUM_BYTES / 3) return ChatMessageReasoning(text, durationMillis, truncated)
            var bytes = 0
            var end = 0
            while (end < text.length) {
                val codePoint = text.codePointAt(end)
                val size = when {
                    codePoint < 0x80 -> 1
                    codePoint < 0x800 -> 2
                    codePoint < 0x10000 -> 3
                    else -> 4
                }
                if (bytes + size > MAXIMUM_BYTES) break
                bytes += size
                end += Character.charCount(codePoint)
            }
            return ChatMessageReasoning(text.substring(0, end), durationMillis, truncated || end < text.length)
        }
    }
}

internal data class ChatMessage(
    val id: Long,
    val role: ChatMessageRole,
    val text: String,
    val status: ChatMessageStatus = ChatMessageStatus.COMPLETE,
    val usage: ChatMessageUsage? = null,
    val target: ConversationTargetSnapshot? = null,
    /** The provider stopped this completed reply at its output-token limit, so the text is incomplete. */
    val truncated: Boolean = false,
    /** Thinking text of an assistant reply (P3); null when the target returned none. */
    val reasoning: ChatMessageReasoning? = null,
    /** Files attached to a user message (P4, D71), in the order the user added them. */
    val attachments: List<ChatAttachment> = emptyList(),
) {
    init {
        require(id > 0L)
        require(role == ChatMessageRole.ASSISTANT || status == ChatMessageStatus.COMPLETE) {
            "Only assistant messages may have a non-complete status"
        }
        require(status == ChatMessageStatus.COMPLETE || usage == null) {
            "Only complete messages may carry generation usage"
        }
        require((role == ChatMessageRole.ASSISTANT) == (target != null)) {
            "Every assistant message, and only an assistant message, must carry its actual target"
        }
        require(!truncated || (role == ChatMessageRole.ASSISTANT && status == ChatMessageStatus.COMPLETE)) {
            "Only complete assistant messages may be marked as truncated"
        }
        require(reasoning == null || role == ChatMessageRole.ASSISTANT) {
            "Only assistant messages may carry reasoning"
        }
        require(attachments.isEmpty() || role == ChatMessageRole.USER) {
            "Only user messages may carry attachments"
        }
        require(attachments.size <= ChatAttachmentLimits.MAXIMUM_PER_MESSAGE)
    }
}

internal data class CompletedChatTurn(
    val user: ChatMessage,
    val assistant: ChatMessage,
)

internal data class ContextCompilationPolicy(
    val estimator: ContextTokenEstimator,
    val maximumInputTokens: Long,
    val minimumRecentTurns: Int = ContextPolicy.MINIMUM_RECENT_TURNS,
    val maximumHistoryBytes: Long = ChatConversationPolicy.MAXIMUM_RETAINED_HISTORY_BYTES.toLong(),
    val contextState: ConversationContextState = ConversationContextState.EMPTY,
    val includeDerivedContext: Boolean = true,
    val maximumWorkingMemoryTokens: Long = ContextPolicy.WORKING_MEMORY_MAXIMUM_TOKENS.toLong(),
    val maximumSummaryTokens: Long = ContextPolicy.SUMMARY_LAYER_MAXIMUM_TOKENS.toLong(),
    val recalledHistory: List<ConversationRecallChunk> = emptyList(),
    val maximumRecalledHistoryTokens: Long =
        ConversationRecallPolicy.MAXIMUM_RECALL_LAYER_TOKENS,
    /** Resending earlier attachments (P4, D134); null leaves a note in their place. */
    val attachmentReplay: ChatAttachmentReplay? = null,
) {
    init {
        require(maximumInputTokens > 0L)
        require(minimumRecentTurns >= 0)
        require(maximumHistoryBytes > 0L)
        require(maximumWorkingMemoryTokens > 0L)
        require(maximumSummaryTokens > 0L)
        require(recalledHistory.size <= ConversationRecallPolicy.MAXIMUM_RECALLED_CHUNKS)
        require(maximumRecalledHistoryTokens > 0L)
    }
}

internal data class CompiledContext(
    /** Derived SYSTEM layers followed by complete successful raw history. */
    val messages: List<GenerationMessage>,
    val estimatedInputTokens: Long,
    /** IDs for raw user/assistant messages only; derived layers cite IDs inside their text. */
    val coveredMessageIds: List<Long>,
    val requiresSessionRebuild: Boolean,
    val inputTokenLimit: Long,
    val workingMemoryItemsIncluded: Int = 0,
    val summarySegmentsIncluded: Int = 0,
    val recalledHistory: List<ConversationRecallChunk> = emptyList(),
    val layerTokens: ContextLayerTokenUsage = ContextLayerTokenUsage.EMPTY,
    val contextFingerprint: String = ConversationContextPolicy.fingerprint(
        ConversationContextState.EMPTY,
    ),
) {
    init {
        require(estimatedInputTokens >= 0L)
        require(coveredMessageIds.distinct().size == coveredMessageIds.size)
        require(inputTokenLimit > 0L)
        require(workingMemoryItemsIncluded >= 0)
        require(summarySegmentsIncluded >= 0)
        require(recalledHistory.size <= ConversationRecallPolicy.MAXIMUM_RECALLED_CHUNKS)
        require(CONTEXT_FINGERPRINT.matches(contextFingerprint))
    }

    val recalledChunksIncluded: Int
        get() = recalledHistory.size

    val exceedsInputBudget: Boolean
        get() = estimatedInputTokens > inputTokenLimit

    private companion object {
        val CONTEXT_FINGERPRINT = Regex("^[0-9a-f]{64}$")
    }
}

/** Pure transcript rules shared by the launcher UI and its local unit tests. */
internal object ChatConversationPolicy {
    const val MAXIMUM_INPUT_CHARACTERS = 16_384
    const val MAXIMUM_RETAINED_HISTORY_BYTES = 192 * 1_024

    /**
     * Packs a contiguous suffix of complete user/assistant turns without splitting a pair.
     *
     * The token limit is a context policy, while [ContextCompilationPolicy.maximumHistoryBytes]
     * remains a transport guard. The current prompt is always counted in full and the newest
     * minimum turn floor may deliberately exceed the token limit; callers surface that as a
     * warning instead of truncating user text.
     */
    fun compileContext(
        transcript: List<ChatMessage>,
        prompt: GenerationMessage,
        policy: ContextCompilationPolicy,
    ): CompiledContext {
        require(prompt.role == GenerationRole.USER) {
            "The compiled context prompt must be a user message"
        }
        val allCompletedTurns = completedTurns(transcript)
        val coveredThrough = policy.contextState.coveredThroughMessageId ?: 0L
        val completedTurns = allCompletedTurns.filter { turn -> turn.assistant.id > coveredThrough }
        val retainedReversed = ArrayList<CompletedChatTurn>()
        var retainedUtf8Bytes = 0L
        for (turn in completedTurns.asReversed().take(policy.minimumRecentTurns)) {
            val turnBytes = turn.utf8Bytes()
            if (saturatedAdd(retainedUtf8Bytes, turnBytes) > policy.maximumHistoryBytes) break
            retainedReversed += turn
            retainedUtf8Bytes = saturatedAdd(retainedUtf8Bytes, turnBytes)
        }

        var retainedMemory = emptyList<MemoryItem>()
        var retainedSummaries = emptyList<SummarySegment>()
        if (policy.includeDerivedContext) {
            ConversationContextFormatter.memoryPriority(policy.contextState.workingMemory)
                .forEach { item ->
                    val candidate = retainedMemory + item
                    val layerMessage = ConversationContextFormatter.workingMemoryMessage(candidate)
                    val layerEstimate = policy.estimator.estimateMessage(layerMessage)
                    if (layerEstimate.estimatedTokens > policy.maximumWorkingMemoryTokens) {
                        return@forEach
                    }
                    if (fits(policy, prompt, listOf(layerMessage), retainedReversed)) {
                        retainedMemory = candidate
                    }
                }
            val selectedReversed = ArrayList<SummarySegment>()
            policy.contextState.summarySegments.asReversed().forEach { segment ->
                val candidateReversed = selectedReversed + segment
                val candidate = candidateReversed.asReversed()
                val layerMessage = ConversationContextFormatter.summaryMessage(candidate)
                val layerEstimate = policy.estimator.estimateMessage(layerMessage)
                if (layerEstimate.estimatedTokens > policy.maximumSummaryTokens) {
                    return@forEach
                }
                val derived = retainedMemory.takeIf { memory -> memory.isNotEmpty() }
                    ?.let { memory ->
                        listOf(ConversationContextFormatter.workingMemoryMessage(memory))
                    }.orEmpty() + layerMessage
                if (fits(policy, prompt, derived, retainedReversed)) {
                    selectedReversed += segment
                    retainedSummaries = candidate
                }
            }
        }
        val derivedMessages = buildList {
            if (retainedMemory.isNotEmpty()) {
                add(ConversationContextFormatter.workingMemoryMessage(retainedMemory))
            }
            if (retainedSummaries.isNotEmpty()) {
                add(ConversationContextFormatter.summaryMessage(retainedSummaries))
            }
        }
        var retainedRecall = emptyList<ConversationRecallChunk>()
        val retainedMinimumMessageIds = retainedReversed.flatMapTo(HashSet()) { turn ->
            listOf(turn.user.id, turn.assistant.id)
        }
        if (policy.includeDerivedContext) {
            policy.recalledHistory.forEach { chunk ->
                if (chunk.sourceMessageIds.any(retainedMinimumMessageIds::contains)) {
                    return@forEach
                }
                val candidate = retainedRecall + chunk
                val recalledMessages = ConversationRecallPolicy.generationMessages(candidate)
                if (
                    policy.estimator.estimateMessages(recalledMessages).estimatedTokens >
                    policy.maximumRecalledHistoryTokens
                ) {
                    return@forEach
                }
                if (fits(policy, prompt, derivedMessages + recalledMessages, retainedReversed)) {
                    retainedRecall = candidate
                }
            }
        }
        val recalledMessages = ConversationRecallPolicy.generationMessages(retainedRecall)
        val recalledSourceIds = retainedRecall.flatMapTo(HashSet()) { chunk ->
            chunk.sourceMessageIds
        }
        for (turn in completedTurns.asReversed().drop(retainedReversed.size)) {
            if (turn.user.id in recalledSourceIds || turn.assistant.id in recalledSourceIds) break
            val turnBytes = turn.utf8Bytes()
            if (saturatedAdd(retainedUtf8Bytes, turnBytes) > policy.maximumHistoryBytes) break
            val candidate = retainedReversed + turn
            if (!fits(policy, prompt, derivedMessages + recalledMessages, candidate)) break
            retainedReversed += turn
            retainedUtf8Bytes = saturatedAdd(retainedUtf8Bytes, turnBytes)
        }
        val retained = retainedReversed.asReversed()
        val replayed = replayAttachments(policy, prompt, derivedMessages + recalledMessages, retained)
        val rawMessages = retained.flatMap { turn ->
            listOf(
                userMessage(turn.user, replayed),
                GenerationMessage(GenerationRole.ASSISTANT, listOf(turn.assistant.text)),
            )
        }
        val messages = derivedMessages + recalledMessages + rawMessages
        val coveredMessageIds = retained.flatMap { turn ->
            listOf(turn.user.id, turn.assistant.id)
        }
        val estimate = policy.estimator.estimateMessages(messages + prompt)
        val workingMemoryTokens = derivedMessages.firstOrNull()
            ?.takeIf { retainedMemory.isNotEmpty() }
            ?.let(policy.estimator::estimateMessage)
            ?.estimatedTokens
            ?: 0L
        val summaryTokens = derivedMessages.lastOrNull()
            ?.takeIf { retainedSummaries.isNotEmpty() }
            ?.let(policy.estimator::estimateMessage)
            ?.estimatedTokens
            ?: 0L
        val recalledHistoryTokens = policy.estimator.estimateMessages(recalledMessages)
            .estimatedTokens
        return CompiledContext(
            messages = messages,
            estimatedInputTokens = estimate.estimatedTokens,
            coveredMessageIds = coveredMessageIds,
            requiresSessionRebuild = retained.size != allCompletedTurns.size,
            inputTokenLimit = policy.maximumInputTokens,
            workingMemoryItemsIncluded = retainedMemory.size,
            summarySegmentsIncluded = retainedSummaries.size,
            recalledHistory = retainedRecall,
            layerTokens = ContextLayerTokenUsage(
                workingMemoryTokens = workingMemoryTokens,
                summaryTokens = summaryTokens,
                recalledHistoryTokens = recalledHistoryTokens,
                recentRawTokens = policy.estimator.estimateMessages(rawMessages).estimatedTokens,
            ),
            contextFingerprint = ConversationContextPolicy.compilationFingerprint(
                state = policy.contextState,
                includeDerivedContext = policy.includeDerivedContext,
                recalledHistoryFingerprint = retainedRecall.takeIf(List<*>::isNotEmpty)
                    ?.let(ConversationRecallPolicy::fingerprint),
            ),
        )
    }

    /**
     * D134: after the text turns are chosen, earlier attachments are resent newest first while the
     * target accepts their kind, the request byte and image bounds hold and the token estimate stays
     * within [ContextCompilationPolicy.maximumInputTokens]. The prompt's own attachments always count.
     */
    private fun replayAttachments(
        policy: ContextCompilationPolicy,
        prompt: GenerationMessage,
        derivedMessages: List<GenerationMessage>,
        retained: List<CompletedChatTurn>,
    ): Map<String, GenerationAttachment> {
        val replay = policy.attachmentReplay ?: return emptyMap()
        val input = replay.input ?: return emptyMap()
        val candidates = retained.asReversed().flatMap { turn -> turn.user.attachments }
        if (candidates.isEmpty()) return emptyMap()
        val textOnly = derivedMessages + retained.flatMap { turn ->
            listOf(
                userMessage(turn.user, emptyMap()),
                GenerationMessage(GenerationRole.ASSISTANT, listOf(turn.assistant.text)),
            )
        } + prompt
        var tokens = policy.estimator.estimateMessages(textOnly).estimatedTokens
        var bytes = prompt.attachments.sumOf(GenerationAttachment::byteCount)
        var images = prompt.attachments.count { attachment -> attachment.kind == GenerationAttachmentKind.IMAGE }
        val selected = LinkedHashMap<String, GenerationAttachment>()
        candidates.forEach { attachment ->
            if (attachment.id in selected) return@forEach
            if (ChatAttachmentLimits.unsupported(listOf(attachment), input).isNotEmpty()) return@forEach
            if (bytes + attachment.sizeBytes > input.maximumRequestBytes) return@forEach
            val image = attachment.kind == ChatAttachmentKind.IMAGE
            if (image && images + 1 > input.maximumRequestImages) return@forEach
            val resolved = replay.resolve(attachment) ?: return@forEach
            val attachmentTokens = resolved.estimatedTokens(policy.estimator.tokensPerUtf8Byte)
            if (saturatedAdd(tokens, attachmentTokens) > policy.maximumInputTokens) return@forEach
            selected[attachment.id] = resolved
            tokens = saturatedAdd(tokens, attachmentTokens)
            bytes += attachment.sizeBytes
            if (image) images += 1
        }
        return selected
    }

    /** A history user message with its resent attachments and a note for the others. */
    private fun userMessage(message: ChatMessage, replayed: Map<String, GenerationAttachment>): GenerationMessage {
        val included = message.attachments.mapNotNull { attachment -> replayed[attachment.id] }
        val note = ChatAttachmentNotes.omitted(message.attachments.filterNot { attachment -> attachment.id in replayed })
        val textParts = when {
            note == null -> listOf(message.text)
            message.text.isEmpty() -> listOf(note)
            else -> listOf(message.text, "\n\n$note")
        }
        return GenerationMessage(GenerationRole.USER, textParts, attachments = included)
    }

    private fun fits(
        policy: ContextCompilationPolicy,
        prompt: GenerationMessage,
        derivedMessages: List<GenerationMessage>,
        retainedReversed: List<CompletedChatTurn>,
    ): Boolean {
        val rawMessages = retainedReversed.asReversed().flatMap { turn ->
            listOf(
                userMessage(turn.user, emptyMap()),
                GenerationMessage(GenerationRole.ASSISTANT, listOf(turn.assistant.text)),
            )
        }
        val history = derivedMessages + rawMessages
        val historyEstimate = policy.estimator.estimateMessages(history)
        if (historyEstimate.utf8Bytes > policy.maximumHistoryBytes) return false
        return policy.estimator.estimateMessages(history + prompt).estimatedTokens <=
            policy.maximumInputTokens
    }

    private fun CompletedChatTurn.utf8Bytes(): Long = saturatedAdd(
        user.text.toByteArray(Charsets.UTF_8).size.toLong(),
        assistant.text.toByteArray(Charsets.UTF_8).size.toLong(),
    )

    /** Cumulative provider input through one visible message, saturated for hostile counters. */
    fun cumulativeInputTokensThrough(messages: List<ChatMessage>, messageId: Long): Long {
        var total = 0L
        messages.forEach { message ->
            message.usage?.let { usage -> total = saturatedAdd(total, usage.inputTokens) }
            if (message.id == messageId) return total
        }
        return 0L
    }

    /**
     * Restores a transcript for display. Only the reply the generation coordinator is still
     * producing ([generatingMessageId], roadmap D77) stays GENERATING; any other GENERATING reply
     * lost its generation (for example with the process) and becomes STOPPED.
     */
    fun restore(messages: List<ChatMessage>, generatingMessageId: Long? = null): List<ChatMessage> =
        messages.map { message ->
            if (message.status == ChatMessageStatus.GENERATING && message.id != generatingMessageId) {
                message.copy(status = ChatMessageStatus.STOPPED)
            } else {
                message
            }
        }

    fun completedTurns(messages: List<ChatMessage>): List<CompletedChatTurn> {
        val turns = ArrayList<CompletedChatTurn>()
        var pendingUser: ChatMessage? = null
        messages.forEach { message ->
            when (message.role) {
                ChatMessageRole.USER -> pendingUser = message
                ChatMessageRole.ASSISTANT -> {
                    val user = pendingUser
                    if (
                        user != null && message.status == ChatMessageStatus.COMPLETE &&
                        message.text.isNotBlank()
                    ) {
                        turns += CompletedChatTurn(user, message)
                    }
                    pendingUser = null
                }
                ChatMessageRole.NOTICE -> Unit
            }
        }
        return turns
    }

}
