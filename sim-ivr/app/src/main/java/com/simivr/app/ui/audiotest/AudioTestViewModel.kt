package com.simivr.app.ui.audiotest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simivr.app.audio.DtmfListener
import com.simivr.app.audio.Player
import com.simivr.app.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioTestViewModel @Inject constructor(
    private val player: Player,
    private val dtmfListener: DtmfListener,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _detectedDigits = MutableStateFlow("")
    val detectedDigits: StateFlow<String> = _detectedDigits.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var listenJob: Job? = null

    fun playTestTone() = player.playTestTone()

    @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        _isListening.value = true
        _detectedDigits.value = ""
        _lastError.value = null
        listenJob = viewModelScope.launch {
            try {
                val sensitivity = settingsRepository.settings.first().dtmfSensitivity
                dtmfListener.listen(sensitivity).collect { digit ->
                    _detectedDigits.value += digit
                }
            } catch (e: Exception) {
                _lastError.value = e.message ?: "Mic capture failed — this device may block mic access during calls."
                _isListening.value = false
            }
        }
    }

    private fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        dtmfListener.stop()
        _isListening.value = false
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
