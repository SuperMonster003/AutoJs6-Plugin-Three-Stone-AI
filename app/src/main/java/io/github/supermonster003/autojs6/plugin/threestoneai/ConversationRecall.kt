package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Locale

internal data class ConversationRecallTurn(
    val userMessageId: Long,
    val assistantMessageId: Long,
    val userText: String,
    val assistantText: String,
) {
    init {
        require(userMessageId > 0L)
        require(assistantMessageId > userMessageId)
    }

    val sourceMessageIds: List<Long>
        get() = listOf(userMessageId, assistantMessageId)
}

/** One relevance-ranked, chronologically intact historical transcript chunk. */
internal data class ConversationRecallChunk(
    val conversationId: String,
    val firstMessageId: Long,
    val lastMessageId: Long,
    val updatedAtMillis: Long,
    val turns: List<ConversationRecallTurn>,
    val searchTerms: List<String>,
    val sourceHash: String,
) {
    init {
        require(conversationId.isNotBlank())
        require(firstMessageId > 0L)
        require(lastMessageId >= firstMessageId)
        require(updatedAtMillis >= 0L)
        require(turns.size in ConversationRecallPolicy.MINIMUM_TURNS_PER_CHUNK..
            ConversationRecallPolicy.MAXIMUM_TURNS_PER_CHUNK)
        require(turns.first().userMessageId == firstMessageId)
        require(turns.last().assistantMessageId == lastMessageId)
        require(turns.zipWithNext().all { (left, right) ->
            left.assistantMessageId < right.userMessageId
        })
        require(searchTerms.isNotEmpty())
        require(searchTerms == searchTerms.distinct())
        require(SHA_256.matches(sourceHash))
    }

    val sourceMessageIds: List<Long>
        get() = turns.flatMap(ConversationRecallTurn::sourceMessageIds)

    val identity: String
        get() = "$conversationId:$firstMessageId:$lastMessageId:$sourceHash"

    fun generationMessages(): List<GenerationMessage> = turns.flatMap { turn ->
        listOf(
            GenerationMessage(GenerationRole.USER, listOf(turn.userText)),
            GenerationMessage(GenerationRole.ASSISTANT, listOf(turn.assistantText)),
        )
    }

    private companion object {
        val SHA_256 = Regex("^[0-9a-f]{64}$")
    }
}

internal data class ConversationRecallScore(
    val chunk: ConversationRecallChunk,
    val matchedTerms: Int,
    val keywordCoverage: Double,
    val recencyScore: Double,
    val combinedScore: Double,
) {
    init {
        require(matchedTerms > 0)
        require(keywordCoverage in 0.0..1.0)
        require(recencyScore in 0.0..1.0)
        require(combinedScore in 0.0..1.0)
    }
}

/** Pure FTS document, ranking, and sticky-recall rules. */
internal object ConversationRecallPolicy {
    const val MINIMUM_TURNS_PER_CHUNK = 2
    const val TARGET_TURNS_PER_CHUNK = 3
    const val MAXIMUM_TURNS_PER_CHUNK = 4
    const val MAXIMUM_RECALLED_CHUNKS = 4
    const val MAXIMUM_QUERY_TERMS = 24
    const val MAXIMUM_INDEX_TERMS = 4_096
    const val MAXIMUM_TEXT_CODE_POINTS = 4_096
    const val MAXIMUM_RECALL_LAYER_TOKENS = 1_024L

    fun chunks(conversation: StoredConversation): List<ConversationRecallChunk> {
        val turns = ChatConversationPolicy.completedTurns(conversation.messages)
        return turns.chunked(TARGET_TURNS_PER_CHUNK)
            .filter { chunk -> chunk.size >= MINIMUM_TURNS_PER_CHUNK }
            .map { chunk -> createChunk(conversation, chunk) }
            .filter { chunk -> chunk.searchTerms.isNotEmpty() }
    }

    fun queryTerms(query: String): List<String> = tokenize(query, MAXIMUM_QUERY_TERMS)

    fun ftsMatchExpression(terms: List<String>): String = terms
        .take(MAXIMUM_QUERY_TERMS)
        .joinToString(" OR ") { term -> "\"$term\"" }

    fun rank(
        chunks: List<ConversationRecallChunk>,
        terms: List<String>,
        maximumResults: Int = MAXIMUM_RECALLED_CHUNKS,
    ): List<ConversationRecallScore> {
        require(maximumResults in 1..MAXIMUM_RECALLED_CHUNKS)
        val query = terms.distinct().take(MAXIMUM_QUERY_TERMS).toSet()
        if (query.isEmpty() || chunks.isEmpty()) return emptyList()
        val oldestTime = chunks.minOf(ConversationRecallChunk::updatedAtMillis)
        val newestTime = chunks.maxOf(ConversationRecallChunk::updatedAtMillis)
        val oldestMessage = chunks.minOf(ConversationRecallChunk::lastMessageId)
        val newestMessage = chunks.maxOf(ConversationRecallChunk::lastMessageId)
        val timeSpan = newestTime - oldestTime
        val messageSpan = (newestMessage - oldestMessage).coerceAtLeast(1L).toDouble()
        return chunks.mapNotNull { chunk ->
            val matched = chunk.searchTerms.count(query::contains)
            if (matched == 0) return@mapNotNull null
            val coverage = matched.toDouble() / query.size.toDouble()
            val recency = if (timeSpan > 0L) {
                (chunk.updatedAtMillis - oldestTime).toDouble() / timeSpan.toDouble()
            } else {
                (chunk.lastMessageId - oldestMessage).toDouble() / messageSpan
            }
            val combined = KEYWORD_WEIGHT * coverage + RECENCY_WEIGHT * recency
            ConversationRecallScore(
                chunk = chunk,
                matchedTerms = matched,
                keywordCoverage = coverage,
                recencyScore = recency,
                combinedScore = combined,
            )
        }.sortedWith(
            compareByDescending<ConversationRecallScore>(ConversationRecallScore::combinedScore)
                .thenByDescending(ConversationRecallScore::matchedTerms)
                .thenByDescending { score -> score.chunk.lastMessageId }
                .thenBy { score -> score.chunk.firstMessageId },
        ).take(maximumResults)
    }

    /**
     * Persistent backends keep already-installed recall chunks until a new relevant hit appears.
     * This avoids rebuilding the backend merely because the next prompt has no FTS match.
     */
    fun mergeSticky(
        sticky: List<ConversationRecallChunk>,
        fresh: List<ConversationRecallChunk>,
        maximumResults: Int = MAXIMUM_RECALLED_CHUNKS,
    ): List<ConversationRecallChunk> {
        require(maximumResults in 1..MAXIMUM_RECALLED_CHUNKS)
        val freshRanges = fresh.mapTo(HashSet()) { chunk ->
            chunk.firstMessageId to chunk.lastMessageId
        }
        return (fresh + sticky.filterNot { chunk ->
            chunk.firstMessageId to chunk.lastMessageId in freshRanges
        }).distinctBy(ConversationRecallChunk::identity)
            .take(maximumResults)
    }

    fun sameChunks(
        left: List<ConversationRecallChunk>,
        right: List<ConversationRecallChunk>,
    ): Boolean = left.map(ConversationRecallChunk::identity).toSet() ==
        right.map(ConversationRecallChunk::identity).toSet()

    fun generationMessages(chunks: List<ConversationRecallChunk>): List<GenerationMessage> =
        chunks.sortedBy(ConversationRecallChunk::firstMessageId)
            .flatMap(ConversationRecallChunk::generationMessages)

    fun fingerprint(chunks: List<ConversationRecallChunk>): String = digest {
        writeInt(chunks.size)
        chunks.sortedBy(ConversationRecallChunk::firstMessageId).forEach { chunk ->
            writeText(chunk.identity)
        }
    }

    private fun createChunk(
        conversation: StoredConversation,
        completedTurns: List<CompletedChatTurn>,
    ): ConversationRecallChunk {
        val turns = completedTurns.map { turn ->
            ConversationRecallTurn(
                userMessageId = turn.user.id,
                assistantMessageId = turn.assistant.id,
                userText = excerpt(turn.user.text),
                assistantText = excerpt(turn.assistant.text),
            )
        }
        val terms = tokenize(
            turns.joinToString("\n") { turn -> "${turn.userText}\n${turn.assistantText}" },
            MAXIMUM_INDEX_TERMS,
        ).sorted()
        return ConversationRecallChunk(
            conversationId = conversation.id,
            firstMessageId = turns.first().userMessageId,
            lastMessageId = turns.last().assistantMessageId,
            updatedAtMillis = conversation.updatedAtMillis,
            turns = turns,
            searchTerms = terms,
            sourceHash = sourceHash(completedTurns),
        )
    }

    private fun excerpt(text: String): String {
        val count = text.codePointCount(0, text.length)
        if (count <= MAXIMUM_TEXT_CODE_POINTS) return text
        val headCount = MAXIMUM_TEXT_CODE_POINTS / 2
        val tailCount = MAXIMUM_TEXT_CODE_POINTS - headCount
        val headEnd = text.offsetByCodePoints(0, headCount)
        val tailStart = text.offsetByCodePoints(text.length, -tailCount)
        return text.substring(0, headEnd).trimEnd() + "\n...\n" +
            text.substring(tailStart).trimStart()
    }

    private fun tokenize(text: String, maximumTerms: Int): List<String> {
        if (text.isBlank() || maximumTerms <= 0) return emptyList()
        val terms = LinkedHashSet<String>()
        val word = StringBuilder()
        val ideographs = ArrayList<Int>()

        fun add(value: String) {
            if (terms.size >= maximumTerms) return
            val normalized = value.lowercase(Locale.ROOT).trim('-', '_')
            if (normalized.length < MINIMUM_WORD_CHARACTERS || normalized in STOP_WORDS) return
            terms += normalized
        }

        fun flushWord() {
            if (word.isEmpty()) return
            val original = word.toString()
            add(original)
            CAMEL_BOUNDARY.split(original).forEach(::add)
            original.split('-', '_').forEach(::add)
            word.clear()
        }

        fun flushIdeographs() {
            if (ideographs.isEmpty()) return
            if (ideographs.size == 1) {
                add(String(Character.toChars(ideographs.single())))
            } else {
                for (index in 0 until ideographs.lastIndex) {
                    add(
                        String(Character.toChars(ideographs[index])) +
                            String(Character.toChars(ideographs[index + 1])),
                    )
                    if (terms.size >= maximumTerms) break
                }
            }
            ideographs.clear()
        }

        var offset = 0
        while (offset < text.length && terms.size < maximumTerms) {
            val codePoint = text.codePointAt(offset)
            when {
                isIdeographic(codePoint) -> {
                    flushWord()
                    ideographs += codePoint
                }
                Character.isLetterOrDigit(codePoint) || codePoint == '_'.code ||
                    codePoint == '-'.code -> {
                    flushIdeographs()
                    word.appendCodePoint(codePoint)
                }
                else -> {
                    flushWord()
                    flushIdeographs()
                }
            }
            offset += Character.charCount(codePoint)
        }
        flushWord()
        flushIdeographs()
        return terms.take(maximumTerms)
    }

    private fun isIdeographic(codePoint: Int): Boolean =
        Character.isIdeographic(codePoint) || codePoint in 0x3040..0x30ff ||
            codePoint in 0xac00..0xd7af

    private fun sourceHash(turns: List<CompletedChatTurn>): String = digest {
        writeInt(turns.size)
        turns.forEach { turn ->
            writeLong(turn.user.id)
            writeText(turn.user.text)
            writeLong(turn.assistant.id)
            writeText(turn.assistant.text)
        }
    }

    private fun digest(block: DataOutputStream.() -> Unit): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output -> output.block() }
            buffer.toByteArray()
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
    private val STOP_WORDS = setOf(
        "about", "after", "again", "also", "and", "are", "before", "can", "could",
        "did", "does", "for", "from", "have", "how", "into", "just", "our", "please",
        "said", "say", "that", "the", "their", "then", "this", "was", "were", "what",
        "when", "where", "which", "with", "would", "your", "之前", "什么", "关于", "提到",
        "记得", "我们",
    )
    private const val MINIMUM_WORD_CHARACTERS = 2
    private const val KEYWORD_WEIGHT = 0.85
    private const val RECENCY_WEIGHT = 0.15
}

/** Length-prefixed text payload kept out of the FTS token index. */
internal object ConversationRecallPayloadCodec {
    fun encode(turns: List<ConversationRecallTurn>): String {
        require(turns.size in ConversationRecallPolicy.MINIMUM_TURNS_PER_CHUNK..
            ConversationRecallPolicy.MAXIMUM_TURNS_PER_CHUNK)
        return buildString {
            append(MAGIC).append('\n')
            append(turns.size).append('\n')
            turns.forEach { turn ->
                append(turn.userMessageId).append('\n')
                append(turn.assistantMessageId).append('\n')
                appendField(turn.userText)
                appendField(turn.assistantText)
            }
        }
    }

    fun decode(payload: String): List<ConversationRecallTurn> {
        require(payload.length <= MAXIMUM_PAYLOAD_CHARACTERS) { "Recall payload is too large" }
        val reader = PayloadReader(payload)
        require(reader.readLine() == MAGIC) { "Invalid recall payload magic" }
        val count = reader.readInteger().toInt()
        require(count in ConversationRecallPolicy.MINIMUM_TURNS_PER_CHUNK..
            ConversationRecallPolicy.MAXIMUM_TURNS_PER_CHUNK) {
            "Invalid recall turn count"
        }
        return buildList(count) {
            repeat(count) {
                add(
                    ConversationRecallTurn(
                        userMessageId = reader.readInteger(),
                        assistantMessageId = reader.readInteger(),
                        userText = reader.readField(),
                        assistantText = reader.readField(),
                    ),
                )
            }
        }.also { reader.requireEnd() }
    }

    private fun StringBuilder.appendField(value: String) {
        require(value.length <= MAXIMUM_FIELD_CHARACTERS) { "Recall field is too large" }
        append(value.length).append('\n').append(value).append('\n')
    }

    private class PayloadReader(private val payload: String) {
        private var offset = 0

        fun readInteger(): Long = readLine().toLong().also { value ->
            require(value >= 0L) { "Negative recall payload integer" }
        }

        fun readField(): String {
            val length = readInteger().toInt()
            require(length in 0..MAXIMUM_FIELD_CHARACTERS) { "Invalid recall field length" }
            val end = Math.addExact(offset, length)
            require(end < payload.length && payload[end] == '\n') {
                "Truncated recall payload field"
            }
            return payload.substring(offset, end).also { offset = end + 1 }
        }

        fun readLine(): String {
            val end = payload.indexOf('\n', offset)
            require(end >= offset && end - offset <= MAXIMUM_LINE_CHARACTERS) {
                "Invalid recall payload line"
            }
            return payload.substring(offset, end).also { offset = end + 1 }
        }

        fun requireEnd() {
            require(offset == payload.length) { "Unexpected trailing recall payload data" }
        }
    }

    private const val MAGIC = "TSR1"
    private const val MAXIMUM_FIELD_CHARACTERS =
        ConversationRecallPolicy.MAXIMUM_TEXT_CODE_POINTS * 2 + 5
    private const val MAXIMUM_PAYLOAD_CHARACTERS =
        MAXIMUM_FIELD_CHARACTERS * ConversationRecallPolicy.MAXIMUM_TURNS_PER_CHUNK * 2 + 1_024
    private const val MAXIMUM_LINE_CHARACTERS = 32
}
