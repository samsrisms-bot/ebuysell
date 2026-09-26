package com.simivr.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simivr.app.data.dao.CallLogDao
import com.simivr.app.data.dao.CampaignDao
import com.simivr.app.data.dao.OutboxDao
import com.simivr.app.data.entity.CallOutcome
import com.simivr.app.data.entity.CampaignStatus
import com.simivr.app.data.entity.OutboxStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val totalCalls: Int = 0,
    val completedCalls: Int = 0,
    val missedCalls: Int = 0,
    val runningCampaigns: Int = 0,
    val pendingOutboxEvents: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    callLogDao: CallLogDao,
    campaignDao: CampaignDao,
    outboxDao: OutboxDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(callLogDao.observeAll(), campaignDao.observeAll(), outboxDao.observeAll()) { calls, campaigns, outbox ->
                DashboardUiState(
                    totalCalls = calls.size,
                    completedCalls = calls.count { it.outcome == CallOutcome.COMPLETED },
                    missedCalls = calls.count { it.outcome == CallOutcome.MISSED || it.outcome == CallOutcome.NO_ANSWER },
                    runningCampaigns = campaigns.count { it.status == CampaignStatus.RUNNING },
                    pendingOutboxEvents = outbox.count { it.status == OutboxStatus.PENDING || it.status == OutboxStatus.FAILED }
                )
            }.collect { _uiState.value = it }
        }
    }
}
