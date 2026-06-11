package com.genesis.sihay.data.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL

data class EspDevice(
    val name: String,
    val ip: String
)

class EspDiscoveryManager {

    private val DISCOVERY_PORT = 8888
    private val TIMEOUT_MS = 2000 // Scan for 2 seconds

    // 1. Scan for ESP32s using UDP Broadcast
    suspend fun scanForDevices(): List<EspDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<EspDevice>()
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = TIMEOUT_MS

            // Send "SIHAY_DISCOVER" message
            val sendData = "SIHAY_DISCOVER".toByteArray()
            // Broadcast to 255.255.255.255 (All devices on Wifi)
            val sendPacket = DatagramPacket(
                sendData,
                sendData.size,
                InetAddress.getByName("255.255.255.255"),
                DISCOVERY_PORT
            )
            socket.send(sendPacket)
            Log.d("EspDiscovery", "Broadcast sent")

            // Listen for replies (e.g., "SIHAY_HERE")
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
                try {
                    val recvBuf = ByteArray(1024)
                    val receivePacket = DatagramPacket(recvBuf, recvBuf.size)
                    socket.receive(receivePacket)

                    val message = String(receivePacket.data, 0, receivePacket.length).trim()
                    val ip = receivePacket.address.hostAddress

                    if (message.startsWith("SIHAY_HERE") && ip != null) {
                        // Avoid duplicates
                        if (devices.none { it.ip == ip }) {
                            devices.add(EspDevice("ESP32 Egg Sorter", ip))
                        }
                    }
                } catch (e: Exception) {
                    // Socket timeout (expected when no more devices reply)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket?.close()
        }

        return@withContext devices
    }

    // 2. Send the Phone's IP to the selected ESP32
    // This allows the ESP32 to know where to send the photos automatically.
    suspend fun configureEsp32(espIp: String, myIp: String) = withContext(Dispatchers.IO) {
        try {
            // We assume the ESP32 has a mini webserver handler at /config
            val url = URL("http://$espIp/config?server_ip=$myIp")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            val responseCode = connection.responseCode
            Log.d("EspDiscovery", "Config sent to $espIp. Response: $responseCode")
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("EspDiscovery", "Failed to configure ESP32: ${e.message}")
            throw e
        }
    }
}