package com.genesis.sihay.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // <--- UPDATED IMPORT
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner // <--- UPDATED IMPORT
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    isAnalyzing: Boolean,
    onCaptureConfirmed: (Uri) -> Unit,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(checkCameraPermission(context)) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(lifecycleOwner, hasPermission) {
        if (hasPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // UPDATED ICON USAGE
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenGallery) {
                        Icon(Icons.Filled.Collections, contentDescription = "Gallery")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.BottomCenter
        ) {
            when {
                !hasPermission -> CameraPermissionPrompt(
                    modifier = Modifier.fillMaxSize(),
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )

                capturedUri != null -> CapturePreview(
                    uri = capturedUri!!,
                    onRetake = { capturedUri = null },
                    onAnalyze = { onCaptureConfirmed(it) },
                    isAnalyzing = isAnalyzing
                )

                else -> {
                    CameraPreview(cameraController = cameraController)
                    CaptureControls(
                        onCapture = {
                            takePhoto(
                                context = context,
                                controller = cameraController,
                                onPhotoCaptured = { uri -> capturedUri = uri }
                            )
                        }
                    )
                }
            }
        }
    }

    if (permissionDenied) {
        PermissionAlert(
            message = "We need permission to use camera.",
            onRetry = {
                permissionDenied = false
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    }
}

@Composable
private fun CameraPreview(cameraController: LifecycleCameraController) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                controller = cameraController
            }
        }
    )
}

@Composable
private fun CaptureControls(
    onCapture: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Center the egg and tap the shutter",
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        FilledIconButton(
            onClick = onCapture,
            shape = CircleShape,
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White
            ),
            modifier = Modifier.size(84.dp)
        ) {
            Icon(Icons.Filled.Camera, contentDescription = "Capture", tint = Color.Black)
        }
    }
}

@Composable
private fun CapturePreview(
    uri: Uri,
    onRetake: () -> Unit,
    onAnalyze: (Uri) -> Unit,
    isAnalyzing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Captured egg",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onRetake) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Retake")
            }
            Button(
                onClick = { onAnalyze(uri) },
                enabled = !isAnalyzing
            ) {
                Icon(Icons.Filled.Camera, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (isAnalyzing) "Analyzing…" else "Analyze Image")
            }
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    modifier: Modifier = Modifier,
    onRequest: () -> Unit
) {
    Column(
        modifier = modifier
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Camera,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "We need permission to use camera.",
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequest) {
            Text("Grant permission")
        }
    }
}

@Composable
private fun PermissionAlert(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(message, textAlign = TextAlign.Center)
            Button(onClick = onRetry) {
                Text("Grant permission")
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    controller: LifecycleCameraController,
    onPhotoCaptured: (Uri) -> Unit
) {
    val photoFile = createTempFile(context)
    val photoUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    controller.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onPhotoCaptured(photoUri)
            }
        }
    )
}

private fun createTempFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.cacheDir
    return File.createTempFile(
        "Sihay_$timeStamp",
        ".jpg",
        storageDir
    )
}

private fun checkCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED