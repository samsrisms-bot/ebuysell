package com.simivr.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.simivr.app.data.dao.OutboxDao
import com.simivr.app.data.entity.OutboxEntity
import com.simivr.app.data.entity.OutboxStatus
import com.simivr.app.sync.model.CallCrmEvent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Flushes the Room outbox to the CRM. Scheduled both immediately after every new event (for
 * near-real-time delivery when online) and periodically (to catch up once connectivity returns) —
 * WorkManager's own backoff policy (see [OutboxSyncScheduler]) handles retry pacing when the CRM
 * or network is unreachable.
 */
@HiltWorker
class OutboxSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val outboxDao: OutboxDao,
    private val crmApi: CrmApi
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val pending = outboxDao.pending()
        if (pending.isEmpty()) return Result.success()

        var anyFailure = false
        for (entry in pending) {
            val sent = trySend(entry)
            if (!sent) anyFailure = true
        }
        return if (anyFailure) Result.retry() else Result.success()
    }

    private suspend fun trySend(entry: OutboxEntity): Boolean {
        outboxDao.update(entry.copy(status = OutboxStatus.SENDING))
        return try {
            val event = gson.fromJson(entry.payloadJson, CallCrmEvent::class.java)
            val response = crmApi.postEvent(event)
            if (response.isSuccessful) {
                outboxDao.update(entry.copy(status = OutboxStatus.SENT))
                true
            } else {
                outboxDao.update(
                    entry.copy(
                        status = OutboxStatus.FAILED,
                        attempts = entry.attempts + 1,
                        lastAttemptAt = System.currentTimeMillis(),
                        lastError = "HTTP ${response.code()}"
                    )
                )
                false
            }
        } catch (e: Exception) {
            outboxDao.update(
                entry.copy(
                    status = OutboxStatus.FAILED,
                    attempts = entry.attempts + 1,
                    lastAttemptAt = System.currentTimeMillis(),
                    lastError = e.message ?: e.javaClass.simpleName
                )
            )
            false
        }
    }
}
