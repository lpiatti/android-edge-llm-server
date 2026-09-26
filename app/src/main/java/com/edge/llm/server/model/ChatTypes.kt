package com.edge.llm.server.model

/**
 * Engine-agnostic chat types shared by the HTTP layer and the inference providers.
 * Pure Kotlin (no Android, no LiteRT-LM imports) so they can be unit-tested on the JVM.
 */

enum class TurnRole { SYSTEM, USER, ASSISTANT, TOOL }

/** A tool call produced by the model (or echoed back by the client in the history). */
data class ToolCallData(
    val id: String,
    val name: String,
    /** JSON object serialized as a string, exactly like OpenAI `function.arguments`. */
    val argumentsJson: String
)

/** One message of the conversation, already normalized from the OpenAI/Ollama wire format. */
data class ChatTurn(
    val role: TurnRole,
    val text: String = "",
    val toolCalls: List<ToolCallData> = emptyList(),
    /** For TOOL turns: name of the function whose result this is. */
    val toolName: String? = null
)

/** A function the client declares; the server never executes it. */
data class ToolDefinition(
    val name: String,
    val description: String = "",
    /** JSON Schema of the parameters, serialized; null when the function takes none. */
    val parametersJson: String? = null
)

data class GenerationRequest(
    val messages: List<ChatTurn>,
    val tools: List<ToolDefinition> = emptyList(),
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val maxTokens: Int? = null,
    val stop: List<String> = emptyList(),
    /** Non-null = constrain output to JSON. "{}" means any JSON object. */
    val jsonSchema: String? = null,
    val seed: Int? = null
)

sealed class GenerationEvent {
    data class Text(val text: String) : GenerationEvent()
    data class ToolCalls(val calls: List<ToolCallData>) : GenerationEvent()
    data class Done(
        val finishReason: String,
        val promptTokens: Int,
        val completionTokens: Int,
        /** Prompt tokens served from a reused KV cache (0 when the context was rebuilt). */
        val cachedPromptTokens: Int = 0
    ) : GenerationEvent()
}

object FinishReason {
    const val STOP = "stop"
    const val LENGTH = "length"
    const val TOOL_CALLS = "tool_calls"
}

/** Aggregated outcome of a non-streaming generation. */
data class GenerationResult(
    val text: String,
    val toolCalls: List<ToolCallData>,
    val finishReason: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val cachedPromptTokens: Int
)

/** Error raised for requests the server cannot honor; mapped to HTTP 400. */
class InvalidRequestException(message: String) : Exception(message)

/** Rough token estimate used only when the engine cannot report real counts. */
fun estimateTokens(text: String): Int = if (text.isEmpty()) 0 else maxOf(1, text.length / 4)
