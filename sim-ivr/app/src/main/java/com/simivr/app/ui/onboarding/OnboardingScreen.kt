package com.simivr.app.ui.onboarding

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

private val REQUIRED_PERMISSIONS = buildList {
    add(android.Manifest.permission.READ_PHONE_STATE)
    add(android.Manifest.permission.CALL_PHONE)
    add(android.Manifest.permission.ANSWER_PHONE_CALLS)
    add(android.Manifest.permission.READ_CALL_LOG)
    add(android.Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val permissionsState = rememberMultiplePermissionsState(REQUIRED_PERMISSIONS)

    var isDefaultDialer by remember { mutableStateOf(isDefaultDialer(context)) }
    var isBatteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultDialer = isDefaultDialer(context)
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isBatteryExempt = isIgnoringBatteryOptimizations(context)
    }

    val allGranted = permissionsState.allPermissionsGranted && isDefaultDialer

    Scaffold(topBar = { TopAppBar(title = { Text("Set up SIM IVR") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "SIM IVR needs to become your default Phone app to answer, control, and route calls. " +
                    "None of this works without these steps — Android gives no other way for an app to control real SIM calls.",
                style = MaterialTheme.typography.bodyMedium
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(REQUIRED_PERMISSIONS) { permission ->
                    val granted = ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ChecklistRow(label = permissionLabel(permission), granted = granted)
                }
                item { ChecklistRow(label = "Default Phone app", granted = isDefaultDialer) }
                item { ChecklistRow(label = "Battery optimization exemption (recommended)", granted = isBatteryExempt) }
            }

            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Grant permissions") }

            Button(
                onClick = {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                    if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDefaultDialer
            ) { Text(if (isDefaultDialer) "Already default Phone app" else "Set as default Phone app") }

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    batteryLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBatteryExempt
            ) { Text(if (isBatteryExempt) "Battery optimization already disabled" else "Disable battery optimization") }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                enabled = allGranted
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun ChecklistRow(label: String, granted: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun permissionLabel(permission: String): String = when (permission) {
    android.Manifest.permission.READ_PHONE_STATE -> "Read phone state"
    android.Manifest.permission.CALL_PHONE -> "Place calls"
    android.Manifest.permission.ANSWER_PHONE_CALLS -> "Answer calls"
    android.Manifest.permission.READ_CALL_LOG -> "Read call log"
    android.Manifest.permission.RECORD_AUDIO -> "Microphone (DTMF detection)"
    android.Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
    else -> permission
}

private fun isDefaultDialer(context: Context): Boolean {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
    return telecomManager.defaultDialerPackage == context.packageName
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
