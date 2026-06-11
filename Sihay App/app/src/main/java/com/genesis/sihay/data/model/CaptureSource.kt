package com.genesis.sihay.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class CaptureSource {
    CAMERA,
    GALLERY,
    ESP32,  // Eggs analyzed from ESP32 device
    OTHER   // Legacy / fallback
}