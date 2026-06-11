package com.genesis.sihay.ui.screens

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.genesis.sihay.R
import com.genesis.sihay.SihayViewModel
import com.genesis.sihay.data.server.EspDevice
import com.genesis.sihay.ui.components.SihayFloatingEgg
import com.genesis.sihay.ui.components.SihayGradientBackground
import com.genesis.sihay.ui.components.EggWaveAnimation
import com.genesis.sihay.ui.components.SihayLogo
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: SihayViewModel,
    onStart: () -> Unit,
    onReturnToEsp32: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    var showExitAnimation by remember { mutableStateOf(false) }

    // Observe States
    val serverIp by viewModel.serverIpAddress.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()

    // Scan States
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val showDeviceDialog by viewModel.showDeviceDialog.collectAsState()

    val isWifiAvailable = serverIp != null
    val espButtonColor = if (isWifiAvailable) MaterialTheme.colorScheme.primary else Color.Gray

    val gifPainter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(R.drawable.sihay_background)
            .decoderFactory(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoderDecoder.Factory()
                } else {
                    GifDecoder.Factory()
                }
            )
            .build()
    )

    LaunchedEffect(Unit) {
        while(true) {
            viewModel.checkWifiIp()
            delay(5000)
        }
    }

    SihayGradientBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SihayLogo()
                Spacer(modifier = Modifier.height(16.dp))
                SihayFloatingEgg(size = 96.dp)
            }
            Image(
                painter = gifPainter,
                contentDescription = "Sihay animated background",
                modifier = Modifier
                    .size(350.dp)
                    .graphicsLayer { alpha = 0.92f },
                contentScale = ContentScale.Fit
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // START APP BUTTON
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().shadow(8.dp, CircleShape),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("START") }

                Spacer(modifier = Modifier.height(16.dp))

                // ESP32 BUTTON: when connected, "Return to ESP32"; otherwise start/scan
                Button(
                    onClick = {
                        if (!isWifiAvailable) {
                            Toast.makeText(context, "Connect to WiFi first", Toast.LENGTH_SHORT).show()
                        } else {
                            if (isServerRunning) {
                                onReturnToEsp32()
                            } else {
                                viewModel.startScanForEsp32()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().shadow(8.dp, CircleShape),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = espButtonColor,
                        contentColor = Color.White
                    )
                ) {
                    val text = when {
                        !isWifiAvailable -> "NO WIFI"
                        isServerRunning -> "RETURN TO ESP32"
                        else -> "START WITH ESP32"
                    }
                    Text(text)
                }

                // IP Address Display
                if (isServerRunning && serverIp != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "SERVER RUNNING: $serverIp",
                            color = Color.Green,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    TextButton(
                        onClick = { showExitDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Exit") }
                }
            }
        }

        // --- SCAN DIALOG ---
        if (showDeviceDialog) {
            Dialog(onDismissRequest = { viewModel.dismissDialog() }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select ESP32 Device",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isScanning) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scanning WiFi...")
                        } else {
                            if (scannedDevices.isEmpty()) {
                                Text("No devices found.", color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.startScanForEsp32() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scan Again")
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 200.dp).fillMaxWidth()
                                ) {
                                    items(scannedDevices) { device ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.connectToEsp32(device) }
                                                .padding(12.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(device.name, fontWeight = FontWeight.Bold)
                                                Text(device.ip, fontSize = 12.sp, color = Color.Gray)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.dismissDialog() }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Leave Sihay?") },
                text = { Text("Are you sure you want to exit the app?") },
                confirmButton = {
                    TextButton(onClick = { showExitDialog = false; showExitAnimation = true }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) { Text("No") }
                }
            )
        }

        AnimatedVisibility(visible = showExitAnimation, enter = fadeIn(), exit = fadeOut()) {
            SihayGradientBackground(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { EggWaveAnimation() }
            }
        }
    }

    if (showExitAnimation) {
        LaunchedEffect(Unit) {
            delay(1600)
            (context as? Activity)?.finish()
            onExit()
        }
    }
}