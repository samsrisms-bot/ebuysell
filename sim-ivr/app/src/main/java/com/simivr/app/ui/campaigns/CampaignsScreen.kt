package com.simivr.app.ui.campaigns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CampaignsScreen(
    onOpenCampaign: (String) -> Unit,
    viewModel: CampaignsViewModel = hiltViewModel()
) {
    val campaigns by viewModel.campaigns.collectAsState()
    val flows by viewModel.flows.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Campaigns") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New campaign")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(campaigns, key = { it.id }) { campaign ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clickableCampaign { onOpenCampaign(campaign.id) }
                    ) {
                        Text(campaign.name, style = MaterialTheme.typography.titleMedium)
                        Text("SIM ${campaign.simSlot + 1} • ${campaign.status}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCampaignDialog(
            flowNames = flows.map { it.id to it.name },
            onDismiss = { showCreateDialog = false },
            onCreate = { name, flowId, sim ->
                viewModel.createCampaign(name, flowId, sim)
                showCreateDialog = false
            }
        )
    }
}

private fun Modifier.clickableCampaign(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))

@Composable
private fun CreateCampaignDialog(
    flowNames: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedFlow by remember { mutableStateOf(flowNames.firstOrNull()) }
    var simSlot by remember { mutableStateOf(0) }
    var flowMenuExpanded by remember { mutableStateOf(false) }
    var simMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New campaign") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Campaign name") })

                ExposedDropdownMenuBox(expanded = flowMenuExpanded, onExpandedChange = { flowMenuExpanded = it }) {
                    OutlinedTextField(
                        value = selectedFlow?.second ?: "Select a flow",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Flow") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = flowMenuExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    DropdownMenu(expanded = flowMenuExpanded, onDismissRequest = { flowMenuExpanded = false }) {
                        flowNames.forEach { (id, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { selectedFlow = id to label; flowMenuExpanded = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = simMenuExpanded, onExpandedChange = { simMenuExpanded = it }) {
                    OutlinedTextField(
                        value = "SIM ${simSlot + 1}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("SIM") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = simMenuExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    DropdownMenu(expanded = simMenuExpanded, onDismissRequest = { simMenuExpanded = false }) {
                        listOf(0, 1).forEach { slot ->
                            DropdownMenuItem(text = { Text("SIM ${slot + 1}") }, onClick = { simSlot = slot; simMenuExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedFlow?.let { onCreate(name, it.first, simSlot) } },
                enabled = name.isNotBlank() && selectedFlow != null
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
