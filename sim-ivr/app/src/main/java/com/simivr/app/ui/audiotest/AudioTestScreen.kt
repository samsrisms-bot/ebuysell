package com.simivr.app.ui.audiotest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AudioTestScreen(viewModel: AudioTestViewModel = hiltViewModel()) {
    val isListening by viewModel.isListening.collectAsState()
    val detected by viewModel.detectedDigits.collectAsState()
    val error by viewModel.lastError.collectAsState()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Call Audio Test") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "IMPORTANT: Android gives apps no direct access to the other party's call audio. " +
                            "This test plays a tone through the speaker and listens with the microphone — results " +
                            "only reflect what your phone's mic can actually pick up. During a REAL call, whether " +
                            "the far end hears the speaker and whether their keypresses are detected depends heavily " +
                            "on the phone model, speaker/mic placement, and OEM call-audio processing (some OEMs mute " +
                            "or heavily denoise the mic during calls, which can block this feature entirely). " +
                            "Always test with a second phone calling in before relying on this in production.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(onClick = { viewModel.playTestTone() }, modifier = Modifier.fillMaxWidth()) {
                Text("Play test tone (speaker)")
            }

            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.toggleListening()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isListening) "Stop listening for DTMF" else "Start listening for DTMF (mic)")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Detected digits:", style = MaterialTheme.typography.titleSmall)
                    Text(detected.ifBlank { "(none yet)" }, style = MaterialTheme.typography.headlineSmall)
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
