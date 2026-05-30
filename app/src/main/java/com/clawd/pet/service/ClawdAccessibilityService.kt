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
            "com.netease.cloudmusic" to "网易云音乐", "com.xingin.xhs" to "小红书",
            "com.ss.android.article.news" to "头条", "com.android.settings" to "设置",
            "com.android.camera" to "相机", "com.android.dialer" to "电话",
            "com.android.mms" to "短信", "com.miui.notes" to "便签",
            "com.miui.weather2" to "天气", "com.android.calculator2" to "计算器"
        )
    }

    var lastAppPackage = ""; private set
    var lastAppName = ""; private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 1000
        }
        Log.d(TAG, "Clawd a11y connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != "com.clawd.pet" && pkg != "com.android.systemui") {
                lastAppPackage = pkg
                lastAppName = appNames[pkg] ?: pkg.substringAfterLast('.')
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy(); instance = null }

    /**
     * Capture screen UI tree - extract text content
     */
    fun captureScreen(maxChars: Int = 600): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        sb.append("📱 正在使用: $lastAppName\n")
        walkTree(root, sb, 0, 6, 0, 40)
        val text = sb.toString().trim()
        return if (text.length > maxChars) text.take(maxChars) + "..." else text
    }

    private fun walkTree(node: AccessibilityNodeInfo, sb: StringBuilder,
                         depth: Int, maxDepth: Int, count: Int, maxCount: Int): Int {
        if (depth > maxDepth || count >= maxCount) return count
        var c = count
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val cls = node.className?.toString()?.substringAfterLast('.') ?: ""

        if (text.isNotBlank() && text.length < 150 && text !in listOf("", " ")) {
            sb.append("[$cls] $text\n")
            c++
        } else if (desc.isNotBlank() && desc.length < 80) {
            sb.append("($desc)\n")
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
