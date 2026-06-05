package com.oppowatch.aichat.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oppowatch.aichat.data.entity.ModelConfig

@Dao
interface ModelConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ModelConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<ModelConfig>): List<Long>

    @Update
    suspend fun update(config: ModelConfig)

    @Delete
    suspend fun delete(config: ModelConfig)

    @Query("SELECT * FROM model_configs ORDER BY createdAt DESC")
    suspend fun getAll(): List<ModelConfig>

    @Query("SELECT * FROM model_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ModelConfig?

    @Query("SELECT * FROM model_configs WHERE id = :id")
    suspend fun getById(id: Long): ModelConfig?
}
