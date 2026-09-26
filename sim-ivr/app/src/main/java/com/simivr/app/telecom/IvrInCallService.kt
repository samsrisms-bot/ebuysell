package com.simivr.app.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import androidx.core.content.ContextCompat
import com.simivr.app.data.SettingsRepository
import com.simivr.app.data.dao.IncomingLineDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Registered as the app's telecom InCallService (required to become the default dialer). Owns the
 * lifecycle of whatever single call is active: auto-answers incoming calls after the configured
 * ring count, and hands the call off to [CallAudioForegroundService] to run the IVR flow once it
 * is answered/active. Concurrency is single-call by design (matches "concurrency = 1" for
 * campaigns, and a phone only has one active voice call per SIM anyway).
 */
@AndroidEntryPoint
class IvrInCallService : InCallService() {

    @Inject lateinit var callRepository: CallRepository
    @Inject lateinit var serviceHolder: InCallServiceHolder
    @Inject lateinit var callController: CallController
    @Inject lateinit var incomingLineDao: IncomingLineDao
    @Inject lateinit var settingsRepository: SettingsRepository

    private var scopeJob = SupervisorJob()
    private lateinit var scope: CoroutineScope

    /** Approximate ring cadence used to translate "answer after N rings" into a delay, since InCallService has no direct ring-count callback. */
    private val ringIntervalMs = 4000L

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.Default + scopeJob)
        serviceHolder.service = this
    }

    override fun onDestroy() {
        serviceHolder.service = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val isIncoming = call.state == Call.STATE_RINGING
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val pending = if (!isIncoming) callRepository.consumePendingOutgoing(number) else null

        callRepository.attach(
            call,
            ManagedCallInfo(
                state = if (isIncoming) ManagedCallState.RINGING else ManagedCallState.DIALING,
                number = number,
                isIncoming = isIncoming,
                simSlot = pending?.simSlot ?: 0,
                campaignId = pending?.campaignId,
                contactId = pending?.contactId,
                flowId = pending?.flowId,
                flowVars = pending?.flowVars ?: emptyMap()
            )
        )

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                val mapped = when (state) {
                    Call.STATE_RINGING -> ManagedCallState.RINGING
                    Call.STATE_DIALING, Call.STATE_CONNECTING -> ManagedCallState.DIALING
                    Call.STATE_ACTIVE -> ManagedCallState.ACTIVE
                    Call.STATE_HOLDING -> ManagedCallState.HOLDING
                    Call.STATE_DISCONNECTED -> ManagedCallState.DISCONNECTED
                    else -> callRepository.callInfo.value.state
                }
                callRepository.updateState(mapped)
                if (state == Call.STATE_ACTIVE) {
                    startCallAudioService()
                }
                if (state == Call.STATE_DISCONNECTED) {
                    call.unregisterCallback(this)
                }
            }
        })

        if (isIncoming) {
            scope.launch { maybeAutoAnswer(call) }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callRepository.clear()
        stopService(Intent(this, CallAudioForegroundService::class.java))
    }

    private suspend fun maybeAutoAnswer(call: Call) {
        val simSlot = callRepository.callInfo.value.simSlot
        val line = incomingLineDao.getForSim(simSlot)
        if (line?.enabled == false) {
            callController.rejectSilently(call)
            return
        }
        val rings = (line?.ringsBeforeAnswer ?: 3).coerceAtLeast(1)
        delay(rings * ringIntervalMs)
        if (call.state == Call.STATE_RINGING) {
            callController.answer(call)
        }
    }

    private fun startCallAudioService() {
        ContextCompat.startForegroundService(this, Intent(this, CallAudioForegroundService::class.java))
    }
}
