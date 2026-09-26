package com.simivr.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class OutboxStatus { PENDING, SENDING, SENT, FAILED }

/** Queued CRM events, retried with backoff by [com.simivr.app.sync.OutboxSyncWorker] when offline. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventType: String,
    val payloadJson: String,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dnd_list")
data class DndEntity(
    @PrimaryKey val phoneNumber: String,
    val reason: String = "opt-out",
    val addedAt: Long = System.currentTimeMillis()
)
