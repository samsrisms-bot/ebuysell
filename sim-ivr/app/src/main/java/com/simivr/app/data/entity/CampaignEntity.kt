package com.simivr.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class CampaignStatus { DRAFT, RUNNING, PAUSED, STOPPED, COMPLETED }

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val flowId: String,
    val simSlot: Int,
    val status: CampaignStatus = CampaignStatus.DRAFT,
    /** minutes since midnight, local time — calls only placed inside this window */
    val windowStartMin: Int = 9 * 60,
    val windowEndMin: Int = 20 * 60,
    val delayBetweenCallsMs: Long = 5000L,
    val maxRetries: Int = 2,
    val remoteCampaignId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ContactStatus { PENDING, CALLING, NO_ANSWER, BUSY, ANSWERED, DIGIT_PRESSED, COMPLETED, FAILED, OPTED_OUT, SKIPPED_DND }

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: String,
    val name: String,
    val phone: String,
    /** custom CSV columns beyond name/phone, serialized as a JSON object */
    val varsJson: String = "{}",
    val status: ContactStatus = ContactStatus.PENDING,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastResult: String? = null,
    val digitsPressed: String? = null
)
