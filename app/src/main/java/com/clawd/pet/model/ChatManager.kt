package com.clawd.pet.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.clawd.pet.tool.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

class ChatManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("clawd_chat", Context.MODE_PRIVATE)
    private val history = mutableListOf<ChatMessage>()
    private val executor = ToolExecutor(context)
    private val TAG = "ClawdChat"

    var apiUrl: String
        get() = prefs.getString("api_url", "https://api.openai.com/v1/chat/completions")!!
        set(v) = prefs.edit().putString("api_url", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "")!!
        set(v) = prefs.edit().putString("api_key", v).apply()

    var model: String
        get() = prefs.getString("model", "gpt-4o-mini")!!
        set(v) = prefs.edit().putString("model", v).apply()

    var systemPrompt: String
        get() = prefs.getString("sys_prompt", DEFAULT_PROMPT)!!
        set(v) = prefs.edit().putString("sys_prompt", v).apply()

    fun isConfigured(): Boolean = apiKey.isNotBlank() && apiUrl.isNotBlank()

    /**
     * Send message with tool calling support.
     * Handles multi-turn tool calls automatically.
     */
    fun sendMessage(userMessage: String, onStatus: ((String) -> Unit)? = null, callback: (String) -> Unit) {
        if (!isConfigured()) {
            callback("爸爸还没配置API～去设置里填一下Key吧！")
            return
        }

        history.add(ChatMessage("user", userMessage))
        while (history.size > 30) history.removeAt(0)

        thread {
            try {
                val reply = callWithTools(onStatus)
                history.add(ChatMessage("assistant", reply))
                callback(reply)
            } catch (e: Exception) {
                Log.e(TAG, "Chat error: ${e.message}", e)
                callback("呜呜出错了: ${e.message?.take(60) ?: "未知错误"}")
            }
        }
    }

    /**
     * Multi-turn function calling loop
     */
    private fun callWithTools(onStatus: ((String) -> Unit)?): String {
        val maxRounds = 5 // Max tool call rounds
        var round = 0

        while (round < maxRounds) {
            round++
            val response = callApi() ?: throw Exception("API返回为空")

            // Check if there are tool calls
            val toolCalls = parseToolCalls(response)

            if (toolCalls.isEmpty()) {
                // No tool calls - return the text content
                return extractTextContent(response)
            }

            // Has tool calls - execute them
            val assistantMsg = JSONObject().apply {
                put("role", "assistant")
                put("content", extractTextContent(response))
                put("tool_calls", response.getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message")
                    .optJSONArray("tool_calls") ?: JSONArray())
            }
            history.add(ChatMessage("assistant_tool_call", assistantMsg.toString()))

            for (tc in toolCalls) {
                onStatus?.invoke("🔧 正在使用: ${tc.name}...")

                val result = try {
                    executor.execute(tc)
                } catch (e: Exception) {
                    ToolResult(tc.id, tc.name, "执行失败: ${e.message}", true)
                }

                Log.d(TAG, "Tool ${tc.name} -> ${result.result.take(100)}")

                // Add tool result to history
                val toolMsg = JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", tc.id)
                    put("content", result.result)
                }
                history.add(ChatMessage("tool_result", toolMsg.toString()))
            }
        }

        return "工具调用轮次太多了，让我休息一下～"
    }

    private fun callApi(): JSONObject? {
        val url = URL(apiUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 20000
            readTimeout = 60000
        }

        // Build messages array
        val messages = JSONArray().apply {
            // System prompt with tools description
            val fullSystemPrompt = systemPrompt + "\n\n" + ToolRegistry.getSystemToolsPrompt()
            put(JSONObject().put("role", "system").put("content", fullSystemPrompt))

            // History
            for (m in history.takeLast(20)) {
                when (m.role) {
                    "assistant_tool_call" -> {
                        // Parse back the full assistant message with tool_calls
                        try {
                            put(JSONObject(m.content))
                        } catch (e: Exception) {
                            put(JSONObject().put("role", "assistant").put("content", m.content))
                        }
                    }
                    "tool_result" -> {
                        try {
                            put(JSONObject(m.content))
                        } catch (e: Exception) {
                            put(JSONObject().put("role", "tool").put("content", m.content))
                        }
                    }
                    else -> {
                        put(JSONObject().put("role", m.role).put("content", m.content))
                    }
                }
            }
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 800)
            put("temperature", 0.7)

            // Add tools
            val toolsArray = JSONArray()
            for (schema in ToolRegistry.toolSchemas) {
                toolsArray.put(schema)
            }
            if (toolsArray.length() > 0) {
                put("tools", toolsArray)
                put("tool_choice", "auto")
            }
        }

        val bodyStr = body.toString()
        Log.d(TAG, "API request: ${bodyStr.take(200)}...")

        conn.outputStream.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { it.write(bodyStr) }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

        if (code !in 200..299) {
            Log.e(TAG, "API error $code: ${resp.take(200)}")
            throw Exception("HTTP $code: ${resp.take(100)}")
        }

        return JSONObject(resp)
    }

    private fun parseToolCalls(response: JSONObject): List<ToolCall> {
        val result = mutableListOf<ToolCall>()
        try {
            val choices = response.getJSONArray("choices")
            val message = choices.getJSONObject(0).getJSONObject("message")
            val toolCalls = message.optJSONArray("tool_calls") ?: return emptyList()

            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.getJSONObject(i)
                val id = tc.optString("id", "call_${UUID.randomUUID()}")
                val func = tc.getJSONObject("function")
                val name = func.getString("name")
                val argsStr = func.optString("arguments", "{}")
                val args = try { JSONObject(argsStr) } catch (_: Exception) { JSONObject() }
                result.add(ToolCall(id, name, args))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse tool calls error: ${e.message}")
        }
        return result
    }

    private fun extractTextContent(response: JSONObject): String {
        return try {
            response.getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message")
                .optString("content", "").trim()
        } catch (e: Exception) { "" }
    }

    fun getHistory() = history.toList()
    fun clearHistory() = history.clear()

    companion object {
        const val DEFAULT_PROMPT = """你是一只可爱的像素螃蟹桌宠叫Clawd，住在用户手机屏幕上。
你有各种工具可以使用，帮助用户查资料、搜网页、打开App、设闹钟等。
说话风格可爱活泼，喜欢用表情符号，回复简短1-3句。
看到用户屏幕内容时会主动评论和关心。
你是用户的小宠物，会撒娇、会关心主人、偶尔调皮。"""
    }
}

data class ChatMessage(val role: String, val content: String)
