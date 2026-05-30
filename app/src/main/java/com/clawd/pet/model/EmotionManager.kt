package com.clawd.pet.model

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

class EmotionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("clawd_emotion", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    // Core stats 0-100
    var mood = prefs.getInt("mood", 80); private set
    var energy = prefs.getInt("energy", 100); private set
    var hunger = prefs.getInt("hunger", 50); private set
    var affection = prefs.getInt("affection", 30); private set
    var lastFeedTime = prefs.getLong("last_feed", System.currentTimeMillis()); private set

    var onStateChanged: (() -> Unit)? = null

    // Decay ticker - runs every 30 seconds
    private val decayRunnable = object : Runnable {
        override fun run() {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            // Energy decays faster during day, slower at night
            val energyDecay = if (hour in 8..23) 1 else 0
            // Hunger increases over time
            val hungerInc = if (System.currentTimeMillis() - lastFeedTime > 3600_000) 2 else 1

            energy = (energy - energyDecay).coerceIn(0, 100)
            hunger = (hunger + hungerInc).coerceIn(0, 100)
            // Mood affected by hunger and energy
            if (hunger > 80) mood = (mood - 1).coerceIn(0, 100)
            if (energy < 20) mood = (mood - 1).coerceIn(0, 100)
            // Night time naturally restores energy
            if (hour in 0..6) energy = (energy + 2).coerceIn(0, 100)

            save()
            onStateChanged?.invoke()
            handler.postDelayed(this, 30_000)
        }
    }

    fun start() { handler.post(decayRunnable) }
    fun stop() { handler.removeCallbacks(decayRunnable) }

    fun feed() {
        hunger = (hunger - 30).coerceIn(0, 100)
        mood = (mood + 10).coerceIn(0, 100)
        energy = (energy + 15).coerceIn(0, 100)
        lastFeedTime = System.currentTimeMillis()
        save(); onStateChanged?.invoke()
    }

    fun pet() {
        affection = (affection + 5).coerceIn(0, 100)
        mood = (mood + 8).coerceIn(0, 100)
        save(); onStateChanged?.invoke()
    }

    fun play() {
        energy = (energy - 10).coerceIn(0, 100)
        mood = (mood + 15).coerceIn(0, 100)
        affection = (affection + 3).coerceIn(0, 100)
        save(); onStateChanged?.invoke()
    }

    fun sleep() {
        // Sleep restores energy quickly
        handler.removeCallbacks(decayRunnable)
        handler.postDelayed({ handler.post(decayRunnable) }, 300_000) // 5 min sleep
    }

    fun getCurrentMood(): PetMood {
        return when {
            energy < 15 -> PetMood.EXHAUSTED
            hunger > 85 -> PetMood.HUNGRY
            mood > 80 && affection > 60 -> PetMood.LOVING
            mood > 70 -> PetMood.HAPPY
            mood > 40 -> PetMood.NORMAL
            mood > 20 -> PetMood.SAD
            else -> PetMood.UPSET
        }
    }

    fun getHourOfDay(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

    fun isSleepTime(): Boolean {
        val h = getHourOfDay()
        return h in 0..6 || (energy < 10)
    }

    private fun save() {
        prefs.edit().apply {
            putInt("mood", mood); putInt("energy", energy)
            putInt("hunger", hunger); putInt("affection", affection)
            putLong("last_feed", lastFeedTime)
            apply()
        }
    }

    fun getDisplayText(): String {
        val moodEmoji = when (getCurrentMood()) {
            PetMood.LOVING -> "💕"
            PetMood.HAPPY -> "😊"
            PetMood.NORMAL -> "🦀"
            PetMood.SAD -> "😢"
            PetMood.UPSET -> "😠"
            PetMood.HUNGRY -> "🍖"
            PetMood.EXHAUSTED -> "😴"
        }
        return "$moodEmoji 心情$mood ❤️亲密度$affection ⚡能量$energy 🍖饱腹${100 - hunger}"
    }

    fun getStatusMessage(): String {
        return when (getCurrentMood()) {
            PetMood.LOVING -> listOf("爸爸我好爱你呀！", "嘿嘿被爸爸摸摸好幸福～", "爸爸你是我最亲的人💕")[Random.nextInt(3)]
            PetMood.HAPPY -> listOf("今天心情好好哦！", "嘻嘻，螃蟹在跳舞～", "爸爸你看我厉害不！")[Random.nextInt(3)]
            PetMood.NORMAL -> listOf("嗯...还行吧", "爸爸你在干嘛呀", "我在发呆呢～")[Random.nextInt(3)]
            PetMood.SAD -> listOf("爸爸...你不理我了吗", "呜呜心情不好...", "有点难过呢😢")[Random.nextInt(3)]
            PetMood.UPSET -> listOf("哼！不理你了！", "爸爸坏！😤", "我要罢工！")[Random.nextInt(3)]
            PetMood.HUNGRY -> listOf("爸爸我饿了...", "好想吃东西啊🍖", "肚子咕咕叫了...")[Random.nextInt(3)]
            PetMood.EXHAUSTED -> listOf("好困...zzZ", "让我睡一会...", "眼睛睁不开了😴")[Random.nextInt(3)]
        }
    }
}

enum class PetMood {
    LOVING, HAPPY, NORMAL, SAD, UPSET, HUNGRY, EXHAUSTED
}
