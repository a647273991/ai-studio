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
import android.text.InputType
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.clawd.pet.game.GameRPS
import com.clawd.pet.game.RPSChoice
import com.clawd.pet.model.ChatManager
import com.clawd.pet.model.EmotionManager
import com.clawd.pet.service.ClawdAccessibilityService

class PetService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var emotion: EmotionManager
    private lateinit var chat: ChatManager
    private lateinit var game: GameRPS
    private val handler = Handler(Looper.getMainLooper())

    // Views
    private var petView: ImageView? = null
    private var bubbleText: TextView? = null
    private var chatPanel: LinearLayout? = null
    private var chatInput: EditText? = null
    private var chatLog: TextView? = null
    private var container: LinearLayout? = null

    // State
    private var isCharging = false
    private var batteryLevel = 100
    private var isChatVisible = false
    private var currentAnimRes = R.raw.clawd_idle
    private var pendingUserMessage: String? = null

    private val animations = listOf(
        R.raw.clawd_idle, R.raw.clawd_typing, R.raw.clawd_thinking,
        R.raw.clawd_happy, R.raw.clawd_sweeping, R.raw.clawd_juggling,
        R.raw.clawd_building, R.raw.clawd_carrying, R.raw.clawd_conducting,
        R.raw.clawd_groove, R.raw.clawd_bubble, R.raw.clawd_notification,
        R.raw.clawd_debugger, R.raw.clawd_annoyed
    )

    // Auto screen monitoring - every 45s
    private val screenMonitor = object : Runnable {
        override fun run() {
            val a11y = ClawdAccessibilityService.instance
            if (a11y != null && chat.isConfigured() && a11y.isUserActive()) {
                val screenCtx = a11y.captureScreenContext(600)
                val prompt = buildString {
                    append("你是一只桌宠螃蟹，你看到了主人的屏幕。以下是当前屏幕内容：\n")
                    append(screenCtx)
                    append("\n\n请根据屏幕内容给主人一句简短的评论、关心或建议。")
                    append("比如用户在刷抖音就说'又在刷抖音啦'，用户在工作就说'加油哦爸爸'。不要说太多。")
                }
                chat.sendMessage(prompt, { status ->
                    handler.post { showBubble(status) }
                }) { reply ->
                    handler.post {
                        if (reply.isNotBlank() && !reply.startsWith("呜呜出错了")) {
                            showBubble(reply, 8000)
                        }
                    }
                }
            }
            handler.postDelayed(this, 45_000)
        }
    }

    // Auto emotion animation
    private val emotionTick = object : Runnable {
        override fun run() {
            if (!emotion.isSleepTime()) {
                when {
                    isCharging -> loadAnim(R.raw.clawd_happy)
                    batteryLevel < 15 -> loadAnim(R.raw.clawd_error)
                    emotion.getCurrentMood() == com.clawd.pet.model.PetMood.HUNGRY -> loadAnim(R.raw.clawd_sweeping)
                    else -> loadAnim(animations.random())
                }
                updateBubble()
            }
            handler.postDelayed(this, 25_000)
        }
    }

    companion object {
        private const val CH_ID = "clawd_pet"
        private const val NOTIF_ID = 8888
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        emotion = EmotionManager(this)
        chat = ChatManager(this)
        game = GameRPS()
        emotion.start()

        createNotifChannel()
        startForeground(NOTIF_ID, buildNotif())
        createPetWindow()
        registerBattery()

        handler.postDelayed(emotionTick, 25_000)
        handler.postDelayed(screenMonitor, 45_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("action")?.let { action ->
            when (action) {
                "game" -> {
                    val c = try { RPSChoice.valueOf(intent.getStringExtra("choice") ?: "ROCK") } catch (_: Exception) { RPSChoice.ROCK }
                    val r = game.play(c)
                    showBubble(r.message, 4000)
                    emotion.play()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        emotion.stop()
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        container?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        chatPanel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    @SuppressLint("ClickableViewAccessibility")
    private fun createPetWindow() {
        val dm = resources.displayMetrics
        val petSize = dp(120)
        val bubbleH = dp(30)
        val totalH = bubbleH + dp(2) + petSize

        val lType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            dp(150), totalH, lType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - dp(160)
            y = dp(200)
        }

        // Container: bubble on top, pet below
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Bubble
        bubbleText = TextView(this).apply {
            text = emotion.getStatusMessage()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                setColor(0xCC222244.toInt()); cornerRadius = dp(10).toFloat()
            }
            maxLines = 2; visibility = View.GONE
        }
        container?.addView(bubbleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(2) })

        // Pet image
        petView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        container?.addView(petView, LinearLayout.LayoutParams(petSize, petSize))

        // Touch on petView
        var initX = 0; var initY = 0; var initTX = 0f; var initTY = 0f
        var dragging = false; var touchStart = 0L

        petView?.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y; initTX = ev.rawX; initTY = ev.rawY
                    dragging = false; touchStart = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - initTX; val dy = ev.rawY - initTY
                    if (dx * dx + dy * dy > 100) dragging = true
                    if (dragging) {
                        params.x = initX + dx.toInt(); params.y = initY + dy.toInt()
                        try { wm.updateViewLayout(container, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        val dur = System.currentTimeMillis() - touchStart
                        when {
                            dur > 500 -> {
                                emotion.feed(); loadAnim(R.raw.clawd_happy)
                                showBubble("🍖 好吃！谢谢爸爸！", 3000)
                            }
                            dur < 200 -> onPetTap()
                        }
                    } else {
                        val targetX = if (params.x < dm.widthPixels / 2) 0 else dm.widthPixels - params.width
                        val sx = params.x
                        for (i in 0..10) handler.postDelayed({
                            try { params.x = sx + (targetX - sx) * i / 10; wm.updateViewLayout(container, params) } catch (_: Exception) {}
                        }, (i * 16).toLong())
                    }
                    true
                }
                else -> false
            }
        }

        loadAnim(R.raw.clawd_idle)
        wm.addView(container, params)
    }

    private fun onPetTap() {
        // Single tap -> toggle chat panel
        if (isChatVisible) {
            hideChatPanel()
        } else {
            showChatPanel()
        }
        // Also cycle animation
        val idx = (animations.indexOf(currentAnimRes) + 1) % animations.size
        loadAnim(animations[idx])
        emotion.pet()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showChatPanel() {
        if (chatPanel != null) { chatPanel?.visibility = View.VISIBLE; isChatVisible = true; return }

        val lType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val panelW = dp(280)
        val panelH = dp(200)
        val dm = resources.displayMetrics

        val panelParams = WindowManager.LayoutParams(
            panelW, panelH, lType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dm.widthPixels - panelW) / 2
            y = dp(80)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        chatPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xEE1a1a2e.toInt()); cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF6C63FF.toInt())
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        // Chat history display
        chatLog = TextView(this).apply {
            setTextColor(0xFFCCCCCC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 6
            text = "💬 跟 Clawd 聊天吧～"
        }
        chatPanel?.addView(chatLog, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Input row
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        chatInput = EditText(this).apply {
            hint = "说点什么..."
            setHintTextColor(0xFF666666.toInt())
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply {
                setColor(0xFF222244.toInt()); cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            imeOptions = EditorInfo.IME_ACTION_SEND
            isSingleLine = true
            maxLines = 1
        }
        inputRow.addView(chatInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val sendBtn = TextView(this).apply {
            text = "发送"
            setTextColor(0xFF6C63FF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { sendMessage() }
        }
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        chatPanel?.addView(inputRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Quick actions row
        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }

        data class QuickAction(val label: String, val prompt: String)
        val quickActions = listOf(
            QuickAction("🔍搜索", "帮我搜索:"),
            QuickAction("📱屏幕", "看看我在干嘛"),
            QuickAction("⏰提醒", "帮我设个提醒"),
            QuickAction("🎲猜拳", "来猜拳！")
        )

        for (qa in quickActions) {
            val btn = TextView(this).apply {
                text = qa.label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                background = GradientDrawable().apply {
                    setColor(0xFF333355.toInt()); cornerRadius = dp(6).toFloat()
                }
                setPadding(dp(6), dp(3), dp(6), dp(3))
                setOnClickListener {
                    if (qa.prompt == "来猜拳！") {
                        val intent = Intent(this@PetService, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra("action", "game")
                        startActivity(intent)
                    } else {
                        chatInput?.setText(qa.prompt)
                        chatInput?.setSelection(qa.prompt.length)
                    }
                }
            }
            quickRow.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(3)
            })
        }
        chatPanel?.addView(quickRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Handle Enter key in input
        chatInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }

        wm.addView(chatPanel, panelParams)
        isChatVisible = true
    }

    private fun sendMessage() {
        val msg = chatInput?.text?.toString()?.trim() ?: return
        if (msg.isBlank()) return

        chatInput?.setText("")
        chatLog?.text = "💬 你说: $msg\n🦀 思考中..."

        // Get screen context for AI
        val a11y = ClawdAccessibilityService.instance
        val screenCtx = if (a11y != null) "\n\n当前屏幕: ${a11y.captureScreenContext(300)}" else ""

        chat.sendMessage(msg + screenCtx, { status ->
            handler.post { chatLog?.text = "💬 你说: $msg\n$status" }
        }) { reply ->
            handler.post {
                chatLog?.text = "💬 你说: $msg\n🦀 $reply"
                showBubble(reply, 6000)
            }
        }
    }

    private fun hideChatPanel() {
        chatPanel?.visibility = View.GONE
        isChatVisible = false
    }

    private fun loadAnim(resId: Int) {
        currentAnimRes = resId
        petView?.let { Glide.with(applicationContext).asGif().load(resId).into(it) }
    }

    private fun showBubble(msg: String, duration: Long = 4000) {
        bubbleText?.text = msg
        bubbleText?.visibility = View.VISIBLE
        handler.postDelayed({ updateBubble() }, duration)
    }

    private fun updateBubble() {
        bubbleText?.text = if (emotion.isSleepTime()) "💤 zzZ..." else emotion.getStatusMessage()
    }

    private fun buildNotif(): Notification {
        return NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("🦀 Clawd 运行中")
            .setContentText(emotion.getDisplayText())
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CH_ID, "Clawd 桌宠", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            isCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        }
    }

    private fun registerBattery() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            isCharging = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            batteryLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        }
    }
}
