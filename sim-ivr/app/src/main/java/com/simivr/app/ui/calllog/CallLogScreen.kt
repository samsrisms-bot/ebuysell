package com.simivr.app.ui.calllog

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallLogScreen(viewModel: CallLogViewModel = hiltViewModel()) {
    val logs by viewModel.callLogs.collectAsState()
    val exportedUri by viewModel.exportedUri.collectAsState()
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    LaunchedEffect(exportedUri) {
        exportedUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export call log"))
            viewModel.clearExported()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call log") },
                actions = {
                    IconButton(onClick = { viewModel.exportCsv() }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export CSV")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${log.direction} • ${log.number}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${dateFormat.format(Date(log.startedAt))} • ${log.durationMs / 1000}s • ${log.outcome}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("Flow: ${log.flowId ?: "-"}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
