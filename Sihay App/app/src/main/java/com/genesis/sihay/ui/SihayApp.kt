package com.genesis.sihay.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState // <--- Added Import
import androidx.navigation.compose.rememberNavController
import com.genesis.sihay.SihayViewModel
import com.genesis.sihay.EggClassifier
import com.genesis.sihay.data.model.CaptureSource
import com.genesis.sihay.ui.navigation.SihayScreen
import com.genesis.sihay.ui.screens.*

@Composable
fun SihayApp(classifier: EggClassifier) {
    val context = LocalContext.current
    val application = context.applicationContext as Application

    val viewModel: SihayViewModel = viewModel(
        factory = SihayViewModel.provideFactory(application, classifier)
    )

    val navController = rememberNavController()
    // We track the current screen to handle the "stuck" error case
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val history by viewModel.history.collectAsStateWithLifecycle()
    val latestResult by viewModel.latestResult.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val serverIp by viewModel.serverIpAddress.collectAsStateWithLifecycle()
    val serverReady by viewModel.serverReady.collectAsStateWithLifecycle()
    val inAppHistory by viewModel.inAppHistory.collectAsStateWithLifecycle()
    val esp32History by viewModel.esp32History.collectAsStateWithLifecycle()

    val navigateToDashboard by viewModel.navigateToDashboard.collectAsStateWithLifecycle()

    // Handle ESP32 Navigation
    LaunchedEffect(navigateToDashboard) {
        if (navigateToDashboard) {
            navController.navigate(SihayScreen.EspDashboard.route) {
                popUpTo(SihayScreen.Home.route) { inclusive = false }
            }
            viewModel.onDashboardNavigationHandled()
        }
    }

    // --- ERROR DIALOG HANDLING ---
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { }, // Prevent clicking outside to dismiss (force OK)
            icon = { Icon(Icons.Default.Warning, contentDescription = "Error") },
            title = { Text(text = "Analysis Failed") },
            text = { Text(text = errorMessage!!) },
            confirmButton = {
                Button(onClick = {
                    viewModel.consumeError()

                    // FIX: If we are stuck on the Result screen (loading) and it failed (no result),
                    // automatically go back to Gallery/Camera when user clicks OK.
                    if (currentRoute == SihayScreen.Result.route && latestResult == null) {
                        navController.popBackStack()
                    }
                }) { Text("OK") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = SihayScreen.Splash.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(SihayScreen.Splash.route) {
                SplashScreen(
                    onFinish = {
                        navController.navigate(SihayScreen.Home.route) {
                            popUpTo(SihayScreen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(SihayScreen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onStart = { navController.navigate(SihayScreen.Dashboard.route) },
                    onReturnToEsp32 = { navController.navigate(SihayScreen.EspDashboard.route) },
                    onExit = { (context as? android.app.Activity)?.finish() }
                )
            }

            composable(SihayScreen.Dashboard.route) {
                DashboardScreen(
                    onShowUpload = { destination ->
                        when (destination) {
                            UploadDestination.Camera -> navController.navigate(SihayScreen.Camera.route)
                            UploadDestination.Gallery -> navController.navigate(SihayScreen.Gallery.route)
                        }
                    },
                    onShowInstructions = { navController.navigate(SihayScreen.Instructions.route) },
                    onShowHistory = { navController.navigate(SihayScreen.History.route) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(SihayScreen.EspDashboard.route) {
                EspDashboardScreen(
                    connectedIp = serverIp,
                    serverReady = serverReady,
                    onShowHistory = { navController.navigate(SihayScreen.EspHistory.route) },
                    onBack = { navController.popBackStack() },
                    onDisconnect = {
                        viewModel.stopEsp32Server()
                        navController.popBackStack()
                    }
                )
            }

            composable(SihayScreen.Instructions.route) {
                InstructionsScreen(onBack = { navController.popBackStack() })
            }

            composable(SihayScreen.Gallery.route) {
                GalleryScreen(
                    isAnalyzing = isAnalyzing,
                    onBack = { navController.popBackStack() },
                    onAnalyze = { uri ->
                        viewModel.analyze(uri, CaptureSource.GALLERY)
                        navController.navigate(SihayScreen.Result.route)
                    },
                    onOpenCamera = { navController.navigate(SihayScreen.Camera.route) },
                    onDeleteFromHistory = viewModel::deleteByImageUris
                )
            }

            composable(SihayScreen.Camera.route) {
                CameraScreen(
                    isAnalyzing = isAnalyzing,
                    onCaptureConfirmed = { uri ->
                        viewModel.analyze(uri, CaptureSource.CAMERA)
                        navController.navigate(SihayScreen.Result.route)
                    },
                    onBack = { navController.popBackStack() },
                    onOpenGallery = { navController.navigate(SihayScreen.Gallery.route) }
                )
            }

            composable(SihayScreen.Result.route) {
                ResultScreen(
                    result = latestResult,
                    isAnalyzing = isAnalyzing,
                    onGoHome = {
                        navController.navigate(SihayScreen.Home.route) {
                            popUpTo(SihayScreen.Home.route) { inclusive = false }
                        }
                    },
                    onBackToSource = { source ->
                        val targetRoute = when (source) {
                            CaptureSource.GALLERY -> SihayScreen.Gallery.route
                            CaptureSource.CAMERA -> SihayScreen.Camera.route
                            else -> SihayScreen.Dashboard.route
                        }
                        navController.popBackStack(targetRoute, inclusive = false)
                    }
                )
            }

            composable(SihayScreen.History.route) {
                HistoryScreen(
                    history = inAppHistory,
                    title = "History & Logs",
                    onDelete = viewModel::deleteHistory,
                    onClearAll = viewModel::clearInAppHistory,
                    onBack = { navController.popBackStack() },
                    onStartAnalyzing = { navController.popBackStack() }
                )
            }

            composable(SihayScreen.EspHistory.route) {
                HistoryScreen(
                    history = esp32History,
                    title = "ESP32 Live Logs & History",
                    onDelete = viewModel::deleteHistory,
                    onClearAll = viewModel::clearEsp32History,
                    onBack = { navController.popBackStack() },
                    onStartAnalyzing = { navController.popBackStack() }
                )
            }
        }
    }
}