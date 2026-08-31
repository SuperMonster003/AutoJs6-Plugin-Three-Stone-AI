package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.AiTargetLocality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reproducible P4-4 gate: add embedding only if bounded FTS misses its intended workload. */
class ConversationRecallEvaluationTest {
    @Test
    fun `multilingual keyword workload exceeds the recall at four gate`() {
        val chunks = evaluationChunks()
        val probes = listOf(
            Probe("budget", "16K context input budget"),
            Probe("budget", "上下文预算是多少"),
            Probe("orchid", "ORCHID-731 confirmed constraint"),
            Probe("endpoint", "v1 chat completions endpoint"),
            Probe("retry", "HTTP 524 retry once"),
            Probe("device", "G8441 Android 9 API 28"),
            Probe("cache", "cached_tokens prompt cache hit"),
            Probe("binder", "Binder sessionId generateNext"),
            Probe("fts", "FTS three turn chunks"),
            Probe("japanese", "日本語で回答"),
            Probe("russian", "лимит токенов"),
            Probe("identifier", "contextTokenBudget property"),
            Probe("summary", "L1 L2 summary checkpoint"),
            Probe("gemma", "gemma litertlm local model"),
            Probe("watermark", "65 80 90 watermark"),
            Probe("database", "conversation-recall.db FTS4"),
        )

        val result = evaluate(chunks, probes)

        println(
            "P4 keyword evaluation: probes=${probes.size}, " +
                "top1=${result.topOneAccuracy}, recallAt4=${result.recallAtFour}",
        )
        assertEquals(16, probes.size)
        assertTrue("Recall@4 was ${result.recallAtFour}", result.recallAtFour >= 0.90)
        assertTrue("Top-1 accuracy was ${result.topOneAccuracy}", result.topOneAccuracy >= 0.90)
    }

    @Test
    fun `semantic only probes remain visible as the boundary of lexical recall`() {
        val chunks = evaluationChunks()
        val keywordProbes = listOf(
            Probe("budget", "16K input budget"),
            Probe("retry", "HTTP 524 retry"),
            Probe("device", "G8441 Android 9"),
            Probe("japanese", "日本語 回答"),
        )
        val semanticOnlyProbes = listOf(
            Probe("budget", "How much room may the prompt occupy?"),
            Probe("retry", "What happens after a gateway timeout?"),
            Probe("device", "Which handset hosted the legacy OS check?"),
            Probe("japanese", "Use the language requested for replies in Tokyo."),
        )

        val keyword = evaluate(chunks, keywordProbes)
        val semanticOnly = evaluate(chunks, semanticOnlyProbes)

        println(
            "P4 boundary evaluation: keywordRecallAt4=${keyword.recallAtFour}, " +
                "semanticOnlyRecallAt4=${semanticOnly.recallAtFour}",
        )
        assertEquals(1.0, keyword.recallAtFour, 0.0)
        assertTrue(
            "Semantic-only probes unexpectedly matched at ${semanticOnly.recallAtFour}",
            semanticOnly.recallAtFour < keyword.recallAtFour,
        )
    }

    private fun evaluate(
        chunks: List<ConversationRecallChunk>,
        probes: List<Probe>,
    ): EvaluationResult {
        var topOneHits = 0
        var topFourHits = 0
        probes.forEach { probe ->
            val ranked = ConversationRecallPolicy.rank(
                chunks = chunks,
                terms = ConversationRecallPolicy.queryTerms(probe.query),
                maximumResults = ConversationRecallPolicy.MAXIMUM_RECALLED_CHUNKS,
            )
            if (ranked.firstOrNull()?.chunk?.turns?.first()?.userText
                    ?.startsWith("[${probe.expectedLabel}]") == true
            ) {
                topOneHits += 1
            }
            if (ranked.any { score ->
                    score.chunk.turns.first().userText.startsWith("[${probe.expectedLabel}]")
                }
            ) {
                topFourHits += 1
            }
        }
        return EvaluationResult(
            topOneAccuracy = topOneHits.toDouble() / probes.size.toDouble(),
            recallAtFour = topFourHits.toDouble() / probes.size.toDouble(),
        )
    }

    private fun evaluationChunks(): List<ConversationRecallChunk> {
        val topics = listOf(
            Topic(
                "budget",
                "上下文 input budget 固定为 16K tokens",
                "The context input budget is 16384 tokens.",
            ),
            Topic(
                "orchid",
                "The confirmed constraint code is ORCHID-731.",
                "Keep the ORCHID-731 identifier unchanged.",
            ),
            Topic(
                "endpoint",
                "The OpenAI endpoint is /v1/chat/completions.",
                "Use the chat completions endpoint for streaming.",
            ),
            Topic(
                "retry",
                "HTTP 524 may retry once and only once.",
                "A second 524 is returned without another retry.",
            ),
            Topic(
                "device",
                "Sony G8441 runs Android 9 at API 28.",
                "The G8441 physical device is arm64-v8a.",
            ),
            Topic(
                "cache",
                "Provider usage reports cached_tokens for prompt cache reads.",
                "Cache hit rate divides cached input by eligible input.",
            ),
            Topic(
                "binder",
                "Binder generateNext preserves the public sessionId.",
                "Persistent Binder sessions rebuild transparently.",
            ),
            Topic(
                "fts",
                "SQLite FTS groups history into three turn chunks.",
                "The final FTS chunk may contain two turns.",
            ),
            Topic(
                "japanese",
                "回答は日本語で簡潔に書く",
                "日本語の回答では専門用語を保持する",
            ),
            Topic(
                "russian",
                "Лимит токенов применяется к входному контексту.",
                "Точный лимит токенов равен 16384.",
            ),
            Topic(
                "identifier",
                "contextTokenBudget is the settings property name.",
                "Do not rename the contextTokenBudget identifier.",
            ),
            Topic(
                "summary",
                "L1 working memory precedes the L2 summary checkpoint.",
                "Summary checkpoints cover evicted raw messages.",
            ),
            Topic(
                "gemma",
                "The local model artifact is gemma-litertlm.",
                "Gemma runs through the LiteRT LM backend.",
            ),
            Topic(
                "watermark",
                "Context watermarks are 65, 80, and 90 percent.",
                "Compaction falls back to the 45 percent target.",
            ),
            Topic(
                "database",
                "The secondary database is conversation-recall.db.",
                "Android SQLite creates an FTS4 virtual table.",
            ),
        )
        val messages = buildList {
            var messageId = 1L
            topics.forEach { topic ->
                repeat(ConversationRecallPolicy.TARGET_TURNS_PER_CHUNK) { repetition ->
                    add(
                        ChatMessage(
                            id = messageId++,
                            role = ChatMessageRole.USER,
                            text = "[${topic.label}] ${topic.question} sample-$repetition",
                        ),
                    )
                    add(
                        ChatMessage(
                            id = messageId++,
                            role = ChatMessageRole.ASSISTANT,
                            text = "${topic.answer} sample-$repetition",
                            target = responseTarget,
                        ),
                    )
                }
            }
        }
        return ConversationRecallPolicy.chunks(
            StoredConversation(
                id = "evaluation",
                title = "Recall evaluation",
                createdAtMillis = 1L,
                updatedAtMillis = 2L,
                target = responseTarget,
                messages = messages,
            ),
        )
    }

    private data class Topic(
        val label: String,
        val question: String,
        val answer: String,
    )

    private data class Probe(
        val expectedLabel: String,
        val query: String,
    )

    private data class EvaluationResult(
        val topOneAccuracy: Double,
        val recallAtFour: Double,
    )

    private companion object {
        val responseTarget = ConversationTargetSnapshot(
            targetId = "local:evaluation",
            providerId = "autojs6.three-stone-ai",
            modelId = "evaluation",
            displayName = "Evaluation",
            locality = AiTargetLocality.LOCAL,
        )
    }
}
