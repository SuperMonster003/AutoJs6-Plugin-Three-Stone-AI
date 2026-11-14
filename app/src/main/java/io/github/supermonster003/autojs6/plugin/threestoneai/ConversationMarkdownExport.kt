package io.github.supermonster003.autojs6.plugin.threestoneai

/** Localized words the Markdown export writes (roadmap P7.1, D150); filled from string resources. */
internal data class MarkdownExportLabels(
    val user: String,
    val assistant: String,
    val model: String,
    val created: String,
    val updated: String,
    val exported: String,
    val attachments: String,
    val reasoning: String,
    /** "Thought for N s" for a reasoning time in whole seconds. */
    val reasoningDuration: (Long) -> String,
    val reasoningTruncated: String,
    val stopped: String,
    val failed: String,
    val truncated: String,
    val emptyResponse: String,
)

/**
 * Writes one conversation as a Markdown file that other Markdown readers show the way the chat does
 * (roadmap P7.1, D83, D150, D151): the messages shown (the selected version of every turn, D148),
 * attachment names, and the reasoning only when [includeReasoning] is set.
 *
 * Replies are Markdown already and are copied as they are, with an unclosed code fence closed so it
 * cannot swallow the rest of the file. Messages, reasoning and notices that the chat shows as plain
 * text are escaped and keep their line breaks.
 */
internal object ConversationMarkdownExport {
    private const val HARD_BREAK = "  "
    private const val MAXIMUM_FILE_NAME_CHARACTERS = 80
    private val INLINE_SPECIAL = setOf('\\', '`', '*', '_', '[', ']', '<', '>', '&', '|', '~')
    private val LINE_START_SPECIAL = setOf('#', '-', '+', '=', '>')
    private val FILE_NAME_FORBIDDEN = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
    private val ORDERED_MARKER = Regex("""^(\d{1,9})([.)])""")

    fun format(
        conversation: StoredConversation,
        labels: MarkdownExportLabels,
        includeReasoning: Boolean,
        formatTime: (Long) -> String,
        exportedAtMillis: Long,
        appVersion: String,
    ): String = buildString {
        append("# ").append(heading(conversation.title)).append("\n\n")
        conversation.target?.let { target -> metadata(labels.model, inline(target.displayName)) }
        metadata(labels.created, formatTime(conversation.createdAtMillis))
        metadata(labels.updated, formatTime(conversation.updatedAtMillis))
        metadata(labels.exported, "${formatTime(exportedAtMillis)} (3-Stone AI ${inline(appVersion)})")
        conversation.messages.forEach { message ->
            append('\n')
            when (message.role) {
                ChatMessageRole.USER -> user(message, labels)
                ChatMessageRole.ASSISTANT -> assistant(message, labels, includeReasoning)
                ChatMessageRole.NOTICE -> quote(plain(message.text))
            }
        }
    }

    /** Whether any shown reply has reasoning, so the export asks whether to include it (D151). */
    fun hasReasoning(conversation: StoredConversation): Boolean = conversation.messages.any { message ->
        message.role == ChatMessageRole.ASSISTANT && !message.reasoning?.text.isNullOrEmpty()
    }

    /** The suggested document name: the title without characters file systems refuse, then ".md". */
    fun fileName(title: String, fallback: String): String {
        val cleaned = buildString {
            title.forEach { character ->
                append(if (character in FILE_NAME_FORBIDDEN || character.isISOControl()) ' ' else character)
            }
        }.replace(Regex("""\s+"""), " ").trim().trim('.', ' ')
        val shortened = if (cleaned.codePointCount(0, cleaned.length) > MAXIMUM_FILE_NAME_CHARACTERS) {
            cleaned.substring(0, cleaned.offsetByCodePoints(0, MAXIMUM_FILE_NAME_CHARACTERS)).trimEnd('.', ' ')
        } else {
            cleaned
        }
        return shortened.ifEmpty { fallback } + ".md"
    }

    private fun StringBuilder.metadata(label: String, value: String) {
        append("- **").append(inline(label)).append(":** ").append(value).append('\n')
    }

    private fun StringBuilder.user(message: ChatMessage, labels: MarkdownExportLabels) {
        append("## ").append(heading(labels.user)).append("\n\n")
        if (message.attachments.isNotEmpty()) {
            append("**").append(inline(labels.attachments)).append(":**\n\n")
            message.attachments.forEach { attachment ->
                append("- ").append(inline(attachment.name, lineStart = true)).append('\n')
            }
            append('\n')
        }
        if (message.text.isNotEmpty()) append(plain(message.text)).append('\n')
    }

    private fun StringBuilder.assistant(message: ChatMessage, labels: MarkdownExportLabels, includeReasoning: Boolean) {
        val model = message.target?.let { target -> " (${target.displayName})" }.orEmpty()
        append("## ").append(heading(labels.assistant + model))
        append("\n\n")
        val reasoning = message.reasoning?.takeIf { includeReasoning && it.text.isNotEmpty() }
        if (reasoning != null) {
            val header = reasoning.durationMillis
                ?.let { duration -> labels.reasoningDuration(ChatReasoningCardPolicy.seconds(duration)) }
                ?: labels.reasoning
            val body = buildString {
                append("**").append(inline(header)).append("**\n\n").append(plain(reasoning.text))
                if (reasoning.truncated) append("\n\n*").append(inline(labels.reasoningTruncated)).append('*')
            }
            quote(body)
            append('\n')
        }
        if (message.text.isEmpty()) {
            val placeholder = when (message.status) {
                ChatMessageStatus.COMPLETE -> labels.emptyResponse
                ChatMessageStatus.FAILED -> labels.failed
                ChatMessageStatus.STOPPED, ChatMessageStatus.GENERATING -> labels.stopped
            }
            append('*').append(inline(placeholder)).append("*\n")
            return
        }
        append(closeOpenFence(message.text.replace("\r\n", "\n").trimEnd())).append('\n')
        val note = when {
            message.status == ChatMessageStatus.STOPPED || message.status == ChatMessageStatus.GENERATING -> labels.stopped
            message.status == ChatMessageStatus.FAILED -> labels.failed
            message.truncated -> labels.truncated
            else -> null
        }
        if (note != null) append("\n*").append(inline(note)).append("*\n")
    }

    /** Prefixes every line with "> ", blank lines included, so the quote stays one block. */
    private fun StringBuilder.quote(text: String) {
        text.split('\n').forEach { line -> append(if (line.isEmpty()) ">" else "> $line").append('\n') }
    }

    /**
     * Text without line structure, such as a model or file name, escaped to show as written; with
     * [lineStart] it may begin a list item, where "#" or "1." would start another block.
     */
    fun inline(text: String, lineStart: Boolean = false): String =
        escape(text.replace(Regex("""\s*[\r\n]+\s*"""), " ").trim(), lineStart)

    /** Heading text; a trailing "#" would be read as the heading's closing sequence. */
    private fun heading(text: String): String {
        val escaped = inline(text)
        return if (escaped.endsWith('#')) escaped.dropLast(1) + "\\#" else escaped
    }

    /**
     * Plain text the chat shows as it is: every line escaped, leading spaces kept as no-break spaces
     * (so they neither vanish nor start a code block), single line breaks kept as hard breaks.
     */
    fun plain(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd().split('\n')
        return lines.mapIndexed { index, line ->
            val trimmed = line.trimEnd()
            val indentation = trimmed.takeWhile { it == ' ' || it == '\t' }
            val content = trimmed.substring(indentation.length)
            val escaped = buildString {
                indentation.forEach { character -> append(if (character == '\t') "&nbsp;".repeat(4) else "&nbsp;") }
                append(escape(content, lineStart = true))
            }
            val next = lines.getOrNull(index + 1)
            if (escaped.isNotEmpty() && !next.isNullOrBlank()) escaped + HARD_BREAK else escaped
        }.joinToString("\n")
    }

    private fun escape(text: String, lineStart: Boolean): String {
        if (text.isEmpty()) return text
        val builder = StringBuilder(text.length + 8)
        var start = 0
        if (lineStart) {
            val ordered = ORDERED_MARKER.find(text)
            if (ordered != null) {
                // "1." or "1)" would start an ordered list.
                builder.append(ordered.groupValues[1]).append('\\').append(ordered.groupValues[2])
                start = ordered.value.length
            } else if (text[0] in LINE_START_SPECIAL) {
                builder.append('\\').append(text[0])
                start = 1
            }
        }
        for (index in start until text.length) {
            val character = text[index]
            if (character in INLINE_SPECIAL) builder.append('\\')
            builder.append(character)
        }
        return builder.toString()
    }

    /**
     * Closes a fenced code block a reply leaves open (for example one stopped while streaming), with
     * the opening fence's indentation so a fence inside a list item closes inside it too.
     */
    fun closeOpenFence(text: String): String {
        var open: Fence? = null
        text.split('\n').forEach { line ->
            val fence = fenceOf(line) ?: return@forEach
            val current = open
            if (current == null) {
                // A backtick fence's info string cannot contain a backtick.
                if (fence.character != '`' || '`' !in fence.rest) open = fence
            } else if (fence.character == current.character && fence.length >= current.length && fence.rest.isBlank()) {
                open = null
            }
        }
        val unclosed = open ?: return text
        return text + "\n" + " ".repeat(unclosed.indentation) + unclosed.character.toString().repeat(unclosed.length)
    }

    private fun fenceOf(line: String): Fence? {
        val indentation = line.takeWhile { it == ' ' }.length
        if (indentation > 3 || indentation >= line.length) return null
        val character = line[indentation]
        if (character != '`' && character != '~') return null
        val length = line.substring(indentation).takeWhile { it == character }.length
        if (length < 3) return null
        return Fence(character, length, indentation, line.substring(indentation + length))
    }

    private data class Fence(val character: Char, val length: Int, val indentation: Int, val rest: String)
}
