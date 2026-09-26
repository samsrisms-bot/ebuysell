package com.simivr.app.sync

import com.google.gson.Gson
import com.simivr.app.data.dao.OutboxDao
import com.simivr.app.data.entity.OutboxEntity
import com.simivr.app.sync.model.CallCrmEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every CRM event is written to the Room outbox first (durable, survives app/process death while
 * offline) and only then is an immediate send attempted — [OutboxSyncWorker] is the single source
 * of truth for actually delivering it, with retry/backoff when offline.
 */
@Singleton
class CrmEventPublisher @Inject constructor(
    private val outboxDao: OutboxDao,
    private val outboxSyncScheduler: OutboxSyncScheduler
) {
    private val gson = Gson()

    suspend fun publish(event: CallCrmEvent) {
        outboxDao.insert(OutboxEntity(eventType = event.event, payloadJson = gson.toJson(event)))
        outboxSyncScheduler.enqueueImmediate()
    }
}
