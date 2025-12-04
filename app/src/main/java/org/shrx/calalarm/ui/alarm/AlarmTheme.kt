// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.ui.alarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color scheme for the alarm screen.
 */
data class AlarmColors(
    val background: Color,
    val onBackground: Color,
    val buttonPrimary: Color,
    val buttonSecondary: Color,
    val onButton: Color
)

private val LocalAlarmColors = compositionLocalOf {
    AlarmColors(
        background = Color(0xFFBA1A1A),
        onBackground = Color.White,
        buttonPrimary = Color.White,
        buttonSecondary = Color.White.copy(alpha = 0.9f),
        onButton = Color(0xFFBA1A1A)
    )
}

/**
 * Theme for alarm activity with hardcoded colors independent of system theme.
 * Uses high-contrast red background for maximum visibility and urgency.
 */
@Composable
fun AlarmActivityTheme(content: @Composable () -> Unit) {
    val colors: AlarmColors = AlarmColors(
        background = Color(0xFFBA1A1A),
        onBackground = Color.White,
        buttonPrimary = Color.White,
        buttonSecondary = Color.White.copy(alpha = 0.9f),
        onButton = Color(0xFFBA1A1A)
    )

    CompositionLocalProvider(LocalAlarmColors provides colors) {
        content()
    }
}

/**
 * Accessor for alarm colors in composables.
 */
object AlarmTheme {
    val colors: AlarmColors
        @Composable
        get() = LocalAlarmColors.current
}
