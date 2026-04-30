package com.flowhack.flowcapital.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Тёмная цветовая схема приложения.
 * Используется по умолчанию.
 */
private val DarkColorScheme = darkColorScheme(
    primary = RedAccent,
    secondary = RedAccentLight,
    background = DarkBackground,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

/**
 * Светлая цветовая схема приложения.
 * Активируется через настройки.
 */
private val LightColorScheme = lightColorScheme(
    primary = RedAccent,
    secondary = RedAccentLight,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary
)

/**
 * Тема приложения FlowCapital.
 * Поддерживает тёмную и светлую темы.
 *
 * @param darkTheme true - тёмная тема, false - светлая
 * @param content Контент приложения
 */
@Suppress("DEPRECATION")
@Composable
fun FlowCapitalTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
