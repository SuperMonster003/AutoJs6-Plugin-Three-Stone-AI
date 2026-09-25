package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.supermonster003.autojs6.plugin.threestoneai.profile.OnlineAiProtocol
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import java.util.TreeMap
import java.util.UUID

/** One instance per HTTP response. Adapters themselves remain stateless and safe to share. */
internal class OnlineAiToolCollector(
    private val protocol: OnlineAiProtocol,
    private val definitions: List<GenerationToolDefinition>,
) {
    private val openAiCalls = TreeMap<Int, JsonObject>()
    private val blocks = TreeMap<Int, JsonObject>()
    private val partialInputs = mutableMapOf<Int, StringBuilder>()
    private val stoppedBlocks = mutableSetOf<Int>()
    private val geminiParts = JsonArray()
    private var retainedBytes = 0L
    private var stopReason: String? = null
    private var jsonResponse = false

    fun event(root: JsonObject) = collect(root, false)
    fun json(root: JsonObject) = collect(root, true)

    private fun collect(root: JsonObject, whole: Boolean) {
        jsonResponse = whole
        when (protocol) {
            OnlineAiProtocol.OPENAI_COMPATIBLE -> {
                val choice = OnlineAiResponseSupport.arrayOrNull(root, "choices")?.firstOrNull()?.asJsonObject ?: return
                OnlineAiResponseSupport.stringOrNull(choice, "finish_reason")?.let { stopReason = it }
                val message = OnlineAiResponseSupport.objectOrNull(choice, if (whole) "message" else "delta") ?: return
                val calls = OnlineAiResponseSupport.arrayOrNull(message, "tool_calls") ?: return
                requireEnabled()
                account(calls.toString())
                calls.forEachIndexed { position, element ->
                    val call = element.asJsonObject
                    val index = if (whole) position else index(call, "index", AiProviderLimits.MAX_OUTSTANDING_TOOL_CALLS)
                    val saved = openAiCalls.getOrPut(index) { JsonObject().apply { add("function", JsonObject()) } }
                    fixed(saved, "id", OnlineAiResponseSupport.stringOrNull(call, "id"))
                    val type = OnlineAiResponseSupport.stringOrNull(call, "type")
                    if (type != null && type != "function") invalid()
                    val function = OnlineAiResponseSupport.objectOrNull(call, "function") ?: invalid()
                    val savedFunction = saved.getAsJsonObject("function")
                    fixed(savedFunction, "name", OnlineAiResponseSupport.stringOrNull(function, "name"))
                    OnlineAiResponseSupport.stringOrNull(function, "arguments")?.let { delta ->
                        savedFunction.addProperty("arguments", (savedFunction.get("arguments")?.asString ?: "") + delta)
                    }
                }
            }
            OnlineAiProtocol.ANTHROPIC_MESSAGES -> {
                if (whole) {
                    stopReason = OnlineAiResponseSupport.stringOrNull(root, "stop_reason")
                    OnlineAiResponseSupport.arrayOrNull(root, "content")?.forEachIndexed { i, element ->
                        saveBlock(i, element.asJsonObject)
                    }
                } else when (OnlineAiResponseSupport.stringOrNull(root, "type")) {
                    "content_block_start" -> {
                        val block = OnlineAiResponseSupport.objectOrNull(root, "content_block") ?: invalid()
                        if (block.get("type")?.asString == "tool_use") requireEnabled()
                        if (definitions.isNotEmpty()) saveBlock(index(root), block)
                    }
                    "content_block_delta" -> if (definitions.isNotEmpty()) {
                        val i = index(root)
                        val block = blocks[i] ?: invalid()
                        if (i in stoppedBlocks) invalid()
                        val delta = OnlineAiResponseSupport.objectOrNull(root, "delta") ?: invalid()
                        account(delta.toString())
                        when (OnlineAiResponseSupport.stringOrNull(delta, "type")) {
                            "input_json_delta" -> {
                                if (block.get("type")?.asString != "tool_use") invalid()
                                partialInputs.getOrPut(i) { StringBuilder() }.append(
                                    OnlineAiResponseSupport.stringOrNull(delta, "partial_json") ?: invalid(),
                                )
                            }
                            "text_delta" -> append(block, "text", delta, "text", "text")
                            "thinking_delta" -> append(block, "thinking", delta, "thinking", "thinking")
                            "signature_delta" -> append(block, "signature", delta, "signature", "thinking")
                            else -> invalid()
                        }
                    }
                    "content_block_stop" -> if (definitions.isNotEmpty()) {
                        val i = index(root)
                        if (i !in blocks || !stoppedBlocks.add(i)) invalid()
                    }
                    "message_delta" -> stopReason = OnlineAiResponseSupport.objectOrNull(root, "delta")
                        ?.let { OnlineAiResponseSupport.stringOrNull(it, "stop_reason") }
                    else -> Unit
                }
            }
            OnlineAiProtocol.GEMINI_GENERATE_CONTENT -> {
                val candidate = OnlineAiResponseSupport.arrayOrNull(root, "candidates")?.firstOrNull()?.asJsonObject ?: return
                OnlineAiResponseSupport.stringOrNull(candidate, "finishReason")?.let { stopReason = it }
                val content = OnlineAiResponseSupport.objectOrNull(candidate, "content") ?: return
                OnlineAiResponseSupport.arrayOrNull(content, "parts")?.forEach { part ->
                    if (part.asJsonObject.has("functionCall")) requireEnabled()
                    if (definitions.isNotEmpty()) {
                        account(part.toString())
                        if (geminiParts.size() >= AiProviderLimits.MAX_CONTENT_PARTS) invalid()
                        // Keep signed parts intact and in their original order, including opaque thoughts.
                        geminiParts.add(part.deepCopy())
                    }
                }
            }
        }
    }

    fun finish(text: String): Pair<List<GenerationToolCall>, NativeToolMessage?> {
        val calls = mutableListOf<GenerationToolCall>()
        val native = when (protocol) {
            OnlineAiProtocol.OPENAI_COMPATIBLE -> JsonObject().apply {
                addProperty("role", "assistant")
                addProperty("content", text)
                add("tool_calls", JsonArray().apply {
                    openAiCalls.values.forEach { saved ->
                        val function = saved.getAsJsonObject("function")
                        calls += GenerationToolCall(required(saved, "id"), required(function, "name"), required(function, "arguments"))
                        saved.addProperty("type", "function")
                        add(saved)
                    }
                })
            }
            OnlineAiProtocol.ANTHROPIC_MESSAGES -> JsonArray().apply {
                blocks.forEach { (i, block) ->
                    if (!jsonResponse && i !in stoppedBlocks) invalid()
                    if (block.get("type")?.asString == "tool_use") {
                        partialInputs[i]?.let { partial ->
                            GenerationToolCall(required(block, "id"), required(block, "name"), partial.toString()).toProviderCall()
                            if (OnlineAiResponseSupport.objectOrNull(block, "input")?.size() != 0) invalid()
                            block.add("input", OnlineAiResponseSupport.parseObject(partial.toString()))
                        }
                        val input = OnlineAiResponseSupport.objectOrNull(block, "input") ?: invalid()
                        calls += GenerationToolCall(required(block, "id"), required(block, "name"), input.toString())
                    }
                    add(block)
                }
            }
            OnlineAiProtocol.GEMINI_GENERATE_CONTENT -> geminiParts.also { parts ->
                parts.forEach { part ->
                    OnlineAiResponseSupport.objectOrNull(part.asJsonObject, "functionCall")?.let { call ->
                        val wireId = OnlineAiResponseSupport.stringOrNull(call, "id")
                        calls += GenerationToolCall(
                            wireId ?: "gemini-${UUID.randomUUID()}", required(call, "name"),
                            (OnlineAiResponseSupport.objectOrNull(call, "args") ?: JsonObject()).toString(), wireId,
                        )
                    }
                }
            }
        }
        if (calls.isEmpty()) return emptyList<GenerationToolCall>() to null
        if (calls.size > AiProviderLimits.MAX_OUTSTANDING_TOOL_CALLS || calls.map { it.callId }.distinct().size != calls.size) invalid()
        val expectedStop = when (protocol) {
            OnlineAiProtocol.OPENAI_COMPATIBLE -> "tool_calls"
            OnlineAiProtocol.ANTHROPIC_MESSAGES -> "tool_use"
            OnlineAiProtocol.GEMINI_GENERATE_CONTENT -> "STOP"
        }
        if (stopReason != expectedStop) invalid() // Never execute a truncated call.
        val names = definitions.map { it.name }.toSet()
        calls.forEach { call ->
            if (call.name !in names) invalid()
            try { call.toProviderCall() } catch (_: IllegalArgumentException) { invalid() }
        }
        return calls.toList() to NativeToolMessage(protocol, native.toString())
    }

    private fun saveBlock(index: Int, block: JsonObject) {
        val type = required(block, "type")
        if (type == "tool_use") requireEnabled()
        if (definitions.isEmpty()) return
        if (type !in setOf("text", "tool_use", "thinking", "redacted_thinking") || index in blocks || blocks.size >= 512) invalid()
        account(block.toString())
        blocks[index] = block.deepCopy()
    }

    private fun append(block: JsonObject, key: String, delta: JsonObject, deltaKey: String, type: String) {
        if (block.get("type")?.asString != type) invalid()
        block.addProperty(key, (OnlineAiResponseSupport.stringOrNull(block, key) ?: "") + required(delta, deltaKey))
    }

    private fun account(text: String) {
        retainedBytes += text.toByteArray(Charsets.UTF_8).size
        if (retainedBytes > OnlineAiTransportLimits.MAXIMUM_CONTEXT_BYTES) {
            throw OnlineAiFailureException(OnlineAiFailureReason.RESPONSE_TOO_LARGE)
        }
    }

    private fun index(root: JsonObject, key: String = "index", maximum: Int = 512): Int {
        val value = OnlineAiResponseSupport.countOrNull(root, key) ?: invalid()
        if (value !in 0 until maximum.toLong()) invalid()
        return value.toInt()
    }

    private fun fixed(target: JsonObject, name: String, value: String?) {
        if (value == null) return
        val previous = OnlineAiResponseSupport.stringOrNull(target, name)
        if (previous != null && previous != value) invalid()
        target.addProperty(name, value)
    }

    private fun required(root: JsonObject, key: String) = OnlineAiResponseSupport.stringOrNull(root, key) ?: invalid()
    private fun requireEnabled() { if (definitions.isEmpty()) invalid() }
    private fun invalid(): Nothing = OnlineAiResponseSupport.invalidResponse()
}
