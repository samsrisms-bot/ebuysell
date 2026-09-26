package com.simivr.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simivr.app.data.entity.FlowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowDao {
    @Query("SELECT * FROM flows ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FlowEntity>>

    @Query("SELECT * FROM flows WHERE id = :id")
    suspend fun getById(id: String): FlowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(flow: FlowEntity)

    @Update
    suspend fun update(flow: FlowEntity)

    @Delete
    suspend fun delete(flow: FlowEntity)

    @Query("SELECT COUNT(*) FROM flows")
    suspend fun count(): Int
}
