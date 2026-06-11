package com.genesis.sihay.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
// We keep the data class import, but we will define the loader locally to ensure the fix is applied
import com.genesis.sihay.data.gallery.GalleryImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    isAnalyzing: Boolean,
    onBack: () -> Unit,
    onAnalyze: (android.net.Uri) -> Unit,
    onOpenCamera: () -> Unit,
    onDeleteFromHistory: (Set<String>) -> Unit = {}
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasGalleryPermission(context)) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(GallerySelectionMode.None) }
    var selectedUris by remember { mutableStateOf(setOf<String>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            showPermissionDialog = true
        }
    }

    // Load images when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            // Using the local function (defined at bottom) to ensure ALL folders are scanned
            images = fetchAllGalleryImages(context)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCamera) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Camera")
                    }
                    IconButton(onClick = { dropdownExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Select multiple to delete") },
                            onClick = {
                                selectionMode = GallerySelectionMode.Delete
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select multiple to analyze") },
                            onClick = {
                                selectionMode = GallerySelectionMode.Analyze
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !hasPermission -> PermissionRequest(
                    modifier = Modifier.fillMaxSize(),
                    onRequest = {
                        val permission = galleryPermission()
                        permissionLauncher.launch(permission)
                    }
                )

                isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading gallery...", color = MaterialTheme.colorScheme.onBackground)
                }

                images.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No images found on device.",
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }

                else -> GalleryGrid(
                    modifier = Modifier.fillMaxSize(),
                    images = images,
                    selectionMode = selectionMode,
                    selectedUris = selectedUris,
                    onToggleSelection = { uri ->
                        selectedUris = selectedUris.toMutableSet().also {
                            if (it.contains(uri)) it.remove(uri) else it.add(uri)
                        }.toSet()
                    },
                    onAnalyze = { uri ->
                        if (selectionMode == GallerySelectionMode.Analyze) {
                            selectedUris = setOf(uri.toString())
                        }
                        onAnalyze(uri)
                    },
                    isAnalyzing = isAnalyzing
                )
            }

            if (selectionMode != GallerySelectionMode.None && selectedUris.isNotEmpty()) {
                SelectionActionBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    mode = selectionMode,
                    selectionCount = selectedUris.size,
                    onClear = {
                        selectionMode = GallerySelectionMode.None
                        selectedUris = emptySet()
                    },
                    onAnalyze = {
                        selectedUris.firstOrNull()?.let { uri ->
                            onAnalyze(android.net.Uri.parse(uri))
                        }
                    },
                    onDelete = {
                        onDeleteFromHistory(selectedUris)
                        selectionMode = GallerySelectionMode.None
                        selectedUris = emptySet()
                    }
                )
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Gallery access required") },
            text = {
                Text("We need permission to access your gallery so we can analyze egg photos.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        permissionLauncher.launch(galleryPermission())
                    }
                ) {
                    Text("Grant permission")
                }
            },
            dismissButton = {
                Text(
                    "Not now",
                    modifier = Modifier
                        .clickable { showPermissionDialog = false }
                        .padding(16.dp)
                )
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryGrid(
    modifier: Modifier = Modifier,
    images: List<GalleryImage>,
    selectionMode: GallerySelectionMode,
    selectedUris: Set<String>,
    onToggleSelection: (String) -> Unit,
    onAnalyze: (android.net.Uri) -> Unit,
    isAnalyzing: Boolean
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(images, key = { it.uri }) { image ->
            val isSelected = selectedUris.contains(image.uri.toString())
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .clickable {
                        if (selectionMode == GallerySelectionMode.None) {
                            onAnalyze(image.uri)
                        } else {
                            onToggleSelection(image.uri.toString())
                        }
                    }
            ) {
                Box {
                    AsyncImage(
                        model = image.uri,
                        contentDescription = image.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentScale = ContentScale.Crop
                    )
                    if (selectionMode != GallerySelectionMode.None) {
                        AssistChip(
                            onClick = { onToggleSelection(image.uri.toString()) },
                            label = { Text(if (selectionMode == GallerySelectionMode.Analyze) "Analyze" else "Delete") },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(Icons.Filled.Collections, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequest(
    modifier: Modifier = Modifier,
    onRequest: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Collections,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "We need permission to access your gallery.",
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequest) {
            Text("Grant permission")
        }
    }
}

@Composable
private fun SelectionActionBar(
    modifier: Modifier = Modifier,
    mode: GallerySelectionMode,
    selectionCount: Int,
    onClear: () -> Unit,
    onAnalyze: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$selectionCount selected")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Clear",
                    modifier = Modifier
                        .clickable { onClear() }
                )
                when (mode) {
                    GallerySelectionMode.Analyze -> Button(onClick = onAnalyze) {
                        Text("Analyze")
                    }

                    GallerySelectionMode.Delete -> Button(onClick = onDelete) {
                        Text("Delete")
                    }

                    GallerySelectionMode.None -> {}
                }
            }
        }
    }
}

private enum class GallerySelectionMode {
    None, Delete, Analyze
}

private fun galleryPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasGalleryPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, galleryPermission()) == PackageManager.PERMISSION_GRANTED


// --- UPDATED LOADING LOGIC ---
private fun fetchAllGalleryImages(context: Context): List<GalleryImage> {
    val imageList = mutableListOf<GalleryImage>()

    // 1. Query External Storage (All Volumes)
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    // 2. Select Columns (Added DATE_ADDED)
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED
    )

    // 3. Sort by Newest
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    try {
        context.contentResolver.query(
            collection,
            projection,
            null, // selection = null (No filters = All folders)
            null, // selectionArgs = null
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            // Get the date column index
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                // Fetch the timestamp (Date Added is usually in seconds, convert to millis if needed)
                val timestamp = cursor.getLong(dateColumn) * 1000L

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // Pass the timestamp to fix the error
                imageList.add(GalleryImage(contentUri, name, timestamp))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return imageList
}