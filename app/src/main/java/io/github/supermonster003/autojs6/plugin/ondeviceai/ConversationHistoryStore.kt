package io.github.supermonster003.autojs6.plugin.ondeviceai

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File

internal class ConversationHistoryStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val historyFile = AtomicFile(File(applicationContext.filesDir, HISTORY_FILE_NAME))
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadAll(): List<StoredConversation> = synchronized(PROCESS_LOCK) { readUnlocked() }

    fun find(id: String?): StoredConversation? {
        if (id.isNullOrBlank()) return null
        return loadAll().firstOrNull { conversation -> conversation.id == id }
    }

    fun upsert(conversation: StoredConversation) = synchronized(PROCESS_LOCK) {
        val merged = buildList {
            add(conversation)
            addAll(readUnlocked().filterNot { stored -> stored.id == conversation.id })
        }
        writeUnlocked(ConversationHistoryPolicy.normalized(merged))
        rememberLastConversation(conversation.id)
    }

    fun rememberLastConversation(id: String) {
        preferences.edit().putString(KEY_LAST_CONVERSATION_ID, id).apply()
    }

    fun lastConversationId(): String? = preferences.getString(KEY_LAST_CONVERSATION_ID, null)

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

    private fun writeUnlocked(conversations: List<StoredConversation>) {
        val bytes = runCatching { ConversationHistoryCodec.encode(conversations) }
            .onFailure { error -> Log.e(TAG, "Unable to encode conversation history", error) }
            .getOrNull() ?: return
        historyFile.baseFile.parentFile?.mkdirs()
        val output = runCatching(historyFile::startWrite)
            .onFailure { error -> Log.e(TAG, "Unable to open conversation history", error) }
            .getOrNull() ?: return
        try {
            output.write(bytes)
            output.flush()
            historyFile.finishWrite(output)
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to save conversation history", error)
            historyFile.failWrite(output)
        }
    }

    private companion object {
        val PROCESS_LOCK = Any()
        const val TAG = "ConversationHistory"
        const val HISTORY_FILE_NAME = "conversation-history.bin"
        const val PREFERENCES_NAME = "conversation-navigation"
        const val KEY_LAST_CONVERSATION_ID = "last-conversation-id"
    }
}

internal object ConversationNavigation {
    const val EXTRA_CONVERSATION_ID =
        "io.github.supermonster003.autojs6.plugin.ondeviceai.extra.CONVERSATION_ID"
}
