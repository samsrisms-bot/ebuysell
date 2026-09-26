package com.simivr.app.ui.common

object NavRoutes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val FLOWS = "flows"
    const val FLOW_EDITOR = "flow_editor/{flowId}"
    const val CAMPAIGNS = "campaigns"
    const val CAMPAIGN_DETAIL = "campaign_detail/{campaignId}"
    const val CALL_LOG = "call_log"
    const val SETTINGS = "settings"
    const val AUDIO_TEST = "audio_test"

    fun flowEditor(flowId: String) = "flow_editor/$flowId"
    fun campaignDetail(campaignId: String) = "campaign_detail/$campaignId"

    const val NEW_FLOW_ID = "new"
}
