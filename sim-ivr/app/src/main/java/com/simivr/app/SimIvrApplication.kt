package com.simivr.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.simivr.app.data.dao.IncomingLineDao
import com.simivr.app.data.entity.IncomingLineEntity
import com.simivr.app.flow.FlowRepository
import com.simivr.app.sync.OutboxSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SimIvrApplication : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
    @Inject lateinit var flowRepository: FlowRepository
    @Inject lateinit var incomingLineDao: IncomingLineDao
    @Inject lateinit var outboxSyncScheduler: OutboxSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            flowRepository.seedSampleFlowsIfNeeded()
            seedDefaultIncomingLinesIfNeeded()
        }
        outboxSyncScheduler.schedulePeriodic()
    }

    private suspend fun seedDefaultIncomingLinesIfNeeded() {
        for (slot in 0..1) {
            if (incomingLineDao.getForSim(slot) == null) {
                incomingLineDao.upsert(
                    IncomingLineEntity(
                        simSlot = slot,
                        enabled = slot == 0,
                        flowId = if (slot == 0) "sample-incoming" else null
                    )
                )
            }
        }
    }
}
