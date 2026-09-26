package com.simivr.app.campaign

import com.simivr.app.data.CallPolicyRepository
import com.simivr.app.data.dao.ContactDao
import com.simivr.app.data.entity.ContactStatus
import com.simivr.app.flow.FlowEndReason
import com.simivr.app.flow.FlowRunResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Called by [com.simivr.app.telecom.CallAudioForegroundService] once a flow finishes on a call
 * that belongs to a campaign contact, so the contact's status reflects what actually happened —
 * and so a caller who presses 9 (the default outbound "opt out" digit) is added to the DND list
 * and never dialed again.
 */
@Singleton
class CampaignEventReporter @Inject constructor(
    private val contactDao: ContactDao,
    private val callPolicyRepository: CallPolicyRepository
) {
    suspend fun reportOutcome(campaignId: String, contactId: Long, result: FlowRunResult?) {
        val contact = contactDao.getById(contactId) ?: return

        val optedOut = result?.digitsPressed?.contains("9") == true
        val status = when {
            optedOut -> ContactStatus.OPTED_OUT
            result == null -> ContactStatus.FAILED
            result.endReason == FlowEndReason.ERROR -> ContactStatus.FAILED
            result.digitsPressed.isNotEmpty() -> ContactStatus.DIGIT_PRESSED
            result.endReason == FlowEndReason.HANGUP_NODE -> ContactStatus.COMPLETED
            else -> ContactStatus.ANSWERED
        }

        contactDao.update(
            contact.copy(
                status = status,
                lastAttemptAt = System.currentTimeMillis(),
                lastResult = result?.endReason?.name ?: "no_result",
                digitsPressed = result?.digitsPressed?.joinToString(",")
            )
        )

        if (optedOut) {
            callPolicyRepository.addToDndList(contact.phone, reason = "pressed 9 to opt out")
        }
    }
}
