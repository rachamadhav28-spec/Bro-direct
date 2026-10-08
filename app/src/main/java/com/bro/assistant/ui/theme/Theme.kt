package com.bro.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BroColors {
    val Background = Color(0xFF05080F)
    val Panel = Color(0xFF111A2B)
    val Accent = Color(0xFF00E5FF)
    val UserBubble = Color(0xFF123A57)
    val BroBubble = Color(0xFF1B2538)
    val Listening = Color(0xFF3DDC84)
    val Thinking = Color(0xFFB388FF)
    val Executing = Color(0xFFFFA726)
    val Success = Color(0xFF66BB6A)
    val Error = Color(0xFFEF5350)
}

@Composable
fun BroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BroColors.Accent,
            onPrimary = Color.Black,
            background = BroColors.Background,
            onBackground = Color.White,
            surface = BroColors.Panel,
            onSurface = Color.White
        ),
        content = content
    )
}
