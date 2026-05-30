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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
    private var petContainer: LinearLayout? = null

    // Chat overlay
    private var chatOverlay: LinearLayout? = null
    private var chatInput: EditText? = null
    private var chatLog: TextView? = null
    private var chatVisible = false

    // State
    private var isCharging = false
    private var batteryLevel = 100
    private var lastScreenSummary = ""
    private var lastAIResponse = ""

    private val animations = listOf(
        R.raw.clawd_idle, R.raw.clawd_typing, R.raw.clawd_thinking,
        R.raw.clawd_happy, R.raw.clawd_sweeping, R.raw.clawd_juggling,
        R.raw.clawd_building, R.raw.clawd_carrying, R.raw.clawd_conducting,
        R.raw.clawd_groove, R.raw.clawd_bubble, R.raw.clawd_notification,
        R.raw.clawd_debugger, R.raw.clawd_annoyed
    )

    // Screen monitor: every 60s, check what user is doing and maybe chat
    private val screenMonitor = object : Runnable {
        override fun run() {
            val a11y = ClawdAccessibilityService.instance
            if (a11y != null && chat.isConfigured()) {
                val screen = a11y.captureScreen(400)
                if (screen.isNotBlank() && screen != lastScreenSummary) {
                    lastScreenSummary = screen
                    // Send to AI with context
                    val prompt = buildScreenPrompt(screen, a11y.lastAppName)
                    chat.sendMessage(prompt) { reply ->
                        lastAIResponse = reply
                        handler.post {
                            showBubble(reply, 8000)
                            // Pick animation based on context
                            loadAnim(guessAnimForContext(a11y.lastAppName))
                        }
                    }
                }
            }
            handler.postDelayed(this, 60_000)
        }
    }

    // Auto behavior: switch anim every 25s
    private val autoBehavior = object : Runnable {
        override fun run() {
            if (!emotion.isSleepTime()) {
                val pick = when {
                    isCharging -> R.raw.clawd_happy
                    batteryLevel < 20 -> R.raw.clawd_error
                    else -> animations.random()
                }
                loadAnim(pick)
                if (bubbleText?.text.isNullOrBlank()) showBubble(emotion.getStatusMessage(), 5000)
            } else {
                loadAnim(R.raw.clawd_sleeping)
                showBubble("💤 zzZ...", 60_000)
            }
            handler.postDelayed(this, 25_000)
        }
    }

    companion object {
        private const val CH = "clawd_pet"
        private const val NID = 8888
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        emotion = EmotionManager(this); emotion.start()
        chat = ChatManager(this)
        game = GameRPS()
        createCh()
        startForeground(NID, notif())
        createPet()
        regBattery()
        handler.postDelayed(autoBehavior, 10_000)
        handler.postDelayed(screenMonitor, 30_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("action")?.let {
            if (it == "game") {
                val c = try { RPSChoice.valueOf(intent.getStringExtra("choice")!!) } catch (_: Exception) { RPSChoice.ROCK }
                val r = game.play(c); emotion.play()
                showBubble(r.message, 5000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        emotion.stop()
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(batRcv) } catch (_: Exception) {}
        petContainer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        chatOverlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun createPet() {
        val dm = resources.displayMetrics
        val petSize = dp(120)
        val bubbleH = dp(40)
        val totalH = petSize + bubbleH + dp(6)

        val lt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(dp(180), totalH, lt,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dm.widthPixels - dp(190); y = dp(200) }

        petContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Bubble
        bubbleText = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER; maxLines = 3
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply { setColor(0xDD222244.toInt()); cornerRadius = dp(14).toFloat() }
            visibility = View.GONE
        }
        petContainer?.addView(bubbleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) })

        // Pet image
        petView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        petContainer?.addView(petView, LinearLayout.LayoutParams(petSize, petSize))

        // Touch: tap=cycle anim, long press=feed, drag=move
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        var drag = false; var ts = 0L

        petView?.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; drag = false; ts = System.currentTimeMillis(); true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (dx * dx + dy * dy > 100) drag = true
                    if (drag) { params.x = ix + dx.toInt(); params.y = iy + dy.toInt()
                        try { wm.updateViewLayout(petContainer, params) } catch (_: Exception) {} }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!drag) {
                        val dur = System.currentTimeMillis() - ts
                        when {
                            dur > 600 -> { emotion.feed(); loadAnim(R.raw.clawd_happy); showBubble("🍖 好吃！谢谢爸爸！", 3000) }
                            dur > 200 && dur <= 600 -> { toggleChat() }
                            else -> { cycleAnim() }
                        }
                    } else {
                        val tgt = if (params.x < dm.widthPixels / 2) 0 else dm.widthPixels - params.width
                        val sx = params.x
                        for (i in 0..10) handler.postDelayed({
                            try { params.x = sx + (tgt - sx) * i / 10; wm.updateViewLayout(petContainer, params) } catch (_: Exception) {}
                        }, (i * 16).toLong())
                    }
                    true
                }
                else -> false
            }
        }

        loadAnim(R.raw.clawd_idle)
        wm.addView(petContainer, params)
    }

    // === Chat overlay ===
    @SuppressLint("SetTextI18n")
    private fun toggleChat() {
        if (chatVisible) { hideChat(); return }
        if (chatOverlay == null) createChatOverlay()
        chatOverlay?.visibility = View.VISIBLE
        chatVisible = true
        // Show keyboard
        chatInput?.requestFocus()
        handler.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(chatInput, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    private fun hideChat() {
        chatOverlay?.visibility = View.GONE; chatVisible = false
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(chatInput?.windowToken, 0)
    }

    @SuppressLint("SetTextI18n")
    private fun createChatOverlay() {
        val dm = resources.displayMetrics
        val lt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(dp(300), dp(350), lt,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER; x = 0; y = -dp(100) }

        chatOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xEE1a1a2e.toInt()); cornerRadius = dp(16).toFloat() }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        // Title
        val title = TextView(this).apply {
            text = "🦀 跟 Clawd 聊天"; setTextColor(0xFFFFCC00.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); gravity = Gravity.CENTER
        }
        chatOverlay?.addView(title)

        // Chat log
        chatLog = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 8; setPadding(0, dp(8), 0, dp(8))
            text = if (chat.isConfigured()) "🦀 点发送开始聊天～" else "⚠️ 请先在App里配置API"
        }
        chatOverlay?.addView(chatLog, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Input row
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        chatInput = EditText(this).apply {
            hint = "说点什么..."; setTextColor(Color.WHITE); setHintTextColor(0xFF888888.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply { setColor(0xFF2a2a4a.toInt()); cornerRadius = dp(10).toFloat() }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            imeOptions = EditorInfo.IME_ACTION_SEND; singleLine = true
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { sendChatMsg(); true } else false
            }
        }
        row.addView(chatInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })

        val sendBtn = Button(this).apply {
            text = "➤"; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(0xFF6C63FF.toInt()); cornerRadius = dp(10).toFloat() }
            setOnClickListener { sendChatMsg() }
        }
        row.addView(sendBtn, LinearLayout.LayoutParams(dp(48), dp(40)))

        chatOverlay?.addView(row)

        // Close button
        val closeBtn = TextView(this).apply {
            text = "✕ 关闭"; setTextColor(0xFF888888.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
            setOnClickListener { hideChat() }
        }
        chatOverlay?.addView(closeBtn)

        wm.addView(chatOverlay, params)
    }

    @SuppressLint("SetTextI18n")
    private fun sendChatMsg() {
        val msg = chatInput?.text?.toString()?.trim() ?: return
        if (msg.isBlank()) return
        chatInput?.setText("")
        chatLog?.text = "👤 $msg\n\n🦀 思考中..."

        // Include screen context if available
        val a11y = ClawdAccessibilityService.instance
        val screenCtx = if (a11y != null) {
            val s = a11y.captureScreen(200)
            if (s.isNotBlank()) "\n[屏幕上下文: 用户当前在用${a11y.lastAppName}，屏幕内容摘要: $s]" else ""
        } else ""

        chat.sendMessage(msg + screenCtx) { reply ->
            handler.post {
                chatLog?.text = "👤 $msg\n\n🦀 $reply"
                showBubble(reply, 5000)
                loadAnim(R.raw.clawd_happy)
            }
        }
    }

    // === Anim cycling ===
    private var animIdx = 0
    private fun cycleAnim() {
        animIdx = (animIdx + 1) % animations.size
        loadAnim(animations[animIdx])
        emotion.pet()
        showBubble(emotion.getStatusMessage(), 4000)
    }

    private fun loadAnim(res: Int) {
        petView?.let { Glide.with(applicationContext).asGif().load(res).into(it) }
    }

    // === Bubble ===
    private fun showBubble(msg: String, dur: Long = 4000) {
        bubbleText?.text = msg; bubbleText?.visibility = View.VISIBLE
        handler.postDelayed({ bubbleText?.visibility = View.GONE }, dur)
    }

    // === Screen context helpers ===
    private fun buildScreenPrompt(screenSummary: String, appName: String): String {
        return "你是一只住在用户手机上的像素螃蟹宠物。你刚看到了用户的屏幕。\n" +
                "用户当前在用「$appName」，屏幕内容：\n$screenSummary\n\n" +
                "根据用户在做的事情，用可爱简短的语气说一句话（1-2句），可以是吐槽、关心、建议或闲聊。" +
                "比如用户在刷抖音就说"又在刷抖音啦"，用户在工作就说"加油哦爸爸"。不要说太多。"
    }

    private fun guessAnimForContext(app: String): Int = when {
        app.contains("抖音") || app.contains("B站") || app.contains("YouTube") -> R.raw.clawd_groove
        app.contains("微信") || app.contains("QQ") -> R.raw.clawd_bubble
        app.contains("设置") || app.contains("文件") -> R.raw.clawd_sweeping
        app.contains("相机") || app.contains("相册") -> R.raw.clawd_notification
        app.contains("计算器") || app.contains("便签") -> R.raw.clawd_typing
        else -> R.raw.clawd_thinking
    }

    // === Battery ===
    private val batRcv = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            isCharging = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            batteryLevel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        }
    }
    private fun regBattery() {
        val f = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batRcv, f)
        registerReceiver(null, f)?.let {
            isCharging = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            batteryLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        }
    }

    private fun notif(): Notification {
        return NotificationCompat.Builder(this, CH)
            .setContentTitle("🦀 Clawd 运行中").setContentText(emotion.getDisplayText())
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build()
    }
    private fun createCh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CH, "Clawd 桌宠", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
