package com.simivr.app.ui.flows

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.simivr.app.ui.common.NavRoutes

@Composable
fun FlowsScreen(
    onOpenFlow: (String) -> Unit,
    viewModel: FlowsViewModel = hiltViewModel()
) {
    val flows by viewModel.flows.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("IVR Flows") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenFlow(NavRoutes.NEW_FLOW_ID) }) {
                Icon(Icons.Filled.Add, contentDescription = "New flow")
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
            items(flows, key = { it.id }) { flow ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier
                            .weight(1f)
                            .clickableFlow { onOpenFlow(flow.id) }) {
                            Text(flow.name, style = MaterialTheme.typography.titleMedium)
                            Text(flow.description, style = MaterialTheme.typography.bodySmall)
                            if (flow.isSample) Text("Sample flow", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { viewModel.deleteFlow(flow) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableFlow(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
