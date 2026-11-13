package io.github.supermonster003.autojs6.plugin.threestoneai

/**
 * The versions of one turn that are not shown (roadmap P6, D82, D148).
 *
 * A turn is a user message and everything after it on the shown path. Regenerating a reply or
 * sending an edited message adds a version of the turn instead of deleting the later messages, so
 * one version is the list of messages from its user message to the end of that branch. The versions
 * live on the user message that starts the shown version: [others] are the versions that are not
 * shown, in version order, and the shown one is version [selected] + 1 of [count]. Messages of
 * another version may carry branches of their own (a turn branched inside that version).
 */
internal data class ChatMessageBranches(
    val selected: Int,
    val others: List<List<ChatMessage>>,
) {
    init {
        require(others.isNotEmpty() && others.size < ChatBranchPolicy.MAXIMUM_VERSIONS) {
            "A branched turn has 2 - ${ChatBranchPolicy.MAXIMUM_VERSIONS} versions"
        }
        require(selected in 0..others.size)
        others.forEach { version ->
            require(version.isNotEmpty() && version.first().role == ChatMessageRole.USER) {
                "Every version starts with its user message"
            }
            require(version.first().branches == null) { "The versions of a turn live on its shown message only" }
        }
    }

    val count: Int
        get() = others.size + 1

    /** Every version in order, with [shown] at [selected]. */
    fun versions(shown: List<ChatMessage>): List<List<ChatMessage>> =
        others.toMutableList().apply { add(selected, shown) }
}

/** Where the "< 2/3 >" switch of the turn that starts at [turnMessageId] is shown. */
internal data class ChatBranchPosition(
    val turnMessageId: Long,
    val index: Int,
    val count: Int,
)

/**
 * Pure rules of message branches (roadmap P6, D82, D148). The shown path stays the plain message
 * list every other policy uses (search, export, context compilation, reading aloud, usage), so they
 * follow the selected versions without knowing about branches.
 */
internal object ChatBranchPolicy {
    const val MAXIMUM_VERSIONS = 8

    /** Estimated bytes all other versions of one conversation may take together (D148). */
    const val MAXIMUM_CONVERSATION_BRANCH_BYTES = 2 * 1_024 * 1_024

    /** Versions of the turn that starts at [userMessageId]; 1 without branches, 0 when it is not on the path. */
    fun versionCount(path: List<ChatMessage>, userMessageId: Long): Int {
        val message = path.singleOrNull { candidate -> candidate.id == userMessageId } ?: return 0
        return message.branches?.count ?: 1
    }

    /** The shown path and every other version, nested ones included, in no particular order. */
    fun allMessages(path: List<ChatMessage>): List<ChatMessage> {
        val result = ArrayList<ChatMessage>(path.size)
        val pending = ArrayDeque<List<ChatMessage>>().apply { add(path) }
        while (pending.isNotEmpty()) {
            pending.removeFirst().forEach { message ->
                result += message
                message.branches?.others?.forEach(pending::addLast)
            }
        }
        return result
    }

    /** Largest message ID used anywhere in the conversation, so new IDs never repeat one. */
    fun maximumId(path: List<ChatMessage>): Long = allMessages(path).maxOfOrNull(ChatMessage::id) ?: 0L

    /**
     * Smallest message ID of any version of [turn]: summaries and the backend session derived from
     * there on belong to the version that was shown and are dropped when the version changes.
     */
    fun firstIdOfTurn(turn: ChatMessage): Long =
        minOf(turn.id, turn.branches?.others?.minOf { version -> version.first().id } ?: turn.id)

    /** Attachment file names any version of the conversation refers to. */
    fun attachmentFileNames(path: List<ChatMessage>): Set<String> =
        allMessages(path).flatMap(ChatMessage::attachments).mapTo(HashSet(), ChatAttachment::fileName)

    /**
     * Message ID under which each branched turn shows its switch: the first reply of the turn, or
     * the user message itself when no reply follows it before the next user message.
     */
    fun positions(path: List<ChatMessage>): Map<Long, ChatBranchPosition> {
        val result = HashMap<Long, ChatBranchPosition>()
        path.forEachIndexed { index, message ->
            val branches = message.branches ?: return@forEachIndexed
            var reply: ChatMessage? = null
            for (later in index + 1 until path.size) {
                val candidate = path[later]
                if (candidate.role == ChatMessageRole.USER) break
                if (candidate.role == ChatMessageRole.ASSISTANT) {
                    reply = candidate
                    break
                }
            }
            result[(reply ?: message).id] = ChatBranchPosition(message.id, branches.selected, branches.count)
        }
        return result
    }

    /**
     * The path with a new version of the turn at [userMessageId]: the shown version is kept as
     * another version and [replacement] (a new user message with a new ID) starts the new shown
     * version, the last one. The caller appends the reply. Null when [userMessageId] is not a user
     * message on the path or the turn already has [MAXIMUM_VERSIONS] versions.
     */
    fun withNewVersion(
        path: List<ChatMessage>,
        userMessageId: Long,
        replacement: ChatMessage,
    ): List<ChatMessage>? {
        require(replacement.role == ChatMessageRole.USER && replacement.branches == null)
        val index = turnIndex(path, userMessageId) ?: return null
        val versions = path[index].branches?.versions(shownVersion(path, index)) ?: listOf(shownVersion(path, index))
        if (versions.size >= MAXIMUM_VERSIONS) return null
        return path.take(index) + replacement.copy(branches = ChatMessageBranches(versions.size, versions))
    }

    /** The path showing version [versionIndex] (0-based) of the turn at [userMessageId]; null when nothing changes. */
    fun withSelectedVersion(path: List<ChatMessage>, userMessageId: Long, versionIndex: Int): List<ChatMessage>? {
        val index = turnIndex(path, userMessageId) ?: return null
        val branches = path[index].branches ?: return null
        if (versionIndex !in 0 until branches.count || versionIndex == branches.selected) return null
        val versions = branches.versions(shownVersion(path, index))
        return path.take(index) + shown(versions, versionIndex)
    }

    /**
     * The path without the shown version of the turn at [userMessageId], showing the version before
     * it (or the next one when the first is deleted). Null when the turn has a single version: deleting
     * the last version deletes the turn and everything after it ([ConversationDeletionPolicy]).
     */
    fun withoutShownVersion(path: List<ChatMessage>, userMessageId: Long): List<ChatMessage>? {
        val index = turnIndex(path, userMessageId) ?: return null
        val branches = path[index].branches ?: return null
        val remaining = branches.versions(shownVersion(path, index)).filterIndexed { versionIndex, _ ->
            versionIndex != branches.selected
        }
        return path.take(index) + shown(remaining, (branches.selected - 1).coerceAtLeast(0))
    }

    /**
     * [path] without the branches a damaged or hostile file could add (D148): another version must
     * start with a user message, keep its IDs increasing and above the message before its turn, and
     * no ID may repeat anywhere in the conversation, so any version can be shown. Versions that break
     * this are dropped, nested ones checked the same way; a reply still marked as generating in a
     * version that is not shown becomes stopped, since only a shown reply can be generating.
     */
    fun sanitized(path: List<ChatMessage>): List<ChatMessage> {
        if (path.none { message -> message.branches != null }) return path
        val seen = path.mapTo(HashSet(), ChatMessage::id)
        return sanitizedVersion(path, seen, shown = true)
    }

    /**
     * Drops other versions, oldest version of the earliest branched turn first, until all of them
     * together take at most [maximumBytes] by [bytesOf]; the shown path is never changed.
     */
    fun fitted(path: List<ChatMessage>, maximumBytes: Int, bytesOf: (ChatMessage) -> Int): List<ChatMessage> {
        var current = path
        while (branchBytes(current, bytesOf) > maximumBytes) {
            val index = current.indexOfFirst { message -> message.branches != null }
            if (index < 0) break
            current = current.toMutableList().also { messages ->
                messages[index] = messages[index].copy(branches = withoutOldestOther(checkNotNull(messages[index].branches)))
            }
        }
        return current
    }

    /** Estimated bytes of every version that is not shown, nested ones included. */
    fun branchBytes(path: List<ChatMessage>, bytesOf: (ChatMessage) -> Int): Long =
        path.sumOf { message ->
            message.branches?.others?.sumOf { version ->
                allMessages(version).sumOf { candidate -> bytesOf(candidate).toLong() }
            } ?: 0L
        }

    /** The same versions without the oldest one that is not shown; null when only the shown one is left. */
    fun withoutOldestOther(branches: ChatMessageBranches): ChatMessageBranches? {
        val others = branches.others.drop(1)
        if (others.isEmpty()) return null
        return ChatMessageBranches(if (branches.selected > 0) branches.selected - 1 else 0, others)
    }

    private fun turnIndex(path: List<ChatMessage>, userMessageId: Long): Int? =
        path.indexOfFirst { message -> message.id == userMessageId }
            .takeIf { index -> index >= 0 && path[index].role == ChatMessageRole.USER }

    /** The shown version of the turn at [index], its first message without the branches it carries. */
    private fun shownVersion(path: List<ChatMessage>, index: Int): List<ChatMessage> =
        listOf(path[index].copy(branches = null)) + path.subList(index + 1, path.size)

    /** Messages that show version [selected] of [versions], the others kept on its first message. */
    private fun shown(versions: List<List<ChatMessage>>, selected: Int): List<ChatMessage> {
        val chosen = versions[selected]
        val others = versions.filterIndexed { versionIndex, _ -> versionIndex != selected }
        val head = chosen.first().copy(
            branches = others.takeIf(List<List<ChatMessage>>::isNotEmpty)?.let { ChatMessageBranches(selected, it) },
        )
        return listOf(head) + chosen.drop(1)
    }

    private fun sanitizedVersion(version: List<ChatMessage>, seen: MutableSet<Long>, shown: Boolean): List<ChatMessage> =
        version.mapIndexed { index, message ->
            val restored = if (!shown && message.status == ChatMessageStatus.GENERATING) {
                message.copy(status = ChatMessageStatus.STOPPED)
            } else {
                message
            }
            val branches = restored.branches ?: return@mapIndexed restored
            val previousId = version.getOrNull(index - 1)?.id ?: 0L
            val kept = ArrayList<List<ChatMessage>>()
            var selected = 0
            branches.others.forEachIndexed { otherIndex, other ->
                val ids = other.map(ChatMessage::id)
                val valid = other.first().role == ChatMessageRole.USER && ids.first() > previousId &&
                    ids.zipWithNext().all { (first, second) -> second > first } &&
                    ids.none(seen::contains) && ids.size == ids.toSet().size
                if (!valid) return@forEachIndexed
                seen += ids
                kept += sanitizedVersion(other, seen, shown = false)
                if (otherIndex < branches.selected) selected++
            }
            restored.copy(branches = kept.takeIf(List<List<ChatMessage>>::isNotEmpty)?.let { ChatMessageBranches(selected, it) })
        }
}
