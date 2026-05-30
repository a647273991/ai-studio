package com.clawd.pet.tool

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool definition compatible with OpenAI function calling format
 */
data class Tool(
    val name: String,
    val description: String,
    val parameters: JSONObject // JSON Schema
) {
    fun toFunctionJson(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", parameters)
            })
        }
    }

    companion object {
        fun param(type: String, description: String, required: Boolean = false): JSONObject {
            return JSONObject().apply {
                put("type", type)
                put("description", description)
            }
        }

        fun stringParam(description: String) = param("string", description)
        fun intParam(description: String) = param("integer", description)
        fun boolParam(description: String) = param("boolean", description)

        fun objectSchema(
            properties: JSONObject,
            required: List<String> = emptyList()
        ): JSONObject {
            return JSONObject().apply {
                put("type", "object")
                put("properties", properties)
                if (required.isNotEmpty()) {
                    put("required", JSONArray(required))
                }
            }
        }
    }
}

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject
)

data class ToolResult(
    val toolCallId: String,
    val name: String,
    val result: String,
    val isError: Boolean = false
)
