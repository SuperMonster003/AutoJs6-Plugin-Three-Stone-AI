package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Disposable secondary index. ConversationHistoryStore's AtomicFile and L1/L2 checkpoints remain
 * authoritative; every failure here is fail-open and can only reduce optional historical recall.
 * Retained raw history is rebuilt automatically, while cached excerpts older than that history may
 * be lost after database recovery without compromising the conversation itself.
 */
internal class ConversationRecallStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {
    private val applicationContext = context.applicationContext

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $CHUNK_TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id TEXT NOT NULL,
                first_message_id INTEGER NOT NULL,
                last_message_id INTEGER NOT NULL,
                updated_at_millis INTEGER NOT NULL,
                turn_count INTEGER NOT NULL,
                source_hash TEXT NOT NULL,
                payload TEXT NOT NULL,
                UNIQUE(conversation_id, first_message_id, last_message_id, source_hash)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX recall_chunks_conversation ON $CHUNK_TABLE" +
                "(conversation_id, last_message_id)",
        )
        database.execSQL(
            "CREATE INDEX recall_chunks_recency ON $CHUNK_TABLE" +
                "(updated_at_millis, last_message_id)",
        )
        database.execSQL(
            "CREATE VIRTUAL TABLE $FTS_TABLE USING fts4(" +
                "search_terms, tokenize=unicode61)",
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        database.execSQL("DROP TABLE IF EXISTS $FTS_TABLE")
        database.execSQL("DROP TABLE IF EXISTS $CHUNK_TABLE")
        onCreate(database)
    }

    fun synchronize(conversation: StoredConversation) {
        safely("synchronize conversation=${conversation.id}") { database ->
            database.inTransaction {
                synchronize(database, conversation)
                prune(database, conversation.id)
            }
        }
    }

    fun replace(conversation: StoredConversation) {
        safely("replace conversation=${conversation.id}") { database ->
            database.inTransaction {
                deleteConversation(database, conversation.id)
                ConversationRecallPolicy.chunks(conversation).forEach { chunk ->
                    insert(database, chunk)
                }
                prune(database, conversation.id)
            }
        }
    }

    fun reconcile(conversations: List<StoredConversation>) {
        safely("reconcile conversations=${conversations.size}") { database ->
            database.inTransaction {
                deleteConversationsNotIn(database, conversations.mapTo(HashSet()) { it.id })
                conversations.forEach { conversation -> synchronize(database, conversation) }
                pruneGlobal(database)
            }
        }
    }

    fun delete(conversationIds: Set<String>) {
        if (conversationIds.isEmpty()) return
        safely("delete conversations=${conversationIds.size}") { database ->
            database.inTransaction {
                conversationIds.forEach { conversationId ->
                    deleteConversation(database, conversationId)
                }
            }
        }
    }

    fun clear() {
        safely("clear") { database ->
            database.inTransaction {
                database.delete(FTS_TABLE, null, null)
                database.delete(CHUNK_TABLE, null, null)
            }
        }
    }

    fun search(
        conversationId: String,
        query: String,
        excludedMessageIds: Set<Long>,
        maximumResults: Int = ConversationRecallPolicy.MAXIMUM_RECALLED_CHUNKS,
    ): List<ConversationRecallChunk> {
        if (conversationId.isBlank()) return emptyList()
        val terms = ConversationRecallPolicy.queryTerms(query)
        if (terms.isEmpty()) return emptyList()
        return safely("search conversation=$conversationId", emptyList()) { database ->
            val candidates = queryCandidates(
                database = database,
                conversationId = conversationId,
                matchExpression = ConversationRecallPolicy.ftsMatchExpression(terms),
            ).filter { chunk ->
                chunk.sourceMessageIds.none(excludedMessageIds::contains)
            }
            ConversationRecallPolicy.rank(candidates, terms, maximumResults)
                .map(ConversationRecallScore::chunk)
                .also { results ->
                    Log.d(
                        TAG,
                        "Recall search conversation=$conversationId terms=${terms.size} " +
                            "candidates=${candidates.size} results=${results.size} " +
                            "ranges=${results.joinToString { chunk ->
                                "${chunk.firstMessageId}-${chunk.lastMessageId}"
                            }}",
                    )
                }
        }
    }

    private fun synchronize(database: SQLiteDatabase, conversation: StoredConversation) {
        val desired = ConversationRecallPolicy.chunks(conversation)
        val replacementFrom = conversation.messages.firstOrNull { message ->
            message.role != ChatMessageRole.NOTICE
        }?.id ?: Long.MAX_VALUE
        val existing = existing(database, conversation.id)
        val desiredIdentities = desired.mapTo(HashSet(), ConversationRecallChunk::identity)
        val staleIds = existing.filter { row ->
            row.lastMessageId >= replacementFrom && row.identity !in desiredIdentities
        }.map(ExistingChunk::rowId)
        deleteRows(database, staleIds)

        val retainedIdentities = existing.asSequence()
            .filterNot { row -> row.rowId in staleIds }
            .map(ExistingChunk::identity)
            .toHashSet()
        var inserted = 0
        desired.filterNot { chunk -> chunk.identity in retainedIdentities }
            .forEach { chunk ->
                insert(database, chunk)
                inserted += 1
            }
        if (staleIds.isNotEmpty() || inserted > 0) {
            Log.d(
                TAG,
                "Recall index sync conversation=${conversation.id} " +
                    "deleted=${staleIds.size} inserted=$inserted desired=${desired.size}",
            )
        }
    }

    private fun existing(database: SQLiteDatabase, conversationId: String): List<ExistingChunk> =
        database.query(
            CHUNK_TABLE,
            arrayOf("id", "first_message_id", "last_message_id", "source_hash"),
            "conversation_id = ?",
            arrayOf(conversationId),
            null,
            null,
            "first_message_id ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ExistingChunk(
                            rowId = cursor.getLong(0),
                            conversationId = conversationId,
                            firstMessageId = cursor.getLong(1),
                            lastMessageId = cursor.getLong(2),
                            sourceHash = cursor.getString(3),
                        ),
                    )
                }
            }
        }

    private fun queryCandidates(
        database: SQLiteDatabase,
        conversationId: String,
        matchExpression: String,
    ): List<ConversationRecallChunk> = database.rawQuery(
        """
        SELECT $CHUNK_TABLE.conversation_id,
               $CHUNK_TABLE.first_message_id,
               $CHUNK_TABLE.last_message_id,
               $CHUNK_TABLE.updated_at_millis,
               $CHUNK_TABLE.source_hash,
               $CHUNK_TABLE.payload,
               $FTS_TABLE.search_terms
          FROM $FTS_TABLE
          JOIN $CHUNK_TABLE ON $CHUNK_TABLE.id = $FTS_TABLE.rowid
         WHERE $FTS_TABLE MATCH ? AND $CHUNK_TABLE.conversation_id = ?
         LIMIT $MAXIMUM_FTS_CANDIDATES
        """.trimIndent(),
        arrayOf(matchExpression, conversationId),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                runCatching {
                    val turns = ConversationRecallPayloadCodec.decode(cursor.getString(5))
                    ConversationRecallChunk(
                        conversationId = cursor.getString(0),
                        firstMessageId = cursor.getLong(1),
                        lastMessageId = cursor.getLong(2),
                        updatedAtMillis = cursor.getLong(3),
                        turns = turns,
                        searchTerms = cursor.getString(6).split(' ').filter(String::isNotBlank),
                        sourceHash = cursor.getString(4),
                    )
                }.onFailure { error ->
                    Log.w(TAG, "Ignoring malformed recall index row", error)
                }.getOrNull()?.let(::add)
            }
        }
    }

    private fun insert(database: SQLiteDatabase, chunk: ConversationRecallChunk) {
        val rowId = database.insertOrThrow(
            CHUNK_TABLE,
            null,
            ContentValues().apply {
                put("conversation_id", chunk.conversationId)
                put("first_message_id", chunk.firstMessageId)
                put("last_message_id", chunk.lastMessageId)
                put("updated_at_millis", chunk.updatedAtMillis)
                put("turn_count", chunk.turns.size)
                put("source_hash", chunk.sourceHash)
                put("payload", ConversationRecallPayloadCodec.encode(chunk.turns))
            },
        )
        database.insertOrThrow(
            FTS_TABLE,
            null,
            ContentValues().apply {
                put("rowid", rowId)
                put("search_terms", chunk.searchTerms.joinToString(" "))
            },
        )
    }

    private fun prune(database: SQLiteDatabase, conversationId: String) {
        val staleIds = database.rawQuery(
            "SELECT id FROM $CHUNK_TABLE WHERE conversation_id = ? " +
                "ORDER BY last_message_id DESC LIMIT -1 OFFSET $MAXIMUM_CHUNKS_PER_CONVERSATION",
            arrayOf(conversationId),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        deleteRows(database, staleIds)
        pruneGlobal(database)
    }

    private fun pruneGlobal(database: SQLiteDatabase) {
        val staleIds = database.rawQuery(
            "SELECT id FROM $CHUNK_TABLE ORDER BY updated_at_millis DESC, " +
                "last_message_id DESC LIMIT -1 OFFSET $MAXIMUM_GLOBAL_CHUNKS",
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        deleteRows(database, staleIds)
    }

    private fun deleteConversationsNotIn(database: SQLiteDatabase, retainedIds: Set<String>) {
        val staleIds = if (retainedIds.isEmpty()) {
            database.rawQuery("SELECT id FROM $CHUNK_TABLE", null)
        } else {
            val placeholders = List(retainedIds.size) { "?" }.joinToString(",")
            database.rawQuery(
                "SELECT id FROM $CHUNK_TABLE WHERE conversation_id NOT IN ($placeholders)",
                retainedIds.toTypedArray(),
            )
        }.use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        deleteRows(database, staleIds)
    }

    private fun deleteConversation(database: SQLiteDatabase, conversationId: String) {
        val rowIds = database.query(
            CHUNK_TABLE,
            arrayOf("id"),
            "conversation_id = ?",
            arrayOf(conversationId),
            null,
            null,
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        deleteRows(database, rowIds)
    }

    private fun deleteRows(database: SQLiteDatabase, rowIds: List<Long>) {
        if (rowIds.isEmpty()) return
        rowIds.chunked(MAXIMUM_SQL_ARGUMENTS).forEach { chunk ->
            val placeholders = List(chunk.size) { "?" }.joinToString(",")
            val arguments = chunk.map(Long::toString).toTypedArray()
            database.delete(FTS_TABLE, "rowid IN ($placeholders)", arguments)
            database.delete(CHUNK_TABLE, "id IN ($placeholders)", arguments)
        }
    }

    private inline fun <T> safely(
        operationName: String,
        fallback: T,
        operation: (SQLiteDatabase) -> T,
    ): T = synchronized(DATABASE_LOCK) {
        try {
            operation(writableDatabase)
        } catch (first: SQLiteException) {
            Log.e(TAG, "Recall database failed during $operationName; rebuilding", first)
            resetDatabase()
            try {
                operation(writableDatabase)
            } catch (second: Throwable) {
                Log.e(TAG, "Recall database retry failed during $operationName", second)
                fallback
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Recall index failed during $operationName", error)
            fallback
        }
    }

    private inline fun safely(
        operationName: String,
        operation: (SQLiteDatabase) -> Unit,
    ) = safely(operationName, Unit, operation)

    private fun resetDatabase() {
        runCatching(::close)
        if (!applicationContext.deleteDatabase(DATABASE_NAME)) {
            Log.w(TAG, "Recall database reset did not remove an existing database")
        }
    }

    private inline fun SQLiteDatabase.inTransaction(block: () -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private data class ExistingChunk(
        val rowId: Long,
        val conversationId: String,
        val firstMessageId: Long,
        val lastMessageId: Long,
        val sourceHash: String,
    ) {
        val identity: String
            get() = "$conversationId:$firstMessageId:$lastMessageId:$sourceHash"
    }

    private companion object {
        val DATABASE_LOCK = Any()
        const val TAG = "ConversationRecall"
        const val DATABASE_NAME = "conversation-recall.db"
        const val DATABASE_VERSION = 1
        const val CHUNK_TABLE = "recall_chunks"
        const val FTS_TABLE = "recall_chunk_terms"
        const val MAXIMUM_FTS_CANDIDATES = 64
        const val MAXIMUM_CHUNKS_PER_CONVERSATION = 512
        const val MAXIMUM_GLOBAL_CHUNKS = 4_096
        const val MAXIMUM_SQL_ARGUMENTS = 900
    }
}
