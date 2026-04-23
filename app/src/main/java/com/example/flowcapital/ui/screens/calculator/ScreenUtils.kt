package com.example.flowcapital.ui.screens.calculator

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Проверяет, является ли экран узким.
 * Узким считается экран с шириной менее 600dp.
 * В этом случае скрываются дополнительные колонки (Кошелек, Начисление).
 *
 * @return true если экран узкий (мобильный телефон в портретной ориентации)
 */
@Composable
fun isNarrowScreen(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp < 600
}

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