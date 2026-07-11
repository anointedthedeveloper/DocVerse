package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF8FCAFF),
    onPrimary = Color(0xFF003259),
    primaryContainer = Color(0xFF00487E),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBAC8DB),
    onSecondary = Color(0xFF243140),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF11141B),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF21252F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099)
  )

private val AmoledColorScheme =
  darkColorScheme(
    primary = Color(0xFF8FCAFF),
    onPrimary = Color(0xFF003259),
    primaryContainer = Color(0xFF00487E),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBAC8DB),
    onSecondary = Color(0xFF243140),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0D0F14),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF191C24),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ProfPolishPrimary,
    onPrimary = ProfPolishOnPrimary,
    primaryContainer = ProfPolishPrimaryContainer,
    onPrimaryContainer = ProfPolishOnPrimaryContainer,
    background = ProfPolishBackground,
    onBackground = ProfPolishOnBackground,
    surface = ProfPolishSurface,
    onSurface = ProfPolishOnSurface,
    surfaceVariant = ProfPolishSurfaceVariant,
    onSurfaceVariant = ProfPolishOnSurfaceVariant,
    outline = ProfPolishOutline
  )

@Composable
fun MyApplicationTheme(
  themeMode: String = "light",
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when (themeMode) {
      "dark" -> DarkColorScheme
      "amoled" -> AmoledColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
