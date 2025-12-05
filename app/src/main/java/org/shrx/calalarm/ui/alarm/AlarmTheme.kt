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
    val goldenYellow = Color(0xFFFFD700)
    val softGold = Color(0xFFFFE066) // Slightly less saturated for snooze button
    AlarmColors(
        background = Color(0xFF006B6B), // Deep teal (water base color)
        onBackground = goldenYellow,
        buttonPrimary = goldenYellow, // Dismiss button - golden yellow
        buttonSecondary = softGold, // Snooze button - less saturated
        onButton = Color(0xFF003366) // Dark navy blue for button text
    )
}

/**
 * Theme for alarm activity with hardcoded colors independent of system theme.
 * Uses tropical water background with golden yellow accents for visibility.
 */
@Composable
fun AlarmActivityTheme(content: @Composable () -> Unit) {
    val goldenYellow = Color(0xFFFFD700)
    val softGold = Color(0xFFFFE066) // Slightly less saturated for snooze button

    val colors: AlarmColors = AlarmColors(
        background = Color(0xFF006B6B), // Deep teal (water base color)
        onBackground = goldenYellow,
        buttonPrimary = goldenYellow, // Dismiss button - golden yellow
        buttonSecondary = softGold, // Snooze button - less saturated
        onButton = Color(0xFF003366) // Dark navy blue for button text
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
