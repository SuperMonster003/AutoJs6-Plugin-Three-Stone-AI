package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.os.SystemClock
import io.github.supermonster003.autojs6.plugin.threestoneai.ThreeStoneAiPlugin
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiAttachmentInput
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackend
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSession
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiBackendSessionRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTarget
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCapabilities
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCatalog
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetCredentialMode
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLimits
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachment
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachmentKind
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationListener
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRequest
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationStatistics
import java.util.Collections

/**
 * A ready local-looking target whose replies are scripted, so chat UI tests need no model,
 * network or credentials. Replies stream as the given chunks; [hold] keeps a turn open until
 * [release] or cancellation, [pauseBeforeReply] stops it between the reasoning and the reply until
 * [continueReply], [failNext] fails the next turn after its chunks.
 */
internal class ScriptedAiBackend : AiBackend {
    override val backendId: String = "speech-test"

    @Volatile var available: Boolean = true
    @Volatile var reply: List<String> = listOf("Done.")
    /** Reasoning chunks streamed before [reply] through onReasoningDelta (P3). */
    @Volatile var reasoning: List<String> = emptyList()
    /** Whether each turn asked for visible reasoning (GenerationRequest.includeReasoning). */
    val reasoningRequested: MutableList<Boolean> = Collections.synchronizedList(ArrayList())
    @Volatile var chunkDelayMillis: Long = 20L
    @Volatile var hold: Boolean = false
    /**
     * Keeps each turn after its [reasoning] chunks, before the [reply], until [continueReply] or
     * [release]: a test can then check the "thinking" state without racing a short timed window.
     */
    @Volatile var pauseBeforeReply: Boolean = false
    @Volatile var failNext: Boolean = false
    val prompts: MutableList<String> = Collections.synchronizedList(ArrayList())
    /** Text of the history each turn replays, one entry per message (P6.3). */
    val histories: MutableList<List<String>> = Collections.synchronizedList(ArrayList())
    /**
     * Chat attachments the target accepts (P4.4); null takes none, as local models. Set it before
     * the chat opens: the catalog generation follows it, so an open screen picks up a change too.
     */
    @Volatile var attachments: AiAttachmentInput? = null
    /** Attachments of each turn: those of the prompt and those replayed with the history. */
    val attachmentTurns: MutableList<AttachmentTurn> = Collections.synchronizedList(ArrayList())
    @Volatile var completions = 0
    @Volatile var completedAt = 0L

    @Volatile private var released = false
    @Volatile private var replyContinued = false

    /** Lets a held turn run to its end, past [pauseBeforeReply] too. */
    fun release() {
        replyContinued = true
        released = true
    }

    /** Lets a turn stopped by [pauseBeforeReply] stream its reply. */
    fun continueReply() {
        replyContinued = true
    }

    val target: AiTarget
        get() = AiTarget(
            targetId = TARGET_ID,
            backendId = backendId,
            providerId = ThreeStoneAiPlugin.PROVIDER_ID,
            profileId = null,
            modelId = MODEL_ID,
            displayName = "Speech test model",
            locality = AiTargetLocality.LOCAL,
            credentialMode = AiTargetCredentialMode.NONE,
            declaredHttpsOrigins = emptyList(),
            configured = available,
            available = available,
            capabilities = AiTargetCapabilities(
                streaming = true,
                persistentSession = false,
                structuredJson = false,
                usage = true,
                reasoning = false,
                tools = false,
                attachments = attachments,
            ),
            limits = AiTargetLimits(maximumContextBytes = null, maximumOutputBytes = null),
            executionProfiles = emptyList(),
        )

    override fun ownsTarget(targetId: String): Boolean = targetId == TARGET_ID

    override fun catalog(): AiTargetCatalog = AiTargetCatalog(
        generation = "speech-test-$available-${attachments?.hashCode() ?: 0}",
        defaultTargetId = TARGET_ID,
        targets = listOf(target),
    )

    override fun createSession(request: AiBackendSessionRequest): AiBackendSession {
        require(request.targetId == TARGET_ID)
        return Session(target)
    }

    private inner class Session(override val target: AiTarget) : AiBackendSession {
        @Volatile private var cancelled = false

        /** Returns at once like the real backends; the reply streams on its own thread. */
        override fun stream(request: GenerationRequest, listener: GenerationListener) {
            prompts += request.prompt.textParts.joinToString("")
            histories += request.history.map { message -> message.textParts.joinToString("") }
            attachmentTurns += AttachmentTurn(
                prompt = request.prompt.attachments.map(::received),
                history = request.history.flatMap { message -> message.attachments }.map(::received),
            )
            reasoningRequested += request.includeReasoning
            released = false
            replyContinued = false
            Thread({ run(listener) }, "speech-test-reply").apply { isDaemon = true }.start()
        }

        private fun run(listener: GenerationListener) {
            val started = SystemClock.elapsedRealtime()
            for (chunk in reasoning) {
                if (cancelled) return
                listener.onReasoningDelta(chunk)
                SystemClock.sleep(chunkDelayMillis)
            }
            while (pauseBeforeReply && !replyContinued && !cancelled) SystemClock.sleep(POLL_MILLIS)
            for (chunk in reply) {
                if (cancelled) return
                listener.onTextDelta(chunk)
                SystemClock.sleep(chunkDelayMillis)
            }
            while (hold && !released && !cancelled) SystemClock.sleep(POLL_MILLIS)
            if (cancelled) return
            if (failNext) {
                failNext = false
                completions++
                listener.onFailed(IllegalStateException("Scripted failure"), null)
                return
            }
            completedAt = SystemClock.elapsedRealtime()
            completions++
            listener.onCompleted(
                GenerationStatistics(
                    inputTokens = 1L,
                    outputTokens = reply.size.toLong(),
                    durationMillis = SystemClock.elapsedRealtime() - started,
                ),
            )
        }

        override fun cancel() {
            cancelled = true
        }

        override fun close() {
            cancelled = true
        }
    }

    /** What the backend got for one attachment; [bytes] is null when reading it failed. */
    data class ReceivedAttachment(
        val kind: GenerationAttachmentKind,
        val name: String,
        val mimeType: String,
        val byteCount: Long,
        val width: Int,
        val height: Int,
        val pageCount: Int,
        val bytes: ByteArray?,
    )

    data class AttachmentTurn(val prompt: List<ReceivedAttachment>, val history: List<ReceivedAttachment>)

    private fun received(attachment: GenerationAttachment) = ReceivedAttachment(
        kind = attachment.kind,
        name = attachment.name,
        mimeType = attachment.mimeType,
        byteCount = attachment.byteCount,
        width = attachment.width,
        height = attachment.height,
        pageCount = attachment.pageCount,
        bytes = runCatching { attachment.bytes() }.getOrNull(),
    )

    companion object {
        const val MODEL_ID = "speech-test-model"
        const val TARGET_ID = "local:$MODEL_ID"
        private const val POLL_MILLIS = 20L
    }
}
