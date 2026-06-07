package com.johnymoo.arverify.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.johnymoo.arverify.R
import com.johnymoo.arverify.ui.home.HomeScreen
import com.johnymoo.arverify.ui.library.LibraryScreen
import com.johnymoo.arverify.ui.session.FrameViewerScreen
import com.johnymoo.arverify.ui.session.SessionDetailScreen
import com.johnymoo.arverify.ui.settings.DiagnosticsScreen
import com.johnymoo.arverify.ui.settings.SettingsScreen
import com.johnymoo.arverify.ui.theme.ScanForgeTheme
import java.net.URLDecoder

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun ScanForgeApp() {
    ScanForgeTheme {
        val nav = rememberNavController()
        val tabs = listOf(
            Tab(Routes.HOME, R.string.nav_home, Icons.Outlined.Home),
            Tab(Routes.LIBRARY, R.string.nav_library, Icons.Outlined.GridView),
            Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
        )
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route

        Scaffold(
            bottomBar = {
                if (current in tabs.map { it.route }) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
            }
        ) { pad ->
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.LIBRARY) { LibraryScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.DIAGNOSTICS) { DiagnosticsScreen() }
                composable(Routes.SESSION_DETAIL) { entry ->
                    val dir = URLDecoder.decode(entry.arguments?.getString("dir").orEmpty(), "UTF-8")
                    SessionDetailScreen(nav, dir)
                }
                composable(Routes.FRAME_VIEWER) { entry ->
                    val dir = URLDecoder.decode(entry.arguments?.getString("dir").orEmpty(), "UTF-8")
                    val index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0
                    FrameViewerScreen(dir, index)
                }
            }
        }
    }
}
