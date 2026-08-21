package com.aakash.callloop.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Sapphire,
    onPrimary = Color.White,
    primaryContainer = DarkSapphire,
    onPrimaryContainer = IceBlue,
    secondary = PowderBlue,
    onSecondary = DeepNavy,
    background = DeepNavy,
    onBackground = IceBlue,
    surface = DarkSapphire,
    onSurface = IceBlue,
    surfaceVariant = DarkSapphire,
    onSurfaceVariant = PowderBlue,
    error = StatusError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Sapphire,
    onPrimary = Color.White,
    primaryContainer = PowderBlue,
    onPrimaryContainer = DeepNavy,
    secondary = DarkSapphire,
    onSecondary = Color.White,
    background = IceBlue,
    onBackground = DeepNavy,
    surface = PowderBlue,
    onSurface = DeepNavy,
    surfaceVariant = PowderBlue,
    onSurfaceVariant = DeepNavy,
    error = StatusError,
    onError = Color.White
)

@Composable
fun CallLoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
