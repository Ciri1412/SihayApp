package com.genesis.sihay

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.genesis.sihay.data.analyzer.EggAnalyzer
import com.genesis.sihay.data.model.CaptureSource
import com.genesis.sihay.data.model.EggAnalysisRecord
import com.genesis.sihay.data.repository.AnalysisHistoryRepository
import com.genesis.sihay.data.server.EspDevice
import com.genesis.sihay.data.server.EspDiscoveryManager
import com.genesis.sihay.service.EggServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class SihayViewModel(
    application: Application,
    classifier: EggClassifier // Removed 'private val' to fix warning
) : AndroidViewModel(application) {

    private val repository = AnalysisHistoryRepository(application)
    private val analyzer = EggAnalyzer(application, classifier)
    private val discoveryManager = EspDiscoveryManager()

    // --- STATES ---
    val history: StateFlow<List<EggAnalysisRecord>> =
        repository.historyStream.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** In-app only: camera + gallery (for main History screen). */
    val inAppHistory: StateFlow<List<EggAnalysisRecord>> =
        repository.historyStream.map { list ->
            list.filter { it.source == CaptureSource.CAMERA || it.source == CaptureSource.GALLERY }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** ESP32-only (for ESP32 Live Logs & History). */
    val esp32History: StateFlow<List<EggAnalysisRecord>> =
        repository.historyStream.map { list ->
            list.filter { it.source == CaptureSource.ESP32 }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _latestResult = MutableStateFlow<EggAnalysisRecord?>(null)
    val latestResult: StateFlow<EggAnalysisRecord?> = _latestResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _serverIpAddress = MutableStateFlow<String?>(null)
    val serverIpAddress: StateFlow<String?> = _serverIpAddress.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<EspDevice>>(emptyList())
    val scannedDevices: StateFlow<List<EspDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _showDeviceDialog = MutableStateFlow(false)
    val showDeviceDialog: StateFlow<Boolean> = _showDeviceDialog.asStateFlow()

    private val _navigateToDashboard = MutableStateFlow(false)
    val navigateToDashboard: StateFlow<Boolean> = _navigateToDashboard.asStateFlow()

    /** True when the in-app HTTP server is listening (ESP32 can send photos). */
    private val _serverReady = MutableStateFlow(false)
    val serverReady: StateFlow<Boolean> = _serverReady.asStateFlow()

    init {
        // Sync state just in case app restarted while service was running
        _isServerRunning.value = EggServerService.isRunning
        _serverReady.value = EggServerService.isServerReady
        viewModelScope.launch(Dispatchers.IO) { checkWifiIp() }
        // Keep serverReady in sync when service is running
        viewModelScope.launch {
            flow {
                while (true) {
                    emit(EggServerService.isServerReady)
                    delay(500)
                }
            }.collect { ready ->
                if (_isServerRunning.value) _serverReady.value = ready
                else _serverReady.value = false
            }
        }
    }

    // --- ANALYZE FUNCTION ---
    fun analyze(uri: Uri, source: CaptureSource) {
        if (_isAnalyzing.value) return
        viewModelScope.launch {
            try {
                _latestResult.value = null
                _isAnalyzing.value = true
                _errorMessage.value = null

                val result = analyzer.analyze(uri, source)
                _latestResult.value = result
                repository.add(result)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Analysis failed"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // --- ESP32 SCAN & CONNECT ---
    fun startScanForEsp32() {
        checkWifiIp()
        if (_serverIpAddress.value == null) {
            _errorMessage.value = "Connect to WiFi first."
            return
        }
        viewModelScope.launch {
            _isScanning.value = true
            _showDeviceDialog.value = true
            _scannedDevices.value = emptyList()
            val devices = discoveryManager.scanForDevices()
            _scannedDevices.value = devices
            _isScanning.value = false
        }
    }

    fun dismissDialog() {
        _showDeviceDialog.value = false
        _isScanning.value = false
    }

    fun connectToEsp32(device: EspDevice) {
        viewModelScope.launch {
            val myIp = _serverIpAddress.value
            if (myIp == null) {
                _errorMessage.value = "Connect to WiFi first."
                return@launch
            }
            _isScanning.value = true
            _showDeviceDialog.value = false
            // Navigate first so UI transitions immediately; service/configure run in background
            _navigateToDashboard.value = true
            _isScanning.value = false
            try {
                startForegroundService()
                // Wait until the HTTP server is actually listening before telling ESP32 our IP.
                var waited = 0
                while (!EggServerService.isServerReady && waited < 10_000) {
                    delay(300)
                    waited += 300
                }
                if (!EggServerService.isServerReady) {
                    _errorMessage.value = "Server failed to start. Try disconnecting and reconnecting."
                    _isServerRunning.value = false
                    return@launch
                }
                discoveryManager.configureEsp32(device.ip, myIp)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Connected to ${device.ip}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Handshake failed: ${e.message}"
                _isServerRunning.value = false
            }
        }
    }

    fun onDashboardNavigationHandled() {
        _navigateToDashboard.value = false
    }

    private fun startForegroundService() {
        try {
            val intent = Intent(getApplication(), EggServerService::class.java)
            intent.action = EggServerService.ACTION_START
            // Removed unnecessary SDK check (minSdk is 29)
            getApplication<Application>().startForegroundService(intent)
            _isServerRunning.value = true
        } catch (e: Exception) {
            _errorMessage.value = "Service Error: ${e.message}"
            _isServerRunning.value = false
        }
    }

    fun stopEsp32Server() {
        try {
            val intent = Intent(getApplication(), EggServerService::class.java)
            intent.action = EggServerService.ACTION_STOP
            getApplication<Application>().startService(intent)
            _isServerRunning.value = false
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun checkWifiIp() {
        // Important: The phone can have multiple interfaces (VPN, hotspot, etc).
        // We must prefer the active Wi-Fi IPv4 address, otherwise the ESP32 will
        // try to connect to the wrong IP and "connection failed" happens.
        try {
            val app = getApplication<Application>()
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(active)

            if (active != null && caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val lp = cm.getLinkProperties(active)
                val wifiIpv4 = lp?.linkAddresses
                    ?.mapNotNull { it.address as? Inet4Address }
                    ?.firstOrNull { !it.isLoopbackAddress }

                if (wifiIpv4 != null) {
                    _serverIpAddress.value = wifiIpv4.hostAddress
                    return
                }
            }
        } catch (_: Throwable) {
            // Fall back below
        }

        // Fallback: scan all interfaces but prefer wlan0/wifi first.
        try {
            val netInterfaces = NetworkInterface.getNetworkInterfaces() ?: run {
                _serverIpAddress.value = null
                return
            }

            val interfaces = Collections.list(netInterfaces)
                .sortedByDescending { intf ->
                    val name = (intf.name ?: "").lowercase()
                    if (name.contains("wlan") || name.contains("wifi")) 1 else 0
                }

            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        // RFC1918
                        if (host.startsWith("192.") || host.startsWith("10.") || host.startsWith("172.")) {
                            _serverIpAddress.value = host
                            return
                        }
                    }
                }
            }

            _serverIpAddress.value = null
        } catch (_: Throwable) {
            _serverIpAddress.value = null
        }
    }

    fun consumeError() { _errorMessage.value = null }

    fun deleteHistory(ids: Set<String>) {
        viewModelScope.launch { repository.delete(ids) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clear() }
    }

    fun clearInAppHistory() {
        viewModelScope.launch {
            val ids = inAppHistory.value.map { it.id }.toSet()
            repository.delete(ids)
        }
    }

    fun clearEsp32History() {
        viewModelScope.launch {
            val ids = esp32History.value.map { it.id }.toSet()
            repository.delete(ids)
        }
    }

    fun deleteByImageUris(uris: Set<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val ids = history.value.filter { it.imageUri in uris }.map { it.id }.toSet()
            repository.delete(ids)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    companion object {
        fun provideFactory(app: Application, classifier: EggClassifier): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SihayViewModel(app, classifier) as T
                }
            }
    }
}