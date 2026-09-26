package com.simivr.app

import android.content.Context
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simivr.app.ui.audiotest.AudioTestScreen
import com.simivr.app.ui.calllog.CallLogScreen
import com.simivr.app.ui.campaigns.CampaignDetailScreen
import com.simivr.app.ui.campaigns.CampaignsScreen
import com.simivr.app.ui.common.NavRoutes
import com.simivr.app.ui.dashboard.DashboardScreen
import com.simivr.app.ui.flows.FlowEditorScreen
import com.simivr.app.ui.flows.FlowsScreen
import com.simivr.app.ui.onboarding.OnboardingScreen
import com.simivr.app.ui.settings.SettingsScreen
import com.simivr.app.ui.theme.SimIvrTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimIvrTheme {
                SimIvrApp(isDefaultDialer = ::isDefaultDialer)
            }
        }
    }

    private fun isDefaultDialer(): Boolean {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecomManager.defaultDialerPackage == packageName
    }
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(NavRoutes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
    BottomTab(NavRoutes.FLOWS, "Flows", Icons.Filled.List),
    BottomTab(NavRoutes.CAMPAIGNS, "Campaigns", Icons.Filled.CallMade),
    BottomTab(NavRoutes.CALL_LOG, "Call log", Icons.Filled.History),
    BottomTab(NavRoutes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun SimIvrApp(isDefaultDialer: () -> Boolean) {
    var onboardingDone by remember { mutableStateOf(isDefaultDialer()) }

    if (!onboardingDone) {
        OnboardingScreen(onContinue = { onboardingDone = true })
        return
    }

    val navController = rememberNavController()

    Scaffold(
        bottomBar = { AppBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.DASHBOARD,
            modifier = Modifier.padding(padding)
        ) {
            composable(NavRoutes.DASHBOARD) { DashboardScreen() }
            composable(NavRoutes.FLOWS) {
                FlowsScreen(onOpenFlow = { id -> navController.navigate(NavRoutes.flowEditor(id)) })
            }
            composable(
                NavRoutes.FLOW_EDITOR,
                arguments = listOf(navArgument("flowId") { defaultValue = NavRoutes.NEW_FLOW_ID })
            ) {
                FlowEditorScreen(onSaved = { navController.popBackStack() })
            }
            composable(NavRoutes.CAMPAIGNS) {
                CampaignsScreen(onOpenCampaign = { id -> navController.navigate(NavRoutes.campaignDetail(id)) })
            }
            composable(
                NavRoutes.CAMPAIGN_DETAIL,
                arguments = listOf(navArgument("campaignId") { })
            ) {
                CampaignDetailScreen()
            }
            composable(NavRoutes.CALL_LOG) { CallLogScreen() }
            composable(NavRoutes.SETTINGS) {
                SettingsScreen(onOpenAudioTest = { navController.navigate(NavRoutes.AUDIO_TEST) })
            }
            composable(NavRoutes.AUDIO_TEST) { AudioTestScreen() }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        bottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}
