package com.clawd.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.clawd.pet.model.ChatManager
import com.clawd.pet.model.EmotionManager

class MainActivity : AppCompatActivity() {

    private lateinit var emotion: EmotionManager
    private lateinit var chat: ChatManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emotion = EmotionManager(this)
        chat = ChatManager(this)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvStats = findViewById<TextView>(R.id.tvStats)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnChat = findViewById<Button>(R.id.btnChat)
        val btnFeed = findViewById<Button>(R.id.btnFeed)
        val btnGame = findViewById<Button>(R.id.btnGame)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        fun refresh() {
            tvStatus.text = emotion.getDisplayText()
            tvStats.text = "饱腹: ${100 - emotion.hunger}%  |  能量: ${emotion.energy}%  |  心情: ${emotion.mood}%  |  亲密度: ${emotion.affection}%"
        }
        refresh()

        btnStart.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startPetService()
                Toast.makeText(this, "🦀 Clawd 已启动！", Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            } else {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")), 100)
            }
        }

        btnChat.setOnClickListener {
            if (!chat.isConfigured()) {
                Toast.makeText(this, "请先在设置中配置 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val input = EditText(this).apply {
                hint = "跟 Clawd 说点什么..."
                setPadding(40, 20, 40, 20)
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("💬 跟 Clawd 聊天")
                .setView(input)
                .setPositiveButton("发送") { _, _ ->
                    val msg = input.text.toString().ifBlank { return@setPositiveButton }
                    Toast.makeText(this, "🦀 思考中...", Toast.LENGTH_SHORT).show()
                    chat.sendMessage(msg) { reply ->
                        runOnUiThread {
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("🦀 Clawd 说")
                                .setMessage(reply)
                                .setPositiveButton("继续聊") { _, _ -> btnChat.performClick() }
                                .setNegativeButton("关闭", null)
                                .show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        btnFeed.setOnClickListener {
            emotion.feed()
            refresh()
            Toast.makeText(this, "🍖 喂食成功！Clawd 好开心！", Toast.LENGTH_SHORT).show()
        }

        btnGame.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("✊✌️🖐 跟 Clawd 猜拳！")
                .setItems(arrayOf("✊ 石头", "✌️ 剪刀", "🖐 布")) { _, which ->
                    val choice = when (which) {
                        0 -> com.clawd.pet.game.RPSChoice.ROCK
                        1 -> com.clawd.pet.game.RPSChoice.SCISSORS
                        else -> com.clawd.pet.game.RPSChoice.PAPER
                    }
                    val intent = Intent(this, PetService::class.java)
                        .putExtra("action", "game")
                        .putExtra("choice", choice.name)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                    emotion.play()
                    refresh()
                }
                .show()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (intent.getBooleanExtra("show_menu", false)) {
            moveTaskToBack(true)
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.tvStatus)?.text = emotion.getDisplayText()
    }

    private fun startPetService() {
        val intent = Intent(this, PetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    @Deprecated("Deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && Settings.canDrawOverlays(this)) {
            startPetService()
            moveTaskToBack(true)
        }
    }
}
