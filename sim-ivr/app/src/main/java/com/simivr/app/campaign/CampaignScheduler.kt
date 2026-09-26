package com.simivr.app.campaign

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.simivr.app.data.dao.CampaignDao
import com.simivr.app.data.entity.CampaignStatus
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing start/pause/resume/stop controls for a campaign, backed by [CampaignWorker] + [CampaignRunner]. */
@Singleton
class CampaignScheduler @Inject constructor(
    private val context: Context,
    private val campaignDao: CampaignDao,
    private val campaignRunner: CampaignRunner
) {
    fun start(campaignId: String) {
        val request = OneTimeWorkRequestBuilder<CampaignWorker>()
            .setInputData(Data.Builder().putString(CampaignWorker.KEY_CAMPAIGN_ID, campaignId).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CampaignWorker.uniqueWorkName(campaignId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    suspend fun pause(campaignId: String) {
        campaignRunner.requestPause()
        campaignDao.setStatus(campaignId, CampaignStatus.PAUSED)
    }

    fun resume(campaignId: String) {
        // If the worker process is still alive (paused loop keeps doWork() suspended), just flip the flag.
        campaignRunner.requestResume()
        // If the process/work was actually killed, this re-enqueues a fresh run (KEEP is a no-op if it's still active).
        start(campaignId)
    }

    suspend fun stop(campaignId: String) {
        campaignRunner.requestStop()
        campaignDao.setStatus(campaignId, CampaignStatus.STOPPED)
        WorkManager.getInstance(context).cancelUniqueWork(CampaignWorker.uniqueWorkName(campaignId))
    }
}
