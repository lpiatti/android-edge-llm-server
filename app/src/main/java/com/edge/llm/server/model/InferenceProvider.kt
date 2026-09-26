package com.edge.llm.server.model

import com.edge.llm.server.util.LogCategory
import com.edge.llm.server.util.ServerConsole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID

/**
 * InferenceProvider: engine-agnostic contract between the HTTP layer and a runtime.
 *
 * [generate] streams [GenerationEvent]s and always ends with a [GenerationEvent.Done]
 * (unless cancelled or failed). Cancelling the collection must abort native generation.
 * Callers serialize access through [RequestQueue]: providers are not required to be reentrant.
 */
interface InferenceProvider {
    suspend fun initialize(): Boolean
    fun generate(request: GenerationRequest): Flow<GenerationEvent>
    fun unload()
}

/**
 * MockInferenceProvider: deterministic simulated engine for development and for exercising the
 * API (streaming, max tokens, tool-call round trip) without a model file.
 */
class MockInferenceProvider : InferenceProvider {
    private var isInit = false

    override suspend fun initialize(): Boolean {
        isInit = true
        ServerConsole.log(LogCategory.ENGINE, "MockInferenceProvider initialized.")
        return true
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        if (!isInit) throw IllegalStateException("Mock provider not initialized")
        val plan = ConversationPlanner.plan(request.messages)
        val promptTokens = estimateTokens(request.messages.joinToString("\n") { it.text })
        val lastInput = plan.input.last()

        if (request.tools.isNotEmpty() && lastInput.role == TurnRole.USER) {
            val tool = request.tools.first()
            val call = ToolCallData("call_" + UUID.randomUUID().toString().replace("-", "").take(24), tool.name, "{}")
            emit(GenerationEvent.ToolCalls(listOf(call)))
            emit(GenerationEvent.Done(FinishReason.TOOL_CALLS, promptTokens, 1))
            return@flow
        }

        val answer = if (lastInput.role == TurnRole.TOOL) {
            "MOCK: tool result received from '${lastInput.toolName ?: "?"}': ${plan.input.joinToString(" | ") { it.text }}"
        } else {
            "MOCK: Ciao! Ti rispondo dal Server Edge Android in modalità simulata. " +
                "Ho ricevuto ${request.messages.size} messaggi (${promptTokens} token stimati)."
        }
        val words = answer.split(" ")
        val limit = request.maxTokens ?: Int.MAX_VALUE
        var emitted = 0
        for (i in words.indices) {
            if (emitted >= limit) break
            emit(GenerationEvent.Text(words[i] + if (i == words.size - 1) "" else " "))
            emitted++
            delay(40)
        }
        val finish = if (emitted < words.size) FinishReason.LENGTH else FinishReason.STOP
        emit(GenerationEvent.Done(finish, promptTokens, emitted))
    }.flowOn(Dispatchers.Default)

    override fun unload() {
        isInit = false
        ServerConsole.log(LogCategory.ENGINE, "MockInferenceProvider unloaded.")
    }
}
