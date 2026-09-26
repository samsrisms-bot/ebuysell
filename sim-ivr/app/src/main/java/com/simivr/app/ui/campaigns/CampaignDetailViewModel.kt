package com.simivr.app.ui.campaigns

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simivr.app.campaign.CampaignScheduler
import com.simivr.app.campaign.ContactRepo
import com.simivr.app.data.dao.CampaignDao
import com.simivr.app.data.dao.ContactDao
import com.simivr.app.data.entity.CampaignEntity
import com.simivr.app.data.entity.ContactEntity
import com.simivr.app.data.entity.ContactStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactStats(val total: Int = 0, val completed: Int = 0, val noAnswer: Int = 0, val optedOut: Int = 0, val pending: Int = 0)

@HiltViewModel
class CampaignDetailViewModel @Inject constructor(
    private val campaignDao: CampaignDao,
    private val contactDao: ContactDao,
    private val contactRepo: ContactRepo,
    private val campaignScheduler: CampaignScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val campaignId: String = requireNotNull(savedStateHandle.get<String>("campaignId"))

    val campaign: StateFlow<CampaignEntity?> = campaignDao.observeById(campaignId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val contacts: StateFlow<List<ContactEntity>> = contactDao.observeForCampaign(campaignId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<ContactStats> = contacts
        .map { list ->
            ContactStats(
                total = list.size,
                completed = list.count { it.status == ContactStatus.COMPLETED || it.status == ContactStatus.DIGIT_PRESSED },
                noAnswer = list.count { it.status == ContactStatus.NO_ANSWER || it.status == ContactStatus.BUSY },
                optedOut = list.count { it.status == ContactStatus.OPTED_OUT },
                pending = list.count { it.status == ContactStatus.PENDING }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactStats())

    fun importCsv(uri: Uri) {
        viewModelScope.launch { contactRepo.importCsv(uri, campaignId) }
    }

    fun pullFromCrm(remoteCampaignId: String) {
        viewModelScope.launch { contactRepo.pullFromCrm(campaignId, remoteCampaignId) }
    }

    fun start() = campaignScheduler.start(campaignId)
    fun pause() = viewModelScope.launch { campaignScheduler.pause(campaignId) }
    fun resume() = campaignScheduler.resume(campaignId)
    fun stop() = viewModelScope.launch { campaignScheduler.stop(campaignId) }
}
