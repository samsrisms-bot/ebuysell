package com.simivr.app.ui.flows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simivr.app.data.entity.FlowEntity
import com.simivr.app.flow.FlowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlowsViewModel @Inject constructor(
    private val flowRepository: FlowRepository
) : ViewModel() {

    val flows: StateFlow<List<FlowEntity>> = flowRepository.observeFlows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteFlow(entity: FlowEntity) {
        viewModelScope.launch { flowRepository.deleteFlow(entity) }
    }

    suspend fun exportFlowJson(id: String): String? = flowRepository.exportFlowJson(id)

    fun importFlowJson(json: String) {
        viewModelScope.launch { flowRepository.importFlowJson(json) }
    }
}
