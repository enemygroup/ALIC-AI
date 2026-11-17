package com.alic.ai.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Slowly moving multicolour light blobs behind every screen. Drawn in the draw phase only. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val ph by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "auroraPh"
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(C.Bg)
        val pal = C.orb + listOf(C.Cyan, C.Purple, C.Blue, Color(0xFFFF2D55))
        val t = ph * 2f * PI.toFloat()
        val alpha = if (ThemeState.isDark) 0.26f else 0.14f
        for (i in 0 until 7) {
            val col = pal[i % pal.size]
            val cx = size.width * (0.5f + 0.45f * sin(t + i * 1.3f))
            val cy = size.height * (0.5f + 0.40f * cos(t * 0.7f + i * 0.9f))
            val rad = size.minDimension * (0.50f + 0.18f * sin(t * 2f + i))
            drawCircle(
                Brush.radialGradient(listOf(col.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = rad),
                radius = rad, center = Offset(cx, cy)
            )
        }
    }
}
