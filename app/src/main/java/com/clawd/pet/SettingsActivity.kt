package com.clawd.pet

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.clawd.pet.model.ChatManager
import com.clawd.pet.model.EmotionManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val chat = ChatManager(this)
        val emotion = EmotionManager(this)

        findViewById<EditText>(R.id.etApiUrl).setText(chat.apiUrl)
        findViewById<EditText>(R.id.etApiKey).setText(chat.apiKey)
        findViewById<EditText>(R.id.etModel).setText(chat.model)
        findViewById<EditText>(R.id.etPrompt).setText(chat.systemPrompt)
        findViewById<TextView>(R.id.tvStats).text = emotion.getDisplayText()

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            chat.apiUrl = findViewById<EditText>(R.id.etApiUrl).text.toString().trim()
            chat.apiKey = findViewById<EditText>(R.id.etApiKey).text.toString().trim()
            chat.model = findViewById<EditText>(R.id.etModel).text.toString().trim()
            chat.systemPrompt = findViewById<EditText>(R.id.etPrompt).text.toString().trim()
            Toast.makeText(this, "✅ 设置已保存！", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnResetEmotion).setOnClickListener {
            getSharedPreferences("clawd_emotion", MODE_PRIVATE).edit().clear().apply()
            Toast.makeText(this, "情绪数据已重置", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
