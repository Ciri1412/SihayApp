package com.genesis.sihay.ui.navigation

sealed class SihayScreen(val route: String) {
    object Splash : SihayScreen("splash")
    object Home : SihayScreen("home")
    object Dashboard : SihayScreen("dashboard")       // Standard Mode
    object EspDashboard : SihayScreen("esp_dashboard") // <--- NEW: ESP32 Mode
    object Instructions : SihayScreen("instructions")
    object Gallery : SihayScreen("gallery")
    object Camera : SihayScreen("camera")
    object Result : SihayScreen("result")
    object History : SihayScreen("history")
    object EspHistory : SihayScreen("esp_history")  // ESP32-only logs
}