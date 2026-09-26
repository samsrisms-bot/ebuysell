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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.simivr.app.flow.model.AudioSource
import com.simivr.app.flow.model.FlowNode
import com.simivr.app.flow.model.IvrFlow

@Composable
fun FlowEditorScreen(
    onSaved: () -> Unit,
    viewModel: FlowEditorViewModel = hiltViewModel()
) {
    val flow by viewModel.flow.collectAsState()
    val saved by viewModel.saved.collectAsState()

    LaunchedEffect(saved) { if (saved) onSaved() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (viewModel.isNew) "New flow" else "Edit flow") }) }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(12.dp)) {

            OutlinedTextField(
                value = flow.name,
                onValueChange = { viewModel.updateMeta(it, flow.description) },
                label = { Text("Flow name") },
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            OutlinedTextField(
                value = flow.description,
                onValueChange = { viewModel.updateMeta(flow.name, it) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))

            NodeIdDropdown(
                label = "Start node (entryNodeId)",
                selected = flow.entryNodeId,
                options = flow.nodes.map { it.id },
                onSelected = { viewModel.setEntryNode(it) }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Nodes", style = MaterialTheme.typography.titleMedium)
                AddNodeMenu(onAdd = { viewModel.addNode(it) })
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(flow.nodes, key = { it.id }) { node ->
                    NodeCard(
                        node = node,
                        allNodeIds = flow.nodes.map { it.id },
                        onChange = { updated -> viewModel.replaceNode(node.id, updated) },
                        onDelete = { viewModel.deleteNode(node.id) }
                    )
                }
            }

            TextButton(onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth()) {
                Text("Save flow")
            }
        }
    }
}

@Composable
private fun AddNodeMenu(onAdd: (NodeKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.Add, contentDescription = "Add node") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        NodeKind.values().forEach { kind ->
            DropdownMenuItem(text = { Text(kind.name) }, onClick = { onAdd(kind); expanded = false })
        }
    }
}

@Composable
private fun NodeCard(
    node: FlowNode,
    allNodeIds: List<String>,
    onChange: (FlowNode) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${node.kind()} — ${node.id}", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete node") }
            }
            when (node) {
                is FlowNode.Play -> PlayFields(node, allNodeIds, onChange)
                is FlowNode.Menu -> MenuFields(node, allNodeIds, onChange)
                is FlowNode.CollectDigits -> CollectFields(node, allNodeIds, onChange)
                is FlowNode.Transfer -> TransferFields(node, allNodeIds, onChange)
                is FlowNode.Voicemail -> VoicemailFields(node, allNodeIds, onChange)
                is FlowNode.Webhook -> WebhookFields(node, allNodeIds, onChange)
                is FlowNode.Hangup -> Text("Ends the call.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun ttsText(audio: AudioSource): String = (audio as? AudioSource.Tts)?.text ?: ""

@Composable
private fun PlayFields(node: FlowNode.Play, allNodeIds: List<String>, onChange: (FlowNode) -> Unit) {
    OutlinedTextField(
        value = ttsText(node.audio),
        onValueChange = { onChange(node.copy(audio = AudioSource.Tts(it))) },
        label = { Text("Prompt text (TTS)") },
        modifier = Modifier.fillMaxWidth()
    )
    NodeIdDropdown("Next", node.next, allNodeIds, onSelected = { onChange(node.copy(next = it)) }, nullable = true)
}

@Composable
private fun MenuFields(node: FlowNode.Menu, allNodeIds: List<String>, onChange: (FlowNode) -> Unit) {
    OutlinedTextField(
        value = ttsText(node.prompt),
        onValueChange = { onChange(node.copy(prompt = AudioSource.Tts(it))) },
        label = { Text("Menu prompt (TTS)") },
        modifier = Modifier.fillMaxWidth()
    )
    Text("Digit -> next node", style = MaterialTheme.typography.labelMedium)
    node.options.forEach { (digit, next) ->
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = digit,
                onValueChange = { newDigit ->
                    val updated = node.options.toMutableMap()
                    updated.remove(digit)
                    updated[newDigit] = next
                    onChange(node.copy(options = updated))
                },
                label = { Text("Digit") },
                modifier = Modifier.weight(0.3f)
            )
            NodeIdDropdown("Next", next, allNodeIds, onSelected = {
                val updated = node.options.toMutableMap()
                updated[digit] = it ?: return@NodeIdDropdown
                onChange(node.copy(options = updated))
            }, modifier = Modifier.weight(0.6f))
            IconButton(onClick = {
                val updated = node.options.toMutableMap()
                updated.remove(digit)
                onChange(node.copy(options = updated))
            }) { Icon(Icons.Filled.Delete, contentDescription = "Remove option") }
        }
    }
    TextButton(onClick = {
        val nextDigit = (0..9).map { it.toString() }.firstOrNull { it !in node.options.keys } ?: "9"
        onChange(node.copy(options = node.options + (nextDigit to (allNodeIds.firstOrNull() ?: node.id))))
    }) { Text("+ Add option") }

    NodeIdDropdown("On timeout", node.timeoutNext, allNodeIds, onSelected = { onChange(node.copy(timeoutNext = it)) }, nullable = true)
    NodeIdDropdown("On invalid (after retries)", node.invalidNext, allNodeIds, onSelected = { onChange(node.copy(invalidNext = it)) }, nullable = true)
}

@Composable
private fun CollectFields(node: FlowNode.CollectDigits, allNodeIds: List<String>, onChange: (FlowNode) -> Unit) {
    OutlinedTextField(
        value = ttsText(node.prompt),
        onValueChange = { onChange(node.copy(prompt = AudioSource.Tts(it))) },
        label = { Text("Prompt (TTS)") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = node.numDigits.toString(),
        onValueChange = { onChange(node.copy(numDigits = it.toIntOrNull() ?: node.numDigits)) },
        label = { Text("Number of digits") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = node.variableName,
        onValueChange = { onChange(node.copy(variableName = it)) },
        label = { Text("Store as variable") },
        modifier = Modifier.fillMaxWidth()
    )
    NodeIdDropdown("Next", node.next, allNodeIds, onSelected = { onChange(node.copy(next = it)) }, nullable = true)
}

@Composable
private fun TransferFields(node: FlowNode.Transfer, allNodeIds: List<String>, onChange: (FlowNode) -> Unit) {
    OutlinedTextField(
        value = node.destinationNumber,
        onValueChange = { onChange(node.copy(destinationNumber = it)) },
        label = { Text("Destination number") },
        modifier = Modifier.fillMaxWidth()
    )
    NodeIdDropdown("Next", node.next, allNodeIds, onSelected = { onChange(node.copy(next = it)) }, nullable = true)
}

@Composable
private fun VoicemailFields(node: FlowNode.Voicemail, allNodeIds: List<String>, onChange: (FlowNode) -> Unit) {
    OutlinedTextField(
        value = ttsText(node.prompt),
        onValueChange = { onChange(node.copy(prompt = AudioSource.Tts(it))) },
        label = { Text("Prompt (TTS)") },
        modifier = Modifier.fillMaxWidth()
    )
    NodeIdDropdown("Next", node.next, allNodeIds, onSelected = { onChange(node.copy(next = it)) }, nullable = true)
}

@Composable
private fun WebhookFields(node: FlowNode.Webhook, allNodeIds: List<String>, onChange: (FlowNode) -> Unit) {
    OutlinedTextField(
        value = node.url,
        onValueChange = { onChange(node.copy(url = it)) },
        label = { Text("Webhook URL") },
        modifier = Modifier.fillMaxWidth()
    )
    NodeIdDropdown("Next", node.next, allNodeIds, onSelected = { onChange(node.copy(next = it)) }, nullable = true)
}

@Composable
private fun NodeIdDropdown(
    label: String,
    selected: String?,
    options: List<String>,
    onSelected: (String?) -> Unit,
    nullable: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected ?: "(none)",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (nullable) {
                DropdownMenuItem(text = { Text("(none)") }, onClick = { onSelected(null); expanded = false })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}
