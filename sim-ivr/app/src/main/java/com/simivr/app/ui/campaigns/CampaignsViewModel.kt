package com.simivr.app.ui.campaigns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simivr.app.campaign.CampaignScheduler
import com.simivr.app.data.dao.CampaignDao
import com.simivr.app.data.dao.FlowDao
import com.simivr.app.data.entity.CampaignEntity
import com.simivr.app.data.entity.CampaignStatus
import com.simivr.app.data.entity.FlowEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CampaignsViewModel @Inject constructor(
    private val campaignDao: CampaignDao,
    flowDao: FlowDao,
    private val campaignScheduler: CampaignScheduler
) : ViewModel() {

    val campaigns: StateFlow<List<CampaignEntity>> = campaignDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flows: StateFlow<List<FlowEntity>> = flowDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCampaign(name: String, flowId: String, simSlot: Int) {
        viewModelScope.launch {
            campaignDao.upsert(CampaignEntity(name = name, flowId = flowId, simSlot = simSlot))
        }
    }

    fun start(campaignId: String) = campaignScheduler.start(campaignId)
    fun pause(campaignId: String) = viewModelScope.launch { campaignScheduler.pause(campaignId) }
    fun resume(campaignId: String) = campaignScheduler.resume(campaignId)
    fun stop(campaignId: String) = viewModelScope.launch { campaignScheduler.stop(campaignId) }
}
