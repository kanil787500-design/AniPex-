package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AetherDarkScheme =
  darkColorScheme(
    primary = AstralCyan,
    onPrimary = AstralNight,
    secondary = AstralGold,
    onSecondary = AstralNight,
    tertiary = AstralCryo,
    onTertiary = AstralNight,
    background = AstralNight,
    onBackground = AstralTextPrimary,
    surface = AstralSurface,
    onSurface = AstralTextPrimary,
    surfaceVariant = AstralSurfaceVariant,
    onSurfaceVariant = AstralTextSecondary,
    outline = AstralOutline,
    error = AstralRose,
  )

private val AetherShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
  )

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AetherDarkScheme,
    typography = Typography,
    shapes = AetherShapes,
    content = content,
  )
}
