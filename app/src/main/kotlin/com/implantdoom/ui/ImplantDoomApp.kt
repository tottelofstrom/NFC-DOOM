package com.implantdoom.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.implantdoom.ui.screens.AboutScreen
import com.implantdoom.ui.screens.BuilderScreen
import com.implantdoom.ui.screens.DetailsScreen
import com.implantdoom.ui.screens.DiagnosticsScreen
import com.implantdoom.ui.screens.HomeScreen
import com.implantdoom.ui.screens.PlayScreen
import com.implantdoom.ui.screens.ScanScreen
import com.implantdoom.ui.theme.ImplantDoomTheme

/**
 * Root composable: theme + single NavHost over all seven screens. One-shot
 * navigation requests from [AppViewModel] (e.g. "a cartridge was scanned, show
 * details") are consumed here.
 */
@Composable
fun ImplantDoomApp(viewModel: AppViewModel) {
    ImplantDoomTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            val pendingRoute by viewModel.pendingRoute.collectAsState()

            // React to navigation requests coming from NFC scans / builder actions.
            androidx.compose.runtime.LaunchedEffect(pendingRoute) {
                pendingRoute?.let { route ->
                    navController.navigate(route)
                    viewModel.consumeRoute()
                }
            }

            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(viewModel, navController) }
                composable(Routes.SCAN) { ScanScreen(viewModel, navController) }
                composable(Routes.DETAILS) { DetailsScreen(viewModel, navController) }
                composable(Routes.PLAY) { PlayScreen(viewModel, navController) }
                composable(Routes.BUILDER) { BuilderScreen(viewModel, navController) }
                composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(viewModel, navController) }
                composable(Routes.ABOUT) { AboutScreen(viewModel, navController) }
            }
        }
    }
}

/**
 * Shared scaffold: a top app bar with an optional back button and a content slot
 * laid out under the bar. [scrollableContent] receives the inner padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    navController: NavHostController?,
    showBack: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (showBack && navController != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            content(innerPadding)
        }
    }
}
