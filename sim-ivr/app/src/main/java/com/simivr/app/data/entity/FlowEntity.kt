package com.simivr.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flows")
data class FlowEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    /** Full [com.simivr.app.flow.model.IvrFlow] serialized via [com.simivr.app.flow.model.FlowJson]. */
    val json: String,
    val isSample: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
