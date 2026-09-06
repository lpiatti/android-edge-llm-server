package com.edge.llm.server.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun testEmptyMessages() {
        val prompt = PromptBuilder.buildGemmaPrompt(emptyList())
        assertEquals("<start_of_turn>model\n", prompt)
    }

    @Test
    fun testSingleUserMessage() {
        val messages = listOf(
            ChatMessage("user", "Hello world!")
        )
        val prompt = PromptBuilder.buildGemmaPrompt(messages)
        val expected = "<start_of_turn>user\nHello world!<end_of_turn>\n<start_of_turn>model\n"
        assertEquals(expected, prompt)
    }

    @Test
    fun testSystemAndUserMessage() {
        val messages = listOf(
            ChatMessage("system", "You are an AI assistant."),
            ChatMessage("user", "Who are you?")
        )
        val prompt = PromptBuilder.buildGemmaPrompt(messages)
        val expected = "<start_of_turn>user\nYou are an AI assistant.\n\nWho are you?<end_of_turn>\n<start_of_turn>model\n"
        assertEquals(expected, prompt)
    }

    @Test
    fun testMultiTurnRecallConversation() {
        val messages = listOf(
            ChatMessage("user", "Il mio nome in codice è Aquila."),
            ChatMessage("assistant", "Ricevuto, ti chiamerò Aquila."),
            ChatMessage("user", "Qual era il mio nome in codice?")
        )
        val prompt = PromptBuilder.buildGemmaPrompt(messages)
        val expected = StringBuilder()
            .append("<start_of_turn>user\nIl mio nome in codice è Aquila.<end_of_turn>\n")
            .append("<start_of_turn>model\nRicevuto, ti chiamerò Aquila.<end_of_turn>\n")
            .append("<start_of_turn>user\nQual era il mio nome in codice?<end_of_turn>\n")
            .append("<start_of_turn>model\n")
            .toString()

        assertEquals(expected, prompt)
    }

    @Test
    fun testToolMessage() {
        val messages = listOf(
            ChatMessage("user", "Check temperature"),
            ChatMessage("assistant", "I will query the sensor"),
            ChatMessage("tool", "21.5 C"),
            ChatMessage("user", "What did the sensor say?")
        )
        val prompt = PromptBuilder.buildGemmaPrompt(messages)
        assertTrue(prompt.contains("[Tool Result]: 21.5 C"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun testOnlySystemMessage() {
        val messages = listOf(
            ChatMessage("system", "Be concise.")
        )
        val prompt = PromptBuilder.buildGemmaPrompt(messages)
        val expected = "<start_of_turn>user\nBe concise.<end_of_turn>\n<start_of_turn>model\n"
        assertEquals(expected, prompt)
    }
}
