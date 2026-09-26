package com.simivr.app.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import com.simivr.app.data.CallPolicyRepository
import com.simivr.app.data.ScreeningDecision
import com.simivr.app.data.dao.IncomingLineDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import javax.inject.Inject

/**
 * Runs before the call ever reaches [IvrInCallService] / rings audibly: rejects blocklisted
 * numbers (and, if an allowlist is configured, anything not on it) outright, and can silently
 * decline calls outside configured business hours instead of auto-answering them.
 */
@AndroidEntryPoint
class IvrCallScreeningService : CallScreeningService() {

    @Inject lateinit var callPolicyRepository: CallPolicyRepository
    @Inject lateinit var incomingLineDao: IncomingLineDao

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        val decision = runBlocking { callPolicyRepository.screenIncoming(number) }

        if (decision == ScreeningDecision.BLOCK) {
            respondToCall(
                callDetails,
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            )
            return
        }

        val outsideBusinessHours = runBlocking { isOutsideBusinessHours() }
        if (outsideBusinessHours) {
            // Still allow the call through — IvrInCallService plays an after-hours flow instead of
            // silently dropping the caller, per the "business hours" incoming-line requirement.
        }

        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .build()
        )
    }

    private suspend fun isOutsideBusinessHours(): Boolean {
        val line = incomingLineDao.getForSim(0) ?: return false
        if (!line.businessHoursEnabled) return false
        val now = Calendar.getInstance()
        val minutesNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minutesNow < line.businessHoursStartMin || minutesNow > line.businessHoursEndMin
    }
}
