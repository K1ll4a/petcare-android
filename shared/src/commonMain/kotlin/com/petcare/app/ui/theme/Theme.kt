package com.petcare.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(primary = Color(0xFF2E7D5B), secondary = Color(0xFF6D5E0F))
private val DarkColors = darkColorScheme(primary = Color(0xFF8BD6AE), secondary = Color(0xFFD9C66B))
@Composable
fun PetCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
