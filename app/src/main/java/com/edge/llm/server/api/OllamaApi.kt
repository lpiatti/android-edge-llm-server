package com.edge.llm.server.api

import com.edge.llm.server.model.ChatTurn
import com.edge.llm.server.model.GenerationRequest
import com.edge.llm.server.model.InvalidRequestException
import com.edge.llm.server.model.JsonValues
import com.edge.llm.server.model.ToolCallData
import com.edge.llm.server.model.TurnRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// --- Ollama wire format ------------------------------------------------------------------------

@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    val top_p: Double? = null,
    val top_k: Int? = null,
    val num_predict: Int? = null,
    val num_ctx: Int? = null,
    val stop: List<String>? = null,
    val seed: Int? = null
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String? = "",
    val images: List<String>? = null,
    val tool_calls: List<OllamaToolCall>? = null,
    val tool_name: String? = null
)

@Serializable
data class OllamaToolCall(val function: OllamaFunctionCall)

@Serializable
data class OllamaFunctionCall(val name: String, val arguments: JsonElement? = null, val index: Int? = null)

@Serializable
data class OllamaChatRequest(
    val model: String? = null,
    val messages: List<OllamaMessage> = emptyList(),
    val tools: List<OaiTool>? = null,
    val options: OllamaOptions? = null,
    // Ollama streams unless told otherwise.
    val stream: Boolean? = true,
    val format: JsonElement? = null
)

@Serializable
data class OllamaGenerateRequest(
    val model: String? = null,
    val prompt: String? = "",
    val system: String? = null,
    val images: List<String>? = null,
    val options: OllamaOptions? = null,
    val stream: Boolean? = true,
    val format: JsonElement? = null
)

@Serializable
data class OllamaChatResponse(
    val model: String,
    val created_at: String,
    val message: OllamaMessage,
    val done: Boolean,
    val done_reason: String? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)

@Serializable
data class OllamaGenerateResponse(
    val model: String,
    val created_at: String,
    val response: String,
    val done: Boolean,
    val done_reason: String? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)

@Serializable
data class OllamaModelDetails(
    val parent_model: String = "",
    val format: String = "litertlm",
    val family: String,
    val families: List<String>,
    val parameter_size: String = "",
    val quantization_level: String = ""
)

@Serializable
data class OllamaModelEntry(
    val name: String,
    val model: String,
    val modified_at: String,
    val size: Long,
    val digest: String,
    val details: OllamaModelDetails,
    val expires_at: String? = null,
    val size_vram: Long? = null
)

@Serializable
data class OllamaModelList(val models: List<OllamaModelEntry>)

@Serializable
data class OllamaShowResponse(
    val modelfile: String,
    val parameters: String,
    val template: String,
    val details: OllamaModelDetails,
    val model_info: Map<String, String>,
    val capabilities: List<String>,
    val modified_at: String
)

@Serializable
data class OllamaVersion(val version: String)

@Serializable
data class OllamaError(val error: String)

// --- Mapping --------------------------------------------------------------------------------------

object OllamaMapping {

    fun toGenerationRequest(req: OllamaChatRequest): GenerationRequest {
        if (req.messages.isEmpty()) throw InvalidRequestException("'messages' must not be empty.")
        val pendingCalls = ArrayDeque<String>()
        var callCounter = 0
        val turns = req.messages.map { m ->
            if (!m.images.isNullOrEmpty()) throw InvalidRequestException("Image input is not supported.")
            val text = m.content ?: ""
            when (m.role.lowercase()) {
                "system" -> ChatTurn(TurnRole.SYSTEM, text)
                "user" -> ChatTurn(TurnRole.USER, text)
                "assistant" -> {
                    pendingCalls.clear()
                    val calls = m.tool_calls.orEmpty().map { c ->
                        pendingCalls.addLast(c.function.name)
                        ToolCallData("call_${callCounter++}", c.function.name, argumentsToJson(c.function.arguments))
                    }
                    ChatTurn(TurnRole.ASSISTANT, text, calls)
                }
                "tool" -> ChatTurn(TurnRole.TOOL, text, toolName = m.tool_name ?: pendingCalls.removeFirstOrNull())
                else -> throw InvalidRequestException("Unsupported message role '${m.role}'.")
            }
        }
        return withOptions(turns, req.options, req.format, OpenAiMapping.toolDefinitions(req.tools.orEmpty()))
    }

    fun toGenerationRequest(req: OllamaGenerateRequest): GenerationRequest {
        if (!req.images.isNullOrEmpty()) throw InvalidRequestException("Image input is not supported.")
        val turns = listOfNotNull(
            req.system?.takeIf { it.isNotBlank() }?.let { ChatTurn(TurnRole.SYSTEM, it) },
            ChatTurn(TurnRole.USER, req.prompt ?: "")
        )
        return withOptions(turns, req.options, req.format, emptyList())
    }

    private fun withOptions(
        turns: List<ChatTurn>,
        o: OllamaOptions?,
        format: JsonElement?,
        tools: List<com.edge.llm.server.model.ToolDefinition>
    ) = GenerationRequest(
        messages = turns,
        tools = tools,
        temperature = o?.temperature,
        topP = o?.top_p,
        topK = o?.top_k,
        // Ollama: -1 = infinite, -2 = fill context
        maxTokens = o?.num_predict?.takeIf { it > 0 },
        stop = o?.stop.orEmpty().filter { it.isNotEmpty() },
        jsonSchema = when {
            format == null || format is JsonNull -> null
            format is JsonPrimitive && format.contentOrNull == "json" -> OpenAiMapping.ANY_JSON_OBJECT
            format is JsonPrimitive && format.contentOrNull.isNullOrEmpty() -> null
            format is JsonObject -> format.toString()
            else -> throw InvalidRequestException("'format' must be \"json\" or a JSON schema object.")
        },
        seed = o?.seed
    )

    /** Ollama sends arguments as a JSON object; accept a JSON string as well. */
    private fun argumentsToJson(arguments: JsonElement?): String = when (arguments) {
        null, is JsonNull -> "{}"
        is JsonPrimitive -> arguments.contentOrNull?.let { JsonValues.parseOrNull(it)?.toString() } ?: "{}"
        else -> arguments.toString()
    }

    fun toOllamaToolCalls(calls: List<ToolCallData>): List<OllamaToolCall> = calls.mapIndexed { i, c ->
        OllamaToolCall(OllamaFunctionCall(c.name, JsonValues.parseOrNull(c.argumentsJson) ?: JsonObject(emptyMap()), i))
    }
}
