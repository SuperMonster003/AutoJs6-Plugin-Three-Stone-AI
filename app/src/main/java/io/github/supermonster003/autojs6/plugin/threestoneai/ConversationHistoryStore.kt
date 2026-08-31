package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal data class ConversationImportResult(
    val added: Int,
    val updated: Int,
    val skipped: Int,
) {
    val imported: Int
        get() = added + updated
}

internal class ConversationHistoryStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val historyFile = AtomicFile(File(applicationContext.filesDir, HISTORY_FILE_NAME))
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val recallStore = ConversationRecallStore(applicationContext)

    fun loadAll(): List<StoredConversation> {
        val (conversations, mutationEpoch) = synchronized(PROCESS_LOCK) {
            readUnlocked() to HISTORY_MUTATION_EPOCH.get()
        }
        scheduleRecallReconciliation(conversations, mutationEpoch)
        return conversations
    }

    fun find(id: String?): StoredConversation? {
        if (id.isNullOrBlank()) return null
        return loadAll().firstOrNull { conversation -> conversation.id == id }
    }

    fun upsert(
        conversation: StoredConversation,
        reviveDeleted: Boolean = false,
    ): Boolean = synchronized(PROCESS_LOCK) {
        if (conversation.id in DELETED_CONVERSATION_IDS && !reviveDeleted) {
            return@synchronized false
        }
        val existing = readUnlocked()
        val merged = buildList {
            add(conversation)
            addAll(existing.filterNot { stored -> stored.id == conversation.id })
        }
        val normalized = ConversationHistoryPolicy.normalized(merged)
        if (!writeUnlocked(normalized)) {
            return@synchronized false
        }
        HISTORY_MUTATION_EPOCH.incrementAndGet()
        recallStore.synchronize(conversation)
        recallStore.delete(
            existing.mapTo(HashSet(), StoredConversation::id) -
                normalized.mapTo(HashSet(), StoredConversation::id),
        )
        DELETED_CONVERSATION_IDS.remove(conversation.id)
        rememberLastConversation(conversation.id)
        true
    }

    fun delete(conversationIds: Set<String>): Int = synchronized(PROCESS_LOCK) {
        if (conversationIds.isEmpty()) return@synchronized 0
        val existing = readUnlocked()
        val retained = existing.filterNot { conversation -> conversation.id in conversationIds }
        val deleted = existing.size - retained.size
        if (deleted > 0 && writeUnlocked(retained)) {
            HISTORY_MUTATION_EPOCH.incrementAndGet()
            recallStore.delete(conversationIds)
            DELETED_CONVERSATION_IDS += existing
                .asSequence()
                .map(StoredConversation::id)
                .filter(conversationIds::contains)
            updateLastConversationAfterMutation(retained)
            deleted
        } else {
            0
        }
    }

    fun clear(): Int = synchronized(PROCESS_LOCK) {
        val existing = readUnlocked()
        if (existing.isEmpty()) {
            recallStore.clear()
            return@synchronized 0
        }
        if (!writeUnlocked(emptyList())) return@synchronized 0
        HISTORY_MUTATION_EPOCH.incrementAndGet()
        recallStore.clear()
        DELETED_CONVERSATION_IDS += existing.map(StoredConversation::id)
        preferences.edit().remove(KEY_LAST_CONVERSATION_ID).apply()
        existing.size
    }

    fun importConversations(imported: List<StoredConversation>): ConversationImportResult =
        synchronized(PROCESS_LOCK) {
            val incoming = ConversationHistoryPolicy.normalized(imported)
            val merged = readUnlocked().associateByTo(LinkedHashMap(), StoredConversation::id)
            var added = 0
            var updated = 0
            var skipped = 0
            val changed = ArrayList<StoredConversation>()
            incoming.forEach { conversation ->
                val current = merged[conversation.id]
                when {
                    current == null -> {
                        merged[conversation.id] = conversation
                        added += 1
                        changed += conversation
                    }
                    conversation.updatedAtMillis > current.updatedAtMillis -> {
                        merged[conversation.id] = conversation
                        updated += 1
                        changed += conversation
                    }
                    else -> skipped += 1
                }
            }
            if (added > 0 || updated > 0) {
                val normalized = ConversationHistoryPolicy.normalized(merged.values.toList())
                check(writeUnlocked(normalized)) { "Unable to save imported conversation history" }
                HISTORY_MUTATION_EPOCH.incrementAndGet()
                changed.forEach(recallStore::replace)
                recallStore.delete(
                    merged.keys - normalized.mapTo(HashSet(), StoredConversation::id),
                )
                DELETED_CONVERSATION_IDS.removeAll(incoming.map(StoredConversation::id).toSet())
                updateLastConversationAfterMutation(normalized)
            }
            ConversationImportResult(added, updated, skipped)
        }

    fun rememberLastConversation(id: String) {
        preferences.edit().putString(KEY_LAST_CONVERSATION_ID, id).apply()
    }

    fun lastConversationId(): String? = preferences.getString(KEY_LAST_CONVERSATION_ID, null)

    fun recall(
        conversationId: String,
        query: String,
        excludedMessageIds: Set<Long>,
        maximumResults: Int = ConversationRecallPolicy.MAXIMUM_RECALLED_CHUNKS,
    ): List<ConversationRecallChunk> = recallStore.search(
        conversationId = conversationId,
        query = query,
        excludedMessageIds = excludedMessageIds,
        maximumResults = maximumResults,
    )

    private fun scheduleRecallReconciliation(
        initialConversations: List<StoredConversation>,
        initialMutationEpoch: Long,
    ) {
        if (!RECALL_RECONCILIATION_SCHEDULED.compareAndSet(false, true)) return
        try {
            RECALL_EXECUTOR.execute {
                var conversations = initialConversations
                var mutationEpoch = initialMutationEpoch
                try {
                    while (true) {
                        if (HISTORY_MUTATION_EPOCH.get() != mutationEpoch) {
                            synchronized(PROCESS_LOCK) {
                                conversations = readUnlocked()
                                mutationEpoch = HISTORY_MUTATION_EPOCH.get()
                            }
                        }
                        recallStore.reconcile(conversations)
                        if (HISTORY_MUTATION_EPOCH.get() == mutationEpoch) break
                    }
                } finally {
                    RECALL_RECONCILIATION_SCHEDULED.set(false)
                }
            }
        } catch (error: RejectedExecutionException) {
            RECALL_RECONCILIATION_SCHEDULED.set(false)
            Log.w(TAG, "Unable to schedule conversation recall reconciliation", error)
        }
    }

    private fun updateLastConversationAfterMutation(retained: List<StoredConversation>) {
        val remembered = lastConversationId()
        if (remembered != null && retained.any { conversation -> conversation.id == remembered }) {
            return
        }
        preferences.edit().apply {
            val replacement = retained.firstOrNull()?.id
            if (replacement == null) remove(KEY_LAST_CONVERSATION_ID)
            else putString(KEY_LAST_CONVERSATION_ID, replacement)
        }.apply()
    }

    private fun readUnlocked(): List<StoredConversation> {
        val baseFile = historyFile.baseFile
        if (!baseFile.exists()) return emptyList()
        if (baseFile.length() > ConversationHistoryCodec.MAXIMUM_FILE_BYTES) {
            Log.e(TAG, "Conversation history exceeds the safe file limit")
            return emptyList()
        }
        return runCatching {
            historyFile.openRead().use { input -> ConversationHistoryCodec.decode(input.readBytes()) }
        }.onFailure { error ->
            Log.e(TAG, "Unable to read conversation history", error)
        }.getOrDefault(emptyList())
    }

    private fun writeUnlocked(conversations: List<StoredConversation>): Boolean {
        val bytes = runCatching { ConversationHistoryCodec.encode(conversations) }
            .onFailure { error -> Log.e(TAG, "Unable to encode conversation history", error) }
            .getOrNull() ?: return false
        historyFile.baseFile.parentFile?.mkdirs()
        val output = runCatching(historyFile::startWrite)
            .onFailure { error -> Log.e(TAG, "Unable to open conversation history", error) }
            .getOrNull() ?: return false
        return try {
            output.write(bytes)
            output.flush()
            historyFile.finishWrite(output)
            true
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to save conversation history", error)
            historyFile.failWrite(output)
            false
        }
    }

    private companion object {
        val PROCESS_LOCK = Any()
        val DELETED_CONVERSATION_IDS = HashSet<String>()
        val RECALL_RECONCILIATION_SCHEDULED = AtomicBoolean(false)
        val HISTORY_MUTATION_EPOCH = AtomicLong(0L)
        val RECALL_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "three-stone-ai-recall-index").apply { isDaemon = true }
        }
        const val TAG = "ConversationHistory"
        const val HISTORY_FILE_NAME = "conversation-history.bin"
        const val PREFERENCES_NAME = "conversation-navigation"
        const val KEY_LAST_CONVERSATION_ID = "last-conversation-id"
    }
}

internal object ConversationNavigation {
    const val EXTRA_CONVERSATION_ID =
        "io.github.supermonster003.autojs6.plugin.threestoneai.extra.CONVERSATION_ID"
}
