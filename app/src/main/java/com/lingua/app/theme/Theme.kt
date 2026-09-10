package com.lingua.app.theme

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
import com.lingua.app.data.settings.ThemeMode

private val LinguaLightScheme =
  lightColorScheme(
    primary = Teal40,
    onPrimary = Neutral100,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Sage40,
    onSecondary = Neutral100,
    secondaryContainer = Sage90,
    onSecondaryContainer = Sage10,
    tertiary = Sky40,
    onTertiary = Neutral100,
    tertiaryContainer = Sky90,
    onTertiaryContainer = Sky10,
    error = Red40,
    onError = Neutral100,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Neutral98,
    onBackground = Neutral12,
    surface = Neutral98,
    onSurface = Neutral12,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Color(0xFFF4F7F5),
    surfaceContainer = Color(0xFFEEF1EF),
    surfaceContainerHigh = Color(0xFFE8ECEA),
    surfaceContainerHighest = Color(0xFFE2E6E4),
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    inverseSurface = Neutral24,
    inverseOnSurface = Neutral95,
    inversePrimary = Teal80,
  )

private val LinguaDarkScheme =
  darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Sage80,
    onSecondary = Sage10,
    secondaryContainer = Sage30,
    onSecondaryContainer = Sage90,
    tertiary = Sky80,
    onTertiary = Sky10,
    tertiaryContainer = Sky30,
    onTertiaryContainer = Sky90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceContainerLowest = Neutral6,
    surfaceContainerLow = Neutral12,
    surfaceContainer = Neutral17,
    surfaceContainerHigh = Neutral22,
    surfaceContainerHighest = Neutral24,
    outline = Color(0xFF889391),
    outlineVariant = NeutralVariant30,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral24,
    inversePrimary = Teal40,
  )

@Composable
fun LinguaTheme(
  themeMode: ThemeMode = ThemeMode.System,
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val darkTheme =
    when (themeMode) {
      ThemeMode.System -> isSystemInDarkTheme()
      ThemeMode.Light -> false
      ThemeMode.Dark -> true
    }

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> LinguaDarkScheme
      else -> LinguaLightScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = LinguaTypography, content = content)
}
