package com.god.compressor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF9C6BFF),
    secondary = Color(0xFF64FFDA)
)
private val Light = lightColorScheme(
    primary = Color(0xFF6A3EFF),
    secondary = Color(0xFF00BFA5)
)

@Composable
fun GodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
