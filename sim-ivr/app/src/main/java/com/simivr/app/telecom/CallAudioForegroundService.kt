package com.simivr.app.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.simivr.app.R
import com.simivr.app.campaign.CampaignEventReporter
import com.simivr.app.data.SettingsRepository
import com.simivr.app.data.dao.CallLogDao
import com.simivr.app.data.dao.IncomingLineDao
import com.simivr.app.data.entity.CallDirection
import com.simivr.app.data.entity.CallLogEntity
import com.simivr.app.data.entity.CallOutcome
import com.simivr.app.flow.FlowEndReason
import com.simivr.app.flow.FlowEngine
import com.simivr.app.flow.FlowRepository
import com.simivr.app.sync.CrmEventPublisher
import com.simivr.app.sync.model.CallCrmEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Foreground service (microphone|phoneCall types) that owns the actual IVR conversation once a
 * call is [android.telecom.Call.STATE_ACTIVE]: routes audio to speaker, runs the selected
 * [com.simivr.app.flow.model.IvrFlow] through [FlowEngine], logs the outcome, and pushes a CRM
 * event.
 */
@AndroidEntryPoint
class CallAudioForegroundService : Service() {

    @Inject lateinit var callRepository: CallRepository
    @Inject lateinit var callController: CallController
    @Inject lateinit var flowRepository: FlowRepository
    @Inject lateinit var incomingLineDao: IncomingLineDao
    @Inject lateinit var callLogDao: CallLogDao
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var actionHandlerFactory: CallFlowActionHandlerFactory
    @Inject lateinit var crmEventPublisher: CrmEventPublisher
    @Inject lateinit var campaignEventReporter: CampaignEventReporter

    private val job = SupervisorJob()
    private lateinit var scope: CoroutineScope

    companion object {
        const val CHANNEL_ID = "call_audio_channel"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.Default + job)
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { runFlowForActiveCall() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runFlowForActiveCall() {
        val info = callRepository.callInfo.first { it.state == com.simivr.app.telecom.ManagedCallState.ACTIVE }
        val call = callRepository.activeCall ?: run { stopSelf(); return }

        callController.routeToSpeaker()

        val flowId = info.flowId ?: resolveIncomingFlowId(info.simSlot)
        val flow = flowId?.let { flowRepository.getFlow(it) }
        val startedAt = System.currentTimeMillis()

        if (flow == null) {
            // No flow configured for this line — leave the call as a normal (silent, non-IVR) voice call.
            stopSelf()
            return
        }

        val settings = settingsRepository.settings.first()
        val handler = actionHandlerFactory.create(settings.dtmfSensitivity)
        val engine = FlowEngine(flow, handler, initialVars = info.flowVars)
        val result = withTimeoutOrNull(15 * 60 * 1000L) { engine.run() }

        val endedAt = System.currentTimeMillis()
        val outcome = when (result?.endReason) {
            FlowEndReason.HANGUP_NODE -> CallOutcome.COMPLETED
            FlowEndReason.CALL_ENDED -> CallOutcome.ANSWERED
            else -> CallOutcome.FAILED
        }

        callLogDao.upsert(
            CallLogEntity(
                direction = if (info.isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING,
                number = info.number ?: "",
                simSlot = info.simSlot,
                flowId = flow.id,
                campaignId = info.campaignId,
                startedAt = startedAt,
                endedAt = endedAt,
                durationMs = endedAt - startedAt,
                pathJson = com.google.gson.Gson().toJson(result?.path ?: emptyList<String>()),
                digitsJson = com.google.gson.Gson().toJson(result?.digitsPressed ?: emptyList<String>()),
                outcome = outcome
            )
        )

        crmEventPublisher.publish(
            CallCrmEvent(
                event = if (result?.digitsPressed?.contains("3") == true) "callback_request" else "call_result",
                direction = if (info.isIncoming) "incoming" else "outgoing",
                number = info.number ?: "",
                sim = info.simSlot,
                flow = flow.id,
                digits = result?.digitsPressed ?: emptyList(),
                durationMs = endedAt - startedAt,
                startedAt = startedAt,
                endedAt = endedAt,
                campaignId = info.campaignId,
                vars = result?.vars ?: emptyMap()
            )
        )

        if (info.campaignId != null && info.contactId != null) {
            campaignEventReporter.reportOutcome(info.campaignId, info.contactId, result)
        }

        stopSelf()
    }

    private suspend fun resolveIncomingFlowId(simSlot: Int): String? {
        val line = incomingLineDao.getForSim(simSlot) ?: return null
        return line.flowId
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_calls), NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_call_active))
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setOngoing(true)
            .build()
    }
}
