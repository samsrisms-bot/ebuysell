package com.simivr.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simivr.app.data.entity.CampaignEntity
import com.simivr.app.data.entity.CampaignStatus
import com.simivr.app.data.entity.ContactEntity
import com.simivr.app.data.entity.ContactStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun getById(id: String): CampaignEntity?

    @Query("SELECT * FROM campaigns WHERE id = :id")
    fun observeById(id: String): Flow<CampaignEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(campaign: CampaignEntity): Unit

    @Update
    suspend fun update(campaign: CampaignEntity)

    @Query("UPDATE campaigns SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: CampaignStatus)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts WHERE campaignId = :campaignId ORDER BY id ASC")
    fun observeForCampaign(campaignId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE campaignId = :campaignId ORDER BY id ASC")
    suspend fun getForCampaign(campaignId: String): List<ContactEntity>

    @Query(
        "SELECT * FROM contacts WHERE campaignId = :campaignId AND status = :status " +
            "AND attempts <= :maxRetries ORDER BY id ASC LIMIT 1"
    )
    suspend fun nextPending(
        campaignId: String,
        status: ContactStatus = ContactStatus.PENDING,
        maxRetries: Int
    ): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("SELECT COUNT(*) FROM contacts WHERE campaignId = :campaignId AND status = :status")
    suspend fun countByStatus(campaignId: String, status: ContactStatus): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE campaignId = :campaignId")
    suspend fun countAll(campaignId: String): Int
}
