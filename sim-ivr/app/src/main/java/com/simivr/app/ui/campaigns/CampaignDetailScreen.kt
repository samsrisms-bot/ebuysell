package com.simivr.app.ui.campaigns

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.simivr.app.data.entity.CampaignStatus

@Composable
fun CampaignDetailScreen(viewModel: CampaignDetailViewModel = hiltViewModel()) {
    val campaign by viewModel.campaign.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val stats by viewModel.stats.collectAsState()

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importCsv(it) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(campaign?.name ?: "Campaign") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Status: ${campaign?.status}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Total ${stats.total} • Completed ${stats.completed} • No answer/busy ${stats.noAnswer} • Opted out ${stats.optedOut} • Pending ${stats.pending}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { csvPicker.launch("text/*") }) { Text("Import CSV") }
                TextButton(onClick = { viewModel.start() }, enabled = campaign?.status != CampaignStatus.RUNNING) { Text("Start") }
                TextButton(onClick = { viewModel.pause() }, enabled = campaign?.status == CampaignStatus.RUNNING) { Text("Pause") }
                TextButton(onClick = { viewModel.resume() }, enabled = campaign?.status == CampaignStatus.PAUSED) { Text("Resume") }
                TextButton(onClick = { viewModel.stop() }) { Text("Stop") }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(contacts, key = { it.id }) { contact ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("${contact.name.ifBlank { "(no name)" }} — ${contact.phone}", style = MaterialTheme.typography.bodyMedium)
                            Text("${contact.status} • attempts ${contact.attempts}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
