package com.simivr.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.simivr.app.data.dao.CallLogDao
import com.simivr.app.data.dao.CallerRuleDao
import com.simivr.app.data.dao.CampaignDao
import com.simivr.app.data.dao.ContactDao
import com.simivr.app.data.dao.DndDao
import com.simivr.app.data.dao.FlowDao
import com.simivr.app.data.dao.IncomingLineDao
import com.simivr.app.data.dao.OutboxDao
import com.simivr.app.data.entity.CallLogEntity
import com.simivr.app.data.entity.CallerRuleEntity
import com.simivr.app.data.entity.CampaignEntity
import com.simivr.app.data.entity.ContactEntity
import com.simivr.app.data.entity.DndEntity
import com.simivr.app.data.entity.FlowEntity
import com.simivr.app.data.entity.IncomingLineEntity
import com.simivr.app.data.entity.OutboxEntity

@Database(
    entities = [
        FlowEntity::class,
        IncomingLineEntity::class,
        CallerRuleEntity::class,
        CampaignEntity::class,
        ContactEntity::class,
        CallLogEntity::class,
        OutboxEntity::class,
        DndEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SimIvrDatabase : RoomDatabase() {
    abstract fun flowDao(): FlowDao
    abstract fun incomingLineDao(): IncomingLineDao
    abstract fun callerRuleDao(): CallerRuleDao
    abstract fun campaignDao(): CampaignDao
    abstract fun contactDao(): ContactDao
    abstract fun callLogDao(): CallLogDao
    abstract fun outboxDao(): OutboxDao
    abstract fun dndDao(): DndDao

    companion object {
        const val NAME = "sim_ivr.db"
    }
}
