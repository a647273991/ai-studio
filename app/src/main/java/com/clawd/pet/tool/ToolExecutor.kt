package com.clawd.pet.tool

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.clawd.pet.service.ClawdAccessibilityService
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

class ToolExecutor(private val context: Context) {

    private val TAG = "ToolExecutor"
    private var tts: TextToSpeech? = null

    /**
     * Execute a tool call and return the result
     */
    fun execute(toolCall: ToolCall, callback: (ToolResult) -> Unit) {
        Log.d(TAG, "Executing: ${toolCall.name} with ${toolCall.arguments}")

        thread {
            try {
                val result = when (toolCall.name) {
                    "web_search" -> searchWeb(toolCall.arguments.optString("query", ""))
                    "get_screen_content" -> getScreenContent()
                    "open_app" -> openApp(toolCall.arguments.optString("app_name", ""))
                    "set_alarm" -> setAlarm(
                        toolCall.arguments.optInt("hour", 8),
                        toolCall.arguments.optInt("minute", 0),
                        toolCall.arguments.optString("label", "Clawd提醒")
                    )
                    "get_clipboard" -> getClipboard()
                    "get_battery" -> getBattery()
                    "get_current_time" -> getCurrentTime()
                    "calculate" -> calculate(toolCall.arguments.optString("expression", ""))
                    "get_installed_apps" -> getInstalledApps(toolCall.arguments.optString("keyword", ""))
                    "send_notification" -> sendNotification(
                        toolCall.arguments.optString("title", "Clawd"),
                        toolCall.arguments.optString("message", "")
                    )
                    "get_location_info" -> getLocationInfo()
                    "speak_text" -> speakText(toolCall.arguments.optString("text", ""))
                    else -> "未知工具: ${toolCall.name}"
                }
                callback(ToolResult(toolCall.id, toolCall.name, result))
            } catch (e: Exception) {
                Log.e(TAG, "Tool error: ${e.message}")
                callback(ToolResult(toolCall.id, toolCall.name, "执行出错: ${e.message}", isError = true))
            }
        }
    }

    // ==================== Tool Implementations ====================

    private fun searchWeb(query: String): String {
        if (query.isBlank()) return "请提供搜索关键词"

        // Use Brave Search API (no key needed for basic)
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://api.search.brave.com/res/v1/web/search?q=$encoded&count=5")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Accept-Encoding", "identity")
        conn.connectTimeout = 10000
        conn.readTimeout = 15000

        val code = conn.responseCode
        if (code !in 200..299) {
            // Fallback: use DuckDuckGo instant answer
            return searchDuckDuckGo(query)
        }

        val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        val json = JSONObject(text)
        val results = json.optJSONObject("web")?.optJSONArray("results") ?: return "搜索无结果"

        val sb = StringBuilder("搜索「$query」的结果：\n")
        for (i in 0 until minOf(results.length(), 5)) {
            val r = results.getJSONObject(i)
            sb.append("${i + 1}. ${r.optString("title", "")}\n")
            sb.append("   ${r.optString("description", "").take(100)}\n")
            sb.append("   ${r.optString("url", "")}\n\n")
        }
        return sb.toString().trim()
    }

    private fun searchDuckDuckGo(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "ClawdPet/1.0")

        val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        val json = JSONObject(text)

        val abstract = json.optString("AbstractText", "")
        if (abstract.isNotBlank()) {
            return "搜索「$query」：\n$abstract\n来源：${json.optString("AbstractURL", "")}"
        }

        val related = json.optJSONArray("RelatedTopics") ?: return "搜索无结果"
        val sb = StringBuilder("搜索「$query」：\n")
        for (i in 0 until minOf(related.length(), 5)) {
            val topic = related.optJSONObject(i) ?: continue
            val text2 = topic.optString("Text", "")
            if (text2.isNotBlank()) sb.append("- $text2\n")
        }
        return if (sb.length > 10) sb.toString().trim() else "搜索无结果"
    }

    private fun getScreenContent(): String {
        val a11y = ClawdAccessibilityService.instance
        return if (a11y != null) {
            a11y.captureScreen(800)
        } else {
            "无障碍服务未开启。请在设置→无障碍→Clawd桌宠中开启。"
        }
    }

    private fun openApp(appName: String): String {
        if (appName.isBlank()) return "请告诉我要打开什么App"

        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // Search by app name (Chinese or package name)
        val target = packages.find { pkg ->
            val label = pm.getApplicationLabel(pkg).toString()
            label.contains(appName, ignoreCase = true) ||
            pkg.packageName.contains(appName.lowercase().replace(" ", ""))
        }

        return if (target != null) {
            val launchIntent = pm.getLaunchIntentForPackage(target.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val label = pm.getApplicationLabel(target)
                "已打开 $label ✅"
            } else {
                "找到了 ${pm.getApplicationLabel(target)} 但无法启动"
            }
        } else {
            // Try common app name mappings
            val pkgMap = mapOf(
                "微信" to "com.tencent.mm", "qq" to "com.tencent.mobileqq",
                "抖音" to "com.ss.android.ugc.aweme", "支付宝" to "com.eg.android.AlipayGphone",
                "淘宝" to "com.taobao.taobao", "京东" to "com.jingdong.app.mall",
                "b站" to "tv.danmaku.bili", "bilibili" to "tv.danmaku.bili",
                "百度" to "com.baidu.searchbox", "高德" to "com.autonavi.minimap",
                "网易云" to "com.netease.cloudmusic", "知乎" to "com.zhihu.android",
                "小红书" to "com.xingin.xhs", "微博" to "com.sina.weibo",
                "设置" to "com.android.settings", "相机" to "com.android.camera",
                "chrome" to "com.android.chrome", "计算器" to "com.android.calculator2"
            )
            val pkg = pkgMap[appName.lowercase()]
            if (pkg != null) {
                try {
                    val intent = pm.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return "已打开 $appName ✅"
                    }
                } catch (_: Exception) {}
            }
            "找不到「$appName」这个App，试试其他名字？"
        }
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(AlarmManager.ACTION_SET_ALARM).apply {
            putExtra(AlarmManager.EXTRA_HOUR, hour)
            putExtra(AlarmManager.EXTRA_MINUTES, minute)
            putExtra(AlarmManager.EXTRA_MESSAGE, label)
            putExtra(AlarmManager.EXTRA_SKIP_UI, false)
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "已设置闹钟：${hour}点${String.format("%02d", minute)}分 - $label ⏰"
        } catch (e: Exception) {
            return "设置闹钟失败: ${e.message}"
        }
    }

    private fun getClipboard(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return if (cm.hasPrimaryClip()) {
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (text.isNotBlank()) "剪贴板内容：${text.take(500)}" else "剪贴板为空"
        } else {
            "剪贴板为空"
        }
    }

    private fun getBattery(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val chargingStr = if (isCharging) "正在充电 🔌" else "未充电"
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        return "电量: ${level}% $chargingStr\n电池温度: ${temp}°C"
    }

    private fun getCurrentTime(): String {
        val now = Calendar.getInstance()
        val dateFmt = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINESE)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return "当前时间：${dateFmt.format(now.time)} ${timeFmt.format(now.time)}"
    }

    private fun calculate(expression: String): String {
        if (expression.isBlank()) return "请提供要计算的表达式"
        return try {
            val result = evaluateExpression(expression)
            "$expression = $result"
        } catch (e: Exception) {
            "计算出错: ${e.message}"
        }
    }

    // Simple math expression evaluator
    private fun evaluateExpression(expr: String): Double {
        val clean = expr.replace(" ", "").replace("×", "*").replace("÷", "/").replace("（", "(").replace("）", ")")
        return object {
            var pos = 0
            fun parse(): Double {
                val result = parseTerm()
                if (pos < clean.length && clean[pos] == '+') { pos++; return result + parse() }
                if (pos < clean.length && clean[pos] == '-') { pos++; return result - parse() }
                return result
            }
            fun parseTerm(): Double {
                val result = parseFactor()
                if (pos < clean.length && clean[pos] == '*') { pos++; return result * parseTerm() }
                if (pos < clean.length && clean[pos] == '/') { pos++; return result / parseTerm() }
                return result
            }
            fun parseFactor(): Double {
                if (pos < clean.length && clean[pos] == '(') {
                    pos++; val result = parse(); pos++ // skip ')'
                    return result
                }
                val start = pos
                if (pos < clean.length && (clean[pos] == '-' || clean[pos] == '+')) pos++
                while (pos < clean.length && (clean[pos].isDigit() || clean[pos] == '.')) pos++
                return clean.substring(start, pos).toDouble()
            }
        }.parse()
    }

    private fun getInstalledApps(keyword: String): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { pm.getApplicationLabel(it).toString() to it.packageName }
            .let { list ->
                if (keyword.isNotBlank()) list.filter { it.first.contains(keyword, ignoreCase = true) || it.second.contains(keyword.lowercase()) }
                else list
            }
            .take(30)

        if (apps.isEmpty()) return if (keyword.isNotBlank()) "没有找到包含「$keyword」的应用" else "没有找到应用"

        val sb = StringBuilder("找到 ${apps.size} 个应用：\n")
        apps.forEach { (name, pkg) -> sb.append("  $name ($pkg)\n") }
        return sb.toString().trim()
    }

    private fun sendNotification(title: String, message: String): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("clawd_notify", "Clawd通知", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(context, "clawd_notify")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
        return "已发送通知: $title - $message ✅"
    }

    private fun getLocationInfo(): String {
        // Simple IP-based location (no GPS permission needed)
        return try {
            val url = URL("http://ip-api.com/json/?lang=zh-CN")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val json = JSONObject(text)
            if (json.optString("status") == "success") {
                "${json.optString("country", "")} ${json.optString("regionName", "")} ${json.optString("city", "")} (IP定位)"
            } else {
                "无法获取位置信息"
            }
        } catch (e: Exception) {
            "获取位置失败: ${e.message}"
        }
    }

    private fun speakText(text: String): String {
        if (text.isBlank()) return "没有要朗读的内容"
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINA
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clawd_tts")
                }
            }
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clawd_tts")
        }
        return "正在朗读: ${text.take(50)}..."
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
