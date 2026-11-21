package com.focusguard.app.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hierarchy
import com.focusguard.app.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainVm = viewModel()
            val mode by vm.prefs.mode.collectAsStateWithLifecycle()
            val pal by vm.prefs.palette.collectAsStateWithLifecycle()
            AppTheme(mode, Palette.of(pal)) { Root(vm) }
        }
    }
}

private class NavTab(val route: String, val icon: ImageVector, val label: Int)

@Composable
private fun Root(vm: MainVm) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val tabs = listOf(
        NavTab("home", Icons.Rounded.Timer, R.string.tab_focus),
        NavTab("tasks", Icons.Rounded.Checklist, R.string.tab_tasks),
        NavTab("plan", Icons.Rounded.CalendarMonth, R.string.tab_plan),
        NavTab("reports", Icons.Rounded.BarChart, R.string.tab_reports),
        NavTab("settings", Icons.Rounded.Settings, R.string.tab_settings)
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                tabs.forEach { t ->
                    val selected = entry?.destination?.hierarchy?.any { it.route == t.route } == true
                    NavigationBarItem(
                        selected = selected, onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label)) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm, nav) }
            composable("profiles") { ProfilesScreen(vm, nav) }
            composable("tasks") { TasksScreen(vm, nav) }
            composable("plan") { PlannerScreen(vm) }
            composable("reports") { ReportsScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
