package com.edge.llm.server.model

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationPipelineTest {

    @Test
    fun stopFilterPassesThroughWithoutStops() {
        val f = StopSequenceFilter(emptyList())
        assertEquals("hello", f.push("hello"))
        assertFalse(f.stopped)
    }

    @Test
    fun stopFilterTruncatesAtStopSequenceSplitAcrossChunks() {
        val f = StopSequenceFilter(listOf("END"))
        val out = StringBuilder()
        for (chunk in listOf("Hello wo", "rld E", "N", "D and more")) {
            out.append(f.push(chunk))
            if (f.stopped) break
        }
        assertEquals("Hello world ", out.toString())
        assertTrue(f.stopped)
    }

    @Test
    fun stopFilterReleasesHeldBackTextThatIsNotAStop() {
        val f = StopSequenceFilter(listOf("###"))
        val first = f.push("a#")
        assertEquals("a", first)
        val second = f.push("b")
        assertEquals("#b", second)
        assertEquals("", f.flush())
    }

    @Test
    fun stopFilterPicksEarliestOfSeveralStops() {
        val f = StopSequenceFilter(listOf("zzz", "\n\n"))
        assertEquals("line", f.push("line\n\nzzz"))
    }

    @Test
    fun applyStopsCancelsUpstreamAndReportsStop() = runBlocking {
        var emittedAfterStop = false
        val upstream = flow {
            emit(GenerationEvent.Text("one "))
            emit(GenerationEvent.Text("two STOP"))
            emittedAfterStop = true
            emit(GenerationEvent.Text("three"))
            emit(GenerationEvent.Done(FinishReason.STOP, 10, 3))
        }
        val events = upstream.applyStops(listOf("STOP")).toList()
        val text = events.filterIsInstance<GenerationEvent.Text>().joinToString("") { it.text }
        assertEquals("one two ", text)
        val done = events.last() as GenerationEvent.Done
        assertEquals(FinishReason.STOP, done.finishReason)
        assertFalse("upstream must be cancelled at the stop sequence", emittedAfterStop)
    }

    @Test
    fun collectResultAggregatesToolCalls() = runBlocking {
        val call = ToolCallData("call_1", "get_weather", "{\"city\":\"Rome\"}")
        val result = flow {
            emit(GenerationEvent.ToolCalls(listOf(call)))
            emit(GenerationEvent.Done(FinishReason.TOOL_CALLS, 42, 1, cachedPromptTokens = 30))
        }.collectResult()
        assertEquals(FinishReason.TOOL_CALLS, result.finishReason)
        assertEquals(listOf(call), result.toolCalls)
        assertEquals(42, result.promptTokens)
        assertEquals(30, result.cachedPromptTokens)
    }

    @Test
    fun mockProviderDoesToolRoundTrip() = runBlocking {
        val mock = MockInferenceProvider()
        mock.initialize()
        val tools = listOf(ToolDefinition("get_battery_status", "Battery level"))
        val first = mock.generate(
            GenerationRequest(listOf(ChatTurn(TurnRole.USER, "battery?")), tools = tools)
        ).collectResult()
        assertEquals(FinishReason.TOOL_CALLS, first.finishReason)
        val call = first.toolCalls.single()
        assertEquals("get_battery_status", call.name)

        val second = mock.generate(
            GenerationRequest(
                listOf(
                    ChatTurn(TurnRole.USER, "battery?"),
                    ChatTurn(TurnRole.ASSISTANT, "", listOf(call)),
                    ChatTurn(TurnRole.TOOL, "{\"level\":80}", toolName = call.name)
                ),
                tools = tools
            )
        ).collectResult()
        assertEquals(FinishReason.STOP, second.finishReason)
        assertTrue(second.text.contains("get_battery_status"))
    }

    @Test
    fun mockProviderHonorsMaxTokens() = runBlocking {
        val mock = MockInferenceProvider()
        mock.initialize()
        val result = mock.generate(
            GenerationRequest(listOf(ChatTurn(TurnRole.USER, "hi")), maxTokens = 3)
        ).collectResult()
        assertEquals(FinishReason.LENGTH, result.finishReason)
        assertEquals(3, result.completionTokens)
    }
}
