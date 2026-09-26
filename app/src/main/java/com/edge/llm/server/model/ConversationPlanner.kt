package com.edge.llm.server.model

/**
 * How a stateless request is mapped onto an engine conversation:
 * - [systemInstruction]: all system messages, joined;
 * - [history]: every other turn before the input, fed as native history;
 * - [input]: what is actually sent — one USER turn, or the trailing group of TOOL turns.
 */
data class ConversationPlan(
    val systemInstruction: String?,
    val history: List<ChatTurn>,
    val input: List<ChatTurn>
) {
    /** The engine-side history after [reply] is generated, used for prefix reuse. */
    fun historyAfter(reply: ChatTurn): List<ChatTurn> = history + input + reply
}

object ConversationPlanner {

    fun plan(messages: List<ChatTurn>): ConversationPlan {
        if (messages.isEmpty()) throw InvalidRequestException("'messages' must contain at least one message.")

        val system = messages.filter { it.role == TurnRole.SYSTEM }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
            .ifEmpty { null }
        val turns = messages.filter { it.role != TurnRole.SYSTEM }
        if (turns.isEmpty()) throw InvalidRequestException("'messages' must contain a user message.")

        val last = turns.last()
        val inputStart = when (last.role) {
            TurnRole.USER -> turns.size - 1
            TurnRole.TOOL -> {
                var i = turns.size - 1
                while (i > 0 && turns[i - 1].role == TurnRole.TOOL) i--
                i
            }
            else -> throw InvalidRequestException(
                "The last message must have role 'user' or 'tool' (got '${last.role.name.lowercase()}')."
            )
        }
        return ConversationPlan(system, turns.subList(0, inputStart), turns.subList(inputStart, turns.size))
    }

    /** True when [plan] continues exactly the conversation whose engine state is [cachedHistory]. */
    fun canReuse(cachedHistory: List<ChatTurn>, plan: ConversationPlan): Boolean =
        sameTurns(cachedHistory, plan.history)

    fun sameTurns(a: List<ChatTurn>, b: List<ChatTurn>): Boolean {
        if (a.size != b.size) return false
        return a.indices.all { sameTurn(a[it], b[it]) }
    }

    private fun sameTurn(a: ChatTurn, b: ChatTurn): Boolean {
        if (a.role != b.role || a.toolName != b.toolName) return false
        if (a.text.trim() != b.text.trim()) return false
        if (a.toolCalls.size != b.toolCalls.size) return false
        return a.toolCalls.indices.all { i ->
            val x = a.toolCalls[i]
            val y = b.toolCalls[i]
            x.name == y.name && JsonValues.parseOrNull(x.argumentsJson) == JsonValues.parseOrNull(y.argumentsJson)
        }
    }
}
