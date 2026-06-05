package com.oppowatch.aichat.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oppowatch.aichat.data.entity.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<Conversation>): List<Long>

    @Update
    suspend fun update(conversation: Conversation)

    @Delete
    suspend fun delete(conversation: Conversation)

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getById(id: Long): Flow<Conversation?>

    @Query("SELECT * FROM conversations WHERE model_config_id = :modelConfigId ORDER BY createdAt DESC")
    fun getAllByModelId(modelConfigId: Long): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Conversation>>
}
