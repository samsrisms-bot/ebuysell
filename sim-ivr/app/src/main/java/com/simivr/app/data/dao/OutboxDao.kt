package com.simivr.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simivr.app.data.entity.DndEntity
import com.simivr.app.data.entity.OutboxEntity
import com.simivr.app.data.entity.OutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun pending(statuses: List<OutboxStatus> = listOf(OutboxStatus.PENDING, OutboxStatus.FAILED)): List<OutboxEntity>

    @Query("SELECT * FROM outbox ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OutboxEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OutboxEntity)

    @Update
    suspend fun update(entity: OutboxEntity)

    @Query("DELETE FROM outbox WHERE status = 'SENT'")
    suspend fun clearSent()
}

@Dao
interface DndDao {
    @Query("SELECT * FROM dnd_list ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DndEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM dnd_list WHERE phoneNumber = :number)")
    suspend fun isOnDndList(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DndEntity)

    @Query("DELETE FROM dnd_list WHERE phoneNumber = :number")
    suspend fun remove(number: String)
}
