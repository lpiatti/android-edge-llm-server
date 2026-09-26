package com.edge.llm.server.api

import com.edge.llm.server.model.ChatTurn
import com.edge.llm.server.model.GenerationRequest
import com.edge.llm.server.model.InvalidRequestException
import com.edge.llm.server.model.ToolCallData
import com.edge.llm.server.model.ToolDefinition
import com.edge.llm.server.model.TurnRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Shared JSON configuration for every HTTP payload. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

// --- OpenAI wire format ------------------------------------------------------------------------

@Serializable
data class OaiChatRequest(
    val model: String? = null,
    val messages: List<OaiMessage> = emptyList(),
    val temperature: Double? = null,
    val top_p: Double? = null,
    val max_tokens: Int? = null,
    val max_completion_tokens: Int? = null,
    val stream: Boolean? = false,
    val stream_options: OaiStreamOptions? = null,
    val stop: JsonElement? = null,
    val tools: List<OaiTool>? = null,
    val tool_choice: JsonElement? = null,
    val response_format: OaiResponseFormat? = null,
    val n: Int? = null,
    val seed: Int? = null
)

@Serializable
data class OaiStreamOptions(val include_usage: Boolean? = null)

@Serializable
data class OaiMessage(
    val role: String,
    val content: JsonElement? = null,
    val name: String? = null,
    val tool_calls: List<OaiToolCall>? = null,
    val tool_call_id: String? = null
)

@Serializable
data class OaiToolCall(
    val index: Int? = null,
    val id: String? = null,
    val type: String = "function",
    val function: OaiFunctionCall
)

@Serializable
data class OaiFunctionCall(val name: String? = null, val arguments: String? = null)

@Serializable
data class OaiTool(val type: String = "function", val function: OaiFunctionDef)

@Serializable
data class OaiFunctionDef(val name: String, val description: String? = null, val parameters: JsonElement? = null)

@Serializable
data class OaiResponseFormat(val type: String, val json_schema: JsonElement? = null)

@Serializable
data class OaiChatResponse(
    val id: String,
    @SerialName("object") val obj: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<OaiChoice>,
    val usage: OaiUsage
)

@Serializable
data class OaiChoice(val index: Int = 0, val message: OaiResponseMessage, val finish_reason: String)

@Serializable
data class OaiResponseMessage(
    val role: String = "assistant",
    val content: String? = null,
    val tool_calls: List<OaiToolCall>? = null
)

@Serializable
data class OaiUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
    val prompt_tokens_details: OaiPromptTokensDetails? = null
)

@Serializable
data class OaiPromptTokensDetails(val cached_tokens: Int)

@Serializable
data class OaiChunk(
    val id: String,
    @SerialName("object") val obj: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<OaiChunkChoice>,
    val usage: OaiUsage? = null
)

@Serializable
data class OaiChunkChoice(val index: Int = 0, val delta: OaiDelta, val finish_reason: String? = null)

@Serializable
data class OaiDelta(val role: String? = null, val content: String? = null, val tool_calls: List<OaiToolCall>? = null)

@Serializable
data class OaiModel(
    val id: String,
    @SerialName("object") val obj: String = "model",
    val created: Long,
    val owned_by: String = "edge-llm-server"
)

@Serializable
data class OaiModelList(@SerialName("object") val obj: String = "list", val data: List<OaiModel>)

@Serializable
data class OaiErrorBody(val error: OaiError)

@Serializable
data class OaiError(val message: String, val type: String, val param: String? = null, val code: String? = null)

// --- Mapping --------------------------------------------------------------------------------------

object OpenAiMapping {

    fun toGenerationRequest(req: OaiChatRequest): GenerationRequest {
        if ((req.n ?: 1) > 1) throw InvalidRequestException("Only n=1 is supported.")
        if (req.messages.isEmpty()) throw InvalidRequestException("'messages' must not be empty.")

        val callNames = mutableMapOf<String, String>()
        val turns = req.messages.map { m ->
            when (m.role.lowercase()) {
                "system", "developer" -> ChatTurn(TurnRole.SYSTEM, contentToText(m.content))
                "user" -> ChatTurn(TurnRole.USER, contentToText(m.content))
                "assistant" -> {
                    val calls = m.tool_calls.orEmpty().mapIndexed { i, c ->
                        val name = c.function.name ?: throw InvalidRequestException("tool_calls[$i].function.name is required.")
                        val id = c.id ?: "call_$i"
                        callNames[id] = name
                        ToolCallData(id, name, c.function.arguments?.ifBlank { "{}" } ?: "{}")
                    }
                    ChatTurn(TurnRole.ASSISTANT, contentToText(m.content), calls)
                }
                "tool", "function" -> {
                    val name = m.tool_call_id?.let { callNames[it] } ?: m.name
                    ChatTurn(TurnRole.TOOL, contentToText(m.content), toolName = name)
                }
                else -> throw InvalidRequestException("Unsupported message role '${m.role}'.")
            }
        }

        return GenerationRequest(
            messages = turns,
            tools = selectTools(req.tools.orEmpty(), req.tool_choice),
            temperature = req.temperature,
            topP = req.top_p,
            maxTokens = (req.max_completion_tokens ?: req.max_tokens)?.takeIf { it > 0 },
            stop = parseStop(req.stop),
            jsonSchema = jsonSchemaFor(req.response_format),
            seed = req.seed
        )
    }

    /** OpenAI `content`: a string, null, or an array of parts (only text parts are supported). */
    fun contentToText(content: JsonElement?): String = when (content) {
        null, is JsonNull -> ""
        is JsonPrimitive -> content.contentOrNull ?: ""
        is JsonArray -> content.joinToString("") { part ->
            val obj = part as? JsonObject ?: throw InvalidRequestException("Content parts must be objects.")
            val type = obj["type"]?.jsonPrimitive?.contentOrNull
            if (type != null && type != "text" && type != "input_text") {
                throw InvalidRequestException("Content part type '$type' is not supported (text only).")
            }
            obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
        }
        is JsonObject -> throw InvalidRequestException("'content' must be a string or an array of parts.")
    }

    fun parseStop(stop: JsonElement?): List<String> = when (stop) {
        null, is JsonNull -> emptyList()
        is JsonPrimitive -> listOfNotNull(stop.contentOrNull).filter { it.isNotEmpty() }
        is JsonArray -> stop.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotEmpty() }
        else -> throw InvalidRequestException("'stop' must be a string or an array of strings.")
    }

    fun toolDefinitions(tools: List<OaiTool>): List<ToolDefinition> = tools
        .filter { it.type == "function" }
        .map { ToolDefinition(it.function.name, it.function.description ?: "", it.function.parameters?.toString()) }

    private fun selectTools(tools: List<OaiTool>, choice: JsonElement?): List<ToolDefinition> {
        val defs = toolDefinitions(tools)
        return when {
            choice is JsonPrimitive && choice.contentOrNull == "none" -> emptyList()
            choice is JsonObject -> {
                val name = (choice["function"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                if (name == null) defs else defs.filter { it.name == name }
            }
            else -> defs
        }
    }

    private fun jsonSchemaFor(format: OaiResponseFormat?): String? = when (format?.type) {
        "json_object" -> ANY_JSON_OBJECT
        "json_schema" -> ((format?.json_schema as? JsonObject)?.get("schema") ?: format?.json_schema)?.toString() ?: ANY_JSON_OBJECT
        else -> null
    }

    fun toOaiToolCalls(calls: List<ToolCallData>, withIndex: Boolean): List<OaiToolCall> =
        calls.mapIndexed { i, c ->
            OaiToolCall(index = if (withIndex) i else null, id = c.id, function = OaiFunctionCall(c.name, c.argumentsJson))
        }

    const val ANY_JSON_OBJECT = "{\"type\":\"object\"}"
}
