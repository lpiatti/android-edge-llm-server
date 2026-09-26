package com.edge.llm.server.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Conversions between kotlinx JSON trees and plain Kotlin values (Map/List/String/Number/Boolean),
 * the representation LiteRT-LM uses for tool-call arguments and tool responses.
 */
object JsonValues {

    fun toAny(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> element.mapValues { toAny(it.value) }
        is JsonArray -> element.map { toAny(it) }
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            element.longOrNull != null -> element.longOrNull
            else -> element.doubleOrNull ?: element.content
        }
    }

    fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> numberToElement(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toElement(v) })
        is Iterable<*> -> JsonArray(value.map { toElement(it) })
        is Array<*> -> JsonArray(value.map { toElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    /** Parses a JSON object string into a map; returns null if it is not a JSON object. */
    fun parseObject(json: String): Map<String, Any?>? = try {
        val element = Json.parseToJsonElement(json)
        if (element is JsonObject) element.mapValues { toAny(it.value) } else null
    } catch (e: Exception) {
        null
    }

    /** Parses any JSON value; returns null if [json] is not valid JSON. */
    fun parseOrNull(json: String): JsonElement? = try {
        Json.parseToJsonElement(json)
    } catch (e: Exception) {
        null
    }

    fun mapToJsonString(map: Map<String, Any?>): String = toElement(map).toString()

    // Numbers may arrive as Gson LazilyParsedNumber: keep integers integral.
    private fun numberToElement(n: Number): JsonElement {
        val text = n.toString()
        text.toLongOrNull()?.let { return JsonPrimitive(it) }
        val d = n.toDouble()
        if (!d.isNaN() && !d.isInfinite() && d == Math.floor(d) && kotlin.math.abs(d) < 9.0e15) {
            return JsonPrimitive(d.toLong())
        }
        return JsonPrimitive(d)
    }
}
