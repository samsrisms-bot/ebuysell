package com.simivr.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class CallDirection { INCOMING, OUTGOING }

enum class CallOutcome { NO_ANSWER, BUSY, ANSWERED, COMPLETED, FAILED, MISSED, REJECTED_BLOCKLIST }

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val direction: CallDirection,
    val number: String,
    val simSlot: Int,
    val flowId: String?,
    val campaignId: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMs: Long = 0,
    /** ordered list of node ids visited, JSON array */
    val pathJson: String = "[]",
    /** ordered list of digits pressed, JSON array */
    val digitsJson: String = "[]",
    val recordingPath: String? = null,
    val outcome: CallOutcome
)
