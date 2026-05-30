package com.clawd.pet.tool

import org.json.JSONObject

/**
 * Registry of all tools available to the AI pet
 */
object ToolRegistry {

    val tools: List<Tool> by lazy { buildTools() }

    val toolSchemas: List<JSONObject> by lazy { tools.map { it.toFunctionJson() } }

    private fun buildTools(): List<Tool> = listOf(

        // 1. Web Search
        Tool(
            "web_search",
            "搜索互联网获取最新信息。可以搜索新闻、知识、天气、百科等任何内容。",
            Tool.objectSchema(
                JSONObject().apply {
                    put("query", Tool.stringParam("搜索关键词"))
                },
                listOf("query")
            )
        ),

        // 2. Get screen content
        Tool(
            "get_screen_content",
            "读取用户当前手机屏幕上的内容，包括正在使用的App和屏幕上的文字。",
            Tool.objectSchema(JSONObject())
        ),

        // 3. Open App
        Tool(
            "open_app",
            "打开手机上的应用程序。传入App名称（中文或英文）。",
            Tool.objectSchema(
                JSONObject().apply {
                    put("app_name", Tool.stringParam("要打开的App名称，如：微信、抖音、Chrome"))
                },
                listOf("app_name")
            )
        ),

        // 4. Set alarm
        Tool(
            "set_alarm",
            "设置闹钟提醒。传入时间和标签。",
            Tool.objectSchema(
                JSONObject().apply {
                    put("hour", Tool.intParam("小时（24小时制）"))
                    put("minute", Tool.intParam("分钟"))
                    put("label", Tool.stringParam("闹钟标签/说明"))
                },
                listOf("hour", "minute", "label")
            )
        ),

        // 5. Get clipboard
        Tool(
            "get_clipboard",
            "读取用户剪贴板中的文字内容。",
            Tool.objectSchema(JSONObject())
        ),

        // 6. Get battery
        Tool(
            "get_battery",
            "获取手机电量和充电状态。",
            Tool.objectSchema(JSONObject())
        ),

        // 7. Get time
        Tool(
            "get_current_time",
            "获取当前的日期和时间。",
            Tool.objectSchema(JSONObject())
        ),

        // 8. Calculator
        Tool(
            "calculate",
            "计算数学表达式。支持加减乘除、括号、幂运算等。",
            Tool.objectSchema(
                JSONObject().apply {
                    put("expression", Tool.stringParam("数学表达式，如：(3+5)*2/4"))
                },
                listOf("expression")
            )
        ),

        // 9. Get installed apps
        Tool(
            "get_installed_apps",
            "获取手机上已安装的应用列表（关键词搜索）。",
            Tool.objectSchema(
                JSONObject().apply {
                    put("keyword", Tool.stringParam("搜索关键词（可选，留空返回全部）"))
                }
            )
        ),

        // 10. Send notification
        Tool(
            "send_notification",
            "向用户发送一条通知提醒。",
            Tool.objectSchema(
                JSONObject().apply {
                    put("title", Tool.stringParam("通知标题"))
                    put("message", Tool.stringParam("通知内容"))
                },
                listOf("title", "message")
            )
        ),

        // 11. Get location (simplified - just IP based)
        Tool(
            "get_location_info",
            "获取用户的大致位置信息（基于网络）。",
            Tool.objectSchema(JSONObject())
        ),

        // 12. Text to speech
        Tool(
            "speak_text",
            "让小螃蟹用语音朗读一段文字。",
            Tool.objectSchema(
                JSONObject().apply {
                    put("text", Tool.stringParam("要朗读的文字内容"))
                },
                listOf("text")
            )
        )
    )

    fun getTool(name: String): Tool? = tools.find { it.name == name }

    fun getSystemToolsPrompt(): String {
        val toolList = tools.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}"
        }
        return """你可以使用以下工具来帮助用户：
$toolList

当你需要使用工具时，直接调用对应的函数。你可以同时调用多个工具。
如果用户的请求不需要工具，直接用语言回答。
回答时要像一只可爱的螃蟹宠物，用简短活泼的语言。"""
    }
}
