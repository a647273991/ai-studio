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
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.clawd.pet.game.GameOutcome
import com.clawd.pet.game.GameRPS
import com.clawd.pet.model.ChatManager
import com.clawd.pet.model.EmotionManager
import kotlin.math.abs

class PetService : Service() {

    private var windowManager: WindowManager? = null
    private var container: FrameLayout? = null
    private var petView: ImageView? = null
    private var bubbleText: TextView? = null
    private var bubbleContainer: FrameLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var emotion: EmotionManager
    private lateinit var chat: ChatManager
    private lateinit var game: GameRPS
    private var isMenuShowing = false

    private val animations = mapOf(
        "idle" to R.raw.clawd_idle, "typing" to R.raw.clawd_typing,
        "thinking" to R.raw.clawd_thinking, "happy" to R.raw.clawd_happy,
        "sleeping" to R.raw.clawd_sleeping, "error" to R.raw.clawd_error,
        "sweeping" to R.raw.clawd_sweeping, "juggling" to R.raw.clawd_juggling,
        "building" to R.raw.clawd_building, "carrying" to R.raw.clawd_carrying,
        "conducting" to R.raw.clawd_conducting, "groove" to R.raw.clawd_groove,
        "bubble" to R.raw.clawd_bubble, "notification" to R.raw.clawd_notification,
        "debugger" to R.raw.clawd_debugger, "annoyed" to R.raw.clawd_annoyed
    )

    private var currentAnim = "idle"

    // Battery receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            if (plugged != 0 && currentAnim == "idle") {
                playAnim("happy")
                showBubble("充电中~ 嘻嘻⚡")
            }
            if (level < 15 && currentAnim == "idle") {
                playAnim("annoyed")
                showBubble("快没电了...爸爸救我！🔋")
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "clawd_pet"
        private const val NOTIFICATION_ID = 9999
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        emotion = EmotionManager(this)
        chat = ChatManager(this)
        game = GameRPS()
        emotion.onStateChanged = { updatePetState() }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        createFloatingWindow()
        emotion.start()
        autoBehavior()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("action")?.let { action ->
            when (action) {
                "chat_reply" -> {
                    val msg = intent.getStringExtra("message") ?: return@let
                    chat.sendMessage(msg) { reply ->
                        handler.post {
                            showBubble(reply)
                            playAnim("bubble")
                            emotion.pet()
                        }
                    }
                }
                "feed" -> { emotion.feed(); showBubble("好好吃！谢谢爸爸🍖"); playAnim("happy") }
                "game" -> {
                    val choice = intent.getStringExtra("choice") ?: return@let
                    val result = game.play(
                        com.clawd.pet.game.RPSChoice.valueOf(choice)
                    )
                    showBubble(result.message)
                    when (result.result) {
                        GameOutcome.WIN -> playAnim("error")
                        GameOutcome.LOSE -> playAnim("juggling")
                        GameOutcome.DRAW -> playAnim("happy")
                    }
                    emotion.play()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        emotion.stop()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        container?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
    }

    private fun updatePetState() {
        val mood = emotion.getCurrentMood()
        val autoAnim = when (mood) {
            com.clawd.pet.model.PetMood.EXHAUSTED -> "sleeping"
            com.clawd.pet.model.PetMood.HUNGRY -> "annoyed"
            com.clawd.pet.model.PetMood.LOVING -> "happy"
            com.clawd.pet.model.PetMood.HAPPY -> "juggling"
            else -> null
        }
        autoAnim?.let { if (currentAnim != "typing") playAnim(it) }
    }

    private fun autoBehavior() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isMenuShowing) { handler.postDelayed(this, 30_000); return }
                val hour = emotion.getHourOfDay()
                when {
                    hour in 0..5 -> { if (currentAnim != "sleeping") { playAnim("sleeping"); showBubble("zzZ...") } }
                    hour in 6..7 -> { if (currentAnim == "sleeping") { playAnim("idle"); showBubble("早安爸爸！☀️") } }
                    hour in 9..11 -> { playAnim(listOf("sweeping","building","carrying").random()); showBubble(emotion.getStatusMessage()) }
                    hour in 12..13 -> { playAnim("groove"); showBubble("午饭时间！🎵") }
                    hour in 14..17 -> { playAnim(listOf("typing","debugger","conducting").random()); showBubble(emotion.getStatusMessage()) }
                    hour in 18..21 -> { playAnim(listOf("idle","bubble","thinking").random()); showBubble(emotion.getStatusMessage()) }
                    hour in 22..23 -> { playAnim("sleeping"); showBubble("困了...晚安爸爸💤") }
                }
                handler.postDelayed(this, (60_000L * 3 + (Math.random() * 120_000).toLong()))
            }
        }, 10_000)
    }

    private fun playAnim(name: String) {
        val resId = animations[name] ?: return
        currentAnim = name
        try {
            Glide.with(this).asGif().load(resId).into(petView!!)
        } catch (_: Exception) {}
    }

    private fun showBubble(text: String, duration: Long = 4000) {
        handler.post {
            bubbleText?.text = text
            bubbleContainer?.visibility = View.VISIBLE
            handler.postDelayed({ bubbleContainer?.visibility = View.GONE }, duration)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        val density = dm.density
        val petSize = (140 * density).toInt()

        container = FrameLayout(this)

        // Bubble
        val bubbleView = createBubbleView()
        bubbleContainer = bubbleView
        bubbleView.visibility = View.GONE
        container?.addView(bubbleView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = (10 * density).toInt() })

        // Pet
        petView = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        container?.addView(petView, FrameLayout.LayoutParams(petSize, petSize).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        })

        playAnim("idle")
        showBubble("爸爸我在这！", 3000)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            petSize, (petSize * 1.6f).toInt(), layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dm.widthPixels - petSize - 10; y = 300 }

        // Touch
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var dragging = false; var t0 = 0L
        var doubleTapDetector = 0L

        container?.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = ev.rawX; ty = ev.rawY; dragging = false; t0 = System.currentTimeMillis(); true }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(ev.rawX - tx) > 10 || abs(ev.rawY - ty) > 10) dragging = true
                    if (dragging) {
                        params.x = ix + (ev.rawX - tx).toInt(); params.y = iy + (ev.rawY - ty).toInt()
                        try { windowManager?.updateViewLayout(container, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        val dur = System.currentTimeMillis() - t0
                        val now = System.currentTimeMillis()
                        if (now - doubleTapDetector < 300) {
                            // Double tap -> show menu
                            showQuickMenu()
                        } else if (dur > 500) {
                            // Long press -> pet/feed
                            emotion.feed()
                            showBubble(emotion.getStatusMessage())
                            playAnim("happy")
                        } else {
                            // Single tap -> chat or random action
                            doubleTapDetector = now
                            val act = listOf(
                                { showBubble(emotion.getStatusMessage()); emotion.pet() },
                                { showBubble(chat.isConfigured().let { if (it) "爸爸跟我聊天呀～\n点两下打开对话！" else "去设置里配API就能聊天啦～" }) },
                                { playAnim(listOf("juggling","conducting","groove","sweeping").random()); showBubble("嘿嘿看我的！") }
                            ).random()
                            act()
                        }
                    } else {
                        val mid = dm.widthPixels / 2
                        val targetX = if (params.x < mid) 0 else dm.widthPixels - petSize
                        val sx = params.x
                        for (i in 0..10) handler.postDelayed({
                            try { params.x = sx + (targetX - sx) * i / 10; windowManager?.updateViewLayout(container, params) } catch (_: Exception) {}
                        }, (i * 16).toLong())
                    }
                    true
                }
                else -> false
            }
        }
        windowManager?.addView(container, params)
    }

    private fun createBubbleView(): FrameLayout {
        val ctx = this
        val dp = resources.displayMetrics.density
        return FrameLayout(ctx).apply {
            setBackgroundResource(android.R.drawable.toast_frame)
            setPadding((12*dp).toInt(), (6*dp).toInt(), (12*dp).toInt(), (6*dp).toInt())
            addView(TextView(ctx).apply {
                textSize = 13f; setTextColor(0xFF333333.toInt())
                maxLines = 3; maxWidth = (180 * dp).toInt()
                bubbleText = this
            })
        }
    }

    private fun showQuickMenu() {
        // Open settings/chat via broadcast
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("show_menu", true)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦀 Clawd 桌宠")
            .setContentText(emotion.getDisplayText())
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Clawd 桌宠", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Clawd 像素桌宠"; setShowBadge(false) }
            )
        }
    }
}
