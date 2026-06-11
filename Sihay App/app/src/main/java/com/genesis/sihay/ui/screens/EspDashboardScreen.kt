package com.genesis.sihay.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genesis.sihay.ui.components.SihayGradientBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EspDashboardScreen(
    connectedIp: String?,
    serverReady: Boolean,
    onShowHistory: () -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit
) {
    SihayGradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ESP32 Monitor", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Connection Status Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9) // Light Green
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF2E7D32))
                        Column {
                            Text("Connected to ESP32", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            Text(
                                text = "IP: ${connectedIp ?: "Unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                // Server ready indicator
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (serverReady) Color(0xFFE3F2FD) else Color(0xFFFFF8E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (serverReady) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1565C0))
                            Column {
                                Text("Server ready", fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                                Text(
                                    text = "ESP32 can send photos to this device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1565C0)
                                )
                            }
                        } else {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFF57C00))
                            Column {
                                Text("Starting server…", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                Text(
                                    text = "Wait a moment, then try capturing on the ESP32",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFF57C00)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Live Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                // RENAMED FUNCTION CALL HERE
                EspDashboardCard(
                    title = "Live Logs & History",
                    description = "View real-time analysis results from the machine",
                    icon = Icons.Default.History,
                    onClick = onShowHistory
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disconnect & Exit")
                }
            }
        }
    }
}

// RENAMED FUNCTION DEFINITION TO AVOID CONFLICT
@Composable
private fun EspDashboardCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFE65100))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}