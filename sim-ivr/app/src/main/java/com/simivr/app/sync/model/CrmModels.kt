package com.simivr.app.sync.model

/**
 * CRM API contract — POST {base}/api/ivr/events body shape. Sent for every completed call and
 * for every "Callback request" (digit 3) or opt-out (digit 9), matching the spec's field names
 * exactly so any CRM (Chat247, TeleCRM, or the bundled crm-stub) can be adapted to consume it.
 */
data class CallCrmEvent(
    val event: String, // "call_result" | "callback_request"
    val direction: String, // "incoming" | "outgoing"
    val number: String,
    val sim: Int,
    val flow: String,
    val digits: List<String>,
    val durationMs: Long,
    val startedAt: Long,
    val endedAt: Long,
    val campaignId: String?,
    val vars: Map<String, String>
)

/** GET {base}/api/ivr/campaigns/{id}/contacts response row shape. */
data class CrmContactDto(
    val name: String?,
    val phone: String,
    val vars: Map<String, String>? = null
)
