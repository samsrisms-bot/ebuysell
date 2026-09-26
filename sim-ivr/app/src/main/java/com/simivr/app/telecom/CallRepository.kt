package com.simivr.app.telecom

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ManagedCallState { NONE, RINGING, DIALING, ACTIVE, HOLDING, DISCONNECTED }

data class ManagedCallInfo(
    val state: ManagedCallState = ManagedCallState.NONE,
    val number: String? = null,
    val isIncoming: Boolean = false,
    val simSlot: Int = 0,
    val campaignId: String? = null,
    val contactId: Long? = null,
    val flowId: String? = null,
    val flowVars: Map<String, String> = emptyMap()
)

/**
 * Process-wide holder for the single active [Call] that [telecom.IvrInCallService] is managing,
 * plus the metadata (which flow/campaign/contact it belongs to) needed to run that call through
 * the FlowEngine. Single-SIM concurrency (campaign spec requires concurrency = 1) means there is
 * only ever one call of interest at a time.
 */
data class PendingOutgoingMeta(
    val number: String,
    val simSlot: Int,
    val campaignId: String?,
    val contactId: Long?,
    val flowId: String?,
    val flowVars: Map<String, String>
)

@Singleton
class CallRepository @Inject constructor() {
    @Volatile
    var activeCall: Call? = null
        private set

    /** Set by CampaignRunner right before TelecomManager.placeCall so IvrInCallService.onCallAdded
     * can attach campaign/flow metadata to the outgoing call it's about to receive. */
    @Volatile
    private var pendingOutgoing: PendingOutgoingMeta? = null

    private val _callInfo = MutableStateFlow(ManagedCallInfo())
    val callInfo: StateFlow<ManagedCallInfo> = _callInfo

    fun setPendingOutgoing(meta: PendingOutgoingMeta) {
        pendingOutgoing = meta
    }

    /** Consumes (clears) and returns pending metadata if [number] matches, used once per outgoing call. */
    fun consumePendingOutgoing(number: String): PendingOutgoingMeta? {
        val meta = pendingOutgoing ?: return null
        val normalizedPending = meta.number.filter { it.isDigit() }
        val normalizedIncoming = number.filter { it.isDigit() }
        if (normalizedPending.isEmpty() || !normalizedIncoming.endsWith(normalizedPending.takeLast(8))) return null
        pendingOutgoing = null
        return meta
    }

    fun attach(call: Call, info: ManagedCallInfo) {
        activeCall = call
        _callInfo.value = info
    }

    fun updateInfo(transform: (ManagedCallInfo) -> ManagedCallInfo) {
        _callInfo.value = transform(_callInfo.value)
    }

    fun updateState(state: ManagedCallState) {
        _callInfo.value = _callInfo.value.copy(state = state)
    }

    fun clear() {
        activeCall = null
        _callInfo.value = ManagedCallInfo()
    }
}
