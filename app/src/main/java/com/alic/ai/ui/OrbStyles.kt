package com.alic.ai.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Legacy orb drawings. The user-facing orb list lives in Orb.kt as ORB_STYLES (List<OrbDesign>). */
fun DrawScope.drawOrbStyle(style: String, pal: List<Color>, c: Offset, r: Float, ph: Float, lv: Float, unit: Float) {
    val tau = (2 * PI).toFloat()
    val n = pal.size
    when (style) {
        "rings" -> for (k in 0 until 5) {
            val rad = r * (0.45f + k * 0.14f) * (1f + 0.04f * sin(ph * tau * 2 + k) + lv * 0.08f)
            val col = pal[k % n]
            drawCircle(col.copy(alpha = 0.85f - k * 0.12f), radius = rad, center = c, style = Stroke(width = (2.5f - k * 0.3f) * unit))
            drawArc(
                col, startAngle = ph * 360f * (if (k % 2 == 0) 1f else -1f) + k * 60f, sweepAngle = 80f, useCenter = false,
                topLeft = Offset(c.x - rad, c.y - rad), size = Size(rad * 2, rad * 2),
                style = Stroke(width = 4f * unit, cap = StrokeCap.Round)
            )
        }
        "bars" -> {
            val bars = 72
            for (i in 0 until bars) {
                val a = i * tau / bars
                val w = 0.5f + 0.5f * sin(ph * tau * 3 + i * 0.45f)
                val len = r * (0.10f + (0.18f + lv * 0.5f) * w)
                drawLine(
                    pal[i % n].copy(alpha = 0.55f + 0.45f * w),
                    Offset(c.x + cos(a) * r * 0.55f, c.y + sin(a) * r * 0.55f),
                    Offset(c.x + cos(a) * (r * 0.55f + len), c.y + sin(a) * (r * 0.55f + len)),
                    strokeWidth = 2.5f * unit, cap = StrokeCap.Round
                )
            }
        }
        "orbit" -> for (k in 0 until 36) {
            val layer = k % 3
            val a = ph * tau * (1f + layer * 0.4f) + k * (tau / 36f) * (1f + layer)
            val rr = r * (0.5f + 0.25f * layer + lv * 0.1f)
            drawCircle(pal[k % n].copy(alpha = 0.9f), radius = (2f + (k % 3)) * unit, center = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr * 0.6f))
        }
        else -> {
            drawCircle(
                Brush.radialGradient(pal.map { it.copy(alpha = 0.5f) } + Color.Transparent, center = c, radius = r),
                radius = r, center = c
            )
            for (k in 0 until 40) {
                val a = k * 2.39996f + ph * tau
                val d = r * (0.2f + 0.7f * ((k * 37) % 100) / 100f)
                drawCircle(pal[k % n], radius = (1.5f + (k % 3)) * unit, center = Offset(c.x + cos(a) * d, c.y + sin(a) * d))
            }
        }
    }
}
