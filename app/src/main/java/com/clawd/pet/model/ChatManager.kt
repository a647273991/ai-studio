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
import java.net.URLEncoder
import kotlin.concurrent.thread

class ChatManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("clawd_chat", Context.MODE_PRIVATE)
    private val history = mutableListOf<ChatMessage>()

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
        get() = prefs.getString("sys_prompt",
            "你是一只可爱的像素螃蟹桌宠叫Clawd，住在用户手机屏幕上。说话可爱活泼，用表情符号，回复简短1-3句。"
        )!!
        set(v) = prefs.edit().putString("sys_prompt", v).apply()

    fun isConfigured(): Boolean = apiKey.isNotBlank() && apiUrl.isNotBlank()

    fun sendMessage(msg: String, cb: (String) -> Unit) {
        if (!isConfigured()) { cb("爸爸还没配置API～去设置里填一下吧！"); return }
        history.add(ChatMessage("user", msg))
        while (history.size > 20) history.removeAt(0)
        thread {
            try {
                val reply = callApi()
                history.add(ChatMessage("assistant", reply))
                cb(reply)
            } catch (e: Exception) {
                cb("呜呜出错了: ${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    private fun callApi(): String {
        val urlObj = URL(apiUrl)
        val conn = (urlObj.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            for (m in history.takeLast(10)) {
                put(JSONObject().put("role", m.role).put("content", m.content))
            }
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 200)
            put("temperature", 0.8)
        }

        val bodyStr = body.toString()
        conn.outputStream.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { it.write(bodyStr) }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

        if (code !in 200..299) {
            throw Exception("HTTP $code: ${resp.take(100)}")
        }

        return try {
            JSONObject(resp).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message")
                .getString("content").trim()
        } catch (e: Exception) {
            throw Exception("解析失败: ${resp.take(80)}")
        }
    }

    fun getHistory() = history.toList()
    fun clearHistory() = history.clear()
}

data class ChatMessage(val role: String, val content: String)
