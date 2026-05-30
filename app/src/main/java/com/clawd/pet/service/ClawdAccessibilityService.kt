package com.clawd.pet.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ClawdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClawdA11y"
        var instance: ClawdAccessibilityService? = null; private set

        val appNames = mapOf(
            "com.tencent.mm" to "微信", "com.tencent.mobileqq" to "QQ",
            "com.eg.android.AlipayGphone" to "支付宝", "com.taobao.taobao" to "淘宝",
            "com.jingdong.app.mall" to "京东", "com.ss.android.ugc.aweme" to "抖音",
            "com.sina.weibo" to "微博", "com.zhihu.android" to "知乎",
            "com.baidu.searchbox" to "百度", "com.android.chrome" to "Chrome",
            "com.google.android.youtube" to "YouTube", "tv.danmaku.bili" to "B站",
            "com.netease.cloudmusic" to "网易云音乐", "com.kugou.android" to "酷狗音乐",
            "com.xingin.xhs" to "小红书", "com.ss.android.article.news" to "今日头条",
            "com.android.settings" to "设置", "com.android.camera" to "相机",
            "com.android.dialer" to "电话", "com.android.mms" to "短信",
            "com.miui.notes" to "便签", "com.miui.weather2" to "天气",
            "com.android.calculator2" to "计算器", "com.android.deskclock" to "时钟",
            "com.miui.securitycenter" to "安全中心", "com.android.fileexplorer" to "文件管理",
            "com.miui.home" to "桌面", "com.android.systemui" to "系统UI",
            "com.tencent.mm.plugin.finder" to "视频号", "com.tencent.wework" to "企业微信",
            "com.alibaba.android.rimet" to "钉钉", "com.microsoft.teams" to "Teams",
            "com.slack" to "Slack", "com.google.android.gm" to "Gmail",
            "com.android.email" to "邮件", "com.miui.gallery" to "相册",
            "com.android.vending" to "Play商店", "com.xiaomi.market" to "小米商店"
        )
    }

    var lastAppPackage = ""; private set
    var lastAppName = ""; private set
    var lastAppSwitchTime = 0L; private set

    // App usage tracking
    private val appUsage = mutableMapOf<String, Long>() // pkg -> total ms
    private var currentAppStart = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 500
        }
        Log.d(TAG, "Clawd accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != "com.clawd.pet" && pkg != "com.android.systemui") {
                // Track time on previous app
                if (lastAppPackage.isNotEmpty()) {
                    val elapsed = System.currentTimeMillis() - currentAppStart
                    appUsage[lastAppPackage] = (appUsage[lastAppPackage] ?: 0) + elapsed
                }
                lastAppPackage = pkg
                lastAppName = appNames[pkg] ?: pkg.substringAfterLast('.')
                lastAppSwitchTime = System.currentTimeMillis()
                currentAppStart = System.currentTimeMillis()
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy(); instance = null }

    /**
     * Capture full screen context for AI
     */
    fun captureScreenContext(maxChars: Int = 800): String {
        val sb = StringBuilder()
        sb.appendLine("📱 当前App: ${lastAppName.ifEmpty { "未知" }} ($lastAppPackage)")
        sb.appendLine("⏰ App使用时长: ${(System.currentTimeMillis() - currentAppStart) / 1000}秒")

        // Top 3 most used apps
        val topApps = appUsage.entries.sortedByDescending { it.value }.take(3)
        if (topApps.isNotEmpty()) {
            sb.append("📊 今日常用: ")
            sb.appendLine(topApps.joinToString(", ") { "${appNames[it.key] ?: it.key}(${it.value / 60000}分钟)" })
        }

        // Capture UI tree
        val root = rootInActiveWindow
        if (root != null) {
            sb.appendLine("--- 屏幕内容 ---")
            walkTree(root, sb, 0, 8, 0, 50)
        }

        val text = sb.toString().trim()
        return if (text.length > maxChars) text.take(maxChars) + "..." else text
    }

    /**
     * Quick app detection without full tree walk
     */
    fun getCurrentAppInfo(): String {
        return if (lastAppName.isNotEmpty()) {
            "正在使用: $lastAppName"
        } else {
            "未知应用"
        }
    }

    /**
     * Check if user is actively using phone
     */
    fun isUserActive(): Boolean {
        return System.currentTimeMillis() - lastAppSwitchTime < 120_000 // Active within 2 min
    }

    private fun walkTree(node: AccessibilityNodeInfo, sb: StringBuilder,
                         depth: Int, maxDepth: Int, count: Int, maxCount: Int): Int {
        if (depth > maxDepth || count >= maxCount) return count
        var c = count

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val cls = node.className?.toString()?.substringAfterLast('.') ?: ""

        if (text.isNotBlank() && text.length < 200 && text !in listOf("", " ", "搜索")) {
            val indent = "  ".repeat(minOf(depth, 4))
            sb.appendLine("$indent$text")
            c++
        } else if (desc.isNotBlank() && desc.length < 100) {
            val indent = "  ".repeat(minOf(depth, 4))
            sb.appendLine("$indent[$desc]")
            c++
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            c = walkTree(child, sb, depth + 1, maxDepth, c, maxCount)
            child.recycle()
        }
        return c
    }
}
