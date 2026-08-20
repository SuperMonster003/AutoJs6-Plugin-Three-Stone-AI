package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import org.autojs.plugin.ondeviceai.api.AiFinishReason
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits

internal data class BufferedChunk(val sequence: Long, val text: String)

internal data class OutputSnapshot(
    val text: String,
    val utf8Bytes: Long,
    val chunkCount: Long,
    val sha256: String,
    val finishReason: Int,
)

internal class StreamingOutputBuffer(
    private val streaming: Boolean,
    private val maximumOutputBytes: Int,
    private val maximumCredits: Int = OnDeviceAiLimits.MAX_OUTSTANDING_CHUNK_CREDITS,
    private val maximumChunkBytes: Int = OnDeviceAiLimits.MAX_CHUNK_TEXT_BYTES,
) {
    private val output = StringBuilder()
    private val pendingChunks = ArrayDeque<String>()
    private var outputBytes = 0
    private var credits = 0
    private var nextSequence = 0L
    private var emittedChunks = 0L
    private var backendDone = false
    private var finishReason = AiFinishReason.STOP

    init {
        require(maximumOutputBytes > 0)
        require(maximumCredits > 0)
        require(maximumChunkBytes > 0)
    }

    @Synchronized
    fun append(delta: String): Boolean {
        check(!backendDone) { "Output arrived after backend completion" }
        if (delta.isEmpty()) return false
        Utf8Text.requireWellFormed(delta)
        val remaining = maximumOutputBytes - outputBytes
        val accepted = Utf8Text.prefix(delta, remaining)
        if (accepted.isNotEmpty()) {
            output.append(accepted)
            outputBytes += Utf8Text.byteCount(accepted)
            if (streaming) pendingChunks.addAll(Utf8Text.split(accepted, maximumChunkBytes))
        }
        val hitLimit = accepted.length != delta.length || outputBytes == maximumOutputBytes
        if (hitLimit) finishReason = AiFinishReason.LENGTH
        return hitLimit
    }

    @Synchronized
    fun grantCredits(count: Int) {
        require(count > 0) { "Chunk credit grant must be positive" }
        require(count <= maximumCredits - credits) { "Chunk credit window overflow" }
        credits += count
    }

    @Synchronized
    fun takeCreditedChunk(): BufferedChunk? {
        if (!streaming || credits == 0 || pendingChunks.isEmpty()) return null
        check(nextSequence < Long.MAX_VALUE) { "Chunk sequence is exhausted" }
        credits -= 1
        emittedChunks += 1
        return BufferedChunk(nextSequence++, pendingChunks.removeFirst())
    }

    @Synchronized
    fun markBackendDone() {
        backendDone = true
    }

    @Synchronized
    fun isReadyForCompletion(): Boolean = backendDone && pendingChunks.isEmpty()

    @Synchronized
    fun hasDrainWork(): Boolean =
        (streaming && credits > 0 && pendingChunks.isNotEmpty()) || isReadyForCompletion()

    @Synchronized
    fun snapshot(): OutputSnapshot {
        check(isReadyForCompletion())
        val text = output.toString()
        return OutputSnapshot(
            text = text,
            utf8Bytes = outputBytes.toLong(),
            chunkCount = emittedChunks,
            sha256 = Utf8Text.sha256(text),
            finishReason = finishReason,
        )
    }
}
