package com.queuetech.queuego.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = lightColorScheme(
    primary = Color(0xFFEC092E),
    onPrimary = Color.White,
    background = Color(0xFFFFFBFC),
    onBackground = Color(0xFF19191D),
    surface = Color.White,
    onSurface = Color(0xFF19191D),
)

@Composable
fun QueueGoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
