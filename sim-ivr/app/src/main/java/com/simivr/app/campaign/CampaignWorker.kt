package com.simivr.app.campaign

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.simivr.app.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs a single campaign to completion as expedited, foreground-promoted WorkManager work —
 * satisfies both "foreground service" (via setForeground) and "WorkManager" (survives process
 * restarts, retried automatically) from the spec in one mechanism.
 */
@HiltWorker
class CampaignWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val campaignRunner: CampaignRunner
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_CAMPAIGN_ID = "campaign_id"
        const val CHANNEL_ID = "campaign_channel"
        const val NOTIF_ID = 2001
        fun uniqueWorkName(campaignId: String) = "campaign-$campaignId"
    }

    override suspend fun doWork(): Result {
        val campaignId = inputData.getString(KEY_CAMPAIGN_ID) ?: return Result.failure()
        setForeground(createForegroundInfo(campaignId))
        campaignRunner.run(campaignId)
        return Result.success()
    }

    private fun createForegroundInfo(campaignId: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.notif_channel_campaigns), NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notif_campaign_running, campaignId))
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }
}
