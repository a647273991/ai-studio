package com.oppowatch.aichat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.oppowatch.aichat.data.dao.ConversationDao
import com.oppowatch.aichat.data.dao.MessageDao
import com.oppowatch.aichat.data.dao.ModelConfigDao
import com.oppowatch.aichat.data.entity.Conversation
import com.oppowatch.aichat.data.entity.Message
import com.oppowatch.aichat.data.entity.ModelConfig
import java.util.Date

// ── Type Converters ─────────────────────────────────────────

class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}

// ── Room Database ───────────────────────────────────────────

@Database(
    entities = [
        ModelConfig::class,
        Conversation::class,
        Message::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun modelConfigDao(): ModelConfigDao

    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "oppo_watch_ai_chat.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
