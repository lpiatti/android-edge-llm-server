package com.edge.llm.server.api

import com.edge.llm.server.model.ConversationPlanner
import com.edge.llm.server.model.FinishReason
import com.edge.llm.server.model.GenerationEvent
import com.edge.llm.server.model.GenerationRequest
import com.edge.llm.server.model.InvalidRequestException
import com.edge.llm.server.model.ModelManager
import com.edge.llm.server.model.QueueFullException
import com.edge.llm.server.model.QueueTimeoutException
import com.edge.llm.server.model.RequestQueue
import com.edge.llm.server.model.ToolCallData
import com.edge.llm.server.model.applyStops
import com.edge.llm.server.model.estimateTokens
import com.edge.llm.server.util.LogCategory
import com.edge.llm.server.util.ServerConsole
import com.edge.llm.server.util.ServerStats
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Optional API key (M6). Null or blank = open access (home LAN profile). */
object ApiKeyStore {
    @Volatile
    var apiKey: String? = null

    val isEnabled: Boolean get() = !apiKey.isNullOrBlank()
}

class ModelNotLoadedException : Exception("No model is currently loaded. Load a model in the ENGINE tab first.")

private enum class ErrorStyle { OPENAI, OLLAMA }

@Serializable
private data class HealthResponse(
    val status: String,
    val server_time: String,
    val uptime_seconds: Long,
    val model: String,
    val model_loaded: Boolean,
    val auth_required: Boolean
)

/** Timing and token metrics of one generation, as observed by the HTTP layer. */
private class RunMetrics(val startMs: Long = System.currentTimeMillis()) {
    var firstTokenMs = -1L
    var endMs = 0L
    var done: GenerationEvent.Done? = null
    var sawToolCalls = false
    val ttftMs: Long get() = if (firstTokenMs < 0) endMs - startMs else firstTokenMs - startMs
    val totalMs: Long get() = endMs - startMs
}

/**
 * Installs every HTTP endpoint (OpenAI + Ollama + service). The contract is docs/api-contract.md.
 */
fun Application.edgeApiModule(appVersion: String, startTimeMs: Long, queueProvider: () -> RequestQueue?) {
    install(ContentNegotiation) { json(ApiJson) }

    routing {
        get("/") {
            call.respondText("Ollama is running (Edge LLM Server $appVersion)", ContentType.Text.Plain)
        }

        get("/health") {
            ServerStats.totalRequests++
            call.respond(
                HealthResponse(
                    status = "healthy",
                    server_time = isoNow(),
                    uptime_seconds = (System.currentTimeMillis() - startTimeMs) / 1000,
                    model = ModelManager.activeModelId,
                    model_loaded = ModelManager.isModelLoaded,
                    auth_required = ApiKeyStore.isEnabled
                )
            )
        }

        // ---------------- OpenAI ----------------

        get("/v1/models") {
            if (!authorized(call, ErrorStyle.OPENAI)) return@get
            ServerStats.totalRequests++
            call.respond(OaiModelList(data = listOf(currentOaiModel())))
        }

        get("/v1/models/{id}") {
            if (!authorized(call, ErrorStyle.OPENAI)) return@get
            ServerStats.totalRequests++
            call.respond(currentOaiModel())
        }

        post("/v1/chat/completions") {
            if (!authorized(call, ErrorStyle.OPENAI)) return@post
            handle(call, ErrorStyle.OPENAI) {
                val req = call.receive<OaiChatRequest>()
                val gen = OpenAiMapping.toGenerationRequest(req)
                ConversationPlanner.plan(gen.messages)
                requireModel()
                val queue = queueProvider() ?: throw IllegalStateException("Request queue not initialized")
                logRequest(call, "stream=${req.stream == true}, msgs=${gen.messages.size}, tools=${gen.tools.size}, max=${gen.maxTokens}, stop=${gen.stop.size}, json=${gen.jsonSchema != null}")
                if (req.stream == true) {
                    queue.execute { streamOpenAi(call, gen, req.stream_options?.include_usage == true) }
                } else {
                    queue.execute { respondOpenAi(call, gen) }
                }
            }
        }

        post("/v1/embeddings") { notImplemented(call, ErrorStyle.OPENAI, "Embeddings are not supported yet (see docs/backlog.md).") }
        post("/v1/completions") { notImplemented(call, ErrorStyle.OPENAI, "Legacy completions are not supported; use /v1/chat/completions.") }

        // ---------------- Ollama ----------------

        get("/api/version") {
            ServerStats.totalRequests++
            call.respond(OllamaVersion(appVersion))
        }

        get("/api/tags") {
            if (!authorized(call, ErrorStyle.OLLAMA)) return@get
            ServerStats.totalRequests++
            call.respond(OllamaModelList(listOf(currentOllamaModel(forPs = false))))
        }

        get("/api/ps") {
            if (!authorized(call, ErrorStyle.OLLAMA)) return@get
            ServerStats.totalRequests++
            val models = if (ModelManager.isModelLoaded) listOf(currentOllamaModel(forPs = true)) else emptyList()
            call.respond(OllamaModelList(models))
        }

        post("/api/show") {
            if (!authorized(call, ErrorStyle.OLLAMA)) return@post
            ServerStats.totalRequests++
            val id = ModelManager.activeModelId
            val info = describeModel(id)
            call.respond(
                OllamaShowResponse(
                    modelfile = "# Served by Edge LLM Server from ${ModelManager.activeModelName}",
                    parameters = "",
                    template = "{{ .Prompt }}",
                    details = info,
                    model_info = mapOf("general.architecture" to info.family, "general.basename" to id),
                    capabilities = listOf("completion", "tools"),
                    modified_at = isoDate(modelModifiedAt())
                )
            )
        }

        post("/api/chat") {
            if (!authorized(call, ErrorStyle.OLLAMA)) return@post
            handle(call, ErrorStyle.OLLAMA) {
                val req = call.receive<OllamaChatRequest>()
                val gen = OllamaMapping.toGenerationRequest(req)
                ConversationPlanner.plan(gen.messages)
                requireModel()
                val queue = queueProvider() ?: throw IllegalStateException("Request queue not initialized")
                logRequest(call, "stream=${req.stream != false}, msgs=${gen.messages.size}, tools=${gen.tools.size}, max=${gen.maxTokens}")
                queue.execute { ollamaChat(call, gen, stream = req.stream != false) }
            }
        }

        post("/api/generate") {
            if (!authorized(call, ErrorStyle.OLLAMA)) return@post
            handle(call, ErrorStyle.OLLAMA) {
                val req = call.receive<OllamaGenerateRequest>()
                if (req.prompt.isNullOrEmpty()) {
                    // Ollama semantics: empty prompt = load the model and return immediately.
                    call.respond(OllamaGenerateResponse(ModelManager.activeModelId, isoNow(), "", done = true, done_reason = "load"))
                    return@handle
                }
                val gen = OllamaMapping.toGenerationRequest(req)
                requireModel()
                val queue = queueProvider() ?: throw IllegalStateException("Request queue not initialized")
                logRequest(call, "stream=${req.stream != false}, max=${gen.maxTokens}")
                queue.execute { ollamaGenerate(call, gen, stream = req.stream != false) }
            }
        }

        for (path in listOf("/api/pull", "/api/push", "/api/create", "/api/copy", "/api/embed", "/api/embeddings")) {
            post(path) { notImplemented(call, ErrorStyle.OLLAMA, "$path is not supported: models are loaded from the device (ENGINE tab).") }
        }
        delete("/api/delete") { notImplemented(call, ErrorStyle.OLLAMA, "/api/delete is not supported.") }
    }
}

// ---------------- OpenAI handlers ----------------

private suspend fun respondOpenAi(call: ApplicationCall, gen: GenerationRequest) {
    val text = StringBuilder()
    val calls = mutableListOf<ToolCallData>()
    val m = runGeneration(gen) { ev ->
        when (ev) {
            is GenerationEvent.Text -> text.append(ev.text)
            is GenerationEvent.ToolCalls -> calls.addAll(ev.calls)
            is GenerationEvent.Done -> Unit
        }
    }
    val finish = finishReason(m)
    call.respond(
        OaiChatResponse(
            id = "chatcmpl-" + UUID.randomUUID(),
            created = System.currentTimeMillis() / 1000,
            model = ModelManager.activeModelId,
            choices = listOf(
                OaiChoice(
                    message = OaiResponseMessage(
                        content = if (calls.isNotEmpty() && text.isBlank()) null else text.toString(),
                        tool_calls = if (calls.isEmpty()) null else OpenAiMapping.toOaiToolCalls(calls, withIndex = false)
                    ),
                    finish_reason = finish
                )
            ),
            usage = usageOf(m, gen)
        )
    )
    ServerConsole.log(LogCategory.SERVER, "<-- 200 chat.completion ($finish, ${m.done?.completionTokens ?: 0} tok, TTFT ${m.ttftMs} ms)")
}

private suspend fun streamOpenAi(call: ApplicationCall, gen: GenerationRequest, includeUsage: Boolean) {
    val id = "chatcmpl-" + UUID.randomUUID()
    val created = System.currentTimeMillis() / 1000
    val model = ModelManager.activeModelId
    call.response.header(HttpHeaders.CacheControl, "no-cache")
    call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
        suspend fun send(choices: List<OaiChunkChoice>, usage: OaiUsage? = null) {
            val chunk = OaiChunk(id = id, created = created, model = model, choices = choices, usage = usage)
            writeSse(ApiJson.encodeToString(OaiChunk.serializer(), chunk))
        }
        send(listOf(OaiChunkChoice(delta = OaiDelta(role = "assistant", content = ""))))
        var toolIndex = 0
        try {
            val m = runGeneration(gen) { ev ->
                when (ev) {
                    is GenerationEvent.Text -> send(listOf(OaiChunkChoice(delta = OaiDelta(content = ev.text))))
                    is GenerationEvent.ToolCalls -> {
                        val calls = ev.calls.map { c ->
                            OaiToolCall(index = toolIndex++, id = c.id, function = OaiFunctionCall(c.name, c.argumentsJson))
                        }
                        send(listOf(OaiChunkChoice(delta = OaiDelta(tool_calls = calls))))
                    }
                    is GenerationEvent.Done -> Unit
                }
            }
            val finish = finishReason(m)
            send(listOf(OaiChunkChoice(delta = OaiDelta(), finish_reason = finish)))
            if (includeUsage) send(emptyList(), usageOf(m, gen))
            ServerConsole.log(LogCategory.SERVER, "<-- 200 SSE done ($finish, ${m.done?.completionTokens ?: 0} tok, TTFT ${m.ttftMs} ms)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.SERVER, "ERROR mid-stream: ${e.message}")
            writeSse(ApiJson.encodeToString(OaiErrorBody.serializer(), OaiErrorBody(OaiError(e.message ?: "Generation failed", "server_error"))))
        }
        writeSse("[DONE]")
    }
}

private suspend fun ByteWriteChannel.writeSse(data: String) {
    writeStringUtf8("data: $data\n\n")
    flush()
}

// ---------------- Ollama handlers ----------------

private suspend fun ollamaChat(call: ApplicationCall, gen: GenerationRequest, stream: Boolean) {
    val model = ModelManager.activeModelId
    if (!stream) {
        val text = StringBuilder()
        val calls = mutableListOf<ToolCallData>()
        val m = runGeneration(gen) { ev ->
            if (ev is GenerationEvent.Text) text.append(ev.text)
            if (ev is GenerationEvent.ToolCalls) calls.addAll(ev.calls)
        }
        call.respond(ollamaChatFinal(model, m, gen, text.toString(), calls))
        ServerConsole.log(LogCategory.SERVER, "<-- 200 /api/chat (${finishReason(m)}, TTFT ${m.ttftMs} ms)")
        return
    }
    call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
        try {
            val m = runGeneration(gen) { ev ->
                val line = when (ev) {
                    is GenerationEvent.Text -> OllamaChatResponse(model, isoNow(), OllamaMessage("assistant", ev.text), done = false)
                    is GenerationEvent.ToolCalls -> OllamaChatResponse(
                        model, isoNow(), OllamaMessage("assistant", "", tool_calls = OllamaMapping.toOllamaToolCalls(ev.calls)), done = false
                    )
                    is GenerationEvent.Done -> null
                }
                if (line != null) writeLine(ApiJson.encodeToString(OllamaChatResponse.serializer(), line))
            }
            writeLine(ApiJson.encodeToString(OllamaChatResponse.serializer(), ollamaChatFinal(model, m, gen, "", emptyList())))
            ServerConsole.log(LogCategory.SERVER, "<-- 200 /api/chat NDJSON done (${finishReason(m)}, TTFT ${m.ttftMs} ms)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ServerConsole.log(LogCategory.SERVER, "ERROR mid-stream: ${e.message}")
            writeLine(ApiJson.encodeToString(OllamaError.serializer(), OllamaError(e.message ?: "Generation failed")))
        }
    }
}

private fun ollamaChatFinal(model: String, m: RunMetrics, gen: GenerationRequest, text: String, calls: List<ToolCallData>): OllamaChatResponse {
    val usage = usageOf(m, gen)
    return OllamaChatResponse(
        model = model,
        created_at = isoNow(),
        message = OllamaMessage("assistant", text, tool_calls = if (calls.isEmpty()) null else OllamaMapping.toOllamaToolCalls(calls)),
        done = true,
        done_reason = ollamaDoneReason(finishReason(m)),
        total_duration = m.totalMs * 1_000_000,
        load_duration = 0,
        prompt_eval_count = usage.prompt_tokens,
        prompt_eval_duration = m.ttftMs * 1_000_000,
        eval_count = usage.completion_tokens,
        eval_duration = (m.totalMs - m.ttftMs).coerceAtLeast(0) * 1_000_000
    )
}

private suspend fun ollamaGenerate(call: ApplicationCall, gen: GenerationRequest, stream: Boolean) {
    val model = ModelManager.activeModelId
    fun finalResponse(m: RunMetrics, text: String): OllamaGenerateResponse {
        val usage = usageOf(m, gen)
        return OllamaGenerateResponse(
            model = model,
            created_at = isoNow(),
            response = text,
            done = true,
            done_reason = ollamaDoneReason(finishReason(m)),
            total_duration = m.totalMs * 1_000_000,
            load_duration = 0,
            prompt_eval_count = usage.prompt_tokens,
            prompt_eval_duration = m.ttftMs * 1_000_000,
            eval_count = usage.completion_tokens,
            eval_duration = (m.totalMs - m.ttftMs).coerceAtLeast(0) * 1_000_000
        )
    }
    if (!stream) {
        val text = StringBuilder()
        val m = runGeneration(gen) { ev -> if (ev is GenerationEvent.Text) text.append(ev.text) }
        call.respond(finalResponse(m, text.toString()))
        return
    }
    call.respondBytesWriter(contentType = ContentType.parse("application/x-ndjson")) {
        try {
            val m = runGeneration(gen) { ev ->
                if (ev is GenerationEvent.Text) {
                    writeLine(ApiJson.encodeToString(OllamaGenerateResponse.serializer(), OllamaGenerateResponse(model, isoNow(), ev.text, done = false)))
                }
            }
            writeLine(ApiJson.encodeToString(OllamaGenerateResponse.serializer(), finalResponse(m, "")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            writeLine(ApiJson.encodeToString(OllamaError.serializer(), OllamaError(e.message ?: "Generation failed")))
        }
    }
}

private suspend fun ByteWriteChannel.writeLine(json: String) {
    writeStringUtf8(json + "\n")
    flush()
}

private fun ollamaDoneReason(finish: String) = if (finish == FinishReason.LENGTH) "length" else "stop"

// ---------------- Shared helpers ----------------

private suspend fun runGeneration(gen: GenerationRequest, onEvent: suspend (GenerationEvent) -> Unit): RunMetrics {
    val m = RunMetrics()
    ServerStats.activeConnections++
    try {
        ModelManager.activeProvider.generate(gen).applyStops(gen.stop).collect { ev ->
            if (m.firstTokenMs < 0 && ev !is GenerationEvent.Done) m.firstTokenMs = System.currentTimeMillis()
            if (ev is GenerationEvent.ToolCalls) m.sawToolCalls = true
            if (ev is GenerationEvent.Done) m.done = ev
            onEvent(ev)
        }
    } finally {
        m.endMs = System.currentTimeMillis()
        ServerStats.activeConnections--
    }
    val done = m.done
    val completion = done?.completionTokens ?: 0
    ServerStats.totalTokensGenerated += completion
    ServerStats.lastTtftMs = m.ttftMs
    ServerStats.lastPromptTokens = done?.promptTokens ?: 0
    ServerStats.lastCachedPromptTokens = done?.cachedPromptTokens ?: 0
    val decodeSec = (m.totalMs - m.ttftMs) / 1000.0
    if (decodeSec > 0 && completion > 1) ServerStats.lastGenerationSpeedTps = (completion - 1) / decodeSec
    return m
}

private fun finishReason(m: RunMetrics): String =
    if (m.sawToolCalls) FinishReason.TOOL_CALLS else m.done?.finishReason ?: FinishReason.STOP

private fun usageOf(m: RunMetrics, gen: GenerationRequest): OaiUsage {
    val done = m.done
    val prompt = done?.promptTokens?.takeIf { it > 0 } ?: estimateTokens(gen.messages.joinToString("\n") { it.text })
    val completion = done?.completionTokens ?: 0
    val cached = done?.cachedPromptTokens ?: 0
    return OaiUsage(prompt, completion, prompt + completion, if (cached > 0) OaiPromptTokensDetails(cached) else null)
}

private fun requireModel() {
    if (!ModelManager.isModelLoaded || ModelManager.isLoading) throw ModelNotLoadedException()
}

private suspend fun authorized(call: ApplicationCall, style: ErrorStyle): Boolean {
    val key = ApiKeyStore.apiKey?.trim()
    if (key.isNullOrEmpty()) return true
    val header = call.request.headers[HttpHeaders.Authorization]?.trim().orEmpty()
    val presented = if (header.startsWith("Bearer ", ignoreCase = true)) header.substring(7).trim() else header
    if (presented == key) return true
    ServerConsole.log(LogCategory.SERVER, "<-- 401 ${call.request.httpMethod.value} ${call.request.uri} from ${call.request.local.remoteHost}: missing or invalid API key")
    respondError(call, style, HttpStatusCode.Unauthorized, "Missing or invalid API key. Send 'Authorization: Bearer <key>'.", "authentication_error")
    return false
}

private suspend fun handle(call: ApplicationCall, style: ErrorStyle, block: suspend () -> Unit) {
    ServerStats.totalRequests++
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: QueueFullException) {
        call.response.header(HttpHeaders.RetryAfter, "30")
        respondError(call, style, HttpStatusCode.TooManyRequests, e.message ?: "Queue full", "rate_limit_exceeded")
    } catch (e: QueueTimeoutException) {
        call.response.header(HttpHeaders.RetryAfter, "30")
        respondError(call, style, HttpStatusCode.TooManyRequests, e.message ?: "Queue timeout", "rate_limit_exceeded")
    } catch (e: ModelNotLoadedException) {
        respondError(call, style, HttpStatusCode.ServiceUnavailable, e.message ?: "No model loaded", "server_error")
    } catch (e: InvalidRequestException) {
        respondError(call, style, HttpStatusCode.BadRequest, e.message ?: "Invalid request", "invalid_request_error")
    } catch (e: io.ktor.server.plugins.BadRequestException) {
        respondError(call, style, HttpStatusCode.BadRequest, "Malformed request body: ${e.cause?.message ?: e.message}", "invalid_request_error")
    } catch (e: kotlinx.serialization.SerializationException) {
        respondError(call, style, HttpStatusCode.BadRequest, "Malformed request body: ${e.message}", "invalid_request_error")
    } catch (e: IllegalArgumentException) {
        respondError(call, style, HttpStatusCode.BadRequest, e.message ?: "Invalid request", "invalid_request_error")
    } catch (e: Exception) {
        // Body conversion failures (e.g. CannotTransformContentToTypeException) are client errors.
        if (e.javaClass.name.contains("Transform")) {
            respondError(call, style, HttpStatusCode.BadRequest, "Malformed request body: ${e.message}", "invalid_request_error")
            return
        }
        ServerConsole.log(LogCategory.SERVER, "ERROR ${call.request.uri}: ${e.javaClass.simpleName}: ${e.message}")
        respondError(call, style, HttpStatusCode.InternalServerError, e.message ?: "Internal error", "server_error")
    }
}

private suspend fun respondError(call: ApplicationCall, style: ErrorStyle, status: HttpStatusCode, message: String, type: String) {
    ServerConsole.log(LogCategory.SERVER, "<-- ${status.value} ${call.request.uri}: $message")
    try {
        when (style) {
            ErrorStyle.OPENAI -> call.respond(status, OaiErrorBody(OaiError(message, type)))
            ErrorStyle.OLLAMA -> call.respond(status, OllamaError(message))
        }
    } catch (e: Exception) {
        // Response already committed (e.g. failure mid-stream): nothing more can be sent.
    }
}

private suspend fun notImplemented(call: ApplicationCall, style: ErrorStyle, message: String) {
    if (!authorized(call, style)) return
    ServerStats.totalRequests++
    respondError(call, style, HttpStatusCode.NotImplemented, message, "invalid_request_error")
}

private fun logRequest(call: ApplicationCall, details: String) {
    ServerConsole.log(LogCategory.SERVER, "--> ${call.request.httpMethod.value} ${call.request.uri} from ${call.request.local.remoteHost} ($details)")
}

private fun currentOaiModel() = OaiModel(
    id = ModelManager.activeModelId,
    created = (ModelManager.activeModelLoadedAt.takeIf { it > 0 } ?: System.currentTimeMillis()) / 1000
)

private fun modelModifiedAt(): Long =
    ModelManager.activeModelPath?.let { File(it).lastModified() }?.takeIf { it > 0 } ?: ModelManager.activeModelLoadedAt

private fun currentOllamaModel(forPs: Boolean): OllamaModelEntry {
    val id = ModelManager.activeModelId
    val size = ModelManager.activeModelSizeBytes
    return OllamaModelEntry(
        name = id,
        model = id,
        modified_at = isoDate(modelModifiedAt()),
        size = size,
        digest = syntheticDigest(),
        details = describeModel(id),
        expires_at = if (forPs) "2999-01-01T00:00:00Z" else null,
        size_vram = if (forPs) (if (ModelManager.isGpuActive) size else 0L) else null
    )
}

/** Identity digest (not a content hash: hashing a multi-GB file on every request is not viable). */
private fun syntheticDigest(): String {
    val seed = "${ModelManager.activeModelPath}|${ModelManager.activeModelSizeBytes}|${modelModifiedAt()}"
    val bytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun describeModel(id: String): OllamaModelDetails {
    val lower = id.lowercase()
    val family = listOf("gemma", "qwen", "llama", "phi", "smollm", "deepseek", "mistral").firstOrNull { lower.contains(it) } ?: "litertlm"
    val size = Regex("(?i)(?:^|[-_])(e?\\d+(?:\\.\\d+)?[bm])(?:[-_.]|$)").find(id)?.groupValues?.get(1)?.uppercase() ?: ""
    val quant = Regex("(?i)(int4|int8|q4\\w*|q8\\w*|fp16|f16)").find(id)?.value?.uppercase() ?: ""
    return OllamaModelDetails(family = family, families = listOf(family), parameter_size = size, quantization_level = quant)
}

private fun isoNow(): String = isoDate(System.currentTimeMillis())

private fun isoDate(ms: Long): String {
    val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(ms))
}
