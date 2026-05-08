package com.remodex.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.remodex.mobile.data.CodexRepository
import com.remodex.mobile.ui.about.AboutScreen
import com.remodex.mobile.ui.about.WhatsNewScreen
import com.remodex.mobile.ui.home.RootReconnectUiState
import com.remodex.mobile.ui.home.HomeMainContent
import com.remodex.mobile.ui.archived.ArchivedChatsScreen
import com.remodex.mobile.ui.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    repository: CodexRepository,
    reconnectUiState: RootReconnectUiState = RootReconnectUiState(),
    onReconnectSavedPairing: () -> Unit = {},
    onWakeSavedComputer: () -> Unit = {},
    onOpenPairingScanner: () -> Unit = {},
    onGitContextChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.Home,
        modifier = modifier,
    ) {
        composable(AppRoutes.Home) {
            HomeMainContent(
                repository = repository,
                reconnectUiState = reconnectUiState,
                onReconnectSavedPairing = onReconnectSavedPairing,
                onWakeSavedComputer = onWakeSavedComputer,
                onOpenPairingScanner = onOpenPairingScanner,
                onGitContextChanged = onGitContextChanged,
            )
        }
        composable(AppRoutes.Settings) {
            SettingsScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(AppRoutes.About) },
                onNavigateToWhatsNew = { navController.navigate(AppRoutes.WhatsNew) },
            )
        }
        composable(AppRoutes.Archived) {
            ArchivedChatsScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.About) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(AppRoutes.WhatsNew) {
            WhatsNewScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
