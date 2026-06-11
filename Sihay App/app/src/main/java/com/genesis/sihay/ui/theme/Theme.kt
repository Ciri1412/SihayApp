package com.genesis.sihay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SihayPrimary,
    onPrimary = SihayOnPrimary,
    secondary = SihaySecondary,
    surface = SihaySurface,
    onSurface = SihayOnBackground,
    background = SihayBackground,
    onBackground = SihayOnBackground,
    tertiary = SihayAccent,
    primaryContainer = SihayPrimaryDark,
    onTertiary = SihayOnBackground
)

private val DarkColorScheme = darkColorScheme(
    primary = SihayPrimary,
    onPrimary = SihayOnPrimary,
    secondary = SihaySecondary,
    surface = Color.Black,
    onSurface = Color.White,
    background = Color(0xFF1B120B),
    onBackground = Color.White
)

@Composable
fun SIhayTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}