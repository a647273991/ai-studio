package com.clawd.pet

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.clawd.pet.game.GameRPS
import com.clawd.pet.game.RPSChoice
import com.clawd.pet.model.ChatManager
import com.clawd.pet.model.EmotionManager

class PetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var emotion: EmotionManager
    private lateinit var chat: ChatManager
    private lateinit var game: GameRPS
    private val handler = Handler(Looper.getMainLooper())

    private var petView: ImageView? = null
    private var statusText: TextView? = null
    private var petContainer: LinearLayout? = null

    private var currentAnimRes = R.raw.clawd_idle
    private var isCharging = false
    private var batteryLevel = 100

    private val animations = listOf(
        R.raw.clawd_idle, R.raw.clawd_typing, R.raw.clawd_thinking,
        R.raw.clawd_happy, R.raw.clawd_sweeping, R.raw.clawd_juggling,
        R.raw.clawd_building, R.raw.clawd_carrying, R.raw.clawd_conducting,
        R.raw.clawd_groove, R.raw.clawd_bubble, R.raw.clawd_notification,
        R.raw.clawd_debugger, R.raw.clawd_annoyed
    )
    private var animIndex = 0

    // Auto behavior: switch animation every 20s
    private val autoBehavior = object : Runnable {
        override fun run() {
            if (!emotion.isSleepTime()) {
                val hour = emotion.getHourOfDay()
                val pick = when {
                    isCharging -> R.raw.clawd_happy
                    batteryLevel < 20 -> R.raw.clawd_error
                    hour in 9..11 -> R.raw.clawd_typing
                    hour in 12..13 -> R.raw.clawd_sweeping
                    hour in 14..17 -> animations.random()
                    hour in 18..21 -> R.raw.clawd_groove
                    else -> R.raw.clawd_idle
                }
                loadAnim(pick)
                updateStatusBubble()
            }
            handler.postDelayed(this, 20_000)
        }
    }

    companion object {
        private const val CHANNEL_ID = "clawd_pet"
        private const val NOTIFICATION_ID = 8888
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        emotion = EmotionManager(this)
        chat = ChatManager(this)
        game = GameRPS()
        emotion.start()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        createPetWindow()
        registerBatteryReceiver()
        handler.postDelayed(autoBehavior, 20_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.getStringExtra("action")) {
                "game" -> {
                    val choice = try { RPSChoice.valueOf(it.getStringExtra("choice") ?: "ROCK") } catch (_: Exception) { RPSChoice.ROCK }
                    val result = game.play(choice)
                    showBubble(result.message)
                    emotion.play()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        emotion.stop()
        handler.removeCallbacks(autoBehavior)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        petContainer?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun createPetWindow() {
        val dm = resources.displayMetrics
        val petSize = dp(120) // 120dp pet
        val bubbleHeight = dp(36)
        val totalHeight = petSize + bubbleHeight + dp(4)

        // Layout type
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            dp(160), totalHeight, layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - dp(170)
            y = dp(200)
        }

        // Container: vertical, bubble on top, pet below
        petContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Status bubble (above pet)
        statusText = TextView(this).apply {
            text = emotion.getStatusMessage()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val bg = GradientDrawable().apply {
                setColor(0xCC222244.toInt())
                cornerRadius = dp(12).toFloat()
            }
            background = bg
            maxLines = 2
        }
        petContainer?.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(4)
        })

        // Pet image
        petView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        petContainer?.addView(petView, LinearLayout.LayoutParams(petSize, petSize))

        // Touch handling on petView only (not entire container)
        var initX = 0; var initY = 0; var initTX = 0f; var initTY = 0f
        var dragging = false; var touchStart = 0L

        petView?.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    initTX = ev.rawX; initTY = ev.rawY
                    dragging = false; touchStart = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - initTX; val dy = ev.rawY - initTY
                    if (dx * dx + dy * dy > 100) dragging = true
                    if (dragging) {
                        params.x = initX + dx.toInt(); params.y = initY + dy.toInt()
                        try { windowManager.updateViewLayout(petContainer, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        val dur = System.currentTimeMillis() - touchStart
                        if (dur > 500) {
                            // Long press: feed
                            emotion.feed()
                            loadAnim(R.raw.clawd_happy)
                            showBubble("🍖 好吃！谢谢爸爸！")
                        } else {
                            // Single tap: show menu
                            showMenu()
                        }
                    } else {
                        // Snap to edge
                        val targetX = if (params.x < dm.widthPixels / 2) 0 else dm.widthPixels - params.width
                        val sx = params.x
                        for (i in 0..10) handler.postDelayed({
                            try {
                                params.x = sx + (targetX - sx) * i / 10
                                windowManager.updateViewLayout(petContainer, params)
                            } catch (_: Exception) {}
                        }, (i * 16).toLong())
                    }
                    true
                }
                else -> false
            }
        }

        loadAnim(R.raw.clawd_idle)
        windowManager.addView(petContainer, params)
    }

    private fun loadAnim(resId: Int) {
        currentAnimRes = resId
        petView?.let {
            Glide.with(applicationContext).asGif().load(resId).into(it)
        }
    }

    private fun showBubble(message: String, duration: Long = 3000) {
        statusText?.text = message
        statusText?.visibility = View.VISIBLE
        handler.postDelayed({ updateStatusBubble() }, duration)
    }

    private fun updateStatusBubble() {
        statusText?.text = if (emotion.isSleepTime()) "💤 zzZ..." else emotion.getStatusMessage()
    }

    private fun showMenu() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("show_menu", true)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦀 Clawd 运行中")
            .setContentText(emotion.getDisplayText())
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Clawd 桌宠", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            isCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ==
                    BatteryManager.BATTERY_STATUS_CHARGING
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        // Get initial state
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let {
            isCharging = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            batteryLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        }
    }
}
