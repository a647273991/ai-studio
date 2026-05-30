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
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import kotlin.math.abs

class FloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var container: FrameLayout? = null
    private var petView: ImageView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "ClawdPet"

    // All available animations
    private val animations = listOf(
        R.raw.clawd_idle,
        R.raw.clawd_typing,
        R.raw.clawd_thinking,
        R.raw.clawd_happy,
        R.raw.clawd_sleeping,
        R.raw.clawd_error,
        R.raw.clawd_sweeping,
        R.raw.clawd_juggling,
        R.raw.clawd_building,
        R.raw.clawd_carrying,
        R.raw.clawd_conducting,
        R.raw.clawd_groove,
        R.raw.clawd_bubble,
        R.raw.clawd_notification,
        R.raw.clawd_debugger,
        R.raw.clawd_annoyed
    )
    private var currentAnimIndex = 0

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
        try {
            container?.let {
                try { windowManager?.removeView(it) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Clawd 桌宠", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Clawd 像素桌宠正在运行"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦀 Clawd 桌宠")
            .setContentText("拖拽移动 · 点击互动 · 长按睡觉")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val dm = resources.displayMetrics
        val petSize = (160 * dm.density).toInt() // 160dp square

        // Container
        container = FrameLayout(this)

        // ImageView for the pet GIF
        petView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0x00000000) // fully transparent
        }
        container?.addView(petView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Load initial GIF
        loadAnim(animations[0])

        // Window params
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            petSize, petSize, layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - petSize - 20
            y = 400
        }

        // Touch handling
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isDragging = false; var touchStartTime = 0L

        container?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isDragging = false; touchStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX; val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx.toInt(); params.y = initialY + dy.toInt()
                        try { windowManager?.updateViewLayout(container, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val duration = System.currentTimeMillis() - touchStartTime
                        if (duration > 500) {
                            // Long press -> sleep
                            loadAnim(R.raw.clawd_sleeping)
                        } else {
                            // Tap -> next random animation
                            currentAnimIndex = (currentAnimIndex + 1) % animations.size
                            loadAnim(animations[currentAnimIndex])
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

        windowManager?.addView(container, params)
        Log.d(TAG, "Floating view added, size=${petSize}px")
    }

    private fun loadAnim(resId: Int) {
        Glide.with(this)
            .asGif()
            .load(resId)
            .into(petView!!)
    }
}
