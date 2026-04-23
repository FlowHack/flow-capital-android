package com.example.flowcapital.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Тёмная цветовая схема приложения.
 * Используется для всех экранов.
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
 * Тема приложения FlowCapital.
 * Применяет тёмную цветовую схему и настраивает системные бары.
 *
 * @param content Контент приложения
 */
@Suppress("DEPRECATION")
@Composable
fun FlowCapitalTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
