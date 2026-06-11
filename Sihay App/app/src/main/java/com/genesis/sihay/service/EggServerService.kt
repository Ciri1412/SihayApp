package com.genesis.sihay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.genesis.sihay.R
import com.genesis.sihay.data.analyzer.EggAnalyzer
import com.genesis.sihay.data.repository.AnalysisHistoryRepository
import com.genesis.sihay.EggClassifier
import com.genesis.sihay.data.server.SihayServer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EggServerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sihayServer: SihayServer? = null
    private var classifier: EggClassifier? = null
    private var analyzer: EggAnalyzer? = null
    private var repository: AnalysisHistoryRepository? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "SihayServerChannel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
        /** True only after the HTTP server is actually listening (so ESP32 can connect). */
        @Volatile
        var isServerReady = false
    }

    override fun onCreate() {
        super.onCreate()
        repository = AnalysisHistoryRepository(
            this,
            Json { ignoreUnknownKeys = true; encodeDefaults = true }
        )
        try {
            classifier = EggClassifier(this)
            if (classifier != null) {
                analyzer = EggAnalyzer(this, classifier!!)
            }
        } catch (e: Throwable) {
            Log.e("EggServerService", "Failed to init AI: ${e.message}", e)
            classifier = null
            analyzer = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopServer()
            return START_NOT_STICKY
        }

        // 1. CRITICAL FIX: Start Foreground IMMEDIATELY to prevent crash
        startForegroundSafely()

        // 2. Start Server Logic
        startServer()

        return START_STICKY
    }

    private fun startForegroundSafely() {
        try {
            createNotificationChannel()
            val notification = createNotification()

            // Android 14+ requires specifying the type if declared in Manifest
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use dataSync type (must match Manifest)
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("EggServerService", "Foreground Start Failed: ${e.message}")
            stopSelf() // Kill service gracefully if we can't go foreground
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sihay Application"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "Sihay Application is running when connected to ESP32"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sihay Application is running")
            .setContentText("Connected to ESP32")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
    }

    private fun startServer() {
        if (sihayServer != null) return // Already running

        serviceScope.launch {
            try {
                if (analyzer == null && classifier != null) {
                    analyzer = EggAnalyzer(this@EggServerService, classifier!!)
                }
                if (analyzer == null) {
                    Log.w("EggServerService", "Analyzer not available; server not started")
                    stopSelf()
                    return@launch
                }
                val repo = repository
                sihayServer = SihayServer(
                    analyzer = analyzer!!,
                    onResult = { response ->
                        Log.d("EggServerService", "Analyzed: ${response.status} (${response.objectType})")
                    },
                    onRecord = { record ->
                        if (repo != null) {
                            serviceScope.launch {
                                try {
                                    repo.add(record)
                                } catch (e: Exception) {
                                    Log.e("EggServerService", "Failed to save ESP32 record: ${e.message}")
                                }
                            }
                        }
                    },
                    onError = { error ->
                        Log.e("EggServerService", "Server Error: $error")
                    }
                )
                sihayServer?.start()
                isRunning = true
                isServerReady = true
                Log.i("EggServerService", "Sihay server listening on port ${sihayServer?.port}")
            } catch (e: Throwable) {
                Log.e("EggServerService", "Server Crash: ${e.message}", e)
                try {
                    sihayServer?.stop()
                } catch (_: Throwable) { }
                sihayServer = null
                isRunning = false
                isServerReady = false
                stopSelf()
            }
        }
    }

    private fun stopServer() {
        try {
            sihayServer?.stop()
            sihayServer = null
            isRunning = false
            isServerReady = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopServer()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}