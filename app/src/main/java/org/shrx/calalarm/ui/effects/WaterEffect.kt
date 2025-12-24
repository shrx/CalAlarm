// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 github.com/shrx

package org.shrx.calalarm.ui.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin

/**
 * Animated tropical ocean water effect using layered gradients.
 * Creates slow, smooth waving patterns that simulate light caustics on water.
 */
@Composable
fun WaterEffect(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "water")

    // Multiple wave animations with different speeds for depth
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Tropical water colors - from darker teal to bright turquoise
        val deepTeal = Color(0xFF006B6B)
        val teal = Color(0xFF008B8B)
        val turquoise = Color(0xFF40E0D0)
        val brightCyan = Color(0xFF00CED1)
        val lightCyan = Color(0xFF7FFFD4)

        // Base layer - deep water
        drawRect(color = deepTeal)

        // Layer 1: Linear waves - more visible
        drawLinearWaves(
            colors = listOf(
                teal.copy(alpha = 0.9f),
                turquoise.copy(alpha = 0.7f),
                teal.copy(alpha = 0.9f)
            ),
            progress = wave1,
            offset = 0f
        )

        // Layer 2: Radial caustics - circular patterns with bright ring
        drawRadialCaustics(
            colors = listOf(
                Color.Transparent,
                turquoise.copy(alpha = 0.1f),
                turquoise.copy(alpha = 0.3f),
                brightCyan.copy(alpha = 0.6f),
                brightCyan.copy(alpha = 0.7f),
                turquoise.copy(alpha = 0.4f),
                turquoise.copy(alpha = 0.2f),
                Color.Transparent
            ),
            progress = wave2,
            offset = 0.3f
        )

        // Layer 3: Light radial caustics - brighter highlight rings
        drawRadialCaustics(
            colors = listOf(
                Color.Transparent,
                brightCyan.copy(alpha = 0.1f),
                brightCyan.copy(alpha = 0.3f),
                lightCyan.copy(alpha = 0.5f),
                lightCyan.copy(alpha = 0.6f),
                brightCyan.copy(alpha = 0.3f),
                brightCyan.copy(alpha = 0.15f),
                Color.Transparent
            ),
            progress = wave3,
            offset = 0.6f
        )
    }
}

/**
 * Draws linear wave gradients that sweep across the screen.
 */
private fun DrawScope.drawLinearWaves(
    colors: List<Color>,
    progress: Float,
    offset: Float
) {
    val width = size.width
    val height = size.height

    // Create multiple linear gradients
    val numWaves = 3
    for (i in 0 until numWaves) {
        val phase1 = (progress + offset + i * 0.07f) * Math.PI.toFloat() * 2f
        val phase2 = (progress + offset + i * 0.11f) * Math.PI.toFloat() * 2f

        val waveOffsetX = sin(phase1.toDouble()).toFloat()
        val waveOffsetY = sin(phase2.toDouble()).toFloat()

        val goldenRatio = 0.618f
        val xBase = (i * goldenRatio) % 1.0f
        val yBase = (i * (1f - goldenRatio)) % 1.0f

        val startX = width * (xBase + waveOffsetX * 0.3f)
        val startY = height * (yBase + waveOffsetY * 0.3f)
        val endX = width * ((xBase + 0.4f) % 1.0f - waveOffsetX * 0.3f)
        val endY = height * ((yBase + 0.4f) % 1.0f - waveOffsetY * 0.3f)

        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(startX, startY),
                end = Offset(endX, endY)
            )
        )
    }
}

/**
 * Draws radial caustic patterns - circular light patterns.
 */
private fun DrawScope.drawRadialCaustics(
    colors: List<Color>,
    progress: Float,
    offset: Float
) {
    val width = size.width
    val height = size.height

    // Create multiple radial gradients scattered across screen
    val numCaustics = 8
    for (i in 0 until numCaustics) {
        val phase1 = (progress + offset + i * 0.13f) * Math.PI.toFloat() * 2f
        val phase2 = (progress + offset + i * 0.17f) * Math.PI.toFloat() * 2f

        val waveOffsetX = sin(phase1.toDouble()).toFloat()
        val waveOffsetY = sin(phase2.toDouble()).toFloat()

        // Use golden ratio for distribution
        val goldenRatio = 0.618f
        val xBase = (i * goldenRatio) % 1.0f
        val yBase = (i * (1f - goldenRatio)) % 1.0f

        val centerX = width * (xBase + waveOffsetX * 0.25f)
        val centerY = height * (yBase + waveOffsetY * 0.25f)

        // Vary the radius based on phase
        val radiusMultiplier = 0.8f + 0.4f * sin((phase1 + phase2).toDouble()).toFloat()
        val radius = width * 0.3f * radiusMultiplier

        drawCircle(
            brush = Brush.radialGradient(
                colors = colors,
                center = Offset(centerX, centerY),
                radius = radius
            ),
            center = Offset(centerX, centerY),
            radius = radius
        )
    }
}
