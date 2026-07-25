package com.example.spikivegcs.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SpikiveSans = FontFamily.SansSerif
val SpikiveMono = FontFamily.Monospace

val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = SpikiveSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SpikiveSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SpikiveSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SpikiveSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SpikiveSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpikiveSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SpikiveSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.1).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SpikiveSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SpikiveSans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
    ),
)
