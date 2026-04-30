package com.flowhack.flowcapital.ui.screens.calculator

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Проверяет, является ли экран широким.
 * Широким считается экран с шириной более 600dp (планшет или горизонтальная ориентация).
 *
 * @return true если экран широкий
 */
@Composable
fun isWideScreen(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp >= 600
}