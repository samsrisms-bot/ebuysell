package com.simivr.app.ui.calllog

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simivr.app.data.dao.CallLogDao
import com.simivr.app.data.entity.CallLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CallLogViewModel @Inject constructor(
    private val context: Context,
    private val callLogDao: CallLogDao
) : ViewModel() {

    val callLogs: StateFlow<List<CallLogEntity>> = callLogDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportedUri = MutableStateFlow<Uri?>(null)
    val exportedUri: StateFlow<Uri?> = _exportedUri.asStateFlow()

    fun exportCsv() {
        viewModelScope.launch {
            val logs = callLogDao.getAllOnce()
            val uri = withContext(Dispatchers.IO) { writeCsv(logs) }
            _exportedUri.value = uri
        }
    }

    fun clearExported() { _exportedUri.value = null }

    private fun writeCsv(logs: List<CallLogEntity>): Uri {
        val dir = File(context.getExternalFilesDir("exports"), "").apply { mkdirs() }
        val fileName = "call_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
        val file = File(dir, fileName)
        file.bufferedWriter().use { writer ->
            writer.write("direction,number,sim,flow,campaignId,startedAt,endedAt,durationMs,outcome,digits,path\n")
            for (log in logs) {
                writer.write(
                    listOf(
                        log.direction, log.number, log.simSlot + 1, log.flowId ?: "", log.campaignId ?: "",
                        log.startedAt, log.endedAt ?: "", log.durationMs, log.outcome,
                        log.digitsJson.replace(",", ";"), log.pathJson.replace(",", ";")
                    ).joinToString(",") { "\"$it\"" } + "\n"
                )
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
