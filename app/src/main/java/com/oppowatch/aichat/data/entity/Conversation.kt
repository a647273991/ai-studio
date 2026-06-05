package com.oppowatch.aichat.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = ModelConfig::class,
            parentColumns = ["id"],
            childColumns = ["model_config_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["model_config_id"])]
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "model_config_id")
    val modelConfigId: Long,

    val title: String,

    val createdAt: Long = System.currentTimeMillis()
)
