package com.simivr.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-SIM incoming line configuration. [simSlot] is 0 or 1 (SIM 1 / SIM 2). */
@Entity(tableName = "incoming_lines")
data class IncomingLineEntity(
    @PrimaryKey val simSlot: Int,
    val enabled: Boolean = true,
    val flowId: String? = null,
    val ringsBeforeAnswer: Int = 3,
    val businessHoursEnabled: Boolean = false,
    /** minutes since midnight, local time */
    val businessHoursStartMin: Int = 9 * 60,
    val businessHoursEndMin: Int = 18 * 60,
    val afterHoursFlowId: String? = null,
    val recordingEnabled: Boolean = false
)

enum class CallerRuleType { BLOCKLIST, ALLOWLIST }

@Entity(tableName = "caller_rules")
data class CallerRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val type: CallerRuleType,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
