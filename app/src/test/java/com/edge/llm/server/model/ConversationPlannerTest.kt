package com.edge.llm.server.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPlannerTest {

    private fun user(t: String) = ChatTurn(TurnRole.USER, t)
    private fun assistant(t: String, calls: List<ToolCallData> = emptyList()) = ChatTurn(TurnRole.ASSISTANT, t, calls)

    @Test
    fun systemMessagesAreFoldedAndLastUserIsInput() {
        val plan = ConversationPlanner.plan(
            listOf(ChatTurn(TurnRole.SYSTEM, "Be brief."), user("Hi"), assistant("Hello"), user("Name?"))
        )
        assertEquals("Be brief.", plan.systemInstruction)
        assertEquals(listOf(user("Hi"), assistant("Hello")), plan.history)
        assertEquals(listOf(user("Name?")), plan.input)
    }

    @Test
    fun trailingToolResultsFormTheInputGroup() {
        val calls = listOf(ToolCallData("a", "f", "{}"), ToolCallData("b", "g", "{}"))
        val plan = ConversationPlanner.plan(
            listOf(
                user("do it"),
                assistant("", calls),
                ChatTurn(TurnRole.TOOL, "1", toolName = "f"),
                ChatTurn(TurnRole.TOOL, "2", toolName = "g")
            )
        )
        assertEquals(2, plan.history.size)
        assertEquals(2, plan.input.size)
    }

    @Test(expected = InvalidRequestException::class)
    fun lastAssistantMessageIsRejected() {
        ConversationPlanner.plan(listOf(user("hi"), assistant("prefill")))
    }

    @Test(expected = InvalidRequestException::class)
    fun onlySystemIsRejected() {
        ConversationPlanner.plan(listOf(ChatTurn(TurnRole.SYSTEM, "x")))
    }

    @Test
    fun reuseWhenRequestExtendsCachedHistory() {
        val first = ConversationPlanner.plan(listOf(user("Hi")))
        val cached = first.historyAfter(assistant("Hello there"))
        val second = ConversationPlanner.plan(listOf(user("Hi"), assistant("Hello there  "), user("More")))
        assertTrue(ConversationPlanner.canReuse(cached, second))
    }

    @Test
    fun noReuseWhenHistoryDiverges() {
        val cached = ConversationPlanner.plan(listOf(user("Hi"))).historyAfter(assistant("Hello"))
        val edited = ConversationPlanner.plan(listOf(user("Hi"), assistant("Edited answer"), user("More")))
        assertFalse(ConversationPlanner.canReuse(cached, edited))
    }

    @Test
    fun toolCallArgumentsAreComparedAsJson() {
        val cached = ConversationPlanner.plan(listOf(user("w?")))
            .historyAfter(assistant("", listOf(ToolCallData("call_x", "weather", "{\"a\":1,\"b\":\"x\"}"))))
        val next = ConversationPlanner.plan(
            listOf(
                user("w?"),
                assistant("", listOf(ToolCallData("call_x", "weather", "{\"a\": 1, \"b\": \"x\"}"))),
                ChatTurn(TurnRole.TOOL, "sunny", toolName = "weather")
            )
        )
        assertTrue(ConversationPlanner.canReuse(cached, next))
    }
}
