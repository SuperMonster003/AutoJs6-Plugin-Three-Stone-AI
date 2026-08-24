package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import okio.Buffer
import okio.BufferedSource
import java.io.Closeable

internal data class OnlineAiSseEvent(
    val event: String?,
    val data: String,
    val isDone: Boolean,
)

/** Bounded UTF-8 SSE reader accepting LF, CRLF, lone CR, BOM, and a final unterminated event. */
internal class OnlineAiSseReader(
    private val source: BufferedSource,
    private val maximumEventBytes: Long = OnlineAiTransportLimits.MAXIMUM_SSE_EVENT_BYTES,
    private val maximumTotalBytes: Long = OnlineAiTransportLimits.MAXIMUM_SSE_TOTAL_BYTES,
) : Closeable {
    init {
        require(maximumEventBytes > 0L)
        require(maximumTotalBytes >= maximumEventBytes)
    }

    private var closed = false
    private var ended = false
    private var firstLine = true
    private var totalBytes = 0L
    private var eventBytes = 0L
    private var dataSeen = false
    private val data = StringBuilder()
    private var eventType: String? = null

    fun readEvent(): OnlineAiSseEvent? {
        check(!closed) { "Online AI SSE reader is closed" }
        if (ended) return null

        while (true) {
            val rawLine = readLine()
            if (rawLine == null) {
                ended = true
                return dispatchEvent()
            }
            val line = if (firstLine) {
                firstLine = false
                rawLine.removePrefix("\uFEFF")
            } else {
                rawLine
            }

            if (line.isEmpty()) {
                val event = dispatchEvent()
                resetEvent()
                if (event != null) {
                    if (event.isDone) ended = true
                    return event
                }
                continue
            }
            if (line.startsWith(':')) continue

            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
            when (field) {
                "data" -> {
                    dataSeen = true
                    data.append(value).append('\n')
                }
                "event" -> eventType = value
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        ended = true
        source.close()
    }

    private fun readLine(): String? {
        if (!source.request(1L)) return null
        val line = Buffer()
        while (true) {
            if (!source.request(1L)) return line.readUtf8()
            val byte = source.readByte()
            accountByte()
            when (byte.toInt() and 0xff) {
                LF -> return line.readUtf8()
                CR -> {
                    if (source.request(1L) && (source.buffer[0L].toInt() and 0xff) == LF) {
                        source.skip(1L)
                        accountByte()
                    }
                    return line.readUtf8()
                }
                else -> line.writeByte(byte.toInt())
            }
        }
    }

    private fun accountByte() {
        totalBytes += 1L
        eventBytes += 1L
        if (totalBytes > maximumTotalBytes || eventBytes > maximumEventBytes) {
            throw OnlineAiFailureException(
                OnlineAiFailureReason.RESPONSE_TOO_LARGE,
            )
        }
    }

    private fun dispatchEvent(): OnlineAiSseEvent? {
        if (!dataSeen) return null
        if (data.isNotEmpty()) data.setLength(data.length - 1)
        val payload = data.toString()
        return OnlineAiSseEvent(
            event = eventType?.takeIf(String::isNotEmpty),
            data = payload,
            isDone = payload.trim() == DONE_SENTINEL,
        )
    }

    private fun resetEvent() {
        dataSeen = false
        data.setLength(0)
        eventType = null
        eventBytes = 0L
    }

    private companion object {
        const val LF = 0x0a
        const val CR = 0x0d
        const val DONE_SENTINEL = "[DONE]"
    }
}
