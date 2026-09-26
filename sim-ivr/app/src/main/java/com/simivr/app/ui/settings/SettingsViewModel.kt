package com.simivr.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simivr.app.data.AppSettings
import com.simivr.app.data.SettingsRepository
import com.simivr.app.data.dao.CallerRuleDao
import com.simivr.app.data.dao.IncomingLineDao
import com.simivr.app.data.entity.CallerRuleEntity
import com.simivr.app.data.entity.CallerRuleType
import com.simivr.app.data.entity.IncomingLineEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val incomingLineDao: IncomingLineDao,
    private val callerRuleDao: CallerRuleDao
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val incomingLines: StateFlow<List<IncomingLineEntity>> = incomingLineDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callerRules: StateFlow<List<CallerRuleEntity>> = callerRuleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun updateIncomingLine(line: IncomingLineEntity) {
        viewModelScope.launch { incomingLineDao.upsert(line) }
    }

    fun addBlockedNumber(number: String) {
        if (number.isBlank()) return
        viewModelScope.launch { callerRuleDao.insert(CallerRuleEntity(phoneNumber = number, type = CallerRuleType.BLOCKLIST)) }
    }

    fun deleteRule(rule: CallerRuleEntity) {
        viewModelScope.launch { callerRuleDao.delete(rule) }
    }
}
