package com.edge.llm.server.model

import com.edge.llm.server.util.LogCategory
import com.edge.llm.server.util.ServerConsole
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LiteRtLmInferenceProvider: wraps the LiteRT-LM Kotlin SDK.
 *
 * - History is passed natively (`ConversationConfig.initialMessages`), so the model's own chat
 *   template is applied exactly once.
 * - Tools are declared natively with `automaticToolCalling = false`: the model's tool calls are
 *   returned to the HTTP client, the server never executes them.
 * - Prefix reuse (M5): the conversation of the last successful request is kept alive; when the
 *   next request extends that exact history, only the new input is prefilled.
 */
class LiteRtLmInferenceProvider(
    private val modelPath: String,
    private val useGpu: Boolean = false,
    private val cacheDir: String? = null,
    private val maxNumTokens: Int? = null
) : InferenceProvider {

    private data class Fingerprint(
        val system: String?,
        val toolsKey: String,
        val sampler: SamplerConfig?,
        val jsonMode: Boolean
    )

    private class Cached(val conversation: Conversation, val fingerprint: Fingerprint, val history: List<ChatTurn>)

    @Volatile private var engine: Engine? = null
    @Volatile private var cached: Cached? = null
    @Volatile private var abandoned: Conversation? = null
    @Volatile private var responseFormatUnsupported = false

    override suspend fun initialize(): Boolean {
        val file = File(modelPath)
        if (!file.exists()) throw Exception("Model file does not exist at absolute path: $modelPath")
        ServerConsole.log(
            LogCategory.ENGINE,
            "LiteRT-LM: Loading ${file.name} (${file.length() / 1024 / 1024} MB, GPU=$useGpu, " +
                "maxNumTokens=${maxNumTokens ?: "model default"}, cacheDir=${cacheDir ?: "default"})..."
        )
        if (!cacheDir.isNullOrEmpty()) File(cacheDir).mkdirs()
        val backend = if (useGpu) Backend.GPU() else Backend.CPU()
        val newEngine = Engine(EngineConfig(modelPath = modelPath, backend = backend, maxNumTokens = maxNumTokens, cacheDir = cacheDir))
        newEngine.initialize()
        engine = newEngine
        ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Engine initialized for ${file.name}.")
        return true
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = callbackFlow {
        val activeEngine = engine ?: throw IllegalStateException("LiteRT-LM engine not initialized or already unloaded")
        closeAbandoned()

        val plan = ConversationPlanner.plan(request.messages)
        val useJson = request.jsonSchema != null && !responseFormatUnsupported
        val system = systemWithJsonHint(plan.systemInstruction, request.jsonSchema != null)
        val fingerprint = Fingerprint(system, toolsKey(request.tools), samplerFor(request), useJson)

        val previous = cached
        cached = null
        val reuse = previous != null && previous.fingerprint == fingerprint &&
            previous.conversation.isAlive && ConversationPlanner.canReuse(previous.history, plan)
        val conversation: Conversation
        val cachedTokens: Int
        if (reuse) {
            conversation = previous!!.conversation
            cachedTokens = tokenCount(conversation)
            ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Reusing conversation KV cache ($cachedTokens tokens, ${plan.history.size} turns).")
        } else {
            previous?.let { closeQuietly(it.conversation) }
            conversation = activeEngine.createConversation(
                ConversationConfig(
                    systemInstruction = system?.let { Contents.of(it) },
                    initialMessages = toSdkHistory(plan.history),
                    tools = request.tools.map { toSdkTool(it) },
                    samplerConfig = fingerprint.sampler,
                    automaticToolCalling = false,
                    enableResponseFormat = useJson
                )
            )
            cachedTokens = 0
            ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: New conversation (${plan.history.size} history turns, ${request.tools.size} tools).")
        }

        val finished = AtomicBoolean(false)
        val text = StringBuilder()
        val calls = mutableListOf<ToolCallData>()
        var pieces = 0

        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                if (message.toolCalls.isNotEmpty()) {
                    val mapped = message.toolCalls.map {
                        ToolCallData(newCallId(), it.name, JsonValues.mapToJsonString(it.arguments))
                    }
                    calls.addAll(mapped)
                    trySend(GenerationEvent.ToolCalls(mapped))
                }
                val chunk = message.toString()
                if (chunk.isNotEmpty()) {
                    pieces++
                    text.append(chunk)
                    trySend(GenerationEvent.Text(chunk))
                }
            }

            override fun onDone() {
                if (!finished.compareAndSet(false, true)) {
                    closeQuietly(conversation)
                    return
                }
                val total = tokenCount(conversation)
                val prompt = if (total > pieces) total - pieces else estimateTokens(request.messages.joinToString("\n") { it.text })
                val finish = when {
                    calls.isNotEmpty() -> FinishReason.TOOL_CALLS
                    request.maxTokens != null && pieces >= request.maxTokens -> FinishReason.LENGTH
                    else -> FinishReason.STOP
                }
                cached = Cached(conversation, fingerprint, plan.historyAfter(ChatTurn(TurnRole.ASSISTANT, text.toString(), calls.toList())))
                trySend(GenerationEvent.Done(finish, prompt, pieces, cachedTokens))
                close()
            }

            override fun onError(throwable: Throwable) {
                val wasActive = finished.compareAndSet(false, true)
                closeQuietly(conversation)
                if (wasActive) {
                    if (useJson && pieces == 0) {
                        responseFormatUnsupported = true
                        ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: constrained JSON decoding failed (${throwable.message}); falling back to prompt-only JSON mode for next requests.")
                    }
                    close(throwable)
                }
            }
        }

        try {
            conversation.sendMessageAsync(
                toSdkInput(plan.input),
                callback,
                maxOutputToken = request.maxTokens,
                responseFormat = if (useJson) ResponseFormat.json(request.jsonSchema ?: "{}") else null
            )
        } catch (e: Exception) {
            finished.set(true)
            closeQuietly(conversation)
            throw e
        }

        awaitClose {
            // Cancelled by the consumer (client disconnect, stop sequence): abort native decoding.
            // The conversation is closed by the native callback, or before the next request.
            if (finished.compareAndSet(false, true)) {
                try {
                    conversation.cancelProcess()
                } catch (e: Exception) {
                    // Already finished natively
                }
                abandoned = conversation
                ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Generation cancelled by consumer.")
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun unload() {
        cached?.let { closeQuietly(it.conversation) }
        cached = null
        closeAbandoned()
        try {
            engine?.close()
            ServerConsole.log(LogCategory.ENGINE, "LiteRT-LM: Closed native engine reference.")
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.ENGINE, "Error closing LiteRT-LM engine: ${e.message}")
        } finally {
            engine = null
        }
    }

    // --- Mapping to the SDK -------------------------------------------------------------------

    private fun toSdkHistory(turns: List<ChatTurn>): List<Message> {
        val out = mutableListOf<Message>()
        var i = 0
        while (i < turns.size) {
            val turn = turns[i]
            when (turn.role) {
                TurnRole.USER -> out.add(Message.user(turn.text))
                TurnRole.ASSISTANT -> out.add(toSdkAssistant(turn))
                TurnRole.TOOL -> {
                    var j = i
                    while (j < turns.size && turns[j].role == TurnRole.TOOL) j++
                    out.add(toSdkToolMessage(turns.subList(i, j)))
                    i = j
                    continue
                }
                TurnRole.SYSTEM -> Unit // folded into systemInstruction by the planner
            }
            i++
        }
        return out
    }

    private fun toSdkAssistant(turn: ChatTurn): Message {
        if (turn.toolCalls.isEmpty()) return Message.model(turn.text)
        val calls = turn.toolCalls.map { ToolCall(it.name, JsonValues.parseObject(it.argumentsJson) ?: emptyMap()) }
        return if (turn.text.isBlank()) Message.model(toolCalls = calls)
        else Message.model(Contents.of(turn.text), calls)
    }

    private fun toSdkToolMessage(turns: List<ChatTurn>): Message =
        Message.tool(Contents.of(turns.map { t ->
            val value: Any? = JsonValues.parseOrNull(t.text)?.let { JsonValues.toAny(it) } ?: t.text
            Content.ToolResponse(t.toolName ?: "tool", value)
        }))

    private fun toSdkInput(input: List<ChatTurn>): Message =
        if (input.first().role == TurnRole.TOOL) toSdkToolMessage(input) else Message.user(input.last().text)

    private fun toSdkTool(def: ToolDefinition) = tool(object : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = toolDescriptionJson(def)
        override fun execute(paramsJsonString: String): String =
            "{\"error\":\"Tools are executed by the client, not by the server.\"}"
    })

    private fun toolDescriptionJson(def: ToolDefinition): String {
        val fields = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "name" to JsonPrimitive(def.name),
            "description" to JsonPrimitive(def.description)
        )
        def.parametersJson?.let { JsonValues.parseOrNull(it) }?.let { fields["parameters"] = it }
        return JsonObject(fields).toString()
    }

    private fun toolsKey(tools: List<ToolDefinition>): String = tools.joinToString("|") { toolDescriptionJson(it) }

    private fun samplerFor(r: GenerationRequest): SamplerConfig? {
        if (r.temperature == null && r.topP == null && r.topK == null && r.seed == null) return null
        return SamplerConfig(
            topK = (r.topK ?: 64).coerceAtLeast(1),
            topP = (r.topP ?: 0.95).coerceIn(0.0, 1.0),
            temperature = (r.temperature ?: 1.0).coerceAtLeast(0.0),
            seed = r.seed ?: 0
        )
    }

    private fun systemWithJsonHint(system: String?, json: Boolean): String? {
        if (!json) return system
        val hint = "Respond only with a single valid JSON value, without markdown fences or extra text."
        return if (system == null) hint else "$system\n\n$hint"
    }

    private fun tokenCount(conversation: Conversation): Int = try {
        conversation.getTokenCount()
    } catch (e: Exception) {
        0
    }

    private fun closeAbandoned() {
        abandoned?.let { closeQuietly(it) }
        abandoned = null
    }

    private fun closeQuietly(conversation: Conversation) {
        try {
            if (conversation.isAlive) conversation.close()
        } catch (e: Exception) {
            // Already closed
        }
    }

    private fun newCallId(): String = "call_" + UUID.randomUUID().toString().replace("-", "").take(24)
}
