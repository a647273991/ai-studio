package com.clawd.pet.model

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class ChatManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("clawd_chat", Context.MODE_PRIVATE)
    private val history = mutableListOf<ChatMessage>()

    var apiUrl: String
        get() = prefs.getString("api_url", "https://api.openai.com/v1/chat/completions") ?: ""
        set(value) = prefs.edit().putString("api_url", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var model: String
        get() = prefs.getString("model", "gpt-4o-mini") ?: ""
        set(value) = prefs.edit().putString("model", value).apply()

    var systemPrompt: String
        get() = prefs.getString("system_prompt",
            "你是一只可爱的像素螃蟹桌宠，名叫 Clawd。你正在用户的手机屏幕上生活。" +
            "说话风格可爱活泼，喜欢用表情符号。回复简短，一般1-3句话。" +
            "你是用户的小宠物，会撒娇、会关心主人、偶尔调皮。"
        ) ?: ""
        set(value) = prefs.edit().putString("system_prompt", value).apply()

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    fun sendMessage(userMessage: String, callback: (String) -> Unit) {
        if (!isConfigured()) {
            callback("爸爸还没配置 API 呀～去设置里填一下 API Key 吧！")
            return
        }

        history.add(ChatMessage("user", userMessage))
        // Keep last 20 messages
        while (history.size > 20) history.removeAt(0)

        thread {
            try {
                val response = callApi(userMessage)
                history.add(ChatMessage("assistant", response))
                callback(response)
            } catch (e: Exception) {
                callback("呜呜...网络出问题了: ${e.message?.take(30)}")
            }
        }
    }

    private fun callApi(userMessage: String): String {
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            for (msg in history.takeLast(10)) {
                put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 200)
            put("temperature", 0.8)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val reader = BufferedReader(InputStreamReader(
            if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        ))
        val response = reader.readText()
        reader.close()

        if (conn.responseCode !in 200..299) {
            throw Exception("API ${conn.responseCode}")
        }

        val json = JSONObject(response)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    fun getHistory(): List<ChatMessage> = history.toList()
    fun clearHistory() { history.clear() }
}

data class ChatMessage(val role: String, val content: String)
