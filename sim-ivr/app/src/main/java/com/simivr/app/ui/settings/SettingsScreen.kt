package com.simivr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.simivr.app.data.entity.CallerRuleType

@Composable
fun SettingsScreen(
    onOpenAudioTest: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val incomingLines by viewModel.incomingLines.collectAsState()
    val callerRules by viewModel.callerRules.collectAsState()
    var newBlockedNumber by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard("CRM integration") {
                    OutlinedTextField(
                        value = settings.crmBaseUrl,
                        onValueChange = { url -> viewModel.update { it.copy(crmBaseUrl = url) } },
                        label = { Text("CRM base URL (e.g. https://crm.example.com)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settings.crmApiKey,
                        onValueChange = { key -> viewModel.update { it.copy(crmApiKey = key) } },
                        label = { Text("API key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SectionCard("Audio") {
                    Text("Call volume: ${settings.callVolumePercent}%")
                    Slider(
                        value = settings.callVolumePercent.toFloat(),
                        onValueChange = { v -> viewModel.update { it.copy(callVolumePercent = v.toInt()) } },
                        valueRange = 0f..100f
                    )
                    Text("DTMF sensitivity: ${"%.2f".format(settings.dtmfSensitivity)} (higher = more sensitive, more false positives)")
                    Slider(
                        value = settings.dtmfSensitivity.toFloat(),
                        onValueChange = { v -> viewModel.update { it.copy(dtmfSensitivity = v.toDouble()) } },
                        valueRange = 0f..1f
                    )
                    TextButton(onClick = onOpenAudioTest) { Text("Open Call Audio Test") }
                }
            }

            item {
                SectionCard("Default SIM") {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        listOf(0, 1).forEach { slot ->
                            TextButton(onClick = { viewModel.update { it.copy(defaultSimSlot = slot) } }) {
                                Text(if (settings.defaultSimSlot == slot) "[SIM ${slot + 1}]" else "SIM ${slot + 1}")
                            }
                        }
                    }
                }
            }

            item {
                SectionCard("Incoming lines") {
                    incomingLines.forEach { line ->
                        Text("SIM ${line.simSlot + 1}", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Enabled")
                            Switch(checked = line.enabled, onCheckedChange = { viewModel.updateIncomingLine(line.copy(enabled = it)) })
                        }
                        Text("Rings before auto-answer: ${line.ringsBeforeAnswer}")
                        Slider(
                            value = line.ringsBeforeAnswer.toFloat(),
                            onValueChange = { viewModel.updateIncomingLine(line.copy(ringsBeforeAnswer = it.toInt().coerceAtLeast(1))) },
                            valueRange = 1f..8f,
                            steps = 6
                        )
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Business hours only")
                            Switch(checked = line.businessHoursEnabled, onCheckedChange = { viewModel.updateIncomingLine(line.copy(businessHoursEnabled = it)) })
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }

            item {
                SectionCard("Blocklist") {
                    Row {
                        OutlinedTextField(
                            value = newBlockedNumber,
                            onValueChange = { newBlockedNumber = it },
                            label = { Text("Phone number") },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.addBlockedNumber(newBlockedNumber); newBlockedNumber = "" }) { Text("Block") }
                    }
                    callerRules.filter { it.type == CallerRuleType.BLOCKLIST }.forEach { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(rule.phoneNumber)
                            TextButton(onClick = { viewModel.deleteRule(rule) }) { Text("Remove") }
                        }
                    }
                }
            }

            item {
                SectionCard("TRAI / TCCCP compliance (bulk outbound calling)") {
                    Text(
                        "Bulk promotional calling in India must follow TRAI/TCCCP rules: use a registered telemarketer " +
                            "header/number (140-series), scrub against the National DND registry, and honor opt-outs. " +
                            "SIM IVR keeps its own DND list (numbers that pressed 9) and skips them automatically — " +
                            "it does not scrub the national DND registry for you.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("I confirm I am a registered telemarketer / this use is compliant")
                        Switch(
                            checked = settings.telemarketerRegistered,
                            onCheckedChange = { viewModel.update { s -> s.copy(telemarketerRegistered = it, bulkCallingComplianceAccepted = it) } }
                        )
                    }
                }
            }

            item {
                SectionCard("Recording") {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Enable call/voicemail recording (off by default)")
                        Switch(
                            checked = settings.recordingEnabledGlobal,
                            onCheckedChange = { viewModel.update { s -> s.copy(recordingEnabledGlobal = it) } }
                        )
                    }
                    Text(
                        "When enabled, a consent line is always played before recording starts.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
