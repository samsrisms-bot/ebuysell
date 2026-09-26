package com.simivr.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simivr.app.data.entity.CallerRuleEntity
import com.simivr.app.data.entity.CallerRuleType
import com.simivr.app.data.entity.IncomingLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomingLineDao {
    @Query("SELECT * FROM incoming_lines ORDER BY simSlot ASC")
    fun observeAll(): Flow<List<IncomingLineEntity>>

    @Query("SELECT * FROM incoming_lines WHERE simSlot = :simSlot")
    suspend fun getForSim(simSlot: Int): IncomingLineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(line: IncomingLineEntity)
}

@Dao
interface CallerRuleDao {
    @Query("SELECT * FROM caller_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CallerRuleEntity>>

    @Query("SELECT * FROM caller_rules WHERE type = :type")
    suspend fun getByType(type: CallerRuleType): List<CallerRuleEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM caller_rules WHERE phoneNumber = :number AND type = :type)")
    suspend fun exists(number: String, type: CallerRuleType): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CallerRuleEntity)

    @Delete
    suspend fun delete(rule: CallerRuleEntity)
}
