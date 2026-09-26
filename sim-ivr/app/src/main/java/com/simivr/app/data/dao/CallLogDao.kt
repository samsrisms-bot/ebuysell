package com.simivr.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simivr.app.data.entity.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE campaignId = :campaignId ORDER BY startedAt DESC")
    fun observeForCampaign(campaignId: String): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs ORDER BY startedAt DESC")
    suspend fun getAllOnce(): List<CallLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CallLogEntity)

    @Update
    suspend fun update(entry: CallLogEntity)
}
