package com.wellbeing.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wellbeing.tracker.service.MonitorService
import com.wellbeing.tracker.service.Permissions
import com.wellbeing.tracker.ui.FocusScreen
import com.wellbeing.tracker.ui.LimitsScreen
import com.wellbeing.tracker.ui.MainViewModel
import com.wellbeing.tracker.ui.ReportsScreen
import com.wellbeing.tracker.ui.SettingsScreen
import com.wellbeing.tracker.ui.TodayScreen
import com.wellbeing.tracker.ui.theme.WellbeingTheme
import dagger.hilt.android.AndroidEntryPoint

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "আজ", Icons.Rounded.Home),
    Limits("limits", "সীমা", Icons.Rounded.Tune),
    Focus("focus", "Focus", Icons.Rounded.Timer),
    Reports("reports", "রিপোর্ট", Icons.Rounded.BarChart),
    Settings("settings", "সেটিংস", Icons.Rounded.Settings),
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val requestedTab = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.value = intent?.getStringExtra(EXTRA_TAB)
        setContent { WellbeingTheme { AppRoot(vm, requestedTab.value) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        requestedTab.value = intent.getStringExtra(EXTRA_TAB)
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_LIMITS = "limits"
    }

    override fun onResume() {
        super.onResume()
        // Start tracking as soon as Usage Access is granted; harmless if already running.
        if (Permissions.usageAccess(this)) {
            try { MonitorService.start(this) } catch (_: Exception) { }
        }
        vm.refresh(withReport = true)
    }
}

@Composable
private fun AppRoot(vm: MainViewModel, requestedTab: String?) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(requestedTab) {
        Tab.entries.firstOrNull { it.route == requestedTab }?.let { tab ->
            nav.navigate(tab.route) { popUpTo(Tab.Today.route); launchSingleTop = true }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = backStack?.destination?.route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(Tab.Today.route)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(nav, startDestination = Tab.Today.route) {
                composable(Tab.Today.route) { TodayScreen(state, settings) }
                composable(Tab.Limits.route) { LimitsScreen(state, settings) { l, v, e -> vm.setLimit(l, v, e) } }
                composable(Tab.Focus.route) {
                    FocusScreen(
                        settings = settings,
                        onStart = vm::startFocus,
                        onEnd = vm::endFocus,
                        onBlocked = vm::setBlockedApps,
                        onBedtime = vm::setBedtime,
                    )
                }
                composable(Tab.Reports.route) { ReportsScreen(report, range, vm::setRange) }
                composable(Tab.Settings.route) { SettingsScreen(vm, settings) }
            }
        }
    }
}
