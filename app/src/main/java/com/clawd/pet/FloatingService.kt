package com.clawd.pet

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var container: FrameLayout? = null
    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "ClawdPet"

    companion object {
        private const val CHANNEL_ID = "clawd_pet_channel"
        private const val NOTIFICATION_ID = 9999
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingService onCreate")
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            createFloatingWindow()
            Log.d(TAG, "FloatingService started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Clawd 启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingService onDestroy")
        try {
            webView?.apply {
                stopLoading()
                destroy()
            }
            container?.let {
                try { windowManager?.removeView(it) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
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
            .setContentText("点击通知可返回应用")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Get screen size for initial positioning
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val petSize = (180 * dm.density).toInt() // 180dp

        // Create container
        container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Create WebView with Application context to avoid Service context issues
        webView = WebView(applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "WebView page loaded: $url")
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Log.e(TAG, "WebView error: $errorCode $description at $failingUrl")
                }
            }
            webChromeClient = WebChromeClient()

            Log.d(TAG, "WebView created, loading HTML...")
            loadUrl("file:///android_asset/clawd-pet.html")
        }

        container?.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Window params
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            petSize,
            petSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - petSize - 20
            y = 300
        }

        // Touch handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var touchStartTime = 0L

        container?.setOnTouchListener { _, event ->
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
                        try { windowManager?.updateViewLayout(container, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val duration = System.currentTimeMillis() - touchStartTime
                        if (duration > 500) {
                            webView?.evaluateJavascript("setState('sleeping');", null)
                        } else {
                            webView?.evaluateJavascript("nextState();", null)
                        }
                    } else {
                        // Snap to nearest edge
                        val midScreen = dm.widthPixels / 2
                        val targetX = if (params.x < midScreen) 0 else dm.widthPixels - petSize
                        val startX = params.x
                        for (i in 0..10) {
                            handler.postDelayed({
                                try {
                                    params.x = startX + (targetX - startX) * i / 10
                                    windowManager?.updateViewLayout(container, params)
                                } catch (_: Exception) {}
                            }, (i * 16).toLong())
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(container, params)
            Log.d(TAG, "Floating view added to window manager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view: ${e.message}", e)
            throw e
        }
    }
}
