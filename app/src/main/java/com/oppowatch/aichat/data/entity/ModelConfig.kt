package com.oppowatch.aichat.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_configs")
data class ModelConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val apiUrl: String,
    val apiKey: String,
    val modelName: String,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val systemPrompt: String = "",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
