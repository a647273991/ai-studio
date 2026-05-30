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
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.clawd.pet.game.GameRPS
import com.clawd.pet.game.RPSChoice
import com.clawd.pet.model.ChatManager
import com.clawd.pet.model.EmotionManager
import com.clawd.pet.service.ClawdAccessibilityService
import kotlin.math.abs

class PetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var emotion: EmotionManager
    private lateinit var chat: ChatManager
    private lateinit var game: GameRPS
    private val handler = Handler(Looper.getMainLooper())

    // 悬浮窗组件
    private var petContainer: FrameLayout? = null          // 整个宠物的容器（包含气泡+螃蟹）
    private var petImage: ImageView? = null                // 螃蟹图片
    private var bubbleText: TextView? = null               // 头顶气泡文字
    private var chatInputContainer: LinearLayout? = null   // 聊天输入框容器（可拖动）
    private var chatEditText: EditText? = null             // 聊天输入框

    private var currentAnimRes = R.raw.clawd_idle
    private var isCharging = false
    private var batteryLevel = 100

    // 动画列表（单击时随机切换）
    private val animations = listOf(
        R.raw.clawd_idle, R.raw.clawd_typing, R.raw.clawd_thinking,
        R.raw.clawd_happy, R.raw.clawd_sweeping, R.raw.clawd_juggling,
        R.raw.clawd_building, R.raw.clawd_carrying, R.raw.clawd_conducting,
        R.raw.clawd_groove, R.raw.clawd_bubble, R.raw.clawd_notification,
        R.raw.clawd_debugger, R.raw.clawd_annoyed
    )

    // 自动行为（每30秒根据状态推荐动画）
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
                updateBubble()
            }
            handler.postDelayed(this, 30000)
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
        handler.postDelayed(autoBehavior, 30000)
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
                "feed" -> {
                    emotion.feed()
                    loadAnim(R.raw.clawd_happy)
                    showBubble("🍖 好吃！谢谢爸爸！")
                }
                "chat" -> {
                    showChatInput()
                }
                "settings" -> {
                    val settingsIntent = Intent(this, SettingsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(settingsIntent)
                }
                "exit" -> {
                    stopSelf()
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
        chatInputContainer?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun createPetWindow() {
        val dm = resources.displayMetrics
        val petSize = dp(120)          // 螃蟹大小
        val bubbleHeight = dp(36)
        val totalHeight = petSize + bubbleHeight + dp(4)

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

        // 容器：垂直布局，气泡在上，螃蟹在下
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        // 气泡（只有有消息时显示）
        bubbleText = TextView(this).apply {
            text = ""
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            val bg = GradientDrawable().apply {
                setColor(0xCC222244.toInt())
                cornerRadius = dp(15).toFloat()
            }
            background = bg
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        container.addView(bubbleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) })

        // 螃蟹图片
        petImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        container.addView(petImage, LinearLayout.LayoutParams(petSize, petSize))

        // 触摸事件：单击/双击/长按/拖拽
        var startX = 0; var startY = 0
        var startRawX = 0f; var startRawY = 0f
        var isDragging = false
        var lastTapTime = 0L

        petImage?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    if (isDragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300) {
                            // 双击 -> 弹出菜单
                            showPetMenu()
                        } else {
                            // 单击 -> 切换动画
                            nextAnimation()
                        }
                        lastTapTime = now
                    } else {
                        // 拖拽结束后吸附边缘
                        val midScreen = dm.widthPixels / 2
                        val targetX = if (params.x < midScreen) 0 else dm.widthPixels - params.width
                        animateSnap(params, targetX)
                    }
                    true
                }
                else -> false
            }
        }

        // 长按菜单（通过android:longClickable）
        petImage?.setOnLongClickListener {
            showPetMenu()
            true
        }

        loadAnim(R.raw.clawd_idle)
        windowManager.addView(container, params)
        petContainer = container
    }

    private fun nextAnimation() {
        val newAnim = animations.random()
        loadAnim(newAnim)
        showBubble("🦀 ~", 1500)
    }

    private fun showPetMenu() {
        val options = arrayOf("💬 聊天", "🍖 喂食", "✊ 猜拳", "⚙️ 设置", "❌ 退出")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clawd 菜单")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showChatInput()
                    1 -> {
                        emotion.feed()
                        loadAnim(R.raw.clawd_happy)
                        showBubble("🍖 好吃！谢谢爸爸！")
                    }
                    2 -> startGame()
                    3 -> {
                        val intent = Intent(this, SettingsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                    4 -> stopSelf()
                }
            }
            .show()
    }

    private fun startGame() {
        val choices = arrayOf("✊ 石头", "✌️ 剪刀", "🖐 布")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("猜拳")
            .setItems(choices) { _, which ->
                val choice = when (which) {
                    0 -> RPSChoice.ROCK
                    1 -> RPSChoice.SCISSORS
                    else -> RPSChoice.PAPER
                }
                val result = game.play(choice)
                showBubble(result.message, 2500)
                emotion.play()
            }
            .show()
    }

    private fun showChatInput() {
        if (chatInputContainer != null) {
            // 如果已经显示，就隐藏
            chatInputContainer?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            chatInputContainer = null
            return
        }

        val dm = resources.displayMetrics
        val width = dp(280)
        val height = dp(100)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            width, height, layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(20)
            y = dm.heightPixels / 2 - height / 2
        }

        // 容器可拖动
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@PetService, android.R.color.transparent))
        }

        // 标题栏（可拖动）
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val bg = GradientDrawable().apply {
                setColor(0xFF2C2C3A.toInt())
                cornerRadii = floatArrayOf(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), 0f, 0f, 0f, 0f)
            }
            background = bg
        }
        val dragIcon = TextView(this).apply {
            text = "🦀"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0)
        }
        val title = TextView(this).apply {
            text = "和 Clawd 聊天"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { hideChatInput() }
        }
        titleBar.addView(dragIcon)
        titleBar.addView(title)
        titleBar.addView(closeBtn)

        // 输入区域
        val inputArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val bg = GradientDrawable().apply {
                setColor(0xFF1C1C28.toInt())
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            }
            background = bg
        }
        chatEditText = EditText(this).apply {
            hint = "说点什么..."
            hintTextColor = 0x88FFFFFF
            setTextColor(Color.WHITE)
            background = null
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendChatMessage()
                    true
                } else false
            }
        }
        val sendBtn = TextView(this).apply {
            text = "发送"
            textSize = 14f
            setTextColor(0xFF6C63FF.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { sendChatMessage() }
        }
        inputArea.addView(chatEditText)
        inputArea.addView(sendBtn)

        container.addView(titleBar)
        container.addView(inputArea)

        // 拖动逻辑
        var startX = 0; var startY = 0
        var startRawX = 0f; var startRawY = 0f
        var isDragging = false
        titleBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    if (isDragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
        chatInputContainer = container
        chatEditText?.requestFocus()
        // 软键盘自动弹出需要额外处理，这里简化
    }

    private fun sendChatMessage() {
        val msg = chatEditText?.text?.toString()?.trim() ?: return
        if (msg.isEmpty()) return
        hideChatInput()
        showBubble("💬 思考中...")
        if (!chat.isConfigured()) {
            showBubble("爸爸还没配置API～去设置里填一下Key吧！", 3000)
            return
        }
        chat.sendMessage(msg, { status ->
            // 可选：更新气泡
        }) { reply ->
            showBubble(reply, 4000)
        }
    }

    private fun hideChatInput() {
        chatInputContainer?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        chatInputContainer = null
        // 关闭键盘
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        chatEditText?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun animateSnap(params: WindowManager.LayoutParams, targetX: Int) {
        val startX = params.x
        for (i in 0..10) {
            handler.postDelayed({
                try {
                    params.x = startX + (targetX - startX) * i / 10
                    windowManager.updateViewLayout(petContainer, params)
                } catch (_: Exception) {}
            }, (i * 16).toLong())
        }
    }

    private fun loadAnim(resId: Int) {
        currentAnimRes = resId
        petImage?.let { Glide.with(applicationContext).asGif().load(resId).into(it) }
    }

    private fun showBubble(message: String, duration: Long = 3000) {
        bubbleText?.text = message
        bubbleText?.visibility = View.VISIBLE
        handler.postDelayed({ updateBubble() }, duration)
    }

    private fun updateBubble() {
        bubbleText?.text = if (emotion.isSleepTime()) "💤 zzZ..." else emotion.getStatusMessage()
        bubbleText?.visibility = if (bubbleText?.text?.isNotEmpty() == true) View.VISIBLE else View.GONE
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
            isCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let {
            isCharging = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            batteryLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        }
    }
}
