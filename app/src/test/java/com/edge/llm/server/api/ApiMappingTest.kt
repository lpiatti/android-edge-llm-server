package com.edge.llm.server.api

import com.edge.llm.server.model.InvalidRequestException
import com.edge.llm.server.model.JsonValues
import com.edge.llm.server.model.ToolCallData
import com.edge.llm.server.model.TurnRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiMappingTest {

    private fun openAi(json: String) = OpenAiMapping.toGenerationRequest(ApiJson.decodeFromString(OaiChatRequest.serializer(), json))
    private fun ollama(json: String) = OllamaMapping.toGenerationRequest(ApiJson.decodeFromString(OllamaChatRequest.serializer(), json))

    @Test
    fun openAiContentArrayIsConcatenated() {
        val gen = openAi(
            """{"model":"m","messages":[{"role":"user","content":[{"type":"text","text":"Hello "},{"type":"text","text":"world"}]}]}"""
        )
        assertEquals("Hello world", gen.messages.single().text)
    }

    @Test(expected = InvalidRequestException::class)
    fun openAiImagePartIsRejected() {
        openAi("""{"messages":[{"role":"user","content":[{"type":"image_url","image_url":{"url":"x"}}]}]}""")
    }

    @Test
    fun openAiParametersAreMapped() {
        val gen = openAi(
            """{"messages":[{"role":"developer","content":"sys"},{"role":"user","content":"hi"}],
               "max_tokens":10,"max_completion_tokens":20,"stop":"END","temperature":0.2,"top_p":0.9,"seed":7,
               "response_format":{"type":"json_object"}}"""
        )
        assertEquals(TurnRole.SYSTEM, gen.messages.first().role)
        assertEquals(20, gen.maxTokens)
        assertEquals(listOf("END"), gen.stop)
        assertEquals(0.2, gen.temperature!!, 1e-9)
        assertEquals(7, gen.seed)
        assertEquals(OpenAiMapping.ANY_JSON_OBJECT, gen.jsonSchema)
    }

    @Test
    fun openAiToolHistoryResolvesToolNamesById() {
        val gen = openAi(
            """{"messages":[
                {"role":"user","content":"weather in Rome?"},
                {"role":"assistant","content":null,"tool_calls":[{"id":"call_9","type":"function","function":{"name":"get_weather","arguments":"{\"city\":\"Rome\"}"}}]},
                {"role":"tool","tool_call_id":"call_9","content":"{\"temp\":21}"}],
               "tools":[{"type":"function","function":{"name":"get_weather","description":"Weather","parameters":{"type":"object","properties":{"city":{"type":"string"}}}}}]}"""
        )
        val assistant = gen.messages[1]
        assertEquals("get_weather", assistant.toolCalls.single().name)
        assertEquals("get_weather", gen.messages[2].toolName)
        assertEquals(1, gen.tools.size)
        assertTrue(gen.tools.single().parametersJson!!.contains("city"))
    }

    @Test
    fun toolChoiceNoneDropsTools() {
        val gen = openAi(
            """{"messages":[{"role":"user","content":"x"}],"tool_choice":"none",
               "tools":[{"type":"function","function":{"name":"f"}}]}"""
        )
        assertTrue(gen.tools.isEmpty())
    }

    @Test(expected = InvalidRequestException::class)
    fun nGreaterThanOneIsRejected() {
        openAi("""{"messages":[{"role":"user","content":"x"}],"n":2}""")
    }

    @Test
    fun openAiResponseSerializesToolCallsWithoutNullContent() {
        val response = OaiChatResponse(
            id = "id", created = 1, model = "m",
            choices = listOf(
                OaiChoice(
                    message = OaiResponseMessage(content = null, tool_calls = OpenAiMapping.toOaiToolCalls(listOf(ToolCallData("call_1", "f", "{}")), false)),
                    finish_reason = "tool_calls"
                )
            ),
            usage = OaiUsage(1, 1, 2)
        )
        val json = Json.parseToJsonElement(ApiJson.encodeToString(OaiChatResponse.serializer(), response)).jsonObject
        assertEquals("chat.completion", json["object"]!!.jsonPrimitive.content)
        val message = json["choices"]!!.let { (it as kotlinx.serialization.json.JsonArray)[0].jsonObject["message"]!!.jsonObject }
        assertFalse(message.containsKey("content"))
        val call = (message["tool_calls"] as kotlinx.serialization.json.JsonArray)[0].jsonObject
        assertEquals("function", call["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun ollamaStreamsByDefaultAndMapsOptions() {
        val req = ApiJson.decodeFromString(
            OllamaChatRequest.serializer(),
            """{"model":"m","messages":[{"role":"user","content":"hi"}],"options":{"num_predict":-1,"stop":["\n"],"top_k":20},"format":"json"}"""
        )
        assertTrue(req.stream != false)
        val gen = OllamaMapping.toGenerationRequest(req)
        assertNull(gen.maxTokens)
        assertEquals(listOf("\n"), gen.stop)
        assertEquals(20, gen.topK)
        assertEquals(OpenAiMapping.ANY_JSON_OBJECT, gen.jsonSchema)
    }

    @Test
    fun ollamaToolRoundTripUsesArgumentObjects() {
        val gen = ollama(
            """{"messages":[
                {"role":"user","content":"weather?"},
                {"role":"assistant","content":"","tool_calls":[{"function":{"name":"get_weather","arguments":{"city":"Rome"}}}]},
                {"role":"tool","content":"sunny"}]}"""
        )
        assertEquals("{\"city\":\"Rome\"}", gen.messages[1].toolCalls.single().argumentsJson)
        assertEquals("get_weather", gen.messages[2].toolName)

        val out = OllamaMapping.toOllamaToolCalls(listOf(ToolCallData("c", "get_weather", "{\"city\":\"Rome\"}")))
        assertTrue(out.single().function.arguments is JsonObject)
    }

    @Test
    fun jsonValuesKeepIntegersIntegral() {
        val map = JsonValues.parseObject("""{"n":3,"x":1.5,"s":"a","b":true,"l":[1,2],"o":{"k":null}}""")!!
        assertEquals(3L, map["n"])
        assertEquals("""{"n":3,"x":1.5,"s":"a","b":true,"l":[1,2],"o":{"k":null}}""", JsonValues.mapToJsonString(map))
        assertNull(JsonValues.parseObject("[1]"))
    }
}
