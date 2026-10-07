package com.huang1988pioneer.mediaconverter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgApp = Color(0xFFEEF4FB)
val CardWhite = Color(0xFFFFFFFF)
val Blue = Color(0xFF2F7BFF)
val BlueSoft = Color(0xFFE8F1FF)
val TextPrimary = Color(0xFF1A2332)
val TextSecondary = Color(0xFF5B6B7C)
val TextMuted = Color(0xFF8A97A8)
val Green = Color(0xFF22C55E)
val GreenSoft = Color(0xFFE8F9EE)
val Pink = Color(0xFFFB7299)
val PinkSoft = Color(0xFFFFE8F0)
val Danger = Color(0xFFEF4444)
val BorderSoft = Color(0xFFD7E4F5)

private val scheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Pink,
    onSecondary = Color.White,
    background = BgApp,
    surface = CardWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Danger
)

@Composable
fun ConverterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
