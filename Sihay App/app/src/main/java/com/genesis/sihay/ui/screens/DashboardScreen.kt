package com.genesis.sihay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.genesis.sihay.ui.components.SihayGradientBackground
import com.genesis.sihay.ui.UploadDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onShowUpload: (UploadDestination) -> Unit,
    onShowInstructions: () -> Unit,
    onShowHistory: () -> Unit,
    onBack: () -> Unit
) {
    // State to control the Upload Reminder Dialog
    var showUploadDialog by remember { mutableStateOf(false) }

    SihayGradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dashboard", fontWeight = FontWeight.Bold) },
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
                Text(
                    text = "Choose what you want to do",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                DashboardCard(
                    title = "Upload",
                    description = "Camera or gallery",
                    icon = Icons.Default.Image,
                    onClick = { showUploadDialog = true } // Trigger Dialog
                )

                DashboardCard(
                    title = "Instructions",
                    description = "Photography & candling guide",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    onClick = onShowInstructions
                )

                DashboardCard(
                    title = "History",
                    description = "Previous analyses",
                    icon = Icons.Default.History,
                    onClick = onShowHistory
                )
            }
        }

        // --- UPLOAD REMINDER DIALOG ---
        if (showUploadDialog) {
            Dialog(onDismissRequest = { showUploadDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Upload Reminder",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF5D4037), // Brownish color
                            fontWeight = FontWeight.Bold
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReminderBullet("Clean the egg surface for a clearer candling result.")
                            ReminderBullet("Hold your phone steady to reduce blur.")
                            ReminderBullet("Center the egg and avoid direct light glare.")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Camera Option
                        SelectionCard(
                            title = "Camera",
                            subtitle = "Capture a new egg photo",
                            icon = Icons.Default.CameraAlt,
                            onClick = {
                                showUploadDialog = false
                                onShowUpload(UploadDestination.Camera)
                            }
                        )

                        // Gallery Option
                        SelectionCard(
                            title = "Gallery",
                            subtitle = "Pick from your album",
                            icon = Icons.Default.Image,
                            onClick = {
                                showUploadDialog = false
                                onShowUpload(UploadDestination.Gallery)
                            }
                        )

                        TextButton(
                            onClick = { showUploadDialog = false },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Cancel", color = Color(0xFFD87846))
                        }
                    }
                }
            }
        }
    }
}

// --- HELPER COMPOSABLES ---

@Composable
private fun ReminderBullet(text: String) {
    // FIXED: Changed 'crossAxisAlignment' (Flutter) to 'verticalAlignment' (Compose)
    Row(verticalAlignment = Alignment.Top) {
        Text("• ", fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF3E0), // Light orange/beige background
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEFEBE9), // Lighter brown icon bg
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF5D4037))
                }
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D4037))
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Column {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}