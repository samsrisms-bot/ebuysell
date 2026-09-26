package com.simivr.app.campaign

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simivr.app.data.CallPolicyRepository
import com.simivr.app.data.dao.CampaignDao
import com.simivr.app.data.dao.ContactDao
import com.simivr.app.data.entity.CampaignStatus
import com.simivr.app.data.entity.ContactEntity
import com.simivr.app.data.entity.ContactStatus
import com.simivr.app.telecom.CallController
import com.simivr.app.telecom.CallRepository
import com.simivr.app.telecom.PendingOutgoingMeta
import com.simivr.app.telecom.SimAccountResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

enum class CampaignRunnerCommand { RUN, PAUSE, STOP }

/**
 * Drives a single outbound campaign end to end: one contact at a time (concurrency = 1, matching
 * "single SIM" hardware reality), respecting the calling window, DND list, and max retries. The
 * actual IVR conversation for an answered call is handled by
 * [com.simivr.app.telecom.CallAudioForegroundService] — this class only places calls, waits for
 * them to conclude, and paces the next attempt.
 */
@Singleton
class CampaignRunner @Inject constructor(
    private val context: Context,
    private val campaignDao: CampaignDao,
    private val contactDao: ContactDao,
    private val callPolicyRepository: CallPolicyRepository,
    private val callRepository: CallRepository,
    private val callController: CallController,
    private val simAccountResolver: SimAccountResolver
) {
    private val gson = Gson()
    private val noAnswerTimeoutMs = 45_000L
    private val callAttachTimeoutMs = 20_000L

    @Volatile private var command: CampaignRunnerCommand = CampaignRunnerCommand.RUN

    fun requestPause() { command = CampaignRunnerCommand.PAUSE }
    fun requestResume() { command = CampaignRunnerCommand.RUN }
    fun requestStop() { command = CampaignRunnerCommand.STOP }

    /** Runs [campaignId] to completion (or until paused/stopped). Suspends for the whole campaign duration. */
    suspend fun run(campaignId: String) {
        command = CampaignRunnerCommand.RUN
        campaignDao.setStatus(campaignId, CampaignStatus.RUNNING)

        while (true) {
            if (command == CampaignRunnerCommand.STOP) {
                campaignDao.setStatus(campaignId, CampaignStatus.STOPPED)
                return
            }
            if (command == CampaignRunnerCommand.PAUSE) {
                campaignDao.setStatus(campaignId, CampaignStatus.PAUSED)
                delay(2000)
                continue
            }

            val campaign = campaignDao.getById(campaignId) ?: return
            val contact = contactDao.nextPending(campaignId, maxRetries = campaign.maxRetries)
            if (contact == null) {
                campaignDao.setStatus(campaignId, CampaignStatus.COMPLETED)
                return
            }

            if (!withinCallingWindow(campaign.windowStartMin, campaign.windowEndMin)) {
                delay(60_000)
                continue
            }

            if (callPolicyRepository.isOnDndList(contact.phone)) {
                contactDao.update(contact.copy(status = ContactStatus.SKIPPED_DND))
                continue
            }

            placeCallAndAwaitOutcome(campaign.id, campaign.simSlot, campaign.flowId, contact)
            delay(campaign.delayBetweenCallsMs)
        }
    }

    private suspend fun placeCallAndAwaitOutcome(campaignId: String, simSlot: Int, flowId: String, contact: ContactEntity) {
        contactDao.update(contact.copy(status = ContactStatus.CALLING, attempts = contact.attempts + 1))

        val vars: MutableMap<String, String> = try {
            gson.fromJson(contact.varsJson, object : TypeToken<Map<String, String>>() {}.type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
        vars["name"] = contact.name.ifBlank { contact.phone }

        callRepository.setPendingOutgoing(
            PendingOutgoingMeta(
                number = contact.phone,
                simSlot = simSlot,
                campaignId = campaignId,
                contactId = contact.id,
                flowId = flowId,
                flowVars = vars
            )
        )

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = simAccountResolver.handleForSlot(simSlot)
        val extras = Bundle().apply {
            if (handle != null) putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }
        try {
            telecomManager.placeCall(Uri.fromParts("tel", contact.phone, null), extras)
        } catch (e: SecurityException) {
            contactDao.update(contact.copy(status = ContactStatus.FAILED, lastResult = "missing CALL_PHONE permission"))
            return
        }

        val call = withTimeoutOrNull(callAttachTimeoutMs) {
            callRepository.callInfo.filter { it.contactId == contact.id }.first()
            callRepository.activeCall
        }

        if (call == null) {
            contactDao.update(contact.copy(status = ContactStatus.FAILED, lastResult = "call was not placed"))
            return
        }

        withTimeoutOrNull(noAnswerTimeoutMs + 15 * 60 * 1000L) {
            callController.stateFlow(call).first { it == Call.STATE_DISCONNECTED }
        }

        // If nothing updated the contact past CALLING (i.e. it never went ACTIVE / no flow ran), classify from the disconnect cause.
        val finalContact = contactDao.getById(contact.id) ?: return
        if (finalContact.status == ContactStatus.CALLING) {
            val cause = call.details?.disconnectCause?.code
            val status = when (cause) {
                DisconnectCause.BUSY -> ContactStatus.BUSY
                DisconnectCause.REJECTED -> ContactStatus.NO_ANSWER
                else -> ContactStatus.NO_ANSWER
            }
            contactDao.update(finalContact.copy(status = status, lastResult = "disconnect_cause=$cause"))
        }
    }

    private fun withinCallingWindow(startMin: Int, endMin: Int): Boolean {
        val now = Calendar.getInstance()
        val minutesNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minutesNow in startMin..endMin
    }
}
