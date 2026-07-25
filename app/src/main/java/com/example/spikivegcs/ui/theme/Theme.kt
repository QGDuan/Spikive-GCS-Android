package com.example.spikivegcs.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LichtblickDarkColorScheme = darkColorScheme(
    primary = LichtblickPrimary,
    onPrimary = LichtblickBackground,
    primaryContainer = LichtblickGrey400,
    onPrimaryContainer = LichtblickTextPrimary,
    secondary = LichtblickSecondary,
    onSecondary = LichtblickBackground,
    background = LichtblickBackground,
    onBackground = LichtblickTextPrimary,
    surface = LichtblickPaper,
    onSurface = LichtblickTextPrimary,
    surfaceVariant = LichtblickGrey400,
    onSurfaceVariant = LichtblickTextSecondary,
    error = LichtblickError,
    onError = LichtblickBackground,
    outline = LichtblickDivider,
    outlineVariant = LichtblickGrey400,
)

@Composable
fun SpikiveGCSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LichtblickDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
