package com.clawd.pet

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    companion object {
        private const val CHANNEL_ID = "clawd_pet_channel"
        private const val NOTIFICATION_ID = 9999
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        webView?.destroy()
        floatingView?.let { windowManager?.removeView(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clawd 桌宠",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Clawd 像素桌宠正在运行"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦀 Clawd 桌宠运行中")
            .setContentText("点击通知可返回应用控制")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create WebView programmatically
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            // Load the embedded HTML from assets
            loadUrl("file:///android_asset/clawd-pet.html")
        }

        floatingView = webView

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // Touch handling for drag & tap
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var touchStartTime = 0L

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    touchStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val duration = System.currentTimeMillis() - touchStartTime
                        if (duration > 500) {
                            // Long press -> sleep
                            webView?.evaluateJavascript("setState('sleeping');", null)
                        } else {
                            // Tap -> random state change
                            webView?.evaluateJavascript("nextState();", null)
                        }
                    } else {
                        // Snap to nearest edge after drag
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val midScreen = screenWidth / 2
                        val targetX = if (params.x < midScreen) 0 else screenWidth - 200
                        // Animate to edge
                        val startX = params.x
                        val steps = 10
                        for (i in 0..steps) {
                            handler.postDelayed({
                                params.x = startX + (targetX - startX) * i / steps
                                try { windowManager?.updateViewLayout(floatingView, params) } catch (_: Exception) {}
                            }, (i * 16).toLong())
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, params)
            isRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
